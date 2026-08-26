package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.travel.CombatTagger;
import com.diamend.boxcore.travel.TravelItems;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.TravelService;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.travel.WarpManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
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

    /** Whether the travel menu is the screen this player currently has open. */
    private boolean travelMenuIsOpen(PlayerMock player) {
        InventoryView view = player.getOpenInventory();
        if (view == null || view.getTopInventory() == null) {
            return false;
        }
        return view.getTopInventory().getHolder() instanceof TravelMenu;
    }

    @Test
    void theStandaloneCommandOpensTheMenu() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        assertTrue(player.performCommand("fasttravel"), "the command should be registered");

        assertTrue(travelMenuIsOpen(player), "/fasttravel opens the travel menu");
    }

    @Test
    void theShortAliasesOpenItToo() {
        // The point of the aliases is that they're what people will actually
        // type, so a typo in plugin.yml has to fail here rather than in game.
        for (String label : List.of("fastravel", "ft")) {
            PlayerMock player = server.addPlayer();

            assertTrue(player.performCommand(label), "/" + label + " should be registered");

            assertTrue(travelMenuIsOpen(player), "/" + label + " opens the travel menu");
        }
    }

    @Test
    void argumentsAfterTheCommandAreIgnored() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        assertTrue(player.performCommand("ft mines nonsense"));

        assertTrue(travelMenuIsOpen(player), "it still just opens the menu");
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

    // ------------------------------------------------------------------
    // Tickets and maps
    // ------------------------------------------------------------------

    /** A ticket to the given warp, in the player's hand. */
    private ItemStack ticket(PlayerMock player, String warpId) {
        ItemStack item = module().items().create(
                new TravelItems.Payload("test-ticket", warpId, TravelItems.Mode.TRAVEL),
                TravelItems.Appearance.defaults(), 1, module().warps().get(warpId));
        assertNotNull(item);
        player.getInventory().addItem(item);
        return item;
    }

    private int carried(PlayerMock player) {
        int total = 0;
        for (ItemStack held : player.getInventory().getContents()) {
            if (module().items().read(held) != null) {
                total += held.getAmount();
            }
        }
        return total;
    }

    @Test
    void anItemSaysWhatItIsWhenReadBack() {
        ItemStack item = module().items().create(
                new TravelItems.Payload("map", TravelItems.ANY, TravelItems.Mode.UNLOCK),
                TravelItems.Appearance.defaults(), 1, null);

        TravelItems.Payload read = module().items().read(item);
        assertNotNull(read);
        assertEquals(TravelItems.Mode.UNLOCK, read.mode());
        assertTrue(read.isAny());
        assertNull(module().items().read(new ItemStack(Material.PAPER)),
                "an ordinary item is not a ticket");
    }

    @Test
    void aTicketTakesYouThereAndIsSpentOnArrival() {
        Warp mines = warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);
        ticket(player, "mines");

        assertFalse(module().useItem(player, module().items().read(
                player.getInventory().getItemInMainHand())),
                "a ticket pays for itself on arrival, not on use");

        assertEquals(mines.location().getBlockX(), player.getLocation().getBlockX());
        assertEquals(0, carried(player), "and then it's gone");
    }

    @Test
    void aTicketIsTheWarpsPermission() {
        // Buying one is what the permission would have been for. The check it
        // does not skip is the combat tag — that's the next test.
        warp("vault", at(300, 64, 300), "boxcore.warp.vault");
        PlayerMock player = server.addPlayer();
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);
        ticket(player, "vault");

        module().useItem(player, module().items().read(
                player.getInventory().getItemInMainHand()));

        assertEquals(300, player.getLocation().getBlockX(), "it let them in anyway");
    }

    @Test
    void aTicketWontGetYouOutOfAFight() {
        warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);
        ticket(player, "mines");
        module().combat().tag(player);

        module().useItem(player, module().items().read(
                player.getInventory().getItemInMainHand()));

        assertEquals(0, player.getLocation().getBlockX(), "still where they were");
        assertEquals(1, carried(player), "and the ticket is still theirs");
    }

    @Test
    void aTicketToNowhereIsNotEaten() {
        PlayerMock player = server.addPlayer();
        player.teleport(at(0, 64, 0));
        ticket(player, "deleted-place");

        assertFalse(module().useItem(player, module().items().read(
                player.getInventory().getItemInMainHand())));
        assertEquals(1, carried(player), "nothing happened, so nothing was spent");
    }

    @Test
    void aMapPutsThePlaceOnYourList() {
        Warp mines = warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();

        TravelItems.Payload map =
                new TravelItems.Payload("map", "mines", TravelItems.Mode.UNLOCK);
        assertTrue(module().useItem(player, map), "it did something, so it's spent");
        assertTrue(profile(player).hasDiscovered(mines.id()));

        assertFalse(module().useItem(player, map),
                "using a second one on a place you already know keeps it");
    }

    @Test
    void anAnyMapUnlocksEverythingYouMaySee() {
        warp("mines", at(300, 64, 300), "");
        warp("shop", at(-300, 64, 0), "");
        warp("staff", at(0, 200, 0), "boxcore.warp.staff");
        PlayerMock player = server.addPlayer();

        assertTrue(module().useItem(player,
                new TravelItems.Payload("map", TravelItems.ANY, TravelItems.Mode.UNLOCK)));

        assertTrue(profile(player).hasDiscovered("mines"));
        assertTrue(profile(player).hasDiscovered("shop"));
        assertFalse(profile(player).hasDiscovered("staff"),
                "a map can't show you somewhere you aren't allowed to go");
    }

    @Test
    void theShippedItemsLoad() {
        assertTrue(module().itemIds().contains("spawn-ticket"));
        assertTrue(module().itemIds().contains("world-map"));
        assertNotNull(module().createItem("spawn-ticket", 1));
        assertNull(module().createItem("no-such-item", 1));
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
