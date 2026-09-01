package com.diamend.spyglass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.diamend.spyglass.watch.Watch;
import com.diamend.spyglass.watch.WatchCategory;
import com.diamend.spyglass.watch.WatchLog;

/**
 * The command and the plugin around it, against a real Bukkit API.
 *
 * <p>MockBukkit does not implement every corner of the Paper API, which is the
 * point: a report is expected to come out whole with {@code n/a} in the gaps,
 * not to fall over.
 */
class SpyCommandTest {

    private ServerMock server;
    private SpyglassPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SpyglassPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A staff member with every node, so the tests exercise the command not the guards. */
    private PlayerMock staff() {
        PlayerMock player = server.addPlayer("Staff");
        for (String node : new String[] { "spyglass.use", "spyglass.watch",
                "spyglass.sensitive", "spyglass.admin" }) {
            player.addAttachment(plugin, node, true);
        }
        drain(player);
        return player;
    }

    /** Everything the sender has been told since we last looked. */
    private String drain(PlayerMock player) {
        StringBuilder out = new StringBuilder();
        String message;
        while ((message = player.nextMessage()) != null) {
            out.append(message).append('\n');
        }
        return out.toString();
    }

    private String run(PlayerMock sender, String commandLine) {
        server.dispatchCommand(sender, commandLine);
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2L);
        return drain(sender);
    }

    // ------------------------------------------------------------------

    @Test
    void thePluginEnables() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.watches());
        assertNotNull(plugin.settings());
        assertNotNull(server.getPluginCommand("spy"));
    }

    @Test
    void helpAndSectionsExplainThemselves() {
        PlayerMock staff = staff();

        assertTrue(run(staff, "spy").contains("/spy <player>"));

        String sections = run(staff, "spy sections");
        assertTrue(sections.contains("inventory"), sections);
        assertTrue(sections.contains("nbt"), sections);
        assertTrue(sections.contains("enderchest"), sections);
    }

    @Test
    void anOnlinePlayerCanBeInspected() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        String overview = run(staff, "spy Notch");

        assertTrue(overview.contains("Notch (online)"), overview);
        assertTrue(overview.contains("Overview"), overview);
        assertTrue(overview.contains("health"), overview);
    }

    @Test
    void eachSectionAnswersForAnOnlinePlayer() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        assertTrue(run(staff, "spy Notch inventory").contains("Inventory"));
        assertTrue(run(staff, "spy Notch vitals").contains("Vitals"));
        assertTrue(run(staff, "spy Notch position").contains("Position"));
        assertTrue(run(staff, "spy Notch effects").contains("effects"));
        assertTrue(run(staff, "spy Notch data").contains("Player data"));
        assertTrue(run(staff, "spy Notch armor").contains("hand"));
        assertTrue(run(staff, "spy Notch item 0").contains("slot 0"));
    }

    @Test
    void unknownNamesAndSectionsAreSaidPlainly() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        assertTrue(run(staff, "spy Ghost").contains("No player called"));
        assertTrue(run(staff, "spy Notch trousers").contains("No section called"));
    }

    @Test
    void listShowsWhoIsOnline() {
        server.addPlayer("Notch");
        server.addPlayer("Jeb");
        PlayerMock staff = staff();

        String list = run(staff, "spy list");

        assertTrue(list.contains("Notch"), list);
        assertTrue(list.contains("Jeb"), list);
        assertTrue(list.contains("player(s)"), list);
    }

    @Test
    void withoutPermissionNothingIsShown() {
        server.addPlayer("Notch");
        PlayerMock nosy = server.addPlayer("Nosy");
        drain(nosy);

        String reply = run(nosy, "spy Notch");

        assertTrue(reply.contains("permission"), reply);
        assertFalse(reply.contains("Overview"), reply);
    }

    @Test
    void anExemptPlayerIsHiddenFromOtherPlayers() {
        PlayerMock hidden = server.addPlayer("Hidden");
        hidden.addAttachment(plugin, "spyglass.exempt", true);
        PlayerMock staff = staff();

        String reply = run(staff, "spy Hidden");

        assertTrue(reply.contains("cannot be inspected"), reply);
    }

    @Test
    void watchesStartStopAndAreListed() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        assertTrue(run(staff, "spy watch Notch chat blocks").contains("Watching Notch"));
        assertEquals(1, plugin.watches().size());

        String watching = run(staff, "spy watching");
        assertTrue(watching.contains("Notch"), watching);
        assertTrue(watching.contains("chat"), watching);

        assertTrue(run(staff, "spy unwatch Notch").contains("No longer watching"));
        assertEquals(0, plugin.watches().size());
    }

    @Test
    void aWatchCanBeSetOnSomebodyWhoIsNotHereYet() {
        PlayerMock staff = staff();

        String reply = run(staff, "spy watch Absentee");

        assertTrue(reply.contains("the watch starts when they join"), reply);
        assertEquals(1, plugin.watches().size());
    }

    @Test
    void whatAWatchedPlayerDoesReachesTheWatcher() {
        PlayerMock notch = server.addPlayer("Notch");
        PlayerMock staff = staff();
        plugin.watches().add(staff, notch.getUniqueId(), "Notch", Set.of(WatchCategory.CHAT));
        drain(staff);

        plugin.watches().emit(notch, WatchCategory.CHAT, "chat", "hello world");
        plugin.watches().emit(notch, WatchCategory.BLOCKS, "block break", "stone");

        String seen = drain(staff);
        assertTrue(seen.contains("hello world"), seen);
        assertTrue(seen.contains("Notch"), seen);
        assertFalse(seen.contains("block break"), "blocks was not one of the categories: " + seen);
    }

    @Test
    void aWatcherWhoLogsOutStopsWatching() {
        PlayerMock notch = server.addPlayer("Notch");
        PlayerMock staff = staff();
        plugin.watches().add(staff, notch.getUniqueId(), "Notch", Set.of(WatchCategory.CHAT));
        assertEquals(1, plugin.watches().size());

        // What the quit listener does when the watcher leaves.
        assertEquals(1, plugin.watches().forgetWatcher(staff.getUniqueId()));

        assertEquals(0, plugin.watches().size());
    }

    @Test
    void theConsoleIsAValidWatcher() {
        PlayerMock notch = server.addPlayer("Notch");

        Watch watch = plugin.watches().add(server.getConsoleSender(), notch.getUniqueId(),
                "Notch", Set.of(WatchCategory.CHAT));

        assertTrue(watch.isConsole());
        assertTrue(plugin.watches().isWatched(notch));
        // Emitting to the console must not throw even though nobody reads it here.
        plugin.watches().emit(notch, WatchCategory.CHAT, "chat", "hello");
    }

    @Test
    void dumpWritesAFile() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        String reply = run(staff, "spy dump Notch");

        assertTrue(reply.contains("Wrote"), reply);
        assertTrue(reply.contains(".txt"), reply);
    }

    @Test
    void dumpsListsWhatHasBeenWritten() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();
        run(staff, "spy dump Notch");

        String reply = run(staff, "spy dumps Notch");

        assertTrue(reply.contains("Notch-"), reply);
        assertTrue(reply.contains(".json"), reply);
    }

    @Test
    void diffReportsWhatChangedSinceTheLastDump() {
        PlayerMock notch = server.addPlayer("Notch");
        PlayerMock staff = staff();
        run(staff, "spy dump Notch");

        notch.setHealth(11.0D);
        String reply = run(staff, "spy diff Notch");

        assertTrue(reply.contains("diff"), reply);
        assertTrue(reply.contains("health"), reply);
        assertTrue(reply.contains("->"), reply);
    }

    @Test
    void diffNeedsSomethingToCompareAgainst() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        String reply = run(staff, "spy diff Notch");

        assertTrue(reply.contains("No dump of Notch"), reply);
    }

    @Test
    void findLooksThroughOnlineInventories() {
        PlayerMock notch = server.addPlayer("Notch");
        notch.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5));
        PlayerMock staff = staff();

        String found = run(staff, "spy find diamond");

        assertTrue(found.contains("Notch"), found);
        assertTrue(found.contains("diamond"), found);
    }

    @Test
    void findCanBePointedAtTheSaveFiles() {
        PlayerMock staff = staff();

        String reply = run(staff, "spy find beacon saves");

        // No world folder means no saves; the point is that it answers rather
        // than throwing, and that it says how much it looked at.
        assertTrue(reply.contains("Searching saves"), reply);
        assertTrue(reply.contains("save(s)"), reply);
    }

    @Test
    void watchLinesCanBeWrittenToAFile(@TempDir Path folder) throws IOException {
        WatchLog log = new WatchLog(plugin, folder.toFile());

        log.append("Notch", 1_700_000_000_000L, "chat", "where is everyone");
        log.append("Notch", 1_700_000_001_000L, "block break", "diamond_ore at world -814 12 341");
        log.close();

        List<String> lines = Files.readAllLines(folder.resolve("Notch.log"));
        assertEquals(2, lines.size(), () -> String.valueOf(lines));
        assertTrue(lines.get(0).contains("chat  where is everyone"), lines.get(0));
        assertTrue(lines.get(1).contains("block break  diamond_ore"), lines.get(1));
    }

    @Test
    void reloadRereadsTheConfig() {
        PlayerMock staff = staff();

        assertTrue(run(staff, "spy reload").contains("Reloaded configuration"));
        assertNotNull(plugin.settings());
    }

    @Test
    void tabCompletionOffersSectionsAndNames() {
        server.addPlayer("Notch");
        PlayerMock staff = staff();

        var completions = server.getPluginCommand("spy").tabComplete(staff, "spy", new String[] { "Notch", "inv" });

        assertTrue(completions.contains("inventory"), String.valueOf(completions));
    }
}
