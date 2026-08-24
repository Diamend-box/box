package com.diamend.boxcore.boost;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Event surface for boosts: multiplying drops, and spending boost items.
 *
 * <p>Drops are multiplied on {@link BlockDropItemEvent} rather than
 * {@code BlockBreakEvent}, because by then the game has already decided what
 * the block gives — Fortune, Silk Touch and every other modifier included — so
 * a boost multiplies the real yield instead of second-guessing it.
 */
public class BoostListener implements Listener {

    private final BoxCorePlugin plugin;
    private final BoostsModule module;

    public BoostListener(BoxCorePlugin plugin, BoostsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /**
     * Adds the boosted share of a block's drops.
     *
     * <p>The extra is dropped as its own stacks rather than by inflating the
     * ones the game made. An item entity holding more than a stack is not
     * something the rest of the game handles well, and splitting is free.
     *
     * <p>This runs at {@code HIGHEST} rather than {@code HIGH} so that every
     * other plugin editing the drop list has already had its turn. That is what
     * makes the boost <em>multiplicative</em>: a drop-replacing plugin like
     * CustomDrops, or a bonus-drop skill like AuraSkills' Lucky Miner, has
     * already put its items in the list, and the boost multiplies that result
     * instead of landing beside it. Running earlier made the two additive,
     * which is not what a 2x boost is understood to mean.
     *
     * <p>The limit worth knowing: this can only multiply what is actually in
     * {@link BlockDropItemEvent}. A plugin that cancels the break and spawns
     * its own item entities never passes through here at all, and no priority
     * fixes that — it needs that plugin's own API.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        double multiplier = module.multiplier(player, BoostType.DROPS);
        if (multiplier <= 1.0) {
            return;
        }
        List<Item> dropped = event.getItems();
        if (dropped.isEmpty()) {
            return;
        }
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (at.getWorld() == null) {
            return;
        }
        for (Item entity : dropped) {
            ItemStack stack = entity.getItemStack();
            if (module.oresOnly() && !plugin.ores().isOre(stack.getType())) {
                continue;
            }
            long extra = Boost.scale(stack.getAmount(), multiplier) - stack.getAmount();
            int max = Math.max(1, stack.getType().getMaxStackSize());
            while (extra > 0) {
                ItemStack copy = stack.clone();
                copy.setAmount((int) Math.min(extra, max));
                at.getWorld().dropItemNaturally(at, copy);
                extra -= copy.getAmount();
            }
        }
    }

    /** Right-click a boost item to start it. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        BoostItems.Payload payload = module.items().read(held);
        if (payload == null) {
            return;
        }
        // Opening a chest while carrying a boost must not spend it.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
                return;
            }
        }
        event.setCancelled(true);

        module.activate(player, payload);
        held.setAmount(held.getAmount() - 1);

        // A global boost has already announced itself to the whole server,
        // this player included. Sending the personal line too would tell them
        // twice and imply the boost was only theirs.
        if (!payload.global()) {
            plugin.messages().send(player, "boost-activated",
                    "type", payload.typeNames(),
                    "multiplier", Text.decimal(payload.multiplier()),
                    "duration", Durations.format(payload.durationMillis()));
        }
    }

    /** Tells a joining player what is already running, if anything. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<String> active = module.summaryFor(player);
        if (active.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.messages().sendLiteral(player, "<gray>Boosts running:");
            for (String line : active) {
                plugin.messages().sendPlain(player, "  " + line);
            }
        }, 20L);
    }
}
