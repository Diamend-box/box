package com.diamend.darksea.loot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses loot.yml into per-tier base tables plus optional per-tier vault
 * tables ({@code tiers.<n>.vault}). Malformed entries are logged and
 * skipped; the shipped-config test refuses to let one slip into a release.
 */
public final class LootConfig {

    private LootConfig() {
    }

    public static LootTables load(ConfigurationSection root, Logger log) {
        Map<Integer, LootTable> base = new HashMap<>();
        Map<Integer, LootTable> vault = new HashMap<>();
        ConfigurationSection tiers = root.getConfigurationSection("tiers");
        if (tiers == null) {
            log.warning("loot.yml has no 'tiers' section — chests will not refill");
            return LootTables.empty();
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
            List<LootEntry> entries = parseEntries(sec, tier, "", log);
            if (entries.isEmpty()) {
                log.warning("loot.yml tier " + tier + " has no valid entries");
                continue;
            }
            long cooldown = Math.max(1, sec.getLong("refill-cooldown-minutes", 60));
            base.put(tier, new LootTable(tier,
                    Math.max(1, sec.getInt("rolls", 4)), cooldown, List.copyOf(entries)));

            ConfigurationSection vaultSec = sec.getConfigurationSection("vault");
            if (vaultSec != null) {
                List<LootEntry> vaultEntries = parseEntries(vaultSec, tier, "vault ", log);
                if (vaultEntries.isEmpty()) {
                    log.warning("loot.yml tier " + tier + " vault has no valid entries — vault skipped");
                } else {
                    // The vault shares the tier's cooldown unless it sets its own.
                    vault.put(tier, new LootTable(tier,
                            Math.max(1, vaultSec.getInt("rolls", 6)),
                            Math.max(1, vaultSec.getLong("refill-cooldown-minutes", cooldown)),
                            List.copyOf(vaultEntries)));
                }
            }
        }
        return new LootTables(Map.copyOf(base), Map.copyOf(vault));
    }

    private static List<LootEntry> parseEntries(ConfigurationSection sec, int tier,
                                                String label, Logger log) {
        List<LootEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : sec.getMapList("entries")) {
            try {
                entries.add(LootEntry.parse(map));
            } catch (RuntimeException ex) {
                log.warning("loot.yml tier " + tier + " " + label + ": bad entry " + map
                        + " (" + ex.getMessage() + ")");
            }
        }
        return entries;
    }
}
