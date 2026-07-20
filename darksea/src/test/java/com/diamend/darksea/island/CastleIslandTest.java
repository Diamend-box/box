package com.diamend.darksea.island;

import com.diamend.darksea.loot.LootMath;
import com.diamend.darksea.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape-aware island behavior: an island whose template is the ruined
 * castle elects two vaults, garrisons one ring deeper with a bigger cap,
 * and never rolls poor — while plain islands keep the classic behavior.
 */
class CastleIslandTest {

    private static IslandInstance island(String template, int tier, int chestCount) {
        List<Pos> chests = new ArrayList<>();
        for (int i = 0; i < chestCount; i++) {
            chests.add(new Pos(1000 + i * 9, 63, -2000 + i * 7));
        }
        return new IslandInstance("t" + tier + "-1", template, tier,
                new Pos(1000, 58, -2000), new Pos(960, 50, -2040), new Pos(1040, 80, -1960),
                chests, List.of(new Pos(1001, 64, -2001)));
    }

    @Test
    void castleIslandsElectTwoStableVaultsAmongTheirChests() {
        IslandInstance castle = island("ruined-castle", 2, 4);
        List<Pos> vaults = castle.vaultChests();
        assertEquals(2, vaults.size(), "a castle hides two vaults");
        assertEquals(vaults, castle.vaultChests(), "election must be stable");
        int marked = 0;
        for (Pos chest : castle.chests()) {
            assertTrue(!castle.isVaultChest(chest) || vaults.contains(chest));
            if (castle.isVaultChest(chest)) {
                marked++;
            }
        }
        assertEquals(2, marked, "exactly two chests answer as vaults");
        assertTrue(castle.chests().size() - marked >= 1, "something plain must remain");
    }

    @Test
    void ordinaryIslandsKeepTheSingleVault() {
        IslandInstance plain = island("demo", 2, 4);
        assertEquals(1, plain.vaultChests().size());
        IslandInstance schematic = island("wreck_small", 3, 2);
        assertEquals(1, schematic.vaultChests().size());
    }

    @Test
    void castleGarrisonFightsOneRingDeeperWithABiggerCap() {
        IslandInstance castle = island("ruined-castle", 2, 4);
        assertEquals(3, castle.mobTier(), "a ring-2 castle fights ring 3's roster");
        assertTrue(castle.mobCapBonus() > 0, "a castle holds more mobs at once");

        IslandInstance plain = island("demo", 2, 1);
        assertEquals(2, plain.mobTier());
        assertEquals(0, plain.mobCapBonus());

        // Ring 4 castles ask for a tier-5 pool; the spawner falls back to
        // the ring's own pool, so the request itself is fine to make.
        assertEquals(5, island("ruined-castle", 4, 6).mobTier());
    }

    @Test
    void castlesNeverDrownPoorButOrdinaryIslandsStillCan() {
        IslandInstance castle = island("ruined-castle", 3, 5);
        assertTrue(castle.wealthMultiplier() >= 1.4,
                "castle wealth " + castle.wealthMultiplier() + " under the floor");
        assertTrue(castle.wealthMultiplier() < LootMath.WEALTH_MAX);

        IslandInstance plain = island("demo", 3, 1);
        assertEquals(LootMath.wealthMultiplier(1000, -2000), plain.wealthMultiplier(),
                "plain islands keep the raw position roll");
    }
}
