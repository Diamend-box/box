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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates in-game events into achievement progress via {@link AchievementService}.
 *
 * <p>When {@code count-player-placed-blocks} is disabled, blocks a player placed
 * are remembered (in memory) so breaking them again doesn't farm BLOCK_BREAK
 * objectives.
 */
public class AchievementTriggerListener implements Listener {

    private final Plugin plugin;
    private final AchievementService service;
    private final Set<String> playerPlaced = ConcurrentHashMap.newKeySet();

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
        service.handle(event.getEntity(), TriggerType.PLAYER_DEATH, (String) null, 1);
    }
}
