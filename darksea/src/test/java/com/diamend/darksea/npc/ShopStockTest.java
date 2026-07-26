package com.diamend.darksea.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure half of the shop system: rotation, derivation and the clue ladder,
 * asserted against hand-built stock rather than the shipped file. What the
 * shipped shops.yml actually says is {@link ShopConfigTest}'s job.
 */
class ShopStockTest {

    /** A miniature outpost: two rotating lines shown out of a pool of four. */
    private static ShopStock stock() {
        return new ShopStock(
                Map.of(NpcType.REFUGEE_TRADER.id(), List.of(ShopOffer.buy("sea_salve", 1, 20)),
                        NpcType.BLACK_MARKET.id(), List.of()),
                List.of(ShopOffer.buy("hullpiercer_arrow", 1, 50),
                        ShopOffer.buy("chainshot_arrow", 8, 45),
                        ShopOffer.buy("harpoon_gun", 1, 150),
                        ShopOffer.buy("dark_sea_boat", 1, 55)),
                List.of(ShopOffer.sell("naxian_claw", 1, 8),
                        ShopOffer.sell("mariphage_stinger", 1, 45)),
                Set.of(NpcType.REFUGEE_TRADER.id(), NpcType.BLACK_MARKET.id()),
                1.6, 1.5, 2,
                List.of(40, 120, 300, 750));
    }

