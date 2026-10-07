import sys, re
from pathlib import Path

pkg = Path(sys.argv[1])

def read(name):
    return (pkg / name).read_text(encoding="utf-8")

def write(name, s):
    (pkg / name).write_text(s, encoding="utf-8")

def replace_once(s, old, new, label):
    if old not in s:
        raise RuntimeError("missing patch anchor: " + label)
    return s.replace(old, new, 1)

# ---------------- GameState.java ----------------
s = read("GameState.java")
s = replace_once(
    s,
    "    public volatile float fovDeg = 120f;\n",
    "    public volatile float fovDeg = 120f;\n    public volatile float adsFovDeg = 90f;\n",
    "ADS FOV field"
)
write("GameState.java", s)

# ---------------- ControllerInput.java ----------------
s = read("ControllerInput.java")
old_triggers = """        // Common Android trigger representations.
        float rTrig = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        float lTrig = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        if (rTrig > 0.35f) shootPressed = true;
        else if (rTrig >= 0f) shootPressed = false;
        if (lTrig > 0.35f) adsPressed = true;
        else if (lTrig >= 0f) adsPressed = false;
"""
new_triggers = """        // Common Android trigger representations.
        // IMPORTANT: do not clear a KEYCODE_BUTTON_L2/R2 state merely because an
        // unrelated joystick motion event reports 0 for an axis the device does
        // not actually expose. That made ADS drop out as soon as the stick moved.
        float rTrig = Math.max(
                readNormalizedTrigger(d, e, MotionEvent.AXIS_RTRIGGER),
                readNormalizedTrigger(d, e, MotionEvent.AXIS_GAS));
        float lTrig = Math.max(
                readNormalizedTrigger(d, e, MotionEvent.AXIS_LTRIGGER),
                readNormalizedTrigger(d, e, MotionEvent.AXIS_BRAKE));

        if (rTrig >= 0f) shootPressed = rTrig > 0.35f;
        if (lTrig >= 0f) adsPressed = lTrig > 0.35f;
"""
s = replace_once(s, old_triggers, new_triggers, "trigger state handling")

diag_old = """        b.append("device=").append(deviceId)
         .append("  RX=").append(String.format(Locale.US, "%.3f", rightX))
         .append(" RY=").append(String.format(Locale.US, "%.3f", rightY)).append("\n");"""
diag_new = """        b.append("device=").append(deviceId)
         .append("  RX=").append(String.format(Locale.US, "%.3f", rightX))
         .append(" RY=").append(String.format(Locale.US, "%.3f", rightY))
         .append(" ADS=").append(adsPressed ? "1" : "0")
         .append(" FIRE=").append(shootPressed ? "1" : "0").append("\n");"""
s = replace_once(s, diag_old, diag_new, "controller diagnostics")

helper_anchor = """    private static float normalizeDirectional(float raw, float center, float positiveRaw, float negativeRaw) {"""
helper = """    private static float readNormalizedTrigger(InputDevice d, MotionEvent e, int axis) {
        if (d == null) return -1f;

        InputDevice.MotionRange found = null;
        for (InputDevice.MotionRange r : d.getMotionRanges()) {
            if (r.getAxis() == axis &&
                    (((r.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) ||
                     ((r.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD))) {
                found = r;
                break;
            }
        }
        if (found == null) return -1f;

        float min = found.getMin();
        float max = found.getMax();
        float span = max - min;
        if (Math.abs(span) < 0.0001f) return -1f;

        float normalized = (e.getAxisValue(axis) - min) / span;
        return clamp(normalized, 0f, 1f);
    }

"""
if helper_anchor not in s:
    raise RuntimeError("missing trigger helper anchor")
s = s.replace(helper_anchor, helper + helper_anchor, 1)
write("ControllerInput.java", s)

# ---------------- MainActivity.java ----------------
s = read("MainActivity.java")
s = s.replace('dlg.setTitle("设置 v0.2.15");', 'dlg.setTitle("设置 v0.2.16");', 1)

# Load/save ADS FOV.
s = replace_once(
    s,
    '        state.fovDeg = prefs.getFloat("fov", state.fovDeg);\n',
    '        state.fovDeg = prefs.getFloat("fov", state.fovDeg);\n        state.adsFovDeg = prefs.getFloat("ads_fov", state.adsFovDeg);\n',
    "load ADS FOV"
)
s = replace_once(
    s,
    '                .putFloat("fov", state.fovDeg)\n',
    '                .putFloat("fov", state.fovDeg)\n                .putFloat("ads_fov", state.adsFovDeg)\n',
    "save ADS FOV"
)

