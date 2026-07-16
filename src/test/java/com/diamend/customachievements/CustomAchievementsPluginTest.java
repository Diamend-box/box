package com.diamend.customachievements;

import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.data.PlayerData;
import org.bukkit.Material;
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

    @Test
    void multipleTriggersRequireAll() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("dual_objective");
        achievement.getRequirements().clear();
        achievement.getRequirements().add(new Requirement(TriggerType.BLOCK_BREAK, "STONE", 2));
        achievement.getRequirements().add(new Requirement(TriggerType.ENTITY_KILL, "ZOMBIE", 1));
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // First objective done, second not -> still locked.
        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 2);
        assertFalse(data.isCompleted("dual_objective"), "not complete until every objective is done");

        // Second objective done -> unlocked.
        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "ZOMBIE", 1);
        assertTrue(data.isCompleted("dual_objective"), "completes once all objectives are done");
    }

    @Test
    void playtimeGaugeCompletesAtThreshold() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("marathon");
        achievement.getRequirements().clear();
        achievement.getRequirements().add(new Requirement(TriggerType.PLAYTIME_HOURS, "ANY", 2));
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handleGauge(player, TriggerType.PLAYTIME_HOURS, null, 1);
        assertEquals(1, data.getProgress(PlayerData.requirementKey("marathon", 0)), "gauge reflects current value");
        assertFalse(data.isCompleted("marathon"));

        plugin.getAchievementService().handleGauge(player, TriggerType.PLAYTIME_HOURS, null, 2);
        assertTrue(data.isCompleted("marathon"), "completes at the threshold");
    }

    @Test
    void auraSkillsGaugeMatchesSkillAndLevel() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("master_miner");
        achievement.getRequirements().clear();
        achievement.getRequirements().add(new Requirement(TriggerType.AURASKILLS_LEVEL, "MINING", 25));
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handleGauge(player, TriggerType.AURASKILLS_LEVEL, "FARMING", 30);
        assertFalse(data.isCompleted("master_miner"), "a different skill should not count");

        plugin.getAchievementService().handleGauge(player, TriggerType.AURASKILLS_LEVEL, "MINING", 25);
        assertTrue(data.isCompleted("master_miner"), "reaching the level in the right skill completes it");
    }

    @Test
    void hiddenCategoryAndItemsSurviveReload() {
        Achievement achievement = new Achievement("secret_one");
        achievement.setHidden(true);
        achievement.setCategory("Combat");
        achievement.getRewardItems().add(new ItemStack(Material.DIAMOND, 3));
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementManager().load(); // reload from disk
        Achievement reloaded = plugin.getAchievementManager().get("secret_one");
        assertNotNull(reloaded);
        assertTrue(reloaded.isHidden(), "hidden flag persists");
        assertEquals("Combat", reloaded.getCategory(), "category persists");
        assertEquals(1, reloaded.getRewardItems().size(), "reward item persists");
        assertEquals(Material.DIAMOND, reloaded.getRewardItems().get(0).getType(), "reward item type persists");
    }

    @Test
    void itemRewardsAreGivenOnUnlock() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("gift");
        achievement.setTrigger(TriggerType.MANUAL);
        achievement.getRewardItems().add(new ItemStack(Material.DIAMOND, 2));
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().grant(player, achievement);
        assertTrue(player.getInventory().contains(Material.DIAMOND), "reward item should be delivered on unlock");
    }

    @Test
    void categoriesAreDetected() {
        Achievement achievement = new Achievement("cat_test");
        achievement.setCategory("Exploration");
        plugin.getAchievementManager().put(achievement);
        assertTrue(plugin.getAchievementManager().hasCategories(), "categories should be detected");
        assertTrue(plugin.getAchievementManager().categories().contains("Exploration"));
    }

    @Test
    void itemObtainMatchesByCustomName() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("compressed_iron");
        Requirement requirement = new Requirement(TriggerType.ITEM_OBTAIN, "Compressed Iron Ingots", 1);
        requirement.setMatchByName(true);
        achievement.getRequirements().clear();
        achievement.getRequirements().add(requirement);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        ItemStack named = new ItemStack(Material.IRON_BLOCK);
        var meta = named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Compressed Iron Ingots"));
        named.setItemMeta(meta);

        // Wrong material but right custom name -> should complete.
        plugin.getAchievementService().handleItem(player, TriggerType.ITEM_OBTAIN, named, 1);
        assertTrue(data.isCompleted("compressed_iron"), "custom-name match should complete regardless of material");
    }

    @Test
    void multiRequirementSurvivesSaveAndLoad() {
        // The seeded "well_prepared" has two requirements; force a reload so it
        // is parsed back from the written achievements.yml (new list format).
        plugin.getAchievementManager().load();
        Achievement reloaded = plugin.getAchievementManager().get("well_prepared");
        assertNotNull(reloaded, "seeded multi-objective achievement should exist");
        assertEquals(2, reloaded.getRequirements().size(),
                "multiple requirements should survive the save/load round-trip");
    }
}
