package com.diamend.darksea.world;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dying out here puts you back out here.
 *
 * <p>Vanilla sends a player without a bed to the spawn of the server's main
 * world, which for a sea running alongside an existing server is somebody
 * else's lobby. The extraction loop does not survive that: losing a run is
 * meant to cost you the run, not eject you from the game mode and make getting
 * back a chore.
 *
 * <p>The death world is recorded rather than read off the player at respawn
 * time, because by then the respawn location has already been decided and the
 * player is no longer a reliable witness to where they died.
 */
public final class RespawnService implements Listener {

    private final DarkSeaPlugin plugin;

    /** Players who died in one of the plugin's worlds and have yet to respawn. */
    private final Set<UUID> diedHere = new HashSet<>();

    public RespawnService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.managedWorld(player.getWorld()) != null) {
            diedHere.add(player.getUniqueId());
        } else {
            diedHere.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!diedHere.remove(player.getUniqueId())) {
            return;
        }
        Location chosen = event.getRespawnLocation();
        if (chosen != null && plugin.managedWorld(chosen.getWorld()) != null) {
            // A bed or anchor they set out here. Their choice beats ours.
            return;
        }
        World sea = plugin.worldService().world();
        if (sea == null) {
            return;   // no sea loaded to send them to; vanilla's answer stands
        }
        // The sea, even for a death in the caves — the caves have no spawn of
        // their own, and the way in is a boat ride from the home island.
        event.setRespawnLocation(sea.getSpawnLocation());
    }

    /**
     * A player who dies and disconnects before clicking respawn is put back by
     * the server's own spawn logic on the way in, with no respawn event to
     * intercept. Dropping the flag here stops it going stale and hijacking a
     * later, unrelated death somewhere else on the server.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        diedHere.remove(event.getPlayer().getUniqueId());
    }
}
