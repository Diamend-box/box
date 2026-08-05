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
 * through step three comes back to a freshly built arena on step three — a
 * tutorial that only exists during the first session is one most people never
 * finish.
 *
 * <p>Logging out inside the arena is also the one way to end up in a world with
 * no instance behind it (the instance goes back in the pool the moment they
 * disconnect), so a join from in there is either put back into a new arena or
 * sent to spawn. Nobody is left standing on a platform that belongs to somebody
 * else.
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

        // Logged out inside the arena: put them back in one, or take them home.
        if (plugin.instances().isArenaWorld(player.getWorld())) {
            later(uuid, 1L, online -> {
                if (plugin.service().isActive(plugin.store().get(uuid))) {
                    plugin.messages().send(online, "resumed");
                    if (!plugin.service().enter(online)) {
                        plugin.service().sendHome(online);
                    }
                } else {
                    plugin.service().sendHome(online);
                }
            });
            return;
        }

        if (!progress.started() && !progress.finished() && !progress.stopped()) {
            if (shouldAutoStart(player, known)) {
                later(uuid, delay, online -> {
                    if (invites()) {
                        plugin.messages().send(online, "invited");
                        plugin.service().sendInvite(online);
                    } else {
                        plugin.service().start(online, false);
                    }
                });
            }
            return;
        }
        if (!plugin.service().isActive(progress)) {
            return;
        }
        // Mid-tutorial but out at spawn: remind them it's waiting, and make the
        // way back one click rather than one remembered command.
        later(uuid, delay, online -> {
            if (plugin.getConfig().getBoolean("remind-on-join", true)) {
                plugin.messages().send(online, "resumed");
                plugin.service().sendInvite(online);
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

    /**
     * Offer it, or teleport them into it?
     *
     * <p>Inviting is the default. Pulling somebody into a private dimension
     * four seconds after their first ever join, before they have seen spawn or
     * read a single message, is startling — and the players who most need the
     * tutorial are the ones most likely to think the server broke.
     */
    private boolean invites() {
        return !"enter".equalsIgnoreCase(plugin.getConfig().getString("auto-start-mode", "invite"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // The arena goes back in the pool immediately; they get a new one when
        // they come back. Nothing in it was theirs to keep except what they're
        // carrying, which they are.
        plugin.instances().release(event.getPlayer().getUniqueId());
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
