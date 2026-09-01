package com.diamend.spyglass.util;

import java.util.Locale;
import java.util.Map;

/**
 * One spelling for a statistic, whichever half of the plugin found it.
 *
 * <p>The same number has two names. A live player's statistics come through
 * Bukkit's enum — {@code MINE_BLOCK} with a material, {@code PLAY_ONE_MINUTE} —
 * while the file on disk uses Mojang's — {@code mined.stone},
 * {@code custom.play_time}. Left alone, that means a filter that works on an
 * online player finds nothing on the same player an hour later, and a diff
 * across a logout reports every statistic as removed and re-added.
 *
 * <p>So both paths are folded onto the vanilla name, which is the one that
 * actually appears in {@code stats/&lt;uuid&gt;.json} and the one a server owner
 * will recognise from the game.
 */
public final class Statistics {

    /** Bukkit's typed statistics, and the vanilla category each belongs to. */
    private static final Map<String, String> TYPED = Map.of(
            "mine_block", "mined",
            "craft_item", "crafted",
            "use_item", "used",
            "break_item", "broken",
            "pickup", "picked_up",
            "drop", "dropped",
            "kill_entity", "killed",
            "entity_killed_by", "killed_by");

    /**
     * The untyped statistics Mojang and Bukkit disagree about. Everything not
     * listed here is the same word in both, just lower-cased.
     */
    private static final Map<String, String> CUSTOM = Map.ofEntries(
            Map.entry("play_one_minute", "play_time"),
            Map.entry("drop_count", "drop"),
            Map.entry("cake_slices_eaten", "eat_cake_slice"),
            Map.entry("cauldron_filled", "fill_cauldron"),
            Map.entry("cauldron_used", "use_cauldron"),
            Map.entry("armor_cleaned", "clean_armor"),
            Map.entry("banner_cleaned", "clean_banner"),
            Map.entry("shulker_box_cleaned", "clean_shulker_box"),
            Map.entry("brewingstand_interaction", "interact_with_brewingstand"),
            Map.entry("beacon_interaction", "interact_with_beacon"),
            Map.entry("furnace_interaction", "interact_with_furnace"),
            Map.entry("crafting_table_interaction", "interact_with_crafting_table"),
            Map.entry("dropper_inspected", "inspect_dropper"),
            Map.entry("hopper_inspected", "inspect_hopper"),
            Map.entry("dispenser_inspected", "inspect_dispenser"),
            Map.entry("noteblock_played", "play_noteblock"),
            Map.entry("noteblock_tuned", "tune_noteblock"),
            Map.entry("record_played", "play_record"),
            Map.entry("flower_potted", "pot_flower"),
            Map.entry("trapped_chest_triggered", "trigger_trapped_chest"),
            Map.entry("enderchest_opened", "open_enderchest"),
            Map.entry("chest_opened", "open_chest"),
            Map.entry("shulker_box_opened", "open_shulker_box"),
            Map.entry("item_enchanted", "enchant_item"));

    private Statistics() {
    }

    /**
     * The vanilla name for one of Bukkit's typed statistics.
     *
     * @param statistic Bukkit's enum name, e.g. {@code MINE_BLOCK}
     * @param target    the block, item or entity key, e.g. {@code stone}
     */
    public static String typed(String statistic, String target) {
        String folded = fold(statistic);
        String category = TYPED.getOrDefault(folded, folded);
        return category + "." + Fmt.shortKey(target == null ? "" : target.toLowerCase(Locale.ROOT));
    }

    /** The vanilla name for one of Bukkit's untyped statistics, under {@code custom}. */
    public static String untyped(String statistic) {
        String folded = fold(statistic);
        return "custom." + CUSTOM.getOrDefault(folded, folded);
    }

    /**
     * True when the statistic counts game ticks — playtime and the various
     * "time since" clocks. Those want rendering as a duration, not a number.
     */
    public static boolean isTicks(String name) {
        String folded = fold(name);
        return folded.contains("time") || folded.contains("one_minute");
    }

    /** True when the statistic counts centimetres travelled. */
    public static boolean isDistance(String name) {
        return fold(name).endsWith("one_cm");
    }

    /** Ticks, blocks travelled or a plain count, as the statistic deserves. */
    public static String value(String name, long value) {
        if (isDistance(name)) {
            return Fmt.centimetres(value);
        }
        return isTicks(name) ? Fmt.ticks(value) : Fmt.count(value);
    }

    private static String fold(String name) {
        return name == null ? "" : Fmt.shortKey(name.trim().toLowerCase(Locale.ROOT));
    }
}
