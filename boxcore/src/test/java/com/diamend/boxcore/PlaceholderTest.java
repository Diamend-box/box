package com.diamend.boxcore;

import com.diamend.boxcore.integration.BoxPlaceholders;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The PlaceholderAPI expansion, which is how any of this reaches a scoreboard.
 *
 * <p>Placeholders are the plugin's only output that nothing else in the code
 * reads, so a typo in one is invisible until somebody notices a blank line on a
 * scoreboard weeks later.
 */
class PlaceholderTest {

    private ServerMock server;
    private BoxCorePlugin plugin;
    private BoxPlaceholders placeholders;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
        placeholders = new BoxPlaceholders(plugin);
        world = server.addSimpleWorld("placeholder-test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TravelModule travel() {
        TravelModule found = plugin.travel();
        assertNotNull(found, "travel module should be active");
        return found;
    }

    private String ask(PlayerMock player, String query) {
        return placeholders.onRequest(player, query);
    }

    private void warp(String id, String permission, Location where) {
        travel().warps().put(new Warp(id, id, Material.ENDER_PEARL, List.of(),
                where, permission, 8.0));
    }

    private Location at(int x, int z) {
        return new Location(world, x, 64, z);
    }

    // ------------------------------------------------------------------
    // Travel
    // ------------------------------------------------------------------

    @Test
    void findingPlacesShowsUpInTheCounts() {
        PlayerMock player = server.addPlayer();
        warp("mines", "", at(0, 0));
        warp("dock", "", at(500, 0));

        assertEquals("0", ask(player, "travel_found"));
        assertEquals("2", ask(player, "travel_total"));
        assertEquals("0", ask(player, "travel_percent"));

        travel().checkDiscovery(player, at(1, 1));

        assertEquals("1", ask(player, "travel_found"));
        assertEquals("50", ask(player, "travel_percent"));
    }

    @Test
    void aPlaceYouCannotUseIsNotPartOfYourTotal() {
        // Otherwise a progress line reads 3/4 forever for everyone who isn't
        // staff, and looks like something is broken.
        PlayerMock player = server.addPlayer();
        warp("mines", "", at(0, 0));
        warp("staffroom", "boxcore.warp.staffroom", at(500, 0));

        assertEquals("1", ask(player, "travel_total"));
        assertEquals("2", ask(player, "travel_destinations"),
                "the server still has two of them");
    }

    @Test
    void oneNamedPlaceAnswersYesOrNo() {
        PlayerMock player = server.addPlayer();
        warp("mines", "", at(0, 0));

        assertEquals("false", ask(player, "travel_found_mines"));

        travel().checkDiscovery(player, at(1, 1));

        assertEquals("true", ask(player, "travel_found_mines"));
        assertEquals("", ask(player, "travel_found_nowhere"),
                "a place that doesn't exist has no answer, true or false");
    }

    @Test
    void theCombatTagIsReadable() {
        PlayerMock player = server.addPlayer();

        assertEquals("false", ask(player, "travel_combat"));

        travel().combat().tag(player);

        assertEquals("true", ask(player, "travel_combat"));
        assertFalse(ask(player, "travel_combat_time").isBlank(),
                "and how long is left is worth putting on screen");
    }

    @Test
    void theWarmupComesFromTheConfig() {
        PlayerMock player = server.addPlayer();

        assertEquals(String.valueOf(travel().travel().warmupSeconds()),
                ask(player, "travel_warmup"));
    }

    // ------------------------------------------------------------------
    // The rest of the surface
    // ------------------------------------------------------------------

    @Test
    void theOlderPlaceholdersStillAnswer() {
        PlayerMock player = server.addPlayer();

        assertEquals("0", ask(player, "points"));
        assertEquals("0", ask(player, "nodes"));
        assertNotNull(ask(player, "collected"));
        assertTrue(ask(player, "boost_drops").startsWith("1"),
                "no boost reads as 1x rather than as nothing");
    }

    @Test
    void somethingItHasNeverHeardOfIsBlank() {
        PlayerMock player = server.addPlayer();

        assertEquals("", ask(player, "travel_nonsense"));
        assertEquals("", ask(player, "nonsense"));
    }
}
