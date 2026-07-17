package com.diamend.anticheat.util;

/**
 * Small numeric helpers used by the combat checks. Everything here is pure and
 * side-effect free so it can be unit-tested without a running server.
 */
public final class MathUtil {

    private MathUtil() {
    }

    /** Arithmetic mean of the given values. Returns 0 for an empty array. */
    public static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Population standard deviation. Returns 0 for arrays of length &lt; 2. */
    public static double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0.0D;
        }
        double mean = mean(values);
        double sumSq = 0.0D;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.length);
    }

    /**
     * Coefficient of variation (standard deviation / mean). This is a
     * scale-independent measure of "how regular" a series of intervals is:
     * a value near 0 means the intervals are almost identical, which is a
     * strong autoclicker signal. Returns {@link Double#MAX_VALUE} when the
     * mean is 0 so callers treat "no meaningful data" as non-suspicious.
     */
    public static double coefficientOfVariation(double[] values) {
        double mean = mean(values);
        if (mean <= 0.0D) {
            return Double.MAX_VALUE;
        }
        return standardDeviation(values) / mean;
    }

    /** Greatest common divisor of two non-negative longs. */
    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Wrap an angle delta into the range (-180, 180]. Yaw in Minecraft is
     * unbounded and wraps around, so the raw difference between two yaw values
     * has to be normalised before it means anything.
     */
    public static float wrapDegrees(float delta) {
        float d = delta % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }

    /** Clamp a double into the inclusive range [min, max]. */
    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
