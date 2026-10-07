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
s = replace_once(s,
    "public enum Mode { TRACKING, SIX_TARGETS, ARC, RECOIL }",
    "public enum Mode { TRACKING, SIX_TARGETS, ARC, GRID_SHOOT, TRANSFER_TRACK }",
    "mode enum")

target_block = """    public final Target tracking = new Target();
    public final Target arc = new Target();
    public final Target recoilTarget = new Target();
    public final Target[] six = new Target[6];"""
target_new = """    public final Target tracking = new Target();
    public final Target arc = new Target();
    public final Target recoilTarget = new Target(); // legacy unused
    public final Target gridTarget = new Target();
    public final Target transferTarget = new Target();
    public final Target[] six = new Target[6];"""
s = replace_once(s, target_block, target_new, "targets")

runtime_anchor = """    // 六目标运行态
    private float sixCenterYaw = 0f;
    private float sixCenterPitch = 0f;
"""
runtime_new = """    // 六目标运行态
    private float sixCenterYaw = 0f;
    private float sixCenterPitch = 0f;

    // 网格射击：固定3x3世界网格，命中后优先跳到较远节点。
    public volatile float gridTargetAngularDiameter = 1.45f;
    public volatile float gridWidthDeg = 72f;
    public volatile float gridHeightDeg = 40f;
    private float gridCenterYaw = 0f;
    private float gridCenterPitch = 0f;
    private int gridIndex = 4;

    // 目标转移跟枪：持续移动，周期性转移到离当前准心较远的新位置。
    public volatile float transferTargetAngularDiameter = 2.80f;
    public volatile float transferSpeedDegPerSec = 26f;
    public volatile float transferIntervalSec = 2.40f;
    public volatile float transferMinAngleDeg = 28f;
    public volatile float transferYawSpanDeg = 78f;
    public volatile float transferPitchSpanDeg = 38f;
    private float transferHeadingDeg = 25f;
    private float transferTimerSec = 2.40f;
"""
s = replace_once(s, runtime_anchor, runtime_new, "runtime fields")

old_reset = """        } else if (mode == Mode.RECOIL) {
            // 静止世界靶：进入模式时放在当前准心正中，之后不自行移动。
            recoilTarget.yawDeg = yaw;
            recoilTarget.pitchDeg = pitch;
            recoilTarget.distance = 9f;
            recoilTarget.active = true;
        } else {
            sixCenterYaw = yaw;
            sixCenterPitch = clamp(pitch, -55f, 55f);
            for (Target t : six) respawnSix(t);
        }"""
new_reset = """        } else if (mode == Mode.GRID_SHOOT) {
            gridCenterYaw = yaw;
            gridCenterPitch = clamp(pitch, -50f, 50f);
            gridIndex = 4;
            placeGridTarget();
        } else if (mode == Mode.TRANSFER_TRACK) {
            transferTarget.yawDeg = wrap180(yaw + 28f);
            transferTarget.pitchDeg = clamp(pitch + 4f, -65f, 65f);
            transferTarget.distance = 9f;
            transferTarget.active = true;
            transferHeadingDeg = rng.nextBoolean() ? 24f : 156f;
            transferTimerSec = Math.max(0.60f, transferIntervalSec);
        } else {
            sixCenterYaw = yaw;
            sixCenterPitch = clamp(pitch, -55f, 55f);
            for (Target t : six) respawnSix(t);
        }"""
s = replace_once(s, old_reset, new_reset, "reset modes")

old_update = """    public synchronized void update(float dt) {
        if (mode == Mode.TRACKING) {
            updateTracking(dt);
        } else if (mode == Mode.ARC) {
            updateArc(dt);
        }
    }

    private void updateTracking(float dt) {"""
