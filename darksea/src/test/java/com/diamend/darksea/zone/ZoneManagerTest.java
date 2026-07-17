package com.diamend.darksea.zone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Zone resolution by radius and the exposure formula — pure logic, no server. */
class ZoneManagerTest {

    private static Zone zone(String id, double maxRadius, int tier) {
        return new Zone(id, id, maxRadius, tier, List.of());
    }

    private ZoneManager manager() {
        // Deliberately unordered input: the manager must sort.
        return new ZoneManager(List.of(
                zone("zone2", 3000, 2),
                zone("safe", 500, 0),
                zone("zone4", -1, 4),
                zone("zone1", 1500, 1),
                zone("zone3", 5000, 3)));
    }

    private static double sq(double v) {
        return v * v;
    }

    @Test
    void resolvesZonesByDistanceWithInclusiveBoundaries() {
        ZoneManager zones = manager();
        assertEquals("safe", zones.zoneAt(0).id());
        assertEquals("safe", zones.zoneAt(sq(500)).id());
        assertEquals("zone1", zones.zoneAt(sq(500.01)).id());
        assertEquals("zone2", zones.zoneAt(sq(2999)).id());
        assertEquals("zone3", zones.zoneAt(sq(4200)).id());
    }

    @Test
    void unboundedOuterRingCatchesEverythingBeyond() {
        ZoneManager zones = manager();
        assertEquals("zone4", zones.zoneAt(sq(5001)).id());
        assertEquals("zone4", zones.zoneAt(sq(1_000_000)).id());
    }

    @Test
    void byTierAndMaxTier() {
        ZoneManager zones = manager();
        assertEquals("zone3", zones.byTier(3).id());
        assertNull(zones.byTier(9));
        assertEquals(4, zones.maxTier());
    }

    @Test
    void exposureFormula() {
        // exposure = required − (protection + shield), floored at 0
        assertEquals(0, ZoneManager.exposure(0, 0, 0));
        assertEquals(3, ZoneManager.exposure(3, 0, 0));
        assertEquals(0, ZoneManager.exposure(4, 4, 0));
        assertEquals(1, ZoneManager.exposure(4, 2, 1));
        assertEquals(0, ZoneManager.exposure(1, 4, 0));
        assertEquals(2, ZoneManager.exposure(3, 0, 1));
    }

    @Test
    void partialProtectionDowngradesDanger() {
        ZoneManager zones = manager();
        // Tier 2 armor in zone 3 should feel like zone 1.
        int exposure = ZoneManager.exposure(3, 2, 0);
        assertEquals("zone1", zones.effectiveDangerZone(exposure).id());
        // Fully protected → no danger zone at all.
        assertNull(zones.effectiveDangerZone(0));
        // Exposure beyond defined tiers clamps to the harshest ring.
        assertEquals("zone4", zones.effectiveDangerZone(7).id());
    }

    @Test
    void innerRadiusIsThePreviousRingsOuterRadius() {
        ZoneManager zones = manager();
        assertEquals(0, zones.innerRadius(zones.byTier(0)));
        assertEquals(500, zones.innerRadius(zones.byTier(1)));
        assertEquals(3000, zones.innerRadius(zones.byTier(3)));
        assertEquals(5000, zones.innerRadius(zones.byTier(4)));
    }

    @Test
    void rejectsEmptyZoneList() {
        assertThrows(IllegalArgumentException.class, () -> new ZoneManager(List.of()));
    }
}
