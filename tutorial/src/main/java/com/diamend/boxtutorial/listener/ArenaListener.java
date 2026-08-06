package com.diamend.boxtutorial.listener;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.arena.Instance;
import com.diamend.boxtutorial.arena.MineSpec;
import com.diamend.boxtutorial.arena.TradeSpec;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.items.ItemRef;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.MerchantInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        payOut(event, mine);
        // Refill on the next tick, so the drop this break produces exists
        // before the block comes back.
        plugin.getServer().getScheduler().runTask(plugin, () -> instance.noteMined(mine));
    }

    /**
     * Hands over what the mine says a block is worth, when that isn't its
     * ordinary drop.
     *
     * <p>It's how the wood mine makes dark oak worth finding: the block is a
     * tenth of the mine and drops four oak logs, so the rare one is a small
     * windfall rather than a different item the player has to work out what to
     * do with. The drop is thrown on the floor rather than pushed into the
     * inventory, because a tutorial teaching the game should look like the game.
     */
    private void payOut(BlockBreakEvent event, MineSpec mine) {
        ItemStack drop = mine.dropFor(event.getBlock().getType());
        if (drop == null) {
            return;
        }
        event.setDropItems(false);
        Block block = event.getBlock();
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.25, 0.5), drop);
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
        if (!isPaidFor(player, tradeFor(result))) {
            event.setCancelled(true);
            plugin.messages().send(player, "arena-wrong-payment");
            return;
        }
        plugin.service().recordItem(player, StepTrigger.BUY_ITEM,
                result, Math.max(1, result.getAmount()));
    }

    /**
     * Checks a trade whose price is one of the plugin's own items.
     *
     * <p>Vanilla decides for itself how closely an ingredient has to match, and
     * "closely enough" has moved between versions. A charm that costs one
     * compressed log must not be buyable with an ordinary log, so the cost is
     * confirmed against the registry before the trade is allowed through.
     */
    private boolean isPaidFor(Player player, TradeSpec trade) {
        if (trade == null || !trade.hasCustomCost()) {
            return true;
        }
        List<ItemStack> paid = onTheCounter(player);
        for (ItemRef ref : trade.cost()) {
            if (!ref.isCustom()) {
                continue;
            }
            int offered = 0;
            for (ItemStack item : paid) {
                if (ref.matches(plugin.items(), item)) {
                    offered += item.getAmount();
                }
            }
            if (offered < ref.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * What the player is actually paying with: the two cost slots of the open
     * trade window.
     *
     * <p>Not their inventory. By the time this event fires the payment has
     * already left the backpack for those slots, so a player buying the charm
     * with the only compressed log they own looks — to anything counting the
     * backpack — like a player holding nothing, and gets told they can't afford
     * the trade they are standing there paying for.
     *
     * <p>Reading the counter is also the stricter check of the two: it asks
     * whether the item on the table is the real one, rather than whether a real
     * one exists somewhere on the player while something else buys the charm.
     */
    private List<ItemStack> onTheCounter(Player player) {
        try {
            if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory window) {
                List<ItemStack> slots = new ArrayList<>();
                for (int slot = 0; slot < 2 && slot < window.getSize(); slot++) {
                    ItemStack item = window.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        slots.add(item);
                    }
                }
                if (!slots.isEmpty()) {
                    return slots;
                }
            }
        } catch (Throwable ignored) {
            // No window to read — another plugin driving the merchant, or a test.
        }
        List<ItemStack> carried = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                carried.add(item);
            }
        }
        return carried;
    }

    /** The configured trade that hands over this item. */
    private TradeSpec tradeFor(ItemStack result) {
        for (TradeSpec trade : plugin.blueprint().trades()) {
            ItemStack product = trade.result().resolve(plugin.items());
            if (product != null && product.isSimilar(result)) {
                return trade;
            }
        }
        return null;
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
