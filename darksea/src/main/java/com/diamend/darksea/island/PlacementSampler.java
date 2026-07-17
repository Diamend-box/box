package com.diamend.darksea.island;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ring-constrained random placement: rejection sampling, uniform over the
 * annulus area, honoring a minimum gap to every existing and newly picked
 * point and a margin from the ring borders. Pure math — no Bukkit — so the
 * spacing guarantees are unit-testable.
 */
public final class PlacementSampler {

    public record Point(double x, double z) {
        public double distanceSquared(Point other) {
            double dx = x - other.x;
            double dz = z - other.z;
            return dx * dx + dz * dz;
        }
    }

    private PlacementSampler() {
    }

    /**
     * Picks up to {@code count} points in the annulus
     * {@code [innerRadius + borderMargin, outerRadius - borderMargin]} around
     * (centerX, centerZ). Returns fewer when the ring saturates (every
     * attempt for a point failed the gap check {@code maxAttemptsPerPoint}
     * times) — callers report the shortfall rather than looping forever.
     */
    public static List<Point> sample(Random rng, double centerX, double centerZ,
                                     double innerRadius, double outerRadius, double borderMargin,
                                     int count, double minGap, List<Point> existing,
                                     int maxAttemptsPerPoint) {
        List<Point> placed = new ArrayList<>();
        double rMin = Math.max(0, innerRadius + borderMargin);
        double rMax = outerRadius - borderMargin;
        if (rMax <= rMin) {
            return placed;
        }
        double rMinSq = rMin * rMin;
        double rMaxSq = rMax * rMax;
        double gapSq = minGap * minGap;

        for (int i = 0; i < count; i++) {
            Point picked = null;
            for (int attempt = 0; attempt < maxAttemptsPerPoint; attempt++) {
                // Uniform over the annulus AREA: r = sqrt(u·(R²−r²)+r²).
                double r = Math.sqrt(rng.nextDouble() * (rMaxSq - rMinSq) + rMinSq);
                double theta = rng.nextDouble() * Math.PI * 2;
                Point candidate = new Point(centerX + r * Math.cos(theta), centerZ + r * Math.sin(theta));
                if (clearOf(candidate, existing, gapSq) && clearOf(candidate, placed, gapSq)) {
                    picked = candidate;
                    break;
                }
            }
            if (picked == null) {
                break;
            }
            placed.add(picked);
        }
        return placed;
    }

    private static boolean clearOf(Point candidate, List<Point> points, double gapSq) {
        for (Point point : points) {
            if (candidate.distanceSquared(point) < gapSq) {
                return false;
            }
        }
        return true;
    }
}
