package com.diamend.darksea.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run-loot stamp: applied on gather, read on death, cleared on banking. */
class RunLootTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFreshItemIsNotRunLoot() {
        assertFalse(RunLoot.isRunLoot(new ItemStack(Material.DIAMOND)));
        assertFalse(RunLoot.isRunLoot(null));
    }

    @Test
    void tagThenBankRoundTrips() {
        ItemStack loot = RunLoot.tag(new ItemStack(Material.DIAMOND, 3));
        assertTrue(RunLoot.isRunLoot(loot), "a tagged item should read as run loot");

        assertTrue(RunLoot.bank(loot), "banking a marked item reports a change");
        assertFalse(RunLoot.isRunLoot(loot), "a banked item is no longer run loot");
    }

    @Test
    void bankingIsIdempotentAndSafeOnPlainItems() {
        assertFalse(RunLoot.bank(new ItemStack(Material.COD)), "nothing to bank on a plain item");
        assertFalse(RunLoot.bank(null), "banking null is a no-op");

        ItemStack loot = RunLoot.tag(new ItemStack(Material.EMERALD));
        assertTrue(RunLoot.bank(loot));
        assertFalse(RunLoot.bank(loot), "a second bank finds nothing left to clear");
    }

    @Test
    void taggingNullIsANoOp() {
        assertFalse(RunLoot.isRunLoot(RunLoot.tag(null)));
    }
}
