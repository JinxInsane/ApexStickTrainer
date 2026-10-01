package com.openai.apexsticktrainer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
        updateModeButtonText(mode);
        mode.setOnClickListener(v -> {
            if (state.mode == GameState.Mode.TRACKING) {
                state.mode = GameState.Mode.SIX_TARGETS;
            } else if (state.mode == GameState.Mode.SIX_TARGETS) {
                state.mode = GameState.Mode.ARC;
            } else {
                state.mode = GameState.Mode.TRACKING;
            }
            state.resetTargets();
            updateModeButtonText(mode);
            saveSettingsSync();
        });
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2);
        mp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mp.setMargins(16, 0, 0, 16);
        root.addView(mode, mp);

        setContentView(root);
    }

    @Override protected void onPause() {
        saveSettingsSync();
        super.onPause();
    }

    private void hideBars() {
        Window w = getWindow();
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
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
        if (prefs.contains("direction_change_weight")) {
            state.directionChangeWeightPct = prefs.getFloat("direction_change_weight", state.directionChangeWeightPct);
        } else {
            state.directionChangeWeightPct = prefs.getFloat("reverse_weight", state.directionChangeWeightPct);
        }
        state.directionReverseWeightPct = state.directionChangeWeightPct;

        state.speedChangeWeightMinPct = prefs.getFloat("speed_weight_min", state.speedChangeWeightMinPct);
        state.speedChangeWeightMaxPct = prefs.getFloat("speed_weight_max", state.speedChangeWeightMaxPct);
        state.speedChangeIntervalMinSec = prefs.getFloat("interval_min", state.speedChangeIntervalMinSec);
        state.speedChangeIntervalMaxSec = prefs.getFloat("interval_max", state.speedChangeIntervalMaxSec);
        state.trackingRadius = prefs.getFloat("tracking_radius", state.trackingRadius);
        state.trackingPitchLimitDeg = prefs.getFloat("tracking_pitch_limit", state.trackingPitchLimitDeg);
        state.trackingTargetAngularDiameter = prefs.getFloat("tracking_target_size", state.trackingTargetAngularDiameter);

        state.arcFixedSpeed = prefs.getFloat("arc_fixed_speed", state.arcFixedSpeed);
        state.arcRandomSpeedMin = prefs.getFloat("arc_random_speed_min", state.arcRandomSpeedMin);
        state.arcRandomSpeedMax = prefs.getFloat("arc_random_speed_max", state.arcRandomSpeedMax);
        state.arcDirectionChangeWeightPct = prefs.getFloat("arc_direction_weight", state.arcDirectionChangeWeightPct);
        state.arcSpeedChangeWeightMinPct = prefs.getFloat("arc_speed_weight_min", state.arcSpeedChangeWeightMinPct);
        state.arcSpeedChangeWeightMaxPct = prefs.getFloat("arc_speed_weight_max", state.arcSpeedChangeWeightMaxPct);
        state.arcEventIntervalMinSec = prefs.getFloat("arc_interval_min", state.arcEventIntervalMinSec);
        state.arcEventIntervalMaxSec = prefs.getFloat("arc_interval_max", state.arcEventIntervalMaxSec);
        state.arcPitchLimitDeg = prefs.getFloat("arc_pitch_limit", state.arcPitchLimitDeg);
        state.arcTargetAngularDiameter = prefs.getFloat("arc_target_size", state.arcTargetAngularDiameter);
        state.arcRadiusMinDeg = prefs.getFloat("arc_radius_min", state.arcRadiusMinDeg);
        state.arcRadiusMaxDeg = prefs.getFloat("arc_radius_max", state.arcRadiusMaxDeg);
        state.arcCurvatureChangeWeightPct = prefs.getFloat("arc_curvature_weight", state.arcCurvatureChangeWeightPct);
        state.arcCurvatureBlendMinPct = prefs.getFloat("arc_curvature_blend_min", state.arcCurvatureBlendMinPct);
        state.arcCurvatureBlendMaxPct = prefs.getFloat("arc_curvature_blend_max", state.arcCurvatureBlendMaxPct);
        try {
            state.arcSpeedMode = GameState.SpeedMode.valueOf(
                    prefs.getString("arc_speed_mode", state.arcSpeedMode.name()));
        } catch (Exception ignored) {}

        state.sixTargetAngularDiameter = prefs.getFloat("six_target_size", state.sixTargetAngularDiameter);
        state.sixBoundaryWidthDeg = prefs.getFloat("six_bound_w", state.sixBoundaryWidthDeg);
        state.sixBoundaryHeightDeg = prefs.getFloat("six_bound_h", state.sixBoundaryHeightDeg);
        state.sixTargetDistance = prefs.getFloat("six_distance", state.sixTargetDistance);

        state.referenceStrengthPct = prefs.getFloat("reference_strength", state.referenceStrengthPct);
        state.backgroundBrightnessPct = prefs.getFloat("background_brightness", state.backgroundBrightnessPct);

        state.crosshairCrossVisible = prefs.getBoolean("crosshair_cross_visible", state.crosshairCrossVisible);
        state.crosshairDotVisible = prefs.getBoolean("crosshair_dot_visible", state.crosshairDotVisible);
        state.crosshairGapPx = prefs.getFloat("crosshair_gap_px", state.crosshairGapPx);
        state.crosshairArmLengthPx = prefs.getFloat("crosshair_arm_px", state.crosshairArmLengthPx);
        state.crosshairThicknessPx = prefs.getFloat("crosshair_thickness_px", state.crosshairThicknessPx);
        state.crosshairDotRadiusPx = prefs.getFloat("crosshair_dot_radius_px", state.crosshairDotRadiusPx);
        state.hitSoundEnabled = prefs.getBoolean("hit_sound_enabled", state.hitSoundEnabled);
        state.hitSoundVolumePct = prefs.getFloat("hit_sound_volume", state.hitSoundVolumePct);

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

    private boolean saveSettingsSync() {
        if (prefs == null) return false;

        ControllerInput.AxisMap m = input.getMap();
        int nextRevision = prefs.getInt("save_revision", 0) + 1;

        SharedPreferences.Editor e = prefs.edit()
                .putInt("save_revision", nextRevision)
                .putLong("saved_at", System.currentTimeMillis())
                .putFloat("fov", state.fovDeg)
                .putFloat("hip", state.hipSensitivity)
                .putFloat("ads", state.adsSensitivity)
                .putFloat("dps_per_sens", state.degreesPerSecondPerSensitivity)
                .putFloat("response_exp", state.responseExponent)
                .putFloat("deadzone", state.deadzone)

                .putFloat("fixed_speed", state.fixedSpeed)
                .putFloat("random_speed_min", state.randomSpeedMin)
                .putFloat("random_speed_max", state.randomSpeedMax)
                .putFloat("direction_change_weight", state.directionChangeWeightPct)
                .putFloat("reverse_weight", state.directionChangeWeightPct)
                .putFloat("speed_weight_min", state.speedChangeWeightMinPct)
                .putFloat("speed_weight_max", state.speedChangeWeightMaxPct)
                .putFloat("interval_min", state.speedChangeIntervalMinSec)
                .putFloat("interval_max", state.speedChangeIntervalMaxSec)
                .putFloat("tracking_radius", state.trackingRadius)
                .putFloat("tracking_pitch_limit", state.trackingPitchLimitDeg)
                .putFloat("tracking_target_size", state.trackingTargetAngularDiameter)

                .putFloat("arc_fixed_speed", state.arcFixedSpeed)
                .putFloat("arc_random_speed_min", state.arcRandomSpeedMin)
                .putFloat("arc_random_speed_max", state.arcRandomSpeedMax)
                .putFloat("arc_direction_weight", state.arcDirectionChangeWeightPct)
                .putFloat("arc_speed_weight_min", state.arcSpeedChangeWeightMinPct)
                .putFloat("arc_speed_weight_max", state.arcSpeedChangeWeightMaxPct)
                .putFloat("arc_interval_min", state.arcEventIntervalMinSec)
                .putFloat("arc_interval_max", state.arcEventIntervalMaxSec)
                .putFloat("arc_pitch_limit", state.arcPitchLimitDeg)
                .putFloat("arc_target_size", state.arcTargetAngularDiameter)
                .putFloat("arc_radius_min", state.arcRadiusMinDeg)
                .putFloat("arc_radius_max", state.arcRadiusMaxDeg)
                .putFloat("arc_curvature_weight", state.arcCurvatureChangeWeightPct)
                .putFloat("arc_curvature_blend_min", state.arcCurvatureBlendMinPct)
                .putFloat("arc_curvature_blend_max", state.arcCurvatureBlendMaxPct)
                .putString("arc_speed_mode", state.arcSpeedMode.name())

                .putFloat("six_target_size", state.sixTargetAngularDiameter)
                .putFloat("six_bound_w", state.sixBoundaryWidthDeg)
                .putFloat("six_bound_h", state.sixBoundaryHeightDeg)
                .putFloat("six_distance", state.sixTargetDistance)

                .putFloat("reference_strength", state.referenceStrengthPct)
                .putFloat("background_brightness", state.backgroundBrightnessPct)

                .putBoolean("crosshair_cross_visible", state.crosshairCrossVisible)
                .putBoolean("crosshair_dot_visible", state.crosshairDotVisible)
                .putFloat("crosshair_gap_px", state.crosshairGapPx)
                .putFloat("crosshair_arm_px", state.crosshairArmLengthPx)
                .putFloat("crosshair_thickness_px", state.crosshairThicknessPx)
                .putFloat("crosshair_dot_radius_px", state.crosshairDotRadiusPx)
                .putBoolean("hit_sound_enabled", state.hitSoundEnabled)
                .putFloat("hit_sound_volume", state.hitSoundVolumePct)

                .putString("mode", state.mode.name())
                .putString("speed_mode", state.speedMode.name())

                .putInt("cal_x_axis", m.xAxis)
                .putInt("cal_y_axis", m.yAxis)
                .putFloat("cal_x_center", m.xCenter)
                .putFloat("cal_x_right", m.xRightRaw)
                .putFloat("cal_x_left", m.xLeftRaw)
                .putFloat("cal_y_center", m.yCenter)
                .putFloat("cal_y_down", m.yDownRaw)
                .putFloat("cal_y_up", m.yUpRaw);

        boolean ok = e.commit();
        if (!ok) return false;

        // Immediate read-back makes persistence failure visible instead of silently pretending.
        return prefs.getInt("save_revision", -1) == nextRevision
                && Math.abs(prefs.getFloat("fov", -999f) - state.fovDeg) < 0.001f;
    }

    private void showSettings() {
        final Dialog dlg = new Dialog(this);
        dlg.setTitle("训练设置 v0.2.9");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setFocusableInTouchMode(true);

        TextView saveStatus = new TextView(this);
        saveStatus.setTextSize(14f);
        saveStatus.setPadding(8, 4, 8, 6);
        updateSaveStatus(saveStatus);
        root.addView(saveStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button hideKb = new Button(this);
        hideKb.setText("收起键盘");
        actions.addView(hideKb, new LinearLayout.LayoutParams(0, -2, 1f));

        Button save = new Button(this);
        save.setText("保存");
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));

        Button close = new Button(this);
        close.setText("关闭");
        actions.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));

        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 4, 12, 28);
        scroll.addView(box);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        header(box, "通用 / 摇杆");
        box.addView(note("数值单位说明：° = 角度，°/s = 每秒转过多少度，% = 概率或比例。所有灵敏度都只影响镜头，不会给目标加辅助瞄准。"));

        final EditText fov = field("水平 FOV", state.fovDeg);
        addSetting(box, fov, "水平视野范围。数值越大，一屏看到的范围越宽，但目标会显得更小、屏幕边缘透视拉伸更明显。只改变画面投影，不直接改变摇杆输入。");

        final EditText hip = field("Hip 灵敏度", state.hipSensitivity);
        addSetting(box, hip, "腰射状态的灵敏度倍率。满杆实际转速 ≈ Hip灵敏度 × 每1档满杆转速。例如 4 × 60 = 240°/s。");

        final EditText ads = field("ADS 灵敏度", state.adsSensitivity);
        addSetting(box, ads, "按住ADS/左扳机时使用的灵敏度倍率。数值通常低于Hip，用来模拟开镜后的更慢镜头速度。");

        final EditText perSens = field("每1档满杆转速 °/s", state.degreesPerSecondPerSensitivity);
        addSetting(box, perSens, "决定灵敏度数字对应多少实际转速。它是基础换算值：满杆转速 = 当前灵敏度 × 这个数。当前并不是Apex官方内部参数，只是训练器自己的标尺。");

        final EditText exponent = field("响应指数（1.00=严格线性）", state.responseExponent);
        addSetting(box, exponent, "摇杆输入曲线。1.00 = 完全线性；大于1时小幅推杆更慢、满杆附近更快；小于1时中心区域更敏感。你现在觉得线性正常，就保持1.00。");

        final EditText deadzone = field("死区（0.00=无死区）", state.deadzone);
        addSetting(box, deadzone, "忽略摇杆中心附近的小输入。0 = 无死区；数值越大越不容易漂移，但也会损失微小跟枪操作。建议没有漂移就保持0。");

        header(box, "背景参考系");
        final EditText refStrength = field("参考网格强度 %", state.referenceStrengthPct);
        addSetting(box, refStrength, "控制世界角度网格、方向线和刻度的可见程度。0% = 完全关闭；100% = 最明显。它只帮助判断旋转方向，不影响目标运动。");

        final EditText bgBrightness = field("背景亮度 %", state.backgroundBrightnessPct);
        addSetting(box, bgBrightness, "控制纯背景的亮度。数值越高越不黑，但太高会降低红色目标的对比度。");


        header(box, "准心 / 命中反馈");
        box.addView(note("准心只负责显示和命中判定中心，不会吸附目标、不会改变摇杆速度，也没有辅助瞄准。跟枪模式的有效反馈条件是：准心中心在小球范围内 + 扳机保持按下。"));

        final boolean[] pendingCrossVisible = { state.crosshairCrossVisible };
        Button crossVisibleButton = new Button(this);
        updateToggleButton(crossVisibleButton, "十字准心", pendingCrossVisible[0]);
        crossVisibleButton.setOnClickListener(v -> {
            pendingCrossVisible[0] = !pendingCrossVisible[0];
            updateToggleButton(crossVisibleButton, "十字准心", pendingCrossVisible[0]);
        });
        box.addView(crossVisibleButton);

        final boolean[] pendingDotVisible = { state.crosshairDotVisible };
        Button dotVisibleButton = new Button(this);
        updateToggleButton(dotVisibleButton, "中心点", pendingDotVisible[0]);
        dotVisibleButton.setOnClickListener(v -> {
            pendingDotVisible[0] = !pendingDotVisible[0];
            updateToggleButton(dotVisibleButton, "中心点", pendingDotVisible[0]);
        });
        box.addView(dotVisibleButton);

        final EditText crossGap = field("十字准心间距 px", state.crosshairGapPx);
        addSetting(box, crossGap, "中心到四条十字线起点的距离。数值越大，中间空隙越大；0表示四条线贴近中心。");

        final EditText crossArm = field("十字准心线长 px", state.crosshairArmLengthPx);
        addSetting(box, crossArm, "每一条十字臂的长度。只改变准心外观，不改变命中范围。");

        final EditText crossThickness = field("十字准心线宽 px", state.crosshairThicknessPx);
        addSetting(box, crossThickness, "十字线粗细。建议1–4px。");

        final EditText dotRadius = field("中心点半径 px", state.crosshairDotRadiusPx);
        addSetting(box, dotRadius, "中心点大小。中心点显示关闭时这个数值仍会保存。");

        final boolean[] pendingHitSound = { state.hitSoundEnabled };
        Button hitSoundButton = new Button(this);
        updateToggleButton(hitSoundButton, "命中提示音", pendingHitSound[0]);
        hitSoundButton.setOnClickListener(v -> {
            pendingHitSound[0] = !pendingHitSound[0];
            updateToggleButton(hitSoundButton, "命中提示音", pendingHitSound[0]);
        });
        box.addView(hitSoundButton);

        final EditText hitSoundVolume = field("命中提示音音量 %", state.hitSoundVolumePct);
        addSetting(box, hitSoundVolume, "0–100%。跟枪：准心进入目标且扳机按下时响一次，持续跟住时不会连续蜂鸣；滑出后再次跟住会再响一次。六目标：真正打掉一个目标时响一次。");

        header(box, "跟枪模式");
        final EditText trackingSize = field("跟枪小球角直径 °", state.trackingTargetAngularDiameter);
        addSetting(box, trackingSize, "目标的角直径。数值越大，屏幕上的球越大；这是角度大小，不是像素大小，所以FOV变化时视觉尺寸也会相应变化。");

        final EditText fixed = field("固定速度 °/s", state.fixedSpeed);
        addSetting(box, fixed, "仅在“固定速度”模式下使用。表示目标沿球面移动的角速度，数值越大目标越快。");

        final EditText pitchLimit = field("球面垂直范围 ±°", state.trackingPitchLimitDeg);
        addSetting(box, pitchLimit, "限制目标上下移动范围。例如60表示目标可以到水平线以上60°或以下60°。数值越大，上下运动幅度越大。");

        final GameState.SpeedMode[] pendingSpeedMode = { state.speedMode };
        Button speedMode = new Button(this);
        updateSpeedModeText(speedMode, pendingSpeedMode[0]);
        speedMode.setOnClickListener(v -> {
            pendingSpeedMode[0] = pendingSpeedMode[0] == GameState.SpeedMode.RANDOM
                    ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            updateSpeedModeText(speedMode, pendingSpeedMode[0]);
        });
        box.addView(speedMode);
        box.addView(note("随机 = 速度会在区间内动态变化，并可能按方向权重改变运动方向；固定 = 始终使用上面的固定速度，不做随机速度变化。"));

        box.addView(subHeader("随机移动速度区间"));
        final EditText speedMin = field("速度区间 MIN °/s", state.randomSpeedMin);
        addSetting(box, speedMin, "随机模式允许出现的最低目标速度。目标速度不会低于这个值。");
        final EditText speedMax = field("速度区间 MAX °/s", state.randomSpeedMax);
        addSetting(box, speedMax, "随机模式允许出现的最高目标速度。程序没有强制最大上限；训练时通常10–180°/s已经覆盖从慢速到高速跟枪。");

        box.addView(subHeader("随机移动方向权重"));
        final EditText directionWeight = field("方向变化权重 %（越低越少变向）", state.directionChangeWeightPct);
        addSetting(box, directionWeight, "每次随机事件发生时“改变运动方向”的概率。0% = 永远保持当前方向；10% = 大约每10次事件有1次变向；100% = 每次事件都变向。它控制的是变向频率，不是转弯幅度。");

        box.addView(subHeader("随机移动速度变化权重区间"));
        final EditText weightMin = field("变化权重区间 MIN %", state.speedChangeWeightMinPct);
        addSetting(box, weightMin, "每次变速时，至少把当前速度向新随机速度靠近多少比例。数值越低，速度变化越柔和。");
        final EditText weightMax = field("变化权重区间 MAX %", state.speedChangeWeightMaxPct);
        addSetting(box, weightMax, "每次变速时，最多靠近新随机速度多少比例。例如20–50%，程序每次会随机抽一个20%到50%的变化量，不会固定跳变。");

        box.addView(subHeader("随机事件间隔区间"));
        final EditText intervalMin = field("间隔 MIN 秒", state.speedChangeIntervalMinSec);
        addSetting(box, intervalMin, "两次随机事件之间最短等待时间。越小，速度/方向检查越频繁。");
        final EditText intervalMax = field("间隔 MAX 秒", state.speedChangeIntervalMaxSec);
        addSetting(box, intervalMax, "两次随机事件之间最长等待时间。程序会在MIN到MAX之间随机等待，避免机械地固定节奏变化。");

        box.addView(note(
                "这版跟枪不再固定在同一高度横着扫。目标在玩家周围球面上运动，yaw可完整360°，pitch也会变化；" +
                "到垂直范围边缘时反射，所以画面会有斜向、弧向和上下分量。"
        ));


        header(box, "弧线模式（独立设置）");
        box.addView(note("弧线模式保留跟枪模式同级的控制：小球大小、固定/随机速度、速度区间、方向变化权重、速度变化权重区间、随机事件间隔区间、垂直范围；另外增加弧线半径与曲率随机控制。这个模式的目标每一帧都在转弯，因此不会退化成直线运动。"));

        final EditText arcSize = field("弧线小球角直径 °", state.arcTargetAngularDiameter);
        addSetting(box, arcSize, "只影响弧线模式的小球大小，与跟枪和六目标完全独立。");

        final EditText arcFixed = field("弧线固定速度 °/s", state.arcFixedSpeed);
        addSetting(box, arcFixed, "弧线速度模式设为固定时使用。表示目标沿曲线前进的角速度。");

        final EditText arcPitchLimit = field("弧线垂直范围 ±°", state.arcPitchLimitDeg);
        addSetting(box, arcPitchLimit, "限制弧线目标上下活动范围。到边缘时会平滑反射，仍保持曲线运动。");

        final GameState.SpeedMode[] pendingArcSpeedMode = { state.arcSpeedMode };
        Button arcSpeedModeButton = new Button(this);
        updateArcSpeedModeText(arcSpeedModeButton, pendingArcSpeedMode[0]);
        arcSpeedModeButton.setOnClickListener(v -> {
            pendingArcSpeedMode[0] = pendingArcSpeedMode[0] == GameState.SpeedMode.RANDOM
                    ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            updateArcSpeedModeText(arcSpeedModeButton, pendingArcSpeedMode[0]);
        });
        box.addView(arcSpeedModeButton);
        box.addView(note("随机 = 速度会在下面的MIN–MAX区间内变化；固定 = 速度保持固定，但弧线半径/曲率随机仍可继续生效。"));

        box.addView(subHeader("弧线随机速度区间"));
        final EditText arcSpeedMin = field("弧线速度 MIN °/s", state.arcRandomSpeedMin);
        addSetting(box, arcSpeedMin, "弧线随机速度的最低值。");
        final EditText arcSpeedMax = field("弧线速度 MAX °/s", state.arcRandomSpeedMax);
        addSetting(box, arcSpeedMax, "弧线随机速度的最高值。与跟枪模式相互独立。");

        box.addView(subHeader("弧线方向变化权重"));
        final EditText arcDirectionWeight = field("弧线方向变化权重 %", state.arcDirectionChangeWeightPct);
        addSetting(box, arcDirectionWeight, "每次随机事件时切换顺/逆时针弯曲方向的概率。数值低时，会长时间保持同一方向绕弧；数值高时更容易形成S形变化。切换不会让目标瞬移。");

        box.addView(subHeader("弧线速度变化权重区间"));
        final EditText arcSpeedWeightMin = field("弧线速度变化权重 MIN %", state.arcSpeedChangeWeightMinPct);
        addSetting(box, arcSpeedWeightMin, "一次变速至少向新随机速度靠近多少比例。");
        final EditText arcSpeedWeightMax = field("弧线速度变化权重 MAX %", state.arcSpeedChangeWeightMaxPct);
        addSetting(box, arcSpeedWeightMax, "一次变速最多向新随机速度靠近多少比例。程序每次从MIN–MAX中随机抽取。");

        box.addView(subHeader("弧线随机事件间隔区间"));
        final EditText arcIntervalMin = field("弧线事件间隔 MIN 秒", state.arcEventIntervalMinSec);
        addSetting(box, arcIntervalMin, "速度、方向、曲率随机检查之间的最短等待时间。");
        final EditText arcIntervalMax = field("弧线事件间隔 MAX 秒", state.arcEventIntervalMaxSec);
        addSetting(box, arcIntervalMax, "随机检查之间的最长等待时间。实际每次会在MIN–MAX之间随机等待。");

        box.addView(subHeader("弧线半径区间（决定弧度/曲率）"));
        final EditText arcRadiusMin = field("弧线半径 MIN °", state.arcRadiusMinDeg);
        addSetting(box, arcRadiusMin, "允许出现的最紧弧线半径。数值越小，转弯越急、圆越小；建议不要低于5°。");
        final EditText arcRadiusMax = field("弧线半径 MAX °", state.arcRadiusMaxDeg);
        addSetting(box, arcRadiusMax, "允许出现的最宽弧线半径。数值越大，弧线越平缓；但无论多大，程序仍持续施加曲率，不会变成真正直线。");

        final EditText arcCurvatureWeight = field("弧度随机程度权重 %", state.arcCurvatureChangeWeightPct);
        addSetting(box, arcCurvatureWeight, "每次随机事件发生时，重新抽取一个弧线半径的概率。0% = 曲率一直不变；100% = 每次事件都尝试改变弧度。");

        box.addView(subHeader("弧度变化权重区间"));
        final EditText arcCurveBlendMin = field("弧度变化权重 MIN %", state.arcCurvatureBlendMinPct);
        addSetting(box, arcCurveBlendMin, "发生弧度变化时，至少向新随机半径靠近多少比例。数值低会更平滑。");
        final EditText arcCurveBlendMax = field("弧度变化权重 MAX %", state.arcCurvatureBlendMaxPct);
        addSetting(box, arcCurveBlendMax, "发生弧度变化时，最多向新随机半径靠近多少比例。与速度变化权重一样，这是一个随机区间，不是固定值。");

        header(box, "六目标模式");
        final EditText sixSize = field("六目标小球角直径 °", state.sixTargetAngularDiameter);
        addSetting(box, sixSize, "六目标模式的球大小，只影响六目标，不会改变跟枪模式的小球。");

        final EditText boundW = field("虚拟边界宽度 °", state.sixBoundaryWidthDeg);
        addSetting(box, boundW, "六个目标横向允许刷新的总角度范围。数值越大，左右切枪距离越远。边界本身不会画出来。");

        final EditText boundH = field("虚拟边界高度 °", state.sixBoundaryHeightDeg);
        addSetting(box, boundH, "六个目标纵向允许刷新的总角度范围。数值越大，上下切枪距离越远。");

        header(box, "手柄");
        box.addView(note("右摇杆校准会记录中心、最左、最右、最上、最下的真实轴值，用来把你的手柄映射到-1到+1。只有出现方向错误、满杆不到1.0、中心偏移时才需要重校准。"));
        Button calibrate = new Button(this);
        calibrate.setText("重新校准右摇杆");
        calibrate.setOnClickListener(v -> {
            hideKeyboard(root);
            startCalibration();
        });
        box.addView(calibrate);

        hideKb.setOnClickListener(v -> hideKeyboard(root));

        save.setOnClickListener(v -> {
            hideKeyboard(root);
            root.clearFocus();

            state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
            state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));
            state.adsSensitivity = Math.max(0.01f, parse(ads, state.adsSensitivity));
            state.degreesPerSecondPerSensitivity = Math.max(1f, parse(perSens, state.degreesPerSecondPerSensitivity));
            state.responseExponent = clamp(parse(exponent, state.responseExponent), 0.25f, 4f);
            state.deadzone = clamp(parse(deadzone, state.deadzone), 0f, 0.30f);

            state.referenceStrengthPct = clamp(parse(refStrength, state.referenceStrengthPct), 0f, 100f);
            state.backgroundBrightnessPct = clamp(parse(bgBrightness, state.backgroundBrightnessPct), 0f, 100f);

            state.crosshairCrossVisible = pendingCrossVisible[0];
            state.crosshairDotVisible = pendingDotVisible[0];
            state.crosshairGapPx = clamp(parse(crossGap, state.crosshairGapPx), 0f, 80f);
            state.crosshairArmLengthPx = clamp(parse(crossArm, state.crosshairArmLengthPx), 1f, 120f);
            state.crosshairThicknessPx = clamp(parse(crossThickness, state.crosshairThicknessPx), 1f, 20f);
            state.crosshairDotRadiusPx = clamp(parse(dotRadius, state.crosshairDotRadiusPx), 1f, 30f);
            state.hitSoundEnabled = pendingHitSound[0];
            state.hitSoundVolumePct = clamp(parse(hitSoundVolume, state.hitSoundVolumePct), 0f, 100f);

            state.trackingTargetAngularDiameter = clamp(parse(trackingSize, state.trackingTargetAngularDiameter), 0.2f, 20f);
            state.fixedSpeed = Math.max(0.1f, parse(fixed, state.fixedSpeed));
            state.trackingPitchLimitDeg = clamp(parse(pitchLimit, state.trackingPitchLimitDeg), 25f, 82f);
            state.speedMode = pendingSpeedMode[0];
            state.randomSpeedMin = Math.max(0.1f, parse(speedMin, state.randomSpeedMin));
            state.randomSpeedMax = Math.max(0.1f, parse(speedMax, state.randomSpeedMax));
            state.directionChangeWeightPct = clamp(parse(directionWeight, state.directionChangeWeightPct), 0f, 100f);
            state.directionReverseWeightPct = state.directionChangeWeightPct;
            state.speedChangeWeightMinPct = clamp(parse(weightMin, state.speedChangeWeightMinPct), 0f, 100f);
            state.speedChangeWeightMaxPct = clamp(parse(weightMax, state.speedChangeWeightMaxPct), 0f, 100f);
            state.speedChangeIntervalMinSec = Math.max(0.08f, parse(intervalMin, state.speedChangeIntervalMinSec));
            state.speedChangeIntervalMaxSec = Math.max(0.08f, parse(intervalMax, state.speedChangeIntervalMaxSec));

            state.arcTargetAngularDiameter = clamp(parse(arcSize, state.arcTargetAngularDiameter), 0.2f, 20f);
            state.arcFixedSpeed = Math.max(0.1f, parse(arcFixed, state.arcFixedSpeed));
            state.arcPitchLimitDeg = clamp(parse(arcPitchLimit, state.arcPitchLimitDeg), 25f, 82f);
            state.arcSpeedMode = pendingArcSpeedMode[0];
            state.arcRandomSpeedMin = Math.max(0.1f, parse(arcSpeedMin, state.arcRandomSpeedMin));
            state.arcRandomSpeedMax = Math.max(0.1f, parse(arcSpeedMax, state.arcRandomSpeedMax));
            state.arcDirectionChangeWeightPct = clamp(parse(arcDirectionWeight, state.arcDirectionChangeWeightPct), 0f, 100f);
            state.arcSpeedChangeWeightMinPct = clamp(parse(arcSpeedWeightMin, state.arcSpeedChangeWeightMinPct), 0f, 100f);
            state.arcSpeedChangeWeightMaxPct = clamp(parse(arcSpeedWeightMax, state.arcSpeedChangeWeightMaxPct), 0f, 100f);
            state.arcEventIntervalMinSec = Math.max(0.08f, parse(arcIntervalMin, state.arcEventIntervalMinSec));
            state.arcEventIntervalMaxSec = Math.max(0.08f, parse(arcIntervalMax, state.arcEventIntervalMaxSec));
            state.arcRadiusMinDeg = Math.max(2f, parse(arcRadiusMin, state.arcRadiusMinDeg));
            state.arcRadiusMaxDeg = Math.max(2f, parse(arcRadiusMax, state.arcRadiusMaxDeg));
            state.arcCurvatureChangeWeightPct = clamp(parse(arcCurvatureWeight, state.arcCurvatureChangeWeightPct), 0f, 100f);
            state.arcCurvatureBlendMinPct = clamp(parse(arcCurveBlendMin, state.arcCurvatureBlendMinPct), 0f, 100f);
            state.arcCurvatureBlendMaxPct = clamp(parse(arcCurveBlendMax, state.arcCurvatureBlendMaxPct), 0f, 100f);

            state.sixTargetAngularDiameter = clamp(parse(sixSize, state.sixTargetAngularDiameter), 0.2f, 20f);
            state.sixBoundaryWidthDeg = Math.max(8f, parse(boundW, state.sixBoundaryWidthDeg));
            state.sixBoundaryHeightDeg = Math.max(6f, parse(boundH, state.sixBoundaryHeightDeg));

            state.resetTargets();
            boolean ok = saveSettingsSync();
            updateSaveStatus(saveStatus);
            Toast.makeText(this, ok ? "设置已写入手机" : "设置保存失败", Toast.LENGTH_SHORT).show();
        });

        close.setOnClickListener(v -> {
            hideKeyboard(root);
            dlg.dismiss();
        });

        dlg.setContentView(root);
        dlg.setOnShowListener(x -> {
            Window w = dlg.getWindow();
            if (w != null) {
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        dlg.show();
        Window dw = dlg.getWindow();
        if (dw != null) {
            dw.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dw.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void updateSaveStatus(TextView t) {
        int rev = prefs == null ? 0 : prefs.getInt("save_revision", 0);
        float savedFov = prefs == null ? state.fovDeg : prefs.getFloat("fov", state.fovDeg);
        t.setText("本机保存记录 #" + rev + "   已保存FOV=" + savedFov);
    }

    private void updateToggleButton(Button b, String name, boolean on) {
        b.setText(name + "：" + (on ? "显示 / 开启" : "隐藏 / 关闭"));
    }

    private void updateModeButtonText(Button b) {
        String name;
        if (state.mode == GameState.Mode.TRACKING) name = "跟枪";
        else if (state.mode == GameState.Mode.SIX_TARGETS) name = "六目标";
        else name = "弧线";
        b.setText("模式：" + name + "（点按切换）");
    }

    private void updateSpeedModeText(Button b, GameState.SpeedMode m) {
        b.setText("跟枪速度模式：" + (m == GameState.SpeedMode.RANDOM ? "随机" : "固定"));
    }

    private void updateArcSpeedModeText(Button b, GameState.SpeedMode m) {
        b.setText("弧线速度模式：" + (m == GameState.SpeedMode.RANDOM ? "随机" : "固定"));
    }

    private void addSetting(LinearLayout box, EditText field, String help) {
        box.addView(field);
        TextView h = new TextView(this);
        h.setText("说明：" + help);
        h.setTextSize(12f);
        h.setPadding(8, 0, 8, 10);
        h.setAlpha(0.82f);
        box.addView(h);
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
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || event != null) {
                hideKeyboard(e);
                e.clearFocus();
                return true;
            }
            return false;
        });
        return e;
    }

    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private float parse(EditText e, float old) {
        try { return Float.parseFloat(e.getText().toString().trim()); }
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

        boolean ok = saveSettingsSync();

        new AlertDialog.Builder(this)
                .setTitle(ok ? "校准完成并已保存" : "校准完成，但保存失败")
                .setMessage("X轴=" + xAxis + "  Y轴=" + yAxis +
                        "\n中心→左右、中心→上下分别做线性映射。\n\n" + input.diagnostics())
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
