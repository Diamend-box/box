package com.diamend.anticheat.util;

/**
 * Turns a series of click timestamps (in milliseconds, oldest first) into the
 * two numbers the autoclicker check cares about:
 * <ul>
 *   <li>the clicks-per-second rate over the window, and</li>
 *   <li>the coefficient of variation of the gaps between clicks.</li>
 * </ul>
 * A human's click cadence is noisy, so its coefficient of variation stays
 * comfortably above zero. Many autoclickers fire on an almost fixed interval,
 * producing an unnaturally low value.
 */
public final class CpsAnalyzer {

    private CpsAnalyzer() {
    }

    /** Result of analysing a click window. */
    public record Result(double cps, double coefficientOfVariation, int sampleCount) {
    }

    /**
     * @param timestamps click times in ms, oldest first (at least 2 for a
     *                   meaningful result).
     */
    public static Result analyze(long[] timestamps) {
        if (timestamps.length < 2) {
            return new Result(0.0D, Double.MAX_VALUE, timestamps.length);
        }
        double[] gaps = new double[timestamps.length - 1];
        for (int i = 1; i < timestamps.length; i++) {
            gaps[i - 1] = timestamps[i] - timestamps[i - 1];
        }
        double meanGap = MathUtil.mean(gaps);
        double cps = meanGap > 0.0D ? 1000.0D / meanGap : 0.0D;
        double cov = MathUtil.coefficientOfVariation(gaps);
        return new Result(cps, cov, timestamps.length);
    }
}
