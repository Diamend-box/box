package com.diamend.robobear;

import com.diamend.robobear.challenge.Objective;
import com.diamend.robobear.challenge.ObjectiveGenerator;
import com.diamend.robobear.challenge.ObjectiveType;
import com.diamend.robobear.mine.MineRegion;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest editor, and the bug it was built around.
 *
 * <p>A live server was offered "break 30 gold ore in the quartz mine". The
 * generator picked a mine and a material out of two unrelated hats, so nothing
 * ever checked the one contained the other — and an objective you cannot
 * complete doesn't just waste a round, it ends the run.
 */
class QuestEditorTest {

    private ServerMock server;
    private RoboBearPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(RoboBearPlugin.class);
        server.addSimpleWorld("mines");
        plugin.getConfig().set("run.entry-item.item", "");
        givenTwoMines();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // The bug
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a material objective only ever names something that mine contains")
    void materialObjectivesStayInsideTheirMine() {
        plugin.mines().materials().set("quartz", List.of(Material.NETHER_QUARTZ_ORE));
        plugin.mines().materials().set("gold", List.of(Material.GOLD_ORE));

        ObjectiveGenerator generator = new ObjectiveGenerator(plugin);
        int seen = 0;
        for (int attempt = 0; attempt < 400; attempt++) {
            for (Objective objective : generator.offer(1 + (attempt % 9))) {
                if (objective.type() != ObjectiveType.MINE_MATERIAL) {
                    continue;
                }
                seen++;
                assertTrue(plugin.mines().materialsFor(objective.mineId()).contains(objective.material()),
                        "asked for " + objective.material() + " in the " + objective.mineId()
                                + " mine, which doesn't have any");
            }
        }
        assertTrue(seen > 0, "the test proves nothing if no material objective was rolled");
    }

    @Test
    @DisplayName("a mine that isn't in the pool is never the target of one")
    void minesOutOfThePoolAreNeverChosen() {
        plugin.mines().toggles().setEnabled("quartz", false);

        List<MineRegion> usable = plugin.mines().minesWithMaterials();
        assertEquals(1, usable.size(), "a switched-off mine can't be sent to");
        assertEquals("gold", usable.get(0).id());

        ObjectiveGenerator generator = new ObjectiveGenerator(plugin);
        for (int attempt = 0; attempt < 200; attempt++) {
            for (Objective objective : generator.offer(4)) {
                if (objective.mineId() != null) {
                    assertEquals("gold", objective.mineId(),
                            "a rank-gated mine nobody can enter must never be the target");
                }
            }
        }
    }

    @Test
    @DisplayName("an empty config material list still switches the type off entirely")
    void emptyMaterialListDisablesTheType() {
        plugin.getConfig().set("objectives.mine-material.materials", List.of());

        assertTrue(plugin.mines().automaticMaterials("gold").isEmpty());
        assertTrue(plugin.mines().minesWithMaterials().isEmpty());
        assertFalse(new ObjectiveGenerator(plugin).allowedTypes()
                        .contains(ObjectiveType.MINE_MATERIAL),
                "the documented off switch has to keep working");
    }

    // ------------------------------------------------------------------
    // Hand-set material lists
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a hand-set list wins over the config list")
    void overrideBeatsTheConfigList() {
        plugin.getConfig().set("objectives.mine-material.materials", List.of("GOLD_ORE"));
        plugin.mines().materials().set("quartz", List.of(Material.NETHER_QUARTZ_ORE));

        assertEquals(List.of(Material.NETHER_QUARTZ_ORE), plugin.mines().materialsFor("quartz"));
        assertEquals(List.of(Material.GOLD_ORE), plugin.mines().materialsFor("gold"),
                "correcting one mine must not touch the others");
    }

