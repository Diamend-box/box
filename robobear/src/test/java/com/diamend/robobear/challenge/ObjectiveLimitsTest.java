package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.mine.MineSurvey;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three ways a round could be handed out unwinnable or pointless.
 *
 * <p>All from one playtest: a safe and a greedy offer that were the same job,
 * an objective naming a material its mine doesn't contain, and "break 250
 * blocks" in a mine holding two stacks that refills every five minutes.
 */
class ObjectiveLimitsTest {

    private ServerMock server;
    private RoboBearPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RoboBearPlugin.class);
        server.addSimpleWorld("mines");
        plugin.getConfig().set("run.entry-item.item", "");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // The same job twice
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a round never offers the same job twice")
    void offersAreDistinct() {
        givenMines("quartz", "gold", "coal");

        ObjectiveGenerator generator = new ObjectiveGenerator(plugin);
        for (int round = 1; round <= 12; round++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                List<Objective> offers = generator.offer(round);
                Set<String> seen = new HashSet<>();
                for (Objective offer : offers) {
                    assertTrue(seen.add(shape(offer)),
                            "round " + round + " offered " + shape(offer) + " twice");
                }
            }
        }
    }

    @Test
    @DisplayName("when only one job exists, one offer is made rather than two of it")
    void oneJobMeansOneOffer() {
        givenMines("quartz");
        plugin.service().objectives().setEnabled(ObjectiveType.MINE_MATERIAL, false);
        plugin.service().objectives().setEnabled(ObjectiveType.KILL_MOBS, false);

        List<Objective> offers = new ObjectiveGenerator(plugin).offer(3);

        assertEquals(1, offers.size(),
                "two offers that differ only in price are not a choice");
        assertEquals(ObjectiveType.MINE_BLOCKS, offers.get(0).type());
    }

    /** Everything about an offer except how much and how well it pays. */
    private static String shape(Objective offer) {
        return offer.type() + ":" + offer.mineId() + ":" + offer.material();
    }

    // ------------------------------------------------------------------
    // Asking for more than the mine holds
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a mine holding two stacks is never asked for 250")
    void theCurveIsCappedByTheMine() {
        // 128 blocks of stock, refilling once per round, 60% claimable.
        assertEquals(75, ObjectiveGenerator.trim(128, 250, 1.0, 0.6, 10));
    }

    @Test
    @DisplayName("a modest ask is left alone")
    void underTheCeilingNothingChanges() {
        assertEquals(40, ObjectiveGenerator.trim(1000, 40, 1.0, 0.6, 10));
    }

    @Test
    @DisplayName("more resets per round means more can be asked for")
    void refillsRaiseTheCeiling() {
        assertEquals(250, ObjectiveGenerator.trim(128, 250, 4.0, 0.6, 10));
    }

    @Test
    @DisplayName("a mine too thin for even the smallest job is passed over")
    void thinMinesAreRefused() {
        assertEquals(0, ObjectiveGenerator.trim(12, 250, 1.0, 0.6, 10),
                "12 blocks at 60% is 7, below the floor of 10");
    }

    @Test
    @DisplayName("the trimmed amount never lands above the ceiling it came from")
    void roundingNeverExceedsTheCeiling() {
        for (long stock = 1; stock <= 4000; stock++) {
            long ceiling = Math.round(stock * 0.6);
            int amount = ObjectiveGenerator.trim(stock, Integer.MAX_VALUE, 1.0, 0.6, 1);
            assertTrue(amount <= ceiling,
                    "stock " + stock + " has a ceiling of " + ceiling + " but asked for " + amount);
        }
    }

    // ------------------------------------------------------------------
    // Scaling a sample up to a mine
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a survey scales its sample up to the whole mine")
    void surveyEstimatesStock() {
        MineSurvey survey = new MineSurvey(1000, 400, Map.of(
                Material.NETHER_QUARTZ_ORE, 100,
                Material.NETHERRACK, 300));

        assertEquals(1000, survey.estimate(Material.NETHER_QUARTZ_ORE, 10_000));
        assertEquals(4000, survey.estimateFilled(10_000));
        assertEquals(0, survey.estimate(Material.GOLD_ORE, 10_000),
                "a material the stride never landed on estimates as nothing");
    }

    @Test
    @DisplayName("a survey that read nothing claims nothing")
    void emptySurveysAreHonest() {
        assertTrue(MineSurvey.NOTHING.isEmpty());
        assertFalse(MineSurvey.NOTHING.foundAnything());
        assertEquals(0, MineSurvey.NOTHING.estimateFilled(10_000));
    }

    @Test
    @DisplayName("an all-air mine was read, but has nothing to give")
    void airIsReadButNotStock() {
        MineSurvey survey = new MineSurvey(500, 0, Map.of());

        assertFalse(survey.isEmpty(), "the blocks were read");
        assertFalse(survey.foundAnything(), "there was just nothing in them");
    }

    // ------------------------------------------------------------------
    // Not guessing at what a mine contains
    // ------------------------------------------------------------------

    @Test
    @DisplayName("with nothing readable anywhere, the config list is still used")
    void noDetectionAnywhereFallsBackToConfig() {
        plugin.getConfig().set("mines.sample-blocks", 0);
        givenMines("quartz");

        assertFalse(plugin.mines().hasDetectedMaterials(),
                "nothing can be read, so nothing should be claimed");
        assertEquals(plugin.mines().configuredMaterials(),
                plugin.mines().automaticMaterials("quartz"),
                "the config list is the only information there is");
    }

    private void givenMines(String... ids) {
        int x = 0;
        for (String id : ids) {
            plugin.mines().manualProvider().put(
                    MineRegion.between(id, id, "mines", x, 0, 0, x + 15, 15, 15));
            x += 100;
        }
        plugin.mines().refresh();
    }
}
