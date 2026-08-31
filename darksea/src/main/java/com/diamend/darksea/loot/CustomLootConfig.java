package com.diamend.darksea.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads and writes {@code loot-custom.yml}, the file {@code /ds loot} owns.
 *
 * <p>Shaped to mirror loot.yml — {@code tiers.<n>.entries} and
 * {@code tiers.<n>.vault.entries} — so an entry can be moved between the two
 * files by hand, and so anyone who has read one file can read the other. What
 * it deliberately does <em>not</em> carry is {@code rolls} or
 * {@code refill-cooldown-minutes}: those belong to the shipped table, and an
 * overlay that could override them would be able to break a tier's pacing from
 * a GUI with no way to see what it had changed.
 */
public final class CustomLootConfig {

    private static final List<String> HEADER = List.of(
            "DarkSea custom loot.",
            "",
            "Written by /ds loot. Everything here is ADDED to the matching table in",
            "loot.yml — the shipped entries are never touched, so an addition dilutes",
            "the existing weights rather than replacing them.",
            "",
            "Weights are relative within the merged table. To judge one, compare it",
            "against the shipped weights for the same tier in loot.yml; the editor",
            "shows the resulting percentage per entry, which is the number that",
            "actually matters.",
            "",
            "Entry types are loot.yml's: ITEM (a material), CUSTOM (a DarkSea item id),",
            "ARMOR, and SNAPSHOT — a base64 image of a whole item stack, written",
            "when you add something the plugin has no id for. Editing a SNAPSHOT's data",
            "by hand is not useful; delete it in game and re-add the item instead.",
            "",
            "Hand edits survive /ds reload. They do NOT survive the next edit made in",
            "game, which rewrites this file from the loaded state.");

    private CustomLootConfig() {
    }

    /** Loads the overlay, or an empty one if the file is absent or unreadable. */
    public static CustomLoot load(File file, Logger log) {
        if (!file.isFile()) {
            return CustomLoot.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tiers = yaml.getConfigurationSection("tiers");
        if (tiers == null) {
            return CustomLoot.empty();
        }
        Map<CustomLoot.Key, List<LootEntry>> added = new LinkedHashMap<>();
        for (String key : tiers.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                log.warning("loot-custom.yml tier '" + key + "' is not a number — skipped");
                continue;
            }
            ConfigurationSection sec = tiers.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            put(added, new CustomLoot.Key(tier, false), read(sec, tier, "", log));
            ConfigurationSection vault = sec.getConfigurationSection("vault");
            if (vault != null) {
                put(added, new CustomLoot.Key(tier, true), read(vault, tier, "vault ", log));
            }
        }
        return new CustomLoot(Map.copyOf(added));
    }

    private static void put(Map<CustomLoot.Key, List<LootEntry>> into,
                            CustomLoot.Key key, List<LootEntry> entries) {
        if (!entries.isEmpty()) {
            into.put(key, List.copyOf(entries));
        }
    }

    private static List<LootEntry> read(ConfigurationSection sec, int tier,
                                        String label, Logger log) {
        List<LootEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : sec.getMapList("entries")) {
            try {
                entries.add(LootEntry.parse(map));
            } catch (RuntimeException ex) {
                log.warning("loot-custom.yml tier " + tier + " " + label + ": bad entry "
                        + map + " (" + ex.getMessage() + ")");
            }
        }
        return entries;
    }

    /** Writes the overlay out whole. Called after every edit, so nothing is lost to a crash. */
    public static void save(CustomLoot loot, File file, Logger log) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<CustomLoot.Key, List<LootEntry>> line : loot.added().entrySet()) {
            CustomLoot.Key key = line.getKey();
            String path = "tiers." + key.tier() + (key.vault() ? ".vault" : "") + ".entries";
            List<Map<String, Object>> maps = new ArrayList<>(line.getValue().size());
            for (LootEntry entry : line.getValue()) {
                maps.add(entry.toMap());
            }
            yaml.set(path, maps);
        }
        yaml.options().setHeader(HEADER);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }
}
