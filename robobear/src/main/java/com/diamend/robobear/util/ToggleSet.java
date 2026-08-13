package com.diamend.robobear.util;

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
 * A named set of things an admin has switched off, persisted to its own file.
 *
 * <p><b>Exclusions, not inclusions.</b> Anything nobody has an opinion about is
 * on. That is the only default that upgrades safely: a server installing a new
 * version keeps the behaviour it had, and something added later — a mine, an
 * upgrade — joins in rather than silently sitting out because it wasn't in a
 * list written before it existed.
 *
 * <p>Its own file rather than config.yml on purpose. Writing config.yml from a
 * GUI means rewriting a file the admin hand-edits and comments, on every click.
 */
public class ToggleSet {

    private final JavaPlugin plugin;
    private final File file;
    private final List<String> comments;

    /** Lower-cased ids that are switched off. */
    private final Set<String> disabled = new LinkedHashSet<>();

    public ToggleSet(JavaPlugin plugin, String fileName, List<String> comments) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.comments = List.copyOf(comments);
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

    /**
     * Switches everything back on, including ids that are no longer known.
     *
     * @return how many were switched back on
     */
    public int enableAll() {
        int count = disabled.size();
        if (count > 0) {
            disabled.clear();
            save();
        }
        return count;
    }

    /**
     * Switches off every id given. They have to be given because the state is
     * stored as exclusions — there is no "everything" to write without them.
     *
     * @return how many this switched off that weren't already
     */
    public int disableAll(Collection<String> ids) {
        int before = disabled.size();
        for (String id : ids) {
            if (id != null) {
                disabled.add(key(id));
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
     * Reads the exclusions back. Ids for things that no longer exist are kept
     * rather than pruned: something that comes back — a mine restored from a
     * backup, an upgrade re-added — should come back the way it was left.
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
        config.setComments("disabled", comments);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent
                        + " to save " + file.getName() + ".");
                return;
            }
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + file.getName(), error);
        }
    }
}
