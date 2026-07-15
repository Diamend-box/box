package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
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

/**
 * Translates in-game events into achievement progress via {@link AchievementService}.
 */
public class AchievementTriggerListener implements Listener {

    private final AchievementService service;

    public AchievementTriggerListener(AchievementService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        service.handle(event.getPlayer(), TriggerType.BLOCK_BREAK, event.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
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
        service.handle(player, TriggerType.ITEM_CRAFT, result.getType().name(), Math.max(1, result.getAmount()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        service.handle(event.getPlayer(), TriggerType.ITEM_CONSUME, event.getItem().getType().name(), 1);
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
