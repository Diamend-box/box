package com.diamend.darksea.loot;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.island.IslandRegistry;
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
        if (!block.getWorld().getName().equals(plugin.settings().worldName())) {
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
        long now = System.currentTimeMillis();
        if (now - ref.island().lastRefill(ref.pos()) < table.refillCooldownMillis()) {
            return;
        }
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        double wealth = LootMath.wealthMultiplier(ref.island().origin().x(), ref.island().origin().z());
        List<ItemStack> loot = table.rollLoot(rng, plugin.settings().armor(), wealth);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, rng);
        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            inventory.setItem(slots.get(i), loot.get(i));
        }
        ref.island().setRefilled(ref.pos(), now);
        registry.save();
    }
}