    @Test
    @DisplayName("clearing a hand-set list goes back to automatic")
    void clearingAnOverrideRestoresAutomatic() {
        plugin.mines().materials().set("quartz", List.of(Material.NETHER_QUARTZ_ORE));
        assertTrue(plugin.mines().materials().isOverridden("quartz"));

        plugin.mines().materials().set("quartz", List.of());

        assertFalse(plugin.mines().materials().isOverridden("quartz"));
        assertEquals(plugin.mines().automaticMaterials("quartz"),
                plugin.mines().materialsFor("quartz"));
    }

    @Test
    @DisplayName("things that can't be mined are refused, not stored")
    void nonBlocksAreRefused() {
        plugin.mines().materials().set("quartz", List.of(Material.DIAMOND_SWORD, Material.AIR));

        assertFalse(plugin.mines().materials().isOverridden("quartz"),
                "a sword is not something a mine can be asked for");
    }

    @Test
    @DisplayName("hand-set lists survive a reload, case-insensitively")
    void overridesPersist() {
        plugin.mines().materials().set("QUARTZ", List.of(Material.NETHER_QUARTZ_ORE));

        plugin.mines().materials().load();

        assertEquals(List.of(Material.NETHER_QUARTZ_ORE), plugin.mines().materialsFor("quartz"));
    }

    // ------------------------------------------------------------------
    // Which job types are offered
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every type is offered until someone says otherwise")
    void everyTypeIsOnByDefault() {
        for (ObjectiveType type : ObjectiveType.values()) {
            assertTrue(plugin.service().objectives().isEnabled(type),
                    type + " should be on out of the box");
        }
    }

    @Test
    @DisplayName("switching a type off stops it being rolled")
    void disabledTypesAreNeverRolled() {
        plugin.service().objectives().setEnabled(ObjectiveType.MINE_BLOCKS, false);
        plugin.service().objectives().setEnabled(ObjectiveType.KILL_MOBS, false);

        assertEquals(List.of(ObjectiveType.MINE_MATERIAL),
                new ObjectiveGenerator(plugin).allowedTypes());

        ObjectiveGenerator generator = new ObjectiveGenerator(plugin);
        for (int attempt = 0; attempt < 100; attempt++) {
            for (Objective objective : generator.offer(2)) {
                assertEquals(ObjectiveType.MINE_MATERIAL, objective.type());
            }
        }
    }

    @Test
    @DisplayName("config.yml is the master switch and the GUI can't override it")
    void configBeatsTheGui() {
        plugin.getConfig().set("objectives.kill-mobs.enabled", false);

        assertFalse(plugin.service().objectives().isEnabled(ObjectiveType.KILL_MOBS));
        assertFalse(plugin.service().objectives().setEnabled(ObjectiveType.KILL_MOBS, true),
                "the screen has to be able to say why it refused");
        assertFalse(plugin.service().objectives().isEnabled(ObjectiveType.KILL_MOBS));
    }

    @Test
    @DisplayName("the choices survive a reload")
    void typeTogglesPersist() {
        plugin.service().objectives().setEnabled(ObjectiveType.KILL_MOBS, false);

        plugin.service().objectives().load();

        assertFalse(plugin.service().objectives().isEnabled(ObjectiveType.KILL_MOBS));
        assertTrue(plugin.service().objectives().isEnabled(ObjectiveType.MINE_BLOCKS));
    }

    @Test
    @DisplayName("a run can't be entered when nothing at all can be offered")
    void nothingToOfferMeansNoRun() {
        for (ObjectiveType type : ObjectiveType.values()) {
            plugin.service().objectives().setEnabled(type, false);
        }
        PlayerMock player = server.addPlayer();

        assertFalse(plugin.service().start(player),
                "taking entry for a run with no possible job is the worst outcome here");
        assertFalse(plugin.service().isRunning(player));
    }

    private void givenTwoMines() {
        plugin.mines().manualProvider().put(
                MineRegion.between("quartz", "Quartz", "mines", 0, 0, 0, 15, 15, 15));
        plugin.mines().manualProvider().put(
                MineRegion.between("gold", "Gold", "mines", 100, 0, 0, 115, 15, 15));
        plugin.mines().refresh();
    }
}
