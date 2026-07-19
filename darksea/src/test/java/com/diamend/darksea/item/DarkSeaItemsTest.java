package com.diamend.darksea.item;

import com.diamend.darksea.relic.Relic;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The custom item registry: identity, integrity, and the no-enchant rule. */
class DarkSeaItemsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyRegisteredIdCreatesATaggedItem() {
        for (String id : DarkSeaItems.allIds()) {
            ItemStack item = DarkSeaItems.create(id, 1);
            assertNotNull(item, id + " failed to create");
            assertEquals(id, DarkSeaItems.idOf(item), id + " lost its PDC identity");
            assertTrue(item.getItemMeta().hasDisplayName(), id + " has no display name");
            // Wyatt's rule: no enchantments on custom gear, ever.
            assertTrue(item.getEnchantments().isEmpty(), id + " shipped enchanted");
        }
        assertNull(DarkSeaItems.create("no_such_item", 1));
        assertNull(DarkSeaItems.idOf(new ItemStack(Material.IRON_SWORD)));
        assertNull(DarkSeaItems.idOf(null));
    }

    @Test
    void weaponsCarryAttributesAndAreUnbreakable() {
        for (DarkSeaItems.WeaponSpec spec : DarkSeaItems.WEAPONS.values()) {
            ItemStack item = DarkSeaItems.create(spec.id(), 1);
            assertEquals(spec.material(), item.getType(), spec.id());
            assertTrue(item.getItemMeta().isUnbreakable(), spec.id() + " should be unbreakable");
            assertNotNull(item.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE),
                    spec.id() + " has no attack damage modifier");
            assertNotNull(item.getItemMeta().getAttributeModifiers(Attribute.ATTACK_SPEED),
                    spec.id() + " has no attack speed modifier");
        }
    }

    @Test
    void relicIdsCreateDormantRelics() {
        for (Relic relic : Relic.values()) {
            ItemStack item = DarkSeaItems.create(relic.id(), 1);
            assertEquals(relic, Relic.of(item));
            assertFalse(Relic.isAwake(item), relic.id() + " should drop dormant");
        }
    }

    @Test
    void tonicsCarryRealPotionEffects() {
        ItemStack deepsight = DarkSeaItems.create(DarkSeaItems.DEEPSIGHT_TONIC, 1);
        PotionMeta meta = (PotionMeta) deepsight.getItemMeta();
        assertTrue(meta.hasCustomEffects());
        assertTrue(meta.getCustomEffects().stream()
                .anyMatch(e -> e.getType().equals(PotionEffectType.NIGHT_VISION)));

        ItemStack gillwater = DarkSeaItems.create(DarkSeaItems.GILLWATER_PHILTER, 1);
        PotionMeta gill = (PotionMeta) gillwater.getItemMeta();
        assertTrue(gill.getCustomEffects().stream()
                .anyMatch(e -> e.getType().equals(PotionEffectType.WATER_BREATHING)));

        assertEquals(Material.HONEY_BOTTLE,
                DarkSeaItems.create(DarkSeaItems.SEA_SALVE, 1).getType());
    }

    @Test
    void chrononsCountAndPayCorrectly() {
        Inventory inventory = server.createInventory(null, 27);
        inventory.setItem(0, DarkSeaItems.create(DarkSeaItems.CHRONON, 60));
        inventory.setItem(5, DarkSeaItems.create(DarkSeaItems.CHRONON, 15));
        // A renamed vanilla lookalike must not count.
        inventory.setItem(9, new ItemStack(Material.PRISMARINE_CRYSTALS, 64));
        assertEquals(75, DarkSeaItems.countChronons(inventory));

        // Paying takes from stacks front to back and splits correctly.
        assertTrue(DarkSeaItems.removeChronons(inventory, 70));
        assertEquals(5, DarkSeaItems.countChronons(inventory));

        // Can't overpay: nothing changes, call reports failure.
        assertFalse(DarkSeaItems.removeChronons(inventory, 6));
        assertEquals(5, DarkSeaItems.countChronons(inventory));
        // The vanilla stack was never touched.
        assertEquals(64, inventory.getItem(9).getAmount());
    }
}
