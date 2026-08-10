package com.diamend.robobear.mine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box maths. Pure — no server needed.
 *
 * <p>This is the check that runs on every block break, so it being right and
 * being cheap both matter.
 */
class MineRegionTest {

    @Test
    @DisplayName("corners are normalised whichever way round they're given")
    void normalisesCorners() {
        MineRegion forwards = MineRegion.between("a", "A", "world", 0, 0, 0, 10, 10, 10);
        MineRegion backwards = MineRegion.between("a", "A", "world", 10, 10, 10, 0, 0, 0);

        assertEquals(forwards, backwards,
                "the same box described from opposite corners should compare equal");
        assertEquals(0, backwards.minX());
        assertEquals(10, backwards.maxX());
    }

    @Test
    @DisplayName("bounds are inclusive on every face")
    void boundsAreInclusive() {
        MineRegion region = MineRegion.between("a", "A", "world", 0, 0, 0, 4, 4, 4);

        assertTrue(region.contains("world", 0, 0, 0), "the minimum corner is inside");
        assertTrue(region.contains("world", 4, 4, 4), "the maximum corner is inside");
        assertTrue(region.contains("world", 2, 2, 2));

        assertFalse(region.contains("world", -1, 0, 0));
        assertFalse(region.contains("world", 5, 4, 4));
        assertFalse(region.contains("world", 0, 5, 0));
    }

    @Test
    @DisplayName("a matching position in another world is not inside")
    void worldMustMatch() {
        MineRegion region = MineRegion.between("a", "A", "world", 0, 0, 0, 4, 4, 4);

        assertTrue(region.contains("world", 2, 2, 2));
        assertFalse(region.contains("world_nether", 2, 2, 2),
                "same coordinates in a different world must not count");
    }

    @Test
    @DisplayName("negative coordinates behave")
    void handlesNegativeCoordinates() {
        MineRegion region = MineRegion.between("a", "A", "world", -20, -64, -20, -10, -50, -10);

        assertTrue(region.contains("world", -15, -60, -15));
        assertFalse(region.contains("world", -5, -60, -15));
        assertTrue(region.contains("world", -20, -64, -20));
    }

    @Test
    @DisplayName("volume counts blocks inclusively")
    void volumeIsInclusive() {
        assertEquals(1L, MineRegion.between("a", "A", "w", 0, 0, 0, 0, 0, 0).volume());
        assertEquals(1000L, MineRegion.between("a", "A", "w", 0, 0, 0, 9, 9, 9).volume());
    }

    @Test
    @DisplayName("the label falls back to the id when no display name is set")
    void labelFallsBack() {
        assertEquals("quarry", new MineRegion("quarry", "", "w", 0, 0, 0, 1, 1, 1).label());
        assertEquals("quarry", new MineRegion("quarry", null, "w", 0, 0, 0, 1, 1, 1).label());
        assertEquals("The Quarry",
                new MineRegion("quarry", "The Quarry", "w", 0, 0, 0, 1, 1, 1).label());
    }
}
