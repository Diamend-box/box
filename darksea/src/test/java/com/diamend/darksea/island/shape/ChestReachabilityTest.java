package com.diamend.darksea.island.shape;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
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

    /** World y a shape's own y=0 lands on, for reporting a failure you can fly to. */
    private static final int PASTE_Y = 58;

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

    /**
     * The two islands the sixth playtest actually walked, at the seeds their
     * world positions give them.
     *
     * <p>The sweep above uses seeds of its own and passes, which is why two
     * rounds of "unreachable chest" reports went unreproduced. An island's
     * seed comes from where it sits in the sea, so the only way to test the
     * castle someone stood in is to ask for that castle. These two came off
     * {@code /ds islands}: t5-4 mariphage-nest at 17824, 12303 and t5-5
     * ruined-castle at 21755, -7555.
     */
    @Test
    @DisplayName("the islands from the sixth playtest have no sealed chests")
    void theIslandsWyattWalkedAreNotSealed() {
        record Report(String shape, int tier, int x, int z) {
        }
        List<Report> reported = List.of(
                new Report("mariphage-nest", 5, 17824, 12303),
                new Report("ruined-castle", 5, 21755, -7555));
        List<String> sealed = new ArrayList<>();
        for (Report report : reported) {
            DemoShape shape = DemoShapes.byId(report.shape());
            assertTrue(shape != null, "no shape called " + report.shape());
            long seed = DemoShapes.seedFor(report.x(), report.z());
            ShapeBuild build = shape.build(report.tier(), seed);
            Island island = new Island(build);
            island.flood();
            for (Rel chest : build.chests()) {
                if (!island.canOpen(chest)) {
                    // World coordinates, so a failure here can be flown to.
                    sealed.add(report.shape() + " @ " + report.x() + "," + report.z()
                            + " chest at " + (report.x() + chest.x()) + ","
                            + (PASTE_Y + chest.y()) + "," + (report.z() + chest.z()));
                }
            }
        }
        assertTrue(sealed.isEmpty(), sealed.size() + " chests on the reported islands "
                + "cannot be walked to:\n" + String.join("\n", sealed));
    }

    /** One built island as a block grid, plus the flood over it. */
    private static final class Island {

        private final int x0, y0, z0, sx, sy, sz;
        private final boolean[] solid;
        private final boolean[] water;
        private final boolean[] reached;
        /** Of those, the ones you can get back to open water from. */
        private final boolean[] canReturn;
        /** Reversed movement steps, for that second question. */
        private final Map<Integer, List<Integer>> back = new HashMap<>();
        /** Where the flood started: standing cells out in the open sea. */
        private final List<Integer> shore = new ArrayList<>();

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
            canReturn = new boolean[sx * sy * sz];

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
                        if (stand(x, y, z) && !reached[idx(x, y, z)]) {
                            reached[idx(x, y, z)] = true;
                            shore.add(idx(x, y, z));
                            queue.add(new int[]{x, y, z});
                        }
                    }
                }
            }
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                int from = idx(at[0], at[1], at[2]);
                for (int to : steps(at[0], at[1], at[2])) {
                    // Every step is recorded backwards too, because getting
                    // back out is a different graph: you can drop off a ledge
                    // you cannot climb.
                    back.computeIfAbsent(to, key -> new ArrayList<>()).add(from);
                    if (!reached[to]) {
                        reached[to] = true;
                        queue.add(new int[]{unX(to), unY(to), unZ(to)});
                    }
                }
            }
            // Second pass, over those steps reversed: which cells can get back
            // to open water. A chest at the bottom of a one-block shaft is
            // "reachable" only in the sense that you can fall in and die
            // there, and two playtests running have reported that kind of
            // chest as unreachable. A route you cannot come back from is not a
            // route.
            ArrayDeque<Integer> out = new ArrayDeque<>(shore);
            for (int seed : shore) {
                canReturn[seed] = true;
            }
            while (!out.isEmpty()) {
                int at = out.poll();
                for (int from : back.getOrDefault(at, List.of())) {
                    if (!canReturn[from]) {
                        canReturn[from] = true;
                        out.add(from);
                    }
                }
            }
        }

        /** Every cell a player standing here can move to in one step. */
        private List<Integer> steps(int x, int y, int z) {
            List<Integer> out = new ArrayList<>(8);
            if (water[idx(x, y, z)] && stand(x, y + 1, z)) {
                out.add(idx(x, y + 1, z));   // swimming up
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dir : dirs) {
                int nx = x + dir[0], nz = z + dir[1];
                if (inside(x, y + 2, z) && !solid[idx(x, y + 2, z)] && stand(nx, y + 1, nz)) {
                    out.add(idx(nx, y + 1, nz));   // step up one
                }
                if (stand(nx, y, nz)) {
                    out.add(idx(nx, y, nz));
                }
                for (int ny = y - 1; ny >= y0; ny--) {   // walk off and fall
                    if (!body(nx, ny, nz)) {
                        break;
                    }
                    if (stand(nx, ny, nz)) {
                        out.add(idx(nx, ny, nz));
                    }
                    if (supported(nx, ny, nz)) {
                        break;
                    }
                }
            }
            return out;
        }

        private int unX(int i) {
            return i / (sy * sz) + x0;
        }

        private int unY(int i) {
            return i / sz % sy + y0;
        }

        private int unZ(int i) {
            return i % sz + z0;
        }

        /**
         * Whether a player who can still get home ever stands somewhere they
         * could open this chest — close enough to click it, with the chest
         * actually visible from there.
         *
         * <p>This used to accept any of the twenty-six cells around the chest,
         * which counted a corridor on the far side of a wall as being in
         * reach. Two rounds of "I can see it and I can't open it" went
         * unreproduced partly because of that one line: the model was
         * answering a question about proximity while the player was asking one
         * about access.
         */
        boolean canOpen(Rel chest) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 20) {
                            continue;   // out of arm's reach (vanilla is 4.5)
                        }
                        int x = chest.x() + dx, y = chest.y() + dy, z = chest.z() + dz;
                        if (!inside(x, y, z) || !reached[idx(x, y, z)]
                                || !canReturn[idx(x, y, z)]) {
                            continue;
                        }
                        if (visible(x, y + 1, z, chest)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** A straight line from the player's eye to the chest, through no rock. */
        private boolean visible(int ex, int ey, int ez, Rel chest) {
            int steps = Math.max(Math.abs(chest.x() - ex),
                    Math.max(Math.abs(chest.y() - ey), Math.abs(chest.z() - ez))) * 4;
            if (steps == 0) {
                return true;
            }
            for (int i = 1; i < steps; i++) {
                int x = ex + Math.round((chest.x() - ex) * (float) i / steps);
                int y = ey + Math.round((chest.y() - ey) * (float) i / steps);
                int z = ez + Math.round((chest.z() - ez) * (float) i / steps);
                if (x == chest.x() && y == chest.y() && z == chest.z()) {
                    return true;
                }
                if (inside(x, y, z) && solid[idx(x, y, z)]) {
                    return false;
                }
            }
            return true;
        }
    }
}
