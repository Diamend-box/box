package com.diamend.robobear.mine;

import com.diamend.robobear.util.ToggleSet;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which mines the challenge is allowed to send players to.
 *
 * <p>A server that hands its whole mine list to RoboBear hands over every mine,
 * including the rank-gated ones a new player can't get into. An objective in a
 * mine you can't enter is a dead run, so the pool is filterable — from
 * {@code /rb mines edit}, in game, with no file editing.
 *
 * <p>The storage rules, and why they are exclusions rather than inclusions, are
 * in {@link ToggleSet}. The GUI's "disable all" button is what makes the
 * opposite workflow — turn everything off, switch six back on — a handful of
 * clicks rather than sixty-nine.
 */
public class MineToggles {

    private final ToggleSet toggles;

    public MineToggles(JavaPlugin plugin) {
        this.toggles = new ToggleSet(plugin, "mine-toggles.yml", List.of(
                "Mines the RoboBear challenge will not set objectives in.",
                "Anything not listed here is playable, so a mine added in",
                "MineResetLite later joins the pool automatically.",
                "Edit this in game with /rb mines edit rather than by hand."));
    }

    public boolean isEnabled(String id) {
        return toggles.isEnabled(id);
    }

    public boolean setEnabled(String id, boolean enabled) {
        return toggles.setEnabled(id, enabled);
    }

    public int enableAll() {
        return toggles.enableAll();
    }

    /** Switches off every mine given. @return how many changed */
    public int disableAll(Collection<MineRegion> known) {
        List<String> ids = new ArrayList<>(known.size());
        for (MineRegion region : known) {
            if (region != null) {
                ids.add(region.id());
            }
        }
        return toggles.disableAll(ids);
    }

    public int disabledCount() {
        return toggles.disabledCount();
    }

    public void load() {
        toggles.load();
    }

    public void save() {
        toggles.save();
    }
}
