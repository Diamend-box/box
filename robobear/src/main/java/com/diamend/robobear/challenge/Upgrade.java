package com.diamend.robobear.challenge;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What Cogs buy between rounds.
 *
 * <p>Bee Swarm spends Cogs on buffs, drives and rerolls inside a single run;
 * none of it leaves the challenge. That property is what lets this exist on a
 * server whose spec (§18) rejects a second currency outright — Cogs are not
 * money. They are earned inside a run, spent inside the same run, and gone when
 * it ends. Nothing here can be banked, traded or carried out.
 */
public enum Upgrade {

    HASTE("Overclocked Drill", Material.GOLDEN_PICKAXE, 3,
            "Mine faster for the rest of the run."),
    SWIFTNESS("Servo Legs", Material.FEATHER, 3,
            "Move faster for the rest of the run."),
    STRENGTH("Impact Driver", Material.IRON_SWORD, 2,
            "Hit harder for the rest of the run."),
    RESISTANCE("Plating", Material.IRON_CHESTPLATE, 2,
            "Take less damage for the rest of the run."),
    OVERCLOCK("Spare Battery", Material.CLOCK, 3,
            "Adds time to this round and every one after it."),
    SALVAGE("Scrap Magnet", Material.HOPPER, 3,
            "Every round from now on pays more Cogs.");

    private final String displayName;
    private final Material icon;
    private final int maxLevel;
    private final String description;

    Upgrade(String displayName, Material icon, int maxLevel, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public String description() {
        return description;
    }

    /** The config key this upgrade's numbers live under. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * What the next level costs. Each level costs more than the last, so a
     * player can't simply max one thing on the first shop visit.
     */
    public int costFor(int nextLevel, int baseCost, double growth) {
        return (int) Math.max(1, Math.round(baseCost * Math.pow(growth, Math.max(0, nextLevel - 1))));
    }

    /** The potion effect this upgrade grants at a level, or null if it grants none. */
    public PotionEffect effectFor(int level, int durationTicks) {
        PotionEffectType type = switch (this) {
            case HASTE -> PotionEffectType.HASTE;
            case SWIFTNESS -> PotionEffectType.SPEED;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            default -> null;
        };
        if (type == null || level <= 0) {
            return null;
        }
        // ambient + no particles: a run buff shouldn't cost the player their view.
        return new PotionEffect(type, durationTicks, level - 1, true, false, true);
    }
}
