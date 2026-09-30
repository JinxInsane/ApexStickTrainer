package com.openai.apexsticktrainer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private final ControllerInput input = new ControllerInput();
    private final GameState state = new GameState();
    private AimCanvasView game;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        hideBars();

        FrameLayout root = new FrameLayout(this);
        game = new AimCanvasView(this, state, input);
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));

        HudView hud = new HudView(this, state, input);
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));

        Button settings = new Button(this);
        settings.setText("设置");
        settings.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2);
        sp.gravity = Gravity.TOP | Gravity.RIGHT;
        sp.setMargins(0, 16, 16, 0);
        root.addView(settings, sp);

        Button mode = new Button(this);
        mode.setText("跟枪 / 六目标");
        mode.setOnClickListener(v -> {
            state.mode = state.mode == GameState.Mode.TRACKING
                    ? GameState.Mode.SIX_TARGETS : GameState.Mode.TRACKING;
            state.resetTargets();
        });
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2);
        mp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mp.setMargins(16, 0, 0, 16);
        root.addView(mode, mp);

        setContentView(root);
    }

    private void hideBars() {
        Window w = getWindow();
        w.getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (input.onMotionEvent(e)) return true;
        return super.dispatchGenericMotionEvent(e);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (input.onKeyEvent(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box);

        header(box, "通用 / 摇杆");
        EditText fov = field("水平 FOV", state.fovDeg);
        EditText hip = field("Hip 灵敏度（预设 4）", state.hipSensitivity);
        EditText ads = field("ADS 灵敏度（预设 3）", state.adsSensitivity);
        EditText perSens = field("每1档满杆转速 °/s", state.degreesPerSecondPerSensitivity);
        EditText exponent = field("响应指数（1.00 = 严格线性）", state.responseExponent);
        box.addView(fov); box.addView(hip); box.addView(ads); box.addView(perSens); box.addView(exponent);

        header(box, "跟枪模式（独立设置）");
        EditText trackingSize = field("跟枪小球大小：角直径 °", state.trackingTargetAngularDiameter);
        EditText fixed = field("固定模式速度 °/s", state.fixedSpeed);
        box.addView(trackingSize); box.addView(fixed);

        Button speedMode = new Button(this);
        updateSpeedModeText(speedMode);
        speedMode.setOnClickListener(v -> {
            state.speedMode = state.speedMode == GameState.SpeedMode.RANDOM
                    ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            updateSpeedModeText(speedMode);
        });
        box.addView(speedMode);

        box.addView(subHeader("随机移动速度区间"));
        EditText speedMin = field("速度区间 MIN °/s", state.randomSpeedMin);
        EditText speedMax = field("速度区间 MAX °/s", state.randomSpeedMax);
        box.addView(speedMin); box.addView(speedMax);

        box.addView(subHeader("随机移动方向权重"));
        EditText reverseWeight = field("反向权重 %（越低越容易完整绕360°）", state.directionReverseWeightPct);
        box.addView(reverseWeight);

        box.addView(subHeader("随机移动速度变化权重区间"));
        EditText weightMin = field("变化权重区间 MIN %", state.speedChangeWeightMinPct);
        EditText weightMax = field("变化权重区间 MAX %", state.speedChangeWeightMaxPct);
        box.addView(weightMin); box.addView(weightMax);

        box.addView(subHeader("速度变化触发间隔区间"));
        EditText intervalMin = field("变化间隔 MIN 秒", state.speedChangeIntervalMinSec);
        EditText intervalMax = field("变化间隔 MAX 秒", state.speedChangeIntervalMaxSec);
        box.addView(intervalMin); box.addView(intervalMax);

        box.addView(note(
                "360°逻辑：小球沿环形轨道持续绕玩家，不在屏幕边缘反弹。\n" +
                "反向权重表示每次速度更新时发生反向的概率。默认10%，所以大多数时候会继续同方向绕圈。\n" +
                "速度变化权重是一个区间：每次更新会在 MIN–MAX 中随机抽权重，再把当前速度向新抽到的目标速度靠近。比如25%只变化四分之一，不会突然跳到新速度。"
        ));

        header(box, "六目标模式（独立设置）");
        EditText sixSize = field("六目标小球大小：角直径 °", state.sixTargetAngularDiameter);
        EditText boundW = field("虚拟边界宽度 °", state.sixBoundaryWidthDeg);
        EditText boundH = field("虚拟边界高度 °", state.sixBoundaryHeightDeg);
        box.addView(sixSize); box.addView(boundW); box.addView(boundH);
        box.addView(note("六目标只在不可见虚拟边界内刷新，不画墙；大小与跟枪模式完全分开。"));

        header(box, "手柄");
        Button calibrate = new Button(this);
        calibrate.setText("重新校准右摇杆");
        calibrate.setOnClickListener(v -> startCalibration());
        box.addView(calibrate);
        box.addView(note("响应指数 1.00 = y=x，不加速、不平滑。"));

        new AlertDialog.Builder(this)
                .setTitle("训练设置 v0.2.2")
                .setView(scroll)
                .setPositiveButton("应用", (d, w) -> {
                    state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
                    state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));
                    state.adsSensitivity = Math.max(0.01f, parse(ads, state.adsSensitivity));
                    state.degreesPerSecondPerSensitivity = Math.max(1f, parse(perSens, state.degreesPerSecondPerSensitivity));
                    state.responseExponent = clamp(parse(exponent, state.responseExponent), 0.25f, 4f);

                    state.trackingTargetAngularDiameter = clamp(parse(trackingSize, state.trackingTargetAngularDiameter), 0.2f, 20f);
                    state.fixedSpeed = Math.max(0.1f, parse(fixed, state.fixedSpeed));
                    state.randomSpeedMin = Math.max(0.1f, parse(speedMin, state.randomSpeedMin));
                    state.randomSpeedMax = Math.max(0.1f, parse(speedMax, state.randomSpeedMax));
                    state.directionReverseWeightPct = clamp(parse(reverseWeight, state.directionReverseWeightPct), 0f, 100f);
                    state.speedChangeWeightMinPct = clamp(parse(weightMin, state.speedChangeWeightMinPct), 0f, 100f);
                    state.speedChangeWeightMaxPct = clamp(parse(weightMax, state.speedChangeWeightMaxPct), 0f, 100f);
                    state.speedChangeIntervalMinSec = Math.max(0.08f, parse(intervalMin, state.speedChangeIntervalMinSec));
                    state.speedChangeIntervalMaxSec = Math.max(0.08f, parse(intervalMax, state.speedChangeIntervalMaxSec));

                    state.sixTargetAngularDiameter = clamp(parse(sixSize, state.sixTargetAngularDiameter), 0.2f, 20f);
                    state.sixBoundaryWidthDeg = Math.max(8f, parse(boundW, state.sixBoundaryWidthDeg));
                    state.sixBoundaryHeightDeg = Math.max(6f, parse(boundH, state.sixBoundaryHeightDeg));
                    state.resetTargets();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSpeedModeText(Button b) {
        b.setText("跟枪速度模式：" + (state.speedMode == GameState.SpeedMode.RANDOM ? "随机" : "固定"));
    }

    private void header(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(19f);
        t.setPadding(0, 20, 0, 8);
        box.addView(t);
    }

    private TextView subHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15f);
        t.setPadding(0, 12, 0, 2);
        return t;
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setPadding(0, 6, 0, 12);
        return t;
    }

    private EditText field(String label, float value) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setText(String.valueOf(value));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    private float parse(EditText e, float old) {
        try { return Float.parseFloat(e.getText().toString()); }
        catch (Exception x) { return old; }
    }

    private float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    private void startCalibration() {
        final String[] prompts = {
                "松开所有摇杆/扳机",
                "右摇杆向最右保持",
                "右摇杆向最左保持",
                "右摇杆向最上保持",
                "右摇杆向最下保持"
        };

        final List<Map<Integer, Float>> snaps = new ArrayList<>();
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("右摇杆严格线性校准")
                .setMessage(prompts[0] + "\n\n每一步保持到位后按“记录”。")
                .setPositiveButton("记录", null)
                .setNegativeButton("取消", null)
                .create();

        dlg.setOnShowListener(x -> {
            Button b = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
            final int[] step = {0};
            b.setOnClickListener(v -> {
                snaps.add(input.snapshotAxes());
                step[0]++;
                if (step[0] >= prompts.length) {
                    dlg.dismiss();
                    finishCalibration(snaps);
                } else {
                    dlg.setMessage(prompts[step[0]] + "\n\n保持到位后按“记录”。");
                }
            });
        });
        dlg.show();
    }

    private void finishCalibration(List<Map<Integer, Float>> s) {
        if (s.size() < 5) return;
        Map<Integer, Float> center=s.get(0), right=s.get(1), left=s.get(2), up=s.get(3), down=s.get(4);
        int xAxis=findBestAxis(center,right,left);
        int yAxis=findBestAxis(center,down,up);

        if (xAxis < 0 || yAxis < 0 || xAxis == yAxis) {
            new AlertDialog.Builder(this)
                    .setTitle("校准失败")
                    .setMessage("没有找到两个独立的右摇杆轴。\n请确认每一步只推动右摇杆。\n\n" + input.diagnostics())
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        input.applyCalibration(
                xAxis, yAxis,
                val(center,xAxis), val(right,xAxis), val(left,xAxis),
                val(center,yAxis), val(down,yAxis), val(up,yAxis));

        new AlertDialog.Builder(this)
                .setTitle("校准完成")
                .setMessage("X轴=" + xAxis + "  Y轴=" + yAxis +
                        "\n中心→左右、中心→上下分别做线性映射。" +
                        "\n默认0死区 / 响应指数1.00。\n\n" + input.diagnostics())
                .setPositiveButton("开始练", null)
                .show();
    }

    private int findBestAxis(Map<Integer, Float> center, Map<Integer, Float> pos, Map<Integer, Float> neg) {
        int best=-1; float score=0f;
        for (Integer a : center.keySet()) {
            float p=val(pos,a)-val(center,a);
            float n=val(neg,a)-val(center,a);
            float sc=Math.abs(p-n);
            if (sc>score) { score=sc; best=a; }
        }
        return score>0.15f ? best : -1;
    }

    private float val(Map<Integer, Float> m, int k) {
        Float v=m.get(k);
        return v==null ? 0f : v;
    }
}
