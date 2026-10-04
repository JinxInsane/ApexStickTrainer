package com.openai.apexsticktrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

public class AimCanvasView extends View implements Choreographer.FrameCallback {
    public interface TrainingListener {
        void onTrainingFinished();
    }

    private final GameState state;
    private final ControllerInput input;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastNs;
    private boolean running;
    private boolean prevShoot;
    private boolean trackingFireHit;
    private boolean arcFireHit;
    private boolean recoilFireHit;
    private ToneGenerator toneGenerator;
    private int toneVolume = -1;
    private HandlerThread soundThread;
    private Handler soundHandler;
    private long lastHitToneMs = 0L;
    private static final long HIT_TONE_COOLDOWN_MS = 140L;
    private TrainingListener trainingListener;

    // 复仇女神实验弹道：软件视频T1–T35反推。
    // 时间：四连发内部55.56ms；每组第4发到下一组第1发约180ms。
    private static final float[] NEMESIS_SHOT_TIME = new float[]{
            0.000000f, 0.055556f, 0.111111f, 0.166667f,
            0.346667f, 0.402222f, 0.457778f, 0.513333f,
            0.693333f, 0.748889f, 0.804444f, 0.860000f,
            1.040000f, 1.095556f, 1.151111f, 1.206667f,
            1.386667f, 1.442222f, 1.497778f, 1.553333f,
            1.733333f, 1.788889f, 1.844444f, 1.900000f,
            2.080000f, 2.135556f, 2.191111f, 2.246667f,
            2.426667f, 2.482222f, 2.537778f, 2.593333f,
            2.773333f, 2.828889f, 2.884444f
    };

    // X: 右为正；Y: 下为正。这里已经是“枪械后坐力方向”，不是压枪方向。
    private static final float[] NEMESIS_RECOIL_X = new float[]{
            -0.004539f, -0.007566f, -0.025723f, -0.068091f, -0.116511f,
            -0.161905f, -0.189141f, -0.195194f, -0.149800f, -0.059012f,
             0.062038f,  0.107432f,  0.107432f,  0.095327f,  0.140721f,
             0.062038f,  0.052960f, -0.046907f, -0.134668f, -0.198522f,
            -0.243614f, -0.222430f, -0.122563f, -0.004539f,  0.052960f,
             0.170984f,  0.180062f,  0.122563f,  0.046907f, -0.052960f,
            -0.134668f, -0.198220f, -0.243614f, -0.273876f, -0.292034f
    };

    private static final float[] NEMESIS_RECOIL_Y = new float[]{
            -1.000000f, -0.959947f, -0.862038f, -0.773031f, -0.666222f,
            -0.568313f, -0.465955f, -0.390298f, -0.368046f, -0.376947f,
            -0.376947f, -0.225634f, -0.141077f, -0.109924f, -0.145527f,
            -0.096573f, -0.178905f, -0.149978f, -0.060970f, -0.001780f,
             0.072541f,  0.028037f,  0.005785f, -0.105474f, -0.034268f,
            -0.101024f, -0.020917f, -0.038718f, -0.005340f, -0.056520f,
            -0.018692f, -0.003115f,  0.036938f,  0.023587f, -0.003115f
    };

    private float weaponElapsedSec = 0f;
    private int weaponNextShot = 0;
    private boolean weaponTriggerWasDown = false;

    // 每发后坐力不再单帧瞬移，而是在短时间内平滑施加。
    // 这样保留每发总角度和轨迹，同时消除四连发“一格一格跳”的卡顿感。
    private static final float RECOIL_KICK_DURATION_SEC = 0.045f;
    private static final int MAX_ACTIVE_KICKS = 8;
    private final boolean[] kickActive = new boolean[MAX_ACTIVE_KICKS];
    private final float[] kickYawTotal = new float[MAX_ACTIVE_KICKS];
    private final float[] kickPitchTotal = new float[MAX_ACTIVE_KICKS];
    private final float[] kickAge = new float[MAX_ACTIVE_KICKS];
    private final float[] kickApplied = new float[MAX_ACTIVE_KICKS];

    // 压枪微调靶三层命中闪烁：1=中心红，2=第二层黄，3=第三层蓝。
    private int recoilFlashTier = 0;
    private float recoilFlashRemainingSec = 0f;
    private static final float RECOIL_FLASH_DURATION_SEC = 0.10f;

