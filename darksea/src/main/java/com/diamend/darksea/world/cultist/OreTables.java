package com.diamend.darksea.world.cultist;

import java.util.List;

/**
 * The parsed {@code ores.yml}: what vein types exist and how far apart they
 * must stand. Pure, immutable, and swapped wholesale by {@code /ds reload} —
 * the same shape as {@link com.diamend.darksea.npc.ShopStock}.
 *
 * <p>{@code minSpacing} is the supply dial nobody thinks of as one. Two veins
 * close enough to work back to back are effectively a single richer vein with
 * half the travel, which is exactly the outcome node-based supply exists to
 * prevent.
 */
public record OreTables(List<OreType> types, int minSpacing, int maxPlacementTries) {

    public OreTables {
        types = List.copyOf(types);
        minSpacing = Math.max(1, minSpacing);
        maxPlacementTries = Math.max(1, maxPlacementTries);
    }

    public static OreTables empty() {
        return new OreTables(List.of(), 48, 2000);
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

    /** Every drop id this config can produce — asserted to be unsellable. */
    public List<String> dropIds() {
        return types.stream().map(OreType::dropId).toList();
    }
}
