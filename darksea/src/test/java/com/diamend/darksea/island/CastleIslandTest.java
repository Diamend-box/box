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
        return islandAt(template, tier, chestCount, 1000, -2000);
    }

    private static IslandInstance islandAt(String template, int tier, int chestCount, int ox, int oz) {
        List<Pos> chests = new ArrayList<>();
        for (int i = 0; i < chestCount; i++) {
            chests.add(new Pos(ox + i * 9, 63, oz + i * 7));
        }
        return new IslandInstance("t" + tier + "-1", template, tier,
                new Pos(ox, 58, oz), new Pos(ox - 40, 50, oz - 40), new Pos(ox + 40, 80, oz + 40),
                chests, List.of(new Pos(ox + 1, 64, oz + 1)));
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
    void trenchCastlesGarrisonHarderAndSometimesRaiseACore() {
        // A Trench (ring 5) castle overflows with the plague and, on a rare
        // island, keeps a Core standing — a jackpot island the outer Naxome
        // fell to first. Below the Trench neither ever happens.
        assertEquals(4, island("ruined-castle", 4, 6).mobCapBonus(),
                "a ring-4 castle keeps its ordinary garrison");
        assertTrue(island("ruined-castle", 5, 7).mobCapBonus() > 8,
                "a Trench castle holds far more mobs");

        int trenchCores = 0, reachCores = 0, samples = 3000;
        for (int i = 0; i < samples; i++) {
            int ox = 1000 + i * 37, oz = -3000 - i * 53;
            if (islandAt("ruined-castle", 5, 7, ox, oz).bossMob() != null) {
                trenchCores++;
            }
            if (islandAt("ruined-castle", 4, 6, ox, oz).bossMob() != null) {
                reachCores++;
            }
        }
        assertEquals(0, reachCores, "no castle raises a Core below the Trench");
        double share = trenchCores / (double) samples;
        assertTrue(share > 0.04 && share < 0.22,
                "t5 castle Core share " + share + " outside the rare-but-real band");

        // The decision is stable per position — a soft reset re-raises (or
        // doesn't) the very same Core.
        IslandInstance same = islandAt("ruined-castle", 5, 7, 4242, -4242);
        assertEquals(same.bossMob(), islandAt("ruined-castle", 5, 7, 4242, -4242).bossMob(),
                "the Core roll must be deterministic per island");
    }

    @Test
    void nestIslandsSeedTheSoulwakeCompassButOrdinaryIslandsDoNot() {
        IslandInstance nest = island("mariphage-nest", 5, 4);
        assertEquals("soulwake_compass", nest.chestBonusItem(), "a nest seeds the compass");
        assertEquals(0.30, nest.chestBonusChance(true), 1e-9, "a nest chest seeds the compass at 30%");
        assertEquals(0.30, nest.chestBonusChance(false), 1e-9, "vault and plain niche seed alike");

        IslandInstance castle = island("ruined-castle", 5, 7);
        assertEquals(null, castle.chestBonusItem(), "a castle seeds no bonus item");
        assertEquals(0.0, castle.chestBonusChance(true), 1e-9);
        IslandInstance plain = island("demo", 4, 3);
        assertEquals(null, plain.chestBonusItem(), "a plain island seeds no bonus item");
        assertEquals(0.0, plain.chestBonusChance(false), 1e-9);
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
