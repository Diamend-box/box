package com.diamend.darksea.naval;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The naval balance promises, held by plain arithmetic. */
class NavalMathTest {

    // ------------------------------------------------------------------
    // Closing speed: rams are about convergence, not raw speed
    // ------------------------------------------------------------------

    @Test
    void headOnChargeSumsBothSpeeds() {
        double closing = NavalMath.closingSpeed(
                new Vector(0, 62, 0), new Vector(0.4, 0, 0),
                new Vector(5, 62, 0), new Vector(-0.4, 0, 0));
        assertEquals(0.8, closing, 1e-9);
    }

    @Test
    void sailingInFormationNeverReadsAsARam() {
        double closing = NavalMath.closingSpeed(
                new Vector(0, 62, 0), new Vector(0.4, 0, 0),
                new Vector(0, 62, 3), new Vector(0.4, 0, 0));
        assertEquals(0.0, closing, 1e-9);
    }

    @Test
    void fleeingReadsNegative() {
        double closing = NavalMath.closingSpeed(
                new Vector(0, 62, 0), new Vector(-0.4, 0, 0),
                new Vector(5, 62, 0), new Vector(0.4, 0, 0));
        assertTrue(closing < 0, "separating boats must never register a ram");
    }

    @Test
    void sidewaysDriftDoesNotCount() {
        // Moving fast, but perpendicular to the line between the hulls.
        double closing = NavalMath.closingSpeed(
                new Vector(0, 62, 0), new Vector(0, 0, 0.9),
                new Vector(5, 62, 0), new Vector(0, 0, 0));
        assertEquals(0.0, closing, 1e-9);
    }

    @Test
    void attackerIsWhoeverContributesTheApproach() {
        Vector chaserPos = new Vector(0, 62, 0);
        Vector runnerPos = new Vector(4, 62, 0);
        Vector chaserVel = new Vector(0.6, 0, 0);   // charging
        Vector runnerVel = new Vector(0.2, 0, 0);   // fleeing, slower

        double chaser = NavalMath.approachSpeed(chaserPos, chaserVel, runnerPos);
        double runner = NavalMath.approachSpeed(runnerPos, runnerVel, chaserPos);
        assertTrue(chaser > runner, "the charger contributes the approach");
        assertEquals(0.0, runner, 1e-9, "fleeing away contributes nothing");
    }

    // ------------------------------------------------------------------
    // Damage: 75/25 split, own toughness only, capped base
    // ------------------------------------------------------------------

    @Test
    void baseDamageScalesWithClosingSpeedAndCaps() {
        assertEquals(4.0, NavalMath.ramBaseDamage(0.5, 8.0, 10.0), 1e-9);
        assertEquals(10.0, NavalMath.ramBaseDamage(5.0, 8.0, 10.0), 1e-9, "capped");
        assertEquals(0.0, NavalMath.ramBaseDamage(-1.0, 8.0, 10.0), 1e-9, "no negative force");
    }

    @Test
    void defenderEatsThreeQuartersAttackerOneQuarter() {
        double base = 8.0;
        assertEquals(6.0, NavalMath.hullShare(base, 0.75, 1.0), 1e-9);
        assertEquals(2.0, NavalMath.hullShare(base, 0.25, 1.0), 1e-9);
    }

    @Test
    void toughnessOnlySoftensYourOwnShare() {
        double base = 8.0;
        // Stormrunner (3.0) rams a Rowboat (1.0): rowboat still eats full 75%,
        // stormrunner's own 25% is cut to a third.
        assertEquals(6.0, NavalMath.hullShare(base, 0.75, 1.0), 1e-9);
        assertEquals(2.0 / 3.0, NavalMath.hullShare(base, 0.25, 3.0), 1e-9);
    }

    @Test
    void subOneToughnessNeverAmplifies() {
        assertEquals(6.0, NavalMath.hullShare(8.0, 0.75, 0.25), 1e-9,
                "toughness below 1 is treated as 1, never a damage boost");
    }

    // ------------------------------------------------------------------
    // Wounded hull + surge timing
    // ------------------------------------------------------------------

    @Test
    void woundedHullSlowsThenHeals() {
        assertEquals(0.7, NavalMath.woundedFactor(1_000, 5_000, 0.7), 1e-9);
        assertEquals(1.0, NavalMath.woundedFactor(5_000, 5_000, 0.7), 1e-9, "lapses exactly on time");
    }

    @Test
    void woundedFactorIsClampedAgainstConfigTypos() {
        assertEquals(0.05, NavalMath.woundedFactor(0, 1_000, -3.0), 1e-9);
        assertEquals(1.0, NavalMath.woundedFactor(0, 1_000, 7.0), 1e-9);
    }

    @Test
    void surgeCooldownGates() {
        assertFalse(NavalMath.surgeReady(1_000, 2_000));
        assertTrue(NavalMath.surgeReady(2_000, 2_000));
    }

    // ------------------------------------------------------------------
    // Knockback
    // ------------------------------------------------------------------

    @Test
    void knockbackShovesAwayFromTheAttacker() {
        Vector kb = NavalMath.ramKnockback(
                new Vector(0, 62, 0), new Vector(0.5, 0, 0), new Vector(4, 62, 0), 0.8);
        assertTrue(kb.getX() > 0.7, "shoved along +X, away from the rammer");
        assertEquals(0.0, kb.getZ(), 1e-9);
        assertTrue(kb.getY() > 0, "a touch of lift");
        assertEquals(0.8, Math.hypot(kb.getX(), kb.getZ()), 1e-9, "horizontal magnitude = strength");
    }

    @Test
    void zeroDistanceCollisionFallsBackToHeadingNeverNaN() {
        Vector pos = new Vector(3, 62, 3);
        Vector kb = NavalMath.ramKnockback(pos, new Vector(0, 0, 0.6), pos.clone(), 0.8);
        assertTrue(Double.isFinite(kb.getX()) && Double.isFinite(kb.getZ()));
        assertTrue(kb.getZ() > 0.7, "falls back to the attacker's heading");

        Vector still = NavalMath.ramKnockback(pos, new Vector(0, 0, 0), pos.clone(), 0.8);
        assertEquals(0.0, still.length(), 1e-9, "two motionless overlapping boats: no shove");
    }
}
