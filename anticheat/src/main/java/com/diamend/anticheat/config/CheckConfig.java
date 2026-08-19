package com.diamend.anticheat.config;

import java.util.List;

import com.diamend.anticheat.violation.PunishmentAction;

/**
 * Per-check settings loaded from config.yml. {@code settings} holds the
 * check-specific numeric knobs (e.g. reach distance, min CPS) keyed by name;
 * checks read them through the typed accessors.
 */
public final class CheckConfig {

    private final boolean enabled;
    private final double alertThreshold;
    private final java.util.Map<String, Double> settings;
    private final List<PunishmentAction> actions;

    public CheckConfig(boolean enabled, double alertThreshold,
                       java.util.Map<String, Double> settings,
                       List<PunishmentAction> actions) {
        this.enabled = enabled;
        this.alertThreshold = alertThreshold;
        this.settings = settings;
        this.actions = actions;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Violation level at which staff start receiving (non-verbose) alerts. */
    public double alertThreshold() {
        return alertThreshold;
    }

    public List<PunishmentAction> actions() {
        return actions;
    }

    /** Read a numeric setting, falling back to {@code def} when absent. */
    public double get(String key, double def) {
        Double v = settings.get(key);
        return v == null ? def : v;
    }
}
