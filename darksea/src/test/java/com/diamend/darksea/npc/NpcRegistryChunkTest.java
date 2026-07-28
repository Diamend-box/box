package com.diamend.darksea.npc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk lookup for NPC placements — what decides whether a shopkeeper comes
 * back when their chunk streams in.
 *
 * <p>The NPCs are non-persistent entities, so a placement this lookup misses is
 * a shopkeeper who is gone until the next restart. The negative-coordinate
 * cases are the ones worth pinning: truncating instead of flooring puts
 * everything in the western or northern half of chunk -1 into chunk 0, which is
 * the kind of bug that only shows up on one side of spawn.
 *
 * <p>Placements are loaded from a written npcs.yml rather than added through a
 * {@code Location}, because the registry stores a world <em>name</em> and the
 * lookup never needs a live world — so neither should the test.
 */
class NpcRegistryChunkTest {

    @TempDir
    Path dir;

    private NpcRegistry registry;
    private File file;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        file = new File(dir.toFile(), "npcs.yml");
        registry = new NpcRegistry(file, Logger.getLogger("npc-chunk-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void chunkOfDividesTheWayMinecraftDoes() {
        assertEquals(0, NpcRegistry.chunkOf(0.0));
        assertEquals(0, NpcRegistry.chunkOf(15.9));
        assertEquals(1, NpcRegistry.chunkOf(16.0));
        assertEquals(1, NpcRegistry.chunkOf(31.5));
        assertEquals(2, NpcRegistry.chunkOf(32.0));
    }

    @Test
    void negativeCoordinatesFloorRatherThanTruncate() {
        assertEquals(-1, NpcRegistry.chunkOf(-0.5),
                "-0.5 stands in chunk -1; truncation would say 0");
        assertEquals(-1, NpcRegistry.chunkOf(-1.0));
        assertEquals(-1, NpcRegistry.chunkOf(-15.5));
        assertEquals(-2, NpcRegistry.chunkOf(-16.5));
        assertEquals(-2, NpcRegistry.chunkOf(-31.5));
        assertEquals(-3, NpcRegistry.chunkOf(-33.0));
    }

    @Test
    void aPlacementIsFoundInItsOwnChunkAndNoOther() {
        load("""
                npcs:
                  refugee_trader-1:
                    type: refugee_trader
                    world: darksea
                    x: 20.5
                    y: 64.0
                    z: 40.5
                    yaw: 0.0
                """);

        List<NpcRegistry.Placement> found = registry.inChunk("darksea", 1, 2);
        assertEquals(1, found.size());
        assertEquals("refugee_trader-1", found.get(0).id());

        assertTrue(registry.inChunk("darksea", 2, 2).isEmpty());
        assertTrue(registry.inChunk("darksea", 1, 3).isEmpty());
        assertTrue(registry.inChunk("darksea", 0, 0).isEmpty());
    }

    @Test
    void aPlacementWestOfSpawnLandsInANegativeChunk() {
        load("""
                npcs:
                  artificer-1:
                    type: artificer
                    world: darksea
                    x: -3.5
                    y: 64.0
                    z: -20.0
                    yaw: 0.0
                """);

        assertEquals(1, registry.inChunk("darksea", -1, -2).size());
        assertTrue(registry.inChunk("darksea", 0, -2).isEmpty(),
                "a truncating lookup would find it here and never respawn it");
    }

    @Test
    void placementsInADifferentWorldAreNotReturned() {
        load("""
                npcs:
                  apothecary-1:
                    type: apothecary
                    world: darksea
                    x: 8.0
                    y: 64.0
                    z: 8.0
                    yaw: 0.0
                """);

        assertEquals(1, registry.inChunk("darksea", 0, 0).size());
        assertTrue(registry.inChunk("darksea_caves", 0, 0).isEmpty(),
                "the same chunk coordinates in another world must not match");
    }

    @Test
    void theWholeOutpostSharesAChunkAndAllOfItComesBack() {
        // The normal case, not an edge one: the five stand together on the home
        // island, so one chunk load has to restore all of them.
        load("""
                npcs:
                  refugee_trader-1:
                    type: refugee_trader
                    world: darksea
                    x: 4.0
                    y: 64.0
                    z: 4.0
                    yaw: 0.0
                  artificer-1:
                    type: artificer
                    world: darksea
                    x: 6.0
                    y: 64.0
                    z: 9.0
                    yaw: 0.0
                  apothecary-1:
                    type: apothecary
                    world: darksea
                    x: 15.9
                    y: 64.0
                    z: 15.9
                    yaw: 0.0
                """);

        assertEquals(3, registry.inChunk("darksea", 0, 0).size());
        assertEquals(3, registry.all().size());
    }

    @Test
    void anEmptyRegistryMatchesNothing() {
        assertTrue(registry.inChunk("darksea", 0, 0).isEmpty());
    }

    @Test
    void aRemovedPlacementStopsMatching() {
        load("""
                npcs:
                  boat_expert-1:
                    type: boat_expert
                    world: darksea
                    x: 3.0
                    y: 64.0
                    z: 3.0
                    yaw: 0.0
                """);

        assertEquals(1, registry.inChunk("darksea", 0, 0).size());
        registry.remove("boat_expert-1");
        assertTrue(registry.inChunk("darksea", 0, 0).isEmpty());
    }

    private void load(String yaml) {
        try {
            Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("could not write the test npcs.yml", ex);
        }
        registry.load();
    }
}
