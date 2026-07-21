package com.diamend.darksea.mob;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shipped mobs.yml and the mythicmobs-pack/ must stay in lockstep: every
 * Mythic name must have a pack definition, and every fallback must be a real,
 * living vanilla mob — a typo in either file would otherwise surface as
 * silently empty islands on the server.
 */
class MobConfigTest {

    private record ParsedEntry(int tier, String type, String fallback) {
    }

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private YamlConfiguration loadShippedMobsYml() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mobs.yml")) {
            assertNotNull(in, "default mobs.yml missing from resources");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return yaml;
        }
    }

    /** Parses entries the same way MobSpawner.reloadSets does. */
    private List<ParsedEntry> parseEntries(YamlConfiguration yaml) {
        ConfigurationSection tiers = yaml.getConfigurationSection("tiers");
        assertNotNull(tiers, "mobs.yml has no tiers section");
        List<ParsedEntry> entries = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            for (Map<?, ?> map : tiers.getMapList(key)) {
                Object type = map.get("type");
                Object fallback = map.get("fallback");
                assertNotNull(type, "entry without type in tier " + key);
                entries.add(new ParsedEntry(Integer.parseInt(key), String.valueOf(type),
                        fallback != null ? String.valueOf(fallback) : null));
            }
        }
        return entries;
    }

    private static void assertLivingVanillaMob(String name, String context) {
        EntityType type;
        try {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            fail(context + ": '" + name + "' is not a vanilla entity type");
            return;
        }
        Class<?> clazz = type.getEntityClass();
        assertTrue(clazz != null && LivingEntity.class.isAssignableFrom(clazz),
                context + ": '" + name + "' is not a living, spawnable mob");
    }

    @Test
    void shippedRosterCoversAllFourRings() throws Exception {
        YamlConfiguration yaml = loadShippedMobsYml();
        List<ParsedEntry> entries = parseEntries(yaml);

        for (int tier = 1; tier <= 4; tier++) {
            int finalTier = tier;
            long natives = entries.stream().filter(e -> e.tier() == finalTier).count();
            // Two commons per ring. The Mariphage Core is no longer a native
            // roaming pick — it is the nest island's resident boss — so the
            // Abyssal Reaches ships two natives like every other ring.
            assertEquals(2, natives,
                    "tier " + tier + " ships the wrong number of natives");
        }

        // The Core must NOT roam natively any more: it belongs to its nest.
        assertTrue(entries.stream().noneMatch(e -> "MariphageCore".equals(e.type())),
                "the Mariphage Core leaked back into the native roaming pool");

        double decay = yaml.getDouble("lower-tier-decay", -1);
        assertTrue(decay > 0 && decay <= 1,
                "lower-tier-decay should be in (0,1], was " + decay);
    }

    @Test
    void everyFallbackIsALivingVanillaMob() throws Exception {
        for (ParsedEntry entry : parseEntries(loadShippedMobsYml())) {
            assertNotNull(entry.fallback(), entry.type() + " ships without a fallback");
            assertLivingVanillaMob(entry.fallback(), "fallback of " + entry.type());
        }
    }

    @Test
    void everyMythicNameHasAPackDefinition() throws Exception {
        File packDir = new File("mythicmobs-pack/Mobs");
        assertTrue(packDir.isDirectory(),
                "mythicmobs-pack/Mobs not found — surefire should run from darksea/");

        Set<String> packMobs = new HashSet<>();
        File[] files = packDir.listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(files);
        assertFalse(files.length == 0, "pack has no mob files");
        for (File file : files) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);  // throws on malformed YAML — a broken pack fails CI
            for (String mob : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(mob);
                assertNotNull(section, file.getName() + ": '" + mob + "' is not a section");
                assertLivingVanillaMob(section.getString("Type", ""),
                        file.getName() + " → " + mob + " Type");
                String display = section.getString("Display", "");
                assertFalse(display.isBlank(), file.getName() + " → " + mob + " has no Display");
                packMobs.add(mob);
            }
        }

        for (ParsedEntry entry : parseEntries(loadShippedMobsYml())) {
            assertTrue(packMobs.contains(entry.type()),
                    "mobs.yml names '" + entry.type() + "' but the pack doesn't define it");
        }
    }
}
