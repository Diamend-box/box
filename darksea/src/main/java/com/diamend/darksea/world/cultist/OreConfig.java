package com.diamend.darksea.world.cultist;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parses {@code ores.yml} into {@link OreTables}. Same posture as
 * {@link com.diamend.darksea.npc.ShopConfig}: a malformed entry is named in
 * the log and dropped rather than taking the whole file down, and the
 * shipped-config test refuses to let a dropped entry reach a release.
 */
public final class OreConfig {

    private OreConfig() {
    }

    public static OreTables load(ConfigurationSection root, Logger log) {
        if (root == null) {
            log.severe("ores.yml is empty — the caves will have no veins at all");
            return OreTables.empty();
        }
        int spacing = Math.max(1, root.getInt("min-spacing", 48));
        int tries = Math.max(1, root.getInt("placement-tries", 4000));

        ConfigurationSection section = root.getConfigurationSection("veins");
        if (section == null) {
            log.severe("ores.yml has no 'veins' section — the caves will have no veins at all");
            return OreTables.empty();
        }

        List<OreType> types = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection vein = section.getConfigurationSection(id);
            if (vein == null) {
                log.warning("ores.yml: '" + id + "' is not a vein definition — skipped");
                continue;
            }
            if (!seen.add(id)) {
                log.warning("ores.yml: '" + id + "' is defined twice — later one skipped");
                continue;
            }
            String block = vein.getString("block", "");
            String drop = vein.getString("drop", "");
            if (block.isBlank() || drop.isBlank()) {
                log.warning("ores.yml '" + id + "': needs both a block and a drop — skipped");
                continue;
            }
            int count = vein.getInt("count", 0);
            if (count <= 0) {
                log.warning("ores.yml '" + id + "': count is " + count + " — skipped");
                continue;
            }
            int min = vein.getInt("size.min", 20);
            int max = vein.getInt("size.max", 40);
            if (min < 1 || max < min) {
                log.warning("ores.yml '" + id + "': size " + min + ".." + max
                        + " is not a band — skipped");
                continue;
            }
            long cooldown = vein.getLong("regrow-minutes", 30L);
            if (cooldown < 1) {
                log.warning("ores.yml '" + id + "': regrow-minutes is " + cooldown + " — skipped");
                continue;
            }
            types.add(new OreType(id, block, drop,
                    Math.max(1, vein.getInt("drop-amount", 1)),
                    count, min, max, cooldown));
        }
        if (types.isEmpty()) {
            log.warning("ores.yml defined no usable veins");
        }
        return new OreTables(types, spacing, tries);
    }
}
