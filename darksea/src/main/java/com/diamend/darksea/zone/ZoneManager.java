package com.diamend.darksea.zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Distance → zone resolution. Zones are kept sorted by radius (bounded rings
 * ascending, the unbounded ring last); lookups compare squared distances so
 * the per-player exposure pass never takes a square root.
 */
public final class ZoneManager {

    private final List<Zone> ordered;

    public ZoneManager(List<Zone> zones) {
        List<Zone> bounded = new ArrayList<>();
        List<Zone> unbounded = new ArrayList<>();
        for (Zone zone : zones) {
            (zone.unbounded() ? unbounded : bounded).add(zone);
        }
        bounded.sort(Comparator.comparingDouble(Zone::maxRadius));
        List<Zone> all = new ArrayList<>(bounded);
        all.addAll(unbounded);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("at least one zone is required");
        }
        this.ordered = List.copyOf(all);
    }

    /**
     * The exposure a player suffers: how many tiers of danger remain after
     * armor and boat protection. 0 or less = fully protected.
     */
    public static int exposure(int requiredTier, int protectionTier, int boatShield) {
        return Math.max(0, requiredTier - protectionTier - boatShield);
    }

    /** Zone containing a point at the given squared distance from center. */
    public Zone zoneAt(double distanceSquared) {
        for (Zone zone : ordered) {
            if (zone.unbounded() || distanceSquared <= zone.maxRadius() * zone.maxRadius()) {
                return zone;
            }
        }
        // No unbounded ring configured: everything beyond falls in the outermost.
        return ordered.get(ordered.size() - 1);
    }

    /** The zone whose required tier equals {@code tier}, or null. */
    public Zone byTier(int tier) {
        for (Zone zone : ordered) {
            if (zone.requiredTier() == tier) {
                return zone;
            }
        }
        return null;
    }

    /**
     * The zone whose effects an exposed player experiences: the most
     * dangerous ring with {@code 1 <= requiredTier <= exposure}. Partial
     * protection therefore downgrades danger instead of pass/fail.
     */
    public Zone effectiveDangerZone(int exposure) {
        Zone best = null;
        for (Zone zone : ordered) {
            if (zone.requiredTier() >= 1 && zone.requiredTier() <= exposure
                    && (best == null || zone.requiredTier() > best.requiredTier())) {
                best = zone;
            }
        }
        return best;
    }

    /** Inner radius of a ring = the outer radius of the ring just inside it. */
    public double innerRadius(Zone zone) {
        double inner = 0;
        for (Zone other : ordered) {
            if (other == zone) {
                break;
            }
            if (!other.unbounded()) {
                inner = Math.max(inner, other.maxRadius());
            }
        }
        return inner;
    }

    public int maxTier() {
        int max = 0;
        for (Zone zone : ordered) {
            max = Math.max(max, zone.requiredTier());
        }
        return max;
    }

    public List<Zone> zones() {
        return ordered;
    }
}
