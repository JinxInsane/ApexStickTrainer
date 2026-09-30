package com.openai.apexsticktrainer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "apex_stick_trainer_settings_v1";

    private final ControllerInput input = new ControllerInput();
    private final GameState state = new GameState();
    private SharedPreferences prefs;
    private AimCanvasView game;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadSettings();
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
            saveSettings();
        });
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2);
        mp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mp.setMargins(16, 0, 0, 16);
        root.addView(mode, mp);

        setContentView(root);
    }

    @Override protected void onPause() {
        saveSettings();
        super.onPause();
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

    private void loadSettings() {
        state.fovDeg = prefs.getFloat("fov", state.fovDeg);
        state.hipSensitivity = prefs.getFloat("hip", state.hipSensitivity);
        state.adsSensitivity = prefs.getFloat("ads", state.adsSensitivity);
        state.degreesPerSecondPerSensitivity = prefs.getFloat("dps_per_sens", state.degreesPerSecondPerSensitivity);
        state.responseExponent = prefs.getFloat("response_exp", state.responseExponent);
        state.deadzone = prefs.getFloat("deadzone", state.deadzone);

        state.fixedSpeed = prefs.getFloat("fixed_speed", state.fixedSpeed);
        state.randomSpeedMin = prefs.getFloat("random_speed_min", state.randomSpeedMin);
        state.randomSpeedMax = prefs.getFloat("random_speed_max", state.randomSpeedMax);
        state.directionReverseWeightPct = prefs.getFloat("reverse_weight", state.directionReverseWeightPct);
        state.speedChangeWeightMinPct = prefs.getFloat("speed_weight_min", state.speedChangeWeightMinPct);
        state.speedChangeWeightMaxPct = prefs.getFloat("speed_weight_max", state.speedChangeWeightMaxPct);
        state.speedChangeIntervalMinSec = prefs.getFloat("interval_min", state.speedChangeIntervalMinSec);
        state.speedChangeIntervalMaxSec = prefs.getFloat("interval_max", state.speedChangeIntervalMaxSec);
        state.trackingRadius = prefs.getFloat("tracking_radius", state.trackingRadius);
        state.trackingPitchDeg = prefs.getFloat("tracking_pitch", state.trackingPitchDeg);
        state.trackingTargetAngularDiameter = prefs.getFloat("tracking_target_size", state.trackingTargetAngularDiameter);

        state.sixTargetAngularDiameter = prefs.getFloat("six_target_size", state.sixTargetAngularDiameter);
        state.sixBoundaryWidthDeg = prefs.getFloat("six_bound_w", state.sixBoundaryWidthDeg);
        state.sixBoundaryHeightDeg = prefs.getFloat("six_bound_h", state.sixBoundaryHeightDeg);
        state.sixTargetDistance = prefs.getFloat("six_distance", state.sixTargetDistance);

        state.referenceStrengthPct = prefs.getFloat("reference_strength", state.referenceStrengthPct);
        state.backgroundBrightnessPct = prefs.getFloat("background_brightness", state.backgroundBrightnessPct);

        try {
            state.mode = GameState.Mode.valueOf(prefs.getString("mode", state.mode.name()));
        } catch (Exception ignored) {}
        try {
            state.speedMode = GameState.SpeedMode.valueOf(prefs.getString("speed_mode", state.speedMode.name()));
        } catch (Exception ignored) {}

        ControllerInput.AxisMap m = input.getMap();
        m.xAxis = prefs.getInt("cal_x_axis", m.xAxis);
        m.yAxis = prefs.getInt("cal_y_axis", m.yAxis);
        m.xCenter = prefs.getFloat("cal_x_center", m.xCenter);
        m.xRightRaw = prefs.getFloat("cal_x_right", m.xRightRaw);
        m.xLeftRaw = prefs.getFloat("cal_x_left", m.xLeftRaw);
        m.yCenter = prefs.getFloat("cal_y_center", m.yCenter);
        m.yDownRaw = prefs.getFloat("cal_y_down", m.yDownRaw);
        m.yUpRaw = prefs.getFloat("cal_y_up", m.yUpRaw);

        state.resetTargets();
    }

    private void saveSettings() {
        if (prefs == null) return;

        ControllerInput.AxisMap m = input.getMap();
        prefs.edit()
                .putFloat("fov", state.fovDeg)
                .putFloat("hip", state.hipSensitivity)
                .putFloat("ads", state.adsSensitivity)
                .putFloat("dps_per_sens", state.degreesPerSecondPerSensitivity)
                .putFloat("response_exp", state.responseExponent)
                .putFloat("deadzone", state.deadzone)

                .putFloat("fixed_speed", state.fixedSpeed)
                .putFloat("random_speed_min", state.randomSpeedMin)
                .putFloat("random_speed_max", state.randomSpeedMax)
                .putFloat("reverse_weight", state.directionReverseWeightPct)
                .putFloat("speed_weight_min", state.speedChangeWeightMinPct)
                .putFloat("speed_weight_max", state.speedChangeWeightMaxPct)
                .putFloat("interval_min", state.speedChangeIntervalMinSec)
                .putFloat("interval_max", state.speedChangeIntervalMaxSec)
                .putFloat("tracking_radius", state.trackingRadius)
                .putFloat("tracking_pitch", state.trackingPitchDeg)
                .putFloat("tracking_target_size", state.trackingTargetAngularDiameter)

                .putFloat("six_target_size", state.sixTargetAngularDiameter)
                .putFloat("six_bound_w", state.sixBoundaryWidthDeg)
                .putFloat("six_bound_h", state.sixBoundaryHeightDeg)
                .putFloat("six_distance", state.sixTargetDistance)

                .putFloat("reference_strength", state.referenceStrengthPct)
                .putFloat("background_brightness", state.backgroundBrightnessPct)

                .putString("mode", state.mode.name())
                .putString("speed_mode", state.speedMode.name())

                .putInt("cal_x_axis", m.xAxis)
                .putInt("cal_y_axis", m.yAxis)
                .putFloat("cal_x_center", m.xCenter)
                .putFloat("cal_x_right", m.xRightRaw)
                .putFloat("cal_x_left", m.xLeftRaw)
                .putFloat("cal_y_center", m.yCenter)
                .putFloat("cal_y_down", m.yDownRaw)
                .putFloat("cal_y_up", m.yUpRaw)
                .apply();
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

        header(box, "背景参考系");
        EditText refStrength = field("参考网格强度 %", state.referenceStrengthPct);
        EditText bgBrightness = field("背景亮度 %", state.backgroundBrightnessPct);
        box.addView(refStrength); box.addView(bgBrightness);
        box.addView(note(
                "参考系不是墙：它是固定在世界里的角度网格。你转动视角时网格会移动，" +
                "用来判断旋转方向和360°位置。0%可完全关闭。"
        ));

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
        EditText reverseWeight = field("反向权重 %（越低越少反向）", state.directionReverseWeightPct);
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
                "小球沿完整360°环形轨道持续绕玩家。反向权重只决定一次速度更新时是否改变方向；" +
                "速度变化权重会在MIN–MAX区间内随机抽取，所以不是固定变化幅度。"
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
        box.addView(note("校准结果也会自动保存。响应指数1.00 = y=x，不加速、不平滑。"));

        new AlertDialog.Builder(this)
                .setTitle("训练设置 v0.2.3")
                .setView(scroll)
                .setPositiveButton("保存并应用", (d, w) -> {
                    state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
                    state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));
                    state.adsSensitivity = Math.max(0.01f, parse(ads, state.adsSensitivity));
                    state.degreesPerSecondPerSensitivity = Math.max(1f, parse(perSens, state.degreesPerSecondPerSensitivity));
                    state.responseExponent = clamp(parse(exponent, state.responseExponent), 0.25f, 4f);

                    state.referenceStrengthPct = clamp(parse(refStrength, state.referenceStrengthPct), 0f, 100f);
                    state.backgroundBrightnessPct = clamp(parse(bgBrightness, state.backgroundBrightnessPct), 0f, 100f);

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
                    saveSettings();
                    Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
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

        saveSettings();

        new AlertDialog.Builder(this)
                .setTitle("校准完成并已保存")
                .setMessage("X轴=" + xAxis + "  Y轴=" + yAxis +
                        "\n中心→左右、中心→上下分别做线性映射。" +
                        "\n下次打开App会自动恢复。\n\n" + input.diagnostics())
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
