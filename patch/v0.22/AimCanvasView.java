package com.openai.apexsticktrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.View;

public class AimCanvasView extends View implements Choreographer.FrameCallback {
    private final GameState state;
    private final ControllerInput input;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastNs;
    private boolean running;
    private boolean prevShoot;

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
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long now) {
        if (!running) return;
        float dt = lastNs == 0L ? 1f/60f : Math.min(0.05f, (now-lastNs)/1_000_000_000f);
        lastNs = now;
        updateCamera(dt);
        state.update(dt);
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
        boolean shoot = input.isShootPressed();
        if (shoot && !prevShoot) state.shootAtCenter();
        prevShoot = shoot;
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
        canvas.drawColor(0xff0e0f13);
        if (state.mode == GameState.Mode.TRACKING) {
            drawTarget(canvas, state.tracking, 0xffff493f, state.trackingTargetAngularDiameter);
        } else {
            for (GameState.Target t : state.six) {
                drawTarget(canvas, t, 0xffff644f, state.sixTargetAngularDiameter);
            }
        }
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

        float nx = (float)(Math.tan(Math.toRadians(relYaw))/Math.tan(Math.toRadians(halfH)));
        float ny = (float)(Math.tan(Math.toRadians(relPitch))/Math.tan(Math.toRadians(halfV)));
        float x = getWidth()*0.5f*(1f+nx);
        float y = getHeight()*0.5f*(1f+ny);
        float radius = (float)(Math.tan(Math.toRadians(Math.max(0.1f, angularDiameter)*0.5))
                * getWidth()*0.5 / Math.tan(Math.toRadians(halfH)));

        paint.setColor(color);
        canvas.drawCircle(x, y, Math.max(5f,radius), paint);
    }

    private static float shortest(float a,float b) {
        float d=a-b;
        while (d>180f) d-=360f;
        while (d<-180f) d+=360f;
        return d;
    }
}
