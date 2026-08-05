package com.diamend.customachievements.achievement;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Parsed form of a {@link TriggerType#REACH_LOCATION} target string. Two modes:
 *
 * <ul>
 *   <li><b>Radius</b> — {@code world;x;y;z;radius}: inside a sphere.</li>
 *   <li><b>Threshold</b> — {@code [world;]AXIS OP VALUE} (e.g. {@code Y>=319}):
 *       a single coordinate above/below a value, ignoring the others. Great for
 *       "reach the build limit". The world prefix is optional (any world).</li>
 * </ul>
 */
public final class LocationTarget {

    private static final double DEFAULT_RADIUS = 5.0;

    private final String world;
    // Radius mode.
    private final double x;
    private final double y;
    private final double z;
    private final double radius;
    // Threshold mode (axis == 0 means radius mode).
    private final char axis;
    private final boolean above;
    private final double threshold;

    /** Radius-mode constructor (kept for the editor's "use my location"). */
    public LocationTarget(String world, double x, double y, double z, double radius) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.axis = 0;
        this.above = false;
        this.threshold = 0;
    }

    private LocationTarget(String world, char axis, boolean above, double threshold) {
        this.world = world;
        this.axis = axis;
        this.above = above;
        this.threshold = threshold;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.radius = 0;
    }

    public boolean isThreshold() {
        return axis != 0;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double radius() {
        return radius;
    }

    /** Parses a target string in either mode, or {@code null} when invalid. */
    public static LocationTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf('>') >= 0 || trimmed.indexOf('<') >= 0) {
            return parseThreshold(trimmed);
        }
        String[] parts = trimmed.contains(";") ? trimmed.split("\\s*;\\s*") : trimmed.split("[,\\s]+");
        if (parts.length < 4 || parts.length > 5 || parts[0].isBlank()) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            double radius = parts.length == 5 ? Double.parseDouble(parts[4]) : DEFAULT_RADIUS;
            if (radius <= 0 || !Double.isFinite(radius)) {
                return null;
            }
            return new LocationTarget(parts[0], x, y, z, radius);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocationTarget parseThreshold(String raw) {
        String world = "";
        String expr = raw;
        int semi = raw.indexOf(';');
        if (semi >= 0) {
            world = raw.substring(0, semi).trim();
            expr = raw.substring(semi + 1);
        }
        expr = expr.replace(" ", "").toUpperCase(Locale.ROOT);
        if (expr.length() < 3) {
            return null;
        }
        char axis = expr.charAt(0);
        if (axis != 'X' && axis != 'Y' && axis != 'Z') {
            return null;
        }
        char op = expr.charAt(1);
        boolean above;
        if (op == '>') {
            above = true;
        } else if (op == '<') {
            above = false;
        } else {
            return null;
        }
        int index = expr.charAt(2) == '=' ? 3 : 2;
        try {
            double value = Double.parseDouble(expr.substring(index));
            if (!Double.isFinite(value)) {
                return null;
            }
            return new LocationTarget(world, axis, above, value);
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /** True when the given location satisfies this target. */
    public boolean contains(Location location) {
        World locWorld = location.getWorld();
        if (locWorld == null) {
            return false;
        }
        if (isThreshold()) {
            if (!world.isBlank() && !worldMatches(locWorld)) {
                return false;
            }
            double value = axis == 'X' ? location.getX() : axis == 'Y' ? location.getY() : location.getZ();
            return above ? value >= threshold : value <= threshold;
        }
        if (!worldMatches(locWorld)) {
            return false;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private boolean worldMatches(World locWorld) {
        return world.equalsIgnoreCase(locWorld.getName())
                || world.equalsIgnoreCase(locWorld.getKey().toString());
    }

    /** Canonical storage form. */
    public String serialize() {
        if (isThreshold()) {
            return (world.isBlank() ? "" : world + ";") + axis + (above ? ">=" : "<=") + fmt(threshold);
        }
        return world + ";" + fmt(x) + ";" + fmt(y) + ";" + fmt(z) + ";" + fmt(radius);
    }

    /** Human-friendly form for GUI lore. */
    public String pretty() {
        if (isThreshold()) {
            return (world.isBlank() ? "" : world + " ") + axis + (above ? " ≥ " : " ≤ ") + fmt(threshold);
        }
        return world + " (" + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ") ±" + fmt(radius);
    }

    private static String fmt(double value) {
        if (value == Math.floor(value) && Double.isFinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
