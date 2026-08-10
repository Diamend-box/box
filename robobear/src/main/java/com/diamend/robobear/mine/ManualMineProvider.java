package com.diamend.robobear.mine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Mines defined in RoboBear's own {@code mines.yml}.
 *
 * <p>The fallback for servers without MineResetLite, and the escape hatch when
 * the reflection integration meets a fork it doesn't understand. Regions are
 * built in game with {@code /rb pos1}, {@code /rb pos2} and
 * {@code /rb mine set <id>} — the plugin never asks anyone to write coordinates
 * into a file by hand.
 */
public class ManualMineProvider implements MineProvider {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, MineRegion> regions = new LinkedHashMap<>();

    public ManualMineProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mines.yml");
        load();
    }

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<MineRegion> mines() {
        return new ArrayList<>(regions.values());
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    public void put(MineRegion region) {
        regions.put(key(region.id()), region);
        save();
    }

    public boolean remove(String id) {
        if (regions.remove(key(id)) == null) {
            return false;
        }
        save();
        return true;
    }

    public MineRegion get(String id) {
        return regions.get(key(id));
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public final void load() {
        regions.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("mines");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String world = section.getString("world");
            if (world == null || world.isBlank()) {
                plugin.getLogger().warning("mines.yml: '" + id + "' has no world; skipped.");
                continue;
            }
            MineRegion region = MineRegion.between(
                    id,
                    section.getString("display", id),
                    world,
                    section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                    section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"));
            regions.put(key(id), region);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (MineRegion region : regions.values()) {
            String base = "mines." + region.id();
            config.set(base + ".display", region.displayName());
            config.set(base + ".world", region.world());
            config.set(base + ".min.x", region.minX());
            config.set(base + ".min.y", region.minY());
            config.set(base + ".min.z", region.minZ());
            config.set(base + ".max.x", region.maxX());
            config.set(base + ".max.y", region.maxY());
            config.set(base + ".max.z", region.maxZ());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent + " to save mines.yml.");
                return;
            }
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mines.yml", error);
        }
    }
}
