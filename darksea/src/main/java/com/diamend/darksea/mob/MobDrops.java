package com.diamend.darksea.mob;

import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.armor.VironicArmor;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Plugin-side mob drops (the {@code drops:} section of mobs.yml) — the
 * signature gear the Mythic pack can't mint: PDC-tagged weapons, Vironic
 * set pieces, Chronons, relics. The spawner stamps every mob it spawns
 * (Mythic or vanilla fallback alike) with {@link #MOB_ID_KEY}, the death
 * listener reads the stamp and rolls these lines — so drops work
 * identically with and without MythicMobs installed.
 */
public final class MobDrops {

    /** String PDC tag on spawned entities: the mobs.yml type that spawned them. */
    public static final NamespacedKey MOB_ID_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:mob_id"));

    /**
     * One drop line: either a registry item ({@code itemId}) or one random
     * piece of a Vironic set ({@code setTier} > 0). {@code chance} in (0,1].
     */
    public record Line(String itemId, int setTier, int min, int max, double chance) {

        public boolean isSet() {
            return setTier > 0;
        }
    }

    private MobDrops() {
    }

    /**
     * Parses mobs.yml's {@code drops:} section into per-mob-type lines.
     * Unknown item ids, bad set tiers and out-of-range chances are logged
     * and skipped — a typo must never turn into a silent no-drop on the
     * server, so the shipped file is CI-locked against this parser.
     */
    public static Map<String, List<Line>> parse(ConfigurationSection root, Logger log) {
        Map<String, List<Line>> result = new HashMap<>();
        ConfigurationSection drops = root.getConfigurationSection("drops");
        if (drops == null) {
            return Map.of();
        }
        Set<String> validIds = DarkSeaItems.allIds();
        for (String mobType : drops.getKeys(false)) {
            List<Line> lines = new ArrayList<>();
            for (Map<?, ?> map : drops.getMapList(mobType)) {
                Line line = parseLine(map, validIds, mobType, log);
                if (line != null) {
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                result.put(mobType, List.copyOf(lines));
            }
        }
        return Map.copyOf(result);
    }

    private static Line parseLine(Map<?, ?> map, Set<String> validIds, String mobType, Logger log) {
        double chance = toDouble(map.get("chance"), 1.0);
        if (chance <= 0 || chance > 1) {
            log.warning("mobs.yml drops." + mobType + ": chance " + chance + " not in (0,1] — skipped");
            return null;
        }
        int min = Math.max(1, toInt(map.get("min"), 1));
        int max = Math.max(min, toInt(map.get("max"), min));

        Object set = map.get("set");
        if (set != null) {
            if (!VironicArmor.SET_ID.equals(String.valueOf(set))) {
                log.warning("mobs.yml drops." + mobType + ": unknown set '" + set + "' — skipped");
                return null;
            }
            int tier = toInt(map.get("tier"), 0);
            if (VironicArmor.rankName(tier) == null) {
                log.warning("mobs.yml drops." + mobType + ": set tier " + tier + " has no rank — skipped");
                return null;
            }
            return new Line(null, tier, 1, 1, chance);
        }

        String id = map.get("id") != null ? String.valueOf(map.get("id")) : null;
        if (id == null || !validIds.contains(id)) {
            log.warning("mobs.yml drops." + mobType + ": unknown item id '" + id + "' — skipped");
            return null;
        }
        return new Line(id, 0, min, max, chance);
    }

    /** Rolls a mob's lines into concrete drops. */
    public static List<ItemStack> roll(List<Line> lines, Random rng) {
        List<ItemStack> drops = new ArrayList<>();
        for (Line line : lines) {
            if (rng.nextDouble() >= line.chance()) {
                continue;
            }
            ItemStack item;
            if (line.isSet()) {
                SeaArmor.Piece[] pieces = SeaArmor.Piece.values();
                item = VironicArmor.createPiece(line.setTier(), pieces[rng.nextInt(pieces.length)]);
            } else {
                int amount = line.min() + rng.nextInt(line.max() - line.min() + 1);
                item = DarkSeaItems.create(line.itemId(), amount);
            }
            if (item != null) {
                drops.add(item);
            }
        }
        return drops;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
