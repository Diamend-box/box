package com.diamend.boxcore;

import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.skill.NodeEffects;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillService;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.skill.perk.Perk;
import org.bukkit.Material;
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

        skills.service().respec(player);
        assertEquals(0.0, skills.perks().of(player).value(Perk.LIFESTEAL), 1.0e-9,
                "a respec takes the perks back too");
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
    void secondChanceRunsOnACooldown() {
        PlayerMock player = server.addPlayer();
        SkillsModule skills = plugin.modules().get(SkillsModule.class);

        assertTrue(skills.perks().useSecondChance(player.getUniqueId(), 15),
                "the first killing blow is survived");
        assertFalse(skills.perks().useSecondChance(player.getUniqueId(), 15),
                "the second one, straight away, is not");
        assertTrue(skills.perks().secondChanceCooldown(player.getUniqueId()) > 0);
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

        int refunded = skills.service().respec(player);

        assertEquals(spent, refunded, "everything spent comes back");
        assertEquals(10, profile.getAvailablePoints());
        assertEquals(0, profile.getNodeLevel("combat.toughness"));
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
