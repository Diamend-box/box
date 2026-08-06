package com.diamend.boxtutorial;

import com.diamend.boxtutorial.items.ItemRef;
import com.diamend.boxtutorial.items.ItemRegistry;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The named item slots: that the defaults are usable, that binding your own
 * item replaces them everywhere, and that a binding survives a restart.
 */
class ItemsTest {

    private ServerMock server;
    private BoxTutorialPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        server.addSimpleWorld("tutorial_arena");
        plugin = MockBukkit.load(BoxTutorialPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemRegistry items() {
        return plugin.items();
    }

    /** An item pretending to be somebody's custom sword from another plugin. */
    private ItemStack custom() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        meta.lore(List.of(net.kyori.adventure.text.Component.text("Not from here")));
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------

    @Test
    void theShippedItemsAreAllUsable() {
        assertTrue(items().warnings().isEmpty(),
                "the shipped items should load cleanly: " + items().warnings());
        for (String id : List.of("axe", "axe_t2", "pickaxe", "sword", "armor",
                "compressed_log", "charm")) {
            assertTrue(items().isKnown(id), id + " should be defined");
            assertNotNull(items().get(id), id + " should produce an item");
        }
    }

    @Test
    void theSecondAxeIsActuallyBetterThanTheFirst() {
        // "Buy the upgrade" only teaches anything if the upgrade is one.
        ItemStack first = items().get("axe");
        ItemStack second = items().get("axe_t2");
        assertNotNull(second, "the tier-two axe is defined");

        assertTrue(second.getEnchantments().size() > first.getEnchantments().size()
                        || second.getType() != first.getType(),
                "the second axe is a better tool than the first one");
        assertFalse(second.getEnchantments().isEmpty(),
                "and it carries the enchantment the config asked for");
    }

    @Test
    void enchantingOnlyTouchesTheDefault() {
        // Somebody's own axe arrives with whatever it already had. Adding to it
        // would be this plugin editing an item it was only asked to hand out.
        ItemStack mine = new ItemStack(Material.NETHERITE_AXE);
        items().bind("axe_t2", mine);

        assertTrue(items().get("axe_t2").getEnchantments().isEmpty(),
                "no enchantment was bolted onto the bound item");
    }

    @Test
    void anUndefinedSlotIsNotInvented() {
        assertFalse(items().isKnown("trident_of_the_deep"));
        assertNull(items().get("trident_of_the_deep"));
    }

    @Test
    void theCharmCarriesItsStats() {
        assertFalse(items().statsFor("charm").isEmpty(), "the charm is configured with stats");

        ItemStack charm = items().get("charm");
        ItemMeta meta = charm.getItemMeta();
        assertNotNull(meta.getAttributeModifiers(),
                "the stats are written onto the item, so vanilla applies them off-hand");
        assertFalse(meta.getAttributeModifiers().isEmpty(),
                "and there is at least one of them");
    }

    @Test
    void whatTheTutorialHandsOutKnowsItself() {
        // Two slots could be bound to the same material; the mark is what tells
        // them apart, and what a renamed anvil copy can't forge.
        ItemStack charm = items().get("charm");

        assertTrue(items().matches("charm", charm), "it recognises what it gave out");
        assertFalse(items().matches("sword", charm), "and knows which slot it came from");
    }

    @Test
    void bindingReplacesTheDefaultEverywhere() {
        ItemStack mine = custom();
        items().bind("sword", mine);

        assertTrue(items().isBound("sword"));
        ItemStack given = items().get("sword");
        assertEquals(Material.NETHERITE_SWORD, given.getType(), "the trade now hands over mine");
        assertEquals("Excalibur", PlainTextComponentSerializer.plainText()
                .serialize(given.getItemMeta().displayName()), "name and all");
        assertTrue(items().matches("sword", mine), "and it recognises the same item back");
        assertFalse(items().matches("sword", new ItemStack(Material.NETHERITE_SWORD)),
                "a bare netherite sword is not that item");
    }

    @Test
    void bindingTakesACopyRatherThanTheStack() {
        // The menu binds what you're holding. It must never be able to consume
        // it, or a staff member loses a one-of-a-kind item to a misclick.
        ItemStack mine = custom();
        mine.setAmount(3);
        items().bind("sword", mine);

        assertEquals(3, mine.getAmount(), "what they were holding is untouched");
        assertEquals(1, items().get("sword").getAmount(), "and the slot holds one");
    }

    @Test
    void clearingGoesBackToTheDefault() {
        items().bind("sword", custom());
        items().clear("sword");

        assertFalse(items().isBound("sword"));
        assertEquals(Material.IRON_SWORD, items().get("sword").getType());
    }

    @Test
    void aBindingSurvivesARestart() {
        items().bind("charm", custom());

        ItemRegistry reread = new ItemRegistry(plugin);

        assertTrue(reread.isBound("charm"), "items.yml remembered it");
        assertEquals(Material.NETHERITE_SWORD, reread.get("charm").getType());
    }

    @Test
    void tradesFollowTheBoundItem() {
        items().bind("charm", custom());

        ItemRef ref = ItemRef.parse("item:charm 1");
        assertNotNull(ref);
        assertTrue(ref.isCustom());
        assertEquals(Material.NETHERITE_SWORD, ref.resolve(items()).getType(),
                "a trade resolves the slot when it's used, not when it's read");
    }

    @Test
    void plainMaterialsStillWork() {
        ItemRef ref = ItemRef.parse("OAK_LOG 64");
        assertNotNull(ref);
        assertFalse(ref.isCustom());
        assertEquals(64, ref.resolve(items()).getAmount());
        assertTrue(ref.matches(items(), new ItemStack(Material.OAK_LOG, 1)),
                "amount is the price, not part of what counts as the item");
    }

    @Test
    void nonsenseIsRefusedRatherThanGuessed() {
        assertNull(ItemRef.parse("NOT_A_REAL_BLOCK 4"));
        assertNull(ItemRef.parse("item:"));
        assertNull(ItemRef.parse(""));
    }
}
