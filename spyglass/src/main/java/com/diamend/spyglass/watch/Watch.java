package com.diamend.spyglass.watch;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * One person following one player.
 *
 * <p>The target is remembered by both UUID and name so a watch can be set on
 * someone who is offline — it starts reporting the moment they log in — and
 * still be exact for someone who is already here.
 */
public final class Watch {

    /** Stands in for the console, which has no UUID. */
    public static final UUID CONSOLE = new UUID(0L, 0L);

    private final UUID watcher;
    private final String watcherName;
    private final UUID target;
    private final String targetName;
    private final Set<WatchCategory> categories;
    private final long since = System.currentTimeMillis();

    // Rate limiting, touched from event threads.
    private long windowStart;
    private int inWindow;
    private int suppressed;

    public Watch(UUID watcher, String watcherName, UUID target, String targetName,
                 Set<WatchCategory> categories) {
        this.watcher = watcher;
        this.watcherName = watcherName;
        this.target = target;
        this.targetName = targetName == null ? "" : targetName;
        this.categories = EnumSet.copyOf(categories.isEmpty()
                ? EnumSet.noneOf(WatchCategory.class) : categories);
    }

    public UUID watcher() {
        return watcher;
    }

    public String watcherName() {
        return watcherName;
    }

    public boolean isConsole() {
        return CONSOLE.equals(watcher);
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    public Set<WatchCategory> categories() {
        return Set.copyOf(categories);
    }

    public long since() {
        return since;
    }

    public boolean wants(WatchCategory category) {
        return categories.contains(category);
    }

    /** True when this watch is about that player, by UUID or by name. */
    public boolean matches(Player player) {
        if (player == null) {
            return false;
        }
        if (target != null && target.equals(player.getUniqueId())) {
            return true;
        }
        return !targetName.isEmpty() && targetName.equalsIgnoreCase(player.getName());
    }

    public boolean isFor(UUID uuid, String name) {
        if (uuid != null && uuid.equals(target)) {
            return true;
        }
        return name != null && !targetName.isEmpty() && targetName.equalsIgnoreCase(name);
    }

    /**
     * Spends one line of this second's budget.
     *
     * @return true when the line may be sent
     */
    public synchronized boolean allow(int maxPerSecond, long now) {
        if (maxPerSecond <= 0) {
            return true;
        }
        if (now - windowStart >= 1000L) {
            windowStart = now;
            inWindow = 0;
            // suppressed is left alone: the next line that gets through reports it.
        }
        if (inWindow >= maxPerSecond) {
            suppressed++;
            return false;
        }
        inWindow++;
        return true;
    }

    /** How many lines this second's limit ate, then forgets them. */
    public synchronized int takeSuppressed() {
        int count = suppressed;
        suppressed = 0;
        return count;
    }
}