new_update = """    public synchronized void update(float dt) {
        if (mode == Mode.TRACKING) {
            updateTracking(dt);
        } else if (mode == Mode.ARC) {
            updateArc(dt);
        } else if (mode == Mode.TRANSFER_TRACK) {
            updateTransferTracking(dt);
        }
    }

    private void updateTransferTracking(float dt) {
        transferTimerSec -= Math.max(0f, dt);
        if (transferTimerSec <= 0f) {
            relocateTransferTarget();
            transferTimerSec = Math.max(0.60f, transferIntervalSec);
        }
        moveTransferTargetOnSphereExact(Math.max(0.1f, transferSpeedDegPerSec), dt);
    }

    private void relocateTransferTarget() {
        float halfW = clamp(transferYawSpanDeg * 0.5f, 10f, 70f);
        float halfH = clamp(transferPitchSpanDeg * 0.5f, 6f, 40f);
        float minAngle = clamp(transferMinAngleDeg, 8f, 70f);

        float bestYaw = wrap180(yaw + (rng.nextBoolean() ? minAngle : -minAngle));
        float bestPitch = clamp(pitch, -72f, 72f);
        float bestDist = 0f;

        for (int i = 0; i < 16; i++) {
            float cy = wrap180(yaw + lerp(-halfW, halfW, rng.nextFloat()));
            float cp = clamp(pitch + lerp(-halfH, halfH, rng.nextFloat()), -72f, 72f);
            float d = angularDistance(yaw, pitch, cy, cp);
            if (d > bestDist) {
                bestDist = d;
                bestYaw = cy;
                bestPitch = cp;
            }
            if (d >= minAngle) break;
        }

        transferTarget.yawDeg = bestYaw;
        transferTarget.pitchDeg = bestPitch;
        transferTarget.distance = 9f;
        transferHeadingDeg = rng.nextFloat() * 360f;
    }

    private void moveTransferTargetOnSphereExact(float speedDegPerSec, float dt) {
        float yawRad = (float)Math.toRadians(transferTarget.yawDeg);
        float pitchRad = (float)Math.toRadians(transferTarget.pitchDeg);
        float headingRad = (float)Math.toRadians(transferHeadingDeg);
        float[] pos = dirFromAngles(transferTarget.yawDeg, transferTarget.pitchDeg);

        float[] yawBasis = new float[]{
                (float)Math.cos(yawRad), 0f, (float)Math.sin(yawRad)
        };
        float[] pitchBasis = new float[]{
                -(float)Math.sin(pitchRad) * (float)Math.sin(yawRad),
                -(float)Math.cos(pitchRad),
                (float)Math.sin(pitchRad) * (float)Math.cos(yawRad)
        };

        float ch = (float)Math.cos(headingRad);
        float sh = (float)Math.sin(headingRad);
        float tx = ch * yawBasis[0] + sh * pitchBasis[0];
        float ty = ch * yawBasis[1] + sh * pitchBasis[1];
        float tz = ch * yawBasis[2] + sh * pitchBasis[2];

        float stepRad = (float)Math.toRadians(Math.max(0.1f, speedDegPerSec) * Math.max(0f, dt));
        float cs = (float)Math.cos(stepRad);
        float sn = (float)Math.sin(stepRad);

        float nx = pos[0] * cs + tx * sn;
        float ny = pos[1] * cs + ty * sn;
        float nz = pos[2] * cs + tz * sn;
        float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len > 0.000001f) { nx /= len; ny /= len; nz /= len; }

        float newYaw = (float)Math.toDegrees(Math.atan2(nx, -nz));
        float newPitch = (float)Math.toDegrees(Math.asin(clamp(-ny, -1f, 1f)));
        float limit = 72f;
        if (newPitch > limit) {
            newPitch = limit - (newPitch - limit);
            transferHeadingDeg = normalize360(-transferHeadingDeg);
        } else if (newPitch < -limit) {
            newPitch = -limit + (-limit - newPitch);
            transferHeadingDeg = normalize360(-transferHeadingDeg);
        }

        transferTarget.yawDeg = wrap180(newYaw);
        transferTarget.pitchDeg = clamp(newPitch, -limit, limit);
    }

    private float angularDistance(float yawA, float pitchA, float yawB, float pitchB) {
        float[] a = dirFromAngles(yawA, pitchA);
        float[] b = dirFromAngles(yawB, pitchB);
        float dot = clamp(a[0]*b[0] + a[1]*b[1] + a[2]*b[2], -1f, 1f);
        return (float)Math.toDegrees(Math.acos(dot));
    }

    private void placeGridTarget() {
        int col = gridIndex % 3;
        int row = gridIndex / 3;
        float x = col - 1f;
        float y = row - 1f;
        gridTarget.yawDeg = wrap180(gridCenterYaw + x * Math.max(10f, gridWidthDeg * 0.5f));
        gridTarget.pitchDeg = clamp(gridCenterPitch + y * Math.max(6f, gridHeightDeg * 0.5f), -72f, 72f);
        gridTarget.distance = 9f;
        gridTarget.active = true;
    }

    private void chooseNextGridTarget() {
        int oldCol = gridIndex % 3;
        int oldRow = gridIndex / 3;
        int[] candidates = new int[8];
        int count = 0;
        for (int i = 0; i < 9; i++) {
            if (i == gridIndex) continue;
            int col = i % 3;
            int row = i / 3;
            int dc = col - oldCol;
            int dr = row - oldRow;
            if (dc*dc + dr*dr >= 2) candidates[count++] = i;
        }
        if (count == 0) {
            do { gridIndex = rng.nextInt(9); } while (gridIndex == oldRow * 3 + oldCol);
        } else {
            gridIndex = candidates[rng.nextInt(count)];
        }
        placeGridTarget();
    }

    private void updateTracking(float dt) {"""
