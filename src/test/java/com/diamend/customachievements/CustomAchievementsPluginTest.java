package com.diamend.customachievements;

import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.data.PlayerDataManager;
import org.bukkit.Material;
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
        assertEquals(2, data.getProgress(PlayerData.requirementKey("break_stone", 0)), "progress accumulates");
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

    @Test
    void leaderboardIgnoresDeletedAchievements() {
        PlayerMock player = server.addPlayer();
        Achievement real = new Achievement("still_here");
        real.setTrigger(TriggerType.MANUAL);
        plugin.getAchievementManager().put(real);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.setCompleted("still_here");
        data.setCompleted("was_deleted"); // no longer a real achievement

        java.util.Set<String> validIds = plugin.getAchievementManager().ids();
        assertEquals(1,
                plugin.getPlayerDataManager().completedCounts(validIds).get(player.getUniqueId()).intValue(),
                "only achievements that still exist should be counted");
        assertEquals(2,
                plugin.getPlayerDataManager().completedCounts(null).get(player.getUniqueId()).intValue(),
                "with no filter the raw completed count includes the stale id");
    }

    @Test
    void achievementsCanBeReordered() {
        Achievement first = new Achievement("order_a");
        Achievement second = new Achievement("order_b");
        plugin.getAchievementManager().put(first);
        plugin.getAchievementManager().put(second);

        // order_a was added before order_b.
        assertTrue(indexOf("order_a") < indexOf("order_b"), "initial insertion order");

        assertTrue(plugin.getAchievementManager().move("order_b", -1), "moving up succeeds");
        assertTrue(indexOf("order_b") < indexOf("order_a"), "order_b now precedes order_a");

        // The new order must survive a reload from disk.
        plugin.getAchievementManager().load();
        assertTrue(indexOf("order_b") < indexOf("order_a"), "reordering persists across reload");
    }

    private int indexOf(String id) {
        java.util.List<Achievement> all = plugin.getAchievementManager().asList();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void fullInventoryRewardGoesToStorage() {
        PlayerMock player = server.addPlayer();
        // Fill the inventory so a reward item can't be added directly.
        for (int i = 0; i < 100; i++) {
            if (!player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64)).isEmpty()) {
                break;
            }
        }
        Achievement gift = new Achievement("overflow_gift");
        gift.setTrigger(TriggerType.MANUAL);
        gift.getRewardItems().add(new ItemStack(Material.DIAMOND, 5));
        plugin.getAchievementManager().put(gift);

        plugin.getAchievementService().grant(player, gift);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        assertTrue(data.hasPendingRewards(), "a reward that doesn't fit should be kept for later");
        assertEquals(Material.DIAMOND, data.getPendingRewards().get(0).getType(),
                "the stored reward should be the overflow item");
    }

    @Test
    void pendingRewardsSurviveReload() {
        PlayerMock player = server.addPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.addPendingReward(new ItemStack(Material.EMERALD, 2));
        plugin.getPlayerDataManager().saveNow(data);

        // A fresh manager reads the file from disk with an empty cache.
        PlayerDataManager fresh = new PlayerDataManager(plugin);
        PlayerData reloaded = fresh.get(player.getUniqueId());
        assertTrue(reloaded.hasPendingRewards(), "pending rewards should persist to disk");
        assertEquals(Material.EMERALD, reloaded.getPendingRewards().get(0).getType(),
                "the stored reward item should round-trip");
    }

    @Test
    void reopenAndClaimCommandsDispatch() {
        PlayerMock player = server.addPlayer();
        assertTrue(player.performCommand("careopen"), "the standalone reopen command should dispatch");
        assertTrue(player.performCommand("achievements reopen"), "/ca reopen should dispatch");
        assertTrue(player.performCommand("achievements claim"), "/ca claim should dispatch");
    }

    @Test
    void groupTargetCountsEveryMaterialInTheFamily() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("lumberjack");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("#LOGS"); // any log, not one specific wood type
        achievement.setAmount(3);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "OAK_LOG", 1);
        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STRIPPED_SPRUCE_LOG", 1);
        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        assertEquals(2, data.getProgress(PlayerData.requirementKey("lumberjack", 0)),
                "different wood types all count, stone does not");

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "CRIMSON_STEM", 1);
        assertTrue(data.isCompleted("lumberjack"), "any three logs should finish it");
    }

    @Test
    void unlockShowsEveryDescriptionLineNotJustTheFirst() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("chop_sleep_repeat");
        achievement.setTrigger(TriggerType.MANUAL);
        achievement.setDisplayName("Chop, Sleep, Repeat.");
        achievement.setDescription(java.util.List.of(
                "<gray>A lumberjack's life for me.",   // flavour text
                "<yellow>Break 100 logs to earn it."));  // how you get it
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().grant(player, achievement);

        StringBuilder seen = new StringBuilder();
        String message;
        while ((message = player.nextMessage()) != null) {
            seen.append(message).append('\n');
        }
        assertTrue(seen.toString().contains("A lumberjack's life for me."),
                "the flavour line should be shown on unlock");
        assertTrue(seen.toString().contains("Break 100 logs to earn it."),
                "the how-to-earn-it line should be shown on unlock too, not just the first line");
    }

    @Test
    void mobGroupTargetCountsEveryHostileMob() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("monster_hunter");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("#HOSTILE"); // any hostile mob, not one specific type
        achievement.setAmount(3);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "ZOMBIE", 1);
        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "CREEPER", 1);
        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "COW", 1);
        assertEquals(2, data.getProgress(PlayerData.requirementKey("monster_hunter", 0)),
                "different hostile mobs all count, a cow does not");

        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "ENDERMAN", 1);
        assertTrue(data.isCompleted("monster_hunter"), "any three hostile mobs should finish it");
    }

    @Test
    void deathObjectiveCanRequireACause() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("hot_footed");
        achievement.setTrigger(TriggerType.PLAYER_DEATH);
        achievement.setTarget("LAVA");
        achievement.setAmount(2);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handleDeath(player, "FALL", null);
        assertEquals(0, data.getProgress(PlayerData.requirementKey("hot_footed", 0)),
                "a fall shouldn't count toward dying in lava");

        plugin.getAchievementService().handleDeath(player, "LAVA", null);
        plugin.getAchievementService().handleDeath(player, "LAVA", null);
        assertTrue(data.isCompleted("hot_footed"), "two lava deaths should complete it");
    }

    @Test
    void deathObjectiveCanRequireAKiller() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("creeper_problem");
        achievement.setTrigger(TriggerType.PLAYER_DEATH);
        achievement.setTarget("CREEPER");
        achievement.setAmount(1);
        plugin.getAchievementManager().put(achievement);

        // The damage cause of a creeper kill is an explosion, so the mob that
        // did it has to be matched separately from the cause.
        plugin.getAchievementService().handleDeath(player, "ENTITY_EXPLOSION", "CREEPER");
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("creeper_problem"),
                "being killed by a creeper should match a CREEPER target");
    }

    @Test
    void deathObjectiveAcceptsAMobFamily() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("fragile");
        achievement.setTrigger(TriggerType.PLAYER_DEATH);
        achievement.setTarget("#HOSTILE");
        achievement.setAmount(2);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handleDeath(player, "ENTITY_ATTACK", "ZOMBIE");
        plugin.getAchievementService().handleDeath(player, "FALL", null);
        assertEquals(1, data.getProgress(PlayerData.requirementKey("fragile", 0)),
                "only the mob death counts toward a hostile-mob target");

        plugin.getAchievementService().handleDeath(player, "ENTITY_EXPLOSION", "CREEPER");
        assertTrue(data.isCompleted("fragile"), "any two hostile mobs should finish it");
    }

    @Test
    void deathObjectiveWithoutATargetStillCountsEveryDeath() {
        // Objectives written before deaths had a cause leave the target unset.
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("clumsy");
        achievement.setTrigger(TriggerType.PLAYER_DEATH);
        achievement.setTarget("ANY");
        achievement.setAmount(2);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().handleDeath(player, "FALL", null);
        plugin.getAchievementService().handleDeath(player, "DROWNING", null);
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("clumsy"),
                "an untargeted death objective should count any death");
    }

    @Test
    void haveItemsCountsWhatIsInTheInventoryByCustomName() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("coin_collector");
        Requirement requirement = new Requirement(TriggerType.ITEM_HAVE, "Ancient Coin", 10);
        requirement.setMatchByName(true);
        achievement.getRequirements().clear();
        achievement.getRequirements().add(requirement);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        ItemStack coins = new ItemStack(Material.GOLD_NUGGET, 4);
        var meta = coins.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Ancient Coin"));
        coins.setItemMeta(meta);
        player.getInventory().addItem(coins);

        // Items can arrive with no event at all (/give), so the gauge reads the
        // inventory rather than counting receipts.
        plugin.getAchievementService().handleItemInventory(player);
        assertEquals(4, data.getProgress(PlayerData.requirementKey("coin_collector", 0)),
                "the gauge should reflect how many are held");
        assertFalse(data.isCompleted("coin_collector"), "4 of 10 isn't done yet");

        ItemStack more = coins.clone();
        more.setAmount(6);
        player.getInventory().addItem(more);
        plugin.getAchievementService().handleItemInventory(player);
        assertTrue(data.isCompleted("coin_collector"), "holding 10 should complete it");
    }

    @Test
    void haveItemsIgnoresItemsWithADifferentName() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("named_only");
        Requirement requirement = new Requirement(TriggerType.ITEM_HAVE, "Ancient Coin", 1);
        requirement.setMatchByName(true);
        achievement.getRequirements().clear();
        achievement.getRequirements().add(requirement);
        plugin.getAchievementManager().put(achievement);

        // Same material, no custom name: must not count.
        player.getInventory().addItem(new ItemStack(Material.GOLD_NUGGET, 64));
        plugin.getAchievementService().handleItemInventory(player);
        assertFalse(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("named_only"),
                "a plain gold nugget is not an Ancient Coin");
    }

    @Test
    void backfillCreditsStatisticsEarnedBeforeTheAchievementExisted() {
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 150);

        Achievement achievement = new Achievement("warmonger");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(200);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        assertEquals(150, data.getProgress(PlayerData.requirementKey("warmonger", 0)),
                "kills made before the achievement existed should already count");
        assertFalse(data.isCompleted("warmonger"), "150 of 200 isn't done yet");

        // The remaining 50 are earned live, on top of the seeded progress.
        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "PLAYER", 50);
        assertTrue(data.isCompleted("warmonger"), "only the remaining 50 should be needed");
    }

    @Test
    void backfillCompletesWhatWasAlreadyEarned() {
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 500);

        Achievement achievement = new Achievement("already_done");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(200);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("already_done"),
                "a target already passed should unlock straight away");
    }

    @Test
    void backfillNeverDoubleCountsProgressAlreadyUnderWay() {
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 150);

        Achievement achievement = new Achievement("no_double");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(200);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().backfill(player);
        plugin.getAchievementService().backfill(player); // e.g. a second join
        plugin.getAchievementService().backfill(player);
        assertEquals(150, data.getProgress(PlayerData.requirementKey("no_double", 0)),
                "running the backfill repeatedly must not stack progress");
    }

    @Test
    void customTriggerCountsUpAndUnlocks() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("boss_slayer");
        achievement.setTrigger(TriggerType.CUSTOM);
        achievement.setTarget("boss_kill");
        achievement.setAmount(3);
        plugin.getAchievementManager().put(achievement);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        plugin.getAchievementService().handleCustom(player, "boss_kill", 1);
        // Keys are matched case-insensitively, so a script needn't match the case.
        plugin.getAchievementService().handleCustom(player, "BOSS_KILL", 1);
        assertEquals(2, data.getProgress(PlayerData.requirementKey("boss_slayer", 0)));
        assertFalse(data.isCompleted("boss_slayer"), "2 of 3 isn't done yet");

        plugin.getAchievementService().handleCustom(player, "boss_kill", 1);
        assertTrue(data.isCompleted("boss_slayer"), "the third firing should unlock it");
    }

    @Test
    void customTriggerIgnoresAKeyItDoesNotListenFor() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("specific_key");
        achievement.setTrigger(TriggerType.CUSTOM);
        achievement.setTarget("boss_kill");
        achievement.setAmount(5);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().handleCustom(player, "quest_step", 4);
        assertEquals(0, plugin.getPlayerDataManager().get(player.getUniqueId())
                        .getProgress(PlayerData.requirementKey("specific_key", 0)),
                "an unrelated key must not advance this objective");
    }

    @Test
    void customTriggerCanSetAnAbsoluteValue() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("script_total");
        achievement.setTrigger(TriggerType.CUSTOM);
        achievement.setTarget("points");
        achievement.setAmount(10);
        plugin.getAchievementManager().put(achievement);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        // A script pushing its own running total must not accumulate here.
        plugin.getAchievementService().setCustom(player, "points", 4);
        plugin.getAchievementService().setCustom(player, "points", 6);
        assertEquals(6, data.getProgress(PlayerData.requirementKey("script_total", 0)));
        assertFalse(data.isCompleted("script_total"));

        plugin.getAchievementService().setCustom(player, "points", 10);
        assertTrue(data.isCompleted("script_total"), "reaching the target should unlock it");
    }

    @Test
    void backfillStillCreditsAnObjectiveThatAlreadyScoredOneKill() {
        // The obvious way to test a new "kill 1000 players" achievement is to go
        // and kill someone. That must not cost the player their existing 150.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 150);

        Achievement achievement = new Achievement("tested_it_first");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(1000);
        plugin.getAchievementManager().put(achievement);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        plugin.getAchievementService().handle(player, TriggerType.ENTITY_KILL, "PLAYER", 1);
        assertEquals(1, data.getProgress(PlayerData.requirementKey("tested_it_first", 0)));

        plugin.getAchievementService().backfill(player);
        assertEquals(150, data.getProgress(PlayerData.requirementKey("tested_it_first", 0)),
                "a kill scored before the first backfill must not lock the objective out of it");
    }

    @Test
    void backfillCountsPlayerKillsTowardAnAnyKillObjective() {
        // The live listener counts a killed player like any other entity, so a
        // wildcard objective has to seed from both statistics, not MOB_KILLS alone.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MOB_KILLS, 40);
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 10);

        Achievement achievement = new Achievement("anything_that_moves");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("ANY");
        achievement.setAmount(100);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        assertEquals(50, plugin.getPlayerDataManager().get(player.getUniqueId())
                        .getProgress(PlayerData.requirementKey("anything_that_moves", 0)),
                "both mob kills and player kills count toward killing anything");
    }

    @Test
    void backfillNeverPullsProgressBackwards() {
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 10);

        Achievement achievement = new Achievement("ahead_of_the_stat");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(200);
        plugin.getAchievementManager().put(achievement);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.setProgress(PlayerData.requirementKey("ahead_of_the_stat", 0), 75);
        plugin.getAchievementService().backfill(player);
        assertEquals(75, data.getProgress(PlayerData.requirementKey("ahead_of_the_stat", 0)),
                "seeding must never reduce progress the player already had");
    }

    @Test
    void backfillRedoReseedsAnObjectiveThatCameUpEmptyTheFirstTime() {
        // The once-per-player marker is set even when the statistic reads zero,
        // so without a way to force a retry a bad first run is permanent.
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("retry_me");
        achievement.setTrigger(TriggerType.ENTITY_KILL);
        achievement.setTarget("PLAYER");
        achievement.setAmount(1000);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        assertEquals(0, data.getProgress(PlayerData.requirementKey("retry_me", 0)));

        player.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 150);
        plugin.getAchievementService().seedWithReport(player, false);
        assertEquals(0, data.getProgress(PlayerData.requirementKey("retry_me", 0)),
                "without redo the objective stays seeded-once");

        plugin.getAchievementService().seedWithReport(player, true);
        assertEquals(150, data.getProgress(PlayerData.requirementKey("retry_me", 0)),
                "redo must re-read the statistic and credit it");
    }

    @Test
    void backfillReportSaysWhyAnObjectiveWasNotCredited() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("unanswerable");
        Requirement requirement = new Requirement(TriggerType.ITEM_OBTAIN, "Ancient Coin", 10);
        requirement.setMatchByName(true);
        achievement.getRequirements().clear();
        achievement.getRequirements().add(requirement);
        plugin.getAchievementManager().put(achievement);

        List<String> report = plugin.getAchievementService().seedWithReport(player, false);
        assertTrue(report.stream().anyMatch(line -> line.contains("unanswerable")
                        && line.contains("no statistic")),
                "the report should say why it couldn't be credited, got: " + report);
    }

    @Test
    void backfillLeavesObjectivesStatisticsCannotAnswer() {
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("named_quest_item");
        Requirement requirement = new Requirement(TriggerType.ITEM_OBTAIN, "Ancient Coin", 10);
        requirement.setMatchByName(true);
        achievement.getRequirements().clear();
        achievement.getRequirements().add(requirement);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        assertEquals(0, plugin.getPlayerDataManager().get(player.getUniqueId())
                        .getProgress(PlayerData.requirementKey("named_quest_item", 0)),
                "a custom item name has no statistic behind it, so it starts at zero");
    }

    @Test
    void backfillAddsUpEveryBlockIntoATotalBrokenCount() {
        // The server counts blocks mined one row per block and keeps no overall
        // total, so "break 10,000 blocks" can only be answered by adding them up.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE, 4000);
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.DIRT, 1500);

        Achievement achievement = new Achievement("ten_thousand");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("ANY");
        achievement.setAmount(10000);
        plugin.getAchievementManager().put(achievement);

        plugin.getAchievementService().backfill(player);
        assertEquals(5500, plugin.getPlayerDataManager().get(player.getUniqueId())
                        .getProgress(PlayerData.requirementKey("ten_thousand", 0)),
                "every block's count should add into one total");
    }

    @Test
    void backfillWontGuessAtHowMuchAnyItemWasConsumed() {
        // USE_ITEM counts blocks placed and tools swung as well as food eaten,
        // so its total across every item is not the number this objective wants.
        PlayerMock player = server.addPlayer();
        Achievement achievement = new Achievement("glutton");
        achievement.setTrigger(TriggerType.ITEM_CONSUME);
        achievement.setTarget("ANY");
        achievement.setAmount(100);
        plugin.getAchievementManager().put(achievement);

        List<String> report = plugin.getAchievementService().seedWithReport(player, false);
        assertTrue(report.stream().anyMatch(line -> line.contains("glutton")
                        && line.contains("no statistic")),
                "consuming any item should stay unseeded rather than take a wrong total, got: " + report);
    }

    @Test
    void unlockingAchievementsCountsTowardACapstone() {
        PlayerMock player = server.addPlayer();
        Achievement first = new Achievement("first");
        first.setTrigger(TriggerType.MANUAL);
        Achievement second = new Achievement("second");
        second.setTrigger(TriggerType.MANUAL);
        Achievement capstone = new Achievement("collector");
        capstone.setTrigger(TriggerType.ACHIEVEMENT_UNLOCK);
        capstone.setTarget("ANY");
        capstone.setAmount(2);
        plugin.getAchievementManager().put(first);
        plugin.getAchievementManager().put(second);
        plugin.getAchievementManager().put(capstone);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().grant(player, first);
        assertFalse(data.isCompleted("collector"), "one unlock is not yet two");

        plugin.getAchievementService().grant(player, second);
        assertTrue(data.isCompleted("collector"),
                "unlocking the second achievement should finish a capstone asking for two");
    }

    @Test
    void aCapstoneCountsOnlyItsOwnCategory() {
        PlayerMock player = server.addPlayer();
        Achievement mining = new Achievement("mining_one");
        mining.setTrigger(TriggerType.MANUAL);
        mining.setCategory("Mining");
        Achievement combat = new Achievement("combat_one");
        combat.setTrigger(TriggerType.MANUAL);
        combat.setCategory("Combat");
        Achievement capstone = new Achievement("master_miner");
        capstone.setTrigger(TriggerType.ACHIEVEMENT_UNLOCK);
        capstone.setTarget("Mining");
        capstone.setAmount(2);
        plugin.getAchievementManager().put(mining);
        plugin.getAchievementManager().put(combat);
        plugin.getAchievementManager().put(capstone);

        plugin.getAchievementService().grant(player, mining);
        plugin.getAchievementService().grant(player, combat);
        assertEquals(1, plugin.getPlayerDataManager().get(player.getUniqueId())
                        .getProgress(PlayerData.requirementKey("master_miner", 0)),
                "an achievement from another category should not count");
    }

    @Test
    void aCapstoneCreditsAchievementsUnlockedBeforeItExisted() {
        // The count is read from the player's own data rather than accumulated,
        // so a capstone added today sees everything already unlocked.
        PlayerMock player = server.addPlayer();
        Achievement old = new Achievement("ancient");
        old.setTrigger(TriggerType.MANUAL);
        plugin.getAchievementManager().put(old);
        plugin.getAchievementService().grant(player, old);

        Achievement capstone = new Achievement("late_arrival");
        capstone.setTrigger(TriggerType.ACHIEVEMENT_UNLOCK);
        capstone.setTarget("ANY");
        capstone.setAmount(1);
        plugin.getAchievementManager().put(capstone);

        plugin.getAchievementService().handleUnlockCount(player);
        assertTrue(plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted("late_arrival"),
                "a capstone should credit unlocks that happened before it was created");
    }

    @Test
    void capstonesChainWithoutRunningAway() {
        // Awarding one capstone is itself an unlock, so it can finish the next.
        PlayerMock player = server.addPlayer();
        Achievement seed = new Achievement("seed");
        seed.setTrigger(TriggerType.MANUAL);
        Achievement one = new Achievement("cap_one");
        one.setTrigger(TriggerType.ACHIEVEMENT_UNLOCK);
        one.setAmount(1);
        Achievement two = new Achievement("cap_two");
        two.setTrigger(TriggerType.ACHIEVEMENT_UNLOCK);
        two.setAmount(2);
        plugin.getAchievementManager().put(seed);
        plugin.getAchievementManager().put(one);
        plugin.getAchievementManager().put(two);

        plugin.getAchievementService().grant(player, seed);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        assertTrue(data.isCompleted("cap_one"), "one unlock finishes the capstone asking for one");
        assertTrue(data.isCompleted("cap_two"),
                "that capstone is itself an unlock, finishing the one asking for two");
    }

    @Test
    void anImprovedBackfillReexaminesWhatItCouldNotAnswerBefore() {
        // An objective is marked seeded even when the read came back empty, so
        // without the schema in the marker the players an improvement is for are
        // exactly the ones shut out of it.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE, 900);

        Achievement achievement = new Achievement("digger");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("ANY");
        achievement.setAmount(5000);
        plugin.getAchievementManager().put(achievement);

        // What an older version left behind: seeded, under its own schema.
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.markBackfilled(PlayerData.requirementKey("digger", 0) + "@BLOCK_BREAK:ANY@v1");

        plugin.getAchievementService().backfill(player);
        assertEquals(900, data.getProgress(PlayerData.requirementKey("digger", 0)),
                "a marker from an older schema should not shut the objective out of the new answer");
    }

    @Test
    void aLockedAchievementEarnsNothingUntilItsPrerequisiteIsUnlocked() {
        PlayerMock player = server.addPlayer();
        Achievement first = new Achievement("stone_age");
        first.setTrigger(TriggerType.BLOCK_BREAK);
        first.setTarget("STONE");
        first.setAmount(1);
        Achievement second = new Achievement("iron_age");
        second.setTrigger(TriggerType.BLOCK_BREAK);
        second.setTarget("IRON_ORE");
        second.setAmount(1);
        second.setRequires(new java.util.ArrayList<>(List.of("stone_age")));
        plugin.getAchievementManager().put(first);
        plugin.getAchievementManager().put(second);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "IRON_ORE", 1);
        assertFalse(data.isCompleted("iron_age"), "a locked achievement should not be earnable");
        assertEquals(0, data.getProgress(PlayerData.requirementKey("iron_age", 0)),
                "a locked achievement should not even accumulate progress");

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "STONE", 1);
        assertTrue(data.isCompleted("stone_age"), "the prerequisite completes normally");

        plugin.getAchievementService().handle(player, TriggerType.BLOCK_BREAK, "IRON_ORE", 1);
        assertTrue(data.isCompleted("iron_age"), "once unlocked, the gated achievement works");
    }

    @Test
    void grantGoesAroundThePrerequisiteGate() {
        PlayerMock player = server.addPlayer();
        Achievement gated = new Achievement("gated");
        gated.setTrigger(TriggerType.MANUAL);
        gated.setRequires(new java.util.ArrayList<>(List.of("never_unlocked")));
        plugin.getAchievementManager().put(gated);

        assertTrue(plugin.getAchievementService().grant(player, gated),
                "an admin grant should still work on a locked achievement");
    }

    @Test
    void aResetSurvivesAnImprovedBackfill() {
        // The two fixes meet here: a reset has to stick even though a better
        // reader deliberately reconsiders every objective it sees.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE, 900);
        Achievement achievement = new Achievement("digger_reset");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("ANY");
        achievement.setAmount(5000);
        plugin.getAchievementManager().put(achievement);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        plugin.getAchievementService().backfill(player);
        assertEquals(900, data.getProgress(PlayerData.requirementKey("digger_reset", 0)), "seeded once");

        data.reset();
        plugin.getAchievementService().backfill(player);
        assertEquals(0, data.getProgress(PlayerData.requirementKey("digger_reset", 0)),
                "a reset player should not be seeded straight back");

        // Now stand in for a later version whose marker keys have all changed.
        data.getBackfilled().clear();
        plugin.getAchievementService().backfill(player);
        assertEquals(0, data.getProgress(PlayerData.requirementKey("digger_reset", 0)),
                "and a reader that reconsiders everything must not walk through the reset");
    }

    @Test
    void aResetPlayerIsStillSeededForAnAchievementAddedAfterwards() {
        // The reset suppresses what they'd already been credited for, not the
        // rest of their life on the server.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE, 700);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.reset();

        Achievement fresh = new Achievement("brand_new");
        fresh.setTrigger(TriggerType.BLOCK_BREAK);
        fresh.setTarget("ANY");
        fresh.setAmount(5000);
        plugin.getAchievementManager().put(fresh);

        plugin.getAchievementService().backfill(player);
        assertEquals(700, data.getProgress(PlayerData.requirementKey("brand_new", 0)),
                "an achievement created after the reset should still seed");
    }

    @Test
    void backfillReadsAnOfflinePlayersStatistics() {
        // Statistics live on disk, and the player stuck at zero is often exactly
        // the one who has logged off.
        PlayerMock player = server.addPlayer();
        player.setStatistic(org.bukkit.Statistic.MINE_BLOCK, Material.STONE, 1200);
        java.util.UUID uuid = player.getUniqueId();

        Achievement achievement = new Achievement("offline_digger");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("ANY");
        achievement.setAmount(5000);
        plugin.getAchievementManager().put(achievement);

        org.bukkit.OfflinePlayer offline = server.getOfflinePlayer(uuid);
        plugin.getAchievementService().seedWithReport(offline, false);
        assertEquals(1200, plugin.getPlayerDataManager().get(uuid)
                        .getProgress(PlayerData.requirementKey("offline_digger", 0)),
                "an offline player's statistics should still be readable");
    }

    @Test
    void groupTargetSurvivesSaveAndLoad() {
        Achievement achievement = new Achievement("miner");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setTarget("#ORES");
        achievement.setAmount(50);
        plugin.getAchievementManager().put(achievement); // writes achievements.yml

        // '#' opens a comment in YAML, so the target has to come back quoted.
        plugin.getAchievementManager().load();
        assertEquals("#ORES", plugin.getAchievementManager().get("miner").getTarget(),
                "a group target should survive the achievements.yml round-trip");
    }
}