# General settings: explicit ADS FOV field.
s = replace_once(
    s,
    """        final EditText fov = field("水平 FOV", state.fovDeg);
        addSetting(p.box, fov, "水平视野范围。当前程序实际范围为50–150。");

        final EditText hip = field("Hip 灵敏度", state.hipSensitivity);""",
    """        final EditText fov = field("腰射水平 FOV", state.fovDeg);
        addSetting(p.box, fov, "腰射水平视野范围。当前程序实际范围为50–150°。");

        final EditText adsFov = field("开镜水平 FOV", state.adsFovDeg);
        addSetting(p.box, adsFov, "按住ADS时立即使用这个水平FOV。建议先用90°测试是否明显缩放；范围30–150°。");

        final EditText hip = field("Hip 灵敏度", state.hipSensitivity);""",
    "ADS FOV setting field"
)
s = replace_once(
    s,
    """            state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
            state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));""",
    """            state.fovDeg = clamp(parse(fov, state.fovDeg), 50f, 150f);
            state.adsFovDeg = clamp(parse(adsFov, state.adsFovDeg), 30f, 150f);
            state.hipSensitivity = Math.max(0.01f, parse(hip, state.hipSensitivity));""",
    "save ADS FOV setting"
)

# Main settings page: fixed close button + independently scrollable categories.
old_categories = """        LinearLayout r1 = categoryRow(root);
        addCategoryButton(r1, "通用 / 摇杆", v -> showGeneralSettings());
        addCategoryButton(r1, "准心 / 命中反馈", v -> showCrosshairSettings());

        LinearLayout r2 = categoryRow(root);
        addCategoryButton(r2, "跟枪模式", v -> showTrackingSettings());
        addCategoryButton(r2, "弧线模式", v -> showArcSettings());

        LinearLayout r3 = categoryRow(root);
        addCategoryButton(r3, "六目标模式", v -> showSixTargetSettings());
        addCategoryButton(r3, "画面 / 参考系", v -> showDisplaySettings());

        LinearLayout r4 = categoryRow(root);
        addCategoryButton(r4, "网格射击", v -> showGridShootSettings());
        addCategoryButton(r4, "目标转移跟枪", v -> showTransferTrackSettings());

        LinearLayout r5 = categoryRow(root);
        addCategoryButton(r5, "手柄 / 校准", v -> showControllerSettings());

        Button close = new Button(this);
        close.setText("关闭设置");
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(4, 12, 4, 0);
        root.addView(close, cp);"""
new_categories = """        Button close = new Button(this);
        close.setText("关闭设置");
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(4, 0, 4, 10);
        root.addView(close, cp);

        ScrollView categoryScroll = new ScrollView(this);
        LinearLayout categoryBox = new LinearLayout(this);
        categoryBox.setOrientation(LinearLayout.VERTICAL);
        categoryScroll.addView(categoryBox, new ScrollView.LayoutParams(-1, -2));

        LinearLayout r1 = categoryRow(categoryBox);
        addCategoryButton(r1, "通用 / 摇杆", v -> showGeneralSettings());
        addCategoryButton(r1, "准心 / 命中反馈", v -> showCrosshairSettings());

        LinearLayout r2 = categoryRow(categoryBox);
        addCategoryButton(r2, "跟枪模式", v -> showTrackingSettings());
        addCategoryButton(r2, "弧线模式", v -> showArcSettings());

        LinearLayout r3 = categoryRow(categoryBox);
        addCategoryButton(r3, "六目标模式", v -> showSixTargetSettings());
        addCategoryButton(r3, "画面 / 参考系", v -> showDisplaySettings());

        LinearLayout r4 = categoryRow(categoryBox);
        addCategoryButton(r4, "网格射击", v -> showGridShootSettings());
        addCategoryButton(r4, "目标转移跟枪", v -> showTransferTrackSettings());

        LinearLayout r5 = categoryRow(categoryBox);
        addCategoryButton(r5, "手柄 / 校准", v -> showControllerSettings());

        root.addView(categoryScroll, new LinearLayout.LayoutParams(-1, 0, 1f));"""
