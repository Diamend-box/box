package com.diamend.darksea.world.cultist;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.util.Pos;
import com.diamend.darksea.world.ManagedWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Taking crystal out of a geode, on the plugin's clock rather than vanilla's.
 *
 * <p><b>Why this exists.</b> Vanilla block hardness cannot be overridden, and by
 * the time a player reaches the caves their pickaxe breaks anything instantly
 * regardless of what block we choose. Left alone, a hundred-and-fifty-block
 * geode would evaporate in fifteen seconds and none of the "a vein is contested
 * ground you have to stand still at" design would survive contact. So extraction
 * is a channel: the block does not break, we drive the crack overlay ourselves,
 * and {@link MiningSpeed} decides how long it takes from what the player is
 * holding.
 *
 * <p>Because we own the curve, gear matters on our terms.
 * {@code reference-efficiency} in {@code ores.yml} says what pickaxe the
 * configured times assume; everything scales off that, so a mid-game pickaxe is
 * several times slower and a Haste beacon is a real advantage without being able
 * to drive a block below {@code floor-seconds}.
 *
 * <p>A channel is cancelled by anything that means the player stopped: letting
 * go, looking at a different block, swapping the held item, walking away, or
 * logging out. All of them reset the overlay, so a half-broken block never
 * stays visually cracked for the next person who walks up to it.
 */
public final class ExtractionChannel implements Listener {

    /** How far a player may drift from where they started before the channel drops. */
    private static final double MAX_DRIFT_SQUARED = 9.0;

    /**
     * How long a channel survives without hearing from the client before it is
     * treated as abandoned.
     *
     * <p>This grace period is load bearing, and the reason is subtle.
     * {@code setInstaBreak(false)} stops the block vanishing on the first tick,
     * but vanilla still runs its <em>own</em> dig timer underneath ours — and
     * against an endgame pickaxe that timer completes almost immediately. The
     * resulting {@link org.bukkit.event.block.BlockBreakEvent} is cancelled by
     * {@link NodeService}, the block is resent, and the client starts digging
     * again. So a player holding the button down produces a stream of
     * start/cancel churn, and an abort in the middle of it means nothing.
     *
     * <p>Ending the channel the instant an abort arrived would therefore make
     * crystal <em>unmineable</em> with exactly the gear the caves are tuned for.
     * Instead an abort simply stops refreshing the channel, and half a second of
     * genuine silence ends it.
     */
    private static final int GRACE_TICKS = 10;

    /** One in-progress extraction. */
    private static final class Channel {
        private final Pos target;
        private final Location anchor;
        private final ItemStack tool;
        private final int totalTicks;
        private int elapsedTicks;
        private int idleTicks;

        Channel(Pos target, Location anchor, ItemStack tool, int totalTicks) {
            this.target = target;
            this.anchor = anchor;
            this.tool = tool;
            this.totalTicks = totalTicks;
        }

        float progress() {
            return Math.min(1.0f, (float) elapsedTicks / (float) totalTicks);
        }

        /** The client is still digging this block. */
        void refresh() {
            idleTicks = 0;
        }

        boolean abandoned() {
            return idleTicks > GRACE_TICKS;
        }
    }

    private final DarkSeaPlugin plugin;
    private final NodeService nodes;
    private final Map<UUID, Channel> active = new HashMap<>();
    private BukkitRunnable ticker;

    public ExtractionChannel(DarkSeaPlugin plugin, NodeService nodes) {
        this.plugin = plugin;
        this.nodes = nodes;
    }

    /** Starts the per-tick pass. Called once, when the plugin enables. */
    public void start() {
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        ticker.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        for (UUID id : Map.copyOf(active).keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                abort(player);
            }
        }
        active.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    // ------------------------------------------------------------------
    // Opening a channel
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (plugin.managedWorld(block.getWorld()) != ManagedWorld.CULTIST) {
            return;
        }
        if (player.getGameMode().name().equals("CREATIVE")
                && player.hasPermission("darksea.admin")) {
            return;
        }

