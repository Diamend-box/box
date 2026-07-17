package com.diamend.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.diamend.anticheat.util.MathUtil;

class MathUtilTest {

    @Test
    void meanOfEmptyIsZero() {
        assertEquals(0.0D, MathUtil.mean(new double[0]));
    }

    @Test
    void meanAndStdDev() {
        double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
        assertEquals(5.0D, MathUtil.mean(values), 1.0e-9);
        assertEquals(2.0D, MathUtil.standardDeviation(values), 1.0e-9);
    }

    @Test
    void coefficientOfVariationIsScaleIndependent() {
        double[] regular = {100, 101, 100, 99, 100, 101};
        double[] irregular = {40, 160, 55, 200, 30, 175};
        assertTrue(MathUtil.coefficientOfVariation(regular)
                < MathUtil.coefficientOfVariation(irregular));
    }

    @Test
    void coefficientOfVariationOfZeroMeanIsHuge() {
        assertEquals(Double.MAX_VALUE, MathUtil.coefficientOfVariation(new double[] {0, 0, 0}));
    }

    @Test
    void gcd() {
        assertEquals(6L, MathUtil.gcd(54, 24));
        assertEquals(7L, MathUtil.gcd(0, 7));
        assertEquals(0L, MathUtil.gcd(0, 0));
    }

    @Test
    void wrapDegrees() {
        assertEquals(0.0F, MathUtil.wrapDegrees(360.0F), 1.0e-4);
        assertEquals(-10.0F, MathUtil.wrapDegrees(350.0F), 1.0e-4);
        assertEquals(170.0F, MathUtil.wrapDegrees(170.0F), 1.0e-4);
        assertEquals(-179.0F, MathUtil.wrapDegrees(181.0F), 1.0e-4);
    }
}
