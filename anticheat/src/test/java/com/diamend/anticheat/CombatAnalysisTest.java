package com.diamend.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.diamend.anticheat.util.AimAnalyzer;
import com.diamend.anticheat.util.CpsAnalyzer;
import com.diamend.anticheat.util.ReachCalculator;

class CombatAnalysisTest {

    @Test
    void distanceZeroWhenInsideBox() {
        double d = ReachCalculator.distanceToBox(0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1);
        assertEquals(0.0D, d, 1.0e-9);
    }

    @Test
    void distanceToNearestFace() {
        // Point 2 blocks in front of a unit box occupying x in [0,1].
        double d = ReachCalculator.distanceToBox(3, 0.5, 0.5, 0, 0, 0, 1, 1, 1);
        assertEquals(2.0D, d, 1.0e-9);
    }

    @Test
    void cpsFromRegularClicks() {
        long[] clicks = new long[10];
        for (int i = 0; i < clicks.length; i++) {
            clicks[i] = i * 50L; // 50ms apart -> 20 cps
        }
        CpsAnalyzer.Result r = CpsAnalyzer.analyze(clicks);
        assertEquals(20.0D, r.cps(), 1.0e-6);
        assertTrue(r.coefficientOfVariation() < 0.01D, "constant interval => near-zero variation");
    }

    @Test
    void humanClicksHaveHigherVariation() {
        long[] clicks = {0, 90, 210, 260, 400, 455, 600, 640, 810, 900};
        CpsAnalyzer.Result r = CpsAnalyzer.analyze(clicks);
        assertTrue(r.coefficientOfVariation() > 0.2D, "irregular human clicking");
    }

    @Test
    void impossiblePitchDetected() {
        assertTrue(AimAnalyzer.isImpossiblePitch(95.0F));
        assertTrue(AimAnalyzer.isImpossiblePitch(-91.0F));
        assertFalse(AimAnalyzer.isImpossiblePitch(45.0F));
    }

    @Test
    void syntheticRotationsShareLargeGcd() {
        long gcd = AimAnalyzer.rotationGcd(2.0F, 4.0F);
        assertEquals(2_000_000L, gcd);
    }

    @Test
    void humanRotationsShareSmallGcd() {
        long gcd = AimAnalyzer.rotationGcd(1.234F, 2.717F);
        assertTrue(gcd < 20_000L, "expected small gcd but was " + gcd);
    }
}
