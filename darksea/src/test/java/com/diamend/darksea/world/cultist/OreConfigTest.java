package com.diamend.darksea.world.cultist;

import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.npc.NpcType;
import com.diamend.darksea.npc.ShopConfig;
import com.diamend.darksea.npc.ShopOffer;
import com.diamend.darksea.npc.ShopStock;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped ores.yml, and the economy rule it exists to keep: cave ore is
 * upgrade material and nothing else.
 */
class OreConfigTest {

    private final List<String> warnings = new ArrayList<>();
    private Logger log;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        log = Logger.getLogger("OreConfigTest-" + System.nanoTime());
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
        MockBukkit.unmock();
    }

    private OreTables loadShipped() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ores.yml")) {
            assertNotNull(in, "default ores.yml missing from resources");
            return OreConfig.load(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)), log);
        }
    }

    private ShopStock loadShops() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/shops.yml")) {
            assertNotNull(in, "default shops.yml missing from resources");
            return ShopConfig.load(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)), log);
        }
    }

    @Test
    void shippedFileParsesWithoutASingleSkippedVein() throws Exception {
        OreTables tables = loadShipped();
        assertTrue(warnings.isEmpty(), "ores.yml produced warnings: " + warnings);
        assertEquals(3, tables.types().size());
        assertTrue(tables.totalVeins() > 0);
        assertTrue(tables.minSpacing() > 0);
    }

    /**
     * Few and <em>very</em> large is the design. The floor is 50 rather than the
     * 15 it started at: a vein has to be minutes of work for the "you are
     * stationary and everybody knows where you are" tension to exist at all, and
     * a small vein would quietly undo that without failing anything else.
     */
    @Test
    void theShippedVeinsAreFewAndLarge() throws Exception {
        OreTables tables = loadShipped();
        assertTrue(tables.totalVeins() <= 30,
                "that is a lot of veins for 'few': " + tables.totalVeins());
        for (OreType type : tables.types()) {
            assertTrue(type.minSize() >= 50,
                    type.id() + " is too small to be worth defending: " + type.minSize());
            assertTrue(type.cooldownMinutes() >= 10,
                    type.id() + " regrows too fast to be scarce");
        }
    }

    /**
     * The whole point of the caves is that you can keep working them. If the
     * time to clear every vein of a type ever exceeds its regrow cooldown by
     * much, that type has a dead window where a solo player has nothing to mine
     * — which is exactly the problem first-touch timing and big veins were
     * introduced to fix.
     */
    @Test
    void everyCrystalComesBackBeforeASoloSweepCouldRunOut() throws Exception {
        OreTables tables = loadShipped();
        for (OreType type : tables.types()) {
            double blocks = (double) type.veinCount() * (type.minSize() + type.maxSize()) / 2.0;
            double sweepMinutes = blocks * type.mineSeconds() / 60.0;
            double gap = type.cooldownMinutes() - sweepMinutes;
            assertTrue(gap <= 12.0, type.id() + " leaves a " + Math.round(gap)
                    + "-minute drought after a full sweep — raise count/size or cut regrow-minutes");
        }
    }

    @Test
    void everyVeinBlockAndDropActuallyExists() throws Exception {
        for (OreType type : loadShipped().types()) {
            assertNotNull(Material.matchMaterial(type.coreBlock()),
                    type.id() + ": unknown block " + type.coreBlock());
            assertNotNull(Material.matchMaterial(type.matrixBlock()),
                    type.id() + ": unknown matrix " + type.matrixBlock());
            if (type.hasShell()) {
                assertNotNull(Material.matchMaterial(type.shellBlock()),
                        type.id() + ": unknown shell " + type.shellBlock());
                assertNotNull(Material.matchMaterial(type.budBlock()),
                        type.id() + ": unknown bud " + type.budBlock());
            }
            assertNotNull(DarkSeaItems.create(type.dropId(), 1),
                    type.id() + ": unknown drop " + type.dropId());
        }
    }

    /**
     * The reference gear has to resolve to a real pickaxe, or every
     * {@code mine-seconds} in the file silently means something else.
     */
    @Test
    void theReferenceGearIsARealPickaxe() throws Exception {
        OreTables tables = loadShipped();
        assertTrue(warnings.isEmpty(), "ores.yml produced warnings: " + warnings);
        assertNotNull(MiningSpeed.tierOf(tables.referenceTool()),
                "reference-tool is not a pickaxe: " + tables.referenceTool());
        // A reference pick must land on exactly the configured time, or the
        // numbers in the file are not the numbers players experience.
        double refSpeed = tables.referenceSpeed();
        for (OreType type : tables.types()) {
            assertEquals(type.mineSeconds(),
                    MiningSpeed.seconds(type.mineSeconds(), refSpeed, refSpeed,
                            tables.floorSeconds()),
                    1e-9, type.id() + " does not take mine-seconds at reference gear");
        }
    }

    /**
     * The rule the whole ore economy rests on. If an ore ever becomes
     * purchasable or sellable, Chronons stop being a Dark-Sea-only currency
     * and a safe mining dimension starts competing with sailing for player
     * time — which is the outcome node-based supply was chosen to avoid.
     */
    @Test
    void noOreCanBeBoughtOrSoldAtAnyShop() throws Exception {
        List<String> oreIds = loadShipped().dropIds();
        assertFalse(oreIds.isEmpty());
        ShopStock shops = loadShops();
        for (NpcType npc : NpcType.values()) {
            for (long cycle = 0L; cycle < 25L; cycle++) {
                for (ShopOffer offer : shops.offers(npc, cycle)) {
                    assertFalse(oreIds.contains(offer.itemId()),
                            npc + " trades in " + offer.itemId()
                                    + " — cave ore must never reach a shop board");
                }
            }
        }
    }

    /** Malformed veins cost you the vein, not the caves. */
    @Test
    void badVeinsAreSkippedAndNamed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("veins.good.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.good.drop", "emberglass");
        yaml.set("veins.good.count", 4);
        yaml.set("veins.nodrop.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.nodrop.count", 4);
        yaml.set("veins.nocount.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.nocount.drop", "voidbloom");
        yaml.set("veins.nocount.count", 0);
        yaml.set("veins.badsize.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.badsize.drop", "voidbloom");
        yaml.set("veins.badsize.count", 2);
        yaml.set("veins.badsize.size.min", 30);
        yaml.set("veins.badsize.size.max", 10);
        yaml.set("veins.badspeed.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.badspeed.drop", "godspore");
        yaml.set("veins.badspeed.count", 2);
        yaml.set("veins.badspeed.mine-seconds", 0);

        OreTables tables = OreConfig.load(yaml, log);
        assertEquals(1, tables.types().size(), "only the good vein should survive");
        assertEquals(4, warnings.size(), "unexpected complaints: " + warnings);
    }

    /** A reference tool that isn't a pickaxe is complained about, not obeyed. */
    @Test
    void aNonsenseReferenceToolFallsBackRatherThanBreakingTheCurve() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("reference-tool", "NETHERITE_SHOVEL");
        yaml.set("veins.good.block", "OCHRE_FROGLIGHT");
        yaml.set("veins.good.drop", "emberglass");
        yaml.set("veins.good.count", 4);

        OreTables tables = OreConfig.load(yaml, log);
        assertEquals("NETHERITE_PICKAXE", tables.referenceTool());
        assertTrue(tables.referenceSpeed() > 0);
        assertFalse(warnings.isEmpty(), "a bad reference-tool should be named in the log");
    }

    @Test
    void anEmptyFileDegradesToNoVeinsRatherThanBlowingUp() {
        OreTables tables = OreConfig.load(new YamlConfiguration(), log);
        assertEquals(0, tables.totalVeins());
        assertFalse(warnings.isEmpty(), "an empty ores.yml should be complained about");
    }
}
