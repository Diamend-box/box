package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Reads what a player has <em>already</em> done from Minecraft's own lifetime
 * statistics, so an achievement created today can credit work done before it
 * existed — 150 player kills already on the board count toward a "kill 200
 * players" objective, leaving 50 to go rather than 200.
 *
 * <p>Only objectives the vanilla statistics can actually answer are covered;
 * everything else reports {@code -1} and simply starts from zero. Notably a
 * custom item name has no statistic behind it (Minecraft counts materials, not
 * names), and neither does "died to lava specifically" — the server only tracks
 * a total death count.
 */
public final class StatisticBackfill {

    private StatisticBackfill() {
    }

    /**
     * The player's lifetime total for whatever this requirement asks for, or
     * {@code -1} when Minecraft doesn't track it.
     */
    public static int total(Player player, Requirement requirement) {
        if (requirement.isMatchByName()) {
            return -1; // statistics count materials, not custom item names
        }
        String target = requirement.getTarget();
        boolean wildcard = target == null || target.isBlank() || target.equalsIgnoreCase("ANY");
        TargetGroup group = requirement.getGroup();
        return switch (requirement.getTrigger()) {
            case ENTITY_KILL -> {
                if (wildcard) {
                    yield untyped(player, Statistic.MOB_KILLS);
                }
                if (group instanceof EntityGroup entities) {
                    yield sumEntities(player, entities);
                }
                // Players killed are counted separately from mobs.
                yield target.equalsIgnoreCase("PLAYER")
                        ? untyped(player, Statistic.PLAYER_KILLS)
                        : entityStat(player, Statistic.KILL_ENTITY, target);
            }
            case BLOCK_BREAK -> materialTotal(player, Statistic.MINE_BLOCK, target, wildcard, group);
            // Vanilla has no "blocks placed"; placing a block is a USE_ITEM.
            case BLOCK_PLACE -> materialTotal(player, Statistic.USE_ITEM, target, wildcard, group);
            case ITEM_CRAFT -> materialTotal(player, Statistic.CRAFT_ITEM, target, wildcard, group);
            case ITEM_OBTAIN -> materialTotal(player, Statistic.PICKUP, target, wildcard, group);
            case ITEM_CONSUME -> materialTotal(player, Statistic.USE_ITEM, target, wildcard, group);
            case FISH_CAUGHT -> untyped(player, Statistic.FISH_CAUGHT);
            // Only the overall death count is tracked, so a cause can't be backfilled.
            case PLAYER_DEATH -> wildcard ? untyped(player, Statistic.DEATHS) : -1;
            default -> -1;
        };
    }

    private static int materialTotal(Player player, Statistic statistic, String target,
                                     boolean wildcard, TargetGroup group) {
        if (wildcard) {
            // "any block" would mean summing every material; not worth it.
            return -1;
        }
        if (group instanceof MaterialGroup materials) {
            return sumMaterials(player, statistic, materials);
        }
        return materialStat(player, statistic, target);
    }

    private static int untyped(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (RuntimeException ex) {
            return -1; // not tracked on this server implementation
        }
    }

    private static int materialStat(Player player, Statistic statistic, String name) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            return -1;
        }
        try {
            return player.getStatistic(statistic, material);
        } catch (RuntimeException ex) {
            // Statistics reject materials they don't apply to (e.g. mining a
            // non-block), and some builds don't implement them at all.
            return -1;
        }
    }

    private static int entityStat(Player player, Statistic statistic, String name) {
        EntityType type;
        try {
            type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return -1;
        }
        try {
            return player.getStatistic(statistic, type);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static int sumMaterials(Player player, Statistic statistic, MaterialGroup group) {
        int total = 0;
        boolean any = false;
        for (Material material : group.members()) {
            try {
                total += player.getStatistic(statistic, material);
                any = true;
            } catch (RuntimeException ignored) {
                // This material has no such statistic; the rest of the family still counts.
            }
        }
        return any ? total : -1;
    }

    private static int sumEntities(Player player, EntityGroup group) {
        int total = 0;
        boolean any = false;
        for (EntityType type : group.members()) {
            try {
                total += player.getStatistic(Statistic.KILL_ENTITY, type);
                any = true;
            } catch (RuntimeException ignored) {
                // Not every entity type is killable/tracked.
            }
        }
        return any ? total : -1;
    }
}
