package com.openai.apexsticktrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.Choreographer;
import android.view.View;

public class AimCanvasView extends View implements Choreographer.FrameCallback {
    private final GameState state;
    private final ControllerInput input;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastNs;
    private boolean running;
    private boolean prevShoot;
    private boolean trackingFireHit;
    private ToneGenerator toneGenerator;
    private int toneVolume = -1;

    public AimCanvasView(Context c, GameState s, ControllerInput i) {
        super(c);
        state = s;
        input = i;
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        if (toneGenerator != null) {
            try { toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long now) {
        if (!running) return;
        float dt = lastNs == 0L ? 1f/60f : Math.min(0.05f, (now-lastNs)/1_000_000_000f);
        lastNs = now;
        updateCamera(dt);
        state.update(dt);
        updateShootingFeedback();
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void updateCamera(float dt) {
        float x = response(input.getRightX());
        float y = response(input.getRightY());
        float dz = Math.max(0f, Math.min(0.30f, state.deadzone));
        if (dz > 0f) {
            x = deadzone(x, dz);
            y = deadzone(y, dz);
        }
        float sensitivity = input.isAdsPressed() ? state.adsSensitivity : state.hipSensitivity;
        float dps = Math.max(1f, state.degreesPerSecondPerSensitivity) * Math.max(0.01f, sensitivity);
        state.yaw = GameState.wrap180(state.yaw + x*dps*dt);
        state.pitch = GameState.clamp(state.pitch + y*dps*dt, -88f, 88f);
    }

    private void updateShootingFeedback() {
        boolean shoot = input.isShootPressed();

        if (state.mode == GameState.Mode.TRACKING) {
            // 跟枪不是“一枪判定”，而是持续判定：
            // 准心在球内 + 扳机保持按下 => 命中状态持续成立。
            boolean nowHit = shoot && state.isCenterOnTrackingTarget();

            // 只在刚进入有效命中时响一次，避免按住扳机持续蜂鸣。
            if (nowHit && !trackingFireHit) {
                playHitTone();
            }
            trackingFireHit = nowHit;

            // 保留一次扳机按下的shots/hits统计，不影响持续颜色反馈。
            if (shoot && !prevShoot) {
                state.shootAtCenter();
            }
        } else {
            trackingFireHit = false;

            // 六目标保持原来的“扣一次扳机打一发”逻辑；
            // 只有真正打中并刷新一个球时才响一下。
            if (shoot && !prevShoot) {
                boolean hit = state.shootAtCenter();
                if (hit && state.mode == GameState.Mode.SIX_TARGETS) {
                    playHitTone();
                }
            }
        }

        prevShoot = shoot;
    }

    private void playHitTone() {
        if (!state.hitSoundEnabled) return;

        int volume = (int)GameState.clamp(state.hitSoundVolumePct, 0f, 100f);
        try {
            if (toneGenerator == null || toneVolume != volume) {
                if (toneGenerator != null) toneGenerator.release();
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
                toneVolume = volume;
            }
            // 短促、高频的确认音，接近练枪软件常见的命中提示，不遮住连续跟枪。
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 55);
        } catch (Exception ignored) {}
    }

    private float response(float v) {
        float e = GameState.clamp(state.responseExponent, 0.25f, 4f);
        if (Math.abs(e - 1f) < 0.0001f) return v;
        return Math.signum(v) * (float)Math.pow(Math.abs(v), e);
    }

    private static float deadzone(float v, float dz) {
        float a = Math.abs(v);
        if (a <= dz) return 0f;
        return Math.signum(v) * (a-dz)/(1f-dz);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int bg = backgroundColor(state.backgroundBrightnessPct);
        canvas.drawColor(bg);

        drawReferenceSystem(canvas);

        if (state.mode == GameState.Mode.TRACKING) {
            int trackingColor = trackingFireHit ? 0xff55e88a : 0xffff5a4d;
            drawTarget(canvas, state.tracking, trackingColor, state.trackingTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.ARC) {
            drawTarget(canvas, state.arc, 0xffffb347, state.arcTargetAngularDiameter);
        } else {
            for (GameState.Target t : state.six) {
                drawTarget(canvas, t, 0xffff715c, state.sixTargetAngularDiameter);
            }
        }
    }

    private int backgroundColor(float pct) {
        float t = GameState.clamp(pct, 0f, 100f) / 100f;
        int base = (int)(10 + 54*t);
        int r = base;
        int g = base + 3;
        int b = base + 8;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private void drawReferenceSystem(Canvas canvas) {
        float strength = GameState.clamp(state.referenceStrengthPct, 0f, 100f) / 100f;
        if (strength <= 0.001f) return;

        float hFov = GameState.clamp(state.fovDeg, 50f, 150f);
        float halfH = hFov * 0.5f;
        float aspect = Math.max(0.1f, getWidth() / (float)Math.max(1, getHeight()));
        double vFovRad = 2.0 * Math.atan(Math.tan(Math.toRadians(hFov) * 0.5) / aspect);
        float halfV = (float)Math.toDegrees(vFovRad) * 0.5f;

        paint.setStyle(Paint.Style.STROKE);

        // Horizontal world-pitch guides every 10 degrees.
        for (int worldPitch = -50; worldPitch <= 50; worldPitch += 10) {
            float relPitch = worldPitch - state.pitch;
            if (Math.abs(relPitch) > halfV + 5f) continue;
            float y = projectY(relPitch, halfV);
            boolean major = worldPitch == 0 || worldPitch % 20 == 0;
            int alpha = (int)((major ? 90 : 46) * strength);
            paint.setColor((alpha << 24) | 0x00aab2bf);
            paint.setStrokeWidth(major ? 2.2f : 1.1f);
            canvas.drawLine(0f, y, getWidth(), y, paint);
        }

        // Vertical world-yaw guides every 15 degrees; major every 45 degrees.
        int nearest15 = Math.round(state.yaw / 15f) * 15;
        for (int k = -8; k <= 8; k++) {
            int worldYaw = nearest15 + k * 15;
            float relYaw = shortest(worldYaw, state.yaw);
            if (Math.abs(relYaw) > halfH + 5f) continue;
            float x = projectX(relYaw, halfH);
            int wrapped = ((worldYaw % 360) + 360) % 360;
            boolean major = wrapped % 45 == 0;
            int alpha = (int)((major ? 100 : 50) * strength);
            paint.setColor((alpha << 24) | 0x009ba6b7);
            paint.setStrokeWidth(major ? 2.2f : 1.0f);
            canvas.drawLine(x, 0f, x, getHeight(), paint);

            if (major) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(18f);
                paint.setColor(((int)(150 * strength) << 24) | 0x00c0c7d2);
                canvas.drawText(compassLabel(wrapped), x + 5f, getHeight() - 18f, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }

        // A slightly stronger horizon cue.
        float horizonRelPitch = -state.pitch;
        if (Math.abs(horizonRelPitch) <= halfV + 2f) {
            float y = projectY(horizonRelPitch, halfV);
            paint.setColor(((int)(130 * strength) << 24) | 0x00c3cad4);
            paint.setStrokeWidth(2.8f);
            canvas.drawLine(0f, y, getWidth(), y, paint);
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private String compassLabel(int deg) {
        switch (deg) {
            case 0: return "0°";
            case 45: return "45°";
            case 90: return "90°";
            case 135: return "135°";
            case 180: return "180°";
            case 225: return "225°";
            case 270: return "270°";
            case 315: return "315°";
            default: return deg + "°";
        }
    }

    private float projectX(float relYaw, float halfH) {
        float nx = (float)(Math.tan(Math.toRadians(relYaw)) / Math.tan(Math.toRadians(halfH)));
        return getWidth() * 0.5f * (1f + nx);
    }

    private float projectY(float relPitch, float halfV) {
        float ny = (float)(Math.tan(Math.toRadians(relPitch)) / Math.tan(Math.toRadians(halfV)));
        return getHeight() * 0.5f * (1f + ny);
    }

    private void drawTarget(Canvas canvas, GameState.Target t, int color, float angularDiameter) {
        float relYaw = shortest(t.yawDeg, state.yaw);
        float relPitch = t.pitchDeg - state.pitch;
        float hFov = GameState.clamp(state.fovDeg, 50f, 150f);
        float halfH = hFov*0.5f;
        float aspect = Math.max(0.1f, getWidth()/(float)Math.max(1,getHeight()));
        double vFov = 2.0*Math.atan(Math.tan(Math.toRadians(hFov)*0.5)/aspect);
        float halfV = (float)Math.toDegrees(vFov)*0.5f;
        if (Math.abs(relYaw) > halfH+8f || Math.abs(relPitch) > halfV+8f) return;

        float x = projectX(relYaw, halfH);
        float y = projectY(relPitch, halfV);
        float radius = (float)(Math.tan(Math.toRadians(Math.max(0.1f, angularDiameter)*0.5))
                * getWidth()*0.5 / Math.tan(Math.toRadians(halfH)));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x, y, Math.max(5f,radius), paint);

        // Thin bright rim helps the target stay readable over the grid.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xffffd7cf);
        canvas.drawCircle(x, y, Math.max(5f,radius), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static float shortest(float a,float b) {
        float d=a-b;
        while (d>180f) d-=360f;
        while (d<-180f) d+=360f;
        return d;
    }
}
