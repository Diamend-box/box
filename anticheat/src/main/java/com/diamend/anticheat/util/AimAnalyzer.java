package com.diamend.anticheat.util;

/**
 * Rotation analysis used by the aim check.
 *
 * <p>The primary technique is GCD (greatest-common-divisor) analysis. A player
 * turning with a mouse produces rotation deltas whose values, expressed in the
 * game's fixed-point angle units, share no meaningful common divisor. Many
 * aim-assist / aimbot implementations compute rotations arithmetically and
 * leave behind deltas that are near-exact multiples of a small step, so their
 * GCD is conspicuously large. This is a heuristic, not proof, so the aim check
 * treats it conservatively.
 */
public final class AimAnalyzer {

    private AimAnalyzer() {
    }

    /**
     * Minecraft encodes rotation on the wire as a byte (256 steps per 360
     * degrees) but the client works in degrees. We scale to a fixed-point
     * integer with this many units per degree before taking the GCD so that
     * sub-degree structure is preserved.
     */
    private static final long ANGLE_SCALE = 1_000_000L;

    /**
     * GCD of the two most recent non-zero rotation deltas, expressed in scaled
     * fixed-point units. A large shared divisor across genuinely different
     * deltas is the suspicious signal. Returns 0 when there is not enough
     * movement to judge.
     *
     * @param deltaA a rotation delta in degrees (e.g. yaw change)
     * @param deltaB a second, different rotation delta in degrees
     */
    public static long rotationGcd(float deltaA, float deltaB) {
        long a = Math.round(Math.abs(deltaA) * ANGLE_SCALE);
        long b = Math.round(Math.abs(deltaB) * ANGLE_SCALE);
        if (a == 0 || b == 0) {
            return 0L;
        }
        return MathUtil.gcd(a, b);
    }

    /**
     * True when a pitch value is outside the range the vanilla client can
     * produce. The client clamps pitch to [-90, 90]; anything beyond that was
     * fabricated.
     */
    public static boolean isImpossiblePitch(float pitch) {
        return pitch > 90.0F || pitch < -90.0F;
    }
}
