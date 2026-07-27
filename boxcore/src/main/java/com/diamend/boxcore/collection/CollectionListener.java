package com.diamend.boxcore.collection;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds collections from gameplay.
 *
 * <p>Block breaking counts the block's *drops* rather than the block itself, so
 * Silk Touch, Fortune and crop yields all behave the way a player expects.
 *
 * <p>When {@code count-player-placed-blocks} is off, blocks a player placed are
 * remembered so place-and-break can't farm a collection.
 */
public class CollectionListener implements Listener {

    /** Safety valve: forget placed blocks rather than grow without bound. */
    private static final int MAX_TRACKED_PLACEMENTS = 250_000;

    private final Plugin plugin;
    private final CollectionService service;
    private final Set<String> playerPlaced = ConcurrentHashMap.newKeySet();

    private boolean sourceBreak;
    private boolean sourceKill;
    private boolean sourceFish;
    private boolean sourceHarvest;
    private boolean sourceCraft;
    private boolean sourcePickup;
    private boolean countPlaced;

    public CollectionListener(Plugin plugin, CollectionService service) {
        this.plugin = plugin;
        this.service = service;
        reload();
    }

    /** Re-reads the config-backed source toggles. */
    public final void reload() {
        sourceBreak = flag("block-break", true);
        sourceKill = flag("entity-kill", true);
        sourceFish = flag("fishing", true);
        sourceHarvest = flag("harvest", true);
        sourceCraft = flag("craft", false);
        sourcePickup = flag("pickup", false);
        countPlaced = plugin.getConfig().getBoolean("collections.count-player-placed-blocks", false);
        if (countPlaced) {
            playerPlaced.clear();
        }
    }

    private boolean flag(String name, boolean fallback) {
        return plugin.getConfig().getBoolean("collections.sources." + name, fallback);
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ',' + block.getY() + ',' + block.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boolean wasPlaced = playerPlaced.remove(key(event.getBlock()));
        if (!sourceBreak || !event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (wasPlaced && !countPlaced) {
            return; // anti-farm
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (ItemStack drop : event.getBlock().getDrops(tool, player)) {
            credit(player, drop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (countPlaced) {
            return;
        }
        if (playerPlaced.size() >= MAX_TRACKED_PLACEMENTS) {
            playerPlaced.clear();
            plugin.getLogger().info("Placed-block anti-farm cache was full and has been cleared.");
        }
        playerPlaced.add(key(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        if (!sourceKill) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            credit(killer, drop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!sourceFish || event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (event.getCaught() instanceof Item item) {
            credit(event.getPlayer(), item.getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!sourceHarvest) {
            return;
        }
        for (ItemStack harvested : event.getItemsHarvested()) {
            credit(event.getPlayer(), harvested);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!sourcePickup || !(event.getEntity() instanceof Player player)) {
            return;
        }
        credit(player, event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!sourceCraft || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe() == null ? event.getCurrentItem() : event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        int crafts = event.isShiftClick() ? craftsFromMatrix(event) : 1;
        if (crafts <= 0) {
            return;
        }
        service.add(player, result.getType(), result.getAmount() * crafts);
    }

    /**
     * How many times a shift-click craft will repeat: the smallest stack among
     * the ingredients. It's the same approximation the vanilla client makes,
     * and it can't over-count.
     */
    private int craftsFromMatrix(CraftItemEvent event) {
        int crafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && !ingredient.getType().isAir()) {
                crafts = Math.min(crafts, ingredient.getAmount());
            }
        }
        return crafts == Integer.MAX_VALUE ? 1 : crafts;
    }

    private void credit(Player player, ItemStack stack) {
        if (stack != null && !stack.getType().isAir()) {
            service.add(player, stack.getType(), stack.getAmount());
        }
    }
}
