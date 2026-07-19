package com.diamend.darksea.island;

import com.diamend.darksea.util.Pos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** islands.yml round-trips: instances, chest index, refill timestamps, reset. */
class IslandRegistryTest {

    private static final Logger LOG = Logger.getLogger("IslandRegistryTest");

    private IslandInstance sample(String id) {
        return new IslandInstance(id, "wreck_small", 2,
                new Pos(1200, 58, -300),
                new Pos(1180, 50, -320), new Pos(1220, 75, -280),
                List.of(new Pos(1201, 63, -299), new Pos(1195, 64, -310)),
                List.of(new Pos(1205, 63, -305)));
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path tmp) {
        File file = tmp.resolve("islands.yml").toFile();
        IslandRegistry registry = new IslandRegistry(file, LOG);
        registry.load();
        assertFalse(registry.spawnPasted());

        IslandInstance island = sample("t2-1");
        island.setRefilled(island.chests().get(0), 1234567890L);
        registry.add(island);
        registry.setSpawnPasted(true);

        IslandRegistry reloaded = new IslandRegistry(file, LOG);
        reloaded.load();
        assertTrue(reloaded.spawnPasted());
        assertEquals(1, reloaded.all().size());

        IslandInstance back = reloaded.get("t2-1");
        assertNotNull(back);
        assertEquals("wreck_small", back.template());
        assertEquals(2, back.tier());
        assertEquals(new Pos(1200, 58, -300), back.origin());
        assertEquals(2, back.chests().size());
        assertEquals(1, back.spawnPoints().size());
        assertEquals(1234567890L, back.lastRefill(back.chests().get(0)));
        assertEquals(0L, back.lastRefill(back.chests().get(1)));
    }

    @Test
    void chestIndexResolvesExactPositions(@TempDir Path tmp) {
        IslandRegistry registry = new IslandRegistry(tmp.resolve("islands.yml").toFile(), LOG);
        registry.load();
        registry.add(sample("t2-1"));

        IslandRegistry.ChestRef ref = registry.chestAt(1201, 63, -299);
        assertNotNull(ref);
        assertEquals("t2-1", ref.island().id());
        assertEquals(new Pos(1201, 63, -299), ref.pos());
        assertNull(registry.chestAt(1201, 62, -299));
    }

    @Test
    void byTierAndNextIdAndClearAll(@TempDir Path tmp) {
        IslandRegistry registry = new IslandRegistry(tmp.resolve("islands.yml").toFile(), LOG);
        registry.load();
        assertEquals("t2-1", registry.nextId(2));
        registry.add(sample("t2-1"));
        assertEquals("t2-2", registry.nextId(2));
        assertEquals("t3-1", registry.nextId(3));
        assertEquals(1, registry.byTier(2).size());
        assertTrue(registry.byTier(3).isEmpty());

        registry.clearAll();
        assertTrue(registry.all().isEmpty());
        assertFalse(registry.spawnPasted());
        assertNull(registry.chestAt(1201, 63, -299));
    }

    @Test
    void softResetClearsRefillTimestamps() {
        IslandInstance island = sample("t2-1");
        Pos chest = island.chests().get(0);
        island.setRefilled(chest, 555L);
        assertEquals(555L, island.lastRefill(chest));
        island.clearRefills();
        assertEquals(0L, island.lastRefill(chest));
    }

    @Test
    void multiChestIslandsElectExactlyOneStableVault(@TempDir Path tmp) {
        IslandInstance island = sample("t2-1");
        Pos vault = island.vaultChest();
        assertNotNull(vault, "a two-chest island must have a vault");
        assertTrue(island.chests().contains(vault), "the vault must be a registered chest");

        int vaults = 0;
        for (Pos chest : island.chests()) {
            if (island.isVaultChest(chest)) {
                vaults++;
            }
        }
        assertEquals(1, vaults, "exactly one chest is the vault");

        // The election survives a disk round-trip (it derives from the origin).
        File file = tmp.resolve("islands.yml").toFile();
        IslandRegistry registry = new IslandRegistry(file, LOG);
        registry.load();
        registry.add(island);
        IslandRegistry reloaded = new IslandRegistry(file, LOG);
        reloaded.load();
        assertEquals(vault, reloaded.get("t2-1").vaultChest());
    }

    @Test
    void singleChestIslandsNeverHaveAVault() {
        IslandInstance lone = new IslandInstance("t1-1", "demo", 1,
                new Pos(700, 62, 900), new Pos(696, 58, 896), new Pos(704, 66, 904),
                List.of(new Pos(702, 65, 902)), List.of(new Pos(698, 65, 898)));
        assertNull(lone.vaultChest());
        assertFalse(lone.isVaultChest(lone.chests().get(0)));
    }
}
