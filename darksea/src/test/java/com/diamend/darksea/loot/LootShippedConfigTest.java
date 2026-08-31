package com.diamend.darksea.loot;

import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.island.IslandPlacer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SHIPPED loot.yml must always parse cleanly: a typo'd material or
 * custom id would otherwise surface as silently thinner chests on the
 * server. Also locks the Loot 2.0 shape — every ring has a base AND a
 * richer vault table, Chronons flow everywhere and grow with depth, every
 * ring carries a real (PDC-tagged) relic, chest weapons stay a band below
 * mob drops, and nothing in a chest is enchanted.
 */
class LootShippedConfigTest {

    private static final Logger LOG = Logger.getLogger("LootShippedConfigTest");

    /** Weapons only the cursed themselves drop — never chest loot. */
    private static final Set<String> MOB_ONLY_WEAPONS = Set.of(
            DarkSeaItems.SAILORS_CUTLASS, DarkSeaItems.NAXIAN_CLAW,
            DarkSeaItems.ENHANCED_NAXIAN_CLAW, DarkSeaItems.ABOMINATION_BONE,
            DarkSeaItems.MARIPHAGE_STINGER);

    private static final Set<String> CHEST_WEAPONS = Set.of(
            DarkSeaItems.NAXIAN_BOARDING_AXE, DarkSeaItems.HARBORGUARD_PIKE,
            DarkSeaItems.ORDER_CEREMONIAL_BLADE);

    private static final Set<String> CONSUMABLES = Set.of(
            DarkSeaItems.TIDAL_DRAUGHT, DarkSeaItems.SEA_SALVE,
            DarkSeaItems.DEEPSIGHT_TONIC, DarkSeaItems.GILLWATER_PHILTER);

    private LootTables tables;

