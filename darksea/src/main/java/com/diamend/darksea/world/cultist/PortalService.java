package com.diamend.darksea.world.cultist;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.island.shape.CultistLandfall;
import com.diamend.darksea.util.Pos;
import com.diamend.darksea.world.ManagedWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way down and the way back. Standing on the black pad at the top of the
 * cultist landfall drops you into the caves' arrival chamber; standing on the
 * matching pad there brings you back out.
 *
 * <p>Deliberately not a vanilla nether portal. Those come with their own
 * linking rules — searching for a counterpart, building one if it can't find
 * one, snapping to a coordinate ratio — all of which would fight a destination
 * that has to be exactly one fixed room. A plugin portal is a block you stand
 * on and a teleport, and it goes precisely where it is told.
 *
 * <p>Triggered by movement rather than a right-click so it reads as walking
 * through rather than operating a machine. A short per-player cooldown stops
 * the return pad bouncing anyone straight back the instant they arrive.
 */
public final class PortalService implements Listener {

    /** The block a player stands on to travel, in both worlds. */
    public static final Material PAD = Material.BLACK_CONCRETE;

    /** How long after a trip before this player can take another. */
    private static final long COOLDOWN_MILLIS = 3000L;

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Long> lastTravel = new HashMap<>();

    public PortalService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!movedBlock(event)) {
            return;   // fires on every look-around otherwise
        }
        Player player = event.getPlayer();
        ManagedWorld world = plugin.managedWorld(player.getWorld());
        if (world == null) {
            return;
        }
        Location below = player.getLocation().subtract(0, 1, 0);
        if (below.getBlock().getType() != PAD) {
            return;
        }
        if (onCooldown(player)) {
            return;
        }
        if (world == ManagedWorld.DARK_SEA) {
            descend(player);
        } else if (world == ManagedWorld.CULTIST) {
            ascend(player);
        }
    }

    private static boolean movedBlock(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return to == null
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private boolean onCooldown(Player player) {
        Long last = lastTravel.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < COOLDOWN_MILLIS;
    }

    private void mark(Player player) {
        lastTravel.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    // Down
    // ------------------------------------------------------------------

    private void descend(Player player) {
        // Only the landfall's pad works — a black concrete block anywhere else
        // in the sea is just a block.
        IslandInstance landfall = landfall();
        if (landfall == null) {
            return;
        }
        Pos origin = landfall.origin();
        int dx = player.getLocation().getBlockX() - origin.x();
        int dz = player.getLocation().getBlockZ() - origin.z();
        if (Math.abs(dx) > CultistLandfall.PORTAL_HALF
                || Math.abs(dz) > CultistLandfall.PORTAL_HALF) {
            return;
        }
        World caves = plugin.worldService().getOrCreateCaves();
        if (caves == null) {
            plugin.messages().send(player, "portal-closed");
            return;
        }
        mark(player);
        player.teleport(new Location(caves, 0.5, plugin.settings().cultist().floorY() + 1, 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch()));
        arrive(player, "portal-descended");
    }

    // ------------------------------------------------------------------
    // Up
    // ------------------------------------------------------------------

    private void ascend(Player player) {
        IslandInstance landfall = landfall();
        if (landfall == null) {
            plugin.messages().send(player, "portal-closed");
            return;
        }
        World sea = plugin.worldService().world();
        if (sea == null) {
            plugin.messages().send(player, "portal-closed");
            return;
        }
        Pos origin = landfall.origin();
        mark(player);
        player.teleport(new Location(sea, origin.x() + 0.5,
                origin.y() + CultistLandfall.PORTAL_Y, origin.z() + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch()));
        arrive(player, "portal-ascended");
    }

    private void arrive(Player player, String messageKey) {
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.7f);
        plugin.messages().send(player, messageKey);
    }

    /** The one placed landfall, or null if it has not been raised yet. */
    private IslandInstance landfall() {
        for (IslandInstance island : plugin.registry().all()) {
            if (CultistLandfall.ID.equals(island.template())) {
                return island;
            }
        }
        return null;
    }

    /**
     * Lays the return pad in the caves' arrival chamber. Called after the
     * world is created — the chamber is carved open by the generator but the
     * pad itself is plugin-placed, so it can be re-laid if anything ever
     * clears it.
     */
    public void installReturnPad(World caves) {
        if (caves == null) {
            return;
        }
        int floor = plugin.settings().cultist().floorY();
        for (int x = -CultistLandfall.PORTAL_HALF; x <= CultistLandfall.PORTAL_HALF; x++) {
            for (int z = -CultistLandfall.PORTAL_HALF; z <= CultistLandfall.PORTAL_HALF; z++) {
                caves.getBlockAt(x, floor, z).setType(PAD, false);
            }
        }
        // A light so the way back is visible from across the chamber.
        caves.getBlockAt(0, floor + 4, 0).setType(Material.SOUL_LANTERN, false);
    }
}