s = replace_once(s, old_update, new_update, "special mode update")

# 60s continuous modes
s = s.replace(
    "if ((mode == Mode.TRACKING || mode == Mode.ARC || mode == Mode.RECOIL) && continuousHit) {",
    "if ((mode == Mode.TRACKING || mode == Mode.ARC || mode == Mode.TRANSFER_TRACK) && continuousHit) {"
)

# shot-based 60s scoring
s = s.replace(
    "if (mode != Mode.SIX_TARGETS) return;",
    "if (mode != Mode.SIX_TARGETS && mode != Mode.GRID_SHOOT) return;",
    1
)

old_shoot_branch = """        } else if (mode == Mode.RECOIL) {
            hit = angularDistanceToCamera(recoilTarget) <= recoilTargetAngularDiameter * 0.5f;
            if (hit) hits++;
        } else {"""
new_shoot_branch = """        } else if (mode == Mode.GRID_SHOOT) {
            hit = angularDistanceToCamera(gridTarget) <= gridTargetAngularDiameter * 0.5f;
            if (hit) {
                hits++;
                chooseNextGridTarget();
            }
        } else if (mode == Mode.TRANSFER_TRACK) {
            hit = angularDistanceToCamera(transferTarget) <= transferTargetAngularDiameter * 0.5f;
            if (hit) hits++;
        } else {"""
s = replace_once(s, old_shoot_branch, new_shoot_branch, "shoot branches")

old_hit_methods = """    public synchronized boolean isCenterOnRecoilTarget() {
        return angularDistanceToCamera(recoilTarget) <= recoilTargetAngularDiameter * 0.5f;
    }

    public synchronized float getRecoilCenterErrorDeg() {
        return angularDistanceToCamera(recoilTarget);
    }

    public synchronized float getTrackingCenterErrorDeg() {"""
new_hit_methods = """    public synchronized boolean isCenterOnGridTarget() {
        return angularDistanceToCamera(gridTarget) <= gridTargetAngularDiameter * 0.5f;
    }

    public synchronized boolean isCenterOnTransferTarget() {
        return angularDistanceToCamera(transferTarget) <= transferTargetAngularDiameter * 0.5f;
    }

    public synchronized int getGridIndex() { return gridIndex; }
    public synchronized float getGridCenterYaw() { return gridCenterYaw; }
    public synchronized float getGridCenterPitch() { return gridCenterPitch; }

    public synchronized float getGridNodeYaw(int index) {
        int i = Math.max(0, Math.min(8, index));
        int col = i % 3;
        return wrap180(gridCenterYaw + (col - 1f) * Math.max(10f, gridWidthDeg * 0.5f));
    }

    public synchronized float getGridNodePitch(int index) {
        int i = Math.max(0, Math.min(8, index));
        int row = i / 3;
        return clamp(gridCenterPitch + (row - 1f) * Math.max(6f, gridHeightDeg * 0.5f), -72f, 72f);
    }

    public synchronized float getTransferHeadingDeg() { return transferHeadingDeg; }
    public synchronized float getTransferTimerSec() { return Math.max(0f, transferTimerSec); }

    public synchronized float getTrackingCenterErrorDeg() {"""
