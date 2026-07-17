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

    private Map<Integer, LootTable> parse(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return LootConfig.load(config, LOG);
    }

    @Test
    void parsesTablesAndSkipsMalformedEntries() throws Exception {
        Map<Integer, LootTable> tables = parse("""
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
        LootTable table = tables.get(2);
        assertNotNull(table);
        assertEquals(3, table.rolls());
        assertEquals(90L * 60_000L, table.refillCooldownMillis());
        // The bad-material entry was dropped, the other three survive.
        assertEquals(3, table.entries().size());
    }

    @Test
    void weightedPickHeavilyFavorsHeavyEntries() throws Exception {
        Map<Integer, LootTable> tables = parse("""
                tiers:
                  1:
                    rolls: 1
                    refill-cooldown-minutes: 10
                    entries:
                      - { type: item, material: DIRT, min: 1, max: 1, weight: 1 }
                      - { type: item, material: IRON_INGOT, min: 1, max: 1, weight: 999 }
                """);
        LootTable table = tables.get(1);
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
                """).get(1);
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
                """).get(2);
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
                """).get(1);
        assertEquals(3, SeaArmor.tokenLevelOf(tokens.rollLoot(new Random(6), armor).get(0)));
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
                """).get(4);
        // armor settings only define tier 2 → every roll yields null → empty loot.
        assertTrue(table.rollLoot(new Random(8), armor).isEmpty());
    }
}
