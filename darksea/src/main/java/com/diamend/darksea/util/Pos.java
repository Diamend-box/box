package com.diamend.darksea.util;

import org.bukkit.Location;
import org.bukkit.World;

/** An immutable block position, serializable as "x,y,z" for islands.yml. */
public record Pos(int x, int y, int z) {

    public String serialize() {
        return x + "," + y + "," + z;
    }

    public static Pos parse(String text) {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not an x,y,z position: " + text);
        }
        return new Pos(Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
    }

    public static Pos of(Location location) {
        return new Pos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Center of this block column at its own Y (for spawns/teleports). */
    public Location toLocation(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    public double distanceSquared2D(double otherX, double otherZ) {
        double dx = x - otherX;
        double dz = z - otherZ;
        return dx * dx + dz * dz;
    }
}
