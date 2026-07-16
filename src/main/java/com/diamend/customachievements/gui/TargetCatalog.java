package com.diamend.customachievements.gui;

import com.diamend.customachievements.achievement.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

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

    /** Whether a paginated picker makes sense for this trigger's target. */
    public static boolean hasPicker(TriggerType trigger) {
        return switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE, ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN, ENTITY_KILL,
                 AURASKILLS_LEVEL, REACH_DIMENSION -> true;
            default -> false;
        };
    }

    public static List<TargetOption> forTrigger(TriggerType trigger) {
        List<TargetOption> options = new ArrayList<>();
        switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE -> {
                for (Material material : Material.values()) {
                    if (material.isBlock() && material.isItem() && !material.isAir()) {
                        options.add(new TargetOption(material.name(), material));
                    }
                }
            }
            case ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN -> {
                for (Material material : Material.values()) {
                    if (material.isItem() && !material.isAir()) {
                        options.add(new TargetOption(material.name(), material));
                    }
                }
            }
            case ENTITY_KILL -> {
                for (EntityType type : EntityType.values()) {
                    if (type == EntityType.UNKNOWN) {
                        continue;
                    }
                    Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
                    options.add(new TargetOption(type.name(), egg != null ? egg : Material.NAME_TAG));
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
}
