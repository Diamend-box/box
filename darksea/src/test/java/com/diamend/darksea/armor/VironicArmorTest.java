package com.diamend.darksea.armor;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Order's sets: naming, virus protection, and the full-set blessing. */
class VironicArmorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plainName(ItemStack item) {
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static ItemStack[] fullSet(int tier) {
        ItemStack[] set = new ItemStack[4];
        SeaArmor.Piece[] pieces = SeaArmor.Piece.values();
        for (int i = 0; i < pieces.length; i++) {
            set[i] = VironicArmor.createPiece(tier, pieces[i]);
        }
        return set;
    }

    @Test
    void namesFollowRankAndGarb() {
        assertEquals("Vironic Initiate Hood",
                plainName(VironicArmor.createPiece(1, SeaArmor.Piece.HELMET)));
        assertEquals("Vironic Acolyte Boots",
                plainName(VironicArmor.createPiece(2, SeaArmor.Piece.BOOTS)));
        assertEquals("Vironic Templar Vestments",
                plainName(VironicArmor.createPiece(3, SeaArmor.Piece.CHESTPLATE)));
        assertEquals("Vironic Lord Robes",
                plainName(VironicArmor.createPiece(4, SeaArmor.Piece.LEGGINGS)));
        assertNull(VironicArmor.createPiece(9, SeaArmor.Piece.HELMET));
    }

    @Test
    void piecesAreClothButProtectLikeSeaArmorOfTheirTier() {
        for (int tier = 1; tier <= 4; tier++) {
            for (SeaArmor.Piece piece : SeaArmor.Piece.values()) {
                ItemStack item = VironicArmor.createPiece(tier, piece);
                assertTrue(item.getType().name().startsWith("LEATHER_"),
                        "cultist garb must be cloth, got " + item.getType());
                assertEquals(tier, SeaArmor.tierOf(item),
                        "rank " + VironicArmor.rankName(tier) + " must protect at tier " + tier);
                assertTrue(VironicArmor.isVironic(item));
                assertTrue(item.getItemMeta().isUnbreakable());
            }
            // A full set slots straight into the exposure formula.
            assertEquals(tier, SeaArmor.effectiveTier(fullSet(tier)));
        }
        assertEquals(0, SeaArmor.tierOf(new ItemStack(Material.LEATHER_HELMET)));
    }

    /**
     * Wyatt's rule: each rank's set protects at "the earliest level you can
     * find them". mobs.yml is the authority on where ranks first appear, so
     * this test reads it and locks the two files together.
     */
    @Test
    void ranksMatchTheirFirstRingInMobsYml() throws Exception {
        YamlConfiguration mobs = new YamlConfiguration();
        try (InputStream in = getClass().getResourceAsStream("/mobs.yml")) {
            mobs.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (int tier = 1; tier <= 4; tier++) {
            String expected = "Vironic" + VironicArmor.rankName(tier);
            List<Map<?, ?>> entries = mobs.getMapList("tiers." + tier);
            assertTrue(entries.stream().anyMatch(e -> expected.equals(String.valueOf(e.get("type")))),
                    expected + " should first appear in ring " + tier);
        }
    }

    @Test
    void fullSetBlessingIsTwoDamagePerTier() {
        assertEquals(2.0, VironicArmor.setBonusDamage(fullSet(1)));
        assertEquals(4.0, VironicArmor.setBonusDamage(fullSet(2)));
        assertEquals(8.0, VironicArmor.setBonusDamage(fullSet(4)));
    }

    @Test
    void anythingLessThanFullCommitmentBreaksTheBlessing() {
        // A lower-rank piece drags the whole blessing to its tier.
        ItemStack[] mixed = fullSet(3);
        mixed[2] = VironicArmor.createPiece(1, SeaArmor.Piece.LEGGINGS);
        assertEquals(2.0, VironicArmor.setBonusDamage(mixed));

        // A missing slot, a vanilla piece, or real sea armor: no blessing.
        ItemStack[] missing = fullSet(4);
        missing[0] = null;
        assertEquals(0.0, VironicArmor.setBonusDamage(missing));

        ItemStack[] vanilla = fullSet(4);
        vanilla[1] = new ItemStack(Material.NETHERITE_CHESTPLATE);
        assertEquals(0.0, VironicArmor.setBonusDamage(vanilla));

        ItemStack[] empty = new ItemStack[4];
        assertEquals(0.0, VironicArmor.setBonusDamage(empty));
    }
}
