package com.diamend.spyglass.watch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.diamend.spyglass.config.SpyglassConfig;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Keeps the list of who is following whom, and puts the lines where they go.
 *
 * <p>Events arrive from several threads (chat is asynchronous), so the watch
 * list is concurrent and every line is handed to the main thread before it is
 * sent.
 */
public final class WatchManager {

    private final Plugin plugin;
    private final Server server;
    private final java.util.function.Supplier<SpyglassConfig> config;

    private final List<Watch> watches = new CopyOnWriteArrayList<>();

    /** Last time each watched player's position was reported, for sampling. */
    private final Map<UUID, Long> lastPositionReport = new ConcurrentHashMap<>();

    public WatchManager(Plugin plugin, java.util.function.Supplier<SpyglassConfig> config) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.config = config;
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    /**
     * Starts a watch, replacing any the same watcher already had on that player.
     *
     * @return the watch that is now in force
     */
    public Watch add(CommandSender watcher, UUID target, String targetName,
                     Set<WatchCategory> categories) {
        UUID watcherId = idOf(watcher);
        remove(watcher, target, targetName);
        Watch watch = new Watch(watcherId, watcher.getName(), target, targetName, categories);
        watches.add(watch);
        return watch;
    }

    /** Stops one watch. Returns true when there was one to stop. */
    public boolean remove(CommandSender watcher, UUID target, String targetName) {
        UUID watcherId = idOf(watcher);
        return watches.removeIf(watch ->
                watch.watcher().equals(watcherId) && watch.isFor(target, targetName));
    }

    /** Stops every watch this sender has. Returns how many. */
    public int removeAll(CommandSender watcher) {
        UUID watcherId = idOf(watcher);
        int before = watches.size();
        watches.removeIf(watch -> watch.watcher().equals(watcherId));
        return before - watches.size();
    }

    /** Forgets a watcher who has left, so their lines stop being rendered. */
    public int forgetWatcher(UUID watcherId) {
        int before = watches.size();
        watches.removeIf(watch -> watch.watcher().equals(watcherId));
        return before - watches.size();
    }

    public List<Watch> watches() {
        return List.copyOf(watches);
    }

    public boolean isEmpty() {
        return watches.isEmpty();
    }

    public int size() {
        return watches.size();
    }

    /** The watches one sender has set. */
    public List<Watch> watchesBy(CommandSender watcher) {
        UUID watcherId = idOf(watcher);
        List<Watch> out = new ArrayList<>();
        for (Watch watch : watches) {
            if (watch.watcher().equals(watcherId)) {
                out.add(watch);
            }
        }
        return out;
    }

    public void clear() {
        watches.clear();
        lastPositionReport.clear();
    }

    /** True when anybody is following this player — the listener's fast path. */
    public boolean isWatched(Player player) {
        if (watches.isEmpty()) {
            return false;
        }
        for (Watch watch : watches) {
            if (watch.matches(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /**
     * Reports one thing a player did to everyone following them.
     *
     * @param what   the short label, e.g. {@code chat} or {@code block break}
     * @param detail the rest of the line
     */
    public void emit(Player player, WatchCategory category, String what, String detail) {
        if (watches.isEmpty() || player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int limit = config.get().maxLinesPerSecond();
        Map<Watch, Integer> deliver = new HashMap<>();
        for (Watch watch : watches) {
            if (!watch.wants(category) || !watch.matches(player)) {
                continue;
            }
            if (!watch.allow(limit, now)) {
                continue;
            }
            deliver.put(watch, watch.takeSuppressed());
        }
        if (deliver.isEmpty()) {
            return;
        }
        String name = Safe.text(player::getName);
        Component line = Component.text("[spy] ", NamedTextColor.DARK_AQUA)
                .append(Component.text(Fmt.clock(now) + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(name + " ", NamedTextColor.YELLOW))
                .append(Component.text(what + " ", NamedTextColor.GRAY))
                .append(Component.text(Fmt.clip(detail == null ? "" : detail, 300),
                        NamedTextColor.WHITE));
        run(() -> {
            for (Map.Entry<Watch, Integer> entry : deliver.entrySet()) {
                CommandSender sender = resolve(entry.getKey());
                if (sender == null) {
                    continue;
                }
                if (entry.getValue() > 0) {
                    sender.sendMessage(Component.text(
                            "[spy] ... " + entry.getValue() + " line(s) dropped to keep up",
                            NamedTextColor.DARK_GRAY));
                }
                sender.sendMessage(line);
            }
        });
    }

    /**
     * Reports a position, no more often than the configured sample interval.
     * Movement fires every tick; nobody wants that in a console.
     */
    public void emitPosition(Player player, String detail) {
        long now = System.currentTimeMillis();
        long every = config.get().movementSampleSeconds() * 1000L;
        UUID id = player.getUniqueId();
        Long last = lastPositionReport.get(id);
        if (last != null && now - last < every) {
            return;
        }
        lastPositionReport.put(id, now);
        emit(player, WatchCategory.MOVEMENT, "moved", detail);
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static UUID idOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : Watch.CONSOLE;
    }

    /** The sender a watch belongs to, or null when they have gone away. */
    private CommandSender resolve(Watch watch) {
        if (watch.isConsole()) {
            return Safe.call(server::getConsoleSender, null);
        }
        return Safe.call(() -> server.getPlayer(watch.watcher()), null);
    }

    /** Runs on the main thread, hopping there first when we are not on it. */
    private void run(Runnable action) {
        boolean primary = Safe.flag(server::isPrimaryThread, true);
        if (primary) {
            Safe.run(action);
            return;
        }
        Safe.run(() -> server.getScheduler().runTask(plugin, () -> Safe.run(action)));
    }
}
