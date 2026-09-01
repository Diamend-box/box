package com.diamend.spyglass.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The small formatting decisions a readable report is made of: how long ago
 * that was, how many blocks that is, how much of a number to show.
 */
public final class Fmt {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // Millisecond precision so two dumps in the same second get different
    // names, and so those names sort in the order they were written.
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    private Fmt() {
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /** {@code 2026-08-13 04:35:12}, or "never" for a zero timestamp. */
    public static String stamp(long epochMillis) {
        return epochMillis <= 0 ? "never" : STAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    /** {@code 04:35:12} — for live lines, where the date is always today. */
    public static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis));
    }

    /** {@code 20260813-043512-880} — safe in a filename, and sorts by time. */
    public static String fileStamp(long epochMillis) {
        return FILE_STAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    /** {@code 2026-08-13 04:35:12 (3d 4h ago)}. */
    public static String stampWithAge(long epochMillis) {
        if (epochMillis <= 0) {
            return "never";
        }
        long age = System.currentTimeMillis() - epochMillis;
        return stamp(epochMillis) + " (" + (age < 0 ? "in " + duration(-age) : duration(age) + " ago") + ")";
    }

    /** {@code 2d 3h}, {@code 29m 55s}, {@code 8s} — two units at most. */
    public static String duration(long millis) {
        if (millis < 1000L) {
            return Math.max(millis, 0L) + "ms";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long rest = seconds % 60L;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return rest > 0 ? minutes + "m " + rest + "s" : minutes + "m";
        }
        return rest + "s";
    }

    /** Game ticks as a duration; the server counts most of its clocks this way. */
    public static String ticks(long ticks) {
        if (ticks <= 0) {
            return "0s";
        }
        return duration(ticks * 50L) + " (" + ticks + " ticks)";
    }

    // ------------------------------------------------------------------
    // Numbers
    // ------------------------------------------------------------------

    /** A number without a trail of float noise: {@code 20}, {@code 19.5}, {@code 0.62}. */
    public static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }

    /** Three decimal places, for coordinates. */
    public static String coord(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** Thousands separators, for statistics that get big. */
    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Vanilla measures travel in centimetres; people measure it in blocks. */
    public static String centimetres(long cm) {
        if (cm >= 100_000L) {
            return String.format(Locale.ROOT, "%.2f km", cm / 100_000.0D);
        }
        return String.format(Locale.ROOT, "%.1f blocks", cm / 100.0D);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0D);
    }

    /** {@code 12/20} plus a bar, for health and similar pairs. */
    public static String bar(double value, double max, int width) {
        if (max <= 0) {
            return num(value);
        }
        int filled = (int) Math.round(Math.max(0, Math.min(1, value / max)) * width);
        return "[" + "#".repeat(filled) + "-".repeat(Math.max(0, width - filled)) + "] "
                + num(value) + "/" + num(max);
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** A component as the plain text a terminal should show for it. */
    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** {@code minecraft:diamond_sword} → {@code diamond_sword}. */
    public static String shortKey(String key) {
        if (key == null) {
            return "";
        }
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** Caps a value so one absurd string can't own the console. */
    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
