package com.diamend.boxtutorial.listener;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.data.Progress;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Who gets the tutorial, and when.
 *
 * <p>The interesting case is the second join. A player who logged off halfway
 * through step three comes back to the same step three, the same boss bar and a
 * reminder of what it wanted — a tutorial that only exists during the first
 * session is a tutorial most people never finish.
 */
public class ConnectionListener implements Listener {

    private final BoxTutorialPlugin plugin;

    public ConnectionListener(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Asked before get(), which would create the record we're testing for.
        boolean known = plugin.store().known(uuid);
        Progress progress = plugin.store().get(uuid);
        progress.setName(player.getName());
        plugin.store().touch();

        long delay = Math.max(0L, plugin.getConfig().getLong("join-delay-seconds", 4L)) * 20L;

        if (!progress.started() && !progress.finished() && !progress.stopped()) {
            if (shouldAutoStart(player, known)) {
                later(uuid, delay, online -> plugin.service().start(online, false));
            }
            return;
        }
        if (!plugin.service().isActive(progress)) {
            return;
        }
        // Mid-tutorial: put the bar back, and say where they left off.
        later(uuid, delay, online -> {
            plugin.guide().refresh(online);
            if (plugin.getConfig().getBoolean("remind-on-join", true)) {
                plugin.messages().send(online, "resumed");
                plugin.service().announceCurrent(online);
            }
        });
    }

    /**
     * Only players the server has never seen, unless the owner opts in to
     * catching up everybody. Handing an existing playerbase a tutorial they
     * didn't ask for the day the plugin is installed is a support ticket, not a
     * feature — so {@code auto-start-existing} is off by default.
     */
    private boolean shouldAutoStart(Player player, boolean known) {
        if (!plugin.getConfig().getBoolean("auto-start", true)) {
            return false;
        }
        if (plugin.getConfig().getBoolean("auto-start-existing", false)) {
            return true;
        }
        return !known && !player.hasPlayedBefore();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.guide().forget(event.getPlayer().getUniqueId());
    }

    /** Runs something for this player in a moment, if they're still online. */
    private void later(UUID uuid, long ticks, java.util.function.Consumer<Player> action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                action.accept(online);
            }
        }, Math.max(1L, ticks));
    }
}
