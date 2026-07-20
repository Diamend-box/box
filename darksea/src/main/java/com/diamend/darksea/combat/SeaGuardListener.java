package com.diamend.darksea.combat;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * The boxpvp guard rails for the Dark Sea (see
 * {@link com.diamend.darksea.config.DarkSeaSettings.CombatSettings}):
 *
 * <ul>
 *   <li><b>Islands are protected loot-content.</b> Generated islands can't be
 *       broken, built on, bucket-griefed or blown up — only their chests open.
 *       The island itself is the map players fight over, not a quarry.</li>
 *   <li><b>PvP everywhere but the home sanctuary.</b> PvP rides the server's
 *       own setting across the open sea; this listener only carves out the
 *       spawn island — within {@code pvp-safe-radius} of center nobody can be
 *       struck by another player, nor grief a block.</li>
 * </ul>
 *
 * <p>Admins ({@code darksea.admin}) bypass block protection so the hand-built
 * home island stays editable. Explosions are trimmed rather than cancelled, so
 * a creeper can still down a careless player without cratering the island.
 */
public final class SeaGuardListener implements Listener {

    private final DarkSeaPlugin plugin;

    public SeaGuardListener(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Block protection
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (guardedForPlayer(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (guardedForPlayer(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (guardedForPlayer(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (guardedForPlayer(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> guarded(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> guarded(block.getLocation()));
    }

    // ------------------------------------------------------------------
    // PvP sanctuary
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;  // mob or environmental damage is the sea's danger — allowed everywhere
        }
        if (!inDarkSea(victim.getLocation())) {
            return;
        }
        if (withinSanctuary(victim.getLocation()) || withinSanctuary(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** The player behind a hit: the melee attacker, or a projectile's shooter. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Shared geometry
    // ------------------------------------------------------------------

    /** A block a specific player may not touch (admins bypass protection). */
    private boolean guardedForPlayer(Block block, Player player) {
        if (player.hasPermission("darksea.admin")) {
            return false;
        }
        return guarded(block.getLocation());
    }

    /** Whether this location is protected terrain — sanctuary or an island. */
    private boolean guarded(Location loc) {
        if (!inDarkSea(loc)) {
            return false;
        }
        if (withinSanctuary(loc)) {
            return true;
        }
        if (!plugin.settings().combat().protectIslands()) {
            return false;
        }
        int buffer = plugin.settings().combat().islandProtectBuffer();
        return plugin.registry().islandAtColumn(loc.getBlockX(), loc.getBlockZ(), buffer) != null;
    }

    private boolean withinSanctuary(Location loc) {
        double radius = plugin.settings().combat().pvpSafeRadius();
        if (radius <= 0) {
            return false;
        }
        double dx = loc.getX() - plugin.settings().centerX();
        double dz = loc.getZ() - plugin.settings().centerZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean inDarkSea(Location loc) {
        World world = loc.getWorld();
        return world != null && world.getName().equals(plugin.settings().worldName());
    }
}
