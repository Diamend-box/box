package com.diamend.anticheat.check;

/**
 * Every detection the anticheat ships. The {@code configKey} is the section
 * name under {@code checks:} in config.yml; the {@code category} groups checks
 * for the GUI and for coarse enable/disable.
 */
public enum CheckType {

    // --- Combat (Killaura / Reach / autoclicker) --------------------------
    REACH("reach", "Reach", Category.COMBAT),
    AUTOCLICKER("autoclicker", "AutoClicker", Category.COMBAT),
    AIM("aim", "Aim", Category.COMBAT),
    KILLAURA("killaura", "KillAura", Category.COMBAT),

    // --- Movement (Fly / Speed / Timer / NoFall / Vehicle) ----------------
    FLIGHT("flight", "Flight", Category.MOVEMENT),
    SPEED("speed", "Speed", Category.MOVEMENT),
    NOFALL("nofall", "NoFall", Category.MOVEMENT),
    TIMER("timer", "Timer", Category.MOVEMENT),
    VEHICLE("vehicle", "Vehicle", Category.MOVEMENT),

    // --- World interaction (Scaffold) -------------------------------------
    SCAFFOLD("scaffold", "Scaffold", Category.WORLD);

    /** Coarse grouping used by the GUI and documentation. */
    public enum Category {
        COMBAT, MOVEMENT, WORLD
    }

    private final String configKey;
    private final String displayName;
    private final Category category;

    CheckType(String configKey, String displayName, Category category) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.category = category;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }
}
