package com.diamend.customachievements.gui;

import com.diamend.customachievements.achievement.TargetGroup;
import com.diamend.customachievements.achievement.TargetGroups;
import com.diamend.customachievements.achievement.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the list of pickable {@link TargetOption}s for a given trigger, used
 * by the {@link TargetPickerMenu}.
 */
public final class TargetCatalog {

    // The default AuraSkills skills (used as suggestions without a hard dependency).
    private static final String[] AURA_SKILLS = {
            "FARMING", "FORAGING", "MINING", "FISHING", "EXCAVATION", "ARCHERY", "DEFENSE",
            "FIGHTING", "ENDURANCE", "AGILITY", "ALCHEMY", "ENCHANTING", "SORCERY", "HEALING", "FORGING"
    };

    private TargetCatalog() {
    }

    /**
     * Damage causes worth an icon of their own in the death picker; anything
     * else falls back to a skull.
     */
    private static final Map<String, Material> DEATH_ICONS = Map.ofEntries(
            Map.entry("FALL", Material.FEATHER),
            Map.entry("LAVA", Material.LAVA_BUCKET),
            Map.entry("FIRE", Material.FLINT_AND_STEEL),
            Map.entry("FIRE_TICK", Material.FLINT_AND_STEEL),
            Map.entry("DROWNING", Material.WATER_BUCKET),
            Map.entry("VOID", Material.END_PORTAL_FRAME),
            Map.entry("ENTITY_ATTACK", Material.IRON_SWORD),
            Map.entry("ENTITY_EXPLOSION", Material.TNT),
            Map.entry("BLOCK_EXPLOSION", Material.TNT),
            Map.entry("PROJECTILE", Material.ARROW),
            Map.entry("MAGIC", Material.POTION),
            Map.entry("POISON", Material.SPIDER_EYE),
            Map.entry("WITHER", Material.WITHER_ROSE),
            Map.entry("SUFFOCATION", Material.SAND),
            Map.entry("STARVATION", Material.ROTTEN_FLESH),
            Map.entry("LIGHTNING", Material.LIGHTNING_ROD),
            Map.entry("HOT_FLOOR", Material.MAGMA_BLOCK),
            Map.entry("FREEZE", Material.POWDER_SNOW_BUCKET),
            Map.entry("CRAMMING", Material.HOPPER),
            Map.entry("FLY_INTO_WALL", Material.ELYTRA),
            Map.entry("THORNS", Material.CACTUS),
            Map.entry("FALLING_BLOCK", Material.ANVIL));

    /** Whether a paginated picker makes sense for this trigger's target. */
    public static boolean hasPicker(TriggerType trigger) {
        return switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE, ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN, ITEM_HAVE,
                 ENTITY_KILL, PLAYER_DEATH, AURASKILLS_LEVEL, REACH_DIMENSION -> true;
            default -> false;
        };
    }

    public static List<TargetOption> forTrigger(TriggerType trigger) {
        List<TargetOption> options = new ArrayList<>();
        switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE -> {
                addGroups(options, trigger);
                for (Material material : Material.values()) {
                    if (material.isBlock() && material.isItem() && !material.isAir()) {
                        options.add(new TargetOption(material.name(), material));
                    }
                }
            }
            case ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN, ITEM_HAVE -> {
                addGroups(options, trigger);
                for (Material material : Material.values()) {
                    if (material.isItem() && !material.isAir()) {
                        options.add(new TargetOption(material.name(), material));
                    }
                }
            }
            case ENTITY_KILL -> {
                addGroups(options, trigger);
                for (EntityType type : EntityType.values()) {
                    if (type == EntityType.UNKNOWN) {
                        continue;
                    }
                    Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
                    options.add(new TargetOption(type.name(), egg != null ? egg : Material.NAME_TAG));
                }
            }
            case PLAYER_DEATH -> {
                // A death matches on either how you died or what killed you, so
                // offer both: the damage causes first, then every mob.
                addGroups(options, trigger);
                for (org.bukkit.event.entity.EntityDamageEvent.DamageCause cause
                        : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
                    options.add(new TargetOption(cause.name(),
                            DEATH_ICONS.getOrDefault(cause.name(), Material.SKELETON_SKULL),
                            null, List.of("Died of " + friendly(cause.name()))));
                }
                for (EntityType type : EntityType.values()) {
                    if (type == EntityType.UNKNOWN) {
                        continue;
                    }
                    Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
                    options.add(new TargetOption(type.name(), egg != null ? egg : Material.NAME_TAG,
                            null, List.of("Killed by " + friendly(type.name()))));
                }
            }
            case AURASKILLS_LEVEL -> {
                for (String skill : AURA_SKILLS) {
                    options.add(new TargetOption(skill, Material.EXPERIENCE_BOTTLE));
                }
            }
            case REACH_DIMENSION -> {
                options.add(new TargetOption("NORMAL", Material.GRASS_BLOCK));
                options.add(new TargetOption("NETHER", Material.NETHERRACK));
                options.add(new TargetOption("THE_END", Material.END_STONE));
                for (World world : Bukkit.getWorlds()) {
                    options.add(new TargetOption(world.getName(), Material.MAP));
                }
            }
            default -> {
            }
        }
        return options;
    }

    /** Turns an enum-style name into readable text: {@code FIRE_TICK} → "fire tick". */
    private static String friendly(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /**
     * Puts the trigger's groups ("Any Logs", "Any Hostile Mobs", ...) at the
     * front of the list so a whole family can be picked in one click, before the
     * long tail of individual values.
     */
    private static void addGroups(List<TargetOption> options, TriggerType trigger) {
        for (TargetGroup group : TargetGroups.forTrigger(trigger)) {
            options.add(new TargetOption(group.targetValue(), group.icon(),
                    "Any " + group.label(),
                    List.of(group.description(),
                            "Matches " + group.memberCount() + " type(s)")));
        }
    }
}
