package com.diamend.boxcore;

import com.diamend.boxcore.collection.PlacedBlocks;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anti-farm flags: that they land in the chunk rather than in memory, that
 * they survive being read back by a fresh instance, and that the cases which
 * used to launder a placed block — a restart, a piston, a regenerated mine —
 * now behave.
 */
class PlacedBlocksTest {

    private ServerMock server;
    private BoxCorePlugin plugin;
    private World world;
    private PlacedBlocks placed;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
        world = server.addSimpleWorld("placed-test");
        placed = new PlacedBlocks(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block block(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material);
        return block;
    }

    // ------------------------------------------------------------------
    // The core rule
    // ------------------------------------------------------------------

    @Test
    void aPlacedBlockIsRefusedWhenItIsBrokenAgain() {
        Block block = block(8, 64, 8, Material.COBBLESTONE);
        placed.mark(block);
        assertTrue(placed.consume(block), "the block a player placed must not count");
    }

    @Test
    void aBlockNobodyPlacedCounts() {
        assertFalse(placed.consume(block(9, 64, 9, Material.COBBLESTONE)));
    }

    @Test
    void breakingItOnceIsEnoughToForgetIt() {
        Block block = block(1, 70, 1, Material.COBBLESTONE);
        placed.mark(block);
        assertTrue(placed.consume(block));
        // Whatever appears in that spot next is nobody's placement.
        assertFalse(placed.consume(block), "the flag must be consumed, not left behind");
    }

    @Test
    void theFlagLivesInTheChunkNotInTheObject() {
        Block block = block(4, 72, 4, Material.COBBLESTONE);
        placed.mark(block);
        // A second instance reading the same world is the closest this suite can
        // get to a restart: nothing is carried over in Java, only in the chunk.
        PlacedBlocks reloaded = new PlacedBlocks(plugin);
        assertTrue(reloaded.consume(block),
                "a restart used to forget every placement and re-open place-and-break farming");
    }

    @Test
    void separateBlocksKeepSeparateFlags() {
        Block placedBlock = block(2, 65, 2, Material.COBBLESTONE);
        Block naturalBlock = block(3, 65, 2, Material.COBBLESTONE);
        placed.mark(placedBlock);
        assertFalse(placed.consume(naturalBlock), "only the flagged position is refused");
        assertTrue(placed.consume(placedBlock));
    }

    @Test
    void flagsAreKeptPerChunkNotPerChunkLocalPosition() {
        // Same position within their chunks, 16 blocks apart: packing masks the
        // coordinates to 0-15, so these must not collide.
        Block here = block(5, 64, 5, Material.COBBLESTONE);
        Block nextChunk = block(21, 64, 5, Material.COBBLESTONE);
        placed.mark(here);
        assertFalse(placed.consume(nextChunk));
        assertTrue(placed.consume(here));
    }

    // ------------------------------------------------------------------
    // Mine regeneration
    // ------------------------------------------------------------------

    @Test
    void aRegeneratedBlockCountsEvenOnAFlaggedSpot() {
        Block block = block(6, 66, 6, Material.COBBLESTONE);
        placed.mark(block);
        // A mine reset drops fresh ore where the cobblestone was. It never fired
        // a place event, so without the material check the stale flag would
        // silently refuse it forever.
        block.setType(Material.DIAMOND_ORE);
        assertFalse(placed.consume(block), "regenerated ore must count");
        assertFalse(placed.consume(block), "and the stale flag must be gone");
    }

    @Test
    void replacingYourOwnPlacementKeepsItFlagged() {
        Block block = block(7, 66, 7, Material.COBBLESTONE);
        placed.mark(block);
        block.setType(Material.DIAMOND_ORE);
        placed.mark(block);
        assertTrue(placed.consume(block), "the second placement is still a placement");
    }

    // ------------------------------------------------------------------
    // Pistons
    // ------------------------------------------------------------------

    @Test
    void aPushedBlockCarriesItsFlag() {
        Block from = block(10, 68, 10, Material.COBBLESTONE);
        placed.mark(from);
        placed.shift(List.of(from), 1, 0, 0);
        Block to = block(11, 68, 10, Material.COBBLESTONE);
        assertTrue(placed.consume(to), "shoving a placed block one space must not launder it");
        assertFalse(placed.consume(from), "and it must not stay flagged where it was");
    }

