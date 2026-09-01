package com.diamend.darksea.relic;

import com.diamend.darksea.config.DarkSeaSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reliquary's rules: bag size, what moves in and out, and the crystal ladder. */
class ReliquaryMathTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static final String COIN = "relic_trade_coin";
    private static final String BELL = "relic_harbor_bell";
    private static final String HEART = "relic_naxome_heart";

    @Test
    void aBagStartsSmallAndGrowsToItsCeiling() {
        assertEquals(2, ReliquaryMath.slots(2, 6, 0));
        assertEquals(5, ReliquaryMath.slots(2, 6, 3));
        assertEquals(6, ReliquaryMath.slots(2, 6, 4));
        // Bought slots past the ceiling are simply capped, never lost.
        assertEquals(6, ReliquaryMath.slots(2, 6, 40));
    }

    @Test
    void onlyWhatIsOwnedAndFitsCountsAsWorn() {
        List<String> collection = List.of(COIN, BELL);
        // A relic that was sold or withdrawn stops counting even if it is
        // still listed as equipped on disk.
        assertEquals(List.of(COIN),
                ReliquaryMath.effective(collection, List.of(COIN, HEART), 2));
        // Shrinking the bag in config trims the overflow rather than breaking.
        assertEquals(List.of(COIN),
                ReliquaryMath.effective(collection, List.of(COIN, BELL), 1));
        assertEquals(List.of(), ReliquaryMath.effective(collection, List.of(COIN), 0));
    }

    @Test
    void aRelicIsFiledOnceHoweverManyYouBringBack() {
        List<String> once = ReliquaryMath.deposit(List.of(), COIN);
        assertEquals(List.of(COIN), once);
        assertEquals(List.of(COIN), ReliquaryMath.deposit(once, COIN));
    }

    @Test
    void aRelicCanOnlyBeWornIfItIsOwnedAndThereIsRoom() {
        List<String> collection = List.of(COIN, BELL);
        assertEquals(List.of(COIN), ReliquaryMath.equip(collection, List.of(), COIN, 2));
        assertNull(ReliquaryMath.equip(collection, List.of(), HEART, 2),
                "a relic you have never filed cannot be worn");
        assertNull(ReliquaryMath.equip(collection, List.of(COIN), COIN, 2),
                "wearing the same relic twice was never worth anything");
        assertNull(ReliquaryMath.equip(collection, List.of(COIN), BELL, 1),
                "a full bag refuses rather than silently dropping a relic");
    }

    @Test
    void takingARelicOffKeepsIt() {
        assertEquals(List.of(BELL), ReliquaryMath.unequip(List.of(COIN, BELL), COIN));
        assertEquals(List.of(COIN), ReliquaryMath.unequip(List.of(COIN), BELL));
    }

    @Test
    void theLadderRunningOutCapsTheBagAsSurelyAsMaxSlotsDoes() {
        assertTrue(ReliquaryMath.canUpgrade(2, 6, true));
        assertFalse(ReliquaryMath.canUpgrade(6, 6, true), "the ceiling holds");
        assertFalse(ReliquaryMath.canUpgrade(2, 6, false), "an unpriced rung is unbuyable");
    }

    @Test
    void theShippedLadderIsPaidForInCaveCrystalsAndNothingElse() throws Exception {
        DarkSeaSettings.RelicSettings relics = shipped().relics();
        assertFalse(relics.bagCosts().isEmpty(), "the shipped bag must be upgradeable");
        for (DarkSeaSettings.SlotCost cost : relics.bagCosts()) {
            assertTrue(List.of("emberglass", "voidbloom", "godspore").contains(cost.itemId()),
                    "the caves are the only source of bag slots: " + cost.itemId());
        }
        // Every rung priced means the ceiling is reachable — a ladder shorter
        // than the ceiling would advertise slots nobody can buy.
        assertEquals(relics.bagMaxSlots() - relics.bagStartSlots(), relics.bagCosts().size());
    }

    private DarkSeaSettings shipped() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return DarkSeaSettings.load(yaml, Logger.getLogger("ReliquaryMathTest"));
        }
    }

    @Test
    void aMalformedRungIsSkippedRatherThanFailingStartup() {
        List<DarkSeaSettings.SlotCost> costs = DarkSeaSettings.slotCosts(
                List.of("emberglass 12", "voidbloom", "godspore lots", "godspore 5"));
        assertEquals(2, costs.size());
        assertEquals("emberglass", costs.get(0).itemId());
        assertEquals(5, costs.get(1).amount());
    }
}
