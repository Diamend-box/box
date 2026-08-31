package com.diamend.darksea.world.cultist;

import com.diamend.darksea.item.ItemDisplay;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        String refTool = root.getString("reference-tool", "NETHERITE_PICKAXE");
        int refEff = root.getInt("reference-efficiency", 15);
        double floor = root.getDouble("floor-seconds", 0.4);
        if (MiningSpeed.tierOf(refTool) == null) {
            log.warning("ores.yml: reference-tool '" + refTool
                    + "' is not a pickaxe — falling back to NETHERITE_PICKAXE");
            refTool = "NETHERITE_PICKAXE";
        }

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
            int min = vein.getInt("size.min", 80);
            int max = vein.getInt("size.max", 120);
            if (min < 1 || max < min) {
                log.warning("ores.yml '" + id + "': size " + min + ".." + max
                        + " is not a band — skipped");
                continue;
            }
            long cooldown = vein.getLong("regrow-minutes", 20L);
            if (cooldown < 1) {
                log.warning("ores.yml '" + id + "': regrow-minutes is " + cooldown + " — skipped");
                continue;
            }
            double mineSeconds = vein.getDouble("mine-seconds", 2.0);
            if (mineSeconds <= 0) {
                log.warning("ores.yml '" + id + "': mine-seconds is " + mineSeconds + " — skipped");
                continue;
            }
            types.add(new OreType(id, block,
                    vein.getString("shell", "CALCITE"),
                    vein.getString("bud", "AMETHYST_CLUSTER"),
                    vein.getString("matrix", "BASALT"),
                    Math.max(0, vein.getInt("bud-one-in", 7)),
                    drop,
                    Math.max(1, vein.getInt("drop-amount", 1)),
                    count, min, max, mineSeconds, cooldown));
        }
        if (types.isEmpty()) {
            log.warning("ores.yml defined no usable veins");
        }
        return new OreTables(types, spacing, tries, refTool, refEff, floor,
                loadDisplays(root.getConfigurationSection("items"), log));
    }

    /**
     * The {@code items:} section: what each crystal is called and what it looks
     * like in the hand. Cosmetics only — identity is the PDC tag — so a bad
     * entry here costs a name, never an item, and is dropped field by field
     * rather than whole.
     */
    private static Map<String, ItemDisplay> loadDisplays(ConfigurationSection section, Logger log) {
        if (section == null) {
            return Map.of();
        }
        Map<String, ItemDisplay> displays = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                log.warning("ores.yml items '" + id + "': not an item block — ignored");
                continue;
            }
            String material = entry.getString("material", "");
            if (!material.isBlank() && Material.matchMaterial(material) == null) {
                log.warning("ores.yml items '" + id + "': '" + material
                        + "' is not a material — keeping the shipped one");
                material = "";
            }
            displays.put(id, new ItemDisplay(material, entry.getString("name", ""),
                    entry.getStringList("lore")));
        }
        return displays;
    }
}
