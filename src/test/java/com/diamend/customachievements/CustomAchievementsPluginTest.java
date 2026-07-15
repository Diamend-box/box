package com.diamend.customachievements;

import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.data.PlayerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory behavioural tests that run the plugin against a real Bukkit/Paper
 * API implementation via MockBukkit. These exercise the plugin lifecycle, the
 * full award path (messages, sound, title, XP, broadcast, persistence) and
 * command routing without needing a live server.
 *
 * <p>MockBukkit downloads the matching server implementation at test runtime,
 * so this suite requires network access (it runs in CI, not in the offline
 * build sandbox).
 */
class CustomAchievementsPluginTest {

    private ServerMock server;
    private CustomAchievementsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(CustomAchievementsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginEnablesAndSeedsExamples() {
        assertTrue(plugin.isEnabled(), "plugin should enable without throwing");
        assertTrue(plugin.getAchievementManager().count() >= 5,
                "the example achievements should be seeded on first run");
        assertNotNull(plugin.getAchievementManager().get("getting_wood"), "getting_wood seeded");
        assertNotNull(plugin.getAchievementManager().get("hot_tourist"), "dimension example seeded");
    }

    @Test
    void manualGrantCompletesAndIsIdempotent() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("test_manual");
        achievement.setTrigger(TriggerType.MANUAL);
        plugin.getAchievementManager().put(achievement);

        assertTrue(plugin.getAchievementService().grant(player, achievement), "first grant succeeds");
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("test_manual"),
                "player should now own the achievement");
        assertFalse(plugin.getAchievementService().grant(player, achievement),
                "granting an already-owned achievement returns false");
    }

    @Test
    void progressAccumulatesThenCompletes() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("break_stone");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("STONE");
        achievement.setAmount(3);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        assertEquals(2, data.getProgress("break_stone"), "progress accumulates");
        assertFalse(data.isCompleted("break_stone"), "not complete before reaching the amount");

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        assertTrue(data.isCompleted("break_stone"), "completes once the required amount is reached");
    }

    @Test
    void wrongTargetDoesNotProgress() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("break_diamond");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("DIAMOND_ORE");
        achievement.setAmount(1);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        assertFalse(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("break_diamond"),
                "breaking the wrong block should not complete the achievement");
    }

    @Test
    void anyTargetMatchesEverything() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("any_kill");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("ANY");
        achievement.setAmount(1);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "SKELETON", 1);
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("any_kill"),
                "ANY target should match any entity");
    }

    @Test
    void listCommandRuns() {
        PlayerMock player = server.addPlayer();
        assertTrue(player.performCommand("achievements list"),
                "the list sub-command should dispatch successfully");
    }
}
