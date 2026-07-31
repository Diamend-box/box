package com.diamend.boxcore.travel;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

/**
 * One fast-travel destination.
 *
 * @param id          the key it is stored and referred to by
 * @param display     name shown in the menu
 * @param icon        menu icon
 * @param description extra lore lines, or empty
 * @param location    where travelling to it puts you
 * @param permission  required to use it, or blank for everyone
 * @param radius      how close a player must get for it to count as found
 */
public record Warp(String id,
                   String display,
                   Material icon,
                   List<String> description,
                   Location location,
                   String permission,
                   double radius) {

    public Warp {
        description = description == null ? List.of() : List.copyOf(description);
        permission = permission == null ? "" : permission.trim();
        radius = radius <= 0 ? 8.0 : radius;
        icon = icon == null || icon.isAir() ? Material.ENDER_PEARL : icon;
    }

    /** Whether this player is allowed to use it at all. */
    public boolean allows(org.bukkit.entity.Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /**
     * Whether a location is close enough to count as finding this warp.
     *
     * <p>Compares squared distances and checks the world first, because
     * {@link Location#distance} on locations in different worlds throws rather
     * than answering, and this runs on movement.
     */
    public boolean isNear(Location where) {
        if (where == null || location == null || where.getWorld() == null
                || location.getWorld() == null) {
            return false;
        }
        if (!where.getWorld().equals(location.getWorld())) {
            return false;
        }
        return where.distanceSquared(location) <= radius * radius;
    }
}