s = replace_once(s, old_hit_methods, new_hit_methods, "hit helpers")

# Legacy recoil helper remains referenced only by unreachable v0.2.14 methods in AimCanvasView.
# Keep this tiny compatibility method so the old code compiles; the recoil mode itself is gone.
s = replace_once(
    s,
    "    public synchronized float getTrackingCenterErrorDeg() {",
    """    public synchronized float getRecoilCenterErrorDeg() {
        return angularDistanceToCamera(recoilTarget);
    }

    public synchronized float getTrackingCenterErrorDeg() {""",
    "legacy recoil compile helper"
)

write("GameState.java", s)

# ---------------- AimCanvasView.java ----------------
s = read("AimCanvasView.java")
# Keep legacy weapon code compiled but unreachable by renaming its old enum reference.
s = s.replace("GameState.Mode.RECOIL", "GameState.Mode.GRID_SHOOT")

# Do not run weapon recoil at all.
s = replace_once(s,
    "        updateCamera(simDt);\n        updateWeaponRecoil(simDt);\n        if (recoilFlashRemainingSec > 0f) {",
    "        updateCamera(simDt);\n        if (recoilFlashRemainingSec > 0f) {",
    "disable recoil update")

# Add transfer hit state
s = replace_once(s,
    "    private boolean recoilFireHit;\n",
    "    private boolean recoilFireHit; // legacy unused\n    private boolean transferFireHit;\n",
    "transfer fire flag")

s = s.replace(
    "state.tickTraining60s(rawDt, trackingFireHit || arcFireHit || recoilFireHit);",
    "state.tickTraining60s(rawDt, trackingFireHit || arcFireHit || transferFireHit);"
)

# Replace whole shooting feedback method.
start = s.index("    private void updateShootingFeedback() {")
end = s.index("    private void playHitTone()", start)
new_feedback = """    private void updateShootingFeedback() {
        boolean shoot = input.isShootPressed();

        if (state.mode == GameState.Mode.TRACKING) {
            boolean nowHit = shoot && state.isCenterOnTrackingTarget();
            if (nowHit && !trackingFireHit) playHitTone();
            trackingFireHit = nowHit;
            arcFireHit = false;
            transferFireHit = false;
            if (shoot && !prevShoot) state.shootAtCenter();

        } else if (state.mode == GameState.Mode.ARC) {
            boolean nowHit = shoot && state.isCenterOnArcTarget();
            if (nowHit && !arcFireHit) playHitTone();
            arcFireHit = nowHit;
            trackingFireHit = false;
            transferFireHit = false;
            if (shoot && !prevShoot) state.shootAtCenter();

        } else if (state.mode == GameState.Mode.TRANSFER_TRACK) {
            boolean nowHit = shoot && state.isCenterOnTransferTarget();
            if (nowHit && !transferFireHit) playHitTone();
            transferFireHit = nowHit;
            trackingFireHit = false;
            arcFireHit = false;
            if (shoot && !prevShoot) state.shootAtCenter();

        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            trackingFireHit = false;
            arcFireHit = false;
            transferFireHit = false;
            if (shoot && !prevShoot) {
                boolean hit = state.shootAtCenter();
                state.recordTrainingShot(hit);
                if (hit) playHitTone();
            }

        } else {
            trackingFireHit = false;
            arcFireHit = false;
            transferFireHit = false;
            if (shoot && !prevShoot) {
                boolean hit = state.shootAtCenter();
                state.recordTrainingShot(hit);
                if (hit) playHitTone();
            }
        }

        prevShoot = shoot;
    }

"""
s = s[:start] + new_feedback + s[end:]

# Replace render dispatch.
old_draw = """        if (state.mode == GameState.Mode.TRACKING) {
            int trackingColor = trackingFireHit ? 0xff55e88a : 0xffff5a4d;
            drawTarget(canvas, state.tracking, trackingColor, state.trackingTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.ARC) {
            int arcColor = arcFireHit ? 0xff55e88a : 0xffffb347;
            drawTarget(canvas, state.arc, arcColor, state.arcTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            drawRecoilTarget(canvas);
        } else {
            for (GameState.Target t : state.six) {
                drawTarget(canvas, t, 0xffff715c, state.sixTargetAngularDiameter);
            }
        }"""
