package com.diamend.darksea.world.cultist;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides where the world's ore veins stand. Pure, because
 * {@link CultistCarve} is pure — the scatter can find its own cave floors
 * without a loaded world, so "are the veins reachable, spaced out, and the
 * same every time" is a unit test rather than something you fly around
 * checking.
 *
 * <p>Veins are embedded in the floor with their origin one block <em>under</em>
 * the surface, so the blob breaks through into the open cave rather than
 * floating in it. That is what makes a vein something you spot from across a
 * cavern and walk to.
 */
public final class VeinScatter {

    /** One placed vein: which type, and where its origin block sits. */
    public record Placement(String typeId, int x, int y, int z, long seed) {

        public int distanceSquared2D(Placement other) {
            int dx = x - other.x();
            int dz = z - other.z();
            return dx * dx + dz * dz;
        }
    }

    private VeinScatter() {
    }

    /**
     * Scatters every configured vein across the caves, deterministically from
     * the world seed — regenerate the world with the same seed and the veins
     * come back in the same places.
     *
     * <p>Placement rejects a candidate for three reasons: it has no walkable
     * cave floor, it sits inside the arrival chamber (the one place that should
     * stay a plain room), or it is closer than {@code minSpacing} to a vein
     * already placed. Rejections are cheap and the attempt budget is generous,
     * but a config asking for more veins than the world has room for will
     * simply place fewer rather than loop forever.
     */
    public static List<Placement> scatter(CultistCarve carve, OreTables tables, long worldSeed) {
        List<Placement> placed = new ArrayList<>();
        // Derived from the world seed so veins move when the world does, but
        // by a different constant than the carve's, so the two are independent.
        Random rng = new Random(worldSeed ^ 0x07E_5EEDL);
        int spacingSquared = tables.minSpacing() * tables.minSpacing();
        int extent = carve.halfExtent();

        for (OreType type : tables.types()) {
            int wanted = type.veinCount();
            int tries = 0;
            int placedOfType = 0;
            while (placedOfType < wanted && tries < tables.maxPlacementTries()) {
                tries++;
                int x = rng.nextInt(extent * 2 + 1) - extent;
                int z = rng.nextInt(extent * 2 + 1) - extent;

                Integer surface = floorSurface(carve, x, z);
                if (surface == null) {
                    continue;
                }
                if (carve.inChamber(x, surface + 1, z)) {
                    continue;  // the arrival room stays a plain room
                }
                Placement candidate = new Placement(type.id(), x, surface, z,
                        rng.nextLong());
                if (tooClose(placed, candidate, spacingSquared)) {
                    continue;
                }
                placed.add(candidate);
                placedOfType++;
            }
        }
        return List.copyOf(placed);
    }

    /**
     * The topmost solid block in this column with open cave directly above it —
     * the floor a player walks on. Null when the column has no floor at all.
     */
    public static Integer floorSurface(CultistCarve carve, int x, int z) {
        if (!carve.inBounds(x, z)) {
            return null;
        }
        for (int y = carve.floorY() + 1; y < carve.roofY() - 2; y++) {
            if (!carve.isOpen(x, y, z) && carve.isOpen(x, y + 1, z)
                    && carve.isOpen(x, y + 2, z)) {
                return y;   // head room above it too, so a player can stand there
            }
        }
        return null;
    }

    private static boolean tooClose(List<Placement> placed, Placement candidate, int spacingSquared) {
        for (Placement other : placed) {
            if (candidate.distanceSquared2D(other) < spacingSquared) {
                return true;
            }
        }
        return false;
    }
}
