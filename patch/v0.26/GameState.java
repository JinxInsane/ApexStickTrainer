package com.openai.apexsticktrainer;

import java.util.Random;

public class GameState {
    public enum Mode { TRACKING, SIX_TARGETS, ARC }
    public enum SpeedMode { FIXED, RANDOM }

    public volatile Mode mode = Mode.TRACKING;

    // 跟枪模式
    public volatile SpeedMode speedMode = SpeedMode.RANDOM;
    public volatile float fixedSpeed = 24f;
    public volatile float randomSpeedMin = 10f;
    public volatile float randomSpeedMax = 42f;
    public volatile float directionChangeWeightPct = 12f;
    public volatile float directionReverseWeightPct = 12f; // legacy alias
    public volatile float speedChangeWeightMinPct = 20f;
    public volatile float speedChangeWeightMaxPct = 55f;
    public volatile float speedChangeIntervalMinSec = 0.55f;
    public volatile float speedChangeIntervalMaxSec = 1.20f;
    public volatile float trackingRadius = 8.0f;
    public volatile float trackingPitchLimitDeg = 62f;
    public volatile float trackingTargetAngularDiameter = 3.20f;

    // 弧线模式：拥有跟枪模式同级的独立控制 + 曲率控制
    public volatile SpeedMode arcSpeedMode = SpeedMode.RANDOM;
    public volatile float arcFixedSpeed = 26f;
    public volatile float arcRandomSpeedMin = 10f;
    public volatile float arcRandomSpeedMax = 42f;
    public volatile float arcDirectionChangeWeightPct = 10f;
    public volatile float arcSpeedChangeWeightMinPct = 20f;
    public volatile float arcSpeedChangeWeightMaxPct = 55f;
    public volatile float arcEventIntervalMinSec = 0.55f;
    public volatile float arcEventIntervalMaxSec = 1.20f;
    public volatile float arcPitchLimitDeg = 62f;
    public volatile float arcTargetAngularDiameter = 3.20f;

    // 弧线半径越小，弧越紧；越大，弧越平缓。
    public volatile float arcRadiusMinDeg = 12f;
    public volatile float arcRadiusMaxDeg = 34f;
    // 每次随机事件时，发生“弧度/曲率变化”的概率。
    public volatile float arcCurvatureChangeWeightPct = 35f;
    // 发生曲率变化时，从当前半径向新随机半径靠近多少比例。
    public volatile float arcCurvatureBlendMinPct = 20f;
    public volatile float arcCurvatureBlendMaxPct = 60f;

    // 六目标模式
    public volatile float sixTargetAngularDiameter = 1.55f;
    public volatile float targetAngularDiameter = 1.55f; // legacy field
    public volatile float sixBoundaryWidthDeg = 68f;
    public volatile float sixBoundaryHeightDeg = 38f;
    public volatile float sixTargetDistance = 9.0f;

    // 通用/镜头
    public volatile float fovDeg = 120f;
    public volatile float hipSensitivity = 4f;
    public volatile float adsSensitivity = 3f;
    public volatile float degreesPerSecondPerSensitivity = 60f;
    public volatile float responseExponent = 1.00f;
    public volatile float deadzone = 0f;
    public volatile float referenceStrengthPct = 42f;
    public volatile float backgroundBrightnessPct = 18f;

    public volatile float yaw = 0f;
    public volatile float pitch = 0f;
    public volatile int shots = 0;
    public volatile int hits = 0;

    public static class Target {
        public float yawDeg;
        public float pitchDeg;
        public float distance;
        public boolean active = true;
    }

    public final Target tracking = new Target();
    public final Target arc = new Target();
    public final Target[] six = new Target[6];
    public final Random rng = new Random();

    // 跟枪运行态
    private float trackingSpeedDegPerSec = 24f;
    private float trackingHeadingDeg = 28f;
    private float trackingRandomTimer = 0f;
    private float lastSpeedChangeWeightPct = 0f;
    private boolean lastDirectionChanged = false;

    // 弧线运行态
    private float arcSpeedDegPerSec = 26f;
    private float arcHeadingDeg = 15f;
    private float arcRadiusDeg = 20f;
    private int arcTurnSign = 1;
    private float arcRandomTimer = 0f;
    private float lastArcSpeedWeightPct = 0f;
    private float lastArcCurvatureBlendPct = 0f;
    private boolean lastArcDirectionChanged = false;
    private boolean lastArcCurvatureChanged = false;

    // 六目标运行态
    private float sixCenterYaw = 0f;
    private float sixCenterPitch = 0f;

    public GameState() {
        for (int i = 0; i < six.length; i++) six[i] = new Target();
        resetTargets();
    }

