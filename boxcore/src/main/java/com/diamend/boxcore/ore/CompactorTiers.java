package com.diamend.boxcore.ore;

import com.diamend.boxcore.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The grades of personal compactor this server hands out, read from
 * {@code compressor.tiers} in config.yml.
 *
 * <p>Kept in config rather than in the GUI-owned recipe file because tiers are
 * a server-balance question, not a content one — and because they are the seam
 * a progression system plugs into later, which wants to be somewhere a server
 * owner reads rather than somewhere a menu rewrites.
 */
public class CompactorTiers {

    private final Plugin plugin;
    private final Map<Integer, CompactorTier> tiers = new TreeMap<>();

    public CompactorTiers(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tiers.clear();
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("compressor.tiers");
        if (section == null) {
            defaults();
            return;
        }
        for (String key : section.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key.trim());
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("compressor.tiers: '" + key
                        + "' is not a tier number, skipping.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            tiers.put(level, new CompactorTier(level,
                    entry.getString("name", "<aqua>Personal Compactor " + level),
                    entry.getInt("slots", 1),
                    Items.material(entry.getString("material"), Material.HOPPER),
                    entry.getBoolean("glow", false)));
        }
        if (tiers.isEmpty()) {
            defaults();
        }
    }

    /** Four grades at 1/3/6/12 slots, used when nothing is configured. */
    private void defaults() {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        slots.put(1, 1);
        slots.put(2, 3);
        slots.put(3, 6);
        slots.put(4, 12);
        for (Map.Entry<Integer, Integer> entry : slots.entrySet()) {
            int level = entry.getKey();
            tiers.put(level, new CompactorTier(level,
                    "<aqua>Personal Compactor " + (3 + level) + "000",
                    entry.getValue(), Material.HOPPER, level >= 3));
        }
    }

    public CompactorTier get(int level) {
        return tiers.get(level);
    }

    public Collection<CompactorTier> all() {
        return tiers.values();
    }

    public boolean isEmpty() {
        return tiers.isEmpty();
    }
}
