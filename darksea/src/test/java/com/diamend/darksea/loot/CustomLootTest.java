package com.diamend.darksea.loot;

import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * The overlay that {@code /ds loot} writes. The whole design rests on one
 * promise — that adding something in game cannot damage the shipped tables —
 * so that is what most of this file checks.
 */
class CustomLootTest {

    private static final Logger LOG = Logger.getLogger("CustomLootTest");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static LootEntry item(Material material, int weight) {
        return new LootEntry(LootEntry.Type.ITEM, material, 1, 1, null, List.of(),
                0, null, weight);
    }

    private static LootTables shipped() {
        LootTable base = new LootTable(1, 4, 60,
                List.of(item(Material.ROTTEN_FLESH, 10), item(Material.BONE, 5)));
        LootTable vault = new LootTable(1, 6, 90, List.of(item(Material.GOLD_INGOT, 3)));
        return new LootTables(Map.of(1, base), Map.of(1, vault));
    }

    @Test
    void anAdditionAppendsAndLeavesEveryShippedLineAlone() {
        LootTables merged = CustomLoot.empty()
                .plus(new CustomLoot.Key(1, false), item(Material.DIAMOND, 2))
                .mergeInto(shipped());

        LootTable base = merged.base().get(1);
        assertEquals(3, base.entries().size());
        // The shipped lines come first, unchanged, in their original order.
        assertEquals(Material.ROTTEN_FLESH, base.entries().get(0).material());
        assertEquals(Material.BONE, base.entries().get(1).material());
        assertEquals(Material.DIAMOND, base.entries().get(2).material());
        // And the table's pacing is the shipped table's, not something invented.
        assertEquals(4, base.rolls());
        assertEquals(60, base.refillCooldownMinutes());
    }

    @Test
    void theBaseAndVaultTablesOfOneTierAreSeparatePools() {
        LootTables merged = CustomLoot.empty()
                .plus(new CustomLoot.Key(1, true), item(Material.DIAMOND, 2))
                .mergeInto(shipped());

        // The fixture ships two base lines and one vault line; a vault addition
        // must move only the second of those numbers.
        assertEquals(2, merged.base().get(1).entries().size(), "base must be untouched");
        assertEquals(2, merged.vault().get(1).entries().size());
    }

    @Test
    void anEntryForATierThatDoesNotExistIsDroppedRatherThanInvented() {
        // Inventing a table would give a typo'd tier number a rolls count and a
        // cooldown nobody chose, and it would look like it had worked.
        LootTables merged = CustomLoot.empty()
                .plus(new CustomLoot.Key(9, false), item(Material.DIAMOND, 2))
                .mergeInto(shipped());
        assertEquals(1, merged.base().size());
        assertFalse(merged.base().containsKey(9));
    }

    @Test
    void anEmptyOverlayReturnsTheShippedTablesUntouched() {
        LootTables tables = shipped();
        assertSame(tables, CustomLoot.empty().mergeInto(tables));
    }

    @Test
    void removingTheLastEntryDropsTheTableRatherThanLeavingItEmpty() {
        CustomLoot.Key key = new CustomLoot.Key(1, false);
        CustomLoot loot = CustomLoot.empty().plus(key, item(Material.DIAMOND, 2));
        assertEquals(1, loot.size());
        CustomLoot cleared = loot.with(key, List.of());
        assertEquals(0, cleared.size());
        assertTrue(cleared.added().isEmpty(), "an empty list must not linger as a key");
    }

    @Test
    void everyEntryTypeSurvivesAWriteAndReadOfTheOverlayFile() throws Exception {
        // The editor writes this file after every click. If a type could not
        // round-trip, an admin would lose the entry at the next restart and
        // have no way to tell which click had failed.
        File file = Files.createTempDirectory("darksea-loot").resolve("loot-custom.yml").toFile();
        LootEntry snapshot = new LootEntry(LootEntry.Type.SNAPSHOT, null, 2, 4, null,
                List.of(), 0, sampleSnapshot(), 7);
        CustomLoot before = CustomLoot.empty()
                .plus(new CustomLoot.Key(1, false), item(Material.DIAMOND, 3))
                .plus(new CustomLoot.Key(1, false),
                        new LootEntry(LootEntry.Type.ARMOR, null, 0, 0, null, List.of(),
                                2, null, 4))
                .plus(new CustomLoot.Key(1, false),
                        new LootEntry(LootEntry.Type.CUSTOM, null, 4, 9, null, List.of(),
                                0, DarkSeaItems.CHRONON, 6))
                .plus(new CustomLoot.Key(1, true), snapshot);

        CustomLootConfig.save(before, file, LOG);
        CustomLoot after = CustomLootConfig.load(file, LOG);

        assertEquals(before.added().keySet(), after.added().keySet());
        for (CustomLoot.Key key : before.added().keySet()) {
            assertEquals(before.entriesFor(key), after.entriesFor(key),
                    "entries for " + key + " changed across a save/load");
        }
    }

    @Test
    void aSnapshotRebuildsTheItemItWasTakenFrom() {
        org.bukkit.inventory.ItemStack original =
                new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = original.getItemMeta();
        meta.setUnbreakable(true);
        original.setItemMeta(meta);

        String data;
        try {
            data = java.util.Base64.getEncoder().encodeToString(original.serializeAsBytes());
        } catch (RuntimeException | LinkageError ex) {
            abort("this server double cannot serialise item stacks");
            return;
        }
        org.bukkit.inventory.ItemStack rebuilt = LootEntry.decode(data);
        assertNotNull(rebuilt);
        assertEquals(Material.DIAMOND_SWORD, rebuilt.getType());
        assertTrue(rebuilt.getItemMeta().isUnbreakable(),
                "a snapshot that dropped item data would be pointless");
    }

    @Test
    void anUnreadableSnapshotIsNullRatherThanAnException() {
        // A hand-edited file or an item from a since-removed plugin. The chest
        // is one item lighter; the refill does not throw.
        assertEquals(null, LootEntry.decode("not base64 at all"));
    }

    /**
     * A base64 stack image, or a skipped test. Stack serialisation is a server
     * capability, not a plugin one; where the test double lacks it, the right
     * answer is to say so rather than to fail a build over a mock's gap or —
     * worse — to weaken the assertion until it passes everywhere.
     */
    private static String sampleSnapshot() {
        try {
            return java.util.Base64.getEncoder().encodeToString(
                    new org.bukkit.inventory.ItemStack(Material.NETHERITE_INGOT)
                            .serializeAsBytes());
        } catch (RuntimeException | LinkageError ex) {
            return abort("this server double cannot serialise item stacks");
        }
    }
}
