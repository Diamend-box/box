package com.diamend.darksea.island.shape;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guarantees for the built-in island shapes: every emitted
 * material is a real placeable block, chests and mob spawns always have a
 * solid floor and clear headroom, every island meets the 30x30 minimum
 * footprint and stays inside the placer's chunk-preload budget, higher
 * tiers are never smaller than lower ones, the chest is never in plain
 * sight, and builds are deterministic per (tier, seed) so a soft reset
 * reconstructs the identical island.
 */
class DemoShapeTest {

    /** Blocks a chest or mob can't stand on. */
    private static final Set<String> NOT_FOOTING = Set.of(
            "AIR", "LAVA", "WATER", "DEAD_BUSH", "SEA_PICKLE", "SOUL_FIRE",
            "SCULK_SENSOR", "SCULK_SHRIEKER", "COBWEB", "MOSS_CARPET", "LILY_PAD",
            "BROWN_MUSHROOM", "RED_MUSHROOM", "HANGING_ROOTS", "SEAGRASS",
            "BLACK_CANDLE", "CANDLE", "SKELETON_SKULL", "SOUL_CAMPFIRE",
            "WITHER_SKELETON_SKULL", "WITHER_ROSE", "CAULDRON", "CHAIN");

    private static final long[] SEEDS = {1L, 424242L, -777L};

