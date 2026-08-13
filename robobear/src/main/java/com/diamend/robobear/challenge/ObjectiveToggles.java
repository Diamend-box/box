package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.util.ToggleSet;

import java.util.List;
import java.util.Locale;

/**
 * Which kinds of job the challenge may hand out.
 *
 * <p>Two switches, deliberately. {@code config.yml} is the master — a type
 * turned off there stays off, because that is where a server's fixed decisions
 * belong and where they survive being diffed. This file is the one an admin
 * flips from {@code /rb quests} while watching a run, without touching a config
 * or reloading. A type has to pass both.
 */
public class ObjectiveToggles {

    private final RoboBearPlugin plugin;
    private final ToggleSet toggles;

    public ObjectiveToggles(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.toggles = new ToggleSet(plugin, "objective-toggles.yml", List.of(
                "Objective types switched off in game with /rb quests.",
                "config.yml's objectives.<type>.enabled is the master switch;",
                "a type has to be on in both places to be offered."));
    }

    /** The config key a type's numbers live under, e.g. {@code mine-blocks}. */
    public static String key(ObjectiveType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Whether config.yml permits this type at all. */
    public boolean allowedByConfig(ObjectiveType type) {
        return plugin.getConfig().getBoolean("objectives." + key(type) + ".enabled", true);
    }

    /** Whether an admin has switched it off in game. */
    public boolean allowedInGame(ObjectiveType type) {
        return toggles.isEnabled(key(type));
    }

    /** Whether this type may actually be offered. */
    public boolean isEnabled(ObjectiveType type) {
        return allowedByConfig(type) && allowedInGame(type);
    }

    /**
     * Switches a type on or off in game.
     *
     * @return false when config.yml forbids it, so the caller can say why
     */
    public boolean setEnabled(ObjectiveType type, boolean enabled) {
        if (enabled && !allowedByConfig(type)) {
            return false;
        }
        toggles.setEnabled(key(type), enabled);
        return true;
    }

    public void load() {
        toggles.load();
    }
}
