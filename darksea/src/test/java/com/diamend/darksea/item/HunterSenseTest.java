package com.diamend.darksea.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Soulwake Compass's off-server hunt math: nearest pick, bearing, range. */
class HunterSenseTest {

    @Test
    void bearingReadsTheEightPointsOnMinecraftAxes() {
        // +x East, -x West, +z South, -z North.
        assertEquals("N", HunterSense.bearing(0, -10));
        assertEquals("E", HunterSense.bearing(10, 0));
        assertEquals("S", HunterSense.bearing(0, 10));
        assertEquals("W", HunterSense.bearing(-10, 0));
        assertEquals("NE", HunterSense.bearing(10, -10));
        assertEquals("SE", HunterSense.bearing(10, 10));
        assertEquals("SW", HunterSense.bearing(-10, 10));
        assertEquals("NW", HunterSense.bearing(-10, -10));
    }

    @Test
    void nearestPicksTheClosestInRangeOrNoneAtAll() {
        double[] dx = {100, 10, 2000};
        double[] dy = {0, 0, 0};
        double[] dz = {0, 0, 0};
        assertEquals(1, HunterSense.nearest(dx, dy, dz, 3000), "the 10-block quarry is nearest");
        assertEquals(1, HunterSense.nearest(dx, dy, dz, 50), "only the near one is in a 50 range");
        assertEquals(-1, HunterSense.nearest(dx, dy, dz, 5), "nothing sits within 5 blocks");
        assertEquals(-1, HunterSense.nearest(new double[0], new double[0], new double[0], 3000),
                "an empty sea has no quarry");
    }

    @Test
    void nearestCountsAllThreeAxes() {
        // A quarry straight up is farther than one just across the water.
        double[] dx = {0, 30};
        double[] dy = {40, 0};
        double[] dz = {0, 0};
        assertEquals(1, HunterSense.nearest(dx, dy, dz, 3000), "vertical distance still counts");
    }

    @Test
    void blocksAndElevationReadTheDelta() {
        assertEquals(5, HunterSense.blocks(3, 0, 4));
        assertEquals(13, HunterSense.blocks(3, 12, 4));
        assertEquals("above", HunterSense.elevation(20));
        assertEquals("below", HunterSense.elevation(-20));
        assertEquals("level", HunterSense.elevation(0));
        assertEquals("level", HunterSense.elevation(4), "a 4-block step is still 'level'");
        assertEquals("above", HunterSense.elevation(5), "a 5-block step reads 'above'");
    }
}
