package com.diamend.darksea.world;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings.ResetSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives the timed sea reset. Ticks once a second: while auto-reset is on it
 * counts down to the next cycle, broadcasts a warning at each configured mark,
 * and when the timer runs out washes everyone in the sea home to the sanctuary
 * (so an island healing over them can't bury anyone) and runs the reset. It
 * reads live settings every tick, so {@code /ds reload} turns it on or off and
 * re-times the cycle.
 */
public final class SeaResetScheduler extends BukkitRunnable {

    /** Tick period: also the most a reset can fire late (one second). */
    public static final long TICK_PERIOD = 20L;

    private final DarkSeaPlugin plugin;
    private final Set<Integer> announced = new HashSet<>();
    private boolean armed;
    private long nextResetMillis;

    public SeaResetScheduler(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The warning thresholds (in minutes) to announce this tick: those the
     * remaining time has reached but that haven't been announced yet. Pure so
     * the countdown can be tested without a clock or a server.
     */
    static List<Integer> dueWarnings(long remainingMillis, List<Integer> warnMinutes,
                                     Set<Integer> announced) {
        long remainingMinutes = (long) Math.ceil(remainingMillis / 60000.0);
        List<Integer> due = new ArrayList<>();
        for (int threshold : warnMinutes) {
            if (!announced.contains(threshold) && remainingMinutes <= threshold) {
                due.add(threshold);
            }
        }
        return due;
    }

    @Override
    public void run() {
        ResetSettings settings = plugin.settings().reset();
        if (!settings.autoEnabled()) {
            armed = false;   // re-arms with a fresh interval if it's turned back on
            return;
        }
        long now = System.currentTimeMillis();
        if (!armed) {
            arm(now, settings);
            return;
        }
        long remaining = nextResetMillis - now;
        if (remaining <= 0) {
            fire(settings);
            arm(now, settings);
            return;
        }
        for (int threshold : dueWarnings(remaining, settings.warnMinutes(), announced)) {
            announced.add(threshold);
            broadcast("reset-cycle-warning", "minutes", String.valueOf(threshold));
        }
    }

    private void arm(long now, ResetSettings settings) {
        nextResetMillis = now + settings.intervalHours() * 3_600_000L;
        announced.clear();
        armed = true;
    }

    private void fire(ResetSettings settings) {
        // Nobody online → nothing to farm; skip the churn and re-time next cycle.
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        World sea = plugin.worldService().world();
        if (sea != null && !settings.fullMode()) {
            // A soft reset re-pastes islands in place; wash anyone in the sea
            // home first so nobody is buried when their island heals over them.
            Location home = sea.getSpawnLocation();
            for (Player player : List.copyOf(sea.getPlayers())) {
                player.leaveVehicle();
                player.teleport(home);
                plugin.messages().send(player, "reset-cycle-now");
            }
        }
        // A full reset evacuates to a fallback world itself, so no wash-home here.
        if (settings.fullMode()) {
            plugin.worldService().resetFull(Bukkit.getConsoleSender());
        } else {
            plugin.worldService().resetSoft(Bukkit.getConsoleSender());
        }
        plugin.getLogger().info("Timed sea reset fired ("
                + (settings.fullMode() ? "full" : "soft") + ").");
    }

    private void broadcast(String key, String... placeholders) {
        World sea = plugin.worldService().world();
        if (sea == null) {
            return;
        }
        for (Player player : sea.getPlayers()) {
            plugin.messages().send(player, key, placeholders);
        }
    }
}
