package com.diamend.darksea.boat;

import com.diamend.darksea.boat.BoatService.UpgradeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boat maths that PvP and sailing lean on, pulled into pure functions so
 * they can be pinned without a live server: the anti-runaway speed clamp and
 * the sequential upgrade rule.
 */
class BoatServiceTest {

    private static final double CAP_BASE = 0.45;

    @Test
    void noBoostLeavesVelocityAlone() {
        assertEquals(1.0, BoatService.boostFactor(1.0, 0.2, CAP_BASE));
        assertEquals(1.0, BoatService.boostFactor(0.5, 0.2, CAP_BASE));
    }

    @Test
    void aStillBoatIsNotScaled() {
        // Below the movement threshold: nothing to accelerate.
        assertEquals(1.0, BoatService.boostFactor(1.45, 0.0, CAP_BASE));
        assertEquals(1.0, BoatService.boostFactor(1.45, 1e-4, CAP_BASE));
    }

    @Test
    void aSlowBoatAcceleratesByAtMostTheMultiplier() {
        // Well below cap: scale by the full multiplier, never more.
        double factor = BoatService.boostFactor(1.45, 0.05, CAP_BASE);
        assertEquals(1.45, factor, 1e-9);
    }

    @Test
    void aBoatAtOrOverTheCapIsNotScaled() {
        double cap = CAP_BASE * 1.45;   // 0.6525
        assertEquals(1.0, BoatService.boostFactor(1.45, cap, CAP_BASE));
        assertEquals(1.0, BoatService.boostFactor(1.45, cap + 0.1, CAP_BASE));
    }

