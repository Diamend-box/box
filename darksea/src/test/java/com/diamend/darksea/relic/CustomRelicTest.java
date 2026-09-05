package com.diamend.darksea.relic;

import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Custom relics: the file they live in, the registry they join, and the ids
 * that keep them from colliding with the shipped six.
 */
class CustomRelicTest {

    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();
    private Logger log;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        warnings.clear();
        log = Logger.getLogger("CustomRelicTest-" + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @AfterEach
    void tearDown() {
        // The registry is static, so a relic left behind here would show up as
        // an extra relic in somebody else's test.
        Relic.setCustom(List.of());
        MockBukkit.unmock();
    }

    private File file() {
        return dir.resolve("relics-custom.yml").toFile();
    }

    private Relic sample() {
        return Relic.custom("bloodstone", 4, 175, Relic.Boost.ARMOR, Material.NETHERITE_SCRAP,
                "<red>Bloodstone</red>",
                List.of("<gray>Warm to the touch.</gray>", "<gray>It should not be.</gray>"),
                "Ironblood — +3 armor", null, 0);
    }

    @Test
    void aRelicSurvivesTheRoundTripThroughItsFile() {
        Relic made = sample();
        CustomRelicConfig.save(List.of(made), file(), log);
        List<Relic> back = CustomRelicConfig.load(file(), log);

        assertEquals(1, back.size(), "one relic in, one relic out");
        Relic read = back.get(0);
        assertEquals("bloodstone", read.id());
        assertEquals(4, read.tier());
        assertEquals(175, read.reviveCost());
        assertEquals(Relic.Boost.ARMOR, read.boost());
        assertEquals(Material.NETHERITE_SCRAP, read.material());
        assertEquals("<red>Bloodstone</red>", read.displayName());
        assertEquals(made.lore(), read.lore());
        assertEquals("Ironblood — +3 armor", read.boostLine());
        assertTrue(read.custom(), "a relic read out of the file is a custom relic");
        assertTrue(warnings.isEmpty(), "a clean file warned: " + warnings);
    }

    @Test
    void aMissingFileIsAnEmptyListRatherThanAFailure() {
        assertEquals(List.of(), CustomRelicConfig.load(file(), log));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aCustomRelicBehavesLikeAShippedOne() {
        Relic.setCustom(List.of(sample()));

        Relic found = Relic.byId("bloodstone");
        assertNotNull(found, "the registry should hand back what was installed");
        assertTrue(DarkSeaItems.allIds().contains("bloodstone"),
                "a custom relic should be giveable through /ds give item");

        ItemStack item = DarkSeaItems.create("bloodstone", 1);
        assertNotNull(item, "the item registry should build a custom relic");
        assertEquals(found, Relic.of(item), "a custom relic should keep its identity");
        assertFalse(Relic.isAwake(item), "a custom relic should drop dormant");
        found.wake(item);
        assertTrue(Relic.isAwake(item), "a custom relic should be wakeable");
        assertEquals(found, Relic.of(item), "waking should not change what it is");
    }

    @Test
    void anIdAlreadySpokenForIsRefused() {
        // Three ways to collide: a shipped relic, a registry item, and each other.
        Relic shipped = Relic.custom("relic_trade_coin", 1, 10, Relic.Boost.SPEED,
                Material.STICK, "<white>Impostor</white>", List.of(), "nothing", null, 0);
        Relic item = Relic.custom(DarkSeaItems.CHRONON, 1, 10, Relic.Boost.SPEED,
                Material.STICK, "<white>Impostor</white>", List.of(), "nothing", null, 0);
        Relic first = Relic.custom("twin", 1, 10, Relic.Boost.SPEED,
                Material.STICK, "<white>First</white>", List.of(), "nothing", null, 0);
        Relic second = Relic.custom("twin", 5, 999, Relic.Boost.ARMOR,
                Material.STONE, "<white>Second</white>", List.of(), "nothing", null, 0);

        Relic.setCustom(List.of(shipped, item, first, second));

        assertEquals(List.of("twin"), Relic.customs().stream().map(Relic::id).toList(),
                "only the one free id should survive");
        assertEquals("<white>First</white>", Relic.byId("twin").displayName(),
                "the first claim on an id should win");
        assertEquals(Relic.TRADE_COIN, Relic.byId("relic_trade_coin"),
                "a shipped relic must not be replaceable");
        assertFalse(Relic.isIdFree("twin"));
        assertTrue(Relic.isIdFree("something_nobody_has"));
    }

    @Test
    void aRelicRemovedFromTheListLeavesTheRegistry() {
        Relic.setCustom(List.of(sample()));
        assertNotNull(Relic.byId("bloodstone"));

        Relic.setCustom(List.of());
        assertNull(Relic.byId("bloodstone"), "deleting one in the editor should mean something");
        assertEquals(Relic.builtIns().size(), Relic.values().length,
                "the shipped six should be all that is left");
    }

    @Test
    void anIdIsSquaredOffToWhatAConfigKeyCanHold() {
        assertEquals("blood_stone", Relic.sanitizeId("Blood Stone"));
        assertEquals("blood_stone", Relic.sanitizeId("blood-stone"));
        assertEquals("bl00d", Relic.sanitizeId("bl00d!!!"));
        assertEquals("relic", Relic.sanitizeId("***"), "an id that vanishes needs a placeholder");
        assertEquals("relic", Relic.sanitizeId(null));
    }

    @Test
    void aBadFieldCostsThatFieldAndNotTheRelic() throws Exception {
        Files.writeString(file().toPath(), """
                relics:
                  halfwrong:
                    material: NOT_A_REAL_BLOCK
                    name: "<white>Half Wrong</white>"
                    tier: 2
                    revive-cost: 60
                    boost: TELEPORTATION
                    boost-line: "Something"
                """);
        List<Relic> back = CustomRelicConfig.load(file(), log);

        assertEquals(1, back.size(), "a relic with two bad fields should still load");
        Relic read = back.get(0);
        assertEquals("halfwrong", read.id());
        assertEquals(2, read.tier(), "the good fields should survive the bad ones");
        assertEquals(60, read.reviveCost());
        assertNotNull(read.material(), "a bad material should fall back, not be null");
        assertNotNull(read.boost(), "a bad boost should fall back, not be null");
        assertEquals(2, warnings.size(), "both bad fields should be reported: " + warnings);
    }

    @Test
    void numbersFromChatAreClampedRatherThanTrusted() {
        Relic silly = Relic.custom("silly", 99, -500, null, null, "  ", null, null, null, 12);
        assertTrue(silly.tier() >= 1 && silly.tier() <= 5, "tier should be clamped: " + silly.tier());
        assertEquals(0, silly.reviveCost(), "a negative wake cost should floor at free");
        assertNotNull(silly.boost());
        assertNotNull(silly.material());
        assertFalse(silly.displayName().isBlank(), "a blank name should get a placeholder");
        assertTrue(silly.effectAmplifier() <= 4, "an amplifier should be clamped");
    }
}
