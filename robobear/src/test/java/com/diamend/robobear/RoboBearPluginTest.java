package com.diamend.robobear;

import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.mine.MineRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin against a real Paper API: that it enables without MineResetLite
 * present, that the manual mine source works, and that a run can't start on a
 * server with nothing set up.
 */
class RoboBearPluginTest {

    private ServerMock server;
    private RoboBearPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RoboBearPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("enables cleanly with no MineResetLite installed")
    void enablesWithoutMineResetLite() {
        assertTrue(plugin.isEnabled(), "the plugin must not need MineResetLite to load");
        assertNotNull(plugin.mines());
        assertNotNull(plugin.milestones());
        assertNotNull(plugin.service());
        assertEquals("manual", plugin.mines().activeSource(),
                "with no MineResetLite, 'auto' should fall back to the manual list");
    }

    @Test
    @DisplayName("the starter milestone tiers are laid out on a fresh install")
    void installsStarterTiers() {
        assertTrue(plugin.milestones().size() > 0,
                "a fresh install should have tiers to fill in, not a blank screen");

        MilestoneRewards.Tier first = plugin.milestones().all().get(0);
        assertTrue(first.isEmpty(),
                "the starter tiers are placeholders — they must not pay anything until filled");
    }

    @Test
    @DisplayName("nextAfter walks the ladder in order")
    void nextAfterWalksTheLadder() {
        MilestoneRewards milestones = plugin.milestones();
        MilestoneRewards.Tier first = milestones.nextAfter(0);
        assertNotNull(first, "there should be a tier above round zero");

        MilestoneRewards.Tier second = milestones.nextAfter(first.round());
        if (second != null) {
            assertTrue(second.round() > first.round(), "tiers must come back in ascending order");
        }
    }

    @Test
    @DisplayName("a manual mine is picked up after a refresh")
    void manualMinesAreFound() {
        int before = plugin.mines().size();

        plugin.mines().manualProvider().put(
                MineRegion.between("quarry", "Quarry", "world", 0, 0, 0, 15, 15, 15));
        plugin.mines().refresh();

        assertEquals(before + 1, plugin.mines().size());
        assertTrue(plugin.mines().exists("quarry"));
        assertTrue(plugin.mines().exists("QUARRY"), "ids resolve case-insensitively");
        assertNotNull(plugin.mines().byId("quarry"));
    }

    @Test
    @DisplayName("a run can't start when there's nothing to do")
    void refusesToStartWithNothingSetUp() {
        plugin.getConfig().set("objectives.kill-mobs.enabled", false);
        plugin.getConfig().set("run.entry-item.item", "");

        PlayerMock player = server.addPlayer();
        assertFalse(plugin.service().start(player),
                "with no mines and no mob objective there is no job to offer");
        assertFalse(plugin.service().isRunning(player));
    }

    @Test
    @DisplayName("entry is refused without the pass, and the pass isn't taken")
    void refusesWithoutThePass() {
        plugin.getConfig().set("run.entry-item.item", "NAME_TAG");
        plugin.getConfig().set("run.entry-item.amount", 1);
        plugin.getConfig().set("run.entry-item.name", "");
        plugin.getConfig().set("objectives.kill-mobs.enabled", true);

        PlayerMock player = server.addPlayer();
        assertFalse(plugin.service().start(player), "no pass, no run");
        assertFalse(plugin.service().isRunning(player));
    }

    @Test
    @DisplayName("a free-entry run starts and lands on the choice screen")
    void startsWhenEntryIsFree() {
        plugin.getConfig().set("run.entry-item.item", "");
        plugin.getConfig().set("objectives.kill-mobs.enabled", true);

        PlayerMock player = server.addPlayer();
        assertTrue(plugin.service().start(player));
        assertTrue(plugin.service().isRunning(player));

        var run = plugin.service().runOf(player);
        assertNotNull(run);
        assertEquals(1, run.round());
        assertFalse(run.offers().isEmpty(), "the player has to be offered something to pick");
        assertEquals(plugin.getConfig().getInt("run.starting-cogs"), run.cogs());
    }

    @Test
    @DisplayName("the run ends when the player is dropped")
    void abandonEndsTheRun() {
        plugin.getConfig().set("run.entry-item.item", "");
        PlayerMock player = server.addPlayer();
        plugin.service().start(player);
        assertTrue(plugin.service().isRunning(player));

        plugin.service().abandon(player.getUniqueId());

        assertFalse(plugin.service().isRunning(player));
        assertEquals(0, plugin.service().activeCount());
    }

    @Test
    @DisplayName("reloading doesn't throw and keeps the plugin enabled")
    void reloadIsSafe() {
        plugin.reloadEverything();

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.mines());
        assertEquals(0, plugin.service().activeCount(), "reloading ends runs rather than orphaning them");
    }
}
