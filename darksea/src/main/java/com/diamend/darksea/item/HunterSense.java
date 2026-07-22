package com.diamend.darksea.item;

/**
 * The direction-finding math behind the Soulwake Compass — the Order's tool
 * for hunting the last living souls in the Deep. Pure functions of position
 * deltas, no Bukkit, so the whole hunt readout is unit-tested off-server.
 *
 * <p>Minecraft world axes: +x is East, -x West, +z South, -z North. All
 * deltas here are <em>target minus hunter</em>.
 */
public final class HunterSense {

    private HunterSense() {
    }

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * Index of the nearest candidate within {@code maxRange} blocks (3-D
     * straight line), or -1 when the sea is empty within reach. Ties resolve
     * to the earlier candidate.
     */
    public static int nearest(double[] dx, double[] dy, double[] dz, double maxRange) {
        double limitSq = maxRange * maxRange;
        int best = -1;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < dx.length; i++) {
            double sq = dx[i] * dx[i] + dy[i] * dy[i] + dz[i] * dz[i];
            if (sq <= limitSq && sq < bestSq) {
                bestSq = sq;
                best = i;
            }
        }
        return best;
    }

    /** The 8-point compass bearing from the hunter to a horizontal delta. */
    public static String bearing(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360.0;
        }
        return POINTS[(int) Math.round(angle / 45.0) % 8];
    }

    /** Rounded straight-line block distance to a delta. */
    public static long blocks(double dx, double dy, double dz) {
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** A coarse vertical hint: whether the quarry sits above, below, or level. */
    public static String elevation(double dy) {
        if (dy >= 5) {
            return "above";
        }
        if (dy <= -5) {
            return "below";
        }
        return "level";
    }
}
