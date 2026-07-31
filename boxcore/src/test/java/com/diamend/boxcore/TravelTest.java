package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.travel.CombatTagger;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.TravelService;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.travel.WarpManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast travel: finding places, and the two rules that stop travelling being a
 * free way out of a fight.
 */
class TravelTest {

    private static final int FIRST_WARP = 10;

    private ServerMock server;
    private BoxCorePlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
        world = server.addSimpleWorld("travel-test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TravelModule module() {
        TravelModule found = plugin.travel();
        assertNotNull(found, "travel module should be active");
        return found;
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** Adds a warp and returns it. */
    private Warp warp(String id, Location where, String permission) {
        Warp warp = new Warp(id, id, Material.ENDER_PEARL, List.of(), where, permission, 8.0);
        module().warps().put(warp);
        return warp;
    }

    private PlayerProfile profile(PlayerMock player) {
        return plugin.profiles().get(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // The warp list
    // ------------------------------------------------------------------

    @Test
    void aWarpSurvivesBeingWrittenAndReadBack() {
        warp("mines", at(100, 64, -40), "");

        WarpManager reread = new WarpManager(plugin);
        reread.load();

        Warp found = reread.get("mines");
        assertNotNull(found, "it should still be there after a reload");
        assertEquals(100, found.location().getBlockX());
        assertEquals(-40, found.location().getBlockZ());
        assertEquals(world, found.location().getWorld());
    }

    @Test
    void aDeletedWarpStaysDeleted() {
        // The file is rewritten on every change, and entries for unloaded worlds
        // are carried forward. A delete must not come back through that path.
        warp("mines", at(100, 64, -40), "");
        assertTrue(module().warps().remove("mines"));

        WarpManager reread = new WarpManager(plugin);
        reread.load();

        assertNull(reread.get("mines"), "deleting it must stick");
    }

    // ------------------------------------------------------------------
    // Finding places
    // ------------------------------------------------------------------

    @Test
    void walkingIntoAPlaceFindsIt() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        assertFalse(profile(player).hasDiscovered("mines"), "not found yet");
        module().checkDiscovery(player, at(3, 64, 0));

        assertTrue(profile(player).hasDiscovered("mines"), "standing next to it finds it");
    }

    @Test
    void walkingPastAtADistanceFindsNothing() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        module().checkDiscovery(player, at(200, 64, 0));

        assertFalse(profile(player).hasDiscovered("mines"));
    }

    @Test
    void aPlaceIsOnlyAnnouncedTheFirstTime() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        module().checkDiscovery(player, at(1, 64, 1));
        while (player.nextMessage() != null) {
            // drain the announcement
        }

        module().checkDiscovery(player, at(1, 64, 1));

        assertNull(player.nextMessage(), "finding it again is not news");
    }

    @Test
    void aPlaceYouCannotUseIsNotFound() {
        // Otherwise a warp someone will never be allowed to use quietly appears
        // in their list the first time they walk past it.
        PlayerMock player = server.addPlayer();
        warp("staff", at(0, 64, 0), "boxcore.warp.staff");

        module().checkDiscovery(player, at(1, 64, 1));

        assertFalse(profile(player).hasDiscovered("staff"));
    }

    @Test
    void discoveryIsRememberedOnTheProfile() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = profile(player);
        profile.markClean();

