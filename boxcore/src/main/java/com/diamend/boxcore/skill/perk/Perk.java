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

    // --- Fighting ---------------------------------------------------------
    LIFESTEAL(Kind.PERCENT, Stacking.SUM, 0.04,
            "Heal {} of the melee damage you deal"),
    ADRENALINE(Kind.SECONDS, Stacking.SUM, 3,
            "Speed II for {} after a kill"),
    FINISHER(Kind.PERCENT, Stacking.SUM, 0.08,
            "{} damage to targets below a third health"),
    PLAYER_DAMAGE(Kind.PERCENT, Stacking.SUM, 0.15,
            "{} damage to other players"),
    PROJECTILE_DAMAGE(Kind.PERCENT, Stacking.SUM, 0.08,
            "{} damage with bows, crossbows and tridents"),
    VENOM_STRIKE(Kind.SECONDS, Stacking.MAX, 3,
            "Your melee hits poison the target for {}"),
    ARROW_SAVER(Kind.CHANCE, Stacking.SUM, 0.15,
            "{} chance not to spend the arrow"),
    MOB_LOOT(Kind.CHANCE, Stacking.SUM, 0.25,
            "{} chance to double a mob's drops"),

    // --- Staying alive ----------------------------------------------------
    POTION_POWER(Kind.PERCENT, Stacking.SUM, 0.15,
            "Your splash potions land {} stronger"),
    GAPPLE_BOOST(Kind.PERCENT, Stacking.SUM, 0.25,
            "Golden apple effects last {} longer"),
    // The cooldown is LAST_BREATH_COOLDOWN_SECONDS in PerkListener — keep the
    // number in this sentence in step with it.
    LAST_BREATH(Kind.SECONDS, Stacking.SUM, 4,
            "Speed II and Resistance I for {} when dropped low (30s cooldown)"),
    DEBUFF_RESIST(Kind.PERCENT, Stacking.SUM, 0.15,
            "Harmful potion effects on you are {} shorter"),
    SECOND_CHANCE(Kind.MINUTES, Stacking.MIN, 15,
            "Survive one killing blow every {}"),
    HUNGER_SAVER(Kind.PERCENT, Stacking.SUM, 0.15,
            "{} less exhaustion from everything you do"),

    // --- Grinding ---------------------------------------------------------
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
    XP_BOOST(Kind.PERCENT, Stacking.SUM, 0.15,
            "{} experience from every source");

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
        // simplify(), not normalize(): the latter strips legacy attribute
        // prefixes, which would turn player_damage into damage.
        String key = Registries.simplify(name);
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
            map.put("bow_damage", PROJECTILE_DAMAGE);
            map.put("pvp_damage", PLAYER_DAMAGE);
            map.put("poison_strike", VENOM_STRIKE);
            map.put("infinity", ARROW_SAVER);
            map.put("gapple", GAPPLE_BOOST);
            map.put("autosmelt", AUTO_SMELT);
            map.put("smelt_touch", AUTO_SMELT);
            map.put("auto_replant", REPLANT);
            map.put("xp_bonus", XP_BOOST);
            map.put("exp_boost", XP_BOOST);
            map.put("cheat_death", SECOND_CHANCE);
            index = map;
        }
        return index.get(key);
    }
}
