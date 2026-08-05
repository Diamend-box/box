package com.diamend.boxtutorial;

import com.diamend.boxtutorial.arena.Instance;
import com.diamend.boxtutorial.arena.MineSpec;
import com.diamend.boxtutorial.arena.TradeSpec;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.guide.TutorialStep;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The practice yard: that each player gets their own, that it gets built, that
 * only the mines can be broken, and that the shipped ladder can actually be
 * climbed with what the arena provides.
 */
class ArenaTest {

    private static final String ARENA_WORLD = "tutorial_arena";

    private ServerMock server;
    private BoxTutorialPlugin plugin;
    private World arena;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // The plugin adopts an existing world of this name rather than creating
        // one, so the tests never go near world generation.
        arena = server.addSimpleWorld(ARENA_WORLD);
        plugin = MockBukkit.load(BoxTutorialPlugin.class);
        plugin.getConfig().set("show-welcome-title", false);
        plugin.getConfig().set("completion.title", false);
        plugin.getConfig().set("bossbar", false);
        plugin.getConfig().set("auto-start", false);
        plugin.guide().start();
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

    private Block block(Instance instance, int x, int y, int z) {
        Location origin = instance.origin();
        return arena.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y,
                origin.getBlockZ() + z);
    }

    // ------------------------------------------------------------------
    // The shipped arena
    // ------------------------------------------------------------------

    @Test
    void theShippedArenaParsesCleanly() {
        assertTrue(plugin.blueprint().warnings().isEmpty(),
                "the shipped arena config should load without warnings: "
                        + plugin.blueprint().warnings());
        assertFalse(plugin.blueprint().mines().isEmpty(), "there is somewhere to mine");
        assertFalse(plugin.blueprint().trades().isEmpty(), "and something to trade with");
    }

    @Test
    void everyStepAsksForSomethingTheArenaCanProvide() {
        // The failure this catches is a tutorial that can't be finished: a step
        // wanting a block no mine contains, or gear no trade sells. Nothing in
        // the arena tells the player that, so nothing but a test can.
        for (TutorialStep step : plugin.tutorial().steps()) {
            if (step.trigger() == StepTrigger.BREAK_BLOCK) {
                for (String target : step.target().split(",")) {
                    Material material = Material.matchMaterial(target.trim());
                    assertNotNull(material, step.id() + " wants '" + target + "'");
                    assertTrue(minedSomewhere(material),
                            step.id() + " wants " + material + ", which no mine contains");
                }
            }
            if (step.trigger() == StepTrigger.BUY_ITEM || step.trigger() == StepTrigger.HAVE_ITEM) {
                Material material = Material.matchMaterial(step.target().trim());
                assertNotNull(material, step.id() + " wants '" + step.target() + "'");
                assertTrue(sold(material),
                        step.id() + " wants " + material + ", which the trader doesn't sell");
            }
        }
    }

    private boolean minedSomewhere(Material material) {
        for (MineSpec mine : plugin.blueprint().mines()) {
            if (mine.produces(material)) {
                return true;
            }
        }
        return false;
    }

    private boolean sold(Material material) {
        for (TradeSpec trade : plugin.blueprint().trades()) {
            if (trade.result().getType() == material) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theTradesAreRealVanillaTrades() {
        TradeSpec first = plugin.blueprint().trades().get(0);
        assertFalse(first.cost().isEmpty(), "a trade costs something");
        assertEquals(first.result().getType(), first.toRecipe().getResult().getType(),
                "the recipe hands over what the trade promised");
        assertFalse(first.toRecipe().getIngredients().isEmpty(),
                "and asks for the ore in the cost slot");
    }

    // ------------------------------------------------------------------
    // Instances
    // ------------------------------------------------------------------

    @Test
    void everyPlayerGetsTheirOwnRoom() {
        PlayerMock first = started();
        PlayerMock second = started();

        Instance one = plugin.instances().of(first);
        Instance two = plugin.instances().of(second);

        assertNotNull(one, "the first player got an arena");
        assertNotNull(two, "and so did the second");
        assertNotEquals(one.index(), two.index(), "not the same one");
        assertEquals(512, Math.abs(one.origin().getBlockX() - two.origin().getBlockX()),
                "far enough apart that neither can see the other");
    }

    @Test
    void theRoomIsBuiltAndTheyLandInIt() {
        PlayerMock player = started();
        Instance instance = plugin.instances().of(player);

        assertEquals(ARENA_WORLD, player.getWorld().getName(), "they were teleported in");
        assertEquals(Material.STONE_BRICKS, block(instance, 0, 0, 0).getType(),
                "there is a floor under them");
        MineSpec wood = plugin.blueprint().mine("wood");
        assertNotNull(wood, "the wood mine is configured");
        assertEquals(Material.OAK_LOG,
                block(instance, wood.min().x(), wood.min().y(), wood.min().z()).getType(),
                "and the wood mine is full");
    }

    @Test
    void theMineFillsItselfBackUp() {
        // The one lesson the arena teaches without words: the ore comes back.
        PlayerMock player = started();
        Instance instance = plugin.instances().of(player);
        MineSpec wood = plugin.blueprint().mine("wood");

        Block dug = block(instance, wood.min().x(), wood.min().y(), wood.min().z());
        dug.setType(Material.AIR);
        for (int taken = 0; taken < wood.minedBeforeRefill(); taken++) {
            instance.noteMined(wood);
        }

        assertEquals(Material.OAK_LOG, dug.getType(), "the mine refilled itself");
    }

    // ------------------------------------------------------------------
    // What you may and may not do in there
    // ------------------------------------------------------------------

    @Test
    void theMineCanBeBrokenAndCounts() {
        PlayerMock player = started();
        Instance instance = plugin.instances().of(player);
        MineSpec wood = plugin.blueprint().mine("wood");
        Block log = block(instance, wood.min().x(), wood.min().y(), wood.min().z());

        BlockBreakEvent event = new BlockBreakEvent(log, player);
        Bukkit.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled(), "the mine is what they're here to break");
        assertEquals(1, plugin.store().get(player.getUniqueId()).count("chop-wood"),
                "and it counts toward the step");
    }

    @Test
    void theFloorCannotBeBroken() {
        // A new player who digs through the floor and falls out of the world
        // has learned only that this server is broken.
        PlayerMock player = started();
        Instance instance = plugin.instances().of(player);

        BlockBreakEvent event = new BlockBreakEvent(block(instance, 0, 0, 0), player);
        Bukkit.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "the floor stays where it is");
    }

    @Test
    void nothingCanBeBuiltInThere() {
        PlayerMock player = started();
        Instance instance = plugin.instances().of(player);
        Block target = block(instance, 2, 1, 2);

        BlockPlaceEvent event = new BlockPlaceEvent(target, target.getState(),
                block(instance, 2, 0, 2), new ItemStack(Material.COBBLESTONE), player, true,
                org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "the arena is not a building site");
    }

    // ------------------------------------------------------------------
    // Coming and going
    // ------------------------------------------------------------------

    @Test
    void walkingOutHandsTheRoomBack() {
        // Otherwise a 32-instance server runs out of rooms at eleven players.
        PlayerMock player = started();
        World home = Bukkit.getWorlds().get(0);

        player.teleport(home.getSpawnLocation());
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, arena));

        assertNull(plugin.instances().of(player), "the arena went back in the pool");
        assertTrue(plugin.service().isActive(plugin.store().get(player.getUniqueId())),
                "but their progress is still theirs");
    }

    @Test
    void comingBackGivesThemAnArenaAgain() {
        PlayerMock player = started();
        plugin.service().leftArena(player);

        assertTrue(plugin.service().enter(player), "/tutorial puts them back in");
        assertNotNull(plugin.instances().of(player));
        assertEquals(ARENA_WORLD, player.getWorld().getName());
    }

    @Test
    void finishingSendsThemHomeWithWhatTheyMade() {
        PlayerMock player = started();
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 5));

        for (TutorialStep step : plugin.tutorial().steps()) {
            plugin.service().completeStep(player, step, true);
        }
        server.getScheduler().performTicks(90);

        assertTrue(plugin.store().get(player.getUniqueId()).finished(), "it's done");
        assertNotEquals(ARENA_WORLD, player.getWorld().getName(), "and they're back outside");
        assertNull(plugin.instances().of(player), "their arena was handed back");
        assertTrue(player.getInventory().contains(Material.OAK_LOG),
                "what they gathered came with them");
    }

    @Test
    void stoppingHalfwayAlsoSendsThemHome() {
        PlayerMock player = started();

        plugin.service().stop(player);

        assertNotEquals(ARENA_WORLD, player.getWorld().getName(),
                "nobody is left standing in a tutorial they turned off");
        assertNull(plugin.instances().of(player));
    }

    @Test
    void aBusyServerSaysSoInsteadOfLosingProgress() {
        plugin.getConfig().set("arena.max-instances", 1);
        plugin.blueprint().load();
        started();

        PlayerMock second = server.addPlayer();
        plugin.service().start(second, true);

        assertFalse(plugin.store().get(second.getUniqueId()).started(),
                "a player who couldn't get a room isn't marked as having started");
        assertNull(plugin.instances().of(second));
    }
}
