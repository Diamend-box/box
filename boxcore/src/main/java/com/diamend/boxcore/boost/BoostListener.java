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

    /**
     * A block a boosted player just broke, and what its drops are worth.
     *
     * <p>Mutable because {@link #captured} is written after the fact: whether
     * anything was found on the ground decides whether the inventory is worth
     * looking at, and that is only known once the tick has played out.
     */
    private static final class Recent {
        private final UUID player;
        private final Location at;
        private final double multiplier;
        private final long expiresAt;
        /** Whether an item entity was boosted for this break. */
        private boolean captured;
        /** What the breaker was carrying, when the inventory is being watched. */
        private List<ItemStack> before;

        private Recent(UUID player, Location at, double multiplier, long expiresAt) {
            this.player = player;
            this.at = at;
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }
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
        Recent note = new Recent(player.getUniqueId(),
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                multiplier, now + module.dropWindowTicks() * 50L);
        recent.add(note);

        if (!module.captureInventory()) {
            return;
        }
        note.before = snapshot(player);
        // Look again once the tick has finished. Anything that reached the
        // player without ever being an item on the ground shows up as the
        // difference, and nothing else does — a block break and an unrelated
        // inventory change inside the same tick is not a thing that happens.
        plugin.getServer().getScheduler().runTask(plugin, () -> settle(note));
    }

    /**
     * The last resort: boost what the player gained, when nothing was dropped.
     *
     * <p>Some plugins hand a block's yield straight to the inventory and never
     * spawn an item at all. There is no event for that — no drop event, no
     * spawn event, no pickup event — so the only evidence it happened is that
     * the player is holding more than they were a tick ago. This runs only when
     * the other two paths found nothing, so a drop that did hit the ground is
     * never counted twice.
     */
    private void settle(Recent note) {
        if (note.captured || note.before == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(note.player);
        if (player == null || !player.isOnline()) {
            return;
        }
        for (ItemStack gained : gains(note.before, snapshot(player))) {
            if (module.oresOnly() && !plugin.ores().isOre(gained.getType())) {
                continue;
            }
            long extra = Boost.scale(gained.getAmount(), note.multiplier) - gained.getAmount();
            int max = Math.max(1, gained.getType().getMaxStackSize());
            while (extra > 0) {
                ItemStack copy = gained.clone();
                copy.setAmount((int) Math.min(extra, max));
                extra -= copy.getAmount();
                // Straight to the inventory, because that is where the drop
                // this is doubling went. Only the overflow hits the floor.
                for (ItemStack spill : player.getInventory().addItem(copy).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), spill);
                }
            }
        }
    }

    /** Everything the player is carrying, as independent copies. */
    private List<ItemStack> snapshot(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    /**
     * What is in {@code after} that wasn't in {@code before}.
     *
     * <p>Matched by {@link ItemStack#isSimilar}, not by material, so a custom
     * item another plugin minted is doubled as itself rather than as a plain
     * stack of whatever it happens to be made of.
     */
    private List<ItemStack> gains(List<ItemStack> before, List<ItemStack> after) {
        List<ItemStack> gained = new ArrayList<>();
        for (ItemStack now : after) {
            int had = 0;
            for (ItemStack was : before) {
                if (was.isSimilar(now)) {
                    had += was.getAmount();
                }
            }
            int has = 0;
            for (ItemStack other : after) {
                if (other.isSimilar(now)) {
                    has += other.getAmount();
                }
            }
            boolean counted = false;
            for (ItemStack already : gained) {
                if (already.isSimilar(now)) {
                    counted = true;
                    break;
                }
            }
            if (counted || has <= had) {
                continue;
            }
            ItemStack delta = now.clone();
            delta.setAmount(has - had);
            gained.add(delta);
        }
        return gained;
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
        Recent note = nearest(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                System.currentTimeMillis());
        if (note != null) {
            note.captured = true;
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
            from.captured = true;
            grow(entity, from.multiplier);
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
            Location from = candidate.at;
            if (candidate.expiresAt < now || from.getWorld() == null
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
        if (recent.removeIf(entry -> entry.expiresAt < now) && recent.isEmpty()) {
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
