package com.diamend.boxtutorial.listener;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.arena.Instance;
import com.diamend.boxtutorial.arena.MineSpec;
import com.diamend.boxtutorial.guide.StepTrigger;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rules inside the arena.
 *
 * <p>A tutorial arena is a room where only two things are meant to happen:
 * blocks come out of the mine, and items come out of the trader. Everything
 * else is refused — not to be strict, but because a new player who has just
 * dug a hole through the floor and fallen into the void has learned nothing
 * except that this server is broken.
 */
public class ArenaListener implements Listener {

    /** Don't repeat "that isn't the mine" more often than this, per player. */
    private static final long NAG_MILLIS = 4000L;

    private final BoxTutorialPlugin plugin;
    private final Map<UUID, Long> lastNag = new HashMap<>();

    public ArenaListener(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inArena(Player player) {
        return plugin.instances().isArenaWorld(player.getWorld());
    }

    // ------------------------------------------------------------------
    // Mining
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!inArena(player)) {
            return;
        }
        Instance instance = plugin.instances().of(player);
        MineSpec mine = instance == null ? null : instance.mineAt(event.getBlock());
        if (mine == null) {
            event.setCancelled(true);
            nag(player);
            return;
        }
        // Refill on the next tick, so the drop this break produces exists
        // before the block comes back.
        plugin.getServer().getScheduler().runTask(plugin, () -> instance.noteMined(mine));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inArena(event.getPlayer())) {
            event.setCancelled(true);
            nag(event.getPlayer());
        }
    }

    /** Tells them where the mine is, and not more than once every few seconds. */
    private void nag(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNag.get(player.getUniqueId());
        if (last != null && now - last < NAG_MILLIS) {
            return;
        }
        lastNag.put(player.getUniqueId(), now);
        plugin.messages().send(player, "arena-not-the-mine");
    }

    // ------------------------------------------------------------------
    // Buying
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        Player player = event.getPlayer();
        if (!inArena(player) || event.getTrade() == null) {
            return;
        }
        ItemStack result = event.getTrade().getResult();
        if (result == null) {
            return;
        }
        plugin.service().record(player, StepTrigger.BUY_ITEM,
                result.getType().name(), Math.max(1, result.getAmount()));
    }

    // ------------------------------------------------------------------
    // Keeping them safe and inside
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // Nothing in a tutorial should be able to hurt anybody — including the
        // void, which is what's under the floor.
        if (event.getEntity() instanceof Player player && inArena(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && inArena(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Instance instance = plugin.instances().of(event.getPlayer());
        if (instance != null && plugin.instances().isArenaWorld(event.getPlayer().getWorld())) {
            event.setRespawnLocation(instance.spawnPoint());
        }
    }

    /**
     * Leaving the arena world pauses the tutorial and gives the instance back.
     *
     * <p>They might have run {@code /spawn}, been summoned by staff, or simply
     * decided they'd had enough. None of those should leave an arena reserved
     * for somebody who isn't in it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inArena(player) || plugin.instances().of(player) == null) {
            return;
        }
        plugin.service().leftArena(player);
    }
}