    /**
     * The deepest ring loot must cover, derived from the shape roster rather
     * than hardcoded — tier 5 shipped with no tables at all because this test
     * only ever counted to 4. Any shape that reaches a new ring now demands
     * loot for it.
     */
    private int maxTier;
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
            ConfigurationSection tier = config.getConfigurationSection("tiers." + key);
            declaredEntries += tier.getMapList("entries").size();
            ConfigurationSection vault = tier.getConfigurationSection("vault");
            if (vault != null) {
                declaredEntries += vault.getMapList("entries").size();
            }
        }
        tables = LootConfig.load(config, LOG);
        maxTier = deepestGeneratedTier();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Every tier an island can actually be registered at: the configured
     * rings, plus the cultist landfall's own tier. Read from config.yml
     * rather than hardcoded — ring 5 shipped with no loot tables at all
     * because this test only ever counted to 4, and a chest with no table
     * fills with nothing and logs nothing.
     */
    private static int deepestGeneratedTier() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = LootShippedConfigTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml missing from resources");
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        ConfigurationSection rings =
                config.getConfigurationSection("generation.islands-per-ring");
        assertNotNull(rings, "config.yml declares no islands-per-ring");
        int deepest = IslandPlacer.LANDFALL_TIER;
        for (String key : rings.getKeys(false)) {
            if (rings.getInt(key) > 0) {
                deepest = Math.max(deepest, Integer.parseInt(key));
            }
        }
        return deepest;
    }

    private List<LootEntry> allEntries(int tier) {
        List<LootEntry> entries = new java.util.ArrayList<>(tables.base().get(tier).entries());
        entries.addAll(tables.vault().get(tier).entries());
        return entries;
    }

    private static boolean isCustom(LootEntry entry, String id) {
        return entry.type() == LootEntry.Type.CUSTOM && id.equals(entry.customId());
    }

    @Test
    void everyRingHasBaseAndVaultAndNoEntryWasDroppedAsMalformed() {
        int parsed = 0;
        for (int tier = 1; tier <= maxTier; tier++) {
            LootTable base = tables.base().get(tier);
            LootTable vault = tables.vault().get(tier);
            assertNotNull(base, "tier " + tier + " has no base table");
            assertNotNull(vault, "tier " + tier + " has no vault table");
            assertTrue(base.rolls() >= 4, "tier " + tier + " base rolls");
            assertTrue(vault.rolls() > base.rolls(),
                    "tier " + tier + " vault must roll more than its base chests");
            assertEquals(base.refillCooldownMinutes(), vault.refillCooldownMinutes(),
                    "tier " + tier + " vault should inherit the tier cooldown");
            parsed += base.entries().size() + vault.entries().size();
        }
        // LootConfig skips malformed entries with only a warning; the shipped
        // file must never lose one that way.
        assertEquals(declaredEntries, parsed, "some shipped entries failed to parse");
    }

    @Test
    void chrononsFlowInEveryRingAndGrowWithDepthAndVaults() {
        int previousMax = 0;
        for (int tier = 1; tier <= maxTier; tier++) {
            LootEntry base = tables.base().get(tier).entries().stream()
                    .filter(e -> isCustom(e, DarkSeaItems.CHRONON)).findFirst().orElse(null);
            LootEntry vault = tables.vault().get(tier).entries().stream()
                    .filter(e -> isCustom(e, DarkSeaItems.CHRONON)).findFirst().orElse(null);
            assertNotNull(base, "tier " + tier + " base drops no Chronons");
            assertNotNull(vault, "tier " + tier + " vault drops no Chronons");
            assertTrue(base.max() >= previousMax, "tier " + tier + " Chronon piles shrank");
            assertTrue(vault.max() > base.max(),
                    "tier " + tier + " vault Chronon piles should beat base chests");
            previousMax = base.max();
        }
    }

    @Test
    void everyRingCarriesARealRelicAndTheVectorStaysWithTheCore() {
        for (int tier = 1; tier <= maxTier; tier++) {
            boolean baseRelic = tables.base().get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.CUSTOM
                            && e.customId().startsWith("relic_"));
            assertTrue(baseRelic, "tier " + tier + " has no relic in its base table");
        }
        for (int tier = 1; tier <= maxTier; tier++) {
            assertTrue(allEntries(tier).stream()
                            .noneMatch(e -> isCustom(e, "relic_mariphage_vector")),
                    "the Vector must only ever come from the Core");
        }
    }

    @Test
    void chestWeaponsEverywhereButMobSignatureWeaponsNever() {
        for (int tier = 1; tier <= maxTier; tier++) {
            assertTrue(allEntries(tier).stream().anyMatch(e ->
                            e.type() == LootEntry.Type.CUSTOM && CHEST_WEAPONS.contains(e.customId())),
                    "tier " + tier + " offers no chest weapon at all");
            assertTrue(allEntries(tier).stream().noneMatch(e ->
                            e.type() == LootEntry.Type.CUSTOM && MOB_ONLY_WEAPONS.contains(e.customId())),
                    "tier " + tier + " leaks a mob-only weapon into chests");
        }
    }

    @Test
    void everyRingOffersSomethingToDrink() {
        for (int tier = 1; tier <= maxTier; tier++) {
            assertTrue(allEntries(tier).stream().anyMatch(e ->
                            e.type() == LootEntry.Type.CUSTOM && CONSUMABLES.contains(e.customId())),
                    "tier " + tier + " has no consumable");
        }
    }

    @Test
    void everyCustomEntryRollsTaggedNamedAndUnenchanted() {
        Random rng = new Random(42);
        ArmorSettings armor = new ArmorSettings(true, Map.of());
        for (int tier = 1; tier <= maxTier; tier++) {
            for (LootEntry entry : allEntries(tier)) {
                if (entry.type() != LootEntry.Type.CUSTOM) {
                    continue;
                }
                ItemStack item = entry.roll(rng, armor);
                assertNotNull(item, entry.customId() + " rolled null");
                assertEquals(entry.customId(), DarkSeaItems.idOf(item),
                        entry.customId() + " rolled without its PDC identity");
                assertTrue(item.getItemMeta().hasDisplayName());
                assertTrue(item.getEnchantments().isEmpty(),
                        entry.customId() + " rolled enchanted — chest gear must stay clean");
            }
        }
    }

    @Test
    void namedSalvageStillRollsWithDisplayNameAndNonItalicLore() {
        Random rng = new Random(42);
        ArmorSettings armor = new ArmorSettings(true, Map.of());
        int named = 0;
        for (int tier = 1; tier <= maxTier; tier++) {
            for (LootEntry entry : allEntries(tier)) {
                if (entry.type() != LootEntry.Type.ITEM || entry.name() == null) {
                    continue;
                }
                named++;
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
        assertTrue(named >= 4, "the junk band lost its flavor salvage");
    }

    @Test
    @DisplayName("every ring can pay for a boat: the treasure band carries real coin, not tokens")
    void everyRingPaysEnoughCoinToProgressAHull() {
        // Boat tiers are bought with Chronons now, so the thing that has to be
        // reachable from chests is money rather than five distinct token levels.
        // A ring whose richest coin entry is pocket change cannot fund the hull
        // its own danger assumes you are sailing.
        for (int tier = 1; tier <= maxTier; tier++) {
            int best = 0;
            for (LootEntry entry : allEntries(tier)) {
                if (entry.type() == LootEntry.Type.CUSTOM
                        && DarkSeaItems.CHRONON.equals(entry.customId())) {
                    best = Math.max(best, entry.max());
                }
            }
            assertTrue(best >= 10 * tier,
                    "ring " + tier + "'s fattest Chronon entry is only " + best);
        }
    }
    @Test
    void deeperRingsRefillSlower() {
        long previous = 0;
        for (int tier = 1; tier <= maxTier; tier++) {
            long cooldown = tables.base().get(tier).refillCooldownMinutes();
            assertTrue(cooldown > previous, "tier " + tier + " cooldown should exceed tier " + (tier - 1));
            previous = cooldown;
        }
    }

    @Test
    void armorProgressionTeasesTheNextTierInBaseAndVaultAlike() {
        for (int tier = 1; tier <= 3; tier++) {
            final int t = tier;
            for (Map.Entry<String, LootTable> side : Map.of(
                    "base", tables.base().get(tier), "vault", tables.vault().get(tier)).entrySet()) {
                boolean own = side.getValue().entries().stream()
                        .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == t);
                boolean tease = side.getValue().entries().stream()
                        .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == t + 1);
                assertTrue(own, "tier " + tier + " " + side.getKey() + " should drop its own armor");
                assertTrue(tease, "tier " + tier + " " + side.getKey() + " should tease tier " + (t + 1));
            }
        }
        // Tier 4 is the armor ceiling, so every ring past the tease band just
        // keeps dropping Leviathan plate — there is nothing above it to tease.
        for (int tier = 4; tier <= maxTier; tier++) {
            assertTrue(tables.base().get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == 4),
                    "tier " + tier + " base should still drop tier-4 armor");
            assertTrue(tables.vault().get(tier).entries().stream()
                    .anyMatch(e -> e.type() == LootEntry.Type.ARMOR && e.armorTier() == 4),
                    "tier " + tier + " vault should still drop tier-4 armor");
        }
    }
}