    @Test
    void aPushedRunKeepsEveryFlagInIt() {
        // Blocks in a run overlap their own destinations, so a naive move that
        // cleared as it went would erase the flag it was about to write.
        Block first = block(0, 69, 0, Material.COBBLESTONE);
        Block second = block(1, 69, 0, Material.COBBLESTONE);
        placed.mark(first);
        placed.mark(second);
        placed.shift(List.of(first, second), 1, 0, 0);
        assertTrue(placed.consume(block(1, 69, 0, Material.COBBLESTONE)));
        assertTrue(placed.consume(block(2, 69, 0, Material.COBBLESTONE)));
    }

    @Test
    void aPushAcrossAChunkBorderStillCarries() {
        Block from = block(15, 71, 3, Material.COBBLESTONE);
        placed.mark(from);
        placed.shift(List.of(from), 1, 0, 0);
        assertTrue(placed.consume(block(16, 71, 3, Material.COBBLESTONE)));
    }

    // ------------------------------------------------------------------
    // Admin wipe
    // ------------------------------------------------------------------

    @Test
    void clearingChunksDropsEveryFlagInThem() {
        Block one = block(2, 64, 2, Material.COBBLESTONE);
        Block two = block(40, 64, 40, Material.COBBLESTONE);
        placed.mark(one);
        placed.mark(two);
        int cleared = placed.clearChunks(world, 0, 0, 1);
        assertEquals(1, cleared, "only the chunks in range are wiped");
        assertFalse(placed.consume(one));
        assertTrue(placed.consume(two), "a flag outside the radius is untouched");
    }

    @Test
    void chunksAreOnlyWrittenWhenTheyHoldSomething() {
        assertEquals(0, placed.clearChunks(world, 0, 0, 2));
    }

    @Test
    void chunkDataIsUsedRatherThanTheMemoryFallback() {
        placed.mark(block(3, 64, 3, Material.COBBLESTONE));
        assertTrue(placed.isPersistent(),
                "Paper chunks take persistent data; falling back to memory would lose it on restart");
    }

    // ------------------------------------------------------------------
    // Packing
    // ------------------------------------------------------------------

    @Test
    void everyPositionInAChunkPacksToItsOwnValue() {
        boolean[] seen = new boolean[512 * 256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y += 7) {
                    int packed = PlacedBlocks.position(x, y, z);
                    assertTrue(packed >= 0, "y " + y + " must be packable");
                    assertFalse(seen[packed], "collision at " + x + "," + y + "," + z);
                    seen[packed] = true;
                }
            }
        }
    }

    @Test
    void aBlockOutsideTheWorldIsNeverTracked() {
        assertEquals(-1, PlacedBlocks.position(0, -65, 0));
        assertEquals(-1, PlacedBlocks.position(0, 448, 0));
        assertTrue(PlacedBlocks.position(0, 319, 0) >= 0, "the tallest vanilla block still fits");
    }

    @Test
    void entriesStaySortedAndSearchable() {
        int[] entries = new int[0];
        int[] positions = {500, 12, 9000, 3, 700};
        for (int position : positions) {
            int index = PlacedBlocks.indexOf(entries, position);
            assertTrue(index < 0, "not there yet");
            entries = PlacedBlocks.insert(entries, -index - 1, (position << 14) | 42);
        }
        assertEquals(positions.length, entries.length);
        for (int i = 1; i < entries.length; i++) {
            assertTrue(entries[i - 1] < entries[i], "the array must stay sorted for binary search");
        }
        for (int position : positions) {
            int index = PlacedBlocks.indexOf(entries, position);
            assertTrue(index >= 0, "position " + position + " should be found");
            assertEquals(42, entries[index] & 0x3fff, "the material hash rides along untouched");
        }
    }

    @Test
    void removingAnEntryLeavesTheRestFindable() {
        int[] entries = new int[0];
        for (int position : new int[]{1, 2, 3}) {
            entries = PlacedBlocks.insert(entries, -PlacedBlocks.indexOf(entries, position) - 1,
                    position << 14);
        }
        entries = PlacedBlocks.removeAt(entries, PlacedBlocks.indexOf(entries, 2));
        assertEquals(2, entries.length);
        assertTrue(PlacedBlocks.indexOf(entries, 1) >= 0);
        assertTrue(PlacedBlocks.indexOf(entries, 2) < 0);
        assertTrue(PlacedBlocks.indexOf(entries, 3) >= 0);
    }

    @Test
    void differentMaterialsHashDifferently() {
        assertFalse(PlacedBlocks.hash(Material.COBBLESTONE) == PlacedBlocks.hash(Material.DIAMOND_ORE));
    }
}
