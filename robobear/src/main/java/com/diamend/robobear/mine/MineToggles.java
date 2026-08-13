package com.diamend.robobear.mine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Which mines the challenge is allowed to send players to.
 *
 * <p>A server that hands its whole mine list to RoboBear hands over every mine,
 * including the rank-gated ones a new player can't get into. An objective in a
 * mine you can't enter is a dead run, so the pool is filterable — from
 * {@code /rb mines edit}, in game, with no file editing.
 *
 * <p><b>Stored as the exclusions, not the inclusions.</b> A mine nobody has an
 * opinion about is playable, so a server upgrading into this feature keeps the
 * behaviour it already had, and a mine added in MineResetLite later joins the
 * pool rather than silently sitting out. The GUI's "disable all" button is what
 * makes the opposite workflow — turn everything off, switch six back on — a
 * handful of clicks rather than sixty-nine.
 */
public class MineToggles {

    private final JavaPlugin plugin;
    private final File file;

    /** Lower-cased ids of mines excluded from objective rolls. */
    private final Set<String> disabled = new LinkedHashSet<>();

    public MineToggles(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mine-toggles.yml");
        load();
    }

    public boolean isEnabled(String id) {
        return id == null || !disabled.contains(key(id));
    }

    /** @return true when this actually changed something */
    public boolean setEnabled(String id, boolean enabled) {
        if (id == null) {
            return false;
        }
        boolean changed = enabled ? disabled.remove(key(id)) : disabled.add(key(id));
        if (changed) {
            save();
        }
        return changed;
    }

    /** Turns everything back on, including ids no longer in the mine list. */
    public int enableAll() {
        int count = disabled.size();
        if (count > 0) {
            disabled.clear();
            save();
        }
        return count;
    }

    /**
     * Turns off every mine currently known. Ids are needed because the state is
     * stored as exclusions — there is no "all" to write without them.
     *
     * @return how many were switched off by this
     */
    public int disableAll(Collection<MineRegion> known) {
        int before = disabled.size();
        for (MineRegion region : known) {
            if (region != null) {
                disabled.add(key(region.id()));
            }
        }
        if (disabled.size() != before) {
            save();
        }
        return disabled.size() - before;
    }

    public int disabledCount() {
        return disabled.size();
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Reads the exclusions back. Ids for mines that no longer exist are kept
     * rather than pruned: a mine that comes back — renamed round, reset, or
     * restored from a backup — should come back switched off the way it was.
     */
    public final void load() {
        disabled.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getStringList("disabled")) {
            if (id != null && !id.isBlank()) {
                disabled.add(key(id));
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("disabled", List.copyOf(disabled));
        config.setComments("disabled", List.of(
                "Mines the RoboBear challenge will not set objectives in.",
                "Anything not listed here is playable, so a mine added in",
                "MineResetLite later joins the pool automatically.",
                "Edit this in game with /rb mines edit rather than by hand."));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent
                        + " to save mine-toggles.yml.");
                return;
            }
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mine-toggles.yml", error);
        }
    }
}