        // Froglights and the like break in a single vanilla tick. Refusing the
        // insta-break is what buys us the time to run our own timer at all —
        // without it the block is gone before the first tick of the channel.
        event.setInstaBreak(false);

        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        NodeRegistry.Node node = nodes.registry().nodeAt(pos);
        if (node == null) {
            return;   // plain rock; NodeService's break handler explains it
        }
        OreType type = plugin.oreTables().byId(node.typeId());
        if (type == null) {
            return;
        }
        Material core = Material.matchMaterial(type.coreBlock());
        if (core == null || block.getType() != core) {
            return;   // already taken, waiting on the regrow
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        MiningSpeed.Tier tier = MiningSpeed.tierOf(held.getType().name());
        if (tier == null) {
            // Told, not stonewalled. Without this a player hitting a geode with
            // a sword gets silence and assumes the block is decorative.
            plugin.messages().send(player, "caves-needs-pickaxe");
            return;
        }

        Channel existing = active.get(player.getUniqueId());
        if (existing != null && existing.target.equals(pos)) {
            // Same block. This fires repeatedly as vanilla's own dig timer
            // completes and gets cancelled, so it is a heartbeat, not a restart —
            // keep the progress and just mark the client as still with us.
            existing.refresh();
            return;
        }
        if (existing != null) {
            clear(player, existing);
        }

        OreTables tables = plugin.oreTables();
        double seconds = MiningSpeed.seconds(type.mineSeconds(), tables.referenceSpeed(),
                MiningSpeed.speed(tier, efficiencyOf(held), levelOf(player, PotionEffectType.HASTE),
                        levelOf(player, PotionEffectType.MINING_FATIGUE)),
                tables.floorSeconds());
        active.put(player.getUniqueId(), new Channel(pos, player.getLocation().clone(),
                held.clone(), MiningSpeed.ticks(seconds)));
    }

    /** Efficiency on the held tool, or 0. */
    private static int efficiencyOf(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta()) {
            return 0;
        }
        return tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
    }

    /** A potion effect's level as vanilla counts it — 1-based, 0 for absent. */
    private static int levelOf(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    // ------------------------------------------------------------------
    // Running it
    // ------------------------------------------------------------------

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Channel> entry : Map.copyOf(active).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Channel channel = entry.getValue();
            if (player == null || !player.isOnline()) {
                active.remove(entry.getKey());
                continue;
            }
            if (!stillValid(player, channel) || channel.abandoned()) {
                abort(player);
                continue;
            }
            channel.idleTicks++;
            channel.elapsedTicks++;
            Block block = blockOf(player, channel);
            player.sendBlockDamage(block.getLocation(), channel.progress());
            if (channel.elapsedTicks >= channel.totalTicks) {
                complete(player, channel, block);
            }
        }
    }

    private boolean stillValid(Player player, Channel channel) {
        if (!player.getWorld().equals(channel.anchor.getWorld())) {
            return false;
        }
        if (player.getLocation().distanceSquared(channel.anchor) > MAX_DRIFT_SQUARED) {
            return false;
        }
        // Swapping tools mid-channel would let someone start a block with a fast
        // pickaxe and finish it with none, so the tool is pinned for the
        // duration rather than re-read each tick.
        return player.getInventory().getItemInMainHand().isSimilar(channel.tool);
    }

    private void complete(Player player, Channel channel, Block block) {
        active.remove(player.getUniqueId());
        player.sendBlockDamage(block.getLocation(), 0.0f);

        NodeRegistry.Node node = nodes.registry().nodeAt(channel.target);
        if (node == null) {
            return;
        }
        OreType type = plugin.oreTables().byId(node.typeId());
        if (type == null) {
            return;
        }
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        Material broken = block.getType();
        if (nodes.extract(player, block, node, type)) {
            player.playSound(centre, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.1f);
            player.getWorld().spawnParticle(Particle.BLOCK, centre, 24, 0.25, 0.25, 0.25,
                    broken.createBlockData());
        }
    }

    // ------------------------------------------------------------------
    // Stopping
    // ------------------------------------------------------------------

    /**
     * Letting go stops refreshing the channel rather than killing it outright.
     * See {@link #GRACE_TICKS} — aborts also arrive as a side effect of vanilla's
     * dig timer being cancelled mid-hold, so treating one as "the player
     * stopped" would break mining for fast pickaxes.
     */
    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        Channel channel = active.get(event.getPlayer().getUniqueId());
        if (channel != null) {
            channel.idleTicks = GRACE_TICKS;   // one more quiet tick ends it
        }
    }

    /**
     * Swapping the held item drops the channel. Handled here as well as in
     * {@link #stillValid} so the overlay clears on the same tick as the swap
     * rather than one tick later.
     */
    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        abort(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    private void abort(Player player) {
        Channel channel = active.remove(player.getUniqueId());
        if (channel != null) {
            clear(player, channel);
        }
    }

    /** Wipes the crack overlay so a partly-worked block does not look damaged. */
    private void clear(Player player, Channel channel) {
        player.sendBlockDamage(blockOf(player, channel).getLocation(), 0.0f);
    }

    private Block blockOf(Player player, Channel channel) {
        return player.getWorld().getBlockAt(channel.target.x(), channel.target.y(),
                channel.target.z());
    }
}
