package com.diamend.darksea.vault;

import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.npc.ShopMenuService;
import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule vault cracking must never break: a soft reset heals and
 * restocks the island, but it does not re-seal a vault someone already opened.
 */
class VaultStateTest {

    private static IslandInstance island() {
        return new IslandInstance("t3-7", "ruined_castle", 3,
                new Pos(400, 64, -900), new Pos(380, 55, -920), new Pos(420, 90, -880),
                List.of(new Pos(400, 66, -900), new Pos(405, 66, -905),
                        new Pos(410, 66, -898), new Pos(395, 66, -910)),
                List.of(new Pos(402, 66, -902), new Pos(415, 70, -885)));
    }

    @Test
    void islandsStartSealed() {
        IslandInstance instance = island();
        assertFalse(instance.vaultsCracked());
        assertFalse(instance.vaultChests().isEmpty(), "a four-chest castle elects vaults");
    }

    @Test
    void crackingSurvivesASoftReset() {
        IslandInstance instance = island();
        instance.setVaultsCracked(true);
        instance.setVaultLever(new Pos(415, 70, -885));

        // A soft reset re-pastes and clears refill timers — and nothing else.
        instance.clearRefills();

        assertTrue(instance.vaultsCracked(), "the soft reset re-sealed the vaults");
        assertEquals(new Pos(415, 70, -885), instance.vaultLever());
    }

    @Test
    void crackedStateAndLeverRoundTripThroughIslandsYml() {
        IslandInstance instance = island();
        instance.setVaultsCracked(true);
        instance.setVaultLever(new Pos(415, 70, -885));

        YamlConfiguration yaml = new YamlConfiguration();
        instance.save(yaml.createSection("t3-7"));
        IslandInstance loaded = IslandInstance.load("t3-7",
                yaml.getConfigurationSection("t3-7"));

        assertTrue(loaded.vaultsCracked());
        assertEquals(new Pos(415, 70, -885), loaded.vaultLever());
        assertEquals(instance.vaultChests(), loaded.vaultChests());
    }

    @Test
    void anIslandWithoutALeverLoadsCleanly() {
        IslandInstance instance = island();
        YamlConfiguration yaml = new YamlConfiguration();
        instance.save(yaml.createSection("t3-7"));
        IslandInstance loaded = IslandInstance.load("t3-7",
                yaml.getConfigurationSection("t3-7"));

        assertFalse(loaded.vaultsCracked());
        assertNull(loaded.vaultLever());
        assertNotNull(loaded.vaultChest());
    }

    /** +X is east and +Z is south, so a clue that says "east" had better mean it. */
    @Test
    void bearingsPointTheRightWay() {
        assertEquals("north", ShopMenuService.compass(0, -100));
        assertEquals("south", ShopMenuService.compass(0, 100));
        assertEquals("east", ShopMenuService.compass(100, 0));
        assertEquals("west", ShopMenuService.compass(-100, 0));
        assertEquals("south-east", ShopMenuService.compass(100, 100));
        assertEquals("north-west", ShopMenuService.compass(-100, -100));
    }
}
