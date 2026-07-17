package com.diamend.darksea.armor;

import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.config.DarkSeaSettings.ArmorStyle;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item tagging and the lowest-worn-piece protection model. */
class SeaArmorTest {

    private ArmorSettings armor;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        armor = new ArmorSettings(true, Map.of(
                1, new ArmorStyle("<aqua>Tidewalker</aqua>", "CHAINMAIL"),
                2, new ArmorStyle("<gray>Stormplate</gray>", "IRON"),
                3, new ArmorStyle("<dark_purple>Abyssal</dark_purple>", "DIAMOND"),
                4, new ArmorStyle("<dark_aqua>Leviathan</dark_aqua>", "NETHERITE")));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsTaggedPiecesWithTheRightMaterial() {
        ItemStack helmet = SeaArmor.createPiece(armor, 2, SeaArmor.Piece.HELMET);
        assertEquals(Material.IRON_HELMET, helmet.getType());
        assertEquals(2, SeaArmor.tierOf(helmet));
        assertTrue(helmet.getItemMeta().isUnbreakable());

        ItemStack boots = SeaArmor.createPiece(armor, 4, SeaArmor.Piece.BOOTS);
        assertEquals(Material.NETHERITE_BOOTS, boots.getType());
        assertEquals(4, SeaArmor.tierOf(boots));
    }

    @Test
    void undefinedTierProducesNothing() {
        assertNull(SeaArmor.createPiece(armor, 9, SeaArmor.Piece.HELMET));
    }

    @Test
    void createSetProducesAllFourPieces() {
        List<ItemStack> set = SeaArmor.createSet(armor, 3);
        assertEquals(4, set.size());
        for (ItemStack item : set) {
            assertEquals(3, SeaArmor.tierOf(item));
        }
    }

    @Test
    void vanillaAndNullItemsAreTierZero() {
        assertEquals(0, SeaArmor.tierOf(null));
        assertEquals(0, SeaArmor.tierOf(new ItemStack(Material.NETHERITE_HELMET)));
    }

    @Test
    void effectiveTierIsTheLowestWornPiece() {
        ItemStack[] fullSet2 = SeaArmor.createSet(armor, 2).toArray(new ItemStack[0]);
        assertEquals(2, SeaArmor.effectiveTier(fullSet2));

        // One weaker piece drags the whole set down.
        ItemStack[] mixed = SeaArmor.createSet(armor, 3).toArray(new ItemStack[0]);
        mixed[1] = SeaArmor.createPiece(armor, 1, SeaArmor.Piece.CHESTPLATE);
        assertEquals(1, SeaArmor.effectiveTier(mixed));

        // A missing piece counts as tier 0 — full commitment required.
        ItemStack[] incomplete = SeaArmor.createSet(armor, 4).toArray(new ItemStack[0]);
        incomplete[0] = null;
        assertEquals(0, SeaArmor.effectiveTier(incomplete));

        // A vanilla (untagged) piece also counts as 0.
        ItemStack[] faked = SeaArmor.createSet(armor, 4).toArray(new ItemStack[0]);
        faked[2] = new ItemStack(Material.NETHERITE_LEGGINGS);
        assertEquals(0, SeaArmor.effectiveTier(faked));
    }

    @Test
    void tokenRoundTrip() {
        ItemStack token = SeaArmor.createToken(2);
        assertEquals(Material.HEART_OF_THE_SEA, token.getType());
        assertEquals(2, SeaArmor.tokenLevelOf(token));
        assertEquals(0, SeaArmor.tokenLevelOf(new ItemStack(Material.HEART_OF_THE_SEA)));
        assertEquals(0, SeaArmor.tokenLevelOf(null));
        // Armor tags and token tags don't cross-read.
        assertEquals(0, SeaArmor.tierOf(token));
    }
}
