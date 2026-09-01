package com.diamend.boxcore.travel;

import com.diamend.boxcore.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * The warp list, stored in its own {@code warps.yml}.
 *
 * <p>Warps are set by standing somewhere and running a command, so this file is
 * the plugin's to write. Keeping it apart from {@code config.yml} means saving
 * a warp never rewrites the hand-commented config.
 *
 * <p>A warp's world is stored by name and resolved on load. A warp in a world
 * that isn't loaded is kept in the file but left out of the live list — deleting
 * someone's spawn point because a world failed to mount would be a poor trade.
 */
public class WarpManager {

    private final Plugin plugin;
    private final Map<String, Warp> warps = new LinkedHashMap<>();
    /**
     * Ids in the file whose world wasn't loaded when we read it.
     *
     * <p>Tracked by name rather than inferred from "not in the live list",
     * because a deleted warp is also not in the live list — and copying those
     * forward on save would resurrect every warp anyone ever removed.
     */
    private final Set<String> unresolved = new LinkedHashSet<>();
    private final List<String> warnings = new ArrayList<>();

    public WarpManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "warps.yml");
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public void load() {
        warps.clear();
        unresolved.clear();
        warnings.clear();
        File file = file();
        if (!file.exists()) {
            // Write the empty book straight away. A staff member looking for
            // warps.yml should find it and see the shape they are editing,
            // rather than a missing file they have to guess the name of.
            save();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read warps.yml", ex);
            return;
        }
        ConfigurationSection section = config.getConfigurationSection("warps");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Warp warp = read(id.toLowerCase(Locale.ROOT), entry);
            if (warp != null) {
                warps.put(warp.id(), warp);
            }
        }
    }

    private Warp read(String id, ConfigurationSection entry) {
        String worldName = entry.getString("world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            unresolved.add(id);
            warn("warp '" + id + "' is in world '" + worldName
                    + "', which isn't loaded; leaving it out for now.");
            return null;
        }
        Location location = new Location(world,
                entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                (float) entry.getDouble("yaw"), (float) entry.getDouble("pitch"));
        return new Warp(id,
                entry.getString("display", id),
                Items.material(entry.getString("icon"), Material.ENDER_PEARL),
                entry.getStringList("description"),
                location,
                entry.getString("permission", ""),
                entry.getDouble("radius", 8.0));
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    /**
     * Writes the list out.
     *
     * <p>Warps whose world failed to load were never read in, so a plain rewrite
     * would delete them. Anything already in the file that this run couldn't
     * resolve is copied across untouched.
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(List.of(
                "BoxCore — fast travel destinations.",
                "",
                "Written by /box warp set and /box warp delete. Hand edits work,",
                "but those commands rewrite this file."));

        copyUnresolved(config);
        for (Warp warp : warps.values()) {
            String base = "warps." + warp.id() + ".";
            Location at = warp.location();
            config.set(base + "display", warp.display());
            config.set(base + "icon", warp.icon().name());
            config.set(base + "world", at.getWorld() == null ? "" : at.getWorld().getName());
            config.set(base + "x", at.getX());
            config.set(base + "y", at.getY());
            config.set(base + "z", at.getZ());
            config.set(base + "yaw", at.getYaw());
            config.set(base + "pitch", at.getPitch());
            config.set(base + "radius", warp.radius());
            if (!warp.permission().isEmpty()) {
                config.set(base + "permission", warp.permission());
            }
            if (!warp.description().isEmpty()) {
                config.set(base + "description", warp.description());
            }
        }
        try {
            File folder = plugin.getDataFolder();
            if (folder.exists() || folder.mkdirs()) {
                config.save(file());
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write warps.yml", ex);
        }
    }

    /** Carries over entries whose world wasn't loaded when we read the file. */
    private void copyUnresolved(YamlConfiguration target) {
        if (unresolved.isEmpty()) {
            return;
        }
        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration existing = new YamlConfiguration();
        try {
            existing.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            return; // unreadable; a fresh file is the best we can do
        }
        ConfigurationSection section = existing.getConfigurationSection("warps");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            if (!unresolved.contains(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry != null) {
                target.createSection("warps." + id, entry.getValues(true));
            }
        }
    }

    // ------------------------------------------------------------------
    // Reading and editing
    // ------------------------------------------------------------------

    public Collection<Warp> all() {
        return warps.values();
    }

    public Warp get(String id) {
        return id == null ? null : warps.get(id.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return warps.size();
    }

    public void put(Warp warp) {
        if (warp != null) {
            warps.put(warp.id(), warp);
            save();
        }
    }

    public boolean remove(String id) {
        if (id == null) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        boolean removed = warps.remove(key) != null;
        // Also drop it from the carry-forward set, or deleting a warp whose
        // world happened to be down would put it straight back on save.
        removed |= unresolved.remove(key);
        if (removed) {
            save();
        }
        return removed;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void warn(String message) {
        warnings.add(message);
        plugin.getLogger().warning("warps.yml: " + message);
    }
}
