package com.diamend.boxtutorial;

import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.data.ProgressStore;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.guide.TutorialStep;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that make it a tutorial rather than a checklist: one step armed at
 * a time, counts that have to be reached, skipping that stays honest, and
 * progress that survives a restart.
 */
class TutorialProgressTest {

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
              sell-it:
                icon: EMERALD
                name: "Sell it"
                description: [ "Type the command." ]
                trigger: RUN_COMMAND
                target: "sell, shop"
                optional: true
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
        // A main world first: MockBukkit starts with none, and "home" is
        // defined as the first world that isn't the arena.
        server.addSimpleWorld("world");
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

    private Progress progress(PlayerMock player) {
        return plugin.store().get(player.getUniqueId());
    }

    private String currentId(PlayerMock player) {
        TutorialStep current = plugin.service().current(progress(player));
        return current == null ? null : current.id();
    }

    // ------------------------------------------------------------------
    // One step at a time
    // ------------------------------------------------------------------

    @Test
    void theTutorialStartsOnTheFirstStep() {
        PlayerMock player = started();

        assertTrue(plugin.service().isActive(progress(player)));
        assertEquals("read-me", currentId(player));
        assertEquals(3, plugin.tutorial().stepCount());
    }

    @Test
    void aLaterStepIsNotArmedYet() {
        // Otherwise mining while reading step one silently ticks off step two,
        // and the player is told they finished something they never saw.
        PlayerMock player = started();

        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 3);

