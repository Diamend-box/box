package com.diamend.boxcore;

import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the travel menu is laid out, and how it keeps up with a world that
 * changes while someone is looking at it.
 */
class TravelMenuTest {

    private static final int HEADER = 4;
    private static final int FIRST = 10;
    private static final int SECOND = 11;

    private ServerMock server;
    private BoxCorePlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
        world = server.addSimpleWorld("travel-menu-test");
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

    private Location at(int x, int z) {
        return new Location(world, x, 64, z);
    }

    private Warp warp(String id, Material icon, Location where) {
        Warp warp = new Warp(id, id, icon, List.of(), where, "", 8.0);
        module().warps().put(warp);
        return warp;
    }

    /** Switches the ordering the way an owner would: edit the config, reload. */
    private void order(String value) {
        plugin.getConfig().set("travel.menu-order", value);
        module().reload();
    }

    private List<String> ids(List<Warp> warps) {
        List<String> ids = new ArrayList<>();
        for (Warp warp : warps) {
            ids.add(warp.id());
        }
        return ids;
    }

    private Inventory openMenu(PlayerMock player) {
        new TravelMenu(plugin, module(), 0).open(player);
        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "the menu should be open");
        return view.getTopInventory();
    }

    /** Every lore line of an item, as plain text. */
    private List<String> lore(ItemStack item) {
        List<String> plain = new ArrayList<>();
        if (item == null || item.getItemMeta() == null) {
            return plain;
        }
        List<Component> lines = item.getItemMeta().lore();
        if (lines == null) {
            return plain;
        }
        for (Component line : lines) {
            plain.add(PlainTextComponentSerializer.plainText().serialize(line)
                    .toLowerCase(Locale.ROOT));
        }
        return plain;
    }

    private boolean says(ItemStack item, String fragment) {
        for (String line : lore(item)) {
            if (line.contains(fragment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    @Test
    void placesYouHaveFoundComeFirst() {
        // The default. A list whose top half you can't use is a list you scroll
        // past, and the ones you can use are the reason the menu is open.
        PlayerMock player = server.addPlayer();
        warp("alpha", Material.DIAMOND, at(500, 0));
        warp("zulu", Material.EMERALD, at(0, 0));
        module().checkDiscovery(player, at(1, 1));

        assertEquals(List.of("zulu", "alpha"), ids(module().orderedFor(player)));
    }

    @Test
    void nameOrderIgnoresWhetherYouFoundIt() {
        PlayerMock player = server.addPlayer();
        warp("alpha", Material.DIAMOND, at(500, 0));
        warp("zulu", Material.EMERALD, at(0, 0));
        module().checkDiscovery(player, at(1, 1));
        order("name");

        assertEquals(List.of("alpha", "zulu"), ids(module().orderedFor(player)));
    }

    @Test
    void fileOrderLeavesThemAsStaffAddedThem() {
        PlayerMock player = server.addPlayer();
        warp("zulu", Material.EMERALD, at(0, 0));
        warp("alpha", Material.DIAMOND, at(500, 0));
        order("file");

        assertEquals(List.of("zulu", "alpha"), ids(module().orderedFor(player)));
    }

    @Test
    void distanceOrderPutsTheNearestFirst() {
        PlayerMock player = server.addPlayer();
        warp("alpha", Material.DIAMOND, at(500, 0));
        warp("zulu", Material.EMERALD, at(0, 0));
        order("distance");
        player.teleport(at(480, 0));

        assertEquals(List.of("alpha", "zulu"), ids(module().orderedFor(player)));
    }

    @Test
    void aWarpInAnotherWorldSortsLastRatherThanThrowing() {
        // Location#distance throws across worlds, and this runs on every open.
        PlayerMock player = server.addPlayer();
        World elsewhere = server.addSimpleWorld("travel-menu-elsewhere");
        warp("far", Material.DIAMOND, new Location(elsewhere, 0, 64, 0));
        warp("near", Material.EMERALD, at(0, 0));
        order("distance");
        player.teleport(at(20, 0));

        assertEquals(List.of("near", "far"), ids(module().orderedFor(player)));
    }

    @Test
    void theMenuShowsThemInThatOrder() {
        PlayerMock player = server.addPlayer();
        warp("alpha", Material.DIAMOND, at(500, 0));
        warp("zulu", Material.EMERALD, at(0, 0));
        module().checkDiscovery(player, at(1, 1));

        Inventory menu = openMenu(player);

        assertEquals(Material.EMERALD, menu.getItem(FIRST).getType(),
                "the one you found is the first thing on the page");
        assertEquals(Material.GRAY_DYE, menu.getItem(SECOND).getType(),
                "and the one you haven't stays anonymous");
    }

    // ------------------------------------------------------------------
    // Keeping up
    // ------------------------------------------------------------------

    @Test
    void theMenuNoticesTheCombatTagRunningOut() {
        PlayerMock player = server.addPlayer();
        warp("mines", Material.EMERALD, at(0, 0));
        module().checkDiscovery(player, at(1, 1));
        module().combat().tag(player);

        Inventory menu = openMenu(player);
        assertTrue(says(menu.getItem(FIRST), "not while you're in combat"),
                "drawn while tagged, it says so");
        assertTrue(says(menu.getItem(HEADER), "in combat"), "and so does the header");

        module().combat().forget(player.getUniqueId());
        server.getScheduler().performTicks(21);

        assertTrue(says(menu.getItem(FIRST), "click to travel"),
                "a second later the menu agrees you can go");
        assertFalse(says(menu.getItem(HEADER), "in combat"));
    }

    @Test
    void theRefreshStopsWhenYouCloseIt() {
        // A timer that outlives the screen would either redraw somebody else's
        // inventory or quietly reopen this one.
        PlayerMock player = server.addPlayer();
        warp("mines", Material.EMERALD, at(0, 0));
        module().checkDiscovery(player, at(1, 1));

        openMenu(player);
        player.closeInventory();
        server.getScheduler().performTicks(60);

        InventoryView view = player.getOpenInventory();
        boolean stillOpen = view != null && view.getTopInventory() != null
                && view.getTopInventory().getHolder() instanceof TravelMenu;
        assertFalse(stillOpen, "closing it closes it");
    }
}
