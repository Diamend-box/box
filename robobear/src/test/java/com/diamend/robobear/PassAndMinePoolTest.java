package com.diamend.robobear;

import com.diamend.robobear.challenge.EntryPass;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry pass and the objective pool — the two things an admin controls that
 * players would otherwise control for them.
 */
class PassAndMinePoolTest {

    private ServerMock server;
    private RoboBearPlugin plugin;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RoboBearPlugin.class);
        world = server.addSimpleWorld("mines");

        plugin.getConfig().set("run.entry-item.item", "NAME_TAG");
        plugin.getConfig().set("run.entry-item.amount", 1);
        plugin.getConfig().set("run.entry-item.name", "&6Robo Pass");
        plugin.getConfig().set("run.entry-item.require-tag", true);
        plugin.getConfig().set("run.entry-item.consume", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // The pass
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an issued pass is accepted")
    void issuedPassIsAPass() {
        EntryPass pass = plugin.service().pass();
        ItemStack issued = pass.issue(1);

        assertNotNull(issued);
        assertEquals(Material.NAME_TAG, issued.getType());
        assertTrue(pass.isPass(issued), "RoboBear must accept what RoboBear issued");
        assertFalse(pass.looksForged(issued), "a real pass is not a forgery");
    }

    @Test
    @DisplayName("a renamed lookalike is refused — the anvil exploit")
    void renamedLookalikeIsNotAPass() {
        EntryPass pass = plugin.service().pass();

        assertFalse(pass.isPass(anvilCopy()),
                "matching on the name alone is what let any player mint passes");
        assertTrue(pass.looksForged(anvilCopy()),
                "it should still be recognised well enough to explain the refusal");
    }

    @Test
    @DisplayName("a run can't be bought with a forged pass, and it isn't eaten")
    void forgedPassBuysNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(anvilCopy());

        assertFalse(plugin.service().start(player), "a renamed name tag must not buy a run");
        assertFalse(plugin.service().isRunning(player));
        assertTrue(player.getInventory().contains(Material.NAME_TAG),
                "a refused start must not consume what it refused");
    }

    @Test
    @DisplayName("an issued pass buys a run and is consumed once")
    void issuedPassBuysARun() {
        PlayerMock player = server.addPlayer();
        plugin.service().pass().give(player, 2);

        assertTrue(plugin.service().start(player));
        assertTrue(plugin.service().isRunning(player));

        int left = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.NAME_TAG) {
                left += item.getAmount();
            }
        }
        assertEquals(1, left, "exactly one entry's worth should have been taken");
    }

    @Test
    @DisplayName("with require-tag off, the old name matching still works")
    void nameMatchingRemainsAvailable() {
        plugin.getConfig().set("run.entry-item.require-tag", false);
        EntryPass pass = plugin.service().pass();

        assertTrue(pass.isPass(anvilCopy()),
                "servers upgrading with passes already handed out need this escape hatch");
        assertFalse(pass.looksForged(anvilCopy()),
                "nothing is forged when names are what count");
    }

    /** A name tag renamed in an anvil: right material, right name, never issued. */
    private ItemStack anvilCopy() {
        ItemStack fake = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = fake.getItemMeta();
        assertNotNull(meta);
        meta.displayName(Text.item("&6Robo Pass"));
        fake.setItemMeta(meta);
        return fake;
    }

    // ------------------------------------------------------------------
    // The objective pool
    // ------------------------------------------------------------------

    @Test
    @DisplayName("switching a mine off takes it out of the objective pool")
    void disabledMinesLeaveThePool() {
        givenTwoMines();
        assertEquals(2, plugin.mines().enabledSize());

        plugin.mines().toggles().setEnabled("QUARRY", false);

        assertEquals(1, plugin.mines().enabledSize(), "ids toggle case-insensitively");
        assertEquals(2, plugin.mines().size(), "the mine still exists, it just isn't offered");
        assertTrue(plugin.mines().enabled().stream().noneMatch(m -> m.id().equals("quarry")));
        assertTrue(plugin.mines().enabled().stream().anyMatch(m -> m.id().equals("deepslate")));
    }

    @Test
    @DisplayName("a mine that is switched off is still recognised underfoot")
    void disabledMinesStillMatchBlocks() {
        givenTwoMines();
        plugin.mines().toggles().setEnabled("quarry", false);

        MineRegion found = plugin.mines().mineAt(world.getBlockAt(5, 5, 5));

        assertNotNull(found, "a run already under way in it must not break");
        assertEquals("quarry", found.id());
    }

    @Test
    @DisplayName("disable-all then pick a few back is the 70-mine workflow")
    void bulkTogglesWork() {
        givenTwoMines();

        plugin.mines().toggles().disableAll(plugin.mines().all());
        assertEquals(0, plugin.mines().enabledSize());

        plugin.mines().toggles().setEnabled("deepslate", true);
        assertEquals(1, plugin.mines().enabledSize());

        plugin.mines().toggles().enableAll();
        assertEquals(2, plugin.mines().enabledSize());
    }

    @Test
    @DisplayName("the toggles survive a reload")
    void togglesPersist() {
        givenTwoMines();
        plugin.mines().toggles().setEnabled("quarry", false);

        plugin.mines().toggles().load();

        assertFalse(plugin.mines().toggles().isEnabled("quarry"));
        assertTrue(plugin.mines().toggles().isEnabled("deepslate"));
    }

    @Test
    @DisplayName("a run can't start when every mine is off and mobs are disabled")
    void everythingOffMeansNothingToDo() {
        givenTwoMines();
        plugin.getConfig().set("objectives.kill-mobs.enabled", false);
        plugin.getConfig().set("run.entry-item.item", "");
        plugin.mines().toggles().disableAll(plugin.mines().all());

        PlayerMock player = server.addPlayer();

        assertFalse(plugin.service().start(player),
                "mines that exist but are all switched off is the same as having none");
    }

    private void givenTwoMines() {
        plugin.mines().manualProvider().put(
                MineRegion.between("quarry", "Quarry", "mines", 0, 0, 0, 15, 15, 15));
        plugin.mines().manualProvider().put(
                MineRegion.between("deepslate", "Deepslate", "mines", 100, 0, 0, 115, 15, 15));
        plugin.mines().refresh();
    }
}
