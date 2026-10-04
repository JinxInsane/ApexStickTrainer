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
        if (state.mode == GameState.Mode.TRACKING) {
            if (state.speedMode == GameState.SpeedMode.RANDOM) {
                line2 = String.format(Locale.US,
                        "随机 %.1f–%.1f°/s  当前 %.1f°/s  方向权重 %.0f%%  heading %.0f°",
                        Math.min(state.randomSpeedMin,state.randomSpeedMax),
                        Math.max(state.randomSpeedMin,state.randomSpeedMax),
                        state.getTrackingSpeed(),
                        state.directionChangeWeightPct,
                        state.getTrackingHeadingDeg());
            } else {
                line2 = String.format(Locale.US,
                        "固定球面恒速 %.1f°/s  当前 %.1f°/s  heading %.0f°",
                        state.fixedSpeed,
                        state.getTrackingSpeed(),
                        state.getTrackingHeadingDeg());
            }
        } else if (state.mode == GameState.Mode.ARC) {
            String turn = state.getArcTurnSign() > 0 ? "顺曲" : "逆曲";
            if (state.arcSpeedMode == GameState.SpeedMode.RANDOM) {
                line2 = String.format(Locale.US,
                        "%s  随机 %.1f–%.1f°/s  当前 %.1f°/s  半径 %.1f°",
                        turn,
                        Math.min(state.arcRandomSpeedMin,state.arcRandomSpeedMax),
                        Math.max(state.arcRandomSpeedMin,state.arcRandomSpeedMax),
                        state.getArcSpeed(),
                        state.getArcRadiusDeg());
            } else {
                line2 = String.format(Locale.US,
                        "%s  固定 %.1f°/s  当前 %.1f°/s  半径 %.1f°",
                        turn,
                        state.arcFixedSpeed,
                        state.getArcSpeed(),
                        state.getArcRadiusDeg());
            }
        } else {
            line2 = String.format(Locale.US,
                    "HFOV %.0f  LIN %.2f  RX %.3f RY %.3f",
                    state.fovDeg,state.responseExponent,input.getRightX(),input.getRightY());
        }
        c.drawText(line2,22f,58f,p);

        float trainingY = 84f;
        if (state.mode == GameState.Mode.TRACKING && state.weaponSimulationEnabled) {
            p.setTextSize(16f);
            String weaponState;
            if (state.weaponCurrentShot <= 0) {
                weaponState = "待机";
            } else if (state.weaponMagazineRemaining <= 0) {
                weaponState = "弹匣结束";
            } else if (state.weaponFiring) {
                weaponState = "开火";
            } else {
                weaponState = "停火";
            }
            String weaponLine = String.format(Locale.US,
                    "复仇女神实验  T%d/35  剩余%d  后坐×%.2f  %s",
                    state.weaponCurrentShot,
                    state.weaponMagazineRemaining,
                    state.nemesisRecoilMultiplier,
                    weaponState);
            c.drawText(weaponLine, 22f, 82f, p);
            trainingY = 108f;
        }

        if (state.trainingActive || state.trainingFinished) {
            p.setTextSize(18f);
            String trainingLine;
            String prefix = state.trainingFinished ? "60秒完成" : "60秒计分";
            if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS) {
                trainingLine = String.format(Locale.US,
                        "%s  剩余 %.1fs  得分 %d  命中 %d/%d  准确率 %.0f%%",
                        prefix,
                        state.trainingRemainingSec,
                        state.trainingScore,
                        state.trainingHits,
                        state.trainingShots,
                        state.getTrainingAccuracyPct());
            } else {
                trainingLine = String.format(Locale.US,
                        "%s  剩余 %.1fs  得分 %d  有效跟枪 %.2fs",
                        prefix,
                        state.trainingRemainingSec,
                        state.trainingScore,
                        state.trainingHitTimeSec);
            }
            c.drawText(trainingLine, 22f, trainingY, p);
        }

        invalidate();
    }
}
