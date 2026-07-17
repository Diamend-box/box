package com.diamend.anticheat.check;

/**
 * Outcome of running a check for a single event. A failing result carries how
 * much to add to the player's violation level and a short human-readable detail
 * string for alerts / verbose output.
 */
public record CheckResult(boolean failed, double amount, String detail) {

    private static final CheckResult PASS = new CheckResult(false, 0.0D, "");

    public static CheckResult pass() {
        return PASS;
    }

    public static CheckResult fail(double amount, String detail) {
        return new CheckResult(true, amount, detail);
    }
}
