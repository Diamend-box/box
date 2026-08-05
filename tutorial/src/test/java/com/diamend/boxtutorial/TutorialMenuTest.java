package com.diamend.boxtutorial;

import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.gui.TopicsMenu;
import com.diamend.boxtutorial.gui.TutorialMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checklist screen: that it draws every step, that the step you're on is
 * the one you can act on, and that the two clicks which change something
 * (reading a step, skipping one) do what the lore promises.
 */
class TutorialMenuTest {

    private static final int FIRST_STEP = 10;
    private static final int SECOND_STEP = 11;
    private static final int TOPICS = 45;

    private static final String STEPS = """
            steps:
              read-me:
                icon: BOOK
                name: "Read me"
                description: [ "A line." ]
                trigger: READ
              mine-it:
                icon: STONE
                name: "Mine it"
                description: [ "Break three stone." ]
                trigger: BREAK_BLOCK
                target: STONE
                amount: 3
            topics:
              money:
                icon: GOLD_INGOT
                title: "Money"
                lines: [ "It is the score." ]
            """;

    private ServerMock server;
    private BoxTutorialPlugin plugin;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        server.addSimpleWorld("tutorial_arena");
        plugin = MockBukkit.load(BoxTutorialPlugin.class);
        plugin.getConfig().set("show-welcome-title", false);
        plugin.getConfig().set("bossbar", false);
        plugin.getConfig().set("auto-start", false);
        plugin.getConfig().set("completion.title", false);
        // The guide read the config when it started; make it read this one.
        plugin.guide().start();
        Files.writeString(new File(plugin.getDataFolder(), "tutorial.yml").toPath(), STEPS);
        plugin.tutorial().load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock started() {
        PlayerMock player = server.addPlayer();
        plugin.service().start(player, true);
        return player;
    }

    private Inventory open(PlayerMock player) {
        TutorialMenu.openFor(plugin, player);
        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "the menu should be open");
        return view.getTopInventory();
    }

    /** Clicks a menu slot the way the server would, so the GUI listener routes it. */
    private void click(PlayerMock player, int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        assertNotNull(view, "the player should have a menu open");
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(view,
                InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.UNKNOWN));
    }

    private String lore(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return "";
        }
        List<Component> lines = item.getItemMeta().lore();
        if (lines == null) {
            return "";
        }
        StringBuilder all = new StringBuilder();
        for (Component line : lines) {
            all.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        }
        return all.toString();
    }

    private String name(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private boolean isOpen(PlayerMock player, Class<?> menu) {
        InventoryView view = player.getOpenInventory();
        if (view == null || view.getTopInventory() == null) {
            return false;
        }
        return menu.isInstance(view.getTopInventory().getHolder());
    }

    // ------------------------------------------------------------------

    @Test
    void everyStepIsOnTheBoard() {
        PlayerMock player = started();

        Inventory menu = open(player);

        assertEquals(Material.BOOK, menu.getItem(FIRST_STEP).getType(), "step one is drawn");
        assertEquals(Material.STONE, menu.getItem(SECOND_STEP).getType(),
                "and so is the one they haven't reached");
        assertEquals(Material.WRITABLE_BOOK, menu.getItem(TOPICS).getType(), "the glossary is there");
    }

    @Test
    void aStepYouHaveNotReachedSaysSo() {
        PlayerMock player = started();

        Inventory menu = open(player);

        assertTrue(lore(menu.getItem(SECOND_STEP)).contains("Comes later"),
                "an upcoming step explains itself: " + lore(menu.getItem(SECOND_STEP)));
    }

    @Test
    void clickingTheStepYouAreOnMarksItRead() {
        PlayerMock player = started();
        open(player);

        click(player, FIRST_STEP, ClickType.LEFT);

        Progress progress = plugin.store().get(player.getUniqueId());
        assertTrue(progress.isComplete("read-me"), "a READ step completes on the click");
        assertFalse(progress.isSkipped("read-me"), "and it counts as done, not skipped");
    }

    @Test
    void shiftClickingSkipsTheStepWithoutTickingIt() {
        PlayerMock player = started();
        open(player);

        click(player, FIRST_STEP, ClickType.SHIFT_LEFT);

        Progress progress = plugin.store().get(player.getUniqueId());
        assertTrue(progress.isSkipped("read-me"), "shift-click skips it");
        assertEquals("mine-it", plugin.service().current(progress).id(), "and moves on");
    }

    @Test
    void aSkippedStepIsDrawnAsSkipped() {
        PlayerMock player = started();
        plugin.service().skipStep(player, plugin.tutorial().step("read-me"));

        Inventory menu = open(player);

        assertTrue(lore(menu.getItem(FIRST_STEP)).contains("Skipped"),
                "the board doesn't claim they did it: " + lore(menu.getItem(FIRST_STEP)));
        assertFalse(name(menu.getItem(FIRST_STEP)).contains("✔"), "no tick for a skip");
    }

    @Test
    void theGlossaryButtonOpensTheGlossary() {
        PlayerMock player = started();
        open(player);

        click(player, TOPICS, ClickType.LEFT);
        server.getScheduler().performTicks(3);

        assertTrue(isOpen(player, TopicsMenu.class), "the topics menu opens");
    }

    @Test
    void clickingATopicExplainsItInChat() {
        PlayerMock player = started();
        new TopicsMenu(plugin, 0).open(player);

        click(player, FIRST_STEP, ClickType.LEFT);

        boolean explained = false;
        String message;
        while ((message = player.nextMessage()) != null) {
            if (message.contains("It is the score")) {
                explained = true;
            }
        }
        assertTrue(explained, "the topic's lines are sent to chat");
    }
}