    public synchronized void resetTargets() {
        if (mode == Mode.TRACKING) {
            tracking.yawDeg = wrap180(yaw + 28f);
            tracking.pitchDeg = clamp(pitch + 6f, -35f, 35f);
            tracking.distance = Math.max(2f, trackingRadius);
            trackingSpeedDegPerSec = speedMode == SpeedMode.RANDOM
                    ? randomInOrderedRange(randomSpeedMin, randomSpeedMax, 0.1f)
                    : Math.max(0.1f, fixedSpeed);
            trackingHeadingDeg = rng.nextBoolean() ? 28f : 152f;
            trackingRandomTimer = nextTrackingEventInterval();
            lastSpeedChangeWeightPct = 0f;
            lastDirectionChanged = false;
        } else if (mode == Mode.ARC) {
            arc.yawDeg = wrap180(yaw + 28f);
            arc.pitchDeg = clamp(pitch + 6f, -35f, 35f);
            arc.distance = Math.max(2f, trackingRadius);
            arcSpeedDegPerSec = arcSpeedMode == SpeedMode.RANDOM
                    ? randomInOrderedRange(arcRandomSpeedMin, arcRandomSpeedMax, 0.1f)
                    : Math.max(0.1f, arcFixedSpeed);
            arcRadiusDeg = randomInOrderedRange(arcRadiusMinDeg, arcRadiusMaxDeg, 2f);
            arcHeadingDeg = rng.nextBoolean() ? 15f : 165f;
            arcTurnSign = rng.nextBoolean() ? 1 : -1;
            arcRandomTimer = nextArcEventInterval();
            lastArcSpeedWeightPct = 0f;
            lastArcCurvatureBlendPct = 0f;
            lastArcDirectionChanged = false;
            lastArcCurvatureChanged = false;
        } else {
            sixCenterYaw = yaw;
            sixCenterPitch = clamp(pitch, -55f, 55f);
            for (Target t : six) respawnSix(t);
        }
    }

    public synchronized void update(float dt) {
        if (mode == Mode.TRACKING) {
            updateTracking(dt);
        } else if (mode == Mode.ARC) {
            updateArc(dt);
        }
    }

    private void updateTracking(float dt) {
        tracking.distance = Math.max(2.0f, trackingRadius);

        if (speedMode == SpeedMode.RANDOM) {
            trackingRandomTimer -= dt;
            if (trackingRandomTimer <= 0f) {
                applyTrackingRandomEvent();
                trackingRandomTimer = nextTrackingEventInterval();
            }
        } else {
            trackingSpeedDegPerSec = Math.max(0.1f, fixedSpeed);
        }

        moveTargetOnSphere(tracking, trackingSpeedDegPerSec, dt, false);
    }

    private void updateArc(float dt) {
        arc.distance = Math.max(2.0f, trackingRadius);

        arcRandomTimer -= dt;
        if (arcRandomTimer <= 0f) {
            applyArcRandomEvent();
            arcRandomTimer = nextArcEventInterval();
        }

        if (arcSpeedMode == SpeedMode.FIXED) {
            arcSpeedDegPerSec = Math.max(0.1f, arcFixedSpeed);
        }

        // 始终存在曲率：heading每一帧都连续旋转，因此轨迹永远不是直线。
        float radius = Math.max(2f, arcRadiusDeg);
        float turnRateDegPerSec = (arcSpeedDegPerSec / radius) * 57.29578f;
        arcHeadingDeg = normalize360(arcHeadingDeg + arcTurnSign * turnRateDegPerSec * dt);

        moveTargetOnSphere(arc, arcSpeedDegPerSec, dt, true);
    }

    private void moveTargetOnSphere(Target t, float speedDegPerSec, float dt, boolean arcMode) {
        float headingDeg = arcMode ? arcHeadingDeg : trackingHeadingDeg;
        float heading = (float)Math.toRadians(headingDeg);
        float pitchRad = (float)Math.toRadians(t.pitchDeg);
        float cosPitch = Math.max(0.28f, Math.abs((float)Math.cos(pitchRad)));

        float yawRate = (float)Math.cos(heading) * speedDegPerSec / cosPitch;
        float pitchRate = (float)Math.sin(heading) * speedDegPerSec;

        t.yawDeg = wrap180(t.yawDeg + yawRate * dt);
        t.pitchDeg += pitchRate * dt;

        float limit = clamp(arcMode ? arcPitchLimitDeg : trackingPitchLimitDeg, 25f, 82f);
        if (t.pitchDeg > limit) {
            t.pitchDeg = limit - (t.pitchDeg - limit);
            if (arcMode) {
                arcHeadingDeg = normalize360(-arcHeadingDeg);
                arcTurnSign = -arcTurnSign;
            } else {
                trackingHeadingDeg = normalize360(-trackingHeadingDeg);
            }
        } else if (t.pitchDeg < -limit) {
            t.pitchDeg = -limit + (-limit - t.pitchDeg);
            if (arcMode) {
                arcHeadingDeg = normalize360(-arcHeadingDeg);
                arcTurnSign = -arcTurnSign;
            } else {
                trackingHeadingDeg = normalize360(-trackingHeadingDeg);
            }
        }
    }

