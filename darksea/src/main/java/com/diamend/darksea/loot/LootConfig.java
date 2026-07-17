package com.diamend.darksea.loot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Parses loot.yml into per-tier tables. Malformed entries are logged and skipped. */
public final class LootConfig {

    private LootConfig() {
    }

    public static Map<Integer, LootTable> load(ConfigurationSection root, Logger log) {
        Map<Integer, LootTable> tables = new HashMap<>();
        ConfigurationSection tiers = root.getConfigurationSection("tiers");
        if (tiers == null) {
            log.warning("loot.yml has no 'tiers' section — chests will not refill");
            return tables;
        }
        for (String key : tiers.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                log.warning("loot.yml tier '" + key + "' is not a number — skipped");
                continue;
            }
            ConfigurationSection sec = tiers.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            List<LootEntry> entries = new ArrayList<>();
            for (Map<?, ?> map : sec.getMapList("entries")) {
                try {
                    entries.add(LootEntry.parse(map));
                } catch (RuntimeException ex) {
                    log.warning("loot.yml tier " + tier + ": bad entry " + map + " (" + ex.getMessage() + ")");
                }
            }
            if (entries.isEmpty()) {
                log.warning("loot.yml tier " + tier + " has no valid entries");
                continue;
            }
            tables.put(tier, new LootTable(tier,
                    Math.max(1, sec.getInt("rolls", 4)),
                    Math.max(1, sec.getLong("refill-cooldown-minutes", 60)),
                    List.copyOf(entries)));
        }
        return tables;
    }
}
