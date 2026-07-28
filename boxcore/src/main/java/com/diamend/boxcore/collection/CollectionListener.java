package com.diamend.boxcore.collection;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Feeds collections from gameplay.
 *
 * <p>Block breaking counts the block's *drops* rather than the block itself, so
 * Silk Touch, Fortune and crop yields all behave the way a player expects.
 *
 * <p>When {@code count-player-placed-blocks} is off, blocks a player placed are
 * flagged in {@link PlacedBlocks} so place-and-break can't farm a collection.
 * Only blocks that could actually feed a collection are flagged — nobody needs
 * a region file recording every dirt block on the server.
 */
public class CollectionListener implements Listener {

    private final Plugin plugin;
    private final CollectionService service;
    private final PlacedBlocks placed;

    private boolean sourceBreak;
    private boolean sourceKill;
    private boolean sourceFish;
    private boolean sourceHarvest;
    private boolean sourceCraft;
    private boolean sourcePickup;
    private boolean countPlaced;

    public CollectionListener(Plugin plugin, CollectionService service, PlacedBlocks placed) {
        this.plugin = plugin;
        this.service = service;
        this.placed = placed;
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
    }

    private boolean flag(String name, boolean fallback) {
        return plugin.getConfig().getBoolean("collections.sources." + name, fallback);
    }

    public PlacedBlocks placedBlocks() {
        return placed;
    }

    // ------------------------------------------------------------------
    // Counting
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // Consume the flag whichever way this break goes: the block is gone, so
        // leaving the flag behind would only mislead the next block here.
        boolean wasPlaced = !countPlaced && placed.consume(event.getBlock());
        if (!sourceBreak || !event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (wasPlaced) {
            return; // anti-farm
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (ItemStack drop : event.getBlock().getDrops(tool, player)) {
            credit(player, drop);
        }
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

    // ------------------------------------------------------------------
    // Placed-block flags
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (countPlaced) {
            return;
        }
        // A bed, a door or a tall flower arrives as a multi-place: every block
        // it occupies has to be flagged, not just the one that was clicked.
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                Block block = state.getBlock();
                if (worthTracking(block)) {
                    placed.mark(block);
                }
            }
            return;
        }
        if (worthTracking(event.getBlock())) {
            placed.mark(event.getBlock());
        }
    }

    /**
     * Whether this block could ever credit a collection — either directly, or
     * through what it drops. Everything else is left untracked so region files
     * only carry flags that can change an outcome.
     */
    private boolean worthTracking(Block block) {
        CollectionManager collections = service.collections();
        if (!collections.forMaterial(block.getType()).isEmpty()) {
            return true; // Silk Touch returns the block itself
        }
        for (ItemStack drop : block.getDrops()) {
            if (!collections.forMaterial(drop.getType()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!countPlaced) {
            // Extending pushes the blocks away from the piston.
            shift(event.getBlock(), event.getBlocks(), event.getDirection(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!countPlaced) {
            // A sticky piston pulls them back towards itself.
            shift(event.getBlock(), event.getBlocks(), event.getDirection(), true);
        }
    }

    /**
     * Moves the flags on a run of pushed blocks.
     *
     * <p>The event's direction is taken as an axis rather than a sense: whether
     * it already points the right way is decided by measuring against the piston
     * itself, which is true for both events regardless of how the server reports
     * a retraction.
     */
    private void shift(Block piston, List<Block> blocks, BlockFace face, boolean towardsPiston) {
        if (blocks.isEmpty() || face == null) {
            return;
        }
        int dx = face.getModX();
        int dy = face.getModY();
        int dz = face.getModZ();
        Block sample = blocks.get(0);
        long before = distanceSquared(piston, sample.getX(), sample.getY(), sample.getZ());
        long after = distanceSquared(piston, sample.getX() + dx, sample.getY() + dy, sample.getZ() + dz);
        if ((after < before) != towardsPiston) {
            dx = -dx;
            dy = -dy;
            dz = -dz;
        }
        placed.shift(blocks, dx, dy, dz);
    }

    private static long distanceSquared(Block from, int x, int y, int z) {
        long dx = (long) from.getX() - x;
        long dy = (long) from.getY() - y;
        long dz = (long) from.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /*
     * Anything else that destroys a block drops its flag too. A flag that
     * outlives its block would sit there waiting to refuse whatever appears in
     * that spot next.
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (countPlaced) {
            return;
        }
        placed.clear(event.getBlock());
        for (Block block : event.blockList()) {
            placed.clear(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (countPlaced) {
            return;
        }
        for (Block block : event.blockList()) {
            placed.clear(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!countPlaced) {
            placed.clear(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (!countPlaced) {
            placed.clear(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!countPlaced) {
            placed.clear(event.getBlock());
        }
    }
}
