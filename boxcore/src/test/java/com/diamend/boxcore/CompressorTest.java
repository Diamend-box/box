package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.ore.CompressedOre;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.ore.OreValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void rawOreNeverMergesIntoACompressedStack() {
        // A compressed unit and a raw item are the same material and differ
        // only in metadata. Anything that merges on material alone would fold
        // raw ore into a compressed stack and multiply it by the ratio, so this
        // is the dupe the design has to be immune to rather than merely avoid.
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().clear();
        player.getInventory().setItem(0, plugin.ores().compressed().create(Material.COAL, 64, 1));
        player.getInventory().setItem(1, new ItemStack(Material.COAL, 32));

        assertEquals(96, plugin.ores().carried(player, Material.COAL),
                "one unit plus thirty-two raw");
        assertEquals(0, compressor().compress(player), "32 is not a full stack");
        assertEquals(96, plugin.ores().carried(player, Material.COAL),
                "the raw ore must not have been absorbed by the compressed stack");
        assertEquals(32, compressor().rawCount(player.getInventory(), Material.COAL));
    }

    // ------------------------------------------------------------------
    // Custom skins
    // ------------------------------------------------------------------

    @Test
    void aSkinnedUnitStillCountsAsItsSourceOre() {
        // The material is only how it renders. What it is worth, and what it is
        // worth it *in*, is stored on the item.
        CompressedOre compressed = plugin.ores().compressed();
        ItemStack unit = compressed.create(Material.COAL,
                new CompressedOre.Appearance(Material.PAPER, "<gold>Coal Briquette", null, 0, false),
                64, 2);
        assertNotNull(unit);
        assertEquals(Material.PAPER, unit.getType(), "it renders as the skin");
        assertEquals(Material.COAL, plugin.ores().oreKey(unit), "but it counts as coal");
        assertEquals(128, plugin.ores().equivalents(unit));
    }

    @Test
    void expandingASkinnedUnitHandsBackTheOreNotTheSkin() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().clear();
        player.getInventory().setItemInMainHand(plugin.ores().compressed().create(Material.COAL,
                new CompressedOre.Appearance(Material.PAPER, null, null, 0, false), 64, 1));

        assertEquals(1, compressor().expand(player, 1));
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.COAL),
                "expanding must give coal, not paper");
        assertEquals(0, compressor().rawCount(player.getInventory(), Material.PAPER));
    }

    @Test
    void twoOresSharingASkinDoNotMerge() {
        // Both skinned as paper at the same ratio, so a merge that only looked
        // at material and ratio would silently turn coal into diamonds.
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().clear();
        CompressedOre compressed = plugin.ores().compressed();
        CompressedOre.Appearance skin =
                new CompressedOre.Appearance(Material.PAPER, null, null, 0, false);
        player.getInventory().setItem(0, compressed.create(Material.COAL, skin, 64, 1));
        player.getInventory().setItem(1, compressed.create(Material.DIAMOND, skin, 64, 1));

        assertEquals(64, plugin.ores().carried(player, Material.COAL));
        assertEquals(64, plugin.ores().carried(player, Material.DIAMOND));
        assertFalse(compressed.sameKind(
                        player.getInventory().getItem(0), player.getInventory().getItem(1)),
                "same skin and ratio, different ore — never the same kind");
    }

    @Test
    void aPlaceableSkinIsRefused() {
        // Placing a block moves ore out of the inventory without anything
        // counting it, which is the route the custom item exists to close.
        assertEquals(List.of(), compressor().placeableSkins(),
                "no configured skin may be a placeable block");
    }

    @Test
    void oldUnitsWithoutASourceTagKeepTheirValue() {
        // Stacks written before the source ore was recorded separately meant
        // "the material I am"; they must not read as worthless.
        ItemStack legacy = new ItemStack(Material.COAL, 1);
        org.bukkit.inventory.meta.ItemMeta meta = legacy.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "compressed"),
                org.bukkit.persistence.PersistentDataType.INTEGER, 64);
        legacy.setItemMeta(meta);

        assertEquals(Material.COAL, plugin.ores().oreKey(legacy));
        assertEquals(64, plugin.ores().equivalents(legacy));
    }

    // ------------------------------------------------------------------
    // Colour codes in a custom skin
    // ------------------------------------------------------------------

    @Test
    void colourCodesInACustomNameAreParsedNotPrinted() {
        // Server owners write &f, not <white>. Both have to work, and neither
        // may end up rendered literally on the item.
        ItemStack unit = plugin.ores().compressed().create(Material.COAL,
                new CompressedOre.Appearance(null, "&f&lCoal Briquette", null, 0, false),
                64, 1);
        assertNotNull(unit);
        Component name = unit.getItemMeta().displayName();
        assertNotNull(name);
        assertEquals("Coal Briquette", plain(name), "the codes are consumed, not shown");
        assertTrue(hasColour(name, NamedTextColor.WHITE), "&f colours the name");
        assertTrue(hasDecoration(name, TextDecoration.BOLD), "&l bolds it");
    }

    @Test
    void hexColourCodesWorkInCustomLore() {
        ItemStack unit = plugin.ores().compressed().create(Material.COAL,
                new CompressedOre.Appearance(null, null, List.of("&#ff8800Pressed flat."), 0, false),
                64, 1);
        assertNotNull(unit);
        List<Component> lore = unit.getItemMeta().lore();
        assertNotNull(lore);
        assertEquals("Pressed flat.", plain(lore.get(0)));
        assertTrue(hasColour(lore.get(0), TextColor.color(0xff8800)), "&#rrggbb sets a hex colour");
    }

    @Test
    void colourCodesAndMiniMessageMixWithPlaceholders() {
        ItemStack unit = plugin.ores().compressed().create(Material.RAW_IRON,
                new CompressedOre.Appearance(null, "&6<bold><Ore> Block",
                        List.of("&7Worth <white><ratio></white> <ore>."), 0, false),
                64, 1);
        assertNotNull(unit);
        assertEquals("Iron Block", plain(unit.getItemMeta().displayName()));
        assertEquals("Worth 64 iron.", plain(unit.getItemMeta().lore().get(0)));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Whether a colour appears anywhere in the component tree. */
    private static boolean hasColour(Component component, TextColor colour) {
        if (colour.equals(component.color())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasColour(child, colour)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDecoration(Component component, TextDecoration decoration) {
        if (component.decoration(decoration) == TextDecoration.State.TRUE) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasDecoration(child, decoration)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The admin give command
    // ------------------------------------------------------------------

    @Test
    void oreNamesResolveByMaterialOrShortName() {
        OreValues ores = plugin.ores();
        assertEquals(Material.RAW_IRON, ores.matchOre("RAW_IRON"));
        assertEquals(Material.RAW_IRON, ores.matchOre("iron"), "the name players actually see");
        assertEquals(Material.LAPIS_LAZULI, ores.matchOre("lapis"));
        assertEquals(Material.NETHERITE_SCRAP, ores.matchOre("netherite_scrap"));
        assertNull(ores.matchOre("dirt"), "off the whitelist is not an ore");
        assertNull(ores.matchOre("not_a_material"));
    }

    @Test
    void mintedUnitsAreWorthTheSameAsMinedOnes() {
        // The give command is a shortcut to the item, never to a different item.
        ItemStack minted = compressor().createUnit(Material.COAL, 2);
        assertNotNull(minted);
        assertEquals(Material.COAL, plugin.ores().oreKey(minted));
        assertEquals(2L * compressor().ratio(), plugin.ores().equivalents(minted));
        assertNull(compressor().createUnit(Material.DIRT, 1), "only whitelisted ore");
    }

    @Test
    void theGiveCommandHandsOverRealCompressedOre() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.getInventory().clear();

        assertTrue(player.performCommand("box give diamond 3"));
        assertEquals(3L * compressor().ratio(),
                plugin.ores().carried(player, Material.DIAMOND),
                "three units, worth a stack apiece");
        assertEquals(0, compressor().rawCount(player.getInventory(), Material.DIAMOND),
                "compressed, not raw");
    }

    @Test
    void theGiveCommandNeedsPermission() {
        PlayerMock player = server.addPlayer();
        player.getInventory().clear();

        assertTrue(player.performCommand("box give diamond 3"));
        assertEquals(0, plugin.ores().carried(player, Material.DIAMOND),
                "no admin permission, no ore");
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
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.LAPIS_LAZULI),
                "one unit's worth comes back as raw ore");
        assertEquals(1, plugin.ores().compressed()
                        .ratio(player.getInventory().getItemInMainHand()) > 0
                ? player.getInventory().getItemInMainHand().getAmount() : 0,
                "the other unit is still compressed in hand");
        assertEquals(128, plugin.ores().carried(player, Material.LAPIS_LAZULI),
                "expanding changes the form, not the amount");
    }

    @Test
    void expandingHoldsOffTheCompressorSoTheOreStaysUsable() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().setItemInMainHand(
                plugin.ores().compressed().create(Material.LAPIS_LAZULI, 64, 1));

        assertEquals(1, compressor().expand(player, 1));
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.LAPIS_LAZULI),
                "expanding the last unit must still hand the ore over");
        assertTrue(compressor().inGrace(player), "a grace window opens on expand");
        assertEquals(0, compressor().compress(player),
                "ore expanded for an enchanting table must not be folded straight back");
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.LAPIS_LAZULI),
                "and it is still raw afterwards");
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
        // Two units need 128 free slots' worth; emptying the hand frees 64.
        player.getInventory().setItemInMainHand(
                plugin.ores().compressed().create(Material.DIAMOND, 64, 2));

        assertEquals(0, compressor().expand(player, 2), "no room means no expand");
        assertEquals(0, compressor().rawCount(player.getInventory(), Material.DIAMOND));
    }

    @Test
    void theLastUnitFitsBecauseItsOwnSlotComesFree() {
        PlayerMock player = server.addPlayer();
        maxCollections(player);
        player.getInventory().clear();
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
        player.getInventory().setItemInMainHand(
                plugin.ores().compressed().create(Material.DIAMOND, 64, 1));

        assertEquals(1, compressor().expand(player, 1),
                "the slot the unit vacates is exactly enough for the stack it becomes");
        assertEquals(64, compressor().rawCount(player.getInventory(), Material.DIAMOND));
    }

    @Test
    void expandingSomethingUncompressedDoesNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 8));
        assertEquals(0, compressor().expand(player, 1));
    }
}
