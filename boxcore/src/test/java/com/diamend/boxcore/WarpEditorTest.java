package com.diamend.boxcore;

import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.gui.WarpEditMenu;
import com.diamend.boxcore.gui.WarpEditorMenu;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
 * Making and editing destinations from in game: the menu, the typed answers and
 * the commands that do the same jobs.
 */
class WarpEditorTest {

    /** Slots in the destination editor. */
    private static final int ADD_SLOT = 49;
    private static final int FIRST_WARP = 10;

    /** Slots in one destination's screen. */
    private static final int ICON_SLOT = 10;
    private static final int RENAME_SLOT = 12;
    private static final int MOVE_SLOT = 14;
    private static final int RADIUS_MINUS_5 = 20;
    private static final int RADIUS_PLUS_10 = 25;
    private static final int DESCRIPTION_SLOT = 30;
    private static final int PERMISSION_SLOT = 32;
    private static final int DELETE_SLOT = 49;

    /** The travel menu's staff-only button. */
    private static final int EDIT_SLOT = 49;

    private ServerMock server;
    private BoxCorePlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
        world = server.addSimpleWorld("editor-test");
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

    private PlayerMock admin() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.teleport(at(10, 64, 10));
        return player;
    }

    private Warp warp(String id) {
        Warp warp = new Warp(id, id, Material.ENDER_PEARL, List.of(), at(0, 64, 0), "", 8.0);
        module().warps().put(warp);
        return warp;
    }

    /** Clicks a menu slot the way the server would, so the GUI listener routes it. */
    private void click(PlayerMock player, int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "the player should have a menu open");
        InventoryClickEvent event = new InventoryClickEvent(view,
                InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.UNKNOWN);
        Bukkit.getPluginManager().callEvent(event);
    }

    private void click(PlayerMock player, int slot) {
        click(player, slot, ClickType.LEFT);
    }

    /** Answers an open chat prompt and lets the answer land on the main thread. */
    private void answer(PlayerMock player, String text) {
        assertTrue(plugin.prompts().deliver(player, text), "something should be asking");
        server.getScheduler().performOneTick();
    }

    private Inventory openInventory(PlayerMock player) {
        InventoryView view = player.getOpenInventory();
        return view == null ? null : view.getTopInventory();
    }

    // ------------------------------------------------------------------
    // Making one
    // ------------------------------------------------------------------

    @Test
    void addingOneAsksForANameAndPutsItWhereYouStand() {
        PlayerMock player = admin();
        player.teleport(at(120, 70, -8));
        new WarpEditorMenu(plugin, module(), 0).open(player);

        click(player, ADD_SLOT);
        assertTrue(plugin.prompts().isWaiting(player), "it should be waiting on a name");
        answer(player, "Old Mine");

        Warp created = module().warps().get("old_mine");
        assertNotNull(created, "the destination exists now");
        assertEquals("Old Mine", created.display(), "typed name is what players see");
        assertEquals(120, created.location().getBlockX());
        assertEquals(-8, created.location().getBlockZ());
    }

    @Test
    void theIconComesFromWhatYouAreHolding() {
        PlayerMock player = admin();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        new WarpEditorMenu(plugin, module(), 0).open(player);

        click(player, ADD_SLOT);
        answer(player, "mines");

        assertEquals(Material.DIAMOND_PICKAXE, module().warps().get("mines").icon());
    }

    @Test
    void aTypedNameBecomesAnIdAFileCanHold() {
        assertEquals("old_mine", WarpEditorMenu.idFrom("Old Mine"));
        assertEquals("pvp_arena", WarpEditorMenu.idFrom("  PvP  Arena!! "));
        assertEquals("", WarpEditorMenu.idFrom("!!!"), "a name with nothing usable in it");
        assertEquals("abandoned_mines", WarpEditorMenu.idFrom("&7Abandoned Mines"),
                "a colour code is how it looks, not part of what it is called");
        assertEquals("abandoned_mines", WarpEditorMenu.idFrom("<gray>Abandoned Mines"));
        assertEquals("", WarpEditorMenu.idFrom("&7&l"), "a name that is only colour");
    }

    @Test
    void aNameThatIsAlreadyTakenIsRefused() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditorMenu(plugin, module(), 0).open(player);

        click(player, ADD_SLOT);
        answer(player, "Mines");

        assertEquals(1, module().warps().size(), "no second destination under the same id");
    }

    @Test
    void cancellingMakesNothing() {
        PlayerMock player = admin();
        new WarpEditorMenu(plugin, module(), 0).open(player);

        click(player, ADD_SLOT);
        answer(player, "cancel");

        assertEquals(0, module().warps().size());
    }

    // ------------------------------------------------------------------
    // Editing one
    // ------------------------------------------------------------------

    @Test
    void renamingChangesWhatPlayersSeeButNotTheId() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, RENAME_SLOT);
        answer(player, "The Deep Mines");

        assertEquals("The Deep Mines", module().warps().get("mines").display());
        assertNotNull(module().warps().get("mines"), "the id is unchanged");
    }

    @Test
    void theIconIsTakenFromYourHand() {
        PlayerMock player = admin();
        warp("mines");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BEACON));
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, ICON_SLOT);

        assertEquals(Material.BEACON, module().warps().get("mines").icon());
    }

    @Test
    void movingItTakesWhereYouStand() {
        PlayerMock player = admin();
        warp("mines");
        player.teleport(at(-300, 40, 55));
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, MOVE_SLOT);

        Location moved = module().warps().get("mines").location();
        assertEquals(-300, moved.getBlockX());
        assertEquals(55, moved.getBlockZ());
    }

    @Test
    void theRadiusMovesInSteps() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, RADIUS_PLUS_10);

        assertEquals(18, Math.round(module().warps().get("mines").radius()));
    }

    @Test
    void theRadiusStopsAtOne() {
        // Zero would mean a place nobody can ever walk into.
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, RADIUS_MINUS_5);
        click(player, RADIUS_MINUS_5);

        assertEquals(1, Math.round(module().warps().get("mines").radius()));
    }

    @Test
    void descriptionLinesAreAddedRemovedAndCleared() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, DESCRIPTION_SLOT);
        answer(player, "Bring a pickaxe.");
        assertEquals(List.of("Bring a pickaxe."), module().warps().get("mines").description());

        click(player, DESCRIPTION_SLOT);
        answer(player, "And a torch.");
        assertEquals(2, module().warps().get("mines").description().size());

        click(player, DESCRIPTION_SLOT, ClickType.RIGHT);
        assertEquals(List.of("Bring a pickaxe."), module().warps().get("mines").description(),
                "right-click drops the last line");

        click(player, DESCRIPTION_SLOT, ClickType.SHIFT_LEFT);
        assertTrue(module().warps().get("mines").description().isEmpty(), "shift-click clears it");
    }

    @Test
    void thePermissionHasAShortcutAndAnOffSwitch() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, PERMISSION_SLOT, ClickType.SHIFT_LEFT);
        assertEquals("boxcore.warp.mines", module().warps().get("mines").permission());

        click(player, PERMISSION_SLOT, ClickType.RIGHT);
        assertEquals("", module().warps().get("mines").permission(), "right-click opens it up");
    }

    @Test
    void aTypedPermissionSticks() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, PERMISSION_SLOT);
        answer(player, "box.staff");

        assertEquals("box.staff", module().warps().get("mines").permission());
    }

    @Test
    void deletingNeedsAShiftClick() {
        PlayerMock player = admin();
        warp("mines");
        new WarpEditMenu(plugin, module(), "mines").open(player);

        click(player, DELETE_SLOT);
        assertNotNull(module().warps().get("mines"), "a plain click is not a delete");

        click(player, DELETE_SLOT, ClickType.SHIFT_LEFT);
        assertNull(module().warps().get("mines"), "shift-click deletes it");
    }

    // ------------------------------------------------------------------
    // Getting to the editor
    // ------------------------------------------------------------------

    @Test
    void onlyStaffSeeTheEditButton() {
        PlayerMock staff = admin();
        PlayerMock player = server.addPlayer();

        new TravelMenu(plugin, module(), 0).open(staff);
        assertEquals(Material.FILLED_MAP, openInventory(staff).getItem(EDIT_SLOT).getType());

        new TravelMenu(plugin, module(), 0).open(player);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE,
                openInventory(player).getItem(EDIT_SLOT).getType(),
                "everyone else sees filler there");
    }

    @Test
    void theCommandOpensTheEditor() {
        PlayerMock player = admin();

        assertTrue(player.performCommand("box warp"));

        assertTrue(openInventory(player).getHolder() instanceof WarpEditorMenu);
    }

    @Test
    void theCommandOpensOneDestination() {
        PlayerMock player = admin();
        warp("mines");

        assertTrue(player.performCommand("box warp edit mines"));

        assertTrue(openInventory(player).getHolder() instanceof WarpEditMenu);
    }

    @Test
    void editingNeedsAdmin() {
        PlayerMock player = server.addPlayer();

        assertTrue(player.performCommand("box warp"));

        assertNull(openInventory(player), "no permission, no editor");
    }

    // ------------------------------------------------------------------
    // The same jobs from the console
    // ------------------------------------------------------------------

    @Test
    void commandsEditEveryFieldTheMenuDoes() {
        PlayerMock player = admin();
        warp("mines");

        assertTrue(player.performCommand("box warp rename mines The Deep Mines"));
        assertEquals("The Deep Mines", module().warps().get("mines").display(),
                "a name with spaces survives");

        assertTrue(player.performCommand("box warp desc mines add Bring a pickaxe."));
        assertEquals(List.of("Bring a pickaxe."), module().warps().get("mines").description());

        assertTrue(player.performCommand("box warp perm mines box.staff"));
        assertEquals("box.staff", module().warps().get("mines").permission());

        assertTrue(player.performCommand("box warp perm mines none"));
        assertEquals("", module().warps().get("mines").permission());

        assertTrue(player.performCommand("box warp radius mines 24"));
        assertEquals(24, Math.round(module().warps().get("mines").radius()));

        assertTrue(player.performCommand("box warp desc mines clear"));
        assertTrue(module().warps().get("mines").description().isEmpty());
    }

    @Test
    void aSillyRadiusIsRefused() {
        PlayerMock player = admin();
        warp("mines");

        assertTrue(player.performCommand("box warp radius mines lots"));
        assertTrue(player.performCommand("box warp radius mines 9000"));

        assertEquals(8, Math.round(module().warps().get("mines").radius()),
                "it keeps the radius it had");
    }

    @Test
    void movingAWarpKeepsEverythingElseAboutIt() {
        // /box warp set on an existing id means "move it", not "make it again".
        PlayerMock player = admin();
        module().warps().put(new Warp("mines", "The Deep Mines", Material.BEACON,
                List.of("Bring a pickaxe."), at(0, 64, 0), "box.staff", 24.0));
        player.teleport(at(500, 70, 500));

        assertTrue(player.performCommand("box warp set mines"));

        Warp moved = module().warps().get("mines");
        assertEquals(500, moved.location().getBlockX(), "it moved");
        assertEquals("The Deep Mines", moved.display());
        assertEquals(Material.BEACON, moved.icon(), "empty-handed doesn't reset the icon");
        assertEquals(24, Math.round(moved.radius()));
        assertEquals("box.staff", moved.permission());
        assertEquals(List.of("Bring a pickaxe."), moved.description());
    }

    @Test
    void teleportingToOneTakesYouThere() {
        PlayerMock player = admin();
        module().warps().put(new Warp("mines", "mines", Material.ENDER_PEARL, List.of(),
                at(88, 65, -12), "", 8.0));

        assertTrue(player.performCommand("box warp tp mines"));

        assertEquals(88, player.getLocation().getBlockX());
        assertEquals(-12, player.getLocation().getBlockZ());
    }

    @Test
    void aWarpThatIsNotThereIsSaidSoRatherThanCreated() {
        PlayerMock player = admin();

        assertTrue(player.performCommand("box warp rename nowhere Somewhere"));

        assertEquals(0, module().warps().size());
    }

    // ------------------------------------------------------------------
    // The prompt itself
    // ------------------------------------------------------------------

    @Test
    void anAnswerNobodyAskedForIsNotSwallowed() {
        PlayerMock player = admin();

        assertFalse(plugin.prompts().deliver(player, "hello"),
                "ordinary chat has to stay ordinary chat");
    }

    @Test
    void leavingClearsTheQuestion() {
        PlayerMock player = admin();
        new WarpEditorMenu(plugin, module(), 0).open(player);
        click(player, ADD_SLOT);
        assertTrue(plugin.prompts().isWaiting(player));

        plugin.prompts().forget(player.getUniqueId());

        assertFalse(plugin.prompts().isWaiting(player));
    }
}
