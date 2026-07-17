package com.diamend.anticheat.violation;

import java.util.Locale;

/**
 * A single action that can be attached to a violation threshold, e.g.
 * "at 15 violations, run: kick %player% Cheating". Actions are only carried
 * out when the plugin is in {@link PunishmentMode#ENFORCE} — in
 * {@link PunishmentMode#ALERT_ONLY} they are parsed and logged but not run.
 */
public record PunishmentAction(int threshold, Type type, String argument) {

    public enum Type {
        /** Cancel the offending packet/event (e.g. deny the hit). */
        CANCEL,
        /** Run a console command; {@code %player%} is substituted. */
        COMMAND,
        /** Kick the player with {@link #argument} as the reason. */
        KICK,
        /** Ban the player with {@link #argument} as the reason. */
        BAN
    }

    /**
     * Parse one config line of the form {@code "<threshold> <type> [argument]"},
     * e.g. {@code "20 kick Suspicious combat"} or {@code "10 cancel"}.
     * Returns {@code null} for a line that cannot be understood.
     */
    public static PunishmentAction parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length < 2) {
            return null;
        }
        int threshold;
        try {
            threshold = Integer.parseInt(parts[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String argument = parts.length == 3 ? parts[2] : "";
        return new PunishmentAction(threshold, type, argument);
    }
}
