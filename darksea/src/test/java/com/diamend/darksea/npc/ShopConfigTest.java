package com.diamend.darksea.npc;

import org.mockbukkit.mockbukkit.MockBukkit;
import com.diamend.darksea.relic.Relic;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped shops.yml must parse cleanly and still obey the two rules that
 * keep the economy honest — a typo in a price is a config problem, but a
 * purchasable Undrowned Heart is a design bug, and neither should reach a
 * release.
 */
class ShopConfigTest {

    private final List<String> warnings = new ArrayList<>();
    private Logger log;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        log = Logger.getLogger("ShopConfigTest-" + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ShopStock loadShipped() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/shops.yml")) {
            assertNotNull(in, "default shops.yml missing from resources");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return ShopConfig.load(yaml, log);
        }
    }

    @Test
    void shippedFileParsesWithoutASingleSkippedLine() throws Exception {
        ShopStock stock = loadShipped();
        assertTrue(warnings.isEmpty(), "shops.yml produced warnings: " + warnings);
        assertTrue(stock.lineCount() > 20, "suspiciously few shop lines: " + stock.lineCount());
        assertEquals(1.6, stock.markup(), 1e-9);
        assertEquals(1.5, stock.salvageRate(), 1e-9);
        assertEquals(4, stock.maxClueLevel());
    }

    @Test
    void everyNpcTypeEndsUpWithSomethingToSay() throws Exception {
        ShopStock stock = loadShipped();
        for (NpcType type : NpcType.values()) {
            boolean hasBoard = !stock.offers(type, 0L).isEmpty();
            boolean hasButton = type.wakesRelics() || type.sellsHeartClues();
            assertTrue(hasBoard || hasButton, type + " has a blank board");
        }
    }

    /** Rule one: shops make a run cheaper to attempt, never skippable. */
    @Test
    void nothingRunEndingIsForSaleInAnyRotation() throws Exception {
        ShopStock stock = loadShipped();
        Set<String> forbidden = Set.of("undrowned_heart", "soulwake_compass",
                "boat_upgrade_token", "relic_mariphage_vector");
        for (NpcType type : NpcType.values()) {
            for (long cycle = 0L; cycle < 25L; cycle++) {
                for (ShopOffer offer : stock.offers(type, cycle)) {
                    if (offer.kind() == ShopOffer.Kind.BUY) {
                        assertFalse(forbidden.contains(offer.itemId()),
                                type + " is selling " + offer.itemId());
                    }
                }
            }
        }
        // No relic of any kind is purchasable, woken or otherwise.
        for (NpcType type : NpcType.values()) {
            for (ShopOffer offer : stock.offers(type, 0L)) {
                assertFalse(offer.kind() == ShopOffer.Kind.BUY
                                && offer.itemId().startsWith("relic_"),
                        type + " is selling " + offer.itemId());
            }
        }
    }

    /** Rule two: selling a relic must never fund waking a different one. */
    @Test
    void artificerPaysLessForARelicThanWakingOneCosts() throws Exception {
        ShopStock stock = loadShipped();
        List<ShopOffer> board = stock.offers(NpcType.ARTIFICER, 0L);
        assertFalse(board.isEmpty(), "the artificer buys nothing");
        for (ShopOffer offer : board) {
            assertEquals(ShopOffer.Kind.SELL, offer.kind(), "the artificer sells nothing");
            Relic relic = Relic.byId(offer.itemId());
            assertNotNull(relic, "not a relic id: " + offer.itemId());
            assertTrue(offer.price() < relic.reviveCost(),
                    relic.id() + ": pays " + offer.price()
                            + " but waking costs " + relic.reviveCost());
        }
        assertEquals(Relic.values().length, board.size(),
                "every relic should have a standing offer");
    }

    @Test
    void theRefugeeStaysTheCheapPlaceToBuyAHull() throws Exception {
        ShopStock stock = loadShipped();
        int refugee = priceOf(stock.offers(NpcType.REFUGEE_TRADER, 0L), "dark_sea_boat");
        int expert = priceOf(stock.offers(NpcType.BOAT_EXPERT, 0L), "dark_sea_boat");
        assertTrue(refugee > 0 && expert > 0, "somebody should sell hulls");
        assertTrue(expert <= refugee, "the boat expert should not be dearer on hulls");
    }

    /** Malformed lines cost you the line, not the outpost. */
    @Test
    void badLinesAreSkippedAndNamed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("shops.refugee_trader.buy", List.of(
                java.util.Map.of("item", "sea_salve", "price", 20),
                java.util.Map.of("item", "sea_salve", "price", 0),
                java.util.Map.of("price", 12)));
        ShopStock stock = ShopConfig.load(yaml, log);

        assertEquals(1, stock.fixedFor(NpcType.REFUGEE_TRADER.id()).size());
        // Two bad lines, plus the missing black-market and salvage sections.
        assertEquals(4, warnings.size(), "unexpected complaints: " + warnings);
    }

    @Test
    void anEmptyFileDegradesToBlankBoardsRatherThanBlowingUp() {
        ShopStock stock = ShopConfig.load(new YamlConfiguration(), log);
        assertEquals(0, stock.lineCount());
        assertFalse(warnings.isEmpty(), "an empty shops.yml should be complained about");
    }

    // ------------------------------------------------------------------
    // Writing back (what /ds shop does after every click)
    // ------------------------------------------------------------------

    /**
     * The editor writes shops.yml on every change, so a save that loses or
     * mangles anything would silently corrupt the economy one click at a time.
     */
    @Test
    void aSavedSnapshotReloadsIdentically(@TempDir Path dir) throws Exception {
        ShopStock original = loadShipped();
        File file = dir.resolve("shops.yml").toFile();
        ShopConfig.save(original, file, log);

        warnings.clear();
        ShopStock reloaded = ShopConfig.load(
                YamlConfiguration.loadConfiguration(file), log);
        assertTrue(warnings.isEmpty(), "the round trip complained: " + warnings);

        assertEquals(original.lineCount(), reloaded.lineCount());
        assertEquals(original.markup(), reloaded.markup(), 1e-9);
        assertEquals(original.salvageRate(), reloaded.salvageRate(), 1e-9);
        assertEquals(original.slots(), reloaded.slots());
        assertEquals(original.clueCosts(), reloaded.clueCosts());
        assertEquals(original.rotatingPool(), reloaded.rotatingPool());
        assertEquals(original.salvage(), reloaded.salvage());
        assertEquals(original.takesSalvage(), reloaded.takesSalvage());
        for (NpcType type : NpcType.values()) {
            assertEquals(original.fixedFor(type.id()), reloaded.fixedFor(type.id()),
                    type + " changed across a save");
            // The boards themselves, not just the file, must come back the same.
            assertEquals(original.offers(type, 4L), reloaded.offers(type, 4L), type.toString());
        }
    }

    /** An edited line — the common case — must survive the write. */
    @Test
    void editsSurviveTheRoundTrip(@TempDir Path dir) throws Exception {
        ShopStock edited = loadShipped()
                .withFixed(NpcType.APOTHECARY.id(),
                        List.of(ShopOffer.buy("sea_salve", 7, 999)))
                .withMarketSettings(2.5, 1.1, 3)
                .withClueCosts(List.of(5, 5000))
                .withSalvageTaker(NpcType.APOTHECARY.id(), true);

        File file = dir.resolve("shops.yml").toFile();
        ShopConfig.save(edited, file, log);
        ShopStock reloaded = ShopConfig.load(YamlConfiguration.loadConfiguration(file), log);

        List<ShopOffer> board = reloaded.fixedFor(NpcType.APOTHECARY.id());
        assertEquals(1, board.size());
        assertEquals(7, board.get(0).amount());
        assertEquals(999, board.get(0).price());
        assertEquals(2.5, reloaded.markup(), 1e-9);
        assertEquals(3, reloaded.slots());
        assertEquals(List.of(5, 5000), reloaded.clueCosts());
        assertTrue(reloaded.takesSalvage().contains(NpcType.APOTHECARY.id()));
    }

    /**
     * The whole reason the editor exists: an item with real item data becomes a
     * base64 snapshot that survives a save, a reload, and decoding back into
     * the same stack.
     */
    @Test
    void customItemSnapshotsSurviveTheRoundTrip(@TempDir Path dir) throws Exception {
        ItemStack fancy = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = fancy.getItemMeta();
        meta.displayName(Component.text("Wyatt's Custom Blade"));
        meta.lore(List.of(Component.text("not a DarkSea item at all")));
        fancy.setItemMeta(meta);

        String id = ShopItems.encode(fancy, log);
        assertNotNull(id);
        assertTrue(id.startsWith(ShopOffer.CUSTOM), "expected a snapshot, got " + id);

        ShopStock stock = loadShipped().withFixed(NpcType.BOAT_EXPERT.id(),
                List.of(new ShopOffer(id, 1, 500, ShopOffer.Kind.BUY)));
        File file = dir.resolve("shops.yml").toFile();
        ShopConfig.save(stock, file, log);
        ShopStock reloaded = ShopConfig.load(YamlConfiguration.loadConfiguration(file), log);

        ShopOffer offer = reloaded.fixedFor(NpcType.BOAT_EXPERT.id()).get(0);
        assertTrue(offer.isCustom());
        ItemStack decoded = ShopItems.resolve(offer, log);
        assertNotNull(decoded, "the snapshot did not decode");
        assertTrue(decoded.isSimilar(fancy), "the decoded item is not the one we saved");
        assertTrue(ShopItems.matches(fancy, offer, log), "a player's copy should match the line");
    }

    /**
     * Plain items must keep a readable id rather than becoming base64 noise —
     * a shops.yml where every loaf of bread is a wall of encoded bytes is not
     * a file anyone can hand-edit afterwards.
     */
    @Test
    void plainItemsEncodeToReadableIds() {
        for (Material material : List.of(Material.BREAD, Material.COOKED_COD,
                Material.GOLDEN_CARROT, Material.DIAMOND_SWORD, Material.OAK_BOAT)) {
            assertEquals("vanilla:" + material.name(),
                    ShopItems.encode(new ItemStack(material), log),
                    material + " should encode readably");
        }
        // Amount is a property of the line, not the item, so it changes nothing.
        assertEquals("vanilla:BREAD", ShopItems.encode(new ItemStack(Material.BREAD, 17), log));
        assertNull(ShopItems.encode(null, log));
        assertNull(ShopItems.encode(new ItemStack(Material.AIR), log));
    }

    /** ...and only a stack carrying something extra takes the snapshot path. */
    @Test
    void onlyItemsWithRealDataBecomeSnapshots() {
        ItemStack plain = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(ShopItems.isPlain(plain));

        ItemStack named = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = named.getItemMeta();
        meta.displayName(Component.text("Named"));
        named.setItemMeta(meta);
        assertFalse(ShopItems.isPlain(named));
        assertTrue(ShopItems.encode(named, log).startsWith(ShopOffer.CUSTOM));

        // A plain-material line must not accept the renamed one.
        ShopOffer line = ShopOffer.sell("vanilla:DIAMOND_SWORD", 1, 10);
        assertTrue(ShopItems.matches(plain, line, log));
        assertFalse(ShopItems.matches(named, line, log));
    }

    /** A corrupt snapshot should degrade to a blank tile, not take the board down. */
    @Test
    void anUnreadableSnapshotResolvesToNull() {
        ShopOffer broken = ShopOffer.buy(ShopOffer.CUSTOM + "not-base64-at-all!!", 1, 10);
        assertNull(ShopItems.resolve(broken, log));
        assertFalse(warnings.isEmpty(), "a corrupt snapshot should be logged");
    }

    private static int priceOf(List<ShopOffer> board, String itemId) {
        for (ShopOffer offer : board) {
            if (offer.kind() == ShopOffer.Kind.BUY && offer.itemId().equals(itemId)) {
                return offer.price();
            }
        }
        return -1;
    }
}
