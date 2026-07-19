package com.diamend.darksea.loot;

import java.util.Map;

/**
 * Everything loot.yml defines: the per-tier base tables every chest rolls,
 * and the per-tier VAULT tables — the one richer chest a multi-chest island
 * hides (tier 3 islands hide two chests, tier 4 three; exactly one of them
 * is the vault, picked deterministically per island).
 */
public record LootTables(Map<Integer, LootTable> base, Map<Integer, LootTable> vault) {

    public static LootTables empty() {
        return new LootTables(Map.of(), Map.of());
    }

    /**
     * The table a chest should roll: the tier's vault table when the chest
     * is the island's vault (falling back to base if no vault is defined).
     */
    public LootTable forChest(int tier, boolean isVault) {
        if (isVault) {
            LootTable table = vault.get(tier);
            if (table != null) {
                return table;
            }
        }
        return base.get(tier);
    }
}
