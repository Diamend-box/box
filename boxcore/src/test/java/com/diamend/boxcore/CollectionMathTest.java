package com.diamend.boxcore;

import com.diamend.boxcore.collection.CollectionTier;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.util.Registries;
import com.diamend.boxcore.util.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier thresholds, progress maths and the display helpers — all pure, so they
 * run without a server.
 */
class CollectionMathTest {

    private ItemCollection cobblestone() {
        ItemCollection collection = new ItemCollection("cobblestone");
        collection.getTiers().add(new CollectionTier(50, 1));
        collection.getTiers().add(new CollectionTier(250, 1));
        collection.getTiers().add(new CollectionTier(1000, 2));
        return collection;
    }

    @Test
    void tierIsTheHighestThresholdReached() {
        ItemCollection collection = cobblestone();
        assertEquals(0, collection.tierFor(0));
        assertEquals(0, collection.tierFor(49));
        assertEquals(1, collection.tierFor(50));
        assertEquals(1, collection.tierFor(249));
        assertEquals(2, collection.tierFor(250));
        assertEquals(3, collection.tierFor(999_999), "past the last tier it stays maxed");
    }

    @Test
    void oneBigGainCrossesEveryTierItPassed() {
        ItemCollection collection = cobblestone();
        // A shulker box of loot, or an admin setting the total outright: the
        // payout loop must cover tiers 1 through 3, not just the last one.
        int paidTiers = collection.tierFor(1000) - collection.tierFor(0);
        assertEquals(3, paidTiers);
    }

    @Test
    void nextTierAndRemainingTrackProgress() {
        ItemCollection collection = cobblestone();
        assertNotNull(collection.nextTier(0));
        assertEquals(50, collection.nextTier(0).amount());
        assertEquals(10, collection.remainingFor(40));
        assertNull(collection.nextTier(1000), "a maxed collection has no next tier");
        assertEquals(0, collection.remainingFor(5000));
    }

    @Test
    void progressIsMeasuredBetweenAdjacentTiers() {
        ItemCollection collection = cobblestone();
        assertEquals(0.0, collection.progress(0), 1.0e-9);
        assertEquals(0.5, collection.progress(25), 1.0e-9);
        // Halfway from tier 1 (50) to tier 2 (250) is 150.
        assertEquals(0.5, collection.progress(150), 1.0e-9);
        assertEquals(1.0, collection.progress(10_000), 1.0e-9);
    }

    @Test
    void attributeNamesNormaliseAcrossVersions() {
        // The same attribute, spelled the four ways servers and configs have
        // written it since 1.20.
        assertEquals("max_health", Registries.normalize("GENERIC_MAX_HEALTH"));
        assertEquals("max_health", Registries.normalize("generic.max_health"));
        assertEquals("max_health", Registries.normalize("minecraft:max_health"));
        assertEquals("max_health", Registries.normalize("Max-Health"));
        assertEquals("", Registries.normalize(null));
    }

    @Test
    void romanNumeralsCoverTheLevelsWeUse() {
        assertEquals("I", Text.roman(1));
        assertEquals("IV", Text.roman(4));
        assertEquals("IX", Text.roman(9));
        assertEquals("XL", Text.roman(40));
        assertEquals("41", Text.roman(41), "outside the range it falls back to digits");
    }

    @Test
    void amountsRenderWithSignAndUnit() {
        assertEquals("+2", Text.amount(2.0, false));
        assertEquals("-1.5", Text.amount(-1.5, false));
        assertEquals("+15%", Text.amount(0.15, true));
        assertEquals("-10%", Text.amount(-0.1, true));
    }

    @Test
    void progressBarFillsProportionally() {
        assertEquals("##--", Text.progressBar(0.5, 4, "#", "-"));
        assertEquals("----", Text.progressBar(0.0, 4, "#", "-"));
        assertEquals("####", Text.progressBar(1.5, 4, "#", "-"), "over 100% clamps");
    }

    @Test
    void prettifyMakesConfigNamesReadable() {
        assertEquals("Max Health", Text.prettify("max_health"));
        assertEquals("Ancient Debris", Text.prettify("ANCIENT_DEBRIS"));
        assertTrue(Text.prettify("").isEmpty());
    }
}
