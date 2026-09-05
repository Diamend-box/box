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
        // Read before write. computeIfAbsent needs its mapping function built
        // whether or not it ends up being called, and this is asked once per
        // exposure tick for every player in the sea — so on the hit path, the
        // only path that is ever hot, the lambda was the entire cost.
        Integer cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        int tier = SeaArmor.effectiveTier(player.getInventory().getArmorContents());
        cache.put(player.getUniqueId(), tier);
        return tier;
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
