package com.diamend.boxcore;

import com.diamend.boxcore.boost.BoostItems;
import com.diamend.boxcore.boost.BoostNotifier;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.BoostMenu;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player-facing half of boosts: the menu, and being told what is happening
 * to a boost you are running.
 */
class BoostUiTest {

    private static final int FIRST_ITEM_SLOT = 19;

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

    private BoostsModule boosts() {
        BoostsModule module = plugin.boosts();
        assertNotNull(module, "boosts module should be active");
        return module;
    }

    /** Throws away messages already sent, so a test only reads its own. */
    private void drain(PlayerMock player) {
        while (player.nextMessage() != null) {
            // discard
        }
    }

    // ------------------------------------------------------------------
    // Reading boosts off a profile
    // ------------------------------------------------------------------

    @Test
    void aProfileCanBeAskedWithoutItsPlayerBeingOnline() {
        PlayerMock player = server.addPlayer();
        boosts().addPlayer(player, BoostType.DROPS, 3.0, 60_000, "test");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        assertEquals(3.0, boosts().multiplierFor(profile, BoostType.DROPS));
        assertEquals(1.0, boosts().multiplierFor(null, BoostType.DROPS),
                "asking with no profile is asking about the server, not about them");
    }

    @Test
    void personalAndServerWideAreShownApart() {
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 2.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 3.0, 60_000, "test");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        assertEquals(6.0, boosts().multiplierFor(profile, BoostType.DROPS));
        assertEquals(2.0, boosts().globalMultiplier(BoostType.DROPS));
        assertEquals(3.0, boosts().personalMultiplier(profile, BoostType.DROPS),
                "the menu shows the two halves, so they have to be separable");
    }

    @Test
    void theCountdownIsTheSoonestBoostToEnd() {
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 2.0, 600_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 30_000, "test");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        long remaining = boosts().remainingFor(profile, BoostType.DROPS);
        assertTrue(remaining > 0 && remaining <= 30_000,
                "the number on screen changes when the first one ends, not the last");
        assertEquals(0, boosts().remainingFor(profile, BoostType.COLLECTIONS),
                "nothing running reads as no time left");
    }

    // ------------------------------------------------------------------
    // The menu
    // ------------------------------------------------------------------

    @Test
    void theMenuListsTheBoostItemsYouAreCarrying() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, boosts().createItem("drops-2x", 1));

        new BoostMenu(plugin, boosts()).open(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        assertNotNull(boosts().items().read(menu.getItem(FIRST_ITEM_SLOT)),
                "the item you're carrying is on show and still readable as a boost");
    }

    @Test
    void clickingABoostItemStartsItAndSpendsOne() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, boosts().createItem("drops-2x", 2));
        new BoostMenu(plugin, boosts()).open(player);

        player.simulateInventoryClick(FIRST_ITEM_SLOT);

        assertEquals(2.0, boosts().multiplier(player, BoostType.DROPS), "the boost started");
        ItemStack left = player.getInventory().getItem(0);
        assertNotNull(left, "the other one is still there");
        assertEquals(1, left.getAmount(), "exactly one was spent");
    }

    @Test
    void spendingYourLastOneEmptiesTheSlot() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, boosts().createItem("collections-2x", 1));
        new BoostMenu(plugin, boosts()).open(player);

        player.simulateInventoryClick(FIRST_ITEM_SLOT);

        assertEquals(2.0, boosts().multiplier(player, BoostType.COLLECTIONS));
        ItemStack left = player.getInventory().getItem(0);
        assertTrue(left == null || left.getType().isAir(), "the slot is empty, not a zero-sized stack");
    }

    @Test
    void anItemSwappedOutFromUnderTheMenuIsNotSpent() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, boosts().createItem("drops-2x", 1));
        new BoostMenu(plugin, boosts()).open(player);

        // The menu is open; the player moves something else into that slot.
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));
        player.simulateInventoryClick(FIRST_ITEM_SLOT);

        assertEquals(1.0, boosts().multiplier(player, BoostType.DROPS), "no boost was started");
        ItemStack left = player.getInventory().getItem(0);
        assertNotNull(left);
        assertEquals(Material.DIAMOND, left.getType(), "and the diamond was left alone");
    }

    @Test
    void clickingTheDecorationDoesNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, boosts().createItem("drops-2x", 1));
        new BoostMenu(plugin, boosts()).open(player);

        player.simulateInventoryClick(0);
        player.simulateInventoryClick(9);

        assertEquals(1.0, boosts().multiplier(player, BoostType.DROPS));
        assertNotNull(player.getInventory().getItem(0), "the item is untouched");
    }

    @Test
    void carryingNothingStillOpens() {
        PlayerMock player = server.addPlayer();
        new BoostMenu(plugin, boosts()).open(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        assertNull(boosts().items().read(menu.getItem(FIRST_ITEM_SLOT)),
                "no boost item is offered when you have none");
        assertNotNull(menu.getItem(4), "the server-wide panel is always there");
    }

    // ------------------------------------------------------------------
    // Being told what's happening
    // ------------------------------------------------------------------

    /** Quietens the actionbar so a test only sees the chat lines it asked about. */
    private BoostNotifier notifier(long warnSeconds) {
        plugin.getConfig().set("boosts.notify.actionbar", false);
        plugin.getConfig().set("boosts.notify.warn-seconds", warnSeconds);
        BoostNotifier notifier = boosts().notifier();
        notifier.start();
        return notifier;
    }

    @Test
    void aBoostThatRunsOutIsAnnouncedOnce() throws InterruptedException {
        PlayerMock player = server.addPlayer();
        BoostNotifier notifier = notifier(0);
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 150L, "test");

        notifier.tick();
        Thread.sleep(300L);
        drain(player);

        notifier.tick();
        String told = player.nextMessage();
        assertNotNull(told, "the player is told their boost ended");
        assertTrue(told.contains("ended"), "and told that it ended: " + told);

        notifier.tick();
        assertNull(player.nextMessage(), "told once, not every second afterwards");
    }

    @Test
    void aBoostShorterThanTheWarningNeverWarns() {
        PlayerMock player = server.addPlayer();
        BoostNotifier notifier = notifier(60);
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 5_000L, "test");
        drain(player);

        notifier.tick();

        assertNull(player.nextMessage(),
                "being told a five-second boost has a minute left would be nonsense");
    }

    @Test
    void aBoostCrossingTheWarningLineIsAnnouncedOnce() throws InterruptedException {
        PlayerMock player = server.addPlayer();
        BoostNotifier notifier = notifier(2);
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 3_000L, "test");
        drain(player);

        notifier.tick(); // three seconds left: above the line, so quiet
        assertNull(player.nextMessage(), "no warning while there's plenty of time");

        Thread.sleep(1_200L);
        notifier.tick(); // under two seconds left
        String warning = player.nextMessage();
        assertNotNull(warning, "the player is warned before it ends");
        assertTrue(warning.contains("ends in"), "and told how long is left: " + warning);

        notifier.tick();
        assertNull(player.nextMessage(), "warned once, not on every pass");
    }

    @Test
    void aServerWideBoostEndingIsAnnouncedToEveryone() throws InterruptedException {
        PlayerMock player = server.addPlayer();
        BoostNotifier notifier = notifier(0);
        boosts().addGlobal(BoostType.COLLECTIONS, 2.0, 150L, "test");

        notifier.tick();
        Thread.sleep(300L);
        drain(player);

        notifier.tick();
        String told = player.nextMessage();
        assertNotNull(told, "everyone online hears that the event is over");
        assertTrue(told.contains("ended"), told);
    }

    @Test
    void turningTheNotifierOffKeepsItQuiet() throws InterruptedException {
        PlayerMock player = server.addPlayer();
        plugin.getConfig().set("boosts.notify.actionbar", false);
        plugin.getConfig().set("boosts.notify.chat", false);
        BoostNotifier notifier = boosts().notifier();
        notifier.start();

        boosts().addPlayer(player, BoostType.DROPS, 2.0, 100L, "test");
        notifier.tick();
        Thread.sleep(200L);
        drain(player);
        notifier.tick();

        assertNull(player.nextMessage(), "nothing is said when both channels are off");
        assertFalse(boosts().multiplier(player, BoostType.DROPS) > 1.0,
                "and the boost still ended on time regardless");
    }

    // ------------------------------------------------------------------
    // Items row plumbing
    // ------------------------------------------------------------------

    @Test
    void theMenuShowsWhatTheItemItselfSaysItGrants() {
        PlayerMock player = server.addPlayer();
        ItemStack item = boosts().createItem("drops-2x", 1);
        BoostItems.Payload payload = boosts().items().read(item);
        assertNotNull(payload);
        player.getInventory().setItem(0, item);

        new BoostMenu(plugin, boosts()).open(player);
        BoostItems.Payload shown = boosts().items()
                .read(player.getOpenInventory().getTopInventory().getItem(FIRST_ITEM_SLOT));

        assertNotNull(shown);
        assertEquals(payload, shown, "the icon carries the same terms as the item it stands for");
    }
}
