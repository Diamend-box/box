package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

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
 *
 * <p>An "any" target is answered by adding the per-type rows together, because
 * that is how the server stores them: there is no "blocks mined" counter, only
 * one counter per block.
 *
 * <p>Statistics are kept on disk per player rather than in memory, so these read
 * an {@link OfflinePlayer} and work whether or not they're logged in.
 */
public final class StatisticBackfill {

    /**
     * Bumped whenever this class learns to answer an objective it previously
     * couldn't. It rides along in the per-player "already seeded" marker, so
     * gaining an answer re-examines every objective exactly once on the next
     * join.
     *
     * <p>Without it an upgrade is invisible to players who already joined: an
     * objective is marked seeded even when the read came back empty (otherwise
     * every join re-examines every unfinished objective forever), so the very
     * players an improvement is for are the ones locked out of it, with no way
     * back short of an admin running {@code /ca backfill <player> redo}. Since
     * seeding only ever raises progress, re-examining costs nothing.
     *
     * <p>2: "any block" objectives, which before had no total to read.
     */
    public static final int SCHEMA = 2;

    /**
     * Minecraft keeps no single "blocks mined" total — it counts one row per
     * block, so the only way to answer "has this player broken 10,000 blocks"
     * is to add every row together. Collected once, since the material list
     * can't change while the server is running.
     */
    private static final List<Material> BLOCKS = materials(Material::isBlock);

    /** The same, for the statistics counted per item rather than per block. */
    private static final List<Material> ITEMS = materials(Material::isItem);

    private StatisticBackfill() {
    }

    private static List<Material> materials(Predicate<Material> keep) {
        List<Material> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && keep.test(material)) {
                out.add(material);
            }
        }
        return List.copyOf(out);
    }

    /**
     * The player's lifetime total for whatever this requirement asks for, or
     * {@code -1} when Minecraft doesn't track it.
     */
    public static int total(OfflinePlayer player, Requirement requirement) {
        if (requirement.isMatchByName()) {
            return -1; // statistics count materials, not custom item names
        }
        String target = requirement.getTarget();
        boolean wildcard = target == null || target.isBlank() || target.equalsIgnoreCase("ANY");
        TargetGroup group = requirement.getGroup();
        return switch (requirement.getTrigger()) {
            case ENTITY_KILL -> {
                if (wildcard) {
                    // MOB_KILLS excludes players, but the live listener counts a
                    // killed player like any other entity, so both are needed.
                    yield sum(untyped(player, Statistic.MOB_KILLS),
                            untyped(player, Statistic.PLAYER_KILLS));
                }
                if (group instanceof EntityGroup entities) {
                    yield sumEntities(player, entities);
                }
                // Players killed are counted separately from mobs: KILL_ENTITY
                // has no PLAYER sub-statistic and throws if asked for one.
                yield normalize(target).equals("PLAYER")
                        ? untyped(player, Statistic.PLAYER_KILLS)
                        : entityStat(player, Statistic.KILL_ENTITY, target);
            }
            case BLOCK_BREAK -> materialTotal(player, Statistic.MINE_BLOCK, target, wildcard, group, BLOCKS);
            // Vanilla has no "blocks placed"; placing a block is a USE_ITEM.
            case BLOCK_PLACE -> materialTotal(player, Statistic.USE_ITEM, target, wildcard, group, BLOCKS);
            case ITEM_CRAFT -> materialTotal(player, Statistic.CRAFT_ITEM, target, wildcard, group, ITEMS);
            case ITEM_OBTAIN -> materialTotal(player, Statistic.PICKUP, target, wildcard, group, ITEMS);
            // USE_ITEM counts every use of an item — blocks placed, tools swung —
            // so adding it up across everything would answer a different question
            // than "how much has this player eaten or drunk". Left unseeded rather
            // than seeded with a number that isn't the one the objective asks for.
            case ITEM_CONSUME -> materialTotal(player, Statistic.USE_ITEM, target, wildcard, group, null);
            case FISH_CAUGHT -> untyped(player, Statistic.FISH_CAUGHT);
            // Only the overall death count is tracked, so a cause can't be backfilled.
            case PLAYER_DEATH -> wildcard ? untyped(player, Statistic.DEATHS) : -1;
            default -> -1;
        };
    }

    /**
     * @param whenAny materials to add up for an "any" target, or null when the
     *                statistic can't be totalled into the answer this objective
     *                is actually asking for
     */
    private static int materialTotal(OfflinePlayer player, Statistic statistic, String target,
                                     boolean wildcard, TargetGroup group, List<Material> whenAny) {
        if (wildcard) {
            return whenAny == null ? -1 : sumMaterials(player, statistic, whenAny);
        }
        if (group instanceof MaterialGroup materials) {
            return sumMaterials(player, statistic, materials.members());
        }
        return materialStat(player, statistic, target);
    }

    private static int untyped(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (RuntimeException ex) {
            return -1; // not tracked on this server implementation
        }
    }

    private static int materialStat(OfflinePlayer player, Statistic statistic, String name) {
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

    /** Adds two statistic reads, where -1 means "this server doesn't track it". */
    private static int sum(int first, int second) {
        if (first < 0 && second < 0) {
            return -1;
        }
        return Math.max(first, 0) + Math.max(second, 0);
    }

    /** Upper-cases a target and drops any {@code minecraft:} namespace. */
    private static String normalize(String name) {
        String value = name.trim().toUpperCase(Locale.ROOT);
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    private static int entityStat(OfflinePlayer player, Statistic statistic, String name) {
        EntityType type;
        try {
            type = EntityType.valueOf(normalize(name));
        } catch (IllegalArgumentException ex) {
            return -1;
        }
        try {
            return player.getStatistic(statistic, type);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static int sumMaterials(OfflinePlayer player, Statistic statistic, Iterable<Material> materials) {
        int total = 0;
        boolean any = false;
        for (Material material : materials) {
            try {
                total += player.getStatistic(statistic, material);
                any = true;
            } catch (RuntimeException ignored) {
                // This material has no such statistic; the rest of the family still counts.
            }
        }
        return any ? total : -1;
    }

    private static int sumEntities(OfflinePlayer player, EntityGroup group) {
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
