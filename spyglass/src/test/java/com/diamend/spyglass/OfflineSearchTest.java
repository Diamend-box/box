package com.diamend.spyglass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtWriter;
import com.diamend.spyglass.offline.NameCache;
import com.diamend.spyglass.offline.OfflineSearch;
import com.diamend.spyglass.offline.PlayerFiles;

/**
 * Searching every save on the disk.
 *
 * <p>The interesting parts are not "does it find the item" but the promises
 * around it: that it stops where it was told to, that a save it cannot read does
 * not take the scan down, and that the cache notices when a file changes
 * underneath it.
 */
class OfflineSearchTest {

    private static final UUID UUID_NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID UUID_JEB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    @TempDir
    Path root;

    private Path world;
    private NameCache names;
    private OfflineSearch search;

    @BeforeEach
    void setUp() throws IOException {
        world = root.resolve("world");
        save(UUID_NOTCH, SamplePlayer.playerData());
        Files.write(root.resolve("usercache.json"), ("[{\"name\":\"Notch\",\"uuid\":\""
                + UUID_NOTCH + "\",\"expiresOn\":\"2030-01-01 00:00:00 +0000\"}]")
                .getBytes(StandardCharsets.UTF_8));

        PlayerFiles files = new PlayerFiles(world.toFile());
        names = new NameCache(files);
        search = new OfflineSearch(files, names, 64);
    }

    private void save(UUID uuid, NbtCompound data) throws IOException {
        NbtWriter.writeFile(world.resolve("playerdata").resolve(uuid + ".dat"), data);
    }

    @Test
    void findsAnItemNobodyIsOnlineToBeAskedAbout() {
        OfflineSearch.Result result = search.search("tnt", 100, 5_000L, 50);

        assertEquals(1, result.hits().size(), () -> String.valueOf(result.hits()));
        OfflineSearch.Hit hit = result.hits().get(0);
        assertEquals("Notch", hit.name(), "named from the server's own usercache");
        assertEquals("inventory", hit.where());
        // The tnt is inside the shulker box, and the line says so.
        assertTrue(hit.line().contains("shulker_box"), hit.line());
        assertTrue(hit.line().contains("> tnt x16"), hit.line());
        assertTrue(result.complete());
    }

    @Test
    void searchesEnderChestsToo() {
        OfflineSearch.Result result = search.search("elytra", 100, 5_000L, 50);

        assertEquals(1, result.hits().size());
        assertEquals("enderchest", result.hits().get(0).where());
    }

    @Test
    void aUuidWithNoCachedNameIsStillReported() throws IOException {
        save(UUID_JEB, SamplePlayer.playerData());

        OfflineSearch.Result result = search.search("elytra", 100, 5_000L, 50);

        assertEquals(2, result.hits().size());
        assertTrue(result.hits().stream().anyMatch(hit -> hit.name().equals(UUID_JEB.toString())),
                () -> String.valueOf(result.hits()));
    }

    @Test
    void theScanStopsWhereItWasToldTo() throws IOException {
        save(UUID_JEB, SamplePlayer.playerData());

        OfflineSearch.Result result = search.search("elytra", 1, 5_000L, 50);

        assertEquals(1, result.scanned());
        assertEquals(2, result.total());
        assertFalse(result.complete());
        assertTrue(result.stopped().contains("find.max-saves"), result.stopped());
    }

    @Test
    void aSaveThatCannotBeReadIsCountedRatherThanFatal() throws IOException {
        Files.write(world.resolve("playerdata").resolve(UUID_JEB + ".dat"),
                new byte[] { 9, 9, 9, 9 });
        // Not named after a UUID at all: skipped without being called a failure.
        Files.write(world.resolve("playerdata").resolve("session.lock.dat"), new byte[] { 1 });

        OfflineSearch.Result result = search.search("tnt", 100, 5_000L, 50);

        assertEquals(1, result.hits().size(), "the readable save still answered");
        assertEquals(1, result.scanned());
        assertEquals(1, result.failed());
        assertEquals(3, result.total());
    }

    @Test
    void aChangedSaveIsReadAgainRatherThanRememberedWrong() throws IOException {
        assertEquals(1, search.search("tnt", 100, 5_000L, 50).hits().size());

        // Same player, no shulker box this time, and a timestamp that says so.
        Path file = world.resolve("playerdata").resolve(UUID_NOTCH + ".dat");
        NbtWriter.writeFile(file, SamplePlayer.emptyHanded());
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000L));

        assertTrue(search.search("tnt", 100, 5_000L, 50).hits().isEmpty());
    }

    @Test
    void namesAreReadFromTheServersCacheNotInvented() {
        names.refresh();

        assertEquals("Notch", names.name(UUID_NOTCH));
        assertNull(names.name(UUID_JEB));
        assertTrue(names.names().contains("Notch"));
        // And back the other way, which is how an offline name resolves.
        assertEquals(UUID_NOTCH, names.uuid("notch"));
        assertNull(names.uuid("SomeoneElse"));
    }
}