    @Test
    void offersRejectNonsense() {
        assertThrows(IllegalArgumentException.class, () -> ShopOffer.buy("x", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ShopOffer.buy("x", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ShopOffer.buy(" ", 1, 10));
    }

    @Test
    void typeIdsRoundTripAndAreUnique() {
        Set<String> ids = new HashSet<>();
        for (NpcType type : NpcType.values()) {
            assertTrue(ids.add(type.id()), "duplicate NPC id " + type.id());
            assertEquals(type, NpcType.byId(type.id()));
            assertEquals(type, NpcType.byId(type.id().toUpperCase(Locale.ROOT)));
        }
        assertNull(NpcType.byId("harbourmaster"));
        assertNull(NpcType.byId(null));
    }

    @Test
    void onlyTheArtificerWakesRelicsAndOnlyTheOldManTalks() {
        for (NpcType type : NpcType.values()) {
            assertEquals(type == NpcType.ARTIFICER, type.wakesRelics(), type.toString());
            assertEquals(type == NpcType.BOAT_EXPERT, type.sellsHeartClues(), type.toString());
            assertEquals(type == NpcType.BLACK_MARKET, type.rotatesStock(), type.toString());
        }
    }

    /** The whole point of walking the extra thirty blocks. */
    @Test
    void blackMarketPaysMoreForSalvageAndChargesMoreForStock() {
        ShopStock stock = stock();
        for (ShopOffer honest : stock.salvageAt(1.0)) {
            ShopOffer gouged = find(stock.offers(NpcType.BLACK_MARKET, 3L),
                    honest.itemId(), ShopOffer.Kind.SELL);
            assertNotEquals(null, gouged, "black market won't buy " + honest.itemId());
            assertTrue(gouged.price() > honest.price(),
                    honest.itemId() + ": " + gouged.price() + " should beat " + honest.price());
        }
        // And it charges more than the pool's face value for what it sells.
        for (ShopOffer sold : stock.offers(NpcType.BLACK_MARKET, 3L)) {
            if (sold.kind() != ShopOffer.Kind.BUY) {
                continue;
            }
            ShopOffer face = find(stock.rotatingPool(), sold.itemId(), ShopOffer.Kind.BUY);
            assertNotEquals(null, face, "sold something not in the pool: " + sold.itemId());
            assertTrue(sold.price() > face.price(), sold.itemId() + " wasn't marked up");
        }
    }

    @Test
    void rotationIsStableWithinACycleAndChangesAcrossThem() {
        ShopStock stock = stock();
        assertEquals(stock.rotate(1L), stock.rotate(1L), "same cycle, same shelf");

        boolean changed = false;
        for (long cycle = 2L; cycle <= 12L && !changed; cycle++) {
            changed = !stock.rotate(cycle).equals(stock.rotate(1L));
        }
        assertTrue(changed, "the black market never restocked");
    }

    @Test
    void rotationShowsExactlyItsSlotsAndNeverRepeatsALine() {
        ShopStock stock = stock();
        for (long cycle = 0L; cycle < 20L; cycle++) {
            List<ShopOffer> shelf = stock.rotate(cycle);
            assertEquals(stock.slots(), shelf.size(), "cycle " + cycle);
            assertEquals(shelf.size(), new HashSet<>(shelf).size(),
                    "cycle " + cycle + " listed a line twice");
        }
    }

    /** Fewer slots than pool entries is the design: scarcity is the feature. */
    @Test
    void rotationNeverExceedsThePool() {
        ShopStock thin = new ShopStock(Map.of(), List.of(ShopOffer.buy("sea_salve", 1, 20)),
                List.of(), Set.of(), 1.6, 1.5, 5, List.of(10));
        assertEquals(1, thin.rotate(0L).size());
    }

    @Test
    void onlySalvageTakersBuyLoot() {
        ShopStock stock = stock();
        for (NpcType type : NpcType.values()) {
            boolean buysLoot = stock.offers(type, 0L).stream()
                    .anyMatch(o -> o.kind() == ShopOffer.Kind.SELL);
            assertEquals(stock.takesSalvage().contains(type.id()), buysLoot, type.toString());
        }
    }

    @Test
    void clueLadderOnlyGetsSteeperAndPlateausPastTheLastRung() {
        ShopStock stock = stock();
        assertEquals(4, stock.maxClueLevel());
        int previous = 0;
        for (int owned = 0; owned < stock.maxClueLevel(); owned++) {
            int cost = stock.clueCost(owned);
            assertTrue(cost > previous, "rumour " + (owned + 1) + " got cheaper");
            previous = cost;
        }
        assertEquals(stock.clueCost(stock.maxClueLevel() - 1), stock.clueCost(99));
    }

    @Test
    void anEmptySnapshotIsHarmless() {
        ShopStock empty = ShopStock.empty();
        assertEquals(0, empty.maxClueLevel());
        assertEquals(0, empty.lineCount());
        for (NpcType type : NpcType.values()) {
            assertTrue(empty.offers(type, 7L).isEmpty(), type.toString());
        }
    }

    @Test
    void vanillaOffersAreTaggedAndUnwrapped() {
        ShopOffer bread = ShopOffer.buy(ShopOffer.VANILLA + "BREAD", 8, 10);
        assertTrue(bread.isVanilla());
        assertEquals("BREAD", bread.vanillaMaterial());

        ShopOffer salve = ShopOffer.buy("sea_salve", 1, 20);
        assertFalse(salve.isVanilla());
        assertNull(salve.vanillaMaterial());
    }

    @Test
    void scalingRoundsUpAndNeverReachesZero() {
        assertEquals(16, ShopOffer.buy("x", 1, 10).scaled(1.6).price());
        assertEquals(5, ShopOffer.buy("x", 1, 3).scaled(1.5).price());
        assertEquals(1, ShopOffer.buy("x", 1, 1).scaled(0.01).price());
        assertNotEquals(0, ShopOffer.buy("x", 1, 100).scaled(0.0).price());
    }

    // ------------------------------------------------------------------
    // Editing (what /ds shop does under the GUI)
    // ------------------------------------------------------------------

    @Test
    void pricesClampAtOneRatherThanThrowing() {
        ShopOffer offer = ShopOffer.buy("sea_salve", 1, 3);
        assertEquals(1, offer.withPrice(0).price());
        assertEquals(1, offer.withPrice(-50).price());
        assertEquals(99, offer.withPrice(99).price());
    }

    /** The editor cycles lot size with one click, so the top must wrap. */
    @Test
    void lotSizeWrapsAtBothEnds() {
        ShopOffer offer = ShopOffer.buy("sea_salve", 1, 10);
        assertEquals(2, offer.withAmount(2).amount());
        assertEquals(1, offer.withAmount(ShopOffer.MAX_AMOUNT + 1).amount());
        assertEquals(ShopOffer.MAX_AMOUNT, offer.withAmount(0).amount());
    }

    @Test
    void customIdsAreRecognisedAndUnwrapped() {
        ShopOffer custom = ShopOffer.buy(ShopOffer.CUSTOM + "AAAA", 1, 10);
        assertTrue(custom.isCustom());
        assertFalse(custom.isVanilla());
        assertEquals("AAAA", custom.customData());

        ShopOffer plain = ShopOffer.buy("sea_salve", 1, 10);
        assertFalse(plain.isCustom());
        assertNull(plain.customData());
    }

    /** An NPC's two shelves share one file list; editing one must not eat the other. */
    @Test
    void replacingOneNpcListLeavesEverythingElseAlone() {
        ShopStock stock = stock();
        List<ShopOffer> merged = List.of(
                ShopOffer.buy("dark_sea_boat", 1, 40),
                ShopOffer.sell("naxian_claw", 1, 8));
        ShopStock edited = stock.withFixed(NpcType.REFUGEE_TRADER.id(), merged);

        assertEquals(1, edited.fixedFor(NpcType.REFUGEE_TRADER.id(), ShopOffer.Kind.BUY).size());
        assertEquals(1, edited.fixedFor(NpcType.REFUGEE_TRADER.id(), ShopOffer.Kind.SELL).size());
        assertEquals(stock.rotatingPool(), edited.rotatingPool());
        assertEquals(stock.salvage(), edited.salvage());
        assertEquals(stock.clueCosts(), edited.clueCosts());
        // And the original snapshot is untouched — boards holding it stay valid.
        assertEquals(1, stock.fixedFor(NpcType.REFUGEE_TRADER.id()).size());
    }

    @Test
    void togglingSalvageTakersIsReversible() {
        ShopStock stock = stock();
        ShopStock off = stock.withSalvageTaker(NpcType.REFUGEE_TRADER.id(), false);
        assertFalse(off.takesSalvage().contains(NpcType.REFUGEE_TRADER.id()));
        assertTrue(off.offers(NpcType.REFUGEE_TRADER, 0L).stream()
                .noneMatch(o -> o.kind() == ShopOffer.Kind.SELL));

        ShopStock on = off.withSalvageTaker(NpcType.REFUGEE_TRADER.id(), true);
        assertEquals(stock.salvage(), on.salvageAt(1.0));
    }

    @Test
    void marketDialsClampToUsableValues() {
        ShopStock stock = stock().withMarketSettings(-5.0, 0.0, -3);
        assertTrue(stock.markup() > 0, "markup must stay positive");
        assertTrue(stock.salvageRate() > 0, "salvage rate must stay positive");
        assertEquals(0, stock.slots());
        assertTrue(stock.rotate(0L).isEmpty(), "zero slots means nothing rotates");
    }

    @Test
    void addingAndRemovingClueRungsChangesTheLadderLength() {
        ShopStock stock = stock().withClueCosts(List.of(50, 200));
        assertEquals(2, stock.maxClueLevel());
        assertEquals(50, stock.clueCost(0));
        assertEquals(200, stock.clueCost(1));
        assertEquals(200, stock.clueCost(5));

        assertEquals(0, stock.withClueCosts(List.of()).maxClueLevel());
    }

    private static ShopOffer find(List<ShopOffer> board, String itemId, ShopOffer.Kind kind) {
        for (ShopOffer offer : board) {
            if (offer.itemId().equals(itemId) && offer.kind() == kind) {
                return offer;
            }
        }
        return null;
    }
}
