package com.diamend.boxcore.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human durations: {@code 30m}, {@code 2h30m}, {@code 1d}.
 *
 * <p>Config and commands both take the short form, because "how long is this
 * boost" is a question people answer in minutes and hours, not milliseconds.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(?i)(\\d+)\\s*([dhms])");

    private Durations() {
    }

    /**
     * Parses {@code 90s}, {@code 30m}, {@code 2h30m}, {@code 1d} to millis.
     *
     * <p>A bare number is read as minutes, since that is what a server owner
     * typing {@code /box boost global drops 2 30} almost certainly means.
     *
     * @return the duration in millis, or -1 when the text names no duration
     */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(trimmed) * 60_000L;
        }
        Matcher matcher = PART.matcher(trimmed);
        long total = 0;
        int matchedChars = 0;
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2).charAt(0)) {
                case 'd' -> value * 86_400_000L;
                case 'h' -> value * 3_600_000L;
                case 'm' -> value * 60_000L;
                default -> value * 1_000L;
            };
            matchedChars += matcher.group().length();
        }
        // Reject "30x" or "abc" rather than silently reading them as zero.
        return matchedChars == trimmed.replace(" ", "").length() && total > 0 ? total : -1;
    }

    /**
     * Formats millis for display: {@code 2d 3h}, {@code 29m 55s}, {@code 8s}.
     *
     * <p>Two units at most — a boost with 1h 59m 58s left is "1h 59m", and the
     * seconds are noise at that range.
     */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
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
}
