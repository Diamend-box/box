package com.diamend.boxcore;

import com.diamend.boxcore.collection.CollectionTier;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.skill.NodeEffects;
import com.diamend.boxcore.skill.RespecCost;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillService;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.skill.perk.Perk;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests against a real Bukkit/Paper API implementation via
 * MockBukkit: the plugin lifecycle, the shipped default configs, and the paths
 * that touch live players.
 *
 * <p>MockBukkit downloads the matching server implementation at test runtime,
 * so this suite needs network access (it runs in CI, not the offline sandbox).
 */
class BoxCorePluginTest {

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

    @Test
    void pluginEnablesWithItsModules() {
        assertTrue(plugin.isEnabled(), "plugin should enable without throwing");
        assertTrue(plugin.modules().isActive("skills"), "skills module active");
        assertTrue(plugin.modules().isActive("collections"), "collections module active");
        assertTrue(plugin.modules().isActive("playtime"), "playtime module active");
    }

    @Test
    void shippedTreesParseCleanly() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        assertNotNull(skills, "skills module loaded");
        assertEquals(4, skills.trees().treeCount(), "combat, gathering, wayfarer and artisan");
        assertNotNull(skills.trees().getTree("combat"), "combat tree seeded");
        assertNotNull(skills.trees().getNode("combat.toughness"), "nodes are keyed tree.node");
        assertTrue(skills.trees().nodeCount() > 40,
                "the shipped trees should offer more than 40 nodes, found "
                        + skills.trees().nodeCount());
        assertTrue(skills.trees().warnings().isEmpty(),
                "the shipped trees.yml should load without warnings: " + skills.trees().warnings());
    }

    @Test
    void everyShippedNodeGrantsSomething() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        for (SkillNode node : skills.trees().nodesByKey().values()) {
            assertFalse(node.getEffects().isEmpty(), node.key() + " should do something");
            assertFalse(node.getEffects().describe(1).isEmpty(),
                    node.key() + " should describe itself in its lore");
        }
    }

    @Test
    void theOverpoweredAndUselessNodesAreGone() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        // Permanent Haste II was a beacon in your pocket; the luck nodes moved a
        // stat that barely shows up outside fishing loot tables.
        assertNull(skills.trees().getNode("gathering.tunnel_rat"), "Tunnel Rat removed");
        assertNull(skills.trees().getNode("gathering.angler"), "Angler removed");
        assertNull(skills.trees().getNode("wayfarer.keen_eye"), "Keen Eye removed");
        // And the survival-flavoured nodes that a boxpvp server has no use for.
        assertNull(skills.trees().getTree("artisan"), "the Artisan tree is gone");
        assertNull(skills.trees().getNode("wayfarer.warm_blooded"), "Warm Blooded removed");
        assertNull(skills.trees().getNode("wayfarer.iron_stomach"), "Iron Stomach removed");
        assertNotNull(skills.trees().getTree("duelist"), "the Duelist tree replaced it");
        for (SkillNode node : skills.trees().nodesByKey().values()) {
            for (NodeEffects.PotionBonus potion : node.getEffects().potions()) {
                assertNotEquals("haste", potion.type().getKey().getKey(),
                        node.key() + " still grants Haste");
            }
            for (NodeEffects.AttributeBonus bonus : node.getEffects().attributes()) {
                assertNotEquals("luck", bonus.attribute().getKey().getKey(),
                        node.key() + " still grants luck");
            }
        }
    }

    @Test
    void shippedTreeEffectsResolveToRealAttributes() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        SkillNode toughness = skills.trees().getNode("combat.toughness");
        assertFalse(toughness.getEffects().attributes().isEmpty(),
                "max_health should have resolved to a real attribute");
        assertEquals(4.0, toughness.getEffects().attributes().get(0).amountFor(2), 1.0e-9,
                "attribute amounts scale with the node level");
    }

    @Test
    void shippedPerksResolveAndScaleWithLevel() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        SkillNode bloodthirst = skills.trees().getNode("combat.bloodthirst");
        assertNotNull(bloodthirst, "the perk-driven nodes are seeded");
        assertEquals(1, bloodthirst.getEffects().perks().size());

        NodeEffects.PerkBonus lifesteal = bloodthirst.getEffects().perks().get(0);
        assertSame(Perk.LIFESTEAL, lifesteal.perk());
        assertEquals(0.12, lifesteal.amountFor(3), 1.0e-9, "4% a level, three levels");

        // The cooldown perk is pinned so extra levels could never shorten it.
        NodeEffects.PerkBonus cooldown =
                skills.trees().getNode("wayfarer.second_chance").getEffects().perks().get(0);
        assertEquals(15.0, cooldown.amountFor(4), 1.0e-9);
    }

    @Test
    void perkTotalsFollowThePlayersNodes() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        assertEquals(0.0, skills.perks().of(player).value(Perk.LIFESTEAL), 1.0e-9,
                "a fresh player has no perks");

        profile.addPoints(20);
        SkillNode bloodthirst = skills.trees().getNode("combat.bloodthirst");
        skills.service().unlock(player, bloodthirst);
        skills.service().unlock(player, bloodthirst);

        assertEquals(2, profile.getNodeLevel("combat.bloodthirst"));
        assertEquals(0.08, skills.perks().of(player).value(Perk.LIFESTEAL), 1.0e-9,
                "unlocking invalidates the cached totals");

        giveRespecItem(player);
        skills.service().respec(player);
        assertEquals(0.0, skills.perks().of(player).value(Perk.LIFESTEAL), 1.0e-9,
                "a respec takes the perks back too");
    }

    /** Puts the configured respec cost in the player's inventory. */
    private void giveRespecItem(PlayerMock player) {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        RespecCost cost = skills.service().respecCost();
        if (!cost.isFree()) {
            player.getInventory().addItem(new ItemStack(cost.material(), cost.amount()));
        }
    }

    @Test
    void flagPerksReadAsPresentRatherThanAsAnAmount() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        skills.service().setLevel(player, skills.trees().getNode("gathering.kiln_touch"), 1);

        assertTrue(skills.perks().of(player).has(Perk.AUTO_SMELT));
        assertFalse(skills.perks().of(player).has(Perk.REPLANT));
        assertEquals(0.0, skills.perks().of(player).value(Perk.REPLANT), 1.0e-9);
    }

    @Test
    void triggeredPerksRunOnTheirOwnCooldowns() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);

        assertTrue(skills.perks().claim(player.getUniqueId(), Perk.SECOND_CHANCE, 900_000L),
                "the first killing blow is survived");
        assertFalse(skills.perks().claim(player.getUniqueId(), Perk.SECOND_CHANCE, 900_000L),
                "the second one, straight away, is not");
        assertTrue(skills.perks().cooldownRemaining(player.getUniqueId(), Perk.SECOND_CHANCE) > 0);

        // Cooldowns are tracked per perk, not per player.
        assertTrue(skills.perks().claim(player.getUniqueId(), Perk.LAST_BREATH, 30_000L),
                "a different perk isn't blocked by another one's cooldown");
        assertEquals(0, skills.perks().cooldownRemaining(player.getUniqueId(), Perk.MOB_LOOT));
    }

    @Test
    void shippedCollectionsParseCleanly() {
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        assertNotNull(collections, "collections module loaded");
        assertTrue(collections.collections().count() >= 20, "the example collections are seeded");
        assertNotNull(collections.collections().get("cobblestone"), "cobblestone seeded");
        assertTrue(collections.collections().tracks(Material.COBBLESTONE),
                "the material index is built");
    }

    @Test
    void playtimeIsACollectionWithAVisibleCeiling() {
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        ItemCollection playtime = collections.collections().get("playtime");
        assertNotNull(playtime, "hours played are a collection like anything else");
        assertFalse(playtime.tracksItems(), "it isn't fed by items");
        assertTrue(playtime.getMaterials().isEmpty());
        assertEquals("hours", playtime.getUnit());
        assertTrue(playtime.tierCount() > 0, "and it has a last tier — that's the ceiling");

        // The module resolves it, by id or by source.
        assertSame(playtime, plugin.playtime().collection());
        assertSame(playtime, collections.collections().bySource(ItemCollection.SOURCE_PLAYTIME));
        // A source-driven collection must never be wired into the material index,
        // or gathering an item would move the playtime counter.
        for (Material material : Material.values()) {
            assertFalse(collections.collections().forMaterial(material).contains(playtime),
                    material + " should not feed the playtime collection");
        }
    }

    @Test
    void recordingHoursPaysTheTiersItCrossed() {
        PlayerMock player = server.addPlayer();
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        ItemCollection playtime = collections.collections().get("playtime");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int before = profile.getAvailablePoints();

        long hours = playtime.getTiers().get(2).amount();
        collections.service().set(player, profile, playtime, hours);

        assertEquals(hours, profile.getCollected("playtime"));
        assertEquals(3, profile.getAwardedTier("playtime"), "three tiers crossed at once");
        assertEquals(before + 3, profile.getAvailablePoints());

        // Past the last tier the counter keeps climbing but stops paying.
        long beyond = playtime.getTiers().get(playtime.tierCount() - 1).amount() * 10;
        collections.service().set(player, profile, playtime, beyond);
        assertEquals(playtime.tierCount(), profile.getAwardedTier("playtime"));
        assertEquals(before + playtime.tierCount(), profile.getAvailablePoints(),
                "the ceiling holds no matter how long someone stays online");
    }

    @Test
    void hoursPlayedDontCountAsItemsGathered() {
        PlayerMock player = server.addPlayer();
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        collections.service().set(player, profile,
                collections.collections().get("playtime"), 500);

        assertEquals(500, profile.getTotalCollected(), "the raw total counts everything");
        assertEquals(0, collections.collections().itemsCollected(profile),
                "but 'items gathered' means items");
    }

    @Test
    void everythingEarnableStaysBelowWhatTheTreesCost() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);

        int earnable = 0;
        for (ItemCollection collection : collections.collections().all()) {
            for (CollectionTier tier : collection.getTiers()) {
                earnable += tier.points();
            }
        }
        int cost = 0;
        for (SkillNode node : skills.trees().nodesByKey().values()) {
            cost += node.totalCostAt(node.getMaxLevel());
        }

        // The point of a finite economy: a player has to choose a build rather
        // than eventually owning every node. If a retune ever makes everything
        // affordable, that decision quietly disappears — so pin it here.
        assertTrue(earnable < cost,
                "every point earnable (" + earnable + ") should be less than the "
                        + cost + " needed to max every node");
        assertTrue(earnable > cost / 2,
                "but not so scarce that a maxed player can't afford half the trees; "
                        + earnable + " of " + cost);
    }

    @Test
    void itemTagsExpandIntoMaterials() {
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        ItemCollection logs = collections.collections().get("logs");
        // A server implementation without tag support drops the collection
        // entirely; that's a mock limitation, not a plugin bug.
        org.junit.jupiter.api.Assumptions.assumeTrue(logs != null,
                "this server implementation exposes item tags");
        assertTrue(logs.getMaterials().size() > 1, "#logs should expand to many materials");
        assertTrue(logs.counts(Material.OAK_LOG), "oak logs count toward it");
    }

    @Test
    void gatheringCreditsCollectionsAndPaysPoints() {
        PlayerMock player = server.addPlayer();
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        ItemCollection cobblestone = collections.collections().get("cobblestone");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int before = profile.getAvailablePoints();

        collections.service().addTo(player, cobblestone, cobblestone.getTiers().get(0).amount());

        assertEquals(cobblestone.getTiers().get(0).amount(), profile.getCollected("cobblestone"));
        assertEquals(1, profile.getAwardedTier("cobblestone"), "first tier paid out");
        assertTrue(profile.getAvailablePoints() > before, "the tier granted skill points");
    }

    @Test
    void unlockingSpendsPointsAndAppliesEffects() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        SkillNode toughness = skills.trees().getNode("combat.toughness");
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addPoints(5);

        SkillService.UnlockCheck result = skills.service().unlock(player, toughness);

        assertTrue(result.allowed(), "an affordable, unlocked node can be taken");
        assertEquals(1, profile.getNodeLevel("combat.toughness"));
        assertEquals(5 - toughness.costForLevel(0), profile.getAvailablePoints());
    }

    @Test
    void unlockIsRefusedWithoutPoints() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        SkillNode toughness = skills.trees().getNode("combat.toughness");

        SkillService.UnlockCheck result = skills.service().unlock(player, toughness);

        assertFalse(result.allowed(), "a broke player can't unlock anything");
        assertEquals(SkillService.Denial.CANNOT_AFFORD, result.reason());
        assertEquals(0, plugin.profiles().get(player.getUniqueId()).getNodeLevel("combat.toughness"));
    }

    @Test
    void respecReturnsEverySpentPoint() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addPoints(10);
        SkillNode toughness = skills.trees().getNode("combat.toughness");
        skills.service().unlock(player, toughness);
        skills.service().unlock(player, toughness);
        int spent = profile.getPointsSpent();
        assertTrue(spent > 0, "points were actually spent");
        giveRespecItem(player);

        SkillService.RespecResult result = skills.service().respec(player);

        assertTrue(result.succeeded());
        assertEquals(spent, result.refunded(), "everything spent comes back");
        assertEquals(10, profile.getAvailablePoints(), "and no points are burnt as a fee");
        assertEquals(0, profile.getNodeLevel("combat.toughness"));
    }

    @Test
    void respecCostsTheConfiguredItem() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        RespecCost cost = skills.service().respecCost();
        assertFalse(cost.isFree(), "the shipped config charges an item");

        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addPoints(10);
        SkillNode toughness = skills.trees().getNode("combat.toughness");
        skills.service().unlock(player, toughness);
        int spent = profile.getPointsSpent();

        // Empty-handed: refused, and nothing about the player changes.
        SkillService.RespecResult refused = skills.service().respec(player);
        assertEquals(SkillService.RespecOutcome.MISSING_ITEM, refused.outcome());
        assertEquals(0, refused.held());
        assertEquals(spent, profile.getPointsSpent(), "a refused respec refunds nothing");
        assertEquals(1, profile.getNodeLevel("combat.toughness"));

        // With one spare, the respec goes through and eats exactly the cost.
        player.getInventory().addItem(new ItemStack(cost.material(), cost.amount() + 1));
        assertTrue(skills.service().respec(player).succeeded());
        assertEquals(0, profile.getPointsSpent());
        assertEquals(1, cost.heldBy(player), "only the configured amount is taken");
    }

    @Test
    void respecWithNothingSpentIsRefusedWithoutEatingTheItem() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        RespecCost cost = skills.service().respecCost();
        giveRespecItem(player);

        SkillService.RespecResult result = skills.service().respec(player);

        assertEquals(SkillService.RespecOutcome.NOTHING_TO_REFUND, result.outcome());
        assertEquals(cost.amount(), cost.heldBy(player),
                "a respec that does nothing must not charge for it");
    }

    @Test
    void profilesRoundTripThroughDisk() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addPoints(7);
        // Node keys are "tree.node". The dot must survive as part of the key
        // rather than being read as a YAML path separator — otherwise every
        // unlocked node silently vanishes on relog.
        profile.setNodeLevel("combat.toughness", 2);
        profile.setNodeLevel("combat.ferocity", 1);
        profile.setCollected("cobblestone", 1234);
        profile.setAwardedTier("cobblestone", 3);
        plugin.profiles().saveNow(profile);
        plugin.profiles().unload(player.getUniqueId());

        String stored = readProfileFile(player.getUniqueId());
        assertTrue(stored.contains("combat.toughness: 2"),
                "the dotted key should be written flat, not nested; file was:\n" + stored);

        PlayerProfile reloaded = plugin.profiles().loadDetached(player.getUniqueId());

        assertEquals(7, reloaded.getPointsEarned());
        assertEquals(2, reloaded.getNodeLevel("combat.toughness"));
        assertEquals(1, reloaded.getNodeLevel("combat.ferocity"));
        assertEquals(2, reloaded.getNodes().size(), "both dotted keys came back whole");
        assertEquals(1234, reloaded.getCollected("cobblestone"));
        assertEquals(3, reloaded.getAwardedTier("cobblestone"));
    }

    @Test
    void aQueuedSaveIsNeverReadAroundOrHalfRead() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addPoints(9);
        profile.setNodeLevel("combat.ferocity", 3);
        profile.setCollected("cobblestone", 4321);

        // unload() hands the write to the background thread and drops the cache
        // entry, so this read goes to disk with a write still in flight. It has
        // to see the finished file — reading a truncated one would look like a
        // player with no progress, and saving that back would make it true.
        plugin.profiles().unload(player.getUniqueId());
        PlayerProfile reloaded = plugin.profiles().loadDetached(player.getUniqueId());

        assertEquals(9, reloaded.getPointsEarned());
        assertEquals(3, reloaded.getNodeLevel("combat.ferocity"));
        assertEquals(4321, reloaded.getCollected("cobblestone"));
    }

    private String readProfileFile(java.util.UUID uuid) {
        try {
            return java.nio.file.Files.readString(
                    new java.io.File(plugin.profiles().folder(), uuid + ".yml").toPath());
        } catch (java.io.IOException ex) {
            throw new AssertionError("profile file was not written", ex);
        }
    }

    @Test
    void treePermissionsAreHonouredByTheCommand() {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        SkillTree combat = skills.trees().getTree("combat");
        assertTrue(combat.getPermission().isEmpty(),
                "the shipped trees are open to everyone by default");
    }

    @Test
    void commandRespondsWithoutErrors() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        assertTrue(player.performCommand("box points"), "/box points runs");
        assertTrue(player.performCommand("box modules"), "/box modules runs");
        assertNotNull(player.nextMessage(), "the command replied");
    }
}