new_draw = """        if (state.mode == GameState.Mode.TRACKING) {
            int trackingColor = trackingFireHit ? 0xff55e88a : 0xffff5a4d;
            drawTarget(canvas, state.tracking, trackingColor, state.trackingTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.ARC) {
            int arcColor = arcFireHit ? 0xff55e88a : 0xffffb347;
            drawTarget(canvas, state.arc, arcColor, state.arcTargetAngularDiameter);
        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            drawGridShoot(canvas);
        } else if (state.mode == GameState.Mode.TRANSFER_TRACK) {
            int color = transferFireHit ? 0xff55e88a : 0xff9b7cff;
            drawTarget(canvas, state.transferTarget, color, state.transferTargetAngularDiameter);
        } else {
            for (GameState.Target t : state.six) {
                drawTarget(canvas, t, 0xffff715c, state.sixTargetAngularDiameter);
            }
        }"""
s = replace_once(s, old_draw, new_draw, "render dispatch")

# Insert grid renderer before legacy drawRecoilTarget.
marker = "    private void drawRecoilTarget(Canvas canvas) {"
grid_renderer = """    private void drawGridShoot(Canvas canvas) {
        int active = state.getGridIndex();
        for (int i = 0; i < 9; i++) {
            drawGridNode(canvas, state.getGridNodeYaw(i), state.getGridNodePitch(i), i == active);
        }
    }

    private void drawGridNode(Canvas canvas, float yawDeg, float pitchDeg, boolean active) {
        float relYaw = shortest(yawDeg, state.yaw);
        float relPitch = pitchDeg - state.pitch;
        float hFov = GameState.clamp(state.fovDeg, 50f, 150f);
        float halfH = hFov * 0.5f;
        float aspect = Math.max(0.1f, getWidth() / (float)Math.max(1, getHeight()));
        double vFov = 2.0 * Math.atan(Math.tan(Math.toRadians(hFov) * 0.5) / aspect);
        float halfV = (float)Math.toDegrees(vFov) * 0.5f;
        if (Math.abs(relYaw) > halfH + 8f || Math.abs(relPitch) > halfV + 8f) return;

        float x = projectX(relYaw, halfH);
        float y = projectY(relPitch, halfV);
        float targetRadius = (float)(Math.tan(Math.toRadians(Math.max(0.2f, state.gridTargetAngularDiameter) * 0.5))
                * getWidth() * 0.5 / Math.tan(Math.toRadians(halfH)));
        targetRadius = Math.max(4f, targetRadius);

        if (active) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffff5f56);
            canvas.drawCircle(x, y, targetRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f);
            paint.setColor(0xffffddd9);
            canvas.drawCircle(x, y, targetRadius, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(0x667f8ea3);
            canvas.drawCircle(x, y, Math.max(3f, targetRadius * 0.42f), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

"""
if marker not in s:
    raise RuntimeError("missing grid renderer marker")
s = s.replace(marker, grid_renderer + marker, 1)
write("AimCanvasView.java", s)

# ---------------- MainActivity.java ----------------
s = read("MainActivity.java")
s = s.replace("GameState.Mode.RECOIL", "GameState.Mode.GRID_SHOOT")
s = s.replace('dlg.setTitle("设置 v0.2.14");', 'dlg.setTitle("设置 v0.2.15");')

old_cycle = """            if (state.mode == GameState.Mode.TRACKING) {
                state.mode = GameState.Mode.SIX_TARGETS;
            } else if (state.mode == GameState.Mode.SIX_TARGETS) {
                state.mode = GameState.Mode.ARC;
            } else if (state.mode == GameState.Mode.ARC) {
                state.mode = GameState.Mode.GRID_SHOOT;
            } else {
                state.mode = GameState.Mode.TRACKING;
            }"""
new_cycle = """            if (state.mode == GameState.Mode.TRACKING) {
                state.mode = GameState.Mode.SIX_TARGETS;
            } else if (state.mode == GameState.Mode.SIX_TARGETS) {
                state.mode = GameState.Mode.ARC;
            } else if (state.mode == GameState.Mode.ARC) {
                state.mode = GameState.Mode.GRID_SHOOT;
            } else if (state.mode == GameState.Mode.GRID_SHOOT) {
                state.mode = GameState.Mode.TRANSFER_TRACK;
            } else {
                state.mode = GameState.Mode.TRACKING;
            }"""
