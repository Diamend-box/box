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
 * solid floor and clear headroom, footprints stay inside the placer's
 * chunk-preload budget, and builds are deterministic per (tier, seed) so a
 * soft reset reconstructs the identical island.
 */
class DemoShapeTest {

    /** Blocks a chest or mob can't stand on. */
    private static final Set<String> NOT_FOOTING = Set.of(
            "AIR", "LAVA", "DEAD_BUSH", "SEA_PICKLE", "SOUL_FIRE", "SCULK_SENSOR");

    @Test
    void everyShapeAtEveryTierIsWellFormed() {
        for (DemoShape shape : DemoShapes.ALL) {
            for (int tier = shape.minTier(); tier <= shape.maxTier(); tier++) {
                for (long seed : new long[]{1L, 424242L, -777L}) {
                    ShapeBuild build = shape.build(tier, seed);
                    String where = shape.id() + " t" + tier + " seed " + seed;

                    assertTrue(build.blocks().size() > 600, where + ": suspiciously small");
                    assertTrue(build.radius() <= 20, where + ": radius " + build.radius());
                    assertTrue(build.max().y() <= 26 && build.min().y() >= -8,
                            where + ": vertical bounds " + build.min().y() + ".." + build.max().y());

                    assertClearWithFooting(build, build.chest(), where + " chest");
                    assertFalse(build.mobSpawns().isEmpty(), where + ": no mob spawns");
                    for (Rel mob : build.mobSpawns()) {
                        assertClearWithFooting(build, mob, where + " mob " + mob);
                    }
                }
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
            assertEquals(a.chest(), b.chest(), shape.id() + ": same seed, different chest");
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

    private static void assertClearWithFooting(ShapeBuild build, Rel pos, String where) {
        String below = build.blocks().get(pos.below());
        assertNotNull(below, where + ": nothing under " + pos);
        assertFalse(NOT_FOOTING.contains(below), where + ": bad footing " + below);
        assertTrue(isClear(build, pos), where + ": cell blocked at " + pos);
        assertTrue(isClear(build, pos.above()), where + ": no headroom at " + pos);
    }

    private static boolean isClear(ShapeBuild build, Rel pos) {
        String material = build.blocks().get(pos);
        return material == null || "AIR".equals(material);
    }
}
