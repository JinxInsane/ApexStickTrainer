package com.openai.apexsticktrainer;

import java.util.Random;

public class GameState {
    public enum Mode { TRACKING, SIX_TARGETS }
    public enum SpeedMode { FIXED, RANDOM }

    public volatile Mode mode = Mode.TRACKING;
    public volatile SpeedMode speedMode = SpeedMode.RANDOM;

    public volatile float fovDeg = 120f;
    public volatile float hipSensitivity = 4f;
    public volatile float adsSensitivity = 3f;
    public volatile float degreesPerSecondPerSensitivity = 60f;
    public volatile float responseExponent = 1.00f;
    public volatile float deadzone = 0f;

    public volatile float fixedSpeed = 24f;
    public volatile float randomSpeedMin = 10f;
    public volatile float randomSpeedMax = 42f;
    public volatile float directionReverseWeightPct = 10f;
    public volatile float speedChangeWeightMinPct = 25f;
    public volatile float speedChangeWeightMaxPct = 70f;
    public volatile float speedChangeIntervalMinSec = 0.55f;
    public volatile float speedChangeIntervalMaxSec = 1.20f;
    public volatile float trackingRadius = 8.0f;
    public volatile float trackingPitchDeg = 0f;

    public volatile float trackingTargetAngularDiameter = 3.20f;
    public volatile float sixTargetAngularDiameter = 1.55f;

    public volatile float sixBoundaryWidthDeg = 68f;
    public volatile float sixBoundaryHeightDeg = 38f;
    public volatile float sixTargetDistance = 9.0f;

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
    public final Target[] six = new Target[6];
    public final Random rng = new Random();

    private float trackingSpeedSigned = 24f;
    private float randomTimer = 0f;
    private float sixCenterYaw = 0f;
    private float sixCenterPitch = 0f;
    private float lastSpeedChangeWeightPct = 0f;
    private boolean lastEventReversed = false;

    public GameState() {
        for (int i = 0; i < six.length; i++) six[i] = new Target();
        resetTargets();
    }

    public synchronized void resetTargets() {
        if (mode == Mode.TRACKING) {
            tracking.yawDeg = wrap180(yaw + 20f);
            tracking.pitchDeg = trackingPitchDeg;
            tracking.distance = Math.max(2f, trackingRadius);

            float start = speedMode == SpeedMode.RANDOM
                    ? randomInOrderedRange(randomSpeedMin, randomSpeedMax, 0.1f)
                    : Math.max(0.1f, fixedSpeed);
            trackingSpeedSigned = start;
            randomTimer = nextEventInterval();
            lastSpeedChangeWeightPct = 0f;
            lastEventReversed = false;
        } else {
            sixCenterYaw = yaw;
            sixCenterPitch = clamp(pitch, -55f, 55f);
            for (Target t : six) respawnSix(t);
        }
    }

    public synchronized void update(float dt) {
        if (mode != Mode.TRACKING) return;

        tracking.distance = Math.max(2.0f, trackingRadius);
        tracking.pitchDeg = trackingPitchDeg;

        if (speedMode == SpeedMode.RANDOM) {
            randomTimer -= dt;
            if (randomTimer <= 0f) {
                applyWeightedRandomSpeedEvent();
                randomTimer = nextEventInterval();
            }
        } else {
            float sign = trackingSpeedSigned < 0f ? -1f : 1f;
            trackingSpeedSigned = sign * Math.max(0.1f, fixedSpeed);
        }

        tracking.yawDeg = wrap180(tracking.yawDeg + trackingSpeedSigned * dt);
    }

    public synchronized boolean shootAtCenter() {
        shots++;
        boolean hit = false;

        if (mode == Mode.TRACKING) {
            hit = angularDistanceToCamera(tracking) <= trackingTargetAngularDiameter * 0.5f;
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

    public synchronized float getTrackingSpeedSigned() { return trackingSpeedSigned; }
    public synchronized float getSixCenterYaw() { return sixCenterYaw; }
    public synchronized float getSixCenterPitch() { return sixCenterPitch; }
    public synchronized float getLastSpeedChangeWeightPct() { return lastSpeedChangeWeightPct; }
    public synchronized boolean wasLastEventReversed() { return lastEventReversed; }

    public synchronized float getOrbitAngle360() {
        float a = tracking.yawDeg;
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

    private void applyWeightedRandomSpeedEvent() {
        float lo = Math.min(randomSpeedMin, randomSpeedMax);
        float hi = Math.max(randomSpeedMin, randomSpeedMax);
        lo = Math.max(0.1f, lo);
        hi = Math.max(lo, hi);

        float currentMag = clamp(Math.abs(trackingSpeedSigned), lo, hi);
        float candidateMag = lerp(lo, hi, rng.nextFloat());

        float wLo = clamp(Math.min(speedChangeWeightMinPct, speedChangeWeightMaxPct), 0f, 100f);
        float wHi = clamp(Math.max(speedChangeWeightMinPct, speedChangeWeightMaxPct), 0f, 100f);
        float weightPct = lerp(wLo, wHi, rng.nextFloat());
        float weight = weightPct / 100f;

        float nextMag = lerp(currentMag, candidateMag, weight);

        float sign = trackingSpeedSigned < 0f ? -1f : 1f;
        float reverseChance = clamp(directionReverseWeightPct, 0f, 100f) / 100f;
        boolean reverse = rng.nextFloat() < reverseChance;
        if (reverse) sign = -sign;

        trackingSpeedSigned = sign * clamp(nextMag, lo, hi);
        lastSpeedChangeWeightPct = weightPct;
        lastEventReversed = reverse;
    }

    private float nextEventInterval() {
        float lo = Math.max(0.08f, Math.min(speedChangeIntervalMinSec, speedChangeIntervalMaxSec));
        float hi = Math.max(lo, Math.max(speedChangeIntervalMinSec, speedChangeIntervalMaxSec));
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

    public static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
