package com.diamend.robobear;

import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.challenge.Upgrade;
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

/** Which upgrades the workshop sells, and that the shop screen obeys it. */
class UpgradeToggleTest {

    private ServerMock server;
    private RoboBearPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RoboBearPlugin.class);
        plugin.getConfig().set("run.entry-item.item", "");
        plugin.getConfig().set("objectives.kill-mobs.enabled", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("everything is on sale until someone says otherwise")
    void everythingSellsByDefault() {
        assertEquals(Upgrade.values().length, plugin.service().upgrades().enabledCount(),
                "a fresh install must not quietly withhold upgrades");
    }

    @Test
    @DisplayName("taking one off sale removes it from the workshop list")
    void disabledUpgradeLeavesTheList() {
        plugin.service().upgrades().setEnabled(Upgrade.STRENGTH, false);

        assertFalse(plugin.service().upgrades().isEnabled(Upgrade.STRENGTH));
        assertFalse(plugin.service().upgrades().enabled().contains(Upgrade.STRENGTH));
        assertTrue(plugin.service().upgrades().enabled().contains(Upgrade.HASTE),
                "the others are untouched");
        assertEquals(Upgrade.values().length - 1, plugin.service().upgrades().enabledCount());
    }

    @Test
    @DisplayName("an upgrade that isn't sold can't be bought, and costs nothing to try")
    void disabledUpgradeCannotBeBought() {
        PlayerMock player = server.addPlayer();
        assertTrue(plugin.service().start(player));
        RoboRun run = plugin.service().runOf(player);
        assertNotNull(run);
        int before = run.cogs();

        plugin.service().upgrades().setEnabled(Upgrade.HASTE, false);

        assertFalse(plugin.service().buy(player, Upgrade.HASTE),
                "a shop screen left open must not still sell it");
        assertEquals(before, run.cogs(), "a refused purchase must not take Cogs");
        assertEquals(0, run.levelOf(Upgrade.HASTE));
    }

    @Test
    @DisplayName("the same upgrade sells normally while it is on sale")
    void enabledUpgradeStillSells() {
        plugin.getConfig().set("upgrades.haste.cost", 5);
        plugin.getConfig().set("run.starting-cogs", 20);

        PlayerMock player = server.addPlayer();
        assertTrue(plugin.service().start(player));
        RoboRun run = plugin.service().runOf(player);
        assertNotNull(run);

        assertTrue(plugin.service().buy(player, Upgrade.HASTE), "this is the control case");
        assertEquals(1, run.levelOf(Upgrade.HASTE));
        assertTrue(run.cogs() < 20, "buying it should have cost something");
    }

    @Test
    @DisplayName("bulk close and reopen the workshop")
    void bulkTogglesWork() {
        plugin.service().upgrades().disableAll();
        assertEquals(0, plugin.service().upgrades().enabledCount());

        plugin.service().upgrades().setEnabled(Upgrade.OVERCLOCK, true);
        assertEquals(1, plugin.service().upgrades().enabledCount());

        plugin.service().upgrades().enableAll();
        assertEquals(Upgrade.values().length, plugin.service().upgrades().enabledCount());
    }

    @Test
    @DisplayName("the choices survive a reload")
    void togglesPersist() {
        plugin.service().upgrades().setEnabled(Upgrade.SALVAGE, false);

        plugin.service().upgrades().load();

        assertFalse(plugin.service().upgrades().isEnabled(Upgrade.SALVAGE));
        assertTrue(plugin.service().upgrades().isEnabled(Upgrade.HASTE));
    }

    @Test
    @DisplayName("refreshing action bars is safe with nothing running and mid-choice")
    void actionBarRefreshIsSafe() {
        plugin.service().refreshActionBars();

        PlayerMock player = server.addPlayer();
        plugin.service().start(player);
        // The run is on the choice screen, so there is no clock to draw yet.
        plugin.service().refreshActionBars();

        assertTrue(plugin.service().isRunning(player));
    }
}