        assertTrue(profile.discoverWarp("mines"));
        assertTrue(profile.isDirty(), "a find has to be saved");
        assertFalse(profile.discoverWarp("mines"), "and only counts once");
    }

    // ------------------------------------------------------------------
    // Leaving a fight
    // ------------------------------------------------------------------

    @Test
    void travellingIsRefusedWhileInCombat() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(0, 64, 0), "");
        module().combat().tag(player);

        TravelService.Outcome outcome = module().travel().begin(player, mines);

        assertEquals(TravelService.Outcome.IN_COMBAT, outcome);
        assertFalse(module().travel().isTravelling(player));
    }

    @Test
    void theCombatTagRunsOut() {
        PlayerMock player = server.addPlayer();
        CombatTagger combat = new CombatTagger();
        combat.setSeconds(0);

        combat.tag(player);

        assertFalse(combat.isTagged(player), "a zero-second tag is no tag at all");
    }

    @Test
    void takingDamageCancelsATrip() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(0, 64, 0), "");
        module().travel().configure(5, true);

        module().travel().begin(player, mines);
        assertTrue(module().travel().isTravelling(player));

        module().travel().onHurt(player);

        assertFalse(module().travel().isTravelling(player), "damage stops the trip");
    }

    @Test
    void movingCancelsATrip() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(0, 64, 0), "");
        player.teleport(at(50, 64, 50));
        module().travel().configure(5, true);

        module().travel().begin(player, mines);
        module().travel().onMove(player, at(52, 64, 50));

        assertFalse(module().travel().isTravelling(player), "stepping away stops the trip");
    }

    @Test
    void lookingAroundDoesNotCancelATrip() {
        // Standing still still produces move events. Cancelling on those would
        // make the feature look broken.
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(0, 64, 0), "");
        player.teleport(new Location(world, 50.5, 64, 50.5, 0f, 0f));
        module().travel().configure(5, true);

        module().travel().begin(player, mines);
        module().travel().onMove(player, new Location(world, 50.7, 64, 50.3, 90f, 20f));

        assertTrue(module().travel().isTravelling(player), "same block, still going");
    }

    // ------------------------------------------------------------------
    // Arriving
    // ------------------------------------------------------------------

    @Test
    void noWarmupMeansArrivingImmediately() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(120, 70, -8), "");
        module().travel().configure(0, true);

        TravelService.Outcome outcome = module().travel().begin(player, mines);

        assertEquals(TravelService.Outcome.ARRIVED, outcome);
        assertEquals(120, player.getLocation().getBlockX());
        assertEquals(-8, player.getLocation().getBlockZ());
    }

    @Test
    void theWarmupArrivesWhenItRunsOut() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(120, 70, -8), "");
        module().travel().configure(1, true);

        module().travel().begin(player, mines);
        assertTrue(module().travel().isTravelling(player), "not there yet");

        server.getScheduler().performTicks(25);

        assertFalse(module().travel().isTravelling(player), "the trip finished");
        assertEquals(120, player.getLocation().getBlockX(), "and they're there");
    }

    // ------------------------------------------------------------------
    // The menu
    // ------------------------------------------------------------------

    @Test
    void theMenuHidesWhereYouHaveNotBeen() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        new TravelMenu(plugin, module(), 0).open(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        assertEquals(Material.GRAY_DYE, menu.getItem(FIRST_WARP).getType(),
                "somewhere you haven't been shows as unknown, not as its icon");
    }

    @Test
    void aFoundPlaceShowsItsOwnIcon() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");
        module().checkDiscovery(player, at(1, 64, 1));

        new TravelMenu(plugin, module(), 0).open(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        assertEquals(Material.ENDER_PEARL, menu.getItem(FIRST_WARP).getType());
    }

    @Test
    void clickingAFoundPlaceStartsTheTrip() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(120, 70, -8), "");
        module().checkDiscovery(player, at(121, 70, -8));
        player.teleport(at(0, 64, 0));
        module().travel().configure(5, true);

        new TravelMenu(plugin, module(), 0).open(player);
        player.simulateInventoryClick(FIRST_WARP);

        assertTrue(module().travel().isTravelling(player));
    }

    // ------------------------------------------------------------------
    // Setting them up
    // ------------------------------------------------------------------

    @Test
    void theCommandSetsAWarpWhereYouStand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.teleport(at(64, 72, 12));

        assertTrue(player.performCommand("box warp set spawn"));

        Warp created = module().warps().get("spawn");
        assertNotNull(created, "the warp exists now");
        assertEquals(64, created.location().getBlockX());
        assertEquals(12, created.location().getBlockZ());
    }

    @Test
    void settingAWarpNeedsAdmin() {
        PlayerMock player = server.addPlayer();
        player.teleport(at(64, 72, 12));

        assertTrue(player.performCommand("box warp set spawn"));

        assertNull(module().warps().get("spawn"), "no admin permission, no warp");
    }

    @Test
    void aBadWarpIdIsRefused() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.teleport(at(64, 72, 12));

        assertTrue(player.performCommand("box warp set my.spawn!"));

        assertNull(module().warps().get("my.spawn!"),
                "ids stay to letters, numbers, - and _");
        assertEquals(0, module().warps().size(), "and nothing was created under any name");
    }
}
