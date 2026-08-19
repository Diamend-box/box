package com.diamend.anticheat.violation;

/**
 * How the anticheat reacts when a player's violation level crosses a check's
 * configured threshold.
 *
 * <p>This is the toggle the design calls for: {@link #ALERT_ONLY} is the
 * default and only ever notifies staff / writes to the log, while
 * {@link #ENFORCE} additionally runs the configured punishment actions
 * (cancel, command, kick, ban).
 */
public enum PunishmentMode {

    /** Flag, alert staff and log — but never punish. The safe default. */
    ALERT_ONLY,

    /** Alert and log, and also carry out the configured punishment actions. */
    ENFORCE;

    /**
     * Parse a config string. Unknown / missing values fall back to the safe
     * {@link #ALERT_ONLY} default rather than silently enabling punishments.
     */
    public static PunishmentMode fromConfig(String raw) {
        if (raw == null) {
            return ALERT_ONLY;
        }
        return switch (raw.trim().toLowerCase()) {
            case "enforce", "full", "punish" -> ENFORCE;
            default -> ALERT_ONLY;
        };
    }
}
