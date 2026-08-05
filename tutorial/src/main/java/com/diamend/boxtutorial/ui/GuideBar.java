package com.diamend.boxtutorial.ui;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.guide.TutorialService;
import com.diamend.boxtutorial.guide.TutorialStep;
import com.diamend.boxtutorial.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The on-screen nudge: a boss bar naming the current step, and the slow-poll
 * triggers that don't deserve an event listener.
 *
 * <p>A new player who closes the chat has lost the tutorial. The bar is what
 * keeps "you are on step 3 of 8, and it wants you to mine 16 blocks" on screen
 * without another line of chat, and it disappears the moment they finish or ask
 * it to.
 *
 * <p>Two triggers are checked here rather than in a listener: standing near a
 * place, and having played long enough. Both are questions about a state rather
 * than an event, and asking them once a second for the handful of players still
 * in the tutorial costs less than a {@code PlayerMoveEvent} handler that runs
 * for everyone, forever.
 */
public class GuideBar {

    /** Below this the bar rewrites faster than a client redraws it. */
    private static final long MIN_INTERVAL = 5L;

    /** Ignore a gap this large between passes — a lagging or frozen server. */
    private static final long MAX_DELTA = 60_000L;

    private final BoxTutorialPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> lastPass = new HashMap<>();

    private boolean enabled = true;
    private boolean actionbar;
    private BossBar.Color color = BossBar.Color.YELLOW;
    private long intervalTicks = 20L;

    private BukkitTask task;

    public GuideBar(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Reads config and (re)schedules the pass. Safe to call repeatedly. */
    public void start() {
        stop();
        enabled = plugin.getConfig().getBoolean("bossbar", true);
        actionbar = plugin.getConfig().getBoolean("actionbar", false);
        color = colorFromConfig(plugin.getConfig().getString("bossbar-color", "YELLOW"));
        intervalTicks = Math.max(MIN_INTERVAL, plugin.getConfig().getLong("nudge-interval-ticks", 20L));
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, BossBar> entry : new HashMap<>(bars).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
        lastPass.clear();
    }

    private BossBar.Color colorFromConfig(String name) {
        try {
            return BossBar.Color.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown bossbar-color '" + name + "'; using YELLOW.");
            return BossBar.Color.YELLOW;
        }
    }

    // ------------------------------------------------------------------
    // The pass
    // ------------------------------------------------------------------

    /** One pass. Public so a test can drive it without waiting on the scheduler. */
    public void tick() {
        long now = System.currentTimeMillis();
        TutorialService service = plugin.service();
        Set<UUID> online = new HashSet<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            online.add(uuid);
            Long previous = lastPass.put(uuid, now);

            Progress progress = plugin.store().get(uuid);
            if (!service.isActive(progress)) {
                hide(player);
                continue;
            }
            if (previous != null) {
                long delta = now - previous;
                if (delta > 0 && delta < MAX_DELTA) {
                    progress.addPlayedMillis(delta);
                    plugin.store().touch();
                }
            }
            checkPlaytime(player, progress);
            checkPlace(player, progress);
            refresh(player);
        }

        lastPass.keySet().retainAll(online);
        bars.keySet().retainAll(online);
    }

    /** Completes a "play for N minutes" step once the clock says so. */
    private void checkPlaytime(Player player, Progress progress) {
        long minutes = progress.playedMillis() / 60_000L;
        for (TutorialStep step : plugin.service().armed(progress)) {
            if (step.trigger() != StepTrigger.PLAYTIME_MINUTES) {
                continue;
            }
            if (minutes >= step.amount()) {
                plugin.service().completeStep(player, step, true);
            } else {
                progress.setCount(step.id(), (int) minutes);
            }
        }
    }

    /** Completes a "go here" step when the player is standing in its radius. */
    private void checkPlace(Player player, Progress progress) {
        List<TutorialStep> armed = plugin.service().armed(progress);
        if (armed.isEmpty()) {
            return;
        }
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (TutorialStep step : armed) {
            TutorialStep.Place place = step.place();
            if (place == null || !place.world().equalsIgnoreCase(world.getName())) {
                continue;
            }
            double dx = at.getX() - place.x();
            double dy = at.getY() - place.y();
            double dz = at.getZ() - place.z();
            if (dx * dx + dy * dy + dz * dz <= place.radius() * place.radius()) {
                plugin.service().completeStep(player, step, true);
            }
        }
    }

    // ------------------------------------------------------------------
    // The bar
    // ------------------------------------------------------------------

    /** Brings this player's bar up to date, showing or hiding it as needed. */
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Progress progress = plugin.store().get(player.getUniqueId());
        TutorialStep step = plugin.service().isActive(progress)
                ? plugin.service().current(progress) : null;
        if (!enabled || step == null) {
            hide(player);
            return;
        }

        Component name = title(progress, step);
        float fraction = fraction(progress);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, fraction, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(fraction);
            bar.color(color);
        }
        if (actionbar) {
            player.sendActionBar(Text.parse(plugin.messages().raw("actionbar",
                    "step", step.name(), "goal", step.objective())));
        }
    }

    /** Takes the bar off this player's screen. */
    public void hide(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void forget(UUID uuid) {
        bars.remove(uuid);
        lastPass.remove(uuid);
    }

    /** True while this player is being shown a bar — used by the tests. */
    public boolean isShowing(UUID uuid) {
        return bars.containsKey(uuid);
    }

    private Component title(Progress progress, TutorialStep step) {
        String counter = step.counts()
                ? " <dark_gray>(<white>" + Math.min(progress.count(step.id()), step.amount())
                        + "<gray>/<white>" + step.amount() + "<dark_gray>)"
                : "";
        return Text.parse(plugin.messages().raw("bossbar",
                "step", step.name(),
                "number", plugin.tutorial().number(step),
                "total", plugin.tutorial().stepCount(),
                "goal", step.objective(),
                "progress", counter));
    }

    private float fraction(Progress progress) {
        int total = plugin.tutorial().stepCount();
        if (total <= 0) {
            return 1.0f;
        }
        float value = plugin.service().completedCount(progress) / (float) total;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
