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
        game.setTrainingListener(this::showTrainingResult);
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
            state.cancelTraining60s();
            game.resetWeaponSimulation();
            if (state.mode == GameState.Mode.TRACKING) {
                state.mode = GameState.Mode.SIX_TARGETS;
            } else if (state.mode == GameState.Mode.SIX_TARGETS) {
                state.mode = GameState.Mode.ARC;
            } else if (state.mode == GameState.Mode.ARC) {
                state.mode = GameState.Mode.RECOIL;
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

        Button training60 = new Button(this);
        training60.setText("开始 / 重开60秒");
        training60.setOnClickListener(v -> {
            boolean restarting = state.trainingActive || state.trainingFinished;
            game.resetWeaponSimulation();
            state.startTraining60s();
            Toast.makeText(this,
                    restarting ? "60秒训练已重新开始" : "60秒训练开始",
                    Toast.LENGTH_SHORT).show();
        });
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2);
        tp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tp.setMargins(0, 0, 0, 16);
        root.addView(training60, tp);

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

        state.recoilTargetAngularDiameter = clamp(
                prefs.getFloat("recoil_target_size", state.recoilTargetAngularDiameter),
                0.6f, 20f);
        state.nemesisRecoilMultiplier = clamp(
                prefs.getFloat("nemesis_recoil_multiplier", state.nemesisRecoilMultiplier),
                0f, 3f);

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

                .putFloat("recoil_target_size", state.recoilTargetAngularDiameter)
                .putFloat("nemesis_recoil_multiplier", state.nemesisRecoilMultiplier)

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

    private static class SettingsPage {
        Dialog dlg;
        LinearLayout root;
        LinearLayout box;
        Button hideKb;
        Button save;
        Button close;
    }

    private SettingsPage createSettingsPage(String title) {
        SettingsPage p = new SettingsPage();
        p.dlg = new Dialog(this);
        p.dlg.setTitle(title);

        p.root = new LinearLayout(this);
        p.root.setOrientation(LinearLayout.VERTICAL);
        p.root.setPadding(16, 12, 16, 12);
        p.root.setFocusableInTouchMode(true);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        p.hideKb = new Button(this);
        p.hideKb.setText("收起键盘");
        actions.addView(p.hideKb, new LinearLayout.LayoutParams(0, -2, 1f));

        p.save = new Button(this);
        p.save.setText("保存");
        actions.addView(p.save, new LinearLayout.LayoutParams(0, -2, 1f));

        p.close = new Button(this);
        p.close.setText("返回");
        actions.addView(p.close, new LinearLayout.LayoutParams(0, -2, 1f));

        p.root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        p.box = new LinearLayout(this);
        p.box.setOrientation(LinearLayout.VERTICAL);
        p.box.setPadding(12, 4, 12, 28);
        scroll.addView(p.box);
        p.root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        p.hideKb.setOnClickListener(v -> hideKeyboard(p.root));
        p.close.setOnClickListener(v -> {
            hideKeyboard(p.root);
            p.dlg.dismiss();
        });

        return p;
    }

    private void showSettingsPage(SettingsPage p) {
        p.dlg.setContentView(p.root);
        p.dlg.setOnShowListener(x -> {
            Window w = p.dlg.getWindow();
            if (w != null) {
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        p.dlg.show();
        Window w = p.dlg.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void saveCategory(SettingsPage p, boolean resetCurrentTargets) {
        hideKeyboard(p.root);
        p.root.clearFocus();
        if (resetCurrentTargets) state.resetTargets();
        boolean ok = saveSettingsSync();
        Toast.makeText(this, ok ? "设置已保存" : "设置保存失败", Toast.LENGTH_SHORT).show();
    }

    private void addCategoryButton(LinearLayout row, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(4, 4, 4, 4);
        row.addView(b, lp);
    }

    private LinearLayout categoryRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void showSettings() {
        state.setTrainingPaused(true);

        final Dialog dlg = new Dialog(this);
        dlg.setTitle("设置 v0.2.14");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 16, 20, 16);

        TextView title = new TextView(this);
        title.setText("选择设置分类");
        title.setTextSize(22f);
        title.setPadding(4, 4, 4, 4);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("现在每类设置单独进入，不再需要在一个超长页面里一直向下滑。");
        desc.setTextSize(13f);
        desc.setPadding(4, 0, 4, 12);
        root.addView(desc);

        TextView saveStatus = new TextView(this);
        saveStatus.setTextSize(13f);
        saveStatus.setPadding(4, 0, 4, 10);
        updateSaveStatus(saveStatus);
        root.addView(saveStatus);

        LinearLayout r1 = categoryRow(root);
        addCategoryButton(r1, "通用 / 摇杆", v -> showGeneralSettings());
        addCategoryButton(r1, "准心 / 命中反馈", v -> showCrosshairSettings());

        LinearLayout r2 = categoryRow(root);
        addCategoryButton(r2, "跟枪模式", v -> showTrackingSettings());
        addCategoryButton(r2, "弧线模式", v -> showArcSettings());

        LinearLayout r3 = categoryRow(root);
        addCategoryButton(r3, "六目标模式", v -> showSixTargetSettings());
        addCategoryButton(r3, "画面 / 参考系", v -> showDisplaySettings());

        LinearLayout r4 = categoryRow(root);
        addCategoryButton(r4, "压枪 / 微调", v -> showRecoilSettings());
        addCategoryButton(r4, "手柄 / 校准", v -> showControllerSettings());

        Button close = new Button(this);
        close.setText("关闭设置");
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(4, 12, 4, 0);
        root.addView(close, cp);

        dlg.setContentView(root);
        dlg.setOnDismissListener(x -> state.setTrainingPaused(false));
        dlg.show();

        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showGeneralSettings() {
        SettingsPage p = createSettingsPage("通用 / 摇杆");

        p.box.addView(note("这里只放镜头和右摇杆响应。°/s = 每秒旋转角度。"));

        final EditText fov = field("水平 FOV", state.fovDeg);
        addSetting(p.box, fov, "水平视野范围。当前程序实际范围为50–150。");

        final EditText hip = field("Hip 灵敏度", state.hipSensitivity);
        addSetting(p.box, hip, "腰射灵敏度倍率。满杆转速≈Hip × 每1档满杆转速。");

        final EditText ads = field("ADS 灵敏度", state.adsSensitivity);
        addSetting(p.box, ads, "按住ADS时使用的灵敏度倍率。");

        final EditText perSens = field("每1档满杆转速 °/s", state.degreesPerSecondPerSensitivity);
        addSetting(p.box, perSens, "训练器自己的基础换算值，不是Apex官方内部数值。");

        final EditText exponent = field("响应指数（1.00=严格线性）", state.responseExponent);
        addSetting(p.box, exponent, "1.00=线性；>1中心更慢；<1中心更敏感。当前实际范围0.25–4.0。");

        final EditText deadzone = field("死区（0.00=无死区）", state.deadzone);
        addSetting(p.box, deadzone, "忽略中心附近的小输入。当前实际范围0–0.30。");

        p.save.setOnClickListener(v -> {
            state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
            state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));
            state.adsSensitivity = Math.max(0.01f, parse(ads, state.adsSensitivity));
            state.degreesPerSecondPerSensitivity = Math.max(1f, parse(perSens, state.degreesPerSecondPerSensitivity));
            state.responseExponent = clamp(parse(exponent, state.responseExponent), 0.25f, 4f);
            state.deadzone = clamp(parse(deadzone, state.deadzone), 0f, 0.30f);
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showDisplaySettings() {
        SettingsPage p = createSettingsPage("画面 / 参考系");

        final EditText refStrength = field("参考网格强度 %", state.referenceStrengthPct);
        addSetting(p.box, refStrength, "0%=关闭参考网格，100%=最明显。只影响视觉参考。");

        final EditText bgBrightness = field("背景亮度 %", state.backgroundBrightnessPct);
        addSetting(p.box, bgBrightness, "背景明暗，0–100%。不会改变目标运动。");

        p.save.setOnClickListener(v -> {
            state.referenceStrengthPct = clamp(parse(refStrength, state.referenceStrengthPct), 0f, 100f);
            state.backgroundBrightnessPct = clamp(parse(bgBrightness, state.backgroundBrightnessPct), 0f, 100f);
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showCrosshairSettings() {
        SettingsPage p = createSettingsPage("准心 / 命中反馈");

        p.box.addView(note("准心只负责显示与中心命中判定，不会吸附目标，也不会改变摇杆速度。"));

        final boolean[] pendingCrossVisible = { state.crosshairCrossVisible };
        Button crossVisibleButton = new Button(this);
        updateToggleButton(crossVisibleButton, "十字准心", pendingCrossVisible[0]);
        crossVisibleButton.setOnClickListener(v -> {
            pendingCrossVisible[0] = !pendingCrossVisible[0];
            updateToggleButton(crossVisibleButton, "十字准心", pendingCrossVisible[0]);
        });
        p.box.addView(crossVisibleButton);

        final boolean[] pendingDotVisible = { state.crosshairDotVisible };
        Button dotVisibleButton = new Button(this);
        updateToggleButton(dotVisibleButton, "中心点", pendingDotVisible[0]);
        dotVisibleButton.setOnClickListener(v -> {
            pendingDotVisible[0] = !pendingDotVisible[0];
            updateToggleButton(dotVisibleButton, "中心点", pendingDotVisible[0]);
        });
        p.box.addView(dotVisibleButton);

        final EditText crossGap = field("十字准心间距 px", state.crosshairGapPx);
        addSetting(p.box, crossGap, "中心到四条准心线起点的距离。");

        final EditText crossArm = field("十字准心线长 px", state.crosshairArmLengthPx);
        addSetting(p.box, crossArm, "每条十字臂长度。");

        final EditText crossThickness = field("十字准心线宽 px", state.crosshairThicknessPx);
        addSetting(p.box, crossThickness, "十字线粗细。");

        final EditText dotRadius = field("中心点半径 px", state.crosshairDotRadiusPx);
        addSetting(p.box, dotRadius, "中心点大小。");

        final boolean[] pendingHitSound = { state.hitSoundEnabled };
        Button hitSoundButton = new Button(this);
        updateToggleButton(hitSoundButton, "命中提示音", pendingHitSound[0]);
        hitSoundButton.setOnClickListener(v -> {
            pendingHitSound[0] = !pendingHitSound[0];
            updateToggleButton(hitSoundButton, "命中提示音", pendingHitSound[0]);
        });
        p.box.addView(hitSoundButton);

        final EditText hitSoundVolume = field("命中提示音音量 %", state.hitSoundVolumePct);
        addSetting(p.box, hitSoundVolume, "0–100%。跟枪/弧线进入有效命中时响一次，六目标打掉一个时响一次。");

        p.save.setOnClickListener(v -> {
            state.crosshairCrossVisible = pendingCrossVisible[0];
            state.crosshairDotVisible = pendingDotVisible[0];
            state.crosshairGapPx = clamp(parse(crossGap, state.crosshairGapPx), 0f, 80f);
            state.crosshairArmLengthPx = clamp(parse(crossArm, state.crosshairArmLengthPx), 1f, 120f);
            state.crosshairThicknessPx = clamp(parse(crossThickness, state.crosshairThicknessPx), 1f, 20f);
            state.crosshairDotRadiusPx = clamp(parse(dotRadius, state.crosshairDotRadiusPx), 1f, 30f);
            state.hitSoundEnabled = pendingHitSound[0];
            state.hitSoundVolumePct = clamp(parse(hitSoundVolume, state.hitSoundVolumePct), 0f, 100f);
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showTrackingSettings() {
        SettingsPage p = createSettingsPage("跟枪模式");

        p.box.addView(note("这里只控制360球面跟枪，不会改弧线和六目标。"));

        final EditText trackingSize = field("跟枪小球角直径 °", state.trackingTargetAngularDiameter);
        addSetting(p.box, trackingSize, "只控制跟枪小球大小。当前设置范围0.2–20°。");

        final EditText fixed = field("固定速度 °/s", state.fixedSpeed);
        addSetting(p.box, fixed, "固定速度模式使用。现在按真实球面角距离恒速推进：填写多少°/s，目标每秒就在球面上走多少度。最低0.1°/s，无隐藏最大硬上限。");

        final EditText pitchLimit = field("球面垂直范围 ±°", state.trackingPitchLimitDeg);
        addSetting(p.box, pitchLimit, "目标上下运动范围。当前实际限制25–82°。");

        final GameState.SpeedMode[] pendingSpeedMode = { state.speedMode };
        Button speedMode = new Button(this);
        updateSpeedModeText(speedMode, pendingSpeedMode[0]);
        speedMode.setOnClickListener(v -> {
            pendingSpeedMode[0] = pendingSpeedMode[0] == GameState.SpeedMode.RANDOM
                    ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            updateSpeedModeText(speedMode, pendingSpeedMode[0]);
        });
        p.box.addView(speedMode);

        p.box.addView(subHeader("随机速度"));
        final EditText speedMin = field("速度区间 MIN °/s", state.randomSpeedMin);
        addSetting(p.box, speedMin, "随机模式允许的最低目标速度，最低0.1°/s。");
        final EditText speedMax = field("速度区间 MAX °/s", state.randomSpeedMax);
        addSetting(p.box, speedMax, "随机模式允许的最高边界，目前没有最大硬上限。");

        p.box.addView(subHeader("方向变化"));
        final EditText directionWeight = field("方向变化权重 %", state.directionChangeWeightPct);
        addSetting(p.box, directionWeight, "每次随机事件发生时改变方向的概率。0%=不随机变向，100%=每次都变。");

        p.box.addView(subHeader("速度变化权重"));
        final EditText weightMin = field("变化权重 MIN %", state.speedChangeWeightMinPct);
        addSetting(p.box, weightMin, "一次变速至少向新随机速度靠近多少比例。");
        final EditText weightMax = field("变化权重 MAX %", state.speedChangeWeightMaxPct);
        addSetting(p.box, weightMax, "一次变速最多向新随机速度靠近多少比例。");

        p.box.addView(subHeader("随机事件间隔"));
        final EditText intervalMin = field("间隔 MIN 秒", state.speedChangeIntervalMinSec);
        addSetting(p.box, intervalMin, "随机事件最短等待时间，程序实际最低0.08秒。");
        final EditText intervalMax = field("间隔 MAX 秒", state.speedChangeIntervalMaxSec);
        addSetting(p.box, intervalMax, "随机事件最长等待时间。");

        p.save.setOnClickListener(v -> {
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
            saveCategory(p, state.mode == GameState.Mode.TRACKING);
        });

        showSettingsPage(p);
    }

    private void showRecoilSettings() {
        SettingsPage p = createSettingsPage("压枪 / 微调");

        p.box.addView(note("独立静止靶模式。靶子不会自己移动；按住右扳机后播放复仇女神T1–T35后坐力。每发后坐在约45ms内平滑施加，减少单帧跳变。"));

        final EditText targetSize = field("三层靶外圈角直径 °", state.recoilTargetAngularDiameter);
        addSetting(p.box, targetSize, "整个三层靶的外圈大小。三层半径按1/3、2/3、3/3划分。设置范围0.6–20°。");

        final EditText recoilMultiplier = field("复仇女神后坐力倍率", state.nemesisRecoilMultiplier);
        addSetting(p.box, recoilMultiplier, "1.00为当前实验基准。只改变整体后坐力度，不改变T1–T35左右走势。觉得太重可降低，太轻可提高。范围0–3。");

        p.box.addView(note("命中反馈：中心层闪红；第二层闪黄色；第三层闪蓝色。判定发生在每一发出弹瞬间。"));

        p.save.setOnClickListener(v -> {
            state.recoilTargetAngularDiameter = clamp(parse(targetSize, state.recoilTargetAngularDiameter), 0.6f, 20f);
            state.nemesisRecoilMultiplier = clamp(parse(recoilMultiplier, state.nemesisRecoilMultiplier), 0f, 3f);
            game.resetWeaponSimulation();
            if (state.mode == GameState.Mode.RECOIL) state.resetTargets();
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showArcSettings() {
        SettingsPage p = createSettingsPage("弧线模式");

        p.box.addView(note("弧线模式参数与跟枪模式独立。目标始终保持曲率，不会退化为真正直线。"));

        final EditText arcSize = field("弧线小球角直径 °", state.arcTargetAngularDiameter);
        addSetting(p.box, arcSize, "只控制弧线模式小球大小。当前设置范围0.2–20°。");

        final EditText arcFixed = field("弧线固定速度 °/s", state.arcFixedSpeed);
        addSetting(p.box, arcFixed, "固定速度模式使用。当前没有最大硬上限，最低0.1°/s。");

        final EditText arcPitchLimit = field("弧线垂直范围 ±°", state.arcPitchLimitDeg);
        addSetting(p.box, arcPitchLimit, "弧线目标上下活动范围，实际限制25–82°。");

        final GameState.SpeedMode[] pendingArcSpeedMode = { state.arcSpeedMode };
        Button arcSpeedModeButton = new Button(this);
        updateArcSpeedModeText(arcSpeedModeButton, pendingArcSpeedMode[0]);
        arcSpeedModeButton.setOnClickListener(v -> {
            pendingArcSpeedMode[0] = pendingArcSpeedMode[0] == GameState.SpeedMode.RANDOM
                    ? GameState.SpeedMode.FIXED : GameState.SpeedMode.RANDOM;
            updateArcSpeedModeText(arcSpeedModeButton, pendingArcSpeedMode[0]);
        });
        p.box.addView(arcSpeedModeButton);

        p.box.addView(subHeader("随机速度"));
        final EditText arcSpeedMin = field("弧线速度 MIN °/s", state.arcRandomSpeedMin);
        addSetting(p.box, arcSpeedMin, "随机模式最低速度，最低0.1°/s。");
        final EditText arcSpeedMax = field("弧线速度 MAX °/s", state.arcRandomSpeedMax);
        addSetting(p.box, arcSpeedMax, "随机模式最高边界，目前没有最大硬上限。");

        p.box.addView(subHeader("方向变化"));
        final EditText arcDirectionWeight = field("弧线方向变化权重 %", state.arcDirectionChangeWeightPct);
        addSetting(p.box, arcDirectionWeight, "随机事件时切换顺/逆时针弯曲方向的概率。");

        p.box.addView(subHeader("速度变化权重"));
        final EditText arcSpeedWeightMin = field("弧线速度变化权重 MIN %", state.arcSpeedChangeWeightMinPct);
        addSetting(p.box, arcSpeedWeightMin, "一次变速至少向新随机速度靠近多少比例。");
        final EditText arcSpeedWeightMax = field("弧线速度变化权重 MAX %", state.arcSpeedChangeWeightMaxPct);
        addSetting(p.box, arcSpeedWeightMax, "一次变速最多向新随机速度靠近多少比例。");

        p.box.addView(subHeader("随机事件间隔"));
        final EditText arcIntervalMin = field("弧线事件间隔 MIN 秒", state.arcEventIntervalMinSec);
        addSetting(p.box, arcIntervalMin, "随机事件最短等待时间，程序实际最低0.08秒。");
        final EditText arcIntervalMax = field("弧线事件间隔 MAX 秒", state.arcEventIntervalMaxSec);
        addSetting(p.box, arcIntervalMax, "随机事件最长等待时间。");

        p.box.addView(subHeader("弧线半径 / 曲率"));
        final EditText arcRadiusMin = field("弧线半径 MIN °", state.arcRadiusMinDeg);
        addSetting(p.box, arcRadiusMin, "最紧弧线半径。程序实际最低2°，越小转弯越急。");
        final EditText arcRadiusMax = field("弧线半径 MAX °", state.arcRadiusMaxDeg);
        addSetting(p.box, arcRadiusMax, "最宽弧线半径，越大弧线越平缓。");

        final EditText arcCurvatureWeight = field("弧度随机程度权重 %", state.arcCurvatureChangeWeightPct);
        addSetting(p.box, arcCurvatureWeight, "每次随机事件重新抽取弧线半径的概率。");

        p.box.addView(subHeader("弧度变化权重"));
        final EditText arcCurveBlendMin = field("弧度变化权重 MIN %", state.arcCurvatureBlendMinPct);
        addSetting(p.box, arcCurveBlendMin, "发生曲率变化时，至少向新半径靠近多少比例。");
        final EditText arcCurveBlendMax = field("弧度变化权重 MAX %", state.arcCurvatureBlendMaxPct);
        addSetting(p.box, arcCurveBlendMax, "发生曲率变化时，最多向新半径靠近多少比例。");

        p.save.setOnClickListener(v -> {
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
            saveCategory(p, state.mode == GameState.Mode.ARC);
        });

        showSettingsPage(p);
    }

    private void showSixTargetSettings() {
        SettingsPage p = createSettingsPage("六目标模式");

        final EditText sixSize = field("六目标小球角直径 °", state.sixTargetAngularDiameter);
        addSetting(p.box, sixSize, "只控制六目标模式小球大小。当前设置范围0.2–20°。");

        final EditText boundW = field("虚拟边界宽度 °", state.sixBoundaryWidthDeg);
        addSetting(p.box, boundW, "六个目标横向刷新总范围。当前最低8°。");

        final EditText boundH = field("虚拟边界高度 °", state.sixBoundaryHeightDeg);
        addSetting(p.box, boundH, "六个目标纵向刷新总范围。当前最低6°。");

        p.save.setOnClickListener(v -> {
            state.sixTargetAngularDiameter = clamp(parse(sixSize, state.sixTargetAngularDiameter), 0.2f, 20f);
            state.sixBoundaryWidthDeg = Math.max(8f, parse(boundW, state.sixBoundaryWidthDeg));
            state.sixBoundaryHeightDeg = Math.max(6f, parse(boundH, state.sixBoundaryHeightDeg));
            saveCategory(p, state.mode == GameState.Mode.SIX_TARGETS);
        });

        showSettingsPage(p);
    }

    private void showControllerSettings() {
        SettingsPage p = createSettingsPage("手柄 / 校准");
        p.save.setVisibility(View.GONE);

        p.box.addView(note("右摇杆校准会记录中心、最左、最右、最上、最下的真实轴值。只有出现方向错误、满杆不到1.0或中心偏移时才需要重校准。"));

        Button calibrate = new Button(this);
        calibrate.setText("重新校准右摇杆");
        calibrate.setOnClickListener(v -> {
            hideKeyboard(p.root);
            startCalibration();
        });
        p.box.addView(calibrate);

        showSettingsPage(p);
    }

    private void showTrainingResult() {
        if (isFinishing()) return;

        String modeName;
        if (state.getTrainingMode() == GameState.Mode.TRACKING) modeName = "跟枪";
        else if (state.getTrainingMode() == GameState.Mode.ARC) modeName = "弧线";
        else if (state.getTrainingMode() == GameState.Mode.RECOIL) modeName = "压枪微调";
        else modeName = "六目标";

        String message;
        if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS) {
            message = "模式：" + modeName +
                    "\n得分：" + state.trainingScore +
                    "\n命中：" + state.trainingHits + "/" + state.trainingShots +
                    "\n准确率：" + String.format(java.util.Locale.US, "%.1f%%", state.getTrainingAccuracyPct());
        } else {
            float rate = state.trainingHitTimeSec / 60f * 100f;
            message = "模式：" + modeName +
                    "\n得分：" + state.trainingScore +
                    "\n有效跟枪：" + String.format(java.util.Locale.US, "%.2f 秒", state.trainingHitTimeSec) +
                    "\n有效跟枪率：" + String.format(java.util.Locale.US, "%.1f%%", rate);
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("60秒训练完成")
                .setMessage(message)
                .setPositiveButton("再来60秒", (d, which) -> {
                    game.resetWeaponSimulation();
                    state.startTraining60s();
                    Toast.makeText(this, "60秒训练重新开始", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("继续自由练习", (d, which) -> state.trainingFinished = false)
                .create();

        dlg.setOnCancelListener(d -> state.trainingFinished = false);
        dlg.show();
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
        else if (state.mode == GameState.Mode.ARC) name = "弧线";
        else name = "压枪微调";
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
