package com.diamend.darksea.loot;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.island.shape.DemoShape;
import com.diamend.darksea.island.shape.DemoShapes;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Refills registered island chests on open, when the tier's cooldown has
 * elapsed since the last refill. Runs before the inventory opens, so the
 * player sees the fresh loot. Timestamps persist in islands.yml.
 */
public final class ChestRefillService implements Listener {

    private final DarkSeaPlugin plugin;
    private final IslandRegistry registry;
    private final Random rng = new Random();

    public ChestRefillService(DarkSeaPlugin plugin, IslandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }
        if (!plugin.isDarkSea(block.getWorld())) {
            return;
        }
        IslandRegistry.ChestRef ref = registry.chestAt(block.getX(), block.getY(), block.getZ());
        if (ref == null) {
            return;
        }
        boolean vault = ref.island().isVaultChest(ref.pos());
        LootTable table = plugin.lootTables().forChest(ref.island().tier(), vault);
        if (table == null) {
            return;
        }
        table = thinnedForShape(table, ref.island().template(), vault);
        long now = System.currentTimeMillis();
        if (now - ref.island().lastRefill(ref.pos()) < table.refillCooldownMillis()) {
            return;
        }
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        double wealth = ref.island().wealthMultiplier();
        List<ItemStack> loot = table.rollLoot(rng, plugin.settings().armor(), wealth);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, rng);
        boolean markRun = plugin.settings().combat().runLootDeath();
        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            ItemStack item = loot.get(i);
            if (markRun) {
                RunLoot.tag(item);   // gathered this run — lost on death until banked at home
            }
            inventory.setItem(slots.get(i), item);
        }
        // A shape's signature cache — the Core nest seeds the Soulwake Compass
        // into its chests, richer odds in the vaults — dropped on top of the
        // rolled table into whatever slot is left free.
        String bonusId = ref.island().chestBonusItem();
        if (bonusId != null && rng.nextDouble() < ref.island().chestBonusChance(vault)) {
            ItemStack bonus = DarkSeaItems.create(bonusId, 1);
            int slot = inventory.firstEmpty();
            if (bonus != null && slot >= 0) {
                if (markRun) {
                    RunLoot.tag(bonus);
                }
                inventory.setItem(slot, bonus);
            }
        }
        ref.island().setRefilled(ref.pos(), now);
        registry.save();
    }

    /**
     * Applies the island shape's chest-roll delta to an ordinary chest.
     *
     * A shape that scatters many chests through a big building (the castle's
     * garrison caches) makes each one thinner, so the building reads as
     * inhabited without paying out several islands' worth. Vaults are never
     * thinned — being worth finding is the entire point of a vault — and a
     * chest never drops below one roll.
     */
    private LootTable thinnedForShape(LootTable table, String template, boolean vault) {
        if (vault || template == null) {
            return table;
        }
        DemoShape shape = DemoShapes.byId(template);
        if (shape == null || shape.chestRollDelta() == 0) {
            return table;
        }
        int rolls = Math.max(1, table.rolls() + shape.chestRollDelta());
        if (rolls == table.rolls()) {
            return table;
        }
        return new LootTable(table.tier(), rolls, table.refillCooldownMinutes(), table.entries());
    }

}
