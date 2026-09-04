package com.diamend.robobear.challenge;

import com.diamend.robobear.util.ToggleSet;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Which workshop upgrades players may buy.
 *
 * <p>Not every server wants every buff. Haste changes how fast a mine empties,
 * Strength changes what a run does to bystanders in a PvP world, and a server
 * that has tuned its own combat will want a say in both. Turning one off here
 * removes it from the workshop rather than nerfing it to nothing.
 *
 * <p>Effects already bought stay applied for the rest of that run — switching an
 * upgrade off stops it being sold, it doesn't reach into runs in progress and
 * strip what players paid Cogs for.
 */
public class UpgradeToggles {

    private final ToggleSet toggles;

    public UpgradeToggles(JavaPlugin plugin) {
        this.toggles = new ToggleSet(plugin, "upgrade-toggles.yml", List.of(
                "Workshop upgrades players cannot buy.",
                "Anything not listed here is on sale, so this file staying",
                "empty means the workshop sells everything.",
                "Edit this in game with /rb upgrades rather than by hand.",
                "Prices live in config.yml under 'upgrades'."));
    }

    public boolean isEnabled(Upgrade upgrade) {
        return upgrade != null && toggles.isEnabled(upgrade.key());
    }

    public boolean setEnabled(Upgrade upgrade, boolean enabled) {
        return upgrade != null && toggles.setEnabled(upgrade.key(), enabled);
    }

    /** The upgrades on sale, in the enum's order so the shop layout is stable. */
    public List<Upgrade> enabled() {
        List<Upgrade> open = new ArrayList<>(Upgrade.values().length);
        for (Upgrade upgrade : Upgrade.values()) {
            if (isEnabled(upgrade)) {
                open.add(upgrade);
            }
        }
        return open;
    }

    public int enabledCount() {
        return enabled().size();
    }

    public int enableAll() {
        return toggles.enableAll();
    }

    public int disableAll() {
        List<String> keys = new ArrayList<>(Upgrade.values().length);
        for (Upgrade upgrade : Upgrade.values()) {
            keys.add(upgrade.key());
        }
        return toggles.disableAll(keys);
    }

    public void load() {
        toggles.load();
    }
}
