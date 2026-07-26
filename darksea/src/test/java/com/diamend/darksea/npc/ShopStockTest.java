package com.diamend.darksea.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shop price list, asserted without a server. */
class ShopStockTest {

    @Test
    void everyNpcTypeHasABoard() {
        for (NpcType type : NpcType.values()) {
            List<ShopOffer> offers = ShopStock.offers(type, 0L);
            assertNotNull(offers, type + " has no board");
            assertFalse(offers.isEmpty(), type + " has an empty board");
        }
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
            assertEquals(type, NpcType.byId(type.id().toUpperCase(java.util.Locale.ROOT)));
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
        for (ShopOffer honest : ShopStock.salvage(1.0)) {
            ShopOffer gouged = find(ShopStock.blackMarket(3L), honest.itemId(),
                    ShopOffer.Kind.SELL);
            assertNotNull(gouged, "black market won't buy " + honest.itemId());
            assertTrue(gouged.price() > honest.price(),
                    honest.itemId() + ": " + gouged.price() + " should beat " + honest.price());
        }
    }

    @Test
    void blackMarketStockRotatesButIsStableWithinACycle() {
        List<ShopOffer> first = ShopStock.blackMarket(1L);
        assertEquals(first, ShopStock.blackMarket(1L), "same cycle must give the same shelf");

        // Over a run of cycles the shelf must actually change at least once.
        boolean changed = false;
        for (long cycle = 2L; cycle <= 12L && !changed; cycle++) {
            changed = !ShopStock.blackMarket(cycle).equals(first);
        }
        assertTrue(changed, "the black market never restocked");
    }

    @Test
    void blackMarketShowsTheRightNumberOfRotatingLines() {
        for (long cycle = 0L; cycle < 20L; cycle++) {
            List<ShopOffer> board = ShopStock.blackMarket(cycle);
            long buys = board.stream().filter(o -> o.kind() == ShopOffer.Kind.BUY).count();
            assertEquals(ShopStock.BLACK_MARKET_SLOTS, buys, "cycle " + cycle);
        }
    }

    /** Nothing that ends a run may be purchasable, at any price, from anyone. */
    @Test
    void nothingRunEndingIsForSale() {
        Set<String> forbidden = Set.of("undrowned_heart", "soulwake_compass",
                "boat_upgrade_token", "relic_mariphage_vector");
        for (NpcType type : NpcType.values()) {
            for (long cycle = 0L; cycle < 20L; cycle++) {
                for (ShopOffer offer : ShopStock.offers(type, cycle)) {
                    if (offer.kind() == ShopOffer.Kind.BUY) {
                        assertFalse(forbidden.contains(offer.itemId()),
                                type + " is selling " + offer.itemId());
                    }
                }
            }
        }
    }

    /** Selling a dormant relic must never fund waking another one. */
    @Test
    void artificerBuysRelicsBelowTheirWakingCost() {
        List<ShopOffer> board = ShopStock.artificer();
        assertFalse(board.isEmpty());
        for (ShopOffer offer : board) {
            assertEquals(ShopOffer.Kind.SELL, offer.kind(), "the artificer sells nothing");
            assertTrue(offer.itemId().startsWith("relic_"), offer.itemId());
        }
    }

    @Test
    void clueLadderOnlyGetsSteeper() {
        int previous = 0;
        for (int owned = 0; owned < ShopStock.MAX_CLUE_LEVEL; owned++) {
            int cost = ShopStock.clueCost(owned);
            assertTrue(cost > previous, "rumour " + (owned + 1) + " got cheaper");
            previous = cost;
        }
        assertEquals(ShopStock.clueCost(ShopStock.MAX_CLUE_LEVEL - 1),
                ShopStock.clueCost(99), "past the last rung the price plateaus");
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

    private static ShopOffer find(List<ShopOffer> board, String itemId, ShopOffer.Kind kind) {
        for (ShopOffer offer : board) {
            if (offer.itemId().equals(itemId) && offer.kind() == kind) {
                return offer;
            }
        }
        return null;
    }
}
