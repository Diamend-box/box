package com.diamend.darksea.npc;

import org.mockbukkit.mockbukkit.MockBukkit;
import com.diamend.darksea.relic.Relic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    private static int priceOf(List<ShopOffer> board, String itemId) {
        for (ShopOffer offer : board) {
            if (offer.kind() == ShopOffer.Kind.BUY && offer.itemId().equals(itemId)) {
                return offer.price();
            }
        }
        return -1;
    }
}
