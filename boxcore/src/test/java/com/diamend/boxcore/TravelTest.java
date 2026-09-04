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
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    /** Turns walking discovery back on, which ships off. */
    private void walkingFinds() {
        plugin.getConfig().set("travel.discover-by-walking", true);
        module().reload();
    }

    private String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
    }

    /** Puts a place on the player's list without walking or an item. */
    private void found(PlayerMock player, String warpId) {
        profile(player).discoverWarp(warpId);
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
        walkingFinds();
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        assertFalse(profile(player).hasDiscovered("mines"), "not found yet");
        module().checkDiscovery(player, at(3, 64, 0));

        assertTrue(profile(player).hasDiscovered("mines"), "standing next to it finds it");
    }

    @Test
    void walkingPastAtADistanceFindsNothing() {
        walkingFinds();
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        module().checkDiscovery(player, at(200, 64, 0));

        assertFalse(profile(player).hasDiscovered("mines"));
    }

    @Test
    void aPlaceIsOnlyAnnouncedTheFirstTime() {
        walkingFinds();
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
        walkingFinds();
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
        found(player, "mines");
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
        found(player, "mines");
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
        found(player, "mines");
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
        found(player, "mines");
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
        found(player, "mines");
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
        found(player, "mines");
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
    void theLockedEntryIsWhateverConfigSays() {
        PlayerMock player = server.addPlayer();
        plugin.getConfig().set("travel.locked.material", "BARRIER");
        plugin.getConfig().set("travel.locked.name", "<red>Locked: <warp>");
        plugin.getConfig().set("travel.locked.lore", List.of("<gray>Buy the map."));
        module().reload();
        warp("mines", at(0, 64, 0), "");

        new TravelMenu(plugin, module(), 0).open(player);
        ItemStack shown = player.getOpenInventory().getTopInventory().getItem(FIRST_WARP);

        assertEquals(Material.BARRIER, shown.getType());
        assertEquals("Locked: mines", plain(shown.getItemMeta().displayName()),
                "<warp> names the place without opening it");
    }

    @Test
    void theLockedEntryGivesNothingAwayByDefault() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        new TravelMenu(plugin, module(), 0).open(player);
        ItemStack shown = player.getOpenInventory().getTopInventory().getItem(FIRST_WARP);

        assertEquals(Material.GRAY_DYE, shown.getType());
        assertEquals("???", plain(shown.getItemMeta().displayName()),
                "the shipped entry doesn't name it");
    }

    @Test
    void aFoundPlaceShowsItsOwnIcon() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");
        found(player, "mines");

        new TravelMenu(plugin, module(), 0).open(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        assertEquals(Material.ENDER_PEARL, menu.getItem(FIRST_WARP).getType());
    }

    @Test
    void clickingAFoundPlaceStartsTheTrip() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(120, 70, -8), "");
        found(player, "mines");
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
        found(player, "mines");
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
        found(player, "vault");
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
        found(player, "mines");
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

    // ------------------------------------------------------------------
    // Using one
    // ------------------------------------------------------------------

    @Test
    void rightClickingThinAirStillUsesTheItem() {
        // A right-click on air reaches plugins with the block result already
        // DENY, because there is no block to use — which makes the event read
        // as cancelled. A handler that ignores cancelled events therefore
        // ignores every click that wasn't aimed at something, which is most of
        // them. This is that regression.
        PlayerMock player = server.addPlayer();
        warp("mines", at(300, 64, 300), "");
        ItemStack map = module().createItem("mines", TravelItems.Mode.UNLOCK, 1);
        player.getInventory().setItemInMainHand(map);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                map, null, BlockFace.SELF, EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.DENY);
        server.getPluginManager().callEvent(event);

        assertTrue(profile(player).hasDiscovered("mines"), "the map did its job");
    }

    // ------------------------------------------------------------------
    // The gate: a map is what puts somewhere on your list
    // ------------------------------------------------------------------

    @Test
    void walkingPastFindsNothingByDefault() {
        // The shipped setting. A map you can sell is worth nothing if the same
        // place turns up free the first time somebody wanders past it.
        PlayerMock player = server.addPlayer();
        warp("mines", at(0, 64, 0), "");

        module().checkDiscovery(player, at(1, 64, 1));

        assertFalse(profile(player).hasDiscovered("mines"));
    }

    @Test
    void thereIsNoTravellingSomewhereYouHaveNotFound() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(300, 64, 300), "");
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);

        TravelService.Outcome outcome = module().travel().begin(player, mines);

        assertEquals(TravelService.Outcome.NOT_FOUND_YET, outcome);
        assertEquals(0, player.getLocation().getBlockX(), "still where they were");
    }

    @Test
    void aTicketIsNotAWayRoundTheGate() {
        PlayerMock player = server.addPlayer();
        warp("mines", at(300, 64, 300), "");
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);
        ticket(player, "mines");

        module().useItem(player, module().items().read(
                player.getInventory().getItemInMainHand()));

        assertEquals(0, player.getLocation().getBlockX(), "a ticket buys the trip, not the place");
        assertEquals(1, carried(player), "and it wasn't eaten");
    }

    @Test
    void aMapThenATicketWorks() {
        PlayerMock player = server.addPlayer();
        Warp mines = warp("mines", at(300, 64, 300), "");
        player.teleport(at(0, 64, 0));
        module().travel().configure(0, true);

        assertTrue(module().useItem(player,
                new TravelItems.Payload("map", "mines", TravelItems.Mode.UNLOCK)));
        assertEquals(TravelService.Outcome.ARRIVED, module().travel().begin(player, mines));

        assertEquals(300, player.getLocation().getBlockX());
    }

    @Test
    void placingADestinationPutsItOnThePlacersList() {
        // Otherwise staff make a place, stand on it, and find it showing as
        // ??? on their own travel screen.
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.teleport(at(64, 72, 12));

        assertTrue(player.performCommand("box warp set outpost"));

        assertTrue(profile(player).hasDiscovered("outpost"));
    }

    // ------------------------------------------------------------------
    // Minting an item for a destination that has no config entry
    // ------------------------------------------------------------------

    @Test
    void anItemCanBeMintedForADestinationWithNoConfigEntry() {
        warp("mines", at(300, 64, 300), "");

        ItemStack map = module().createItem("mines", TravelItems.Mode.UNLOCK, 2);

        assertNotNull(map, "a destination is enough to make one from");
        assertEquals(2, map.getAmount());
        assertEquals(Material.FILLED_MAP, map.getType(), "a map looks like a map");
        TravelItems.Payload read = module().items().read(map);
        assertNotNull(read);
        assertEquals("mines", read.warpId());
        assertEquals(TravelItems.Mode.UNLOCK, read.mode());
    }

    @Test
    void aMintedMapStillUnlocksThePlace() {
        Warp mines = warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();

        ItemStack map = module().createItem("mines", TravelItems.Mode.UNLOCK, 1);
        assertTrue(module().useItem(player, module().items().read(map)),
                "it did something, so it is spent");

        assertTrue(module().hasDiscovered(player, mines));
    }

    @Test
    void thereIsNothingToMintForAPlaceThatDoesNotExist() {
        assertNull(module().createItem("nowhere", TravelItems.Mode.UNLOCK, 1));
        assertNull(module().createItem("", TravelItems.Mode.UNLOCK, 1));
        assertNull(module().createItem(null, TravelItems.Mode.UNLOCK, 1));
    }

    @Test
    void aTicketToAnywhereIsRefusedButAMapIsNot() {
        assertNull(module().createItem(TravelItems.ANY, TravelItems.Mode.TRAVEL, 1),
                "a ticket has to know where it is taking you");
        assertNotNull(module().createItem(TravelItems.ANY, TravelItems.Mode.UNLOCK, 1));
    }

    @Test
    void theCommandTakesADestinationAndTheKindOfItem() {
        warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box warp item mines map"));

        assertEquals(1, carried(player));
        TravelItems.Payload read = module().items().read(player.getInventory().getItem(0));
        assertNotNull(read);
        assertEquals(TravelItems.Mode.UNLOCK, read.mode());
        assertEquals("mines", read.warpId());
    }

    @Test
    void theCommandWontGuessBetweenATripAndForever() {
        warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box warp item mines"));

        assertEquals(0, carried(player), "it asked which kind rather than picking one");
    }

    @Test
    void theCommandReadsItsArgumentsByShapeNotOrder() {
        warp("mines", at(300, 64, 300), "");
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box warp item mines 8 ticket " + player.getName()));

        assertEquals(8, carried(player));
        TravelItems.Payload read = module().items().read(player.getInventory().getItem(0));
        assertNotNull(read);
        assertEquals(TravelItems.Mode.TRAVEL, read.mode());
    }

    @Test
    void aConfiguredItemNeedsNoModeWord() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box warp item world-map"));

        assertEquals(1, carried(player), "a configured entry needs no mode word");
    }

    @Test
    void anUnknownFirstWordIsRefused() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box warp item not-a-place map"));

        assertEquals(0, carried(player));
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