s = replace_once(s, old_categories, new_categories, "scrollable settings categories")

s = replace_once(
    s,
    "            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);\n",
    "            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);\n",
    "main settings full height"
)
write("MainActivity.java", s)

# ---------------- AimCanvasView.java ----------------
s = read("AimCanvasView.java")

# All projection and target drawing use the live FOV, including ADS.
s = s.replace("GameState.clamp(state.fovDeg, 50f, 150f)", "currentHorizontalFov()")

# Replace continuous tracking hit tests with screen-space tests that exactly match the rendered circle.
s = s.replace(
    "boolean nowHit = shoot && state.isCenterOnTrackingTarget();",
    "boolean nowHit = shoot && isVisuallyOnTarget(state.tracking, state.trackingTargetAngularDiameter, trackingFireHit);"
)
s = s.replace(
    "boolean nowHit = shoot && state.isCenterOnArcTarget();",
    "boolean nowHit = shoot && isVisuallyOnTarget(state.arc, state.arcTargetAngularDiameter, arcFireHit);"
)
s = s.replace(
    "boolean nowHit = shoot && state.isCenterOnTransferTarget();",
    "boolean nowHit = shoot && isVisuallyOnTarget(state.transferTarget, state.transferTargetAngularDiameter, transferFireHit);"
)

# Insert current FOV + exact visible hit test before response().
anchor = """    private float response(float v) {"""
helpers = """    private float currentHorizontalFov() {
        float f = input.isAdsPressed() ? state.adsFovDeg : state.fovDeg;
        return GameState.clamp(f, 30f, 150f);
    }

    private boolean isVisuallyOnTarget(GameState.Target t, float angularDiameter, boolean wasHit) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return false;

        float hFov = currentHorizontalFov();
        float halfH = hFov * 0.5f;
        float aspect = Math.max(0.1f, w / (float)Math.max(1, h));
        double vFov = 2.0 * Math.atan(Math.tan(Math.toRadians(hFov) * 0.5) / aspect);
        float halfV = (float)Math.toDegrees(vFov) * 0.5f;

        float relYaw = shortest(t.yawDeg, state.yaw);
        float relPitch = t.pitchDeg - state.pitch;
        if (Math.abs(relYaw) > halfH + 8f || Math.abs(relPitch) > halfV + 8f) return false;

        float tx = projectX(relYaw, halfH);
        float ty = projectY(relPitch, halfV);
        float radius = (float)(Math.tan(Math.toRadians(Math.max(0.1f, angularDiameter) * 0.5))
                * w * 0.5 / Math.tan(Math.toRadians(halfH)));
        radius = Math.max(5f, radius);

        // Small hysteresis prevents one-frame hit/miss toggling when the crosshair
        // rides the visible edge. Entry uses the exact rendered radius; exit gets
        // an 8% margin only after a valid hit has already begun.
        float threshold = wasHit ? radius * 1.08f : radius;
        float dx = tx - w * 0.5f;
        float dy = ty - h * 0.5f;
        return dx * dx + dy * dy <= threshold * threshold;
    }

"""
if anchor not in s:
    raise RuntimeError("missing AimCanvas hit helper anchor")
s = s.replace(anchor, helpers + anchor, 1)
write("AimCanvasView.java", s)

# ---------------- HudView.java ----------------
s = read("HudView.java")

# Add an always-visible input/FOV line so ADS state can be verified immediately.
anchor = """        c.drawText(line2,22f,58f,p);

        float trainingY = 84f;"""
new = """        c.drawText(line2,22f,58f,p);

        p.setTextSize(15f);
        float liveFov = input.isAdsPressed() ? state.adsFovDeg : state.fovDeg;
        float liveSens = input.isAdsPressed() ? state.adsSensitivity : state.hipSensitivity;
        String adsLine = String.format(Locale.US,
                "ADS %s  当前HFOV %.0f°  当前灵敏 %.2f",
                input.isAdsPressed() ? "ON" : "OFF",
                liveFov,
                liveSens);
        c.drawText(adsLine, 22f, 82f, p);

        float trainingY = 108f;"""
s = replace_once(s, anchor, new, "ADS HUD line")
write("HudView.java", s)

print("v0.2.16 bugfix patch applied")
