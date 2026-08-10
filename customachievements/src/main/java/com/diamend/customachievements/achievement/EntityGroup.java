package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Raider;
import org.bukkit.entity.WaterMob;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A named family of mobs — every hostile mob, every animal, every undead — that
 * a {@code ENTITY_KILL} objective can target instead of one specific entity
 * type. It's what makes "kill 100 hostile mobs" possible without one objective
 * per mob.
 *
 * <p>Stored as {@code #ID} in the objective's target field, exactly like
 * {@link MaterialGroup}. Where Bukkit already models the category as an
 * interface ({@link Enemy}, {@link Animals}, {@link Raider}, ...) membership is
 * taken from the entity's own class, so mobs added by later Minecraft versions
 * land in the right group without a plugin update. The categories vanilla
 * doesn't express as a type — undead, arthropods, "lives in the nether" — are
 * listed explicitly.
 */
public enum EntityGroup implements TargetGroup {

    HOSTILE("Hostile Mobs", "Anything that attacks you — zombies, creepers, slimes, ghasts, bosses …",
            Material.ZOMBIE_SPAWN_EGG,
            type -> isA(type, Enemy.class)),

    ANIMALS("Animals", "Farm and wild animals — cows, pigs, sheep, wolves, horses, bees …",
            Material.COW_SPAWN_EGG,
            type -> isA(type, Animals.class)),

    UNDEAD("Undead", "Everything that burns in daylight or takes extra damage from Smite",
            Material.SKELETON_SPAWN_EGG,
            type -> false,
            "ZOMBIE", "ZOMBIE_VILLAGER", "HUSK", "DROWNED", "ZOMBIFIED_PIGLIN", "ZOGLIN",
            "SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON", "SKELETON_HORSE", "ZOMBIE_HORSE",
            "PHANTOM", "WITHER"),

    ARTHROPODS("Arthropods", "Spiders, silverfish, endermites and bees (the Bane of Arthropods crowd)",
            Material.SPIDER_SPAWN_EGG,
            type -> false,
            "SPIDER", "CAVE_SPIDER", "SILVERFISH", "ENDERMITE", "BEE"),

    ILLAGERS("Illagers & Raiders", "The raid roster — pillagers, vindicators, evokers, ravagers, witches",
            Material.PILLAGER_SPAWN_EGG,
            type -> isA(type, Raider.class),
            "VEX"),

    BOSSES("Bosses", "The big ones — ender dragon, wither, warden and elder guardians",
            Material.DRAGON_EGG,
            type -> isA(type, Boss.class),
            "WARDEN", "ELDER_GUARDIAN"),

    AQUATIC("Aquatic Mobs", "Anything at home in water — fish, squid, dolphins, guardians, drowned",
            Material.COD_SPAWN_EGG,
            type -> isA(type, WaterMob.class),
            "GUARDIAN", "ELDER_GUARDIAN", "DROWNED", "TURTLE", "FROG", "TADPOLE", "AXOLOTL"),

    NETHER("Nether Mobs", "Mobs native to the Nether — blazes, ghasts, piglins, hoglins, striders",
            Material.BLAZE_SPAWN_EGG,
            type -> false,
            "BLAZE", "GHAST", "MAGMA_CUBE", "WITHER_SKELETON", "PIGLIN", "PIGLIN_BRUTE",
            "HOGLIN", "ZOGLIN", "STRIDER", "ZOMBIFIED_PIGLIN", "SKELETON", "WITHER"),

    END("End Mobs", "Mobs of the End — endermen, endermites, shulkers and the dragon",
            Material.ENDER_PEARL,
            type -> false,
            "ENDERMAN", "ENDERMITE", "SHULKER", "ENDER_DRAGON");

    private final String label;
    private final String description;
    private final Material icon;
    private final Predicate<EntityType> rule;
    private final Set<String> extras;

    // Resolved against the running server's entity list on first use.
    private volatile List<EntityType> members;

    EntityGroup(String label, String description, Material icon,
                Predicate<EntityType> rule, String... extras) {
        this.label = label;
        this.description = description;
        this.icon = icon;
        this.rule = rule;
        this.extras = Set.copyOf(Arrays.asList(extras));
    }

    /**
     * Resolves a group from a target string, accepting {@code #HOSTILE},
     * {@code HOSTILE} and {@code #minecraft:hostile}. Returns null when no group
     * matches, in which case the target is treated as a plain entity name.
     */
    public static EntityGroup byId(String raw) {
        // Be forgiving about hand-edited YAML: "#BOSS" also finds BOSSES.
        for (String id : TargetGroups.candidateIds(raw)) {
            EntityGroup group = lookup(id);
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    private static EntityGroup lookup(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Whether the entity's class implements the given Bukkit category interface. */
    private static boolean isA(EntityType type, Class<?> category) {
        try {
            Class<?> entityClass = type.getEntityClass();
            return entityClass != null && category.isAssignableFrom(entityClass);
        } catch (RuntimeException ex) {
            // Some server builds don't expose a class for every entity type;
            // the explicit member list still covers those.
            return false;
        }
    }

    @Override
    public boolean matches(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return false;
        }
        String name = entityName.trim().toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (extras.contains(name)) {
            return true;
        }
        EntityType type = entityType(name);
        return type != null && rule.test(type);
    }

    private static EntityType entityType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null; // an entity this server doesn't know
        }
    }

    /** The entity types on this server that belong to the group. */
    public List<EntityType> members() {
        List<EntityType> cached = members;
        if (cached == null) {
            Set<EntityType> found = new LinkedHashSet<>();
            for (EntityType type : EntityType.values()) {
                if (matches(type.name())) {
                    found.add(type);
                }
            }
            cached = List.copyOf(found);
            members = cached;
        }
        return cached;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public int memberCount() {
        return members().size();
    }
}
