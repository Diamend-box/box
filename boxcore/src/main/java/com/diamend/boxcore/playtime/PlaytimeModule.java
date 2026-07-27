package com.diamend.boxcore.playtime;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.BoxModule;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Pays out skill points for time played.
 *
 * <p>Hours come from the server's own {@code PLAY_ONE_MINUTE} statistic rather
 * than a counter of our own, so time a player banked before BoxCore was
 * installed counts, and the total survives anything happening to our data.
 * The number of points already granted is stored per player, so changing
 * {@code hours-per-point} tops players up instead of paying them twice.
 */
public class PlaytimeModule implements BoxModule {

    /** 20 ticks per second × 3600 seconds. */
    private static final int TICKS_PER_HOUR = 72_000;

    private final BoxCorePlugin plugin;
    private BukkitTask task;

    public PlaytimeModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "playtime";
    }

    @Override
    public String displayName() {
        return "Playtime points";
    }

    @Override
    public void enable() {
        // Every minute is plenty: the payout only ever changes on the hour.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                sync(player);
            }
        }, 200L, 1200L);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Tops a player up to the points their playtime has earned. */
    public void sync(Player player) {
        int hoursPerPoint = plugin.getConfig().getInt("playtime.hours-per-point", 5);
        if (hoursPerPoint <= 0) {
            return;
        }
        int hours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
        int earned = hours / hoursPerPoint;

        int cap = plugin.getConfig().getInt("playtime.max-points", 0);
        if (cap > 0) {
            earned = Math.min(earned, cap);
        }

        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int already = profile.getPlaytimePointsGranted();
        if (earned <= already) {
            return;
        }
        int owed = earned - already;
        profile.setPlaytimePointsGranted(earned);
        plugin.grantPoints(player, owed, "playtime");
    }

    @Override
    public List<String> statusLines() {
        int hoursPerPoint = plugin.getConfig().getInt("playtime.hours-per-point", 5);
        return List.of("<gray>1 point per <white>" + hoursPerPoint + "<gray> hour(s) played");
    }
}