    private void applyTrackingRandomEvent() {
        float lo = Math.max(0.1f, Math.min(randomSpeedMin, randomSpeedMax));
        float hi = Math.max(lo, Math.max(randomSpeedMin, randomSpeedMax));

        float candidateSpeed = lerp(lo, hi, rng.nextFloat());
        float wLo = clamp(Math.min(speedChangeWeightMinPct, speedChangeWeightMaxPct), 0f, 100f);
        float wHi = clamp(Math.max(speedChangeWeightMinPct, speedChangeWeightMaxPct), 0f, 100f);
        float weightPct = lerp(wLo, wHi, rng.nextFloat());
        trackingSpeedDegPerSec = clamp(
                lerp(trackingSpeedDegPerSec, candidateSpeed, weightPct / 100f),
                lo, hi);
        lastSpeedChangeWeightPct = weightPct;

        float chance = clamp(directionChangeWeightPct, 0f, 100f) / 100f;
        lastDirectionChanged = rng.nextFloat() < chance;
        if (lastDirectionChanged) {
            float delta = lerp(25f, 115f, rng.nextFloat());
            if (rng.nextBoolean()) delta = -delta;
            trackingHeadingDeg = normalize360(trackingHeadingDeg + delta);
        }
    }

    private void applyArcRandomEvent() {
        if (arcSpeedMode == SpeedMode.RANDOM) {
            float lo = Math.max(0.1f, Math.min(arcRandomSpeedMin, arcRandomSpeedMax));
            float hi = Math.max(lo, Math.max(arcRandomSpeedMin, arcRandomSpeedMax));
            float candidateSpeed = lerp(lo, hi, rng.nextFloat());

            float wLo = clamp(Math.min(arcSpeedChangeWeightMinPct, arcSpeedChangeWeightMaxPct), 0f, 100f);
            float wHi = clamp(Math.max(arcSpeedChangeWeightMinPct, arcSpeedChangeWeightMaxPct), 0f, 100f);
            float weightPct = lerp(wLo, wHi, rng.nextFloat());
            arcSpeedDegPerSec = clamp(
                    lerp(arcSpeedDegPerSec, candidateSpeed, weightPct / 100f),
                    lo, hi);
            lastArcSpeedWeightPct = weightPct;
        }

        // 方向权重：只决定顺/逆时针曲率是否切换。切换不会瞬移目标。
        float dirChance = clamp(arcDirectionChangeWeightPct, 0f, 100f) / 100f;
        lastArcDirectionChanged = rng.nextFloat() < dirChance;
        if (lastArcDirectionChanged) {
            arcTurnSign = -arcTurnSign;
        }

        // 弧度随机权重：决定这次事件是否重新抽一个弧线半径。
        float curveChance = clamp(arcCurvatureChangeWeightPct, 0f, 100f) / 100f;
        lastArcCurvatureChanged = rng.nextFloat() < curveChance;
        if (lastArcCurvatureChanged) {
            float rLo = Math.max(2f, Math.min(arcRadiusMinDeg, arcRadiusMaxDeg));
            float rHi = Math.max(rLo, Math.max(arcRadiusMinDeg, arcRadiusMaxDeg));
            float candidateRadius = lerp(rLo, rHi, rng.nextFloat());

            float bLo = clamp(Math.min(arcCurvatureBlendMinPct, arcCurvatureBlendMaxPct), 0f, 100f);
            float bHi = clamp(Math.max(arcCurvatureBlendMinPct, arcCurvatureBlendMaxPct), 0f, 100f);
            float blendPct = lerp(bLo, bHi, rng.nextFloat());

            arcRadiusDeg = clamp(
                    lerp(arcRadiusDeg, candidateRadius, blendPct / 100f),
                    rLo, rHi);
            lastArcCurvatureBlendPct = blendPct;
        }
    }

