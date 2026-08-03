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
import java.util.List;
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
        assertEquals("dark_sea_caves", settings.cultist().worldName());
        assertTrue(settings.cultist().roofY() > settings.cultist().floorY() + 8,
                "the caves need headroom between floor and roof");
        assertEquals(256, settings.cultist().halfExtent(), "a 512x512 box");
        // The landfall stands well out from the calm center: reaching it needs
        // a real boat and real armor, which is the gate on the whole post-sea arc.
        assertTrue(Math.hypot(settings.landfallOffsetX(), settings.landfallOffsetZ()) > 1500,
                "the landfall is too close to home to be a journey");
        assertTrue(settings.cultist().chamberRadius() > 0,
                "the portal needs somewhere open to arrive");
        assertEquals(62, settings.seaLevel());
        assertEquals(0, settings.centerX());
        assertEquals(0, settings.centerZ());

        assertEquals(7, settings.zones().size());
        ZoneManager zones = new ZoneManager(settings.zones());
        assertEquals(6, zones.maxTier());
        assertEquals("safe", zones.zoneAt(0).id());
        assertEquals("zone4", zones.zoneAt(6000.0 * 6000.0).id());
        // The two outer rings are pushed far out: the Trench (tier 5) starts at
        // ~14,500 and the Rim (tier 6) at ~24,500 — roughly 15 and 25 minutes
        // out at top boat speed.
        assertEquals("zone4", zones.zoneAt(14000.0 * 14000.0).id());
        assertEquals("zone5", zones.zoneAt(15000.0 * 15000.0).id());
        assertEquals("zone5", zones.zoneAt(24000.0 * 24000.0).id());
        assertEquals("zone6", zones.zoneAt(25000.0 * 25000.0).id());
        // The outermost ring is the lethal rim, and only it bypasses protection.
        assertEquals("zone6", zones.zoneAt(50000.0 * 50000.0).id());
        assertTrue(zones.byTier(6).bypassProtection(), "the rim ignores armor and shield");
        assertFalse(zones.byTier(5).bypassProtection(), "tier 5 is still gear-reducible");
        // The Sunless Trench (tier 5) is a deadlier ring than the Reaches: it
        // adds perpetual darkness on top of a deeper wither, but still lets
        // armor blunt it — only the rim bypasses gear.
        assertTrue(zones.byTier(5).effects().stream()
                        .anyMatch(e -> e.type() == org.bukkit.potion.PotionEffectType.DARKNESS),
                "the Sunless Trench must carry darkness");

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

        assertEquals(5, settings.generation().islandsPerRing().size());
        assertEquals(6, settings.generation().islandsPerRing().get(1));
        // The Sunless Trench (tier 5) builds a sparse couple of islands, about
        // half of them Core nests and the rest rare castle/volcano outposts.
        assertEquals(2, settings.generation().islandsPerRing().get(5));
        assertEquals(Material.LODESTONE, settings.generation().chestMarker());
        assertEquals(Material.GOLD_BLOCK, settings.generation().mobMarker());
        assertTrue(settings.generation().outerRadius() > 5000);
        assertTrue(settings.generation().demoIslands());
        assertTrue(settings.generation().demoPaceTicks() >= 1);

        assertEquals(6, settings.boat().levels().size());
        assertEquals(0, settings.boat().levels().get(0).shield());
        assertEquals(1, settings.boat().levels().get(3).shield());
        assertEquals(2, settings.boat().levels().get(5).shield());
        assertTrue(settings.boat().levels().get(3).speed() > settings.boat().levels().get(1).speed());
        assertTrue(settings.boat().levels().get(5).speed() > settings.boat().levels().get(3).speed());
        // Every level from the Sloop up carries its own hull, a smooth ramp
        // (11/13/14/18/24) instead of a cliff at tier 3; only the Rowboat
        // leaves hp at 0 to fall back on the global default (10).
        assertEquals(0.0, settings.boat().levels().get(0).hp(), 1e-9);
        assertEquals(11.0, settings.boat().levels().get(1).hp(), 1e-9);
        assertEquals(13.0, settings.boat().levels().get(2).hp(), 1e-9);
        assertEquals(14.0, settings.boat().levels().get(3).hp(), 1e-9);
        assertEquals(18.0, settings.boat().levels().get(4).hp(), 1e-9);
        assertEquals(24.0, settings.boat().levels().get(5).hp(), 1e-9);
        assertTrue(settings.boat().levels().get(5).toughness()
                > settings.boat().levels().get(3).toughness(),
                "each apex tier out-tanks the one below it");
        // Custom stat-point economy.
        assertEquals(0.03, settings.boat().statPoints().speedPerPoint(), 1e-9);
        assertEquals(0.15, settings.boat().statPoints().toughnessPerPoint(), 1e-9);
        assertEquals(2.0, settings.boat().statPoints().hpPerPoint(), 1e-9);
        assertEquals(0.10, settings.boat().statPoints().ramPowerPerPoint(), 1e-9);
        assertTrue(settings.boat().statPoints().resetCostPerPoint() > 0,
                "a respec must cost something");

        assertEquals(0.75, settings.naval().ram().defenderShare());
        assertEquals(0.15, settings.naval().ram().powerPerLevel(), 1e-9,
                "the shipped ram gains 15% offensive power per boat level");
        assertTrue(settings.naval().ram().minClosingSpeed() > 0);
        assertTrue(settings.naval().hull().maxHp() > 0);
        assertTrue(settings.naval().hull().combatTagSeconds() >= 1,
                "a naval hit must lock out healing for a real window");
        assertTrue(settings.naval().hull().regenPerSecond() > 0
                        && settings.naval().hull().regenPerSecond() < settings.naval().hull().maxHp(),
                "regen is a gradual claw-back, not a same-second snap to full");
        assertTrue(settings.naval().hull().woundedSpeedFactor() < 1.0);
        assertEquals(0.05, settings.naval().hull().speedPenaltyPerHp(), 1e-9,
                "the shipped hull loses 5% top speed per missing HP");
        // The hullpiercer is the rare anti-tank arrow: big flat damage that
        // ignores most of the toughness bonus, so a fat curve barely helps.
        assertEquals(9.0, settings.naval().hullpiercerDamage(), 1e-9);
        assertEquals(0.25, settings.naval().hullpiercerToughnessFactor(), 1e-9,
                "only a quarter of the toughness bonus resists a hullpiercer");
        assertTrue(settings.naval().surge().boostFactor() > 1.0);
        assertTrue(settings.naval().chainshotSpeedFactor()
                < settings.naval().hull().woundedSpeedFactor(),
                "chainshot must slow harder than an ordinary hull hit");
        assertTrue(settings.naval().harpoon().range() >= 4);
        assertTrue(settings.naval().hud().enabled(), "the shipped config turns the boat HUD on");
        assertTrue(settings.naval().hud().periodTicks() >= 2,
                "HUD repaint must never run every tick");
        assertEquals(2.0, settings.naval().repair().costPerHp(),
                "the shipped dry-dock bills 2 Chronons per missing hull HP");

        // The Undrowned Heart's cooldown and revive health are configurable.
        assertEquals(150, settings.relics().undrownedCooldownSeconds());
        assertEquals(1.0, settings.relics().undrownedReviveHealth(), 1e-9);

        assertFalse(settings.messages().isEmpty());
        assertTrue(settings.messages().containsKey("prefix"));
        assertTrue(settings.messages().containsKey("reset-full-warning"));
        assertTrue(settings.messages().containsKey("naval-hooked"));
        assertTrue(settings.messages().containsKey("naval-surge-cooldown"));
        assertTrue(settings.messages().containsKey("boat-stowed"));
        assertTrue(settings.messages().containsKey("boat-repaired"));
        assertTrue(settings.messages().containsKey("boat-pickup-combat"));
        assertTrue(settings.messages().containsKey("boat-wreck-recovered"));
        assertTrue(settings.messages().containsKey("boat-wreck-repaired"));
        assertTrue(settings.messages().containsKey("boat-debug-damaged"));
        assertTrue(settings.messages().containsKey("boat-outfit-reset"));
        assertTrue(settings.messages().containsKey("undrowned-attuned"));
        assertTrue(settings.messages().containsKey("undrowned-saved"));
        assertTrue(settings.messages().containsKey("soulwake-heading"));
        assertTrue(settings.messages().containsKey("soulwake-empty"));
        assertTrue(settings.messages().containsKey("npc-created"));
        assertTrue(settings.messages().containsKey("npc-unknown-type"));
        assertTrue(settings.messages().containsKey("shop-lore-buy"));
        assertTrue(settings.messages().containsKey("shop-lore-sell"));
        assertTrue(settings.messages().containsKey("shop-bought"));
        assertTrue(settings.messages().containsKey("shop-sold"));
        assertTrue(settings.messages().containsKey("shop-wake-holding"));
        assertTrue(settings.messages().containsKey("shop-clue-bearing"));
        assertTrue(settings.messages().containsKey("vault-sealed"));
        assertTrue(settings.messages().containsKey("vault-cracked"));
        assertTrue(settings.messages().containsKey("caves-unbreakable"));
        assertTrue(settings.messages().containsKey("caves-no-building"));
        assertTrue(settings.messages().containsKey("portal-descended"));
        assertTrue(settings.messages().containsKey("portal-ascended"));
        assertTrue(settings.messages().containsKey("portal-closed"));
        // The loot editor is all lore lines: a missing key there is not a
        // silent fallback, it is a tile whose numbers cannot be read, which
        // makes the board useless for the one thing it exists to do.
        for (String key : List.of(
                "loot-editor-title-picker", "loot-editor-title-list",
                "loot-editor-table-counts", "loot-editor-table-rolls",
                "loot-editor-help-file", "loot-editor-help-pick",
                "loot-editor-line-weight", "loot-editor-line-amount",
                "loot-editor-line-shipped", "loot-editor-line-added",
                "loot-editor-help-add", "loot-editor-help-weight",
                "loot-editor-help-amount", "loot-editor-help-delete",
                "loot-editor-help-shipped", "loot-editor-added",
                "loot-editor-removed", "loot-editor-list-full",
                "loot-editor-cannot-encode",
                // The caves speak entirely through the action bar: without
                // these the extraction channel and the vein readout both run
                // silently, which is indistinguishable from not running.
                "caves-extracting", "caves-vein-sense", "caves-vein-sense-growing")) {
            assertTrue(settings.messages().containsKey(key), "config.yml is missing " + key);
        }
        // Every rung of the old man's ladder must have a line to speak. The
        // ladder's length is shops.yml's clue-costs list; ShopConfigTest pins
        // that to four, so four lines is what config.yml owes.
        for (int level = 1; level <= 4; level++) {
            assertTrue(settings.messages().containsKey("shop-clue-" + level),
                    "missing shop-clue-" + level);
        }
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
