package com.diamend.darksea.mob;

import com.diamend.darksea.armor.VironicArmor;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped drops: section and the parser/roller behind it. */
class MobDropsTest {

    private static final Logger LOG = Logger.getLogger("MobDropsTest");

    private YamlConfiguration shipped;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        shipped = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/mobs.yml")) {
            assertNotNull(in, "mobs.yml missing from resources");
            shipped.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyShippedMobHasDropsAndNoLineIsDroppedAsMalformed() {
        Map<String, List<MobDrops.Line>> drops = MobDrops.parse(shipped, LOG);

        int declared = 0;
        int parsed = 0;
        for (String key : shipped.getConfigurationSection("drops").getKeys(false)) {
            declared += shipped.getConfigurationSection("drops").getMapList(key).size();
        }
        for (List<MobDrops.Line> lines : drops.values()) {
            parsed += lines.size();
        }
        assertEquals(declared, parsed, "some shipped drop lines failed to parse");

        // Every mob on the tier roster earns something when killed.
        for (String tierKey : shipped.getConfigurationSection("tiers").getKeys(false)) {
            for (Map<?, ?> entry : shipped.getMapList("tiers." + tierKey)) {
                String type = String.valueOf(entry.get("type"));
                assertTrue(drops.containsKey(type), type + " has no drops entry");
            }
        }
    }

    @Test
    void cultistsDropTheSetOfTheirOwnRank() {
        Map<String, List<MobDrops.Line>> drops = MobDrops.parse(shipped, LOG);
        for (int tier = 1; tier <= 4; tier++) {
            String type = "Vironic" + VironicArmor.rankName(tier);
            int finalTier = tier;
            assertTrue(drops.get(type).stream()
                            .anyMatch(l -> l.isSet() && l.setTier() == finalTier),
                    type + " should drop its rank-" + tier + " set");
        }
    }

    /** Wyatt's call: the Core's chase drops must NOT be guaranteed. */
    @Test
    void coreChaseDropsAreChaseDropsButTheHoardIsCertain() {
        Map<String, List<MobDrops.Line>> drops = MobDrops.parse(shipped, LOG);
        List<MobDrops.Line> core = drops.get("MariphageCore");
        assertNotNull(core, "the Core drops nothing?");

        MobDrops.Line stinger = core.stream()
                .filter(l -> DarkSeaItems.MARIPHAGE_STINGER.equals(l.itemId())).findFirst().orElseThrow();
        MobDrops.Line vector = core.stream()
                .filter(l -> "relic_mariphage_vector".equals(l.itemId())).findFirst().orElseThrow();
        MobDrops.Line chronons = core.stream()
                .filter(l -> DarkSeaItems.CHRONON.equals(l.itemId())).findFirst().orElseThrow();

        assertTrue(stinger.chance() > 0 && stinger.chance() < 1, "Stinger must be a chase drop");
        assertTrue(vector.chance() > 0 && vector.chance() < 1, "Vector must be a chase drop");
        assertEquals(1.0, chronons.chance(), "the Chronon hoard always drops");
        assertTrue(chronons.min() >= 20, "a boss hoard should be a hoard");
    }

    @Test
    void parserSkipsGarbageLinesButKeepsGoodOnes() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                drops:
                  SomeMob:
                    - { id: sailors_cutlass, chance: 0.5 }
                    - { id: not_a_real_item, chance: 0.5 }
                    - { id: chronon, chance: 1.5 }
                    - { set: vironic, tier: 9, chance: 0.5 }
                    - { set: illuminati, tier: 2, chance: 0.5 }
                    - { set: vironic, tier: 2, chance: 0.5 }
                """);
        List<MobDrops.Line> lines = MobDrops.parse(yaml, LOG).get("SomeMob");
        assertEquals(2, lines.size(), "exactly the cutlass and the valid set line survive");
    }

    @Test
    void rollingProducesProperlyTaggedItems() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                drops:
                  TestMob:
                    - { id: naxian_claw, chance: 1.0 }
                    - { id: chronon, min: 5, max: 9, chance: 1.0 }
                    - { set: vironic, tier: 3, chance: 1.0 }
                """);
        List<MobDrops.Line> lines = MobDrops.parse(yaml, LOG).get("TestMob");
        Random rng = new Random(7);
        for (int i = 0; i < 100; i++) {
            List<ItemStack> drops = MobDrops.roll(lines, rng);
            assertEquals(3, drops.size());
            assertEquals(DarkSeaItems.NAXIAN_CLAW, DarkSeaItems.idOf(drops.get(0)));
            assertTrue(DarkSeaItems.isChronon(drops.get(1)));
            int amount = drops.get(1).getAmount();
            assertTrue(amount >= 5 && amount <= 9, "chronon amount " + amount);
            assertTrue(VironicArmor.isVironic(drops.get(2)));
        }
    }
}
