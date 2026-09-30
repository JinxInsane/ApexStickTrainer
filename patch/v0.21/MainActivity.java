package com.openai.apexsticktrainer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private final ControllerInput input = new ControllerInput();
    private final GameState state = new GameState();
    private AimCanvasView game;

    @Override
    protected void onCreate(Bundle b) {
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
                    ? GameState.Mode.SIX_TARGETS
                    : GameState.Mode.TRACKING;
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

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (input.onMotionEvent(e)) return true;
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (input.onKeyEvent(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        box.setPadding(pad, pad, pad, pad);

        EditText fov = field("水平 FOV", state.fovDeg);
        EditText hip = field("Hip 灵敏度（预设 4）", state.hipSensitivity);
        EditText ads = field("ADS 灵敏度（预设 3）", state.adsSensitivity);
        EditText perSens = field("每1档满杆转速 °/s", state.degreesPerSecondPerSensitivity);
        EditText exponent = field("响应指数（1.00=严格线性）", state.responseExponent);
        EditText min = field("跟枪随机最小速度 °/s", state.minSpeed);
        EditText max = field("跟枪随机最大速度 °/s", state.maxSpeed);
        EditText fixed = field("跟枪固定速度 °/s", state.fixedSpeed);
        EditText interval = field("随机速度变化间隔 秒", state.randomInterval);
        EditText radius = field("360跟枪半径", state.trackingRadius);
        EditText dia = field("目标角直径 °", state.targetAngularDiameter);
        EditText boundW = field("六目标虚拟边界宽度 °", state.sixBoundaryWidthDeg);
        EditText boundH = field("六目标虚拟边界高度 °", state.sixBoundaryHeightDeg);

        box.addView(fov); box.addView(hip); box.addView(ads); box.addView(perSens);
        box.addView(exponent); box.addView(min); box.addView(max); box.addView(fixed);
        box.addView(interval); box.addView(radius); box.addView(dia); box.addView(boundW); box.addView(boundH);

        Button speedMode = new Button(this);
        speedMode.setText("跟枪速度模式：" + (state.speedMode == GameState.SpeedMode.RANDOM ? "随机" : "固定"));
        speedMode.setOnClickListener(v -> {
            state.speedMode = state.speedMode == GameState.SpeedMode.RANDOM ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            speedMode.setText("跟枪速度模式：" + (state.speedMode == GameState.SpeedMode.RANDOM ? "随机" : "固定"));
        });
        box.addView(speedMode);

        Button calibrate = new Button(this);
        calibrate.setText("重新校准右摇杆");
        calibrate.setOnClickListener(v -> startCalibration());
        box.addView(calibrate);

        TextView note = new TextView(this);
        note.setText("响应指数 1.00 = y=x，不加速、不平滑。\n六目标边界是虚拟边界，不画墙。");
        note.setTextSize(12);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("训练设置")
                .setView(box)
                .setPositiveButton("应用", (d, w) -> {
                    state.fovDeg = parse(fov, state.fovDeg);
                    state.hipSensitivity = parse(hip, state.hipSensitivity);
                    state.adsSensitivity = parse(ads, state.adsSensitivity);
                    state.degreesPerSecondPerSensitivity = parse(perSens, state.degreesPerSecondPerSensitivity);
                    state.responseExponent = parse(exponent, state.responseExponent);
                    state.minSpeed = parse(min, state.minSpeed);
                    state.maxSpeed = parse(max, state.maxSpeed);
                    state.fixedSpeed = parse(fixed, state.fixedSpeed);
                    state.randomInterval = parse(interval, state.randomInterval);
                    state.trackingRadius = parse(radius, state.trackingRadius);
                    state.targetAngularDiameter = parse(dia, state.targetAngularDiameter);
                    state.sixBoundaryWidthDeg = parse(boundW, state.sixBoundaryWidthDeg);
                    state.sixBoundaryHeightDeg = parse(boundH, state.sixBoundaryHeightDeg);
                    state.resetTargets();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText field(String label, float value) {
        EditText e = new EditText(this);
        e.setHint(label);
        e.setText(String.valueOf(value));
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    private float parse(EditText e, float old) {
        try { return Float.parseFloat(e.getText().toString()); }
        catch (Exception x) { return old; }
    }

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
        Map<Integer, Float> center = s.get(0), right = s.get(1), left = s.get(2), up = s.get(3), down = s.get(4);
        int xAxis = findBestAxis(center, right, left);
        int yAxis = findBestAxis(center, down, up);

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
                val(center, xAxis), val(right, xAxis), val(left, xAxis),
                val(center, yAxis), val(down, yAxis), val(up, yAxis));

        new AlertDialog.Builder(this)
                .setTitle("校准完成")
                .setMessage("X轴=" + xAxis + "  Y轴=" + yAxis +
                        "\n中心→左右、中心→上下分别做线性映射。" +
                        "\n默认 0 死区 / 响应指数 1.00。\n\n" + input.diagnostics())
                .setPositiveButton("开始练", null)
                .show();
    }

    private int findBestAxis(Map<Integer, Float> center, Map<Integer, Float> pos, Map<Integer, Float> neg) {
        int best = -1; float score = 0f;
        for (Integer a : center.keySet()) {
            float p = val(pos, a) - val(center, a);
            float n = val(neg, a) - val(center, a);
            float sc = Math.abs(p - n);
            if (sc > score) { score = sc; best = a; }
        }
        return score > 0.15f ? best : -1;
    }

    private float val(Map<Integer, Float> m, int k) {
        Float v = m.get(k);
        return v == null ? 0f : v;
    }
}
