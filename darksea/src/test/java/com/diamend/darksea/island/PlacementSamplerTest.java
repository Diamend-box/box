package com.diamend.darksea.island;

import com.diamend.darksea.island.PlacementSampler.Point;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Annulus bounds, spacing guarantees and saturation behavior — pure math. */
class PlacementSamplerTest {

    @Test
    void pointsStayInsideTheRingWithMargins() {
        List<Point> points = PlacementSampler.sample(new Random(42), 0, 0,
                500, 1500, 200, 20, 50, List.of(), 500);
        assertEquals(20, points.size());
        for (Point p : points) {
            double r = Math.sqrt(p.x() * p.x() + p.z() * p.z());
            assertTrue(r >= 700 - 1e-6, "point at r=" + r + " violates inner margin");
            assertTrue(r <= 1300 + 1e-6, "point at r=" + r + " violates outer margin");
        }
    }

    @Test
    void pairwiseSpacingIsRespected() {
        double gap = 150;
        List<Point> points = PlacementSampler.sample(new Random(7), 0, 0,
                500, 3000, 100, 25, gap, List.of(), 500);
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double dist = Math.sqrt(points.get(i).distanceSquared(points.get(j)));
                assertTrue(dist >= gap, "points " + i + " and " + j + " only " + dist + " apart");
            }
        }
    }

    @Test
    void existingIslandsAreKeptClearOf() {
        Point existing = new Point(1000, 0);
        List<Point> points = PlacementSampler.sample(new Random(3), 0, 0,
                500, 1500, 0, 30, 300, List.of(existing), 500);
        for (Point p : points) {
            assertTrue(Math.sqrt(p.distanceSquared(existing)) >= 300);
        }
    }

    @Test
    void offCenterRingsSampleAroundTheConfiguredCenter() {
        List<Point> points = PlacementSampler.sample(new Random(1), 10_000, -2_000,
                100, 600, 0, 10, 10, List.of(), 500);
        for (Point p : points) {
            double dx = p.x() - 10_000;
            double dz = p.z() + 2_000;
            double r = Math.sqrt(dx * dx + dz * dz);
            assertTrue(r >= 100 - 1e-6 && r <= 600 + 1e-6);
        }
    }

    @Test
    void impossibleRingReturnsEmptyInsteadOfLooping() {
        // Margins collapse the annulus: outer 501 − 200 < inner 500 + 200.
        List<Point> points = PlacementSampler.sample(new Random(5), 0, 0,
                500, 501, 200, 10, 50, List.of(), 500);
        assertTrue(points.isEmpty());
    }

    @Test
    void saturatedRingReturnsAShortfallInsteadOfLooping() {
        // A gap far wider than the ring: only one point can ever fit.
        List<Point> points = PlacementSampler.sample(new Random(9), 0, 0,
                700, 800, 0, 10, 50_000, List.of(), 200);
        assertEquals(1, points.size());
    }
}
