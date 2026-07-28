package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.CollectionDetailMenu;
import com.diamend.boxcore.gui.CompressorMenu;
import com.diamend.boxcore.ore.CompressorModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player-facing half of the compressor: seeing which ores fold up, what
 * unlocks the rest, and turning it on and off.
 */
class CompressorUiTest {

    private static final int TOGGLE_SLOT = 4;

    private ServerMock server;
    private BoxCorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CompressorModule compressor() {
        CompressorModule module = plugin.compressor();
        assertNotNull(module, "compressor module should be active");
        return module;
    }

    /** Gives the player enough of every collection to clear all unlock tiers. */
    private void maxCollections(PlayerMock player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        for (String id : List.of("coal", "iron", "copper", "redstone", "lapis",
                "gold", "quartz", "diamond", "emerald", "ancient_debris")) {
            profile.setCollected(id, 1_000_000);
        }
    }

    private Inventory open(PlayerMock player) {
        new CompressorMenu(plugin, compressor()).open(player);
        return player.getOpenInventory().getTopInventory();
    }

    /** The slot showing one ore, or -1. Icons render as the ore itself. */
    private int slotOf(Inventory menu, Material ore) {
        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (item != null && item.getType() == ore) {
                return slot;
            }
        }
        return -1;
    }

    /** An icon's name and lore as one plain string, for reading assertions. */
    private String words(ItemStack item) {
        assertNotNull(item, "expected an icon here");
        StringBuilder text = new StringBuilder();
        if (item.getItemMeta() != null && item.getItemMeta().displayName() != null) {
            text.append(plain(item.getItemMeta().displayName())).append('\n');
        }
        List<Component> lore = item.getItemMeta() == null ? null : item.getItemMeta().lore();
        if (lore != null) {
            for (Component line : lore) {
                text.append(plain(line)).append('\n');
            }
        }
        return text.toString();
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // ------------------------------------------------------------------
    // What the page shows
    // ------------------------------------------------------------------

    @Test
    void everyConfiguredOreIsOnThePage() {
        PlayerMock player = server.addPlayer();
        Inventory menu = open(player);

        for (Material ore : compressor().unlocks().keySet()) {
            assertNotEquals(-1, slotOf(menu, ore),
                    ore + " is compressible but isn't shown anywhere");
        }
    }

    @Test
    void aLockedOreSaysWhatWouldUnlockIt() {
        PlayerMock player = server.addPlayer();
        Inventory menu = open(player);

        String text = words(menu.getItem(slotOf(menu, Material.COAL)));
        assertTrue(text.contains("Locked"), text);
        assertTrue(text.contains("tier"), "the gate is named, not just refused: " + text);
        assertTrue(text.contains("Still to go"),
                "and how far off it is, which is the whole point: " + text);
    }

    @Test
    void anUnlockedOreSaysSoInstead() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        Inventory menu = open(player);

        String text = words(menu.getItem(slotOf(menu, Material.COAL)));
        assertTrue(text.contains("Unlocked"), text);
        assertFalse(text.contains("Still to go"), "nothing is left to go: " + text);
    }

    @Test
    void thePageCountsWhatYouAreCarrying() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().setItem(0, new ItemStack(Material.COAL, 5));
        ItemStack unit = compressor().createUnit(Material.COAL, 1);
        assertNotNull(unit);
        player.getInventory().setItem(1, unit);

        Inventory menu = open(player);
        String text = words(menu.getItem(slotOf(menu, Material.COAL)));

        assertTrue(text.contains("Carrying: 5"), text);
        assertTrue(text.contains("Compressed: 1"), text);
    }

    @Test
    void theToggleIconMatchesTheSetting() {
        PlayerMock player = server.addPlayer();
        Inventory menu = open(player);

        String text = words(menu.getItem(TOGGLE_SLOT));
        assertEquals(compressor().isEnabledFor(player), text.contains("ON"),
                "the toggle icon and the actual setting have to agree: " + text);
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    @Test
    void clickingTheToggleFlipsItAndRedraws() {
        PlayerMock player = server.addPlayer();
        boolean before = compressor().isEnabledFor(player);
        Inventory menu = open(player);

        player.simulateInventoryClick(TOGGLE_SLOT);

        assertNotEquals(before, compressor().isEnabledFor(player), "the setting flipped");
        String text = words(menu.getItem(TOGGLE_SLOT));
        assertTrue(text.contains(compressor().isEnabledFor(player) ? "ON" : "OFF"),
                "and the icon under the cursor changed with it: " + text);
    }

    @Test
    void clickingALockedOreOpensTheCollectionGatingIt() {
        PlayerMock player = server.addPlayer();
        Inventory menu = open(player);

        player.simulateInventoryClick(slotOf(menu, Material.COAL));
        server.getScheduler().performTicks(3); // the menu opens on the next tick

        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                        instanceof CollectionDetailMenu,
                "'needs Coal tier 1' is only useful next to what Coal tier 1 costs");
    }

    @Test
    void clickingAnUnlockedOreLeavesYouWhereYouAre() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        Inventory menu = open(player);

        player.simulateInventoryClick(slotOf(menu, Material.COAL));
        server.getScheduler().performTicks(3);

        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                        instanceof CompressorMenu,
                "there is nothing to go and look at once it's unlocked");
    }

    @Test
    void clickingTheDecorationDoesNothing() {
        PlayerMock player = server.addPlayer();
        boolean before = compressor().isEnabledFor(player);
        open(player);

        player.simulateInventoryClick(0);
        player.simulateInventoryClick(49);

        assertEquals(before, compressor().isEnabledFor(player));
    }

    // ------------------------------------------------------------------
    // The command
    // ------------------------------------------------------------------

    @Test
    void theBareCommandOpensTheMenu() {
        PlayerMock player = server.addPlayer();

        assertTrue(player.performCommand("box compress"));

        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof CompressorMenu);
    }

    @Test
    void theCommandStillTogglesDirectly() {
        PlayerMock player = server.addPlayer();

        assertTrue(player.performCommand("box compress off"));
        assertFalse(compressor().isEnabledFor(player));

        assertTrue(player.performCommand("box compress on"));
        assertTrue(compressor().isEnabledFor(player));
    }

    // ------------------------------------------------------------------
    // What the placeholders read
    // ------------------------------------------------------------------

    @Test
    void theUnlockedCountCanBeReadFromAProfileAlone() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        assertEquals(0, compressor().unlockedCount(profile),
                "a fresh player has unlocked nothing");

        maxCollections(player);
        assertEquals(compressor().unlocks().size(), compressor().unlockedCount(profile),
                "and a maxed one has unlocked everything");
        assertEquals(compressor().unlockedFor(player).size(), compressor().unlockedCount(profile),
                "asking by profile and asking by player have to give the same answer");
    }
}
