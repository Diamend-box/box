package com.diamend.darksea.island.shape;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Can a player who arrives by boat actually walk to every chest?
 *
 * <p>The shape suite already asked whether each chest has a cell beside it a
 * player could stand in. That is adjacency, not reachability, and the
 * difference is the whole bug: it passed on every seed while the watchtower's
 * first-floor chest was sealed behind a "staircase" of single stones three
 * blocks apart, and the spire's summit sat above ledges that rose two blocks at
 * a time. Both had a perfectly good standing cell next to them and no way to
 * get to it. Playing a tier-5 castle turned up several chests that could be
 * seen and not opened, which is what sent this test looking.
 *
 * <p>So this one floods the island from the open sea with a player's actual
 * movement rules — walk, step up one, fall any distance, swim — and asks
 * whether the flood ever gets within arm's reach of each chest. It is
 * deliberately generous about what a player can do (no fall damage, no drowning,
 * diagonals ignored in the player's favour by being left out) so that a failure
 * here means a chest is genuinely walled in, never that the model was fussy.
 */
class ChestReachabilityTest {

    /** Shape-relative y of the sea surface: sea-level 62 over a paste at 58. */
    private static final int WATERLINE = 4;

    /** Seeds per shape and tier. Twelve was enough to catch every real failure. */
    private static final int SEEDS = 12;

    private static final Set<String> NON_SOLID = Set.of(
            "AIR", "WATER", "LAVA", "COBWEB", "TORCH", "WALL_TORCH", "SOUL_TORCH",
            "LANTERN", "SOUL_LANTERN", "CHAIN", "SEA_PICKLE", "SEAGRASS", "LILY_PAD",
            "DEAD_BUSH", "BROWN_MUSHROOM", "RED_MUSHROOM", "HANGING_ROOTS",
            "MOSS_CARPET", "WITHER_ROSE", "SKELETON_SKULL", "WITHER_SKELETON_SKULL",
            "CANDLE", "BLACK_CANDLE", "LEVER", "SCULK_SENSOR", "SCULK_SHRIEKER",
            "SOUL_FIRE", "FIRE", "RAIL", "LADDER", "VINE", "GLOW_LICHEN",
            "SHORT_GRASS", "TALL_GRASS", "KELP", "KELP_PLANT", "SOUL_CAMPFIRE",
            "CAMPFIRE", "CAULDRON", "FLOWER_POT", "END_ROD", "AMETHYST_CLUSTER");

    @Test
    @DisplayName("every chest of every shape, tier and seed can be walked to from the sea")
    void everyChestIsReachableOnFoot() {
        List<String> sealed = new ArrayList<>();
        int chests = 0;
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                for (int i = 0; i < SEEDS; i++) {
                    long seed = DemoShapes.seedFor(i * 977 - 18008, i * 613 + 13752);
                    ShapeBuild build = shape.build(tier, seed);
                    Island island = new Island(build);
                    island.flood();
                    chests += build.chests().size();
                    for (Rel chest : build.chests()) {
                        if (!island.canOpen(chest)) {
                            sealed.add(shape.id() + " t" + tier + " seed=" + seed
                                    + " chest=" + chest.x() + "," + chest.y() + "," + chest.z());
                        }
                    }
                }
            }
        }
        assertTrue(chests > 1000, "the sweep should cover a four-figure number of chests, saw " + chests);
        assertTrue(sealed.isEmpty(),
                sealed.size() + " of " + chests + " chests cannot be walked to:\n"
                        + String.join("\n", sealed));
    }

    /** One built island as a block grid, plus the flood over it. */
    private static final class Island {

        private final int x0, y0, z0, sx, sy, sz;
        private final boolean[] solid;
        private final boolean[] water;
        private final boolean[] reached;

        Island(ShapeBuild build) {
            int margin = 6;
            x0 = build.min().x() - margin;
            z0 = build.min().z() - margin;
            y0 = Math.min(build.min().y(), -8) - 2;
            int x1 = build.max().x() + margin;
            int z1 = build.max().z() + margin;
            int y1 = build.max().y() + 4;
            sx = x1 - x0 + 1;
            sy = y1 - y0 + 1;
            sz = z1 - z0 + 1;
            solid = new boolean[sx * sy * sz];
            water = new boolean[sx * sy * sz];
            reached = new boolean[sx * sy * sz];

            // The placer writes only the cells a shape declares, so everything
            // it leaves alone below the surface is still open ocean.
            for (int y = y0; y <= Math.min(y1, WATERLINE); y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        water[idx(x, y, z)] = true;
                    }
                }
            }
            for (Map.Entry<Rel, String> entry : build.blocks().entrySet()) {
                Rel at = entry.getKey();
                if (!inside(at.x(), at.y(), at.z())) {
                    continue;
                }
                int i = idx(at.x(), at.y(), at.z());
                String material = entry.getValue();
                water[i] = "WATER".equals(material);
                solid[i] = isSolid(material);
            }
        }

        private static boolean isSolid(String material) {
            if (material.endsWith("_SLAB") || material.endsWith("_STAIRS")) {
                return true;   // partial blocks, but you can stand on them
            }
            return !NON_SOLID.contains(material)
                    && !material.endsWith("_FENCE") && !material.endsWith("_WALL")
                    && !material.endsWith("_DOOR") && !material.endsWith("_SIGN")
                    && !material.endsWith("_BUTTON") && !material.endsWith("_PRESSURE_PLATE")
                    && !material.endsWith("_CARPET") && !material.contains("CHEST");
        }

        private boolean inside(int x, int y, int z) {
            return x >= x0 && y >= y0 && z >= z0
                    && x - x0 < sx && y - y0 < sy && z - z0 < sz;
        }

        private int idx(int x, int y, int z) {
            return ((x - x0) * sy + (y - y0)) * sz + (z - z0);
        }

        /** Two clear cells for the body. */
        private boolean body(int x, int y, int z) {
            return inside(x, y, z) && inside(x, y + 1, z)
                    && !solid[idx(x, y, z)] && !solid[idx(x, y + 1, z)];
        }

        private boolean supported(int x, int y, int z) {
            return inside(x, y - 1, z)
                    && (solid[idx(x, y - 1, z)] || water[idx(x, y, z)]);
        }

        /** A cell a player can occupy and hold position in. */
        private boolean stand(int x, int y, int z) {
            return body(x, y, z) && supported(x, y, z);
        }

        void flood() {
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            // You arrive by sea: start from the water all round the island.
            for (int x = x0; x < x0 + sx; x++) {
                for (int z = z0; z < z0 + sz; z++) {
                    if (x != x0 && z != z0 && x != x0 + sx - 1 && z != z0 + sz - 1) {
                        continue;
                    }
                    for (int y = y0; y < y0 + sy; y++) {
                        push(queue, x, y, z);
                    }
                }
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                int x = at[0], y = at[1], z = at[2];
                if (water[idx(x, y, z)]) {
                    push(queue, x, y + 1, z);   // swimming up
                }
                for (int[] dir : dirs) {
                    int nx = x + dir[0], nz = z + dir[1];
                    if (inside(x, y + 2, z) && !solid[idx(x, y + 2, z)]) {
                        push(queue, nx, y + 1, nz);   // step up one
                    }
                    push(queue, nx, y, nz);
                    for (int ny = y - 1; ny >= y0; ny--) {   // walk off and fall
                        if (!body(nx, ny, nz)) {
                            break;
                        }
                        push(queue, nx, ny, nz);
                        if (supported(nx, ny, nz)) {
                            break;
                        }
                    }
                }
            }
        }

        private void push(ArrayDeque<int[]> queue, int x, int y, int z) {
            if (!stand(x, y, z) || reached[idx(x, y, z)]) {
                return;
            }
            reached[idx(x, y, z)] = true;
            queue.add(new int[]{x, y, z});
        }

        /** Whether the flood ever gets within arm's reach of this chest. */
        boolean canOpen(Rel chest) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = chest.x() + dx, y = chest.y() + dy, z = chest.z() + dz;
                        if (inside(x, y, z) && reached[idx(x, y, z)]) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
