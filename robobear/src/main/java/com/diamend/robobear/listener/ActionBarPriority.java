package com.diamend.robobear.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Rewrites the run clock at the very end of the server tick.
 *
 * <p>The action bar is a single shared line with no notion of ownership or
 * priority: the last plugin to write it in a given tick is the one the player
 * sees. Writing from the end-of-tick hook puts RoboBear after everything that
 * writes from a normal scheduler task or event handler, which is as close to
 * "taking priority" as the game allows.
 *
 * <p>Kept in its own class so that a server whose API lacks this Paper event
 * fails to load exactly one small listener — caught at registration, replaced
 * with a plain timer — rather than failing to enable the plugin.
 */
public class ActionBarPriority implements Listener {

    private final RoboBearPlugin plugin;
    private final int everyTicks;
    private int sinceLast;

    public ActionBarPriority(RoboBearPlugin plugin, int everyTicks) {
        this.plugin = plugin;
        this.everyTicks = Math.max(1, everyTicks);
    }

    /**
     * MONITOR so that any other end-of-tick handler has already run. This fires
     * every tick on a busy server, so the counter check comes before anything
     * else and the work itself is skipped entirely when nobody is running.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (++sinceLast < everyTicks) {
            return;
        }
        sinceLast = 0;
        plugin.service().refreshActionBars();
    }
}
