package com.diamend.anticheat.util;

/**
 * Computes the distance from a player's eye position to a target entity's
 * axis-aligned bounding box (hitbox). Pure math so it can be unit-tested.
 *
 * <p>The relevant quantity for a reach check is the distance from the eye to
 * the <em>nearest point</em> of the target box, not to the target's centre —
 * a player legitimately hits the near face of a wide hitbox.
 */
public final class ReachCalculator {

    private ReachCalculator() {
    }

    /**
     * Distance from the point (ex, ey, ez) to the nearest point of the box
     * bounded by [minX,maxX] x [minY,maxY] x [minZ,maxZ]. Returns 0 when the
     * point is inside the box.
     */
    public static double distanceToBox(double ex, double ey, double ez,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = axisGap(ex, minX, maxX);
        double dy = axisGap(ey, minY, maxY);
        double dz = axisGap(ez, minZ, maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisGap(double p, double min, double max) {
        if (p < min) {
            return min - p;
        }
        if (p > max) {
            return p - max;
        }
        return 0.0D;
    }
}
