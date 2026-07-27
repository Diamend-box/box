package com.diamend.boxcore.skill.perk;

import com.diamend.boxcore.util.Registries;
import com.diamend.boxcore.util.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The gameplay perks a skill node can grant — everything that isn't just a
 * vanilla attribute modifier or a potion effect.
 *
 * <p>Each constant carries enough metadata to describe itself, so a node's lore
 * is generated from the numbers the perk actually runs with. A server owner
 * changes {@code amount} in {@code trees.yml} and the tooltip follows.
 *
 * <p>{@link Stacking} decides what happens when two owned nodes grant the same
 * perk: chances and bonuses add up, cooldowns take the shortest, and flags are
 * simply on.
 */
public enum Perk {

    LIFESTEAL(Kind.PERCENT, Stacking.SUM, 0.04,
            "Heal {} of the melee damage you deal"),
    ADRENALINE(Kind.SECONDS, Stacking.SUM, 3,
            "Speed II for {} after a kill"),
    FINISHER(Kind.PERCENT, Stacking.SUM, 0.08,
            "{} damage to targets below a third health"),
    MOB_LOOT(Kind.CHANCE, Stacking.SUM, 0.25,
            "{} chance to double a mob's drops"),

    ORE_BOUNTY(Kind.CHANCE, Stacking.SUM, 0.08,
            "{} chance to double an ore's drops"),
    LOG_BOUNTY(Kind.CHANCE, Stacking.SUM, 0.10,
            "{} chance for a bonus log"),
    AUTO_SMELT(Kind.FLAG, Stacking.MAX, 1,
            "Ores, sand and cobble drop already smelted"),
    REPLANT(Kind.FLAG, Stacking.MAX, 1,
            "Fully grown crops replant themselves"),
    FISHING_SPEED(Kind.PERCENT, Stacking.SUM, 0.12,
            "Fish bite {} sooner"),

    HUNGER_SAVER(Kind.PERCENT, Stacking.SUM, 0.15,
            "{} less exhaustion from everything you do"),
    SECOND_CHANCE(Kind.MINUTES, Stacking.MIN, 15,
            "Survive one killing blow every {}"),
    NO_FREEZE(Kind.FLAG, Stacking.MAX, 1,
            "Powder snow can no longer freeze you"),
    SAFE_STOMACH(Kind.FLAG, Stacking.MAX, 1,
            "Bad food can't poison or starve you"),

    XP_BOOST(Kind.PERCENT, Stacking.SUM, 0.15,
            "{} experience from every source"),
    ANVIL_DISCOUNT(Kind.PERCENT, Stacking.SUM, 0.20,
            "Anvil repairs cost {} fewer levels"),
    TOOL_SAVER(Kind.CHANCE, Stacking.SUM, 0.10,
            "{} chance to spare a point of durability"),
    BONUS_CRAFT(Kind.CHANCE, Stacking.SUM, 0.10,
            "{} chance for a bonus item when you craft"),
    ENCHANT_DISCOUNT(Kind.PERCENT, Stacking.SUM, 0.15,
            "Enchanting offers cost {} fewer levels"),
    SELF_REPAIR(Kind.FLAT, Stacking.SUM, 2,
            "Mends {} durability on your held item every 5s");

    /** How a perk's amount reads in lore. */
    public enum Kind {
        /** A signed proportion, shown as {@code +15%}. */
        PERCENT,
        /** A probability, shown as {@code 10%}. */
        CHANCE,
        /** A signed plain number, shown as {@code +2}. */
        FLAT,
        /** A duration in seconds, shown as {@code 6s}. */
        SECONDS,
        /** A duration in minutes, shown as {@code 15 min}. */
        MINUTES,
        /** On or off; the amount is ignored. */
        FLAG
    }

    /** How two nodes granting the same perk combine. */
    public enum Stacking {
        SUM, MAX, MIN;

        public double combine(double first, double second) {
            return switch (this) {
                case SUM -> first + second;
                case MAX -> Math.max(first, second);
                case MIN -> Math.min(first, second);
            };
        }
    }

    private static Map<String, Perk> index;

    private final Kind kind;
    private final Stacking stacking;
    private final double defaultAmount;
    private final String template;

    Perk(Kind kind, Stacking stacking, double defaultAmount, String template) {
        this.kind = kind;
        this.stacking = stacking;
        this.defaultAmount = defaultAmount;
        this.template = template;
    }

    /** The name used in {@code trees.yml}, e.g. {@code auto_smelt}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Kind kind() {
        return kind;
    }

    public Stacking stacking() {
        return stacking;
    }

    /** Used when a node lists the perk without an explicit {@code amount}. */
    public double defaultAmount() {
        return defaultAmount;
    }

    /** True when the amount carries no meaning and only presence matters. */
    public boolean isFlag() {
        return kind == Kind.FLAG;
    }

    /** One lore line describing this perk at the given total. */
    public String describe(double amount) {
        return kind == Kind.FLAG ? template : template.replace("{}", format(amount));
    }

    private String format(double amount) {
        return switch (kind) {
            case PERCENT -> Text.amount(amount, true);
            case CHANCE -> Text.percent(amount);
            case FLAT -> Text.amount(amount, false);
            case SECONDS -> Text.seconds(amount);
            case MINUTES -> Math.round(amount) + " min";
            case FLAG -> "";
        };
    }

    /**
     * Resolves a config-written perk name, tolerating dashes, dots, case and a
     * few obvious synonyms. Returns null when nothing matches, which the tree
     * loader reports as a config warning.
     */
    public static Perk byName(String name) {
        String key = Registries.normalize(name);
        if (key.isEmpty()) {
            return null;
        }
        if (index == null) {
            Map<String, Perk> map = new HashMap<>();
            for (Perk perk : values()) {
                map.put(perk.id(), perk);
            }
            map.put("life_steal", LIFESTEAL);
            map.put("vampirism", LIFESTEAL);
            map.put("autosmelt", AUTO_SMELT);
            map.put("smelt_touch", AUTO_SMELT);
            map.put("auto_replant", REPLANT);
            map.put("xp_bonus", XP_BOOST);
            map.put("exp_boost", XP_BOOST);
            map.put("unbreaking", TOOL_SAVER);
            map.put("mending", SELF_REPAIR);
            map.put("cheat_death", SECOND_CHANCE);
            index = map;
        }
        return index.get(key);
    }
}