        assertEquals(0, progress(player).count("mine-it"), "nothing counted");
        assertEquals("read-me", currentId(player), "and nothing moved");
    }

    @Test
    void everyStepIsArmedWhenTheOrderIsSwitchedOff() {
        plugin.getConfig().set("sequential", false);
        PlayerMock player = started();

        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 3);

        assertTrue(progress(player).isComplete("mine-it"), "free-order counts it straight away");
        assertEquals("read-me", currentId(player), "and step one is still waiting");
    }

    @Test
    void readingAStepMovesToTheNext() {
        PlayerMock player = started();

        assertTrue(plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true));

        assertTrue(progress(player).isComplete("read-me"));
        assertEquals("mine-it", currentId(player));
    }

    @Test
    void aStepThatCountsNeedsTheWholeCount() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 2);
        assertFalse(progress(player).isComplete("mine-it"), "two of three is not three");
        assertEquals(2, progress(player).count("mine-it"));

        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 1);
        assertTrue(progress(player).isComplete("mine-it"), "the third one finishes it");
    }

    @Test
    void theWrongBlockDoesNotCount() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "DIRT", 3);

        assertEquals(0, progress(player).count("mine-it"));
    }

    @Test
    void aCommandStepMatchesWhatWasTypedAfterIt() {
        // `target: sell` has to be finished by "/sell all", or the step is a
        // trivia question about argument-less commands.
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);
        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 3);

        plugin.service().record(player, StepTrigger.RUN_COMMAND, "/sell all", 1);

        assertTrue(progress(player).isComplete("sell-it"));
    }

    @Test
    void aDifferentCommandDoesNot() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);
        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 3);

        plugin.service().record(player, StepTrigger.RUN_COMMAND, "/sellwand", 1);

        assertFalse(progress(player).isComplete("sell-it"), "a longer word is a different command");
    }

    // ------------------------------------------------------------------
    // The events that feed it
    // ------------------------------------------------------------------

    @Test
    void breakingARealBlockCounts() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        World world = server.addSimpleWorld("mine-test");
        // Out of the arena: in there only the mine may be broken, and this test
        // is about the listener rather than the protection.
        player.teleport(world.getSpawnLocation());
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.STONE);
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(block, player));

        assertEquals(1, progress(player).count("mine-it"), "the listener is wired up");
    }

    @Test
    void typingARealCommandCounts() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);
        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 3);

        Bukkit.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, "/sell all"));

        assertTrue(progress(player).isComplete("sell-it"));
    }

    // ------------------------------------------------------------------
    // Finishing, skipping, stopping
    // ------------------------------------------------------------------

    @Test
    void doingTheLastStepFinishesTheTutorial() {
        PlayerMock player = started();
        for (TutorialStep step : plugin.tutorial().steps()) {
            plugin.service().completeStep(player, step, true);
        }

        Progress progress = progress(player);
        assertTrue(progress.finished(), "the last step ends it");
        assertFalse(plugin.service().isActive(progress), "and it stops nudging");
        assertNull(currentId(player), "there is no next step");
        assertEquals(100, plugin.service().percent(progress));
    }

    @Test
    void aSkippedStepIsNotATick() {
        // Skipping exists so nobody is trapped by a step this server can't
        // finish. It must not then claim they did it.
        PlayerMock player = started();

        assertTrue(plugin.service().skipStep(player, plugin.tutorial().step("read-me")));

        Progress progress = progress(player);
        assertTrue(progress.isSkipped("read-me"), "recorded as skipped");
        assertEquals("mine-it", currentId(player), "and it moved on");
    }

    @Test
    void skippingCanBeSwitchedOffExceptForOptionalSteps() {
        plugin.getConfig().set("allow-step-skip", false);

        assertFalse(plugin.service().canSkip(plugin.tutorial().step("read-me")),
                "a normal step can't be skipped when the server says so");
        assertTrue(plugin.service().canSkip(plugin.tutorial().step("sell-it")),
                "but an optional one always can");
    }

    @Test
    void stoppingTheTutorialSilencesItWithoutLosingAnything() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        plugin.service().stop(player);

        Progress progress = progress(player);
        assertFalse(plugin.service().isActive(progress), "no more nudging");
        assertTrue(progress.isComplete("read-me"), "and the step they did is still done");

        plugin.service().start(player, false);
        assertTrue(plugin.service().isActive(progress), "starting it again picks up where it was");
        assertEquals("mine-it", currentId(player));
    }

    @Test
    void restartingClearsTheBoard() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        plugin.service().start(player, true);

        assertFalse(progress(player).isComplete("read-me"), "a restart starts at the start");
        assertEquals("read-me", currentId(player));
    }

    @Test
    void resettingSomeoneWipesThem() {
        PlayerMock player = started();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);

        plugin.service().reset(player.getUniqueId());

        Progress progress = progress(player);
        assertFalse(progress.started(), "back to never having seen it");
        assertTrue(progress.completed().isEmpty());
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Test
    void progressSurvivesARestart() {
        PlayerMock player = started();
        UUID uuid = player.getUniqueId();
        plugin.service().completeStep(player, plugin.tutorial().step("read-me"), true);
        plugin.service().record(player, StepTrigger.BREAK_BLOCK, "STONE", 2);
        plugin.service().skipStep(player, plugin.tutorial().step("mine-it"));

        plugin.store().saveAndShutdown();
        ProgressStore reread = new ProgressStore(plugin);

        Progress progress = reread.get(uuid);
        assertTrue(progress.started(), "still mid-tutorial");
        assertTrue(progress.isComplete("read-me"), "completed steps come back");
        assertTrue(progress.isSkipped("mine-it"), "and so does the fact one was skipped");
    }

    @Test
    void anUnknownPlayerIsNotInvented() {
        assertFalse(plugin.store().known(UUID.randomUUID()),
                "asking about a stranger must not create a record for them");
    }

    @Test
    void aPlaytimeStepFinishesOnTheClock() throws IOException {
        // The one trigger nothing a player does can fire: the guide's own pass
        // has to notice the clock ran out.
        Files.writeString(new File(plugin.getDataFolder(), "tutorial.yml").toPath(), """
                steps:
                  hang-around:
                    icon: CLOCK
                    name: "Stick around"
                    description: [ "Play for five minutes." ]
                    trigger: PLAYTIME_MINUTES
                    amount: 5
                """);
        plugin.tutorial().load();
        PlayerMock player = started();

        progress(player).addPlayedMillis(6 * 60_000L);
        plugin.guide().tick();

        assertTrue(progress(player).isComplete("hang-around"),
                "six minutes is more than five");
        assertTrue(progress(player).finished(), "and that was the only step");
    }
}
