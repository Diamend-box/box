package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.ore.CompressedOre;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.ore.OreValues;
import org.bukkit.Material;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auto-compressor: ore-equivalent accounting, unlock gating, and the two
 * properties the whole design leans on — that compression never changes how
 * much ore a player is holding, and that no whitelisted item can be placed as
 * a block.
 */
class CompressorTest {

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

    /** Gives the player enough of a collection to clear every unlock tier. */
    private void maxCollections(PlayerMock player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        for (String id : List.of("coal", "iron", "copper", "redstone", "lapis",
                "gold", "quartz", "diamond", "emerald", "ancient_debris")) {
            profile.setCollected(id, 1_000_000);
        }
    }

    // ------------------------------------------------------------------
    // The rule everything else depends on
    // ------------------------------------------------------------------

    @Test
    void noWhitelistedOreCanBePlacedAsABlock() {
        // Placing a block takes the item out of the inventory, which would park
        // ore somewhere nothing counts it. ANCIENT_DEBRIS is the live trap.
        assertEquals(List.of(), plugin.ores().placeableEntries(),
                "whitelist must contain no placeable blocks");
    }

    @Test
    void compressingDoesNotChangeHowMuchOreIsHeld() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));

        OreValues ores = plugin.ores();
        long before = ores.carried(player, Material.DIAMOND);
        assertEquals(128, before);

        int units = compressor().compress(player);
        assertEquals(2, units, "two full stacks fold into two units");
        assertEquals(before, ores.carried(player, Material.DIAMOND),
                "compression must be invisible to anything counting ore");
    }

    @Test
    void aCompressedUnitIsWorthTheStackItCameFrom() {
        CompressedOre compressed = plugin.ores().compressed();
        ItemStack unit = compressed.create(Material.RAW_IRON, 64, 1);
        assertNotNull(unit);
        assertEquals(64, plugin.ores().unitValue(unit));
        assertEquals(64, plugin.ores().equivalents(unit));

        ItemStack five = compressed.create(Material.RAW_IRON, 64, 5);
        assertEquals(320, plugin.ores().equivalents(five));
    }

    @Test
    void rawOreIsStillWorthOne() {
        assertEquals(1, plugin.ores().unitValue(new ItemStack(Material.COAL, 1)));
        assertEquals(30, plugin.ores().equivalents(new ItemStack(Material.COAL, 30)));
    }

    @Test
    void nonOreIsWorthNothing() {
        assertEquals(0, plugin.ores().unitValue(new ItemStack(Material.COBBLESTONE, 64)));
        assertEquals(0, plugin.ores().equivalents(new ItemStack(Material.IRON_INGOT, 64)),
                "smelted output carries no ore-equivalent");
    }

    // ------------------------------------------------------------------
    // Compression behaviour
    // ------------------------------------------------------------------

    @Test
    void partialStacksAreLeftAlone() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().addItem(new ItemStack(Material.COAL, 63));

        assertEquals(0, compressor().compress(player), "63 is not a full stack");
        assertEquals(63, plugin.ores().carried(player, Material.COAL));
    }

    @Test
    void theRemainderStaysRaw() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().addItem(new ItemStack(Material.COAL, 64));
        player.getInventory().addItem(new ItemStack(Material.COAL, 10));

        assertEquals(1, compressor().compress(player));
        assertEquals(74, plugin.ores().carried(player, Material.COAL),
                "one unit plus the ten left over");
        assertEquals(10, compressor().rawCount(player.getInventory(), Material.COAL),
                "the remainder is still raw");
    }

    @Test
    void alreadyCompressedOreIsNotCompressedAgain() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        // 64 compressed units of coal is 4096 ore-equivalents, but only 64 items.
        player.getInventory().addItem(plugin.ores().compressed().create(Material.COAL, 64, 64));

        assertEquals(0, compressor().compress(player), "compressed stacks are not raw ore");
        assertEquals(4096, plugin.ores().carried(player, Material.COAL));
    }

    // ------------------------------------------------------------------
    // Gating
    // ------------------------------------------------------------------

    @Test
    void oreIsNotCompressedBeforeItsCollectionTier() {
        PlayerMock player = server.addPlayer();
        // No collection progress at all.
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 64));

        assertFalse(compressor().isUnlocked(player, Material.DIAMOND));
        assertEquals(0, compressor().compress(player));
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.DIAMOND));
    }

    @Test
    void reachingTheTierUnlocksThatOreOnly() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        // coal unlocks at tier 1, whose first threshold is 50.
        profile.setCollected("coal", 50);

        assertTrue(compressor().isUnlocked(player, Material.COAL));
        assertFalse(compressor().isUnlocked(player, Material.DIAMOND),
                "an unrelated collection stays locked");
    }

    @Test
    void theToggleStopsCompressionAndPersists() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().addItem(new ItemStack(Material.COAL, 64));
        plugin.profiles().get(player.getUniqueId()).markClean();

        assertFalse(compressor().toggle(player), "toggling from the default turns it off");
        assertEquals(0, compressor().compress(player));

        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        assertFalse(profile.isCompressorEnabled());
        assertTrue(profile.isDirty(), "the toggle must be saved");
    }

    // ------------------------------------------------------------------
    // Expanding
    // ------------------------------------------------------------------

    @Test
    void expandingReturnsTheRawStack() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        ItemStack unit = plugin.ores().compressed().create(Material.LAPIS_LAZULI, 64, 2);
        player.getInventory().setItemInMainHand(unit);

        assertEquals(1, compressor().expand(player, 1));
        assertEquals(128, plugin.ores().carried(player, Material.LAPIS_LAZULI),
                "expanding changes the form, not the amount");
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.LAPIS_LAZULI));
    }

    @Test
    void expandingHoldsOffTheCompressorSoTheOreStaysUsable() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().setItemInMainHand(
                plugin.ores().compressed().create(Material.LAPIS_LAZULI, 64, 1));

        assertEquals(1, compressor().expand(player, 1));
        assertTrue(compressor().inGrace(player), "a grace window opens on expand");
        assertEquals(0, compressor().compress(player),
                "ore expanded for an enchanting table must not be folded straight back");
    }

    @Test
    void expandingNeedsRoomForTheResult() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().clear();
        // Fill every slot but the hand with something that cannot merge.
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
        player.getInventory().setItemInMainHand(
                plugin.ores().compressed().create(Material.DIAMOND, 64, 1));

        assertEquals(0, compressor().expand(player, 1), "no room means no expand");
    }

    @Test
    void expandingSomethingUncompressedDoesNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 8));
        assertEquals(0, compressor().expand(player, 1));
    }
}
