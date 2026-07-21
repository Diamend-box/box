package com.diamend.darksea.config;

import com.diamend.darksea.zone.Zone;
import com.diamend.darksea.zone.ZoneManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped default config.yml must parse into a fully usable settings snapshot. */
class ConfigParsingTest {

    private static final Logger LOG = Logger.getLogger("ConfigParsingTest");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private DarkSeaSettings loadDefault() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "default config.yml missing from resources");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return DarkSeaSettings.load(yaml, LOG);
        }
    }

    @Test
    void defaultConfigParsesCompletely() throws Exception {
        DarkSeaSettings settings = loadDefault();

        assertEquals("dark_sea", settings.worldName());
        assertEquals(62, settings.seaLevel());
        assertEquals(0, settings.centerX());
        assertEquals(0, settings.centerZ());

        assertEquals(5, settings.zones().size());
        ZoneManager zones = new ZoneManager(settings.zones());
        assertEquals(4, zones.maxTier());
        assertEquals("safe", zones.zoneAt(0).id());
        assertEquals("zone4", zones.zoneAt(6000.0 * 6000.0).id());

        // Every danger ring carries at least one resolvable potion effect.
        for (Zone zone : settings.zones()) {
            if (zone.requiredTier() > 0) {
                assertFalse(zone.effects().isEmpty(), zone.id() + " has no effects");
            }
        }

        assertEquals(4, settings.armor().tiers().size());
        assertTrue(settings.armor().unbreakable());
        assertEquals("CHAINMAIL", settings.armor().tiers().get(1).materialPrefix());
        assertEquals("NETHERITE", settings.armor().tiers().get(4).materialPrefix());

        assertEquals(4, settings.generation().islandsPerRing().size());
        assertEquals(6, settings.generation().islandsPerRing().get(1));
        assertEquals(Material.LODESTONE, settings.generation().chestMarker());
        assertEquals(Material.GOLD_BLOCK, settings.generation().mobMarker());
        assertTrue(settings.generation().outerRadius() > 5000);
        assertTrue(settings.generation().demoIslands());
        assertTrue(settings.generation().demoPaceTicks() >= 1);

        assertEquals(4, settings.boat().levels().size());
        assertEquals(0, settings.boat().levels().get(0).shield());
        assertEquals(1, settings.boat().levels().get(3).shield());
        assertTrue(settings.boat().levels().get(3).speed() > settings.boat().levels().get(1).speed());

        assertEquals(0.75, settings.naval().ram().defenderShare());
        assertTrue(settings.naval().ram().minClosingSpeed() > 0);
        assertTrue(settings.naval().hull().maxHp() > 0);
        assertTrue(settings.naval().hull().woundedSpeedFactor() < 1.0);
        assertTrue(settings.naval().surge().boostFactor() > 1.0);
        assertTrue(settings.naval().chainshotSpeedFactor()
                < settings.naval().hull().woundedSpeedFactor(),
                "chainshot must slow harder than an ordinary hull hit");
        assertTrue(settings.naval().harpoon().range() >= 4);
        assertTrue(settings.naval().hud().enabled(), "the shipped config turns the boat HUD on");
        assertTrue(settings.naval().hud().periodTicks() >= 2,
                "HUD repaint must never run every tick");

        assertFalse(settings.messages().isEmpty());
        assertTrue(settings.messages().containsKey("prefix"));
        assertTrue(settings.messages().containsKey("reset-full-warning"));
        assertTrue(settings.messages().containsKey("naval-hooked"));
        assertTrue(settings.messages().containsKey("naval-surge-cooldown"));
    }

    @Test
    void configWithoutZonesIsRejected() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertThrows(IllegalStateException.class, () -> DarkSeaSettings.load(yaml, LOG));
    }

    @Test
    void unknownEffectTypesAreSkippedNotFatal() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                zones:
                  - id: weird
                    max-radius: -1
                    required-tier: 1
                    effects:
                      - { type: not_an_effect, amplifier: 0 }
                      - { type: poison, amplifier: 1 }
                armor:
                  tiers:
                    1: { name: "Test", material: IRON }
                """);
        DarkSeaSettings settings = DarkSeaSettings.load(yaml, LOG);
        assertEquals(1, settings.zones().size());
        assertEquals(1, settings.zones().get(0).effects().size());
        assertEquals(1, settings.zones().get(0).effects().get(0).amplifier());
    }

    @Test
    void badArmorMaterialPrefixIsSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                zones:
                  - id: all
                    max-radius: -1
                    required-tier: 0
                    effects: []
                armor:
                  tiers:
                    1: { name: "Good", material: DIAMOND }
                    2: { name: "Bad", material: SPONGE }
                """);
        DarkSeaSettings settings = DarkSeaSettings.load(yaml, LOG);
        assertEquals(1, settings.armor().tiers().size());
        assertNotNull(settings.armor().tiers().get(1));
    }
}
