package com.diamend.darksea.loot;

import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A tier's weighted loot table: {@code rolls} independent weighted picks per refill. */
public record LootTable(int tier, int rolls, long refillCooldownMinutes, List<LootEntry> entries) {

    public LootEntry pick(Random rng) {
        int total = 0;
        for (LootEntry entry : entries) {
            total += entry.weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        for (LootEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    public List<ItemStack> rollLoot(Random rng, ArmorSettings armor) {
        return rollLoot(rng, armor, 1.0);
    }

    /** {@code chrononScale}: the island's wealth multiplier, applied to Chronon piles only. */
    public List<ItemStack> rollLoot(Random rng, ArmorSettings armor, double chrononScale) {
        List<ItemStack> items = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = pick(rng);
            if (entry == null) {
                continue;
            }
            ItemStack item = entry.roll(rng, armor, chrononScale);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    public long refillCooldownMillis() {
        return refillCooldownMinutes * 60_000L;
    }
}
