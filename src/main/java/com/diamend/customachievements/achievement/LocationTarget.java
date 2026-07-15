package com.diamend.customachievements.achievement;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Parsed form of a {@link TriggerType#REACH_LOCATION} target string.
 *
 * <p>Serialized as {@code world;x;y;z;radius}. When typed in chat, spaces or
 * commas are accepted as separators too. The world component matches either
 * the world's name or its namespaced key, so custom (datapack/plugin) worlds
 * work as well.
 */
public record LocationTarget(String world, double x, double y, double z, double radius) {

    private static final double DEFAULT_RADIUS = 5.0;

    /** Parses a target string, returning {@code null} when it isn't a valid location. */
    public static LocationTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String[] parts = trimmed.contains(";")
                ? trimmed.split("\\s*;\\s*")
                : trimmed.split("[,\\s]+");
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

    /** True when the given location is inside this target's radius (same world). */
    public boolean contains(Location location) {
        World locWorld = location.getWorld();
        if (locWorld == null) {
            return false;
        }
        if (!world.equalsIgnoreCase(locWorld.getName())
                && !world.equalsIgnoreCase(locWorld.getKey().toString())) {
            return false;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** The canonical storage form: {@code world;x;y;z;radius}. */
    public String serialize() {
        return world + ";" + fmt(x) + ";" + fmt(y) + ";" + fmt(z) + ";" + fmt(radius);
    }

    /** A human-friendly form for GUI lore. */
    public String pretty() {
        return world + " (" + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ") ±" + fmt(radius);
    }

    private static String fmt(double value) {
        if (value == Math.floor(value) && Double.isFinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