    @Test
    void everyShapeAtEveryTierIsWellFormed() {
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                for (long seed : SEEDS) {
                    ShapeBuild build = shape.build(tier, seed);
                    String where = shape.id() + " t" + tier + " seed " + seed;

                    assertTrue(build.blocks().size() > 1500, where + ": suspiciously small ("
                            + build.blocks().size() + " blocks)");
                    assertTrue(build.radius() <= shape.radiusBudget(),
                            where + ": radius " + build.radius() + " over the shape's "
                                    + shape.radiusBudget() + "-block preload budget");
                    assertTrue(build.max().y() <= 40 && build.min().y() >= -10,
                            where + ": vertical bounds " + build.min().y() + ".." + build.max().y());
                    assertTrue(spanX(build) >= 30 && spanZ(build) >= 30, where
                            + ": footprint " + spanX(build) + "x" + spanZ(build) + " under 30x30");

                    assertEquals(shape.chestCount(tier), build.chests().size(),
                            where + ": wrong chest count");
                    for (int c = 0; c < build.chests().size(); c++) {
                        Rel chest = build.chests().get(c);
                        assertClearWithFooting(build, chest, where + " chest#" + c);
                        assertConcealed(build, chest, where + " chest#" + c);
                        for (int o = 0; o < c; o++) {
                            Rel other = build.chests().get(o);
                            double gap = Math.sqrt(Math.pow(chest.x() - other.x(), 2)
                                    + Math.pow(chest.y() - other.y(), 2)
                                    + Math.pow(chest.z() - other.z(), 2));
                            assertTrue(gap >= 6.0, where + ": chests #" + o + " and #" + c
                                    + " only " + gap + " apart");
                        }
                    }
                    assertFalse(build.mobSpawns().isEmpty(), where + ": no mob spawns");
                    for (Rel mob : build.mobSpawns()) {
                        assertClearWithFooting(build, mob, where + " mob " + mob);
                    }
                }
            }
        }
    }

    @Test
    void higherTiersAreNeverSmaller() {
        long[] seeds = {11L, 22L, 33L, 44L};
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier < shape.maxTier(); tier++) {
                int maxLow = 0, minHigh = Integer.MAX_VALUE;
                long sumLow = 0, sumHigh = 0;
                for (long seed : seeds) {
                    int low = area(shape.build(tier, seed));
                    int high = area(shape.build(tier + 1, seed));
                    maxLow = Math.max(maxLow, low);
                    minHigh = Math.min(minHigh, high);
                    sumLow += low;
                    sumHigh += high;
                }
                assertTrue(sumHigh > sumLow, shape.id() + ": t" + (tier + 1)
                        + " not bigger than t" + tier + " on average");
                assertTrue(minHigh * 10 >= maxLow * 9, shape.id() + ": a t" + (tier + 1)
                        + " (" + minHigh + ") can roll far smaller than a t" + tier
                        + " (" + maxLow + ")");
            }
        }
    }

    @Test
    void everyEmittedMaterialIsARealBlock() {
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                ShapeBuild build = shape.build(tier, 99L);
                for (Map.Entry<Rel, String> entry : build.blocks().entrySet()) {
                    Material material = Material.matchMaterial(entry.getValue());
                    assertNotNull(material, shape.id() + " t" + tier + " emits unknown material "
                            + entry.getValue() + " at " + entry.getKey());
                    assertTrue(material.isBlock(), shape.id() + " t" + tier + ": "
                            + entry.getValue() + " is not a placeable block");
                }
            }
        }
    }

    @Test
    void buildsAreDeterministicPerSeed() {
        for (DemoShape shape : DemoShapes.ALL) {
            int tier = shape.minTier();
            ShapeBuild a = shape.build(tier, 1234L);
            ShapeBuild b = shape.build(tier, 1234L);
            assertEquals(a.blocks(), b.blocks(), shape.id() + ": same seed, different blocks");
            assertEquals(a.chests(), b.chests(), shape.id() + ": same seed, different chests");
            assertEquals(a.mobSpawns(), b.mobSpawns(), shape.id() + ": same seed, different mobs");
        }
    }

    @Test
    void everyTierHasAPoolAndPickingWorks() {
        Random rng = new Random(5);
        for (int tier = 1; tier <= 4; tier++) {
            List<DemoShape> pool = DemoShapes.forTier(tier);
            assertFalse(pool.isEmpty(), "no shapes fit tier " + tier);
            for (int i = 0; i < 50; i++) {
                DemoShape picked = DemoShapes.pick(tier, rng);
                assertTrue(picked.fitsTier(tier));
                assertEquals(picked, DemoShapes.byId(picked.id()));
            }
        }
        assertEquals(DemoShapes.seedFor(100, -200), DemoShapes.seedFor(100, -200),
                "position seed must be stable");
    }

    @Test
    void standardShapesKeepTheClassicBudgetsOnlyTheCastleExceedsThem() {
        for (DemoShape shape : DemoShapes.ALL) {
            if (shape.id().equals("ruined-castle")) {
                continue;
            }
            assertEquals(30, shape.radiusBudget(), shape.id() + " changed its preload budget");
            assertEquals(10, shape.rarityWeight(), shape.id() + " changed its pick weight");
            assertEquals(0, shape.mobTierBoost(), shape.id() + " boosts its mob tier");
            assertEquals(1, shape.vaultChestCount(), shape.id() + " elects extra vaults");
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                assertEquals(tier >= 4 ? 3 : tier == 3 ? 2 : 1, shape.chestCount(tier),
                        shape.id() + " changed its chest count at tier " + tier);
            }
        }
    }

    @Test
    void theCastleDwarfsEveryOtherShapeAndPaysForItInEnemies() {
        DemoShape castle = DemoShapes.byId("ruined-castle");
        assertNotNull(castle, "the ruined castle is not registered");
        assertEquals(2, castle.minTier(), "castles start in ring 2");
        assertEquals(1, castle.mobTierBoost(), "a castle fights one ring deeper");
        assertTrue(castle.mobCapBonus() > 0, "a castle must hold a bigger garrison");
        assertEquals(2, castle.vaultChestCount(), "a castle hides two vaults");
        assertTrue(castle.wealthFloor() >= 1.0, "a castle never drowned poor");
        assertTrue(castle.rarityWeight() < 10, "the castle must stay rare");

        for (int tier = castle.minTier(); tier <= castle.maxTier(); tier++) {
            ShapeBuild build = castle.build(tier, 4242L);
            // "Bigger than all the rest": ~75x75ish against everyone's ~50 max.
            assertTrue(spanX(build) >= 60 && spanZ(build) >= 60,
                    "t" + tier + " castle only " + spanX(build) + "x" + spanZ(build));
            int area = area(build);
            for (DemoShape other : DemoShapes.ALL) {
                if (other == castle || !other.fitsTier(tier)) {
                    continue;
                }
                int otherArea = area(other.build(tier, 4242L));
                assertTrue(area > otherArea * 3 / 2, "t" + tier + " castle (" + area
                        + ") not clearly bigger than " + other.id() + " (" + otherArea + ")");
            }
            assertTrue(build.chests().size() >= 4,
                    "t" + tier + " castle must hide at least four chests");
            assertTrue(build.mobSpawns().size() >= 6,
                    "t" + tier + " castle garrison too thin");
        }
    }

    @Test
    void weightedPickKeepsTheCastleRareButReachable() {
        for (int tier = 2; tier <= 4; tier++) {
            Random rng = new Random(20260720L + tier);
            int castles = 0, picks = 20_000;
            for (int i = 0; i < picks; i++) {
                if (DemoShapes.pick(tier, rng).id().equals("ruined-castle")) {
                    castles++;
                }
            }
            double share = castles / (double) picks;
            assertTrue(share > 0.02 && share < 0.10, "tier " + tier
                    + " castle share " + share + " outside the rare-but-real band");
        }
        // Ring 1 never rolls a castle at all.
        for (DemoShape shape : DemoShapes.forTier(1)) {
            assertFalse(shape.id().equals("ruined-castle"), "a castle leaked into ring 1");
        }
    }

    @Test
    void islandsWadeIntoTheSeaInsteadOfDroppingOffACliff() {
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                ShapeBuild build = shape.build(tier, 424242L);
                int rays = 24, landed = 0, gentle = 0, submergedRim = 0;
                for (int i = 0; i < rays; i++) {
                    double angle = i * Math.PI * 2 / rays;
                    Rel edge = outermostColumn(build, angle, shape.radiusBudget());
                    if (edge == null) {
                        // Open sea the whole way: a crescent island (the
                        // beast's carcass curls like a banana) leaves a bay
                        // some rays cross without ever meeting land.
                        continue;
                    }
                    landed++;
                    // The shoreline: the outermost ground should sit at or
                    // below the waterline (an offshore stack may buck this
                    // on a few rays, but never around the whole compass).
                    if (edge.y() <= 1) {
                        gentle++;
                    }
                    if (edge.y() <= 0) {
                        submergedRim++;
                    }
                }
                assertTrue(landed >= rays / 2, shape.id() + " t" + tier
                        + ": only " + landed + "/" + rays + " rays found land at all"
                        + " — is the island missing?");
                // 2/3, not all: overhanging features (rib arches, offshore
                // stacks) legitimately poke above the apron on a few rays.
                assertTrue(gentle >= landed * 2 / 3, shape.id() + " t" + tier
                        + ": only " + gentle + "/" + landed + " landing directions reach"
                        + " the water gently — where are the beaches?");
                assertTrue(submergedRim >= landed / 2, shape.id() + " t" + tier
                        + ": the rim rarely dips underwater (" + submergedRim + "/" + landed
                        + ") — no shallows to wade through");
            }
        }
    }

    /** Marches a ray inward from the budget radius; the first solid column
     *  found, returned with its topmost solid y. */
    private static Rel outermostColumn(ShapeBuild build, double angle, int fromRadius) {
        for (double r = fromRadius; r >= 0; r -= 0.5) {
            int x = (int) Math.round(Math.cos(angle) * r);
            int z = (int) Math.round(Math.sin(angle) * r);
            for (int y = build.max().y(); y >= build.min().y(); y--) {
                if (isSolid(build, new Rel(x, y, z))) {
                    return new Rel(x, y, z);
                }
            }
        }
        return null;
    }

    private static int spanX(ShapeBuild build) {
        return build.max().x() - build.min().x() + 1;
    }

    private static int spanZ(ShapeBuild build) {
        return build.max().z() - build.min().z() + 1;
    }

    private static int area(ShapeBuild build) {
        return spanX(build) * spanZ(build);
    }

    private static void assertClearWithFooting(ShapeBuild build, Rel pos, String where) {
        String below = build.blocks().get(pos.below());
        assertNotNull(below, where + ": nothing under " + pos);
        assertFalse(NOT_FOOTING.contains(below), where + ": bad footing " + below);
        assertTrue(isClear(build, pos), where + ": cell blocked at " + pos);
        assertTrue(isClear(build, pos.above()), where + ": no headroom at " + pos);
    }

    /**
     * "You have to explore to find it": the chest is roofed (solid within 12
     * blocks straight up) and enclosed at its own height on at least three
     * of four sides — one side may be the way in.
     */
    private static void assertConcealed(ShapeBuild build, Rel chest, String where) {
        boolean roofed = false;
        for (int dy = 1; dy <= 12 && !roofed; dy++) {
            roofed = isSolid(build, new Rel(chest.x(), chest.y() + dy, chest.z()));
        }
        assertTrue(roofed, where + ": chest is open to the sky at " + chest);

        int enclosed = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int d = 1; d <= 24; d++) {
                if (isSolid(build, new Rel(chest.x() + dir[0] * d, chest.y(),
                        chest.z() + dir[1] * d))) {
                    enclosed++;
                    break;
                }
            }
        }
        assertTrue(enclosed >= 3, where + ": chest visible from " + (4 - enclosed)
                + " open sides at " + chest);
    }

    private static boolean isSolid(ShapeBuild build, Rel pos) {
        String material = build.blocks().get(pos);
        return material != null && !"AIR".equals(material);
    }

    private static boolean isClear(ShapeBuild build, Rel pos) {
        String material = build.blocks().get(pos);
        return material == null || "AIR".equals(material);
    }
}
