package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads, stores and persists all achievement definitions in
 * {@code achievements.yml}. On first run a couple of example achievements are
 * created so the server owner has something to look at.
 */
public class AchievementManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    public AchievementManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
    }

    public void load() {
        achievements.clear();
        if (!file.exists()) {
            seedDefaults();
            save();
            plugin.getLogger().info("Loaded " + achievements.size() + " custom achievement(s) (seeded defaults).");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("achievements");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                try {
                    achievements.put(id.toLowerCase(Locale.ROOT), read(id, section));
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load achievement '" + id + "'", ex);
                }
            }
        }
        plugin.getLogger().info("Loaded " + achievements.size() + " custom achievement(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Achievement achievement : achievements.values()) {
            write(config, achievement);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for achievements.yml");
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save achievements.yml", ex);
        }
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public Collection<Achievement> all() {
        return achievements.values();
    }

    public List<Achievement> asList() {
        return new ArrayList<>(achievements.values());
    }

    public Achievement get(String id) {
        return id == null ? null : achievements.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return id != null && achievements.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public int count() {
        return achievements.size();
    }

    /** Adds or replaces an achievement and persists to disk. */
    public void put(Achievement achievement) {
        achievements.put(achievement.getId().toLowerCase(Locale.ROOT), achievement);
        save();
    }

    /** Removes an achievement by id and persists. Returns true if something was removed. */
    public boolean remove(String id) {
        if (id == null) {
            return false;
        }
        boolean removed = achievements.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Serialization
    // ------------------------------------------------------------------

    private Achievement read(String id, ConfigurationSection section) {
        Achievement achievement = new Achievement(id);
        achievement.setDisplayName(section.getString("display-name", id));

        List<String> description = section.getStringList("description");
        if (description.isEmpty() && section.isString("description")) {
            description = new ArrayList<>(List.of(section.getString("description", "")));
        }
        achievement.setDescription(new ArrayList<>(description));

        Material icon = Material.matchMaterial(section.getString("icon", "NETHER_STAR"));
        achievement.setIcon(icon != null ? icon : Material.NETHER_STAR);

        achievement.setAnnounce(section.getBoolean("announce", true));
        achievement.setRewardXp(section.getInt("reward-xp", 0));
        achievement.setRewardCommands(new ArrayList<>(section.getStringList("reward-commands")));

        // Requirements: prefer the new list form; fall back to the legacy
        // single trigger/target/amount fields for older files.
        achievement.getRequirements().clear();
        List<Map<?, ?>> reqList = section.getMapList("requirements");
        if (!reqList.isEmpty()) {
            for (Map<?, ?> map : reqList) {
                achievement.getRequirements().add(readRequirement(map));
            }
        } else {
            achievement.getRequirements().add(new Requirement(
                    TriggerType.fromString(section.getString("trigger", "MANUAL")),
                    section.getString("target", "ANY"),
                    section.getInt("amount", 1)));
        }
        if (achievement.getRequirements().isEmpty()) {
            achievement.getRequirements().add(new Requirement());
        }
        return achievement;
    }

    private Requirement readRequirement(Map<?, ?> map) {
        TriggerType trigger = TriggerType.fromString(String.valueOf(map.get("trigger")));
        Object rawTarget = map.get("target");
        String target = rawTarget != null ? String.valueOf(rawTarget) : "ANY";
        int amount = 1;
        Object rawAmount = map.get("amount");
        if (rawAmount instanceof Number number) {
            amount = number.intValue();
        }
        return new Requirement(trigger, target, amount);
    }

    private void write(YamlConfiguration config, Achievement achievement) {
        String base = "achievements." + achievement.getId();
        config.set(base + ".display-name", achievement.getDisplayName());
        config.set(base + ".description", achievement.getDescription());
        config.set(base + ".icon", achievement.getIcon().name());
        config.set(base + ".announce", achievement.isAnnounce());
        config.set(base + ".reward-xp", achievement.getRewardXp());
        config.set(base + ".reward-commands", achievement.getRewardCommands());

        List<Map<String, Object>> reqList = new ArrayList<>();
        for (Requirement requirement : achievement.getRequirements()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("trigger", requirement.getTrigger().name());
            map.put("target", requirement.getTarget());
            map.put("amount", requirement.getAmount());
            reqList.add(map);
        }
        config.set(base + ".requirements", reqList);
    }

    // ------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------

    private void seedDefaults() {
        Achievement wood = new Achievement("getting_wood");
        wood.setDisplayName("<gold>Getting Wood");
        wood.setDescription(new ArrayList<>(List.of("<gray>Punch a tree and", "<gray>collect some logs.")));
        wood.setIcon(Material.OAK_LOG);
        wood.setTrigger(TriggerType.BLOCK_BREAK);
        wood.setTarget("OAK_LOG");
        wood.setAmount(10);
        wood.setAnnounce(true);
        wood.setRewardXp(20);
        achievements.put(wood.getId(), wood);

        Achievement miner = new Achievement("diamonds_forever");
        miner.setDisplayName("<aqua>Diamonds Forever");
        miner.setDescription(new ArrayList<>(List.of("<gray>Mine your very first", "<gray>diamond ore.")));
        miner.setIcon(Material.DIAMOND);
        miner.setTrigger(TriggerType.BLOCK_BREAK);
        miner.setTarget("DIAMOND_ORE");
        miner.setAmount(1);
        miner.setAnnounce(true);
        miner.setRewardXp(100);
        achievements.put(miner.getId(), miner);

        Achievement slayer = new Achievement("monster_hunter");
        slayer.setDisplayName("<red>Monster Hunter");
        slayer.setDescription(new ArrayList<>(List.of("<gray>Defeat 25 zombies.")));
        slayer.setIcon(Material.DIAMOND_SWORD);
        slayer.setTrigger(TriggerType.ENTITY_KILL);
        slayer.setTarget("ZOMBIE");
        slayer.setAmount(25);
        slayer.setAnnounce(true);
        slayer.getRewardCommands().add("give %player% golden_apple 1");
        achievements.put(slayer.getId(), slayer);

        Achievement tourist = new Achievement("hot_tourist");
        tourist.setDisplayName("<red>Hot Tourist");
        tourist.setDescription(new ArrayList<>(List.of("<gray>Set foot in the Nether.")));
        tourist.setIcon(Material.NETHERRACK);
        tourist.setTrigger(TriggerType.REACH_DIMENSION);
        tourist.setTarget("NETHER");
        tourist.setAmount(1);
        tourist.setAnnounce(true);
        tourist.setRewardXp(50);
        achievements.put(tourist.getId(), tourist);

        Achievement veteran = new Achievement("veteran");
        veteran.setDisplayName("<light_purple>Veteran");
        veteran.setDescription(new ArrayList<>(List.of("<gray>Play for 60 minutes.")));
        veteran.setIcon(Material.CLOCK);
        veteran.setTrigger(TriggerType.PLAYTIME_MINUTES);
        veteran.setTarget("ANY");
        veteran.setAmount(60);
        veteran.setAnnounce(false);
        achievements.put(veteran.getId(), veteran);

        // Multi-objective example: must satisfy BOTH requirements.
        Achievement prepared = new Achievement("well_prepared");
        prepared.setDisplayName("<yellow>Well Prepared");
        prepared.setDescription(new ArrayList<>(List.of("<gray>Craft a shield and", "<gray>cook some food.")));
        prepared.setIcon(Material.SHIELD);
        prepared.setAnnounce(true);
        prepared.setRewardXp(30);
        prepared.getRequirements().clear();
        prepared.getRequirements().add(new Requirement(TriggerType.ITEM_CRAFT, "SHIELD", 1));
        prepared.getRequirements().add(new Requirement(TriggerType.ITEM_CRAFT, "COOKED_BEEF", 8));
        achievements.put(prepared.getId(), prepared);
    }
}
