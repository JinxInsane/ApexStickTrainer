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

        p.setStrokeWidth(3f);
        p.setColor(0xffffffff);
        c.drawLine(cx-11,cy,cx-3,cy,p);
        c.drawLine(cx+3,cy,cx+11,cy,p);
        c.drawLine(cx,cy-11,cx,cy-3,p);
        c.drawLine(cx,cy+3,cx,cy+11,p);

        p.setTextSize(23f);
        String line1;
        if (state.mode == GameState.Mode.TRACKING) {
            String dir = state.getTrackingSpeedSigned() >= 0f ? ">" : "<";
            line1 = String.format(Locale.US,
                    "360跟枪  %s %.1f°/s  轨道 %.0f°  球 %.2f°  命中 %d/%d",
                    dir,
                    Math.abs(state.getTrackingSpeedSigned()),
                    state.getOrbitAngle360(),
                    state.trackingTargetAngularDiameter,
                    state.hits,
                    state.shots);
        } else {
            line1 = String.format(Locale.US,
                    "六目标  边界 %.0f×%.0f°  球 %.2f°  命中 %d/%d",
                    state.sixBoundaryWidthDeg,
                    state.sixBoundaryHeightDeg,
                    state.sixTargetAngularDiameter,
                    state.hits,
                    state.shots);
        }
        c.drawText(line1, 22f, 36f, p);

        p.setTextSize(17f);
        String line2;
        if (state.mode == GameState.Mode.TRACKING && state.speedMode == GameState.SpeedMode.RANDOM) {
            line2 = String.format(Locale.US,
                    "随机速 %.1f–%.1f°/s  反向权重 %.0f%%  变化权重 %.0f–%.0f%%  RX %.3f RY %.3f",
                    Math.min(state.randomSpeedMin,state.randomSpeedMax),
                    Math.max(state.randomSpeedMin,state.randomSpeedMax),
                    state.directionReverseWeightPct,
                    Math.min(state.speedChangeWeightMinPct,state.speedChangeWeightMaxPct),
                    Math.max(state.speedChangeWeightMinPct,state.speedChangeWeightMaxPct),
                    input.getRightX(),input.getRightY());
        } else {
            line2 = String.format(Locale.US,"HFOV %.0f  LIN %.2f  RX %.3f RY %.3f",
                    state.fovDeg,state.responseExponent,input.getRightX(),input.getRightY());
        }
        c.drawText(line2,22f,62f,p);

        invalidate();
    }
}