    public AimCanvasView(Context c, GameState s, ControllerInput i) {
        super(c);
        state = s;
        input = i;
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setTrainingListener(TrainingListener listener) {
        trainingListener = listener;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;

        // 命中提示音放到独立线程，避免ToneGenerator阻塞主线程造成跟枪掉帧。
        soundThread = new HandlerThread("AimHitSound");
        soundThread.start();
        soundHandler = new Handler(soundThread.getLooper());

        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);

        final Handler h = soundHandler;
        final HandlerThread t = soundThread;
        soundHandler = null;
        soundThread = null;

        if (h != null) {
            h.post(() -> {
                if (toneGenerator != null) {
                    try { toneGenerator.release(); } catch (Exception ignored) {}
                    toneGenerator = null;
                    toneVolume = -1;
                }
            });
        }
        if (t != null) t.quitSafely();

        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long now) {
        if (!running) return;
        float rawDt = lastNs == 0L ? 1f/60f : Math.max(0f, (now-lastNs)/1_000_000_000f);
        lastNs = now;

        // 旧版把dt强制截到0.05秒，低于20FPS时会让“固定速度”实际变慢。
        // 现在目标与镜头最多只防御极端>250ms卡顿；正常低帧率仍按真实经过时间推进。
        float simDt = Math.min(0.25f, rawDt);
        updateCamera(simDt);
        updateWeaponRecoil(simDt);
        if (recoilFlashRemainingSec > 0f) {
            recoilFlashRemainingSec = Math.max(0f, recoilFlashRemainingSec - simDt);
            if (recoilFlashRemainingSec <= 0f) recoilFlashTier = 0;
        }
        state.update(simDt);
        updateShootingFeedback();

        boolean wasTrainingActive = state.trainingActive;
        // 计时使用真实经过时间，不受渲染帧率影响。
        state.tickTraining60s(rawDt, trackingFireHit || arcFireHit || recoilFireHit);
        if (wasTrainingActive && !state.trainingActive && state.trainingFinished && trainingListener != null) {
            trainingListener.onTrainingFinished();
        }

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

    public void resetWeaponSimulation() {
        weaponElapsedSec = 0f;
        weaponNextShot = 0;
        weaponTriggerWasDown = false;
        clearActiveKicks();
        recoilFlashTier = 0;
        recoilFlashRemainingSec = 0f;
        state.resetWeaponRuntime();
    }

    private void clearActiveKicks() {
        for (int i = 0; i < MAX_ACTIVE_KICKS; i++) {
            kickActive[i] = false;
            kickYawTotal[i] = 0f;
            kickPitchTotal[i] = 0f;
            kickAge[i] = 0f;
            kickApplied[i] = 0f;
        }
    }

    private void updateWeaponRecoil(float dt) {
        boolean enabled = state.mode == GameState.Mode.RECOIL;
        boolean trigger = input.isShootPressed();

        if (!enabled) {
            if (weaponTriggerWasDown || weaponNextShot != 0 || state.weaponCurrentShot != 0) {
                resetWeaponSimulation();
            } else {
                clearActiveKicks();
            }
            return;
        }

        // 新一轮按下：从T1开始。松开后序列复位，但已经打出的那一发仍会平滑完成。
        if (trigger && !weaponTriggerWasDown) {
            weaponElapsedSec = 0f;
            weaponNextShot = 0;
            state.resetWeaponRuntime();
            weaponTriggerWasDown = true;
        } else if (trigger) {
            weaponElapsedSec += Math.max(0f, dt);
        }

        if (trigger) {
            while (weaponNextShot < NEMESIS_SHOT_TIME.length
                    && weaponElapsedSec + 0.0005f >= NEMESIS_SHOT_TIME[weaponNextShot]) {
                enqueueNemesisShot(weaponNextShot);
                weaponNextShot++;
            }
        }

        // 每帧都推进平滑后坐，即使用户刚松开扳机，已经发射的那一发也不会被截断。
        advanceActiveKicks(dt);

        if (!trigger && weaponTriggerWasDown) {
            weaponElapsedSec = 0f;
            weaponNextShot = 0;
            weaponTriggerWasDown = false;
            state.resetWeaponRuntime();
        }

        if (weaponNextShot >= NEMESIS_SHOT_TIME.length && !hasActiveKicks()) {
            state.weaponFiring = false;
        }
    }

    private void enqueueNemesisShot(int i) {
        // 子弹命中判定发生在这一发后坐力开始之前，符合“先出弹、随后枪口上跳”的手感。
        registerRecoilTargetHit();

        float mul = GameState.clamp(state.nemesisRecoilMultiplier, 0f, 3f);
        float deg = GameState.NEMESIS_BASE_DEG_PER_UNIT * mul;
        float yawKick = NEMESIS_RECOIL_X[i] * deg;
        float pitchKick = NEMESIS_RECOIL_Y[i] * deg;

        int slot = -1;
        for (int k = 0; k < MAX_ACTIVE_KICKS; k++) {
            if (!kickActive[k]) {
                slot = k;
                break;
            }
        }

        if (slot >= 0) {
            kickActive[slot] = true;
            kickYawTotal[slot] = yawKick;
            kickPitchTotal[slot] = pitchKick;
            kickAge[slot] = 0f;
            kickApplied[slot] = 0f;
        } else {
            // 极端掉帧时保证总后坐量不丢失。
            state.yaw = GameState.wrap180(state.yaw + yawKick);
            state.pitch = GameState.clamp(state.pitch + pitchKick, -88f, 88f);
        }

        state.weaponCurrentShot = i + 1;
        state.weaponMagazineRemaining = NEMESIS_RECOIL_X.length - (i + 1);
        state.weaponFiring = true;
    }

    private void registerRecoilTargetHit() {
        float error = state.getRecoilCenterErrorDeg();
        float outerRadius = Math.max(0.15f, state.recoilTargetAngularDiameter * 0.5f);
        float centerRadius = outerRadius / 3f;
        float secondRadius = outerRadius * 2f / 3f;

        if (error <= centerRadius) {
            recoilFlashTier = 1;
        } else if (error <= secondRadius) {
            recoilFlashTier = 2;
        } else if (error <= outerRadius) {
            recoilFlashTier = 3;
        } else {
            recoilFlashTier = 0;
        }

        recoilFlashRemainingSec = recoilFlashTier == 0 ? 0f : RECOIL_FLASH_DURATION_SEC;
    }

    private void advanceActiveKicks(float dt) {
        float d = Math.max(0f, dt);
        for (int i = 0; i < MAX_ACTIVE_KICKS; i++) {
            if (!kickActive[i]) continue;

            kickAge[i] += d;
            float t = GameState.clamp(kickAge[i] / RECOIL_KICK_DURATION_SEC, 0f, 1f);
            // smoothstep：起落都更柔和，但积分后仍严格等于这一发的总后坐角度。
            float eased = t * t * (3f - 2f * t);
            float delta = eased - kickApplied[i];
            kickApplied[i] = eased;

            state.yaw = GameState.wrap180(state.yaw + kickYawTotal[i] * delta);
            state.pitch = GameState.clamp(state.pitch + kickPitchTotal[i] * delta, -88f, 88f);

            if (t >= 1f) kickActive[i] = false;
        }
    }

    private boolean hasActiveKicks() {
        for (boolean active : kickActive) {
            if (active) return true;
        }
        return false;
    }

    private void updateShootingFeedback() {
        boolean shoot = input.isShootPressed();

        if (state.mode == GameState.Mode.TRACKING) {
            boolean nowHit = shoot && state.isCenterOnTrackingTarget();
            if (nowHit && !trackingFireHit) playHitTone();
            trackingFireHit = nowHit;
            arcFireHit = false;
            recoilFireHit = false;
            if (shoot && !prevShoot) state.shootAtCenter();

        } else if (state.mode == GameState.Mode.ARC) {
            // 弧线模式和跟枪模式使用同样的持续命中逻辑。
            boolean nowHit = shoot && state.isCenterOnArcTarget();
            if (nowHit && !arcFireHit) playHitTone();
            arcFireHit = nowHit;
            trackingFireHit = false;
            recoilFireHit = false;
            if (shoot && !prevShoot) state.shootAtCenter();

        } else if (state.mode == GameState.Mode.RECOIL) {
            // 压枪微调只用每发三层颜色反馈，不播放持续命中音，避免干扰微调与渲染节奏。
            boolean nowHit = shoot && state.weaponFiring && state.isCenterOnRecoilTarget();
            recoilFireHit = nowHit;
            trackingFireHit = false;
            arcFireHit = false;

        } else {
            trackingFireHit = false;
            arcFireHit = false;
            recoilFireHit = false;

            // 六目标保持“一次扳机打一发”的原逻辑。
            if (shoot && !prevShoot) {
                boolean hit = state.shootAtCenter();
                state.recordTrainingShot(hit);
                if (hit) playHitTone();
            }
        }

        prevShoot = shoot;
    }

    private void playHitTone() {
        if (!state.hitSoundEnabled) return;

        // 在目标边缘轻微抖动时，命中状态可能快速进出。
        // 140ms冷却防止连续创建/播放提示音造成音频和主线程压力。
        long now = SystemClock.uptimeMillis();
        if (now - lastHitToneMs < HIT_TONE_COOLDOWN_MS) return;
        lastHitToneMs = now;

        final int volume = (int)GameState.clamp(state.hitSoundVolumePct, 0f, 100f);
        final Handler h = soundHandler;
        if (h == null) return;

        h.post(() -> {
            try {
                if (toneGenerator == null || toneVolume != volume) {
                    if (toneGenerator != null) {
                        try { toneGenerator.release(); } catch (Exception ignored) {}
                    }
                    toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
                    toneVolume = volume;
                }
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 55);
            } catch (Exception ignored) {}
        });
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
            int arcColor = arcFireHit ? 0xff55e88a : 0xffffb347;
            drawTarget(canvas, state.arc, arcColor, state.arcTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.RECOIL) {
            drawRecoilTarget(canvas);
        } else {
            for (GameState.Target t : state.six) {
                drawTarget(canvas, t, 0xffff715c, state.sixTargetAngularDiameter);
            }
        }
    }

