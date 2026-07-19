package com.diamend.darksea.loot;

import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.config.DarkSeaSettings.ArmorStyle;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** loot.yml parsing, weighted picking and item rolling. */
class LootTableTest {

    private static final Logger LOG = Logger.getLogger("LootTableTest");

    private ArmorSettings armor;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        armor = new ArmorSettings(true, Map.of(2, new ArmorStyle("<gray>Stormplate</gray>", "IRON")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LootTables parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return LootConfig.load(config, LOG);
    }

    @Test
    void parsesTablesAndSkipsMalformedEntries() throws Exception {
        LootTables tables = parse("""
                tiers:
                  2:
                    rolls: 3
                    refill-cooldown-minutes: 90
                    entries:
                      - { type: item, material: IRON_INGOT, min: 2, max: 5, weight: 10 }
                      - { type: item, material: NOT_A_MATERIAL, min: 1, max: 1, weight: 5 }
                      - { type: armor, tier: 2, weight: 2 }
                      - { type: token, level: 1, weight: 1 }
                """);
        LootTable table = tables.base().get(2);
        assertNotNull(table);
        assertEquals(3, table.rolls());
        assertEquals(90L * 60_000L, table.refillCooldownMillis());
        // The bad-material entry was dropped, the other three survive.
        assertEquals(3, table.entries().size());
    }

    @Test
    void weightedPickHeavilyFavorsHeavyEntries() throws Exception {
        LootTables tables = parse("""
                tiers:
                  1:
                    rolls: 1
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: item, material: DIRT, min: 1, max: 1, weight: 1 }
                      - { type: item, material: IRON_INGOT, min: 1, max: 1, weight: 999 }
                """);
        LootTable table = tables.base().get(1);
        Random rng = new Random(1234);
        int heavy = 0;
        for (int i = 0; i < 1000; i++) {
            if (table.pick(rng).material() == Material.IRON_INGOT) {
                heavy++;
            }
        }
        assertTrue(heavy > 950, "heavy entry picked only " + heavy + "/1000 times");
    }

    @Test
    void itemRollsStayWithinTheAmountRange() throws Exception {
        LootTable table = parse("""
                tiers:
                  1:
                    rolls: 4
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: item, material: EMERALD, min: 2, max: 6, weight: 1 }
                """).base().get(1);
        Random rng = new Random(99);
        for (int i = 0; i < 200; i++) {
            List<ItemStack> loot = table.rollLoot(rng, armor);
            assertEquals(4, loot.size());
            for (ItemStack item : loot) {
                assertEquals(Material.EMERALD, item.getType());
                assertTrue(item.getAmount() >= 2 && item.getAmount() <= 6,
                        "rolled amount " + item.getAmount());
            }
        }
    }

    @Test
    void armorAndTokenEntriesRollTaggedItems() throws Exception {
        LootTable table = parse("""
                tiers:
                  2:
                    rolls: 2
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: armor, tier: 2, weight: 1 }
                """).base().get(2);
        List<ItemStack> loot = table.rollLoot(new Random(5), armor);
        assertEquals(2, loot.size());
        for (ItemStack item : loot) {
            assertEquals(2, SeaArmor.tierOf(item));
        }

        LootTable tokens = parse("""
                tiers:
                  1:
                    rolls: 1
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: token, level: 3, weight: 1 }
                """).base().get(1);
        assertEquals(3, SeaArmor.tokenLevelOf(tokens.rollLoot(new Random(6), armor).get(0)));
    }

    @Test
    void customEntriesParseRollTaggedAndRejectUnknownIds() throws Exception {
        LootTables tables = parse("""
                tiers:
                  1:
                    rolls: 2
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: custom, id: chronon, min: 4, max: 8, weight: 5 }
                      - { type: custom, id: definitely_not_an_item, weight: 5 }
                      - { type: custom, weight: 5 }
                """);
        LootTable table = tables.base().get(1);
        // Both bad customs were dropped at parse time — CI's tripwire.
        assertEquals(1, table.entries().size());
        ItemStack rolled = table.entries().get(0).roll(new Random(3), armor);
        assertEquals(Material.PRISMARINE_CRYSTALS, rolled.getType());
        assertTrue(rolled.getAmount() >= 4 && rolled.getAmount() <= 8);
    }

    @Test
    void islandWealthScalesChrononsAndNothingElse() throws Exception {
        LootTable table = parse("""
                tiers:
                  1:
                    rolls: 1
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: custom, id: chronon, min: 10, max: 10, weight: 1 }
                """).base().get(1);
        // A rich island (1.5x) fattens the pile; a poor one (0.6x) thins it.
        assertEquals(15, table.rollLoot(new Random(1), armor, 1.5).get(0).getAmount());
        assertEquals(6, table.rollLoot(new Random(1), armor, 0.6).get(0).getAmount());

        LootTable items = parse("""
                tiers:
                  1:
                    rolls: 1
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: item, material: EMERALD, min: 3, max: 3, weight: 1 }
                """).base().get(1);
        // Plain goods ignore wealth entirely.
        assertEquals(3, items.rollLoot(new Random(1), armor, 1.8).get(0).getAmount());
    }

    @Test
    void vaultTablesParseAlongsideTheBase() throws Exception {
        LootTables tables = parse("""
                tiers:
                  3:
                    rolls: 4
                    refill-cooldown-minutes: 60
                    entries:
                      - { type: item, material: DIAMOND, min: 1, max: 2, weight: 1 }
                    vault:
                      rolls: 7
                      entries:
                        - { type: item, material: DIAMOND_BLOCK, min: 1, max: 1, weight: 1 }
                """);
        assertEquals(4, tables.base().get(3).rolls());
        assertEquals(7, tables.vault().get(3).rolls());
        // The vault inherits the tier cooldown when it doesn't set its own.
        assertEquals(60, tables.vault().get(3).refillCooldownMinutes());
        // forChest routes vault chests to the vault and everyone else to base.
        assertEquals(tables.vault().get(3), tables.forChest(3, true));
        assertEquals(tables.base().get(3), tables.forChest(3, false));
        // A tier without a vault table falls back to base for its vault chest.
        assertEquals(tables.base().get(3), new LootTables(tables.base(), Map.of()).forChest(3, true));
    }

    @Test
    void armorEntryForUndefinedTierRollsNothingButDoesNotCrash() throws Exception {
        LootTable table = parse("""
                tiers:
                  4:
                    rolls: 3
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: armor, tier: 4, weight: 1 }
                """).base().get(4);
        // armor settings only define tier 2 → every roll yields null → empty loot.
        assertTrue(table.rollLoot(new Random(8), armor).isEmpty());
    }
}
