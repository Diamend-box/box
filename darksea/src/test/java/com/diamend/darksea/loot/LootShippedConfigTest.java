package com.diamend.darksea.loot;

import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.config.DarkSeaSettings.ArmorStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SHIPPED loot.yml must always parse cleanly: a typo'd material or a
 * malformed relic entry would otherwise surface as silently thinner chests
 * on the server. Also locks the Loot 2.0 shape — every ring has tables,
 * every ring carries at least one named relic, and boat tokens for every
 * level exist somewhere in the progression.
 */
class LootShippedConfigTest {

    private static final Logger LOG = Logger.getLogger("LootShippedConfigTest");

    private Map<Integer, LootTable> tables;
    private int declaredEntries;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        String yaml;
        try (InputStream in = getClass().getResourceAsStream("/loot.yml")) {
            assertNotNull(in, "loot.yml missing from resources");
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        declaredEntries = 0;
        for (String key : config.getConfigurationSection("tiers").getKeys(false)) {
            declaredEntries += config.getConfigurationSection("tiers." + key).getMapList("entries").size();
        }
        tables = LootConfig.load(config, LOG);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyRingHasATableAndNoEntryWasDroppedAsMalformed() {
        int parsed = 0;
        for (int tier = 1; tier <= 4; tier++) {
            LootTable table = tables.get(tier);
            assertNotNull(table, "tier " + tier + " has no loot table");
            assertTrue(table.rolls() >= 4, "tier " + tier + " rolls");
            assertTrue(table.refillCooldownMinutes() > 0, "tier " + tier + " cooldown");
            parsed += table.entries().size();
        }
        // LootConfig skips malformed entries with only a warning; the shipped
        // file must never lose one that way.
        assertEquals(declaredEntries, parsed, "some shipped entries failed to parse");
    }

    @Test
    void everyRingCarriesAtLeastOneNamedRelic() {
        for (int tier = 1; tier <= 4; tier++) {
            boolean relic = tables.get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.ITEM && e.name() != null);
            assertTrue(relic, "tier " + tier + " has no named relic");
        }
    }

    @Test
    void namedRelicsRollWithDisplayNameAndNonItalicLore() {
        Random rng = new Random(42);
        ArmorSettings armor = new ArmorSettings(true, Map.of());
        for (LootTable table : tables.values()) {
            for (LootEntry entry : table.entries()) {
                if (entry.type() != LootEntry.Type.ITEM || entry.name() == null) {
                    continue;
                }
                ItemStack item = entry.roll(rng, armor);
                ItemMeta meta = item.getItemMeta();
                assertTrue(meta.hasDisplayName(), entry.name() + " lost its display name");
                assertFalse(entry.lore().isEmpty(), entry.name() + " should carry lore");
                for (Component line : meta.lore()) {
                    assertEquals(TextDecoration.State.FALSE,
                            line.decoration(TextDecoration.ITALIC),
                            entry.name() + " lore rendered italic");
                }
            }
        }
    }

    @Test
    void boatTokensForEveryLevelExistSomewhereInTheProgression() {
        Set<Integer> levels = new HashSet<>();
        for (LootTable table : tables.values()) {
            for (LootEntry entry : table.entries()) {
                if (entry.type() == LootEntry.Type.TOKEN) {
                    levels.add(entry.tokenLevel());
                }
            }
        }
        assertEquals(Set.of(1, 2, 3), levels, "token levels reachable from chests");
    }

    @Test
    void deeperRingsRefillSlower() {
        long previous = 0;
        for (int tier = 1; tier <= 4; tier++) {
            long cooldown = tables.get(tier).refillCooldownMinutes();
            assertTrue(cooldown > previous, "tier " + tier + " cooldown should exceed tier " + (tier - 1));
            previous = cooldown;
        }
    }

    @Test
    void armorProgressionTeasesTheNextTier() {
        for (int tier = 1; tier <= 3; tier++) {
            final int t = tier;
            boolean own = tables.get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == t);
            boolean tease = tables.get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == t + 1);
            assertTrue(own, "tier " + tier + " should drop its own armor");
            assertTrue(tease, "tier " + tier + " should tease tier " + (t + 1) + " armor");
        }
        assertTrue(tables.get(4).entries().stream()
                .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == 4));
    }
}
