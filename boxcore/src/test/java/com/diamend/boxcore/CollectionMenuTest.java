package com.diamend.boxcore;

import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.gui.CollectionCategoryMenu;
import com.diamend.boxcore.gui.CollectionListMenu;
import com.diamend.boxcore.gui.HubMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collections landing page: where the category icons actually land.
 *
 * <p>Everything else in this menu is read off the config. The layout is the one
 * part the code decides, and it used to decide it badly once there were more
 * categories than fitted on a row.
 */
class CollectionMenuTest {

    private static final int HEADER = 4;
    private static final int BACK = 18;

    private ServerMock server;
    private BoxCorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CollectionsModule module() {
        CollectionsModule found = plugin.modules().get(CollectionsModule.class);
        assertNotNull(found, "collections module should be active");
        return found;
    }

    /** Rewrites collections.yml with this many categories and reloads. */
    private void categories(int count) throws IOException {
        StringBuilder yaml = new StringBuilder("categories:\n");
        for (int index = 1; index <= count; index++) {
            yaml.append("  cat").append(index).append(":\n")
                    .append("    display: \"Category ").append(index).append("\"\n")
                    .append("    icon: BOOK\n")
                    .append("    order: ").append(index).append("\n");
        }
        yaml.append("collections:\n")
                .append("  stone:\n")
                .append("    display: Stone\n")
                .append("    category: cat1\n")
                .append("    icon: STONE\n")
                .append("    items: [ STONE ]\n")
                .append("    tiers: [ 10, 20 ]\n");
        Files.writeString(new File(plugin.getDataFolder(), "collections.yml").toPath(), yaml);
        module().reload();
    }

    private Inventory open(PlayerMock player) {
        new CollectionCategoryMenu(plugin, module()).open(player);
        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "the menu should be open");
        return view.getTopInventory();
    }

    private boolean isOpen(PlayerMock player, Class<?> menu) {
        InventoryView view = player.getOpenInventory();
        if (view == null || view.getTopInventory() == null) {
            return false;
        }
        return menu.isInstance(view.getTopInventory().getHolder());
    }

    private String text(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return "";
        }
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) {
            return "";
        }
        StringBuilder all = new StringBuilder();
        for (Component line : lore) {
            all.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        }
        return all.toString();
    }

    private int iconCount(Inventory menu, Material material) {
        int found = 0;
        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (item != null && item.getType() == material) {
                found++;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Test
    void theShippedCategoriesSitUnderTheHeader() throws IOException {
        categories(6);
        PlayerMock player = server.addPlayer();

        Inventory menu = open(player);

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, menu.getItem(10).getType(),
                "six across a seven-wide row leaves the gap on the left");
        for (int slot = 11; slot <= 16; slot++) {
            assertEquals(Material.BOOK, menu.getItem(slot).getType(),
                    "category expected in slot " + slot);
        }
    }

    @Test
    void anOddNumberSitsExactlyInTheMiddle() throws IOException {
        categories(3);
        PlayerMock player = server.addPlayer();

        Inventory menu = open(player);

        assertEquals(Material.BOOK, menu.getItem(13).getType(),
                "the middle category is under the header");
        assertEquals(Material.BOOK, menu.getItem(12).getType());
        assertEquals(Material.BOOK, menu.getItem(14).getType());
    }

    @Test
    void moreThanARowSpillsOntoTheNextOne() throws IOException {
        // The tenth used to land on the back button: drawn over, and unclickable
        // because back was checked first.
        categories(10);
        PlayerMock player = server.addPlayer();

        Inventory menu = open(player);

        assertEquals(10, iconCount(menu, Material.BOOK), "every category is on screen");
        assertEquals(Material.BOOK, menu.getItem(19).getType(), "the second row starts at 19");
        assertEquals(Material.ARROW, menu.getItem(BACK).getType(),
                "and the back button survives");
    }

    @Test
    void theBackButtonStillGoesBack() throws IOException {
        categories(10);
        PlayerMock player = server.addPlayer();
        open(player);

        player.simulateInventoryClick(BACK);
        server.getScheduler().performTicks(3);

        assertTrue(isOpen(player, HubMenu.class), "back is back, not a category");
    }

    @Test
    void tooManyToShowIsSaidOutLoud() throws IOException {
        categories(20);
        PlayerMock player = server.addPlayer();

        Inventory menu = open(player);

        assertTrue(text(menu.getItem(HEADER)).contains("14"),
                "the header owns up to what it can't fit: " + text(menu.getItem(HEADER)));
    }

    @Test
    void aCategoryStillOpens() throws IOException {
        categories(6);
        PlayerMock player = server.addPlayer();
        open(player);

        player.simulateInventoryClick(11);
        server.getScheduler().performTicks(3);

        assertTrue(isOpen(player, CollectionListMenu.class));
    }
}
