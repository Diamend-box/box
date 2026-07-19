package com.diamend.darksea.relic;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Relic life cycle, revival economics, and the active-set rules. */
class RelicTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void relicsDropDormantAndWakeInPlace() {
        for (Relic relic : Relic.values()) {
            ItemStack item = relic.createDormant();
            assertEquals(relic, Relic.of(item));
            assertFalse(Relic.isAwake(item), relic.id() + " should drop dormant");

            relic.wake(item);
            assertTrue(Relic.isAwake(item), relic.id() + " failed to wake");
            assertEquals(relic, Relic.of(item), relic.id() + " lost identity on waking");
        }
        assertFalse(Relic.isAwake(new ItemStack(Material.GOLD_NUGGET)));
        assertFalse(Relic.isAwake(null));
    }

    @Test
    void revivalCostsClimbWithTierAndTheVectorCostsMost() {
        int previousTier = 0;
        int previousCost = 0;
        for (Relic relic : Relic.values()) {
            assertTrue(relic.reviveCost() > 0);
            if (relic.tier() > previousTier) {
                assertTrue(relic.reviveCost() > previousCost,
                        relic.id() + " should cost more than lower-tier relics");
            }
            previousTier = relic.tier();
            previousCost = relic.reviveCost();
        }
        for (Relic relic : Relic.values()) {
            assertTrue(relic.reviveCost() <= Relic.MARIPHAGE_VECTOR.reviveCost(),
                    "the Core's relic should be the priciest to wake");
        }
    }

    @Test
    void everyRelicGrantsADistinctBoost() {
        Set<Relic.Boost> seen = EnumSet.noneOf(Relic.Boost.class);
        for (Relic relic : Relic.values()) {
            assertTrue(seen.add(relic.boost()), relic.id() + " duplicates a boost");
        }
    }

    /** A relic on a drinkable/consumable base item could be destroyed by a misclick. */
    @Test
    void noRelicLivesOnAConsumableMaterial() {
        for (Relic relic : Relic.values()) {
            Material material = relic.material();
            assertFalse(material.isEdible(), relic.id() + " is edible");
            assertFalse(material == Material.POTION || material == Material.HONEY_BOTTLE
                            || material == Material.MILK_BUCKET || material == Material.EXPERIENCE_BOTTLE,
                    relic.id() + " sits on a consumable/throwable base item");
        }
    }

    @Test
    void activeSetKeepsInventoryOrderDedupesAndHonorsTheCap() {
        List<Relic> awake = List.of(Relic.TRADE_COIN, Relic.TRADE_COIN,
                Relic.HARBOR_BELL, Relic.NAXOME_HEART);

        assertEquals(List.of(Relic.TRADE_COIN, Relic.HARBOR_BELL),
                RelicService.selectActive(awake, 2));
        assertEquals(List.of(Relic.TRADE_COIN, Relic.HARBOR_BELL, Relic.NAXOME_HEART),
                RelicService.selectActive(awake, 3));
        assertEquals(List.of(), RelicService.selectActive(awake, 0));
        assertEquals(List.of(), RelicService.selectActive(List.of(), 2));
    }
}