s = replace_once(s, old_cycle, new_cycle, "mode cycle")

old_load = """        state.recoilTargetAngularDiameter = clamp(
                prefs.getFloat("recoil_target_size", state.recoilTargetAngularDiameter),
                0.6f, 20f);
        state.nemesisRecoilMultiplier = clamp(
                prefs.getFloat("nemesis_recoil_multiplier", state.nemesisRecoilMultiplier),
                0f, 3f);
"""
new_load = """        state.gridTargetAngularDiameter = clamp(prefs.getFloat("grid_target_size", state.gridTargetAngularDiameter), 0.2f, 20f);
        state.gridWidthDeg = clamp(prefs.getFloat("grid_width", state.gridWidthDeg), 20f, 140f);
        state.gridHeightDeg = clamp(prefs.getFloat("grid_height", state.gridHeightDeg), 12f, 80f);

        state.transferTargetAngularDiameter = clamp(prefs.getFloat("transfer_target_size", state.transferTargetAngularDiameter), 0.2f, 20f);
        state.transferSpeedDegPerSec = clamp(prefs.getFloat("transfer_speed", state.transferSpeedDegPerSec), 0.1f, 180f);
        state.transferIntervalSec = clamp(prefs.getFloat("transfer_interval", state.transferIntervalSec), 0.60f, 10f);
        state.transferMinAngleDeg = clamp(prefs.getFloat("transfer_min_angle", state.transferMinAngleDeg), 8f, 70f);
        state.transferYawSpanDeg = clamp(prefs.getFloat("transfer_yaw_span", state.transferYawSpanDeg), 20f, 140f);
        state.transferPitchSpanDeg = clamp(prefs.getFloat("transfer_pitch_span", state.transferPitchSpanDeg), 12f, 80f);
"""
s = replace_once(s, old_load, new_load, "load special settings")

old_save = """                .putFloat("recoil_target_size", state.recoilTargetAngularDiameter)
                .putFloat("nemesis_recoil_multiplier", state.nemesisRecoilMultiplier)
"""
new_save = """                .putFloat("grid_target_size", state.gridTargetAngularDiameter)
                .putFloat("grid_width", state.gridWidthDeg)
                .putFloat("grid_height", state.gridHeightDeg)
                .putFloat("transfer_target_size", state.transferTargetAngularDiameter)
                .putFloat("transfer_speed", state.transferSpeedDegPerSec)
                .putFloat("transfer_interval", state.transferIntervalSec)
                .putFloat("transfer_min_angle", state.transferMinAngleDeg)
                .putFloat("transfer_yaw_span", state.transferYawSpanDeg)
                .putFloat("transfer_pitch_span", state.transferPitchSpanDeg)
"""
s = replace_once(s, old_save, new_save, "save special settings")

old_categories = """        LinearLayout r4 = categoryRow(root);
        addCategoryButton(r4, "压枪 / 微调", v -> showRecoilSettings());
        addCategoryButton(r4, "手柄 / 校准", v -> showControllerSettings());"""
new_categories = """        LinearLayout r4 = categoryRow(root);
        addCategoryButton(r4, "网格射击", v -> showGridShootSettings());
        addCategoryButton(r4, "目标转移跟枪", v -> showTransferTrackSettings());

        LinearLayout r5 = categoryRow(root);
        addCategoryButton(r5, "手柄 / 校准", v -> showControllerSettings());"""
s = replace_once(s, old_categories, new_categories, "settings categories")

# Replace recoil settings method with the two new settings pages.
pattern = re.compile(r'    private void showRecoilSettings\(\) \{.*?\n    \}\n\n    private void showArcSettings\(\) \{', re.S)
m = pattern.search(s)
if not m:
    raise RuntimeError("missing showRecoilSettings method")
