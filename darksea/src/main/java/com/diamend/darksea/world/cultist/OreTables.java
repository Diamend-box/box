package com.diamend.darksea.world.cultist;

import com.diamend.darksea.item.ItemDisplay;

import java.util.List;
import java.util.Map;

/**
 * The parsed {@code ores.yml}: what vein types exist and how far apart they
 * must stand. Pure, immutable, and swapped wholesale by {@code /ds reload} —
 * the same shape as {@link com.diamend.darksea.npc.ShopStock}.
 *
 * <p>{@code minSpacing} is the supply dial nobody thinks of as one. Two veins
 * close enough to work back to back are effectively a single richer vein with
 * half the travel, which is exactly the outcome node-based supply exists to
 * prevent.
 *
 * <p>The reference-gear fields define what every {@code mine-seconds} is
 * measured against — see {@link MiningSpeed}. They live here rather than on the
 * individual types because the whole economy has to be calibrated to one
 * yardstick; letting each crystal pick its own would make the numbers
 * incomparable.
 *
 * @param referenceTool       material name of the pickaxe the numbers assume
 * @param referenceEfficiency Efficiency level that pickaxe is assumed to carry
 * @param floorSeconds        no block may take less than this, however stacked
 * @param displays            per-drop-id cosmetic overrides from the {@code items:}
 *                            section: what each crystal is called, what it reads
 *                            as in the hand, and which material carries it. Keyed
 *                            by drop id, and every entry is optional — an absent
 *                            id, or an absent field within one, means "ship what
 *                            the plugin ships".
 */
public record OreTables(List<OreType> types, int minSpacing, int maxPlacementTries,
                        String referenceTool, int referenceEfficiency, double floorSeconds,
                        Map<String, ItemDisplay> displays) {

    /** Without cosmetic overrides — the shipped names and materials stand. */
    public OreTables(List<OreType> types, int minSpacing, int maxPlacementTries,
                     String referenceTool, int referenceEfficiency, double floorSeconds) {
        this(types, minSpacing, maxPlacementTries, referenceTool, referenceEfficiency,
                floorSeconds, Map.of());
    }

    public OreTables {
        displays = displays == null ? Map.of() : Map.copyOf(displays);
        types = List.copyOf(types);
        minSpacing = Math.max(1, minSpacing);
        maxPlacementTries = Math.max(1, maxPlacementTries);
        referenceTool = referenceTool == null || referenceTool.isBlank()
                ? "NETHERITE_PICKAXE" : referenceTool;
        referenceEfficiency = Math.max(0, referenceEfficiency);
        floorSeconds = Math.max(MiningSpeed.FLOOR_SECONDS_MIN, floorSeconds);
    }

    public static OreTables empty() {
        return new OreTables(List.of(), 48, 2000, "NETHERITE_PICKAXE", 15, 0.4);
    }

    /**
     * The dig speed every extraction is scaled against. Falls back to a bare
     * netherite pickaxe if the configured reference tool is not a pickaxe at
     * all, so a typo slows the caves down rather than dividing by zero.
     */
    public double referenceSpeed() {
        MiningSpeed.Tier tier = MiningSpeed.tierOf(referenceTool);
        if (tier == null) {
            tier = MiningSpeed.Tier.NETHERITE;
        }
        return MiningSpeed.speed(tier, referenceEfficiency, 0, 0);
    }

    /** Total veins across every type — how many landmarks the world holds. */
    public int totalVeins() {
        int total = 0;
        for (OreType type : types) {
            total += type.veinCount();
        }
        return total;
    }

    public OreType byId(String id) {
        for (OreType type : types) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** The cosmetic override for a drop id — never null, empty if unconfigured. */
    public ItemDisplay displayFor(String dropId) {
        return displays.getOrDefault(dropId, ItemDisplay.NONE);
    }

    /** Every drop id this config can produce — asserted to be unsellable. */
    public List<String> dropIds() {
        return types.stream().map(OreType::dropId).toList();
    }
}