    @Test
    void boostingNeverPushesSpeedPastTheCap() {
        // The whole point of the clamp: the factor stays within [1, multiplier],
        // and whenever it actually boosts (factor > 1), the scaled speed lands
        // at or under the cap. A boat already moving faster than the cap is
        // simply left alone (factor == 1) — the boost declines to add speed,
        // it doesn't brake a naturally fast boat.
        for (double multiplier : new double[]{1.15, 1.30, 1.45, 2.0}) {
            double cap = CAP_BASE * multiplier;
            for (double speed = 0.01; speed < 1.0; speed += 0.01) {
                double factor = BoatService.boostFactor(multiplier, speed, CAP_BASE);
                assertTrue(factor >= 1.0, "factor dipped below 1 at speed " + speed);
                assertTrue(factor <= multiplier + 1e-9,
                        "factor " + factor + " exceeded the multiplier at speed " + speed);
                if (factor > 1.0) {
                    assertTrue(speed * factor <= cap + 1e-9,
                            "a boost pushed speed to " + (speed * factor) + ", past cap " + cap);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Wounded hull: the naval slow feeding into the same clamp
    // ------------------------------------------------------------------

    @Test
    void aHealthyHullBehavesExactlyLikeBoostFactor() {
        for (double speed : new double[]{0.0, 0.1, 0.3, 0.5, 0.9}) {
            assertEquals(BoatService.boostFactor(1.45, speed, CAP_BASE),
                    BoatService.speedFactor(1.45, speed, CAP_BASE, 1.0), 1e-12);
        }
    }

    @Test
    void aWoundedHullIsDraggedDownToItsReducedCap() {
        double wounded = 0.7;
        double cap = CAP_BASE * 1.45 * wounded;
        double speed = 0.6;  // cruising above the wounded cap
        double factor = BoatService.speedFactor(1.45, speed, CAP_BASE, wounded);
        assertTrue(factor < 1.0, "a wounded hull over its cap must slow down");
        assertEquals(cap, speed * factor, 1e-9, "dragged exactly to the wounded cap");
    }

    @Test
    void aWoundedRowboatIsSlowedTooNotJustUpgradedBoats() {
        // multiplier 1.0 boats never boost, but the wound still drags them.
        double factor = BoatService.speedFactor(1.0, 0.4, CAP_BASE, 0.5);
        assertTrue(factor < 1.0);
        assertEquals(CAP_BASE * 0.5, 0.4 * factor, 1e-9);
    }

    @Test
    void aWoundedHullBelowItsCapIsNotTouched() {
        // Limping along under the reduced ceiling: no extra brake.
        assertEquals(1.0, BoatService.speedFactor(1.0, 0.1, CAP_BASE, 0.7), 1e-12);
    }

    @Test
    void tokensUpgradeOnlyInSequence() {
        // The exact next level upgrades.
        assertEquals(UpgradeResult.UPGRADED, BoatService.evaluateUpgrade(0, 1, true));
        assertEquals(UpgradeResult.UPGRADED, BoatService.evaluateUpgrade(2, 3, true));
        // A token for a further level does nothing (no skipping ahead).
        assertEquals(UpgradeResult.WRONG_TOKEN, BoatService.evaluateUpgrade(0, 2, true));
        // A token at or below the current level does nothing.
        assertEquals(UpgradeResult.WRONG_TOKEN, BoatService.evaluateUpgrade(2, 2, true));
        // A non-token (level 0) does nothing.
        assertEquals(UpgradeResult.WRONG_TOKEN, BoatService.evaluateUpgrade(1, 0, true));
    }

    @Test
    void aMaxedBoatCannotUpgradeRegardlessOfToken() {
        assertEquals(UpgradeResult.AT_MAX, BoatService.evaluateUpgrade(3, 4, false));
        assertEquals(UpgradeResult.AT_MAX, BoatService.evaluateUpgrade(3, 0, false));
    }

    @Test
    @DisplayName("the throttle climbs to the cap, holds there, and never overshoots")
    void throttleIsMonotoneTowardTheCap() {
        double cap = 0.56;
        double speed = 0.0;
        double previous = -1.0;
        for (int tick = 0; tick < 200; tick++) {
            speed = BoatService.driveStep(speed, cap, true);
            // Non-decreasing, not strictly increasing: the ramp converges on
            // the cap and then a double simply stops changing. Holding forward
            // must never SLOW the boat, which is the invariant that matters —
            // demanding it speed up forever fails once it has arrived.
            assertTrue(speed >= previous, "holding forward must never slow the boat down");
            assertTrue(speed <= cap, "the throttle must not overshoot the cap, saw " + speed);
            previous = speed;
        }
        assertEquals(cap, speed, 1e-6, "a long hold should settle on the cap");
    }

    @Test
    @DisplayName("a steady cap gives a steady speed — no per-tick wobble at cruise")
    void cruiseIsSteady() {
        // The jitter was the boat being pushed one tick and left alone the
        // next. At the cap the step must be a fixed point, not an alternation.
        double cap = 0.56;
        double speed = cap;
        for (int tick = 0; tick < 50; tick++) {
            speed = BoatService.driveStep(speed, cap, true);
            assertEquals(cap, speed, 1e-9, "cruise wobbled on tick " + tick);
        }
    }

    @Test
    @DisplayName("letting go coasts down toward a stop instead of stopping dead")
    void releasingForwardCoasts() {
        double speed = 0.56;
        double afterOne = BoatService.driveStep(speed, 0.56, false);
        assertTrue(afterOne < speed, "coasting must shed speed");
        assertTrue(afterOne > speed * 0.5,
                "one tick must not halve the speed — that is the wall you feel");
        for (int tick = 0; tick < 500; tick++) {
            speed = BoatService.driveStep(speed, 0.56, false);
        }
        assertEquals(0.0, speed, 1e-3, "coasting should eventually stop the boat");
    }

    @Test
    @DisplayName("a surge decays back to cruise rather than being clamped away")
    void aSurgeBleedsOff() {
        double cap = 0.56;
        double burst = cap * 3.0;
        double speed = BoatService.driveStep(burst, cap, true);
        assertTrue(speed < burst, "the burst has to decay");
        assertTrue(speed > cap, "one tick must not erase the surge");
        // Still above cruise several ticks later: the shove is felt, not blinked.
        for (int tick = 0; tick < 5; tick++) {
            speed = BoatService.driveStep(speed, cap, true);
        }
        assertTrue(speed > cap, "the surge should still be carrying after six ticks");
        for (int tick = 0; tick < 300; tick++) {
            speed = BoatService.driveStep(speed, cap, true);
        }
        assertEquals(cap, speed, 1e-6, "and it should settle back to the hull's own cruise");
    }
}