new_methods = """    private void showGridShootSettings() {
        SettingsPage p = createSettingsPage("网格射击");

        p.box.addView(note("固定3×3世界网格。只显示一个主目标；命中后会优先跳到较远节点，用来练大幅度推摇杆、刹停和落点。"));

        final EditText targetSize = field("主目标角直径 °", state.gridTargetAngularDiameter);
        addSetting(p.box, targetSize, "命中目标大小，范围0.2–20°。");

        final EditText gridW = field("网格总宽度 °", state.gridWidthDeg);
        addSetting(p.box, gridW, "左列到右列的总角距离。建议60–90°练大幅推停，范围20–140°。");

        final EditText gridH = field("网格总高度 °", state.gridHeightDeg);
        addSetting(p.box, gridH, "上排到下排的总角距离，范围12–80°。");

        p.save.setOnClickListener(v -> {
            state.gridTargetAngularDiameter = clamp(parse(targetSize, state.gridTargetAngularDiameter), 0.2f, 20f);
            state.gridWidthDeg = clamp(parse(gridW, state.gridWidthDeg), 20f, 140f);
            state.gridHeightDeg = clamp(parse(gridH, state.gridHeightDeg), 12f, 80f);
            if (state.mode == GameState.Mode.GRID_SHOOT) state.resetTargets();
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showTransferTrackSettings() {
        SettingsPage p = createSettingsPage("目标转移跟枪");

        p.box.addView(note("目标持续运动；到达转移时间后，会跳到一个离当前准心较远的新位置并立即继续运动。训练快速转移后立刻接持续跟枪。"));

        final EditText targetSize = field("目标角直径 °", state.transferTargetAngularDiameter);
        addSetting(p.box, targetSize, "转移跟枪目标大小，范围0.2–20°。");

        final EditText speed = field("跟枪速度 °/s", state.transferSpeedDegPerSec);
        addSetting(p.box, speed, "转移后目标持续移动的球面角速度，范围0.1–180°/s。");

        final EditText interval = field("转移间隔 秒", state.transferIntervalSec);
        addSetting(p.box, interval, "每隔多久进行一次大幅目标转移，范围0.60–10秒。");

        final EditText minAngle = field("最小转移角度 °", state.transferMinAngleDeg);
        addSetting(p.box, minAngle, "新位置尽量与当前准心至少相隔这个角度，范围8–70°。");

        final EditText yawSpan = field("转移横向范围 °", state.transferYawSpanDeg);
        addSetting(p.box, yawSpan, "以当前准心为中心可抽取的新目标横向总范围，范围20–140°。");

        final EditText pitchSpan = field("转移纵向范围 °", state.transferPitchSpanDeg);
        addSetting(p.box, pitchSpan, "以当前准心为中心可抽取的新目标纵向总范围，范围12–80°。");

        p.save.setOnClickListener(v -> {
            state.transferTargetAngularDiameter = clamp(parse(targetSize, state.transferTargetAngularDiameter), 0.2f, 20f);
            state.transferSpeedDegPerSec = clamp(parse(speed, state.transferSpeedDegPerSec), 0.1f, 180f);
            state.transferIntervalSec = clamp(parse(interval, state.transferIntervalSec), 0.60f, 10f);
            state.transferMinAngleDeg = clamp(parse(minAngle, state.transferMinAngleDeg), 8f, 70f);
            state.transferYawSpanDeg = clamp(parse(yawSpan, state.transferYawSpanDeg), 20f, 140f);
            state.transferPitchSpanDeg = clamp(parse(pitchSpan, state.transferPitchSpanDeg), 12f, 80f);
            if (state.mode == GameState.Mode.TRANSFER_TRACK) state.resetTargets();
            saveCategory(p, false);
        });

        showSettingsPage(p);
    }

    private void showArcSettings() {"""
s = s[:m.start()] + new_methods + s[m.end():]

old_mode_label = """        if (state.mode == GameState.Mode.TRACKING) name = "跟枪";
        else if (state.mode == GameState.Mode.SIX_TARGETS) name = "六目标";
        else if (state.mode == GameState.Mode.ARC) name = "弧线";
        else name = "压枪微调";"""
new_mode_label = """        if (state.mode == GameState.Mode.TRACKING) name = "跟枪";
        else if (state.mode == GameState.Mode.SIX_TARGETS) name = "六目标";
        else if (state.mode == GameState.Mode.ARC) name = "弧线";
        else if (state.mode == GameState.Mode.GRID_SHOOT) name = "网格射击";
        else name = "目标转移跟枪";"""
