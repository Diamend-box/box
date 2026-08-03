package com.diamend.darksea.world.cultist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The bearing arithmetic behind the caves' vein readout.
 *
 * <p>Worth its own file because the conversion is the kind that looks right and
 * is reversed: Minecraft yaw runs clockwise from +Z, which is neither the
 * handedness nor the zero of {@code atan2}. An arrow that points left when it
 * means right is worse than no arrow at all — it would send a player away from
 * the vein with confidence.
 */
class VeinSenseTest {

    @Test
    void aVeinStraightAheadPointsForwardFromEveryFacing() {
        // yaw 0 faces +Z, 90 faces -X, 180 faces -Z, 270 faces +X.
        assertEquals("↑", VeinSense.arrow(0, 0, 10));
        assertEquals("↑", VeinSense.arrow(90, -10, 0));
        assertEquals("↑", VeinSense.arrow(180, 0, -10));
        assertEquals("↑", VeinSense.arrow(270, 10, 0));
    }

    @Test
    void theFourQuartersAreNotMirrored() {
        // Facing +Z (south), west (-X) is on the right hand and east on the left.
        assertEquals("→", VeinSense.arrow(0, -10, 0));
        assertEquals("←", VeinSense.arrow(0, 10, 0));
        assertEquals("↓", VeinSense.arrow(0, 0, -10));
    }

    @Test
    void theDiagonalsLandOnTheDiagonalArrows() {
        assertEquals("↗", VeinSense.arrow(0, -10, 10));
        assertEquals("↘", VeinSense.arrow(0, -10, -10));
        assertEquals("↙", VeinSense.arrow(0, 10, -10));
        assertEquals("↖", VeinSense.arrow(0, 10, 10));
    }

    @Test
    void aYawOutsideZeroToThreeSixtyStillResolves() {
        // Bukkit hands back yaws well outside one turn — players who keep
        // spinning accumulate them, and a raw array index would throw.
        assertEquals("↑", VeinSense.arrow(720, 0, 10));
        assertEquals("↑", VeinSense.arrow(-360, 0, 10));
        assertEquals("→", VeinSense.arrow(-720, -10, 0));
    }

    @Test
    void noBearingAnywhereOnTheCompassFallsOffTheArrowTable() {
        // Rounding a bearing to eighths has an obvious off-by-one at the wrap
        // point: 337.5 degrees and up round to 8, which is not an index.
        for (int yaw = -400; yaw <= 400; yaw += 7) {
            for (int degrees = 0; degrees < 360; degrees++) {
                double radians = Math.toRadians(degrees);
                assertNotNull(VeinSense.arrow(yaw, Math.cos(radians) * 30.0,
                        Math.sin(radians) * 30.0));
            }
        }
    }

    @Test
    void aVeinUnderfootDoesNotThrow() {
        // Standing on top of a geode: no horizontal offset at all, so the
        // bearing is whatever atan2(0, 0) says. It must still be an arrow.
        assertNotNull(VeinSense.arrow(37.5, 0, 0));
    }
}
