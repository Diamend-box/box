package com.diamend.darksea.util;

/**
 * Which way, and how far. Used to point a player at the vault lever they
 * cannot find — a bare floor lever somewhere on a 70-block island is not
 * something anyone locates by sweeping the ground, and the second live test
 * said so plainly.
 *
 * Minecraft's axes: north is -Z, east is +X. Bukkit-free so the arithmetic
 * is unit-testable.
 */
public final class Bearing {

    private static final String[] POINTS = {
            "north", "north-east", "east", "south-east",
            "south", "south-west", "west", "north-west"
    };

    private Bearing() {
    }

    /**
     * The eight-point compass name for a delta, as the player would say it:
     * "north-west", not "315 degrees". A zero delta reads as "here".
     */
    public static String compass(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return "here";
        }
        // atan2 measured clockwise from north, so index 0 lands on north and
        // each step is 45 degrees. Adding half a step centres each sector.
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.floor(((degrees + 360.0) % 360.0) / 45.0 + 0.5);
        return POINTS[index % POINTS.length];
    }

    /** Horizontal distance, rounded to whole blocks. */
    public static int blocks(double dx, double dz) {
        return (int) Math.round(Math.hypot(dx, dz));
    }
}