    private void drawRecoilTarget(Canvas canvas) {
        GameState.Target t = state.recoilTarget;
        float relYaw = shortest(t.yawDeg, state.yaw);
        float relPitch = t.pitchDeg - state.pitch;
        float hFov = GameState.clamp(state.fovDeg, 50f, 150f);
        float halfH = hFov * 0.5f;
        float aspect = Math.max(0.1f, getWidth() / (float)Math.max(1, getHeight()));
        double vFov = 2.0 * Math.atan(Math.tan(Math.toRadians(hFov) * 0.5) / aspect);
        float halfV = (float)Math.toDegrees(vFov) * 0.5f;
        if (Math.abs(relYaw) > halfH + 8f || Math.abs(relPitch) > halfV + 8f) return;

        float x = projectX(relYaw, halfH);
        float y = projectY(relPitch, halfV);
        float outerRadiusPx = (float)(Math.tan(Math.toRadians(Math.max(0.3f, state.recoilTargetAngularDiameter) * 0.5))
                * getWidth() * 0.5 / Math.tan(Math.toRadians(halfH)));
        outerRadiusPx = Math.max(12f, outerRadiusPx);
        float secondRadiusPx = outerRadiusPx * 2f / 3f;
        float centerRadiusPx = outerRadiusPx / 3f;

        // 三层静态微调靶。默认保持暗色，命中对应层后只闪该层。
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(recoilFlashTier == 3 ? 0xff318cff : 0xff26364a);
        canvas.drawCircle(x, y, outerRadiusPx, paint);

        paint.setColor(recoilFlashTier == 2 ? 0xffffd23f : 0xff354457);
        canvas.drawCircle(x, y, secondRadiusPx, paint);

        paint.setColor(recoilFlashTier == 1 ? 0xffff4b4b : 0xff465263);
        canvas.drawCircle(x, y, centerRadiusPx, paint);

        // 清晰的三层边界。
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xffd9e1eb);
        canvas.drawCircle(x, y, outerRadiusPx, paint);
        canvas.drawCircle(x, y, secondRadiusPx, paint);
        canvas.drawCircle(x, y, centerRadiusPx, paint);
        paint.setStyle(Paint.Style.FILL);
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
