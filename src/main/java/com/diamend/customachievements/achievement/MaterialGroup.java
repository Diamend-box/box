package com.diamend.customachievements.achievement;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A named family of materials — all logs, all ores, all wool — that an
 * objective can target instead of one specific block or item. It's what makes
 * "mine 100 logs" possible without having to pick <em>oak</em> logs.
 *
 * <p>Objectives store a group as {@code #ID} (e.g. {@code #LOGS}) in their
 * target field, mirroring Minecraft's own {@code #minecraft:logs} tag syntax.
 * The leading {@code #} can't collide with a material name, so old single-target
 * objectives keep working untouched.
 *
 * <p>Membership is decided from the material's <em>name</em> rather than a fixed
 * list of enum constants, so wood and ore types added by later Minecraft
 * versions are included automatically without a plugin update.
 */
public enum MaterialGroup implements TargetGroup {

    LOGS("Logs", "Any log, stem, hyphae or wood block — every tree type, stripped or not",
            Material.OAK_LOG,
            name -> name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE")
                    // Crimson/warped stems are logs; melon, pumpkin and mushroom stems are not.
                    || (name.endsWith("_STEM") && !name.contains("MELON")
                        && !name.contains("PUMPKIN") && !name.contains("MUSHROOM")),
            "BAMBOO_BLOCK", "STRIPPED_BAMBOO_BLOCK"),

    ORES("Ores", "Any ore, including deepslate and nether variants, plus ancient debris",
            Material.IRON_ORE,
            name -> name.endsWith("_ORE"),
            "ANCIENT_DEBRIS"),

    PLANKS("Planks", "Any wooden planks", Material.OAK_PLANKS,
            name -> name.endsWith("_PLANKS")),

    LEAVES("Leaves", "Any tree leaves", Material.OAK_LEAVES,
            name -> name.endsWith("_LEAVES")),

    SAPLINGS("Saplings", "Any sapling or propagule", Material.OAK_SAPLING,
            name -> name.endsWith("_SAPLING"),
            "MANGROVE_PROPAGULE"),

    FLOWERS("Flowers", "Any flower, small or tall", Material.POPPY,
            name -> name.endsWith("_TULIP") || name.endsWith("_EYEBLOSSOM"),
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY",
            "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE", "TORCHFLOWER", "SUNFLOWER",
            "LILAC", "ROSE_BUSH", "PEONY", "PITCHER_PLANT", "PINK_PETALS", "SPORE_BLOSSOM"),

    CROPS("Crops", "Farmable crops — wheat, carrots, potatoes, nether wart and friends",
            Material.WHEAT,
            name -> false,
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "PUMPKIN",
            "MELON", "SUGAR_CANE", "BAMBOO", "CACTUS", "SWEET_BERRY_BUSH", "TORCHFLOWER_CROP",
            "PITCHER_CROP", "KELP", "KELP_PLANT"),

    STONES("Stone Types", "Naturally generated stone — stone, deepslate, tuff, basalt, netherrack …",
            Material.STONE,
            name -> false,
            "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE",
            "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK",
            "BLACKSTONE", "BASALT", "SMOOTH_BASALT", "NETHERRACK", "END_STONE",
            "SANDSTONE", "RED_SANDSTONE", "MAGMA_BLOCK", "OBSIDIAN"),

    DIRT("Dirt & Grass", "Diggable ground — dirt, grass, podzol, mycelium, mud …",
            Material.DIRT,
            name -> false,
            "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK", "PODZOL", "MYCELIUM",
            "FARMLAND", "DIRT_PATH", "MUD", "MOSS_BLOCK", "SOUL_SAND", "SOUL_SOIL"),

    SAND("Sand & Gravel", "Falling ground blocks — sand, red sand and gravel", Material.SAND,
            name -> false,
            "SAND", "RED_SAND", "GRAVEL", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL"),

    WOOL("Wool", "Any colour of wool", Material.WHITE_WOOL,
            name -> name.endsWith("_WOOL")),

    TERRACOTTA("Terracotta", "Any terracotta, glazed or plain", Material.TERRACOTTA,
            name -> name.equals("TERRACOTTA") || name.endsWith("_TERRACOTTA")),

    CONCRETE("Concrete", "Any colour of concrete (powder not included)", Material.WHITE_CONCRETE,
            name -> name.endsWith("_CONCRETE")),

    GLASS("Glass", "Any glass block or pane, stained or not", Material.GLASS,
            name -> name.equals("GLASS") || name.equals("GLASS_PANE")
                    || name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE")),

    CORAL("Coral", "Any coral, coral block or coral fan", Material.TUBE_CORAL,
            name -> name.contains("CORAL")),

    ICE("Ice", "Any kind of ice", Material.ICE,
            name -> false,
            "ICE", "PACKED_ICE", "BLUE_ICE", "FROSTED_ICE"),

    MUSHROOMS("Mushrooms", "Mushrooms and mushroom blocks", Material.RED_MUSHROOM,
            name -> name.contains("MUSHROOM") && !name.endsWith("_STEW")),

    SLABS("Slabs", "Any slab", Material.SMOOTH_STONE_SLAB,
            name -> name.endsWith("_SLAB")),

    STAIRS("Stairs", "Any stairs", Material.OAK_STAIRS,
            name -> name.endsWith("_STAIRS")),

    FENCES("Fences", "Any fence or fence gate", Material.OAK_FENCE,
            name -> name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")),

    DOORS("Doors", "Any door or trapdoor", Material.OAK_DOOR,
            name -> name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR"));

    private final String label;
    private final String description;
    private final Material icon;
    private final Predicate<String> rule;
    private final Set<String> extras;

    // Resolved against the running server's material list on first use.
    private volatile List<Material> members;

    MaterialGroup(String label, String description, Material icon,
                  Predicate<String> rule, String... extras) {
        this.label = label;
        this.description = description;
        this.icon = icon;
        this.rule = rule;
        this.extras = Set.copyOf(Arrays.asList(extras));
    }

    /**
     * Resolves a group from a target string, accepting {@code #LOGS}, {@code LOGS},
     * {@code #minecraft:logs} and the singular {@code #LOG}. Returns null when no
     * group matches, in which case the target is treated as a plain material name.
     */
    public static MaterialGroup byId(String raw) {
        // Be forgiving about hand-edited YAML: "#LOG" also finds LOGS.
        for (String id : TargetGroups.candidateIds(raw)) {
            MaterialGroup group = lookup(id);
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    private static MaterialGroup lookup(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public boolean matches(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return false;
        }
        String name = materialName.trim().toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.startsWith("LEGACY_")) {
            // Pre-flattening aliases (LEGACY_LOG, LEGACY_WOOD, ...) would other-
            // wise slip into the suffix rules; they never occur in live events.
            return false;
        }
        return extras.contains(name) || rule.test(name);
    }

    /** The materials on this server that belong to the group (used for the picker). */
    public List<Material> members() {
        List<Material> cached = members;
        if (cached == null) {
            Set<Material> found = new LinkedHashSet<>();
            for (Material material : Material.values()) {
                if (matches(material.name())) {
                    found.add(material);
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
