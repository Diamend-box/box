package com.diamend.boxtutorial;

import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.guide.Topic;
import com.diamend.boxtutorial.guide.TutorialStep;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin lifecycle and the content that ships with it, against a real
 * Bukkit/Paper API via MockBukkit.
 *
 * <p>The shipped {@code tutorial.yml} is the plugin's actual product — a server
 * owner who installs this and changes nothing gets exactly these steps — so it
 * is tested like code.
 */
class BoxTutorialPluginTest {

    private ServerMock server;
    private BoxTutorialPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxTutorialPlugin.class);
        // Titles and boss bars are client-side dressing this mock has no need
        // to implement; the tests here are about what the plugin decides.
        plugin.getConfig().set("show-welcome-title", false);
        plugin.getConfig().set("bossbar", false);
        // The guide read the config when it started; make it read this one.
        plugin.guide().start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnables() {
        assertTrue(plugin.isEnabled(), "plugin should enable without throwing");
        assertNotNull(plugin.tutorial(), "steps loaded");
        assertNotNull(plugin.store(), "progress store ready");
        assertNotNull(plugin.service(), "service ready");
    }

    @Test
    void theShippedTutorialParsesCleanly() {
        assertTrue(plugin.tutorial().warnings().isEmpty(),
                "the shipped tutorial.yml should load without warnings: "
                        + plugin.tutorial().warnings());
        assertTrue(plugin.tutorial().stepCount() >= 5,
                "the defaults should be a walkthrough, not a stub: "
                        + plugin.tutorial().stepCount());
        assertFalse(plugin.tutorial().topics().isEmpty(), "a glossary ships too");
    }

    @Test
    void everyShippedStepSaysWhatItWants() {
        for (TutorialStep step : plugin.tutorial().steps()) {
            assertFalse(step.name().isBlank(), step.id() + " needs a name");
            assertFalse(step.description().isEmpty(), step.id() + " needs a description");
            assertFalse(step.objective().isBlank(), step.id() + " needs a readable goal");
            if (step.trigger() == StepTrigger.REACH_LOCATION) {
                assertNotNull(step.place(), step.id() + " must point somewhere reachable");
            }
        }
    }

    @Test
    void noShippedStepCanTrapAPlayer() {
        // Every step is either something any server can finish (read it, break
        // blocks, play for a while) or is marked optional. A step wired to a
        // command a given server doesn't have must be skippable.
        for (TutorialStep step : plugin.tutorial().steps()) {
            boolean serverSpecific = step.trigger() == StepTrigger.RUN_COMMAND
                    || step.trigger() == StepTrigger.ENTER_WORLD
                    || step.trigger() == StepTrigger.REACH_LOCATION
                    || step.trigger() == StepTrigger.KILL_PLAYER
                    || step.trigger() == StepTrigger.MANUAL;
            if (serverSpecific) {
                assertTrue(step.optional(),
                        step.id() + " depends on this server's setup, so it must be optional");
            }
        }
    }

    @Test
    void everyShippedTopicExplainsSomething() {
        for (Topic topic : plugin.tutorial().topics()) {
            assertFalse(topic.title().isBlank(), topic.id() + " needs a title");
            assertFalse(topic.lines().isEmpty(), topic.id() + " needs some lines");
        }
        assertNotNull(plugin.tutorial().topic("combat"), "topics are findable by prefix");
    }

    @Test
    void theCommandIsRegisteredAndAnswersTheConsole() {
        assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tutorial steps"),
                "/tutorial steps should run from the console");
        assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tutorial status"),
                "/tutorial status should run from the console");
        assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tutorial reload"),
                "/tutorial reload should run from the console");
    }

    @Test
    void joiningStartsTheTutorialByItself() {
        plugin.getConfig().set("auto-start", true);
        plugin.getConfig().set("auto-start-existing", true);
        plugin.getConfig().set("join-delay-seconds", 1);

        PlayerMock player = server.addPlayer();
        Progress progress = plugin.store().get(player.getUniqueId());
        assertFalse(progress.started(), "nothing happens in the same tick as the join");

        server.getScheduler().performTicks(40);

        assertTrue(progress.started(), "the tutorial starts by itself shortly after joining");
        assertTrue(plugin.service().isActive(progress), "and it is running");
    }

    @Test
    void aServerThatSwitchesAutoStartOffGetsNoTutorialOnJoin() {
        plugin.getConfig().set("auto-start", false);
        plugin.getConfig().set("auto-start-existing", true);

        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(120);

        assertFalse(plugin.store().get(player.getUniqueId()).started(),
                "auto-start: false means the tutorial only ever begins on request");
    }

    @Test
    void someoneWhoTurnedItOffIsNotHandedItAgain() {
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(120);
        plugin.service().stop(player);

        // A second join: the record now says they stopped it.
        server.getPluginManager().callEvent(
                new org.bukkit.event.player.PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performTicks(120);

        Progress progress = plugin.store().get(player.getUniqueId());
        assertTrue(progress.stopped(), "stopping it sticks across a join");
        assertFalse(plugin.service().isActive(progress), "and it stays quiet");
    }
}
