package com.diamend.darksea.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island wealth and vault election: deterministic, bounded, spread out. */
class LootMathTest {

    @Test
    void wealthStaysInBoundsAndIsDeterministic() {
        double poorest = Double.MAX_VALUE;
        double richest = Double.MIN_VALUE;
        for (int x = -6000; x <= 6000; x += 397) {
            for (int z = -6000; z <= 6000; z += 401) {
                double wealth = LootMath.wealthMultiplier(x, z);
                assertEquals(wealth, LootMath.wealthMultiplier(x, z), "wealth must be stable");
                assertTrue(wealth >= LootMath.WEALTH_MIN && wealth < LootMath.WEALTH_MAX,
                        "wealth " + wealth + " out of bounds at " + x + "," + z);
                poorest = Math.min(poorest, wealth);
                richest = Math.max(richest, wealth);
            }
        }
        // "some islands having more than others" — the spread must be real.
        assertTrue(poorest < 0.8, "no poor islands found (min " + poorest + ")");
        assertTrue(richest > 1.5, "no rich islands found (max " + richest + ")");
    }

    @Test
    void vaultElectionIsDeterministicBoundedAndMultiChestOnly() {
        assertEquals(-1, LootMath.vaultChestIndex(100, 100, 0));
        assertEquals(-1, LootMath.vaultChestIndex(100, 100, 1));

        boolean[] seen = new boolean[3];
        for (int x = -5000; x <= 5000; x += 613) {
            for (int z = -5000; z <= 5000; z += 617) {
                int index = LootMath.vaultChestIndex(x, z, 3);
                assertEquals(index, LootMath.vaultChestIndex(x, z, 3), "election must be stable");
                assertTrue(index >= 0 && index < 3, "index " + index + " out of range");
                seen[index] = true;
            }
        }
        // Every chest position should win somewhere in the sea.
        assertTrue(seen[0] && seen[1] && seen[2], "vault election never picks some slots");
    }

    @Test
    void multiVaultElectionIsDistinctSortedStableAndLeavesAPlainChest() {
        assertEquals(0, LootMath.vaultChestIndices(10, 10, 1, 2).length,
                "a lone chest can never be a vault");
        assertEquals(0, LootMath.vaultChestIndices(10, 10, 5, 0).length);

        for (int x = -5000; x <= 5000; x += 613) {
            for (int z = -5000; z <= 5000; z += 617) {
                for (int chests = 2; chests <= 6; chests++) {
                    int[] vaults = LootMath.vaultChestIndices(x, z, chests, 2);
                    assertArrayEquals(vaults, LootMath.vaultChestIndices(x, z, chests, 2),
                            "election must be stable");
                    // Never every chest: something plain always remains.
                    assertEquals(Math.min(2, chests - 1), vaults.length,
                            chests + " chests at " + x + "," + z);
                    for (int i = 0; i < vaults.length; i++) {
                        assertTrue(vaults[i] >= 0 && vaults[i] < chests, "index out of range");
                        if (i > 0) {
                            assertTrue(vaults[i] > vaults[i - 1],
                                    "indices must be distinct and sorted");
                        }
                    }
                }
            }
        }

        // The single-vault path is just the k=1 election.
        assertEquals(LootMath.vaultChestIndices(1200, -300, 3, 1)[0],
                LootMath.vaultChestIndex(1200, -300, 3));

        // Across the sea, a six-chest castle's pair should land on many
        // different combinations, not always the same two doors.
        java.util.Set<String> combos = new java.util.HashSet<>();
        for (int x = -5000; x <= 5000; x += 613) {
            combos.add(java.util.Arrays.toString(LootMath.vaultChestIndices(x, 77, 6, 2)));
        }
        assertTrue(combos.size() > 5, "vault pairs barely vary: " + combos);
    }
}
