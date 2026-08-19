package com.diamend.anticheat.protect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.diamend.anticheat.config.AntiCheatConfig;

/**
 * Anti-ESP: server-side entity culling.
 *
 * <p>Client-side ESP can't be detected, so the only real defence is to stop
 * feeding the client the data. This task hides players who are out of a viewer's
 * line of sight — behind a wall, underground, around a corner — so a cheater's
 * ESP has nothing to draw. When the target steps back into view it is shown
 * again. Hiding/showing goes through Bukkit's {@code hideEntity}/{@code
 * showEntity} API, which sends the {@code DestroyEntities} and re-{@code
 * SpawnEntity} packets for us (with the correct spawn data), so we get the
 * packet-level effect the design calls for without hand-serialising entity spawns.
 *
 * <p>Line-of-sight occlusion inherently needs to read the world along the ray
 * between two players, which is only safe on the main thread — so, unlike the
 * detection checks (fully off-thread), this sweep runs on a throttled main-thread
 * cadence (default every few ticks) over a bounded candidate set. It is disabled
 * by default; enable it only if ESP is a real problem, and mind the CPU cost on
 * a crowded server.
 *
 * <p>Players very close to each other are never hidden (they'd pop in and out as
 * you turn), and players with the alerts/bypass permission are never hidden from
 * anyone, so staff can always see everyone.
 */
public final class EntityCullingTask implements Runnable {

    private final Plugin plugin;
    private final Supplier<AntiCheatConfig> config;

    /** viewer UUID -> set of target UUIDs currently hidden from them. */
    private final ConcurrentHashMap<UUID, Set<UUID>> hidden = new ConcurrentHashMap<>();

    public EntityCullingTask(Plugin plugin, Supplier<AntiCheatConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void run() {
        AntiCheatConfig cfg = config.get();
        if (cfg == null || !cfg.cullingEnabled()) {
            // If it was just turned off, reveal everyone we had hidden.
            if (!hidden.isEmpty()) {
                revealAll();
            }
            return;
        }
        double maxDist = cfg.cullingMaxDistance();
        double minDist = cfg.cullingMinDistance();
        double maxDistSq = maxDist * maxDist;
        double minDistSq = minDist * minDist;

        var players = plugin.getServer().getOnlinePlayers();
        for (Player viewer : players) {
            Set<UUID> viewerHidden = hidden.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
            for (Player target : players) {
                if (viewer == target) {
                    continue;
                }
                if (isExemptTarget(target)) {
                    ensureVisible(viewer, target, viewerHidden);
                    continue;
                }
                if (viewer.getWorld() != target.getWorld()) {
                    // Different worlds: Bukkit doesn't send them anyway; forget any state.
                    viewerHidden.remove(target.getUniqueId());
                    continue;
                }
                Location v = viewer.getLocation();
                Location t = target.getLocation();
                double distSq = v.distanceSquared(t);
                if (distSq <= minDistSq || distSq > maxDistSq) {
                    // Too close to risk pop-in, or too far to matter: always visible.
                    ensureVisible(viewer, target, viewerHidden);
                    continue;
                }
                boolean visible = viewer.hasLineOfSight(target);
                if (visible) {
                    ensureVisible(viewer, target, viewerHidden);
                } else {
                    ensureHidden(viewer, target, viewerHidden);
                }
            }
        }
    }

    private boolean isExemptTarget(Player target) {
        return target.hasPermission("anticheat.alerts")
                || target.hasPermission("anticheat.bypass");
    }

    private void ensureHidden(Player viewer, Player target, Set<UUID> viewerHidden) {
        if (viewerHidden.add(target.getUniqueId())) {
            viewer.hideEntity(plugin, target);
        }
    }

    private void ensureVisible(Player viewer, Player target, Set<UUID> viewerHidden) {
        if (viewerHidden.remove(target.getUniqueId())) {
            viewer.showEntity(plugin, target);
        }
    }

    /** Reveal everything we've hidden — used on disable/shutdown. */
    public void revealAll() {
        for (var entry : hidden.entrySet()) {
            Player viewer = plugin.getServer().getPlayer(entry.getKey());
            if (viewer == null) {
                continue;
            }
            for (UUID targetId : entry.getValue()) {
                Player target = plugin.getServer().getPlayer(targetId);
                if (target != null) {
                    viewer.showEntity(plugin, target);
                }
            }
        }
        hidden.clear();
    }

    public void forget(UUID viewer) {
        hidden.remove(viewer);
    }
}
