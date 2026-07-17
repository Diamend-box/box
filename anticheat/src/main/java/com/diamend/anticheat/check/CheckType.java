package com.diamend.anticheat.check;

/**
 * The combat checks shipped in v1. The {@code configKey} is the section name
 * under {@code checks:} in config.yml.
 */
public enum CheckType {

    REACH("reach", "Reach"),
    AUTOCLICKER("autoclicker", "AutoClicker"),
    AIM("aim", "Aim"),
    KILLAURA("killaura", "KillAura");

    private final String configKey;
    private final String displayName;

    CheckType(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }
}
