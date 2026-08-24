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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
     * How far from a broken block an item can appear and still be its drop.
     *
     * <p>Vanilla drops spawn inside the block. A plugin spawning its own tends
     * to use the block's own location, so this only has to be generous enough
     * to cover the scatter, not to reach the next block along.
     */
    private static final double CAPTURE_RADIUS = 2.0;

    /** A block a boosted player just broke, and what its drops are worth. */
    private record Recent(Location at, double multiplier, long expiresAt) {
    }

    /** Breaks still inside their capture window, oldest first. */
    private final List<Recent> recent = new ArrayList<>();

    /** Item entities already boosted, so the safety net never doubles them. */
    private final Set<UUID> boosted = new HashSet<>();

    /** True while spawning our own extra drops, which must not be boosted again. */
    private boolean minting;

    /**
     * Notes that a boosted player broke a block here.
     *
     * <p>This deliberately does <em>not</em> ignore cancelled breaks. A plugin
     * that replaces a block's drops entirely — CustomDrops is the one on this
     * server — cancels the break and spawns its own items, and refusing to
     * record a cancelled break is exactly why boosting did nothing to them.
     * Recording is only a note that a boosted player was here; it grants
     * nothing on its own.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        double multiplier = module.multiplier(player, BoostType.DROPS);
        if (multiplier <= 1.0) {
            return;
        }
        long now = System.currentTimeMillis();
        prune(now);
        recent.add(new Recent(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                multiplier, now + module.dropWindowTicks() * 50L));
    }

    /**
     * Boosts a block's drops as the game hands them over.
     *
     * <p>This is the precise path: the drop list belongs to a known block and a
     * known player, so there is no guessing involved. It runs at
     * {@code HIGHEST} to let every other plugin finish editing the list first,
     * which is what makes the boost multiply their result rather than land
     * beside it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        double multiplier = module.multiplier(event.getPlayer(), BoostType.DROPS);
        if (multiplier <= 1.0) {
            return;
        }
        for (Item entity : event.getItems()) {
            grow(entity, multiplier);
        }
    }

    /**
     * The safety net: anything that appeared next to a block a boosted player
     * just broke, however it got there.
     *
     * <p>{@link BlockDropItemEvent} only carries drops the <em>game</em>
     * produced. A plugin that cancels the break and spawns its own item
     * entities never passes through it, and no event priority reaches that —
     * which is what the first playtest found. Item entities always spawn,
     * though, so watching for one near a recent break catches every source
     * without needing that plugin's API.
     *
     * <p>Two things stop this double-counting: entities already boosted through
     * the drop event are remembered and skipped, and our own extra drops are
     * spawned behind a flag.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (minting) {
            return;
        }
        long now = System.currentTimeMillis();
        prune(now);
        if (recent.isEmpty()) {
            return;
        }
        Item entity = event.getEntity();
        if (boosted.contains(entity.getUniqueId())) {
            return;
        }
        Recent from = nearest(entity.getLocation(), now);
        if (from != null) {
            grow(entity, from.multiplier());
        }
    }

    /**
     * Multiplies one item entity in place, spilling the overflow into new
     * stacks.
     *
     * <p>Growing the entity rather than spawning a replacement is what keeps
     * this from recursing: no new entity means no new spawn event. Only what
     * will not fit in a single stack has to be dropped separately, and an item
     * entity holding more than a stack is not something the rest of the game
     * handles well.
     */
    private void grow(Item entity, double multiplier) {
        ItemStack stack = entity.getItemStack();
        if (module.oresOnly() && !plugin.ores().isOre(stack.getType())) {
            return;
        }
        long extra = Boost.scale(stack.getAmount(), multiplier) - stack.getAmount();
        if (extra <= 0) {
            return;
        }
        boosted.add(entity.getUniqueId());

        int max = Math.max(1, stack.getType().getMaxStackSize());
        int room = Math.max(0, max - stack.getAmount());
        int fits = (int) Math.min(extra, room);
        if (fits > 0) {
            stack.setAmount(stack.getAmount() + fits);
            entity.setItemStack(stack);
            extra -= fits;
        }
        if (extra <= 0) {
            return;
        }
        Location at = entity.getLocation();
        if (at.getWorld() == null) {
            return;
        }
        minting = true;
        try {
            while (extra > 0) {
                ItemStack copy = stack.clone();
                copy.setAmount((int) Math.min(extra, max));
                at.getWorld().dropItemNaturally(at, copy);
                extra -= copy.getAmount();
            }
        } finally {
            minting = false;
        }
    }

    /** The recent break this location belongs to, or null. */
    private Recent nearest(Location at, long now) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        for (Recent candidate : recent) {
            Location from = candidate.at();
            if (candidate.expiresAt() < now || from.getWorld() == null
                    || !from.getWorld().equals(at.getWorld())) {
                continue;
            }
            if (from.distanceSquared(at) <= CAPTURE_RADIUS * CAPTURE_RADIUS) {
                return candidate;
            }
        }
        return null;
    }

    /** Drops breaks and remembered entities once their window has passed. */
    private void prune(long now) {
        if (recent.removeIf(entry -> entry.expiresAt() < now) && recent.isEmpty()) {
            // Nothing is being watched, so nothing needs remembering either.
            boosted.clear();
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
