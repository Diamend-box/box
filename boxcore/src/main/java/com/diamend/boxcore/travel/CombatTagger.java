package com.diamend.boxcore.travel;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who has been in a player-versus-player fight recently.
 *
 * <p>Fast travel on a PvP server is only safe if it can't be used to leave a
 * losing fight, and a warmup alone doesn't manage that — breaking line of sight
 * for three seconds is not hard. This is the other half: while you're tagged,
 * travelling is refused outright.
 *
 * <p>Only player-versus-player damage tags. Being hit by a zombie is not a
 * fight anyone is trying to escape by warping, and tagging on it would make the
 * feature feel broken in a mob farm.
 */
public class CombatTagger {

    private final Map<UUID, Long> tagged = new ConcurrentHashMap<>();
    private long tagMillis = 15_000L;

    public void setSeconds(long seconds) {
        this.tagMillis = Math.max(0L, seconds) * 1000L;
    }

    public long seconds() {
        return tagMillis / 1000L;
    }

    /** Marks both sides of a fight; being attacked counts as much as attacking. */
    public void tag(Player player) {
        if (player != null && tagMillis > 0) {
            tagged.put(player.getUniqueId(), System.currentTimeMillis() + tagMillis);
        }
    }

    public boolean isTagged(Player player) {
        return remainingMillis(player) > 0;
    }

    /** How much longer they stay tagged, in milliseconds; 0 when clear. */
    public long remainingMillis(Player player) {
        if (player == null) {
            return 0;
        }
        Long until = tagged.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            tagged.remove(player.getUniqueId());
            return 0;
        }
        return left;
    }

    public void forget(UUID uuid) {
        tagged.remove(uuid);
    }

    public void clear() {
        tagged.clear();
    }
}
