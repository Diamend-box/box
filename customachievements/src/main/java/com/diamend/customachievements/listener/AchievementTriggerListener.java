package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Translates in-game events into achievement progress via {@link AchievementService}.
 *
 * <p>When {@code count-player-placed-blocks} is disabled, blocks a player placed
 * are remembered (in memory) so breaking them again doesn't farm BLOCK_BREAK
 * objectives.
 */
public class AchievementTriggerListener implements Listener {

    /**
     * Upper bound on remembered placed-block positions. Block break/place events
     * are main-thread, so this is a best-effort, non-persistent anti-farm guard;
     * capping it stops the set from growing without limit on build-heavy servers
     * (the oldest positions are evicted first).
     */
    private static final int MAX_TRACKED_PLACED = 50_000;

    private final Plugin plugin;
    private final AchievementService service;
    private final Set<String> playerPlaced = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(1024, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_PLACED;
                }
            }));

    public AchievementTriggerListener(Plugin plugin, AchievementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ',' + block.getY() + ',' + block.getZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boolean wasPlaced = playerPlaced.remove(key(event.getBlock()));
        if (wasPlaced && !plugin.getConfig().getBoolean("count-player-placed-blocks", true)) {
            return; // anti-farm: don't count breaking a block the player placed
        }
        service.handle(event.getPlayer(), TriggerType.BLOCK_BREAK, event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("count-player-placed-blocks", true)) {
            playerPlaced.add(key(event.getBlock()));
        }
        service.handle(event.getPlayer(), TriggerType.BLOCK_PLACE, event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.handle(killer, TriggerType.ENTITY_KILL, event.getEntityType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : event.getCurrentItem();
        if (result == null) {
            return;
        }
        service.handleItem(player, TriggerType.ITEM_CRAFT, result, Math.max(1, result.getAmount()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        service.handleItem(event.getPlayer(), TriggerType.ITEM_CONSUME, event.getItem(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack stack = event.getItem().getItemStack();
            service.handleItem(player, TriggerType.ITEM_OBTAIN, stack, Math.max(1, stack.getAmount()));
            // The item lands in the inventory after this event, so re-read the
            // "have X items" gauges on the next tick rather than now.
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    service.handleItemInventory(player);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            service.handle(event.getPlayer(), TriggerType.FISH_CAUGHT, (String) null, 1);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        org.bukkit.event.entity.EntityDamageEvent damage = player.getLastDamageCause();
        String cause = damage != null ? damage.getCause().name() : null;
        service.handleDeath(player, cause, killerName(player, damage));
    }

    /**
     * What killed the player, as an entity type name, or null when nothing did
     * (fall damage, lava, ...). Projectiles resolve to whoever fired them, so
     * "killed by a skeleton" counts rather than "killed by an arrow".
     */
    private static String killerName(Player player, org.bukkit.event.entity.EntityDamageEvent damage) {
        org.bukkit.entity.Entity source = player.getKiller();
        if (source == null && damage instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            source = byEntity.getDamager();
        }
        if (source instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            source = shooter;
        }
        return source == null ? null : source.getType().name();
    }

    /**
     * Counts items taken out of a container — chests, furnace and trade results,
     * loot — toward ITEM_OBTAIN, which otherwise only sees items picked up off
     * the ground. Rearranging your own inventory or crafting grid isn't
     * obtaining anything, and neither is clicking one of this plugin's menus.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryTake(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder()
                instanceof com.diamend.customachievements.gui.Menu) {
            return;
        }
        org.bukkit.inventory.Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        org.bukkit.event.inventory.InventoryType type = clicked.getType();
        if (type == org.bukkit.event.inventory.InventoryType.PLAYER
                || type == org.bukkit.event.inventory.InventoryType.CRAFTING
                || type == org.bukkit.event.inventory.InventoryType.WORKBENCH
                || type == org.bukkit.event.inventory.InventoryType.CREATIVE) {
            return;
        }
        ItemStack taken = event.getCurrentItem();
        if (taken == null || taken.getType().isAir()) {
            return;
        }
        int amount = switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD ->
                    taken.getAmount();
            case PICKUP_HALF -> (taken.getAmount() + 1) / 2;
            case PICKUP_ONE -> 1;
            default -> 0;
        };
        if (amount > 0) {
            service.handleItem(player, TriggerType.ITEM_OBTAIN, taken, amount);
        }
    }

    /** Re-reads "have X items" gauges once a player finishes with a container. */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.handleItemInventory(player);
        }
    }
}
