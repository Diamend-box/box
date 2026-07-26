package com.diamend.darksea.world.cultist;

import com.diamend.darksea.util.Pos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First-touch regen timing, which is the whole reason the caves are
 * continuously grindable rather than a supply drop that gets cleared and then
 * sits dead for half an hour.
 */
class NodeRegistryTest {

    private static final Logger LOG = Logger.getLogger(NodeRegistryTest.class.getName());
    private static final long COOLDOWN = 18L * 60_000L;

    private static NodeRegistry.Node node(String id, int blocks) {
        List<Pos> positions = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            positions.add(new Pos(i, 30, 0));
        }
        return new NodeRegistry.Node(id, "emberglass", new Pos(0, 30, 0), positions, 99L, 0L);
    }

    /**
     * The core of the change. Working a vein must not push its deadline back —
     * with last-touch timing a player chipping at a vein held it open
     * indefinitely and punished themselves for not clearing it in one go.
     */
    @Test
    void onlyTheFirstBlockStartsTheClock() {
        NodeRegistry.Node vein = node("emberglass-1", 100);
        assertEquals(0L, vein.firstMined());

        assertTrue(vein.touch(1_000L), "the first block should start the clock");
        assertEquals(1_000L, vein.firstMined());

        assertFalse(vein.touch(2_000L), "the second block must not restart the clock");
        assertFalse(vein.touch(900_000L), "nor a block mined much later");
        assertEquals(1_000L, vein.firstMined(), "the deadline moved");
    }

    /** The vein is due a fixed cooldown after that first block, whatever happened since. */
    @Test
    void theVeinIsDueOnFirstTouchPlusCooldown() {
        NodeRegistry.Node vein = node("emberglass-1", 100);
        assertFalse(vein.isDue(Long.MAX_VALUE, COOLDOWN), "an untouched vein is never due");

        vein.touch(1_000L);
        assertFalse(vein.isDue(1_000L + COOLDOWN - 1, COOLDOWN));
        assertTrue(vein.isDue(1_000L + COOLDOWN, COOLDOWN));

        // Someone mining right up to the deadline does not extend it.
        vein.touch(1_000L + COOLDOWN - 1);
        assertTrue(vein.isDue(1_000L + COOLDOWN, COOLDOWN), "late mining delayed the regrow");
    }

    /**
     * After regrowing, the clock has to be unset rather than merely old — a vein
     * left with a stale timestamp would be instantly due again and would refill
     * on every pass forever.
     */
    @Test
    void regrowingClearsTheClockSoTheNextCycleStartsFresh() {
        NodeRegistry.Node vein = node("emberglass-1", 100);
        vein.touch(1_000L);
        long after = 1_000L + COOLDOWN;
        assertTrue(vein.isDue(after, COOLDOWN));

        vein.resetClock();
        assertEquals(0L, vein.firstMined());
        assertFalse(vein.isDue(after + 10_000_000L, COOLDOWN),
                "a regrown vein is due again without anyone touching it");

        assertTrue(vein.touch(after + 5_000L), "the next block should start a fresh cycle");
        assertEquals(after + 5_000L, vein.firstMined());
    }

    @Test
    void aVeinRoundTripsThroughDiskWithItsClockIntact(@TempDir Path dir) {
        File file = new File(dir.toFile(), "nodes.yml");
        NodeRegistry registry = new NodeRegistry(file, LOG);
        NodeRegistry.Node vein = node("emberglass-1", 40);
        vein.touch(1_234_567L);
        registry.add(vein);
        registry.save();

        NodeRegistry reloaded = new NodeRegistry(file, LOG);
        reloaded.load();
        assertEquals(1, reloaded.all().size());
        NodeRegistry.Node back = reloaded.all().get(0);
        assertEquals("emberglass-1", back.id());
        assertEquals(1_234_567L, back.firstMined(), "the regrow clock did not survive a restart");
        assertEquals(99L, back.seed(), "the seed did not survive, so the rind cannot be redrawn");
        assertEquals(40, back.blocks().size());
    }

    /** The index has to find every block of every vein, and nothing else. */
    @Test
    void everyBlockOfAVeinResolvesBackToIt(@TempDir Path dir) {
        NodeRegistry registry = new NodeRegistry(new File(dir.toFile(), "nodes.yml"), LOG);
        NodeRegistry.Node vein = node("emberglass-1", 150);
        registry.add(vein);

        for (Pos pos : vein.blocks()) {
            assertSame(vein, registry.nodeAt(pos), "index lost " + pos);
        }
        assertNull(registry.nodeAt(new Pos(9999, 30, 0)), "index claimed a block it does not own");

        registry.clearAll();
        assertNull(registry.nodeAt(vein.blocks().get(0)), "index survived a clear");
        assertTrue(registry.isEmpty());
    }

    /** A reload rebuilds the index, not just the node list. */
    @Test
    void theIndexIsRebuiltOnLoad(@TempDir Path dir) {
        File file = new File(dir.toFile(), "nodes.yml");
        NodeRegistry registry = new NodeRegistry(file, LOG);
        registry.add(node("emberglass-1", 60));
        registry.save();

        NodeRegistry reloaded = new NodeRegistry(file, LOG);
        reloaded.load();
        assertNotNull(reloaded.nodeAt(new Pos(17, 30, 0)),
                "nodeAt found nothing after a reload — the index was not rebuilt");
    }
}