s = replace_once(s, old_mode_label, new_mode_label, "mode button label")

old_result_names = """        if (state.getTrainingMode() == GameState.Mode.TRACKING) modeName = "跟枪";
        else if (state.getTrainingMode() == GameState.Mode.ARC) modeName = "弧线";
        else if (state.getTrainingMode() == GameState.Mode.GRID_SHOOT) modeName = "压枪微调";
        else modeName = "六目标";"""
new_result_names = """        if (state.getTrainingMode() == GameState.Mode.TRACKING) modeName = "跟枪";
        else if (state.getTrainingMode() == GameState.Mode.ARC) modeName = "弧线";
        else if (state.getTrainingMode() == GameState.Mode.GRID_SHOOT) modeName = "网格射击";
        else if (state.getTrainingMode() == GameState.Mode.TRANSFER_TRACK) modeName = "目标转移跟枪";
        else modeName = "六目标";"""
s = replace_once(s, old_result_names, new_result_names, "result names")

# Grid uses shot/accuracy score like six-target.
s = s.replace(
    "if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS) {",
    "if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS || state.getTrainingMode() == GameState.Mode.GRID_SHOOT) {",
    1
)

write("MainActivity.java", s)

# ---------------- HudView.java ----------------
s = read("HudView.java")
s = s.replace("GameState.Mode.RECOIL", "GameState.Mode.GRID_SHOOT")

# line1 special branch
old_line1 = """        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            line1 = String.format(Locale.US,
                    "压枪微调  静止三层靶 %.2f°  复仇女神 T%d/35",
                    state.recoilTargetAngularDiameter,
                    state.weaponCurrentShot);
        } else {"""
new_line1 = """        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            line1 = String.format(Locale.US,
                    "网格射击  3×3  范围 %.0f×%.0f°  球 %.2f°  命中 %d/%d",
                    state.gridWidthDeg,
                    state.gridHeightDeg,
                    state.gridTargetAngularDiameter,
                    state.hits,
                    state.shots);
        } else if (state.mode == GameState.Mode.TRANSFER_TRACK) {
            line1 = String.format(Locale.US,
                    "目标转移跟枪  速度 %.1f°/s  球 %.2f°",
                    state.transferSpeedDegPerSec,
                    state.transferTargetAngularDiameter);
        } else {"""
s = replace_once(s, old_line1, new_line1, "HUD line1")

old_line2 = """        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            line2 = String.format(Locale.US,
                    "后坐×%.2f  剩余%d  中心红 / 二层黄 / 三层蓝",
                    state.nemesisRecoilMultiplier,
                    state.weaponMagazineRemaining);
        } else {"""
new_line2 = """        } else if (state.mode == GameState.Mode.GRID_SHOOT) {
            line2 = String.format(Locale.US,
                    "当前节点 %d/9  大幅推停：命中后跳远点  RX %.3f RY %.3f",
                    state.getGridIndex() + 1,
                    input.getRightX(),
                    input.getRightY());
        } else if (state.mode == GameState.Mode.TRANSFER_TRACK) {
            line2 = String.format(Locale.US,
                    "转移倒计时 %.1fs  最小转移 %.0f°  RX %.3f RY %.3f",
                    state.getTransferTimerSec(),
                    state.transferMinAngleDeg,
                    input.getRightX(),
                    input.getRightY());
        } else {"""
s = replace_once(s, old_line2, new_line2, "HUD line2")

# Remove recoil weapon detail block.
pattern = re.compile(r'        float trainingY = 84f;\n        if \(state\.mode == GameState\.Mode\.GRID_SHOOT\) \{.*?\n        \}\n', re.S)
m = pattern.search(s)
if not m:
    raise RuntimeError("missing old weapon HUD block")
s = s[:m.start()] + "        float trainingY = 84f;\n" + s[m.end():]

s = s.replace(
    "if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS) {",
    "if (state.getTrainingMode() == GameState.Mode.SIX_TARGETS || state.getTrainingMode() == GameState.Mode.GRID_SHOOT) {",
    1
)
write("HudView.java", s)

print("v0.2.15 source patch applied")
