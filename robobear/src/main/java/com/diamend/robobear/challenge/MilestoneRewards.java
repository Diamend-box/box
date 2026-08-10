package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * What surviving to a given round pays.
 *
 * <p>Bee Swarm hands out a new tier of Cog Amulet every five rounds; this is the
 * same idea with the contents left to the server. A tier is a plain list of
 * items staff dropped into a menu, so RoboBear never invents a reward currency
 * of its own — it pays in whatever this server already treats as valuable.
 *
 * <p>{@code milestones.yml} is the plugin's file to rewrite. The hand-editable
 * settings live in {@code config.yml}, which this class never touches.
 */
public class MilestoneRewards {

    /** One tier: the round that earns it, and what it pays. */
    public static class Tier {

        private final int round;
        private String name;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();

        public Tier(int round, String name) {
            this.round = round;
            this.name = name;
        }

        public int round() {
            return round;
        }

        public String name() {
            return name == null || name.isBlank() ? "Round " + round : name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ItemStack> items() {
            return items;
        }

        public List<String> commands() {
            return commands;
        }

        public boolean isEmpty() {
            return items.isEmpty() && commands.isEmpty();
        }
    }

    private final RoboBearPlugin plugin;
    private final File file;

    /** Keyed by round, sorted, so "the next one" is a lookup rather than a scan. */
    private final TreeMap<Integer, Tier> tiers = new TreeMap<>();

    public MilestoneRewards(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "milestones.yml");
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public List<Tier> all() {
        return new ArrayList<>(tiers.values());
    }

    public int size() {
        return tiers.size();
    }

    /** The tier earned by finishing exactly this round, or null. */
    public Tier at(int round) {
        return tiers.get(round);
    }

    /** The next tier strictly above a round, or null when there's nothing left. */
    public Tier nextAfter(int round) {
        Map.Entry<Integer, Tier> entry = tiers.higherEntry(round);
        return entry == null ? null : entry.getValue();
    }

    public Tier createOrGet(int round) {
        return tiers.computeIfAbsent(round, key -> new Tier(key, "Round " + key));
    }

    public void remove(int round) {
        tiers.remove(round);
        save();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        tiers.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("milestones");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            int round;
            try {
                round = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("milestones.yml: '" + key + "' isn't a round number; skipped.");
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Tier tier = new Tier(round, section.getString("name", "Round " + round));
            for (Object raw : section.getList("items", List.of())) {
                if (raw instanceof ItemStack item && !item.getType().isAir()) {
                    tier.items().add(item);
                }
            }
            tier.commands().addAll(section.getStringList("commands"));
            tiers.put(round, tier);
        }
        plugin.getLogger().info("Loaded " + tiers.size() + " milestone tier(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Tier tier : tiers.values()) {
            String base = "milestones." + tier.round();
            config.set(base + ".name", tier.name());
            if (!tier.items().isEmpty()) {
                config.set(base + ".items", tier.items());
            }
            if (!tier.commands().isEmpty()) {
                config.set(base + ".commands", tier.commands());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent + " to save milestones.yml.");
                return;
            }
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save milestones.yml", error);
        }
    }

    /**
     * On a fresh install, lays out empty tiers at the configured interval so
     * there is something to click in the editor rather than a blank screen.
     */
    public void installPlaceholdersIfEmpty() {
        if (!tiers.isEmpty()) {
            return;
        }
        int every = Math.max(1, plugin.getConfig().getInt("run.milestone-every", 5));
        String[] names = { "Bronze", "Silver", "Gold", "Diamond" };
        for (int i = 0; i < names.length; i++) {
            int round = every * (i + 1);
            tiers.put(round, new Tier(round, names[i] + " Cog"));
        }
        save();
        plugin.getLogger().info("Created " + names.length + " empty milestone tiers at rounds "
                + every + ", " + (every * 2) + ", " + (every * 3) + " and " + (every * 4)
                + ". Fill them in with /rb milestones — until you do, a run pays nothing.");
    }
}