    public synchronized boolean shootAtCenter() {
        shots++;
        boolean hit = false;

        if (mode == Mode.TRACKING) {
            hit = angularDistanceToCamera(tracking) <= trackingTargetAngularDiameter * 0.5f;
            if (hit) hits++;
        } else if (mode == Mode.ARC) {
            hit = angularDistanceToCamera(arc) <= arcTargetAngularDiameter * 0.5f;
            if (hit) hits++;
        } else {
            for (Target t : six) {
                if (angularDistanceToCamera(t) <= sixTargetAngularDiameter * 0.5f) {
                    hit = true;
                    hits++;
                    respawnSix(t);
                    break;
                }
            }
        }
        return hit;
    }

    // 跟枪状态
    public synchronized float getTrackingSpeedSigned() { return trackingSpeedDegPerSec; }
    public synchronized float getTrackingSpeed() { return trackingSpeedDegPerSec; }
    public synchronized float getTrackingHeadingDeg() { return trackingHeadingDeg; }
    public synchronized float getLastSpeedChangeWeightPct() { return lastSpeedChangeWeightPct; }
    public synchronized boolean wasLastEventReversed() { return lastDirectionChanged; }
    public synchronized boolean wasLastDirectionChanged() { return lastDirectionChanged; }

    // 弧线状态
    public synchronized float getArcSpeed() { return arcSpeedDegPerSec; }
    public synchronized float getArcHeadingDeg() { return arcHeadingDeg; }
    public synchronized float getArcRadiusDeg() { return arcRadiusDeg; }
    public synchronized int getArcTurnSign() { return arcTurnSign; }
    public synchronized float getLastArcSpeedWeightPct() { return lastArcSpeedWeightPct; }
    public synchronized float getLastArcCurvatureBlendPct() { return lastArcCurvatureBlendPct; }
    public synchronized boolean wasLastArcDirectionChanged() { return lastArcDirectionChanged; }
    public synchronized boolean wasLastArcCurvatureChanged() { return lastArcCurvatureChanged; }

    // 六目标状态
    public synchronized float getSixCenterYaw() { return sixCenterYaw; }
    public synchronized float getSixCenterPitch() { return sixCenterPitch; }

    public synchronized float getOrbitAngle360() {
        return normalizedYaw360(tracking.yawDeg);
    }

    public synchronized float getArcYaw360() {
        return normalizedYaw360(arc.yawDeg);
    }

    private float normalizedYaw360(float v) {
        float a = v;
        while (a < 0f) a += 360f;
        while (a >= 360f) a -= 360f;
        return a;
    }

    private float angularDistanceToCamera(Target t) {
        float[] a = dirFromAngles(yaw, pitch);
        float[] b = dirFromAngles(t.yawDeg, t.pitchDeg);
        float dot = clamp(a[0]*b[0] + a[1]*b[1] + a[2]*b[2], -1f, 1f);
        return (float)Math.toDegrees(Math.acos(dot));
    }

    private float nextTrackingEventInterval() {
        return randomInterval(speedChangeIntervalMinSec, speedChangeIntervalMaxSec);
    }

    private float nextArcEventInterval() {
        return randomInterval(arcEventIntervalMinSec, arcEventIntervalMaxSec);
    }

    private float randomInterval(float a, float b) {
        float lo = Math.max(0.08f, Math.min(a, b));
        float hi = Math.max(lo, Math.max(a, b));
        return lerp(lo, hi, rng.nextFloat());
    }

    private float randomInOrderedRange(float a, float b, float floor) {
        float lo = Math.max(floor, Math.min(a, b));
        float hi = Math.max(lo, Math.max(a, b));
        return lerp(lo, hi, rng.nextFloat());
    }

    private void respawnSix(Target t) {
        float halfW = Math.max(4f, sixBoundaryWidthDeg * 0.5f);
        float halfH = Math.max(3f, sixBoundaryHeightDeg * 0.5f);
        t.yawDeg = wrap180(sixCenterYaw + lerp(-halfW, halfW, rng.nextFloat()));
        t.pitchDeg = clamp(sixCenterPitch + lerp(-halfH, halfH, rng.nextFloat()), -72f, 72f);
        t.distance = Math.max(3f, sixTargetDistance);
        t.active = true;
    }

    public static float[] dirFromAngles(float yawDeg, float pitchDeg) {
        float y = (float)Math.toRadians(yawDeg);
        float p = (float)Math.toRadians(pitchDeg);
        float cp = (float)Math.cos(p);
        return new float[]{
                cp * (float)Math.sin(y),
                -(float)Math.sin(p),
                -cp * (float)Math.cos(y)
        };
    }

    public static float wrap180(float x) {
        while (x > 180f) x -= 360f;
        while (x < -180f) x += 360f;
        return x;
    }

    public static float normalize360(float x) {
        while (x < 0f) x += 360f;
        while (x >= 360f) x -= 360f;
        return x;
    }

    public static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b-a)*t;
    }
}
