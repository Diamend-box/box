package com.diamend.darksea.zone;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The heart of the gameplay loop: once per configured interval, every player
 * in the Dark Sea is resolved to a zone, their exposure is computed
 * (required tier − armor − boat shield), and any remaining danger is applied
 * as the effects of the ring matching that exposure. Effects are applied
 * with a short duration and simply lapse after leaving danger — no removal
 * bookkeeping.
 */
public final class ExposureTask extends BukkitRunnable implements Listener {

    private final DarkSeaPlugin plugin;
    private final Map<UUID, String> lastZone = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTime = new ConcurrentHashMap<>();

    public ExposureTask(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        DarkSeaSettings settings = plugin.settings();
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) {
            return;
        }
        ZoneManager zones = plugin.zoneManager();
        long now = System.currentTimeMillis();
        long graceMillis = settings.exposure().graceOnLoginSeconds() * 1000L;

        for (Player player : world.getPlayers()) {
            double dx = player.getLocation().getX() - settings.centerX();
            double dz = player.getLocation().getZ() - settings.centerZ();
            Zone zone = zones.zoneAt(dx * dx + dz * dz);

            boolean exempt = player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR
                    || player.hasPermission("darksea.bypass");

            int protection = plugin.protection().tierOf(player);
            int shield = plugin.boat().shieldFor(player);
            int exposure = ZoneManager.exposure(zone.requiredTier(), protection, shield);

            // Zone-crossing feedback (also for exempt players — it's navigation info).
            String previous = lastZone.put(player.getUniqueId(), zone.id());
            if (previous != null && !previous.equals(zone.id())) {
                boolean safeHere = exempt || exposure <= 0;
                plugin.messages().actionBar(player,
                        safeHere ? "zone-crossed-protected" : "zone-crossed-exposed",
                        "zone", zone.displayName());
                player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 0.6f, 0.7f);
            }

            if (exempt || exposure <= 0) {
                continue;
            }
            Long joined = joinTime.get(player.getUniqueId());
            if (joined != null && now - joined < graceMillis) {
                continue;
            }
            Zone danger = zones.effectiveDangerZone(exposure);
            if (danger == null) {
                continue;
            }
            for (Zone.ZoneEffect effect : danger.effects()) {
                player.addPotionEffect(new PotionEffect(effect.type(),
                        settings.exposure().effectDurationTicks(), effect.amplifier(), true, true, true));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joinTime.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        lastZone.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        // Re-entering the sea should re-announce the current zone.
        lastZone.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        joinTime.remove(event.getPlayer().getUniqueId());
        lastZone.remove(event.getPlayer().getUniqueId());
    }
}
