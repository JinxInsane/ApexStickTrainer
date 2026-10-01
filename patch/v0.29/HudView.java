package com.openai.apexsticktrainer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.Locale;

public class HudView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GameState state;
    private final ControllerInput input;

    public HudView(Context c, GameState s, ControllerInput i) {
        super(c);
        state = s;
        input = i;
        p.setTypeface(android.graphics.Typeface.MONOSPACE);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth()/2f;
        float cy = getHeight()/2f;

        p.setColor(0xffffffff);

        if (state.crosshairCrossVisible) {
            float gap = Math.max(0f, state.crosshairGapPx);
            float arm = Math.max(1f, state.crosshairArmLengthPx);
            float thick = Math.max(1f, state.crosshairThicknessPx);
            p.setStrokeWidth(thick);

            c.drawLine(cx-gap-arm, cy, cx-gap, cy, p);
            c.drawLine(cx+gap, cy, cx+gap+arm, cy, p);
            c.drawLine(cx, cy-gap-arm, cx, cy-gap, p);
            c.drawLine(cx, cy+gap, cx, cy+gap+arm, p);
        }

        if (state.crosshairDotVisible) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, Math.max(1f, state.crosshairDotRadiusPx), p);
        }

        p.setTextSize(22f);
        String line1;
        if (state.mode == GameState.Mode.TRACKING) {
            line1 = String.format(Locale.US,
                    "360球面跟枪  速度 %.1f°/s  yaw %.0f°  pitch %.0f°  球 %.2f°",
                    state.getTrackingSpeed(),
                    state.getOrbitAngle360(),
                    state.tracking.pitchDeg,
                    state.trackingTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.ARC) {
            line1 = String.format(Locale.US,
                    "弧线模式  速度 %.1f°/s  半径 %.1f°  yaw %.0f°  pitch %.0f°",
                    state.getArcSpeed(),
                    state.getArcRadiusDeg(),
                    state.getArcYaw360(),
                    state.arc.pitchDeg);
        } else {
            line1 = String.format(Locale.US,
                    "六目标  边界 %.0f×%.0f°  球 %.2f°  命中 %d/%d",
                    state.sixBoundaryWidthDeg,
                    state.sixBoundaryHeightDeg,
                    state.sixTargetAngularDiameter,
                    state.hits,
                    state.shots);
        }
        c.drawText(line1, 22f, 34f, p);

        p.setTextSize(16f);
        String line2;
        if (state.mode == GameState.Mode.TRACKING && state.speedMode == GameState.SpeedMode.RANDOM) {
            line2 = String.format(Locale.US,
                    "速度 %.1f–%.1f  方向权重 %.0f%%  速度变化权重 %.0f–%.0f%%  heading %.0f°",
                    Math.min(state.randomSpeedMin,state.randomSpeedMax),
                    Math.max(state.randomSpeedMin,state.randomSpeedMax),
                    state.directionChangeWeightPct,
                    Math.min(state.speedChangeWeightMinPct,state.speedChangeWeightMaxPct),
                    Math.max(state.speedChangeWeightMinPct,state.speedChangeWeightMaxPct),
                    state.getTrackingHeadingDeg());
        } else if (state.mode == GameState.Mode.ARC) {
            String turn = state.getArcTurnSign() > 0 ? "顺曲" : "逆曲";
            line2 = String.format(Locale.US,
                    "%s  速度 %.1f–%.1f  曲率随机 %.0f%%  半径 %.1f–%.1f°  heading %.0f°",
                    turn,
                    Math.min(state.arcRandomSpeedMin,state.arcRandomSpeedMax),
                    Math.max(state.arcRandomSpeedMin,state.arcRandomSpeedMax),
                    state.arcCurvatureChangeWeightPct,
                    Math.min(state.arcRadiusMinDeg,state.arcRadiusMaxDeg),
                    Math.max(state.arcRadiusMinDeg,state.arcRadiusMaxDeg),
                    state.getArcHeadingDeg());
        } else {
            line2 = String.format(Locale.US,
                    "HFOV %.0f  LIN %.2f  RX %.3f RY %.3f",
                    state.fovDeg,state.responseExponent,input.getRightX(),input.getRightY());
        }
        c.drawText(line2,22f,58f,p);

        invalidate();
    }
}
