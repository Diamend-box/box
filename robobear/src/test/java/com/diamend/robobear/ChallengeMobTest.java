package com.diamend.robobear;

import com.diamend.robobear.challenge.Objective;
import com.diamend.robobear.challenge.ObjectiveGenerator;
import com.diamend.robobear.challenge.ObjectiveType;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.mob.ChallengeMobs;
import com.diamend.robobear.mob.MobArchetype;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mobs the challenge sends, and the arithmetic that decides how many.
 *
 * <p>Spawning itself is left to a real server — what is worth pinning down here
 * is that the roster reads correctly, that the ladder escalates, and above all
 * that a kill objective is never sized past what the challenge can actually
 * deliver. That last one is the same fault as "break 250 blocks in a mine
 * holding two stacks", wearing a different hat.
 */
class ChallengeMobTest {

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
    // The roster
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the shipped roster loads")
    void rosterLoads() {
        ChallengeMobs mobs = plugin.mobs();

        assertTrue(mobs.enabled(), "challenge mobs should be on out of the box");
        assertFalse(mobs.roster().isEmpty());
        assertNotNull(find(mobs, "swarf-mite"));
        assertEquals(EntityType.SILVERFISH, find(mobs, "swarf-mite").type());
    }

    @Test
    @DisplayName("nothing in the roster edits the mine")
    void nothingInTheRosterGriefs() {
        for (MobArchetype archetype : plugin.mobs().roster()) {
            assertFalse(archetype.type() == EntityType.CREEPER
                            || archetype.type() == EntityType.ENDERMAN,
                    archetype.id() + " rearranges the mine it spawns in");
        }
    }

    @Test
    @DisplayName("later rounds unlock things earlier rounds never see")
    void theLadderEscalates() {
        int earliest = 0;
        int late = 0;
        for (MobArchetype archetype : plugin.mobs().roster()) {
            if (archetype.availableAt(1)) {
                earliest++;
            }
            if (archetype.availableAt(9)) {
                late++;
            }
        }
        assertTrue(earliest > 0, "round one has to be able to send something");
        assertTrue(late > earliest, "round nine should have more to draw on than round one");
    }

    @Test
    @DisplayName("the elite is never part of the ordinary population")
    void elitesAreNotRolled() {
        MobArchetype foreman = find(plugin.mobs(), "foreman");

        assertNotNull(foreman);
        assertTrue(foreman.elite());
        for (int round = 1; round <= 30; round++) {
            assertFalse(foreman.availableAt(round),
                    "the Foreman is a milestone, not a random spawn");
        }
    }

    @Test
    @DisplayName("an unknown entity type is skipped rather than breaking the roster")
    void badEntriesAreSkipped() {
        plugin.getConfig().set("mobs.roster.nonsense.type", "NOT_A_REAL_MOB");
        plugin.getConfig().set("mobs.roster.nonsense.weight", 5);

        plugin.mobs().load();

        assertTrue(plugin.mobs().enabled(), "one bad entry must not take the rest with it");
        for (MobArchetype archetype : plugin.mobs().roster()) {
            assertFalse(archetype.id().equals("nonsense"));
        }
    }

    @Test
    @DisplayName("an empty roster counts as switched off")
    void emptyRosterIsOff() {
        plugin.getConfig().set("mobs.roster", null);
        plugin.mobs().load();

        assertFalse(plugin.mobs().enabled());
        assertEquals(-1, plugin.mobs().supplyPerRound(5),
                "nothing to send means no supply to size a kill job against");
    }

    // ------------------------------------------------------------------
    // How many, how fast
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the wave grows with the round, up to the cap")
    void populationGrowsAndStops() {
        ChallengeMobs mobs = plugin.mobs();

        assertEquals(2, mobs.population(1, false));
        assertEquals(6, mobs.population(9, false));
        assertEquals(8, mobs.population(50, false), "the cap is a cap");
    }

    @Test
    @DisplayName("a kill round sends more of them, and sooner")
    void killRoundsAreHeavier() {
        ChallengeMobs mobs = plugin.mobs();

        assertTrue(mobs.population(5, true) > mobs.population(5, false));
        assertTrue(mobs.reinforceSeconds(5, true) < mobs.reinforceSeconds(5, false));
    }

    @Test
    @DisplayName("reinforcements speed up with the round but never below the floor")
    void reinforcementsHaveAFloor() {
        ChallengeMobs mobs = plugin.mobs();
        int floor = plugin.getConfig().getInt("mobs.reinforce.minimum-seconds", 3);

        assertTrue(mobs.reinforceSeconds(9, false) < mobs.reinforceSeconds(1, false));
        for (int round = 1; round <= 60; round++) {
            assertTrue(mobs.reinforceSeconds(round, true) >= floor,
                    "round " + round + " went under the floor");
        }
    }

    // ------------------------------------------------------------------
    // Kill objectives sized to the supply
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a kill round never asks for more than the challenge can send")
    void killObjectivesFitTheSupply() {
        onlyKillObjectives();
        ObjectiveGenerator generator = new ObjectiveGenerator(plugin);

        for (int round = 1; round <= 15; round++) {
            long supply = plugin.mobs().supplyPerRound(round);
            double share = plugin.getConfig().getDouble("objectives.limits.mob-fraction", 0.6);
            long ceiling = Math.round(supply * share);

            for (Objective offer : generator.offer(round)) {
                assertEquals(ObjectiveType.KILL_MOBS, offer.type());
                assertTrue(offer.amount() <= ceiling,
                        "round " + round + " asked for " + offer.amount()
                                + " but the challenge can only send about " + ceiling);
            }
        }
    }

    @Test
    @DisplayName("without the clamp, round nine asks for well past what it can send")
    void theClampIsLoadBearing() {
        // base 10, growth 1.22, greedy difficulty 1.9 — the curve on its own.
        double raw = 10 * Math.pow(1.22, 8) * 1.9;
        long supply = plugin.mobs().supplyPerRound(9);
        long ceiling = Math.round(supply
                * plugin.getConfig().getDouble("objectives.limits.mob-fraction", 0.6));

        assertTrue(raw > ceiling,
                "the curve wants " + Math.round(raw) + " and the round can fairly give "
                        + ceiling + " — if that ever stops being true this test proves nothing");
    }

    @Test
    @DisplayName("with challenge mobs off, kill objectives fall back to the plain curve")
    void noMobsMeansNoClamp() {
        plugin.getConfig().set("mobs.enabled", false);
        onlyKillObjectives();

        List<Objective> offers = new ObjectiveGenerator(plugin).offer(9);

        assertFalse(offers.isEmpty(), "vanilla mobs still count, so the job is still offerable");
        assertTrue(offers.get(0).amount() > plugin.mobs().population(9, true),
                "nothing is guaranteeing a supply, so nothing should be trimming the ask");
    }

    private void onlyKillObjectives() {
        plugin.mines().manualProvider().put(
                MineRegion.between("quartz", "Quartz", "mines", 0, 0, 0, 15, 15, 15));
        plugin.mines().refresh();
        plugin.service().objectives().setEnabled(ObjectiveType.MINE_BLOCKS, false);
        plugin.service().objectives().setEnabled(ObjectiveType.MINE_MATERIAL, false);
    }

    private static MobArchetype find(ChallengeMobs mobs, String id) {
        for (MobArchetype archetype : mobs.roster()) {
            if (archetype.id().equals(id)) {
                return archetype;
            }
        }
        return null;
    }
}
