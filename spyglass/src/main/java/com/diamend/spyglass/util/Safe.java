package com.diamend.spyglass.util;

import java.util.function.Supplier;

/**
 * Runs one piece of a report and shrugs if it fails.
 *
 * <p>A diagnostic tool that dies on the one field a fork implements differently
 * is worse than useless — you lose the other ninety fields you actually came
 * for. Every value in a report goes through here, so an unsupported call shows
 * up as {@code n/a} on its own line and the rest of the report still prints.
 */
public final class Safe {

    /** What an unavailable value looks like in a report. */
    public static final String UNKNOWN = "n/a";

    private Safe() {
    }

    /** The supplier's value, or the fallback if it throws or returns null. */
    public static <T> T call(Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (Throwable ex) {
            return fallback;
        }
    }

    /** The supplier's value as text, or {@code n/a}. */
    public static String text(Supplier<?> supplier) {
        Object value = call(supplier::get, null);
        return value == null ? UNKNOWN : String.valueOf(value);
    }

    public static int integer(Supplier<Integer> supplier, int fallback) {
        return call(supplier, fallback);
    }

    public static double number(Supplier<Double> supplier, double fallback) {
        return call(supplier, fallback);
    }

    public static boolean flag(Supplier<Boolean> supplier, boolean fallback) {
        return call(supplier, fallback);
    }

    /** Runs a step that has no value, swallowing whatever it throws. */
    public static void run(Runnable step) {
        try {
            step.run();
        } catch (Throwable ignored) {
            // The caller is building a report; a broken section is not fatal.
        }
    }
}
