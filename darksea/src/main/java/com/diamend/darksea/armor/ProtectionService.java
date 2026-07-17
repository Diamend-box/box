package com.diamend.darksea.armor;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player protection tier, cached so the exposure task never re-reads
 * item NBT on its own. The cache is invalidated by Paper's armor-change
 * event and recomputed lazily on the next lookup.
 */
public final class ProtectionService implements Listener {

    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public int tierOf(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(),
                id -> SeaArmor.effectiveTier(player.getInventory().getArmorContents()));
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
