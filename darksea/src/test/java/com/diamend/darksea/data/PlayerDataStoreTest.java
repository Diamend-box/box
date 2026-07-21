package com.diamend.darksea.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Boat-level persistence: it survives a cold cache (a server restart). */
class PlayerDataStoreTest {

    private static final Logger LOG = Logger.getLogger("PlayerDataStoreTest");

    @TempDir
    Path folder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unknownPlayersStartOnTheRowboat() {
        PlayerDataStore store = new PlayerDataStore(folder.toFile(), LOG);
        assertEquals(0, store.boatLevel(UUID.randomUUID()));
    }

    @Test
    void boatLevelSurvivesAReload() {
        UUID id = UUID.randomUUID();
        PlayerDataStore store = new PlayerDataStore(folder.toFile(), LOG);
        store.setBoatLevel(id, 3);
        assertEquals(3, store.boatLevel(id));

        // A brand-new store shares no cache — it must read the value back off
        // disk, the way the plugin would after a restart.
        PlayerDataStore reloaded = new PlayerDataStore(folder.toFile(), LOG);
        assertEquals(3, reloaded.boatLevel(id));
    }

    @Test
    void playersKeepSeparateLevels() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PlayerDataStore store = new PlayerDataStore(folder.toFile(), LOG);
        store.setBoatLevel(a, 2);
        store.setBoatLevel(b, 0);
        assertEquals(2, store.boatLevel(a));
        assertEquals(0, store.boatLevel(b));

        PlayerDataStore reloaded = new PlayerDataStore(folder.toFile(), LOG);
        assertEquals(2, reloaded.boatLevel(a));
        assertEquals(0, reloaded.boatLevel(b));
    }

    @Test
    void unknownPlayersHaveNoStatPoints() {
        PlayerDataStore store = new PlayerDataStore(folder.toFile(), LOG);
        assertEquals(PlayerDataStore.StatPoints.ZERO, store.statPoints(UUID.randomUUID()));
    }

    @Test
    void statPointsSurviveAReloadAlongsideTheBoatLevel() {
        UUID id = UUID.randomUUID();
        PlayerDataStore store = new PlayerDataStore(folder.toFile(), LOG);
        store.setBoatLevel(id, 5);
        store.setStatPoints(id, new PlayerDataStore.StatPoints(1, 2, 1, 0));
        assertEquals(4, store.statPoints(id).total());

        // A cold store reads both the level and the allocation back off the same
        // file — neither write clobbers the other's keys.
        PlayerDataStore reloaded = new PlayerDataStore(folder.toFile(), LOG);
        assertEquals(5, reloaded.boatLevel(id));
        assertEquals(new PlayerDataStore.StatPoints(1, 2, 1, 0), reloaded.statPoints(id));
    }
}
