package com.diamend.anticheat.player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.violation.ViolationTracker;

/**
 * Owns one {@link PlayerData} per online player. Entries are created on join and
 * dropped on quit. Backed by a concurrent map because packetevents may look a
 * player up on a network thread.
 */
public final class PlayerDataManager {

    private final ConcurrentHashMap<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private volatile AntiCheatConfig config;

    public PlayerDataManager(AntiCheatConfig config) {
        this.config = config;
    }

    /** Swap in a fresh config snapshot after a reload. */
    public void updateConfig(AntiCheatConfig config) {
        this.config = config;
    }

    public PlayerData create(UUID uuid, long now) {
        ViolationTracker tracker = new ViolationTracker(config.decayPerSecond());
        PlayerData created = new PlayerData(uuid, tracker, now);
        data.put(uuid, created);
        return created;
    }

    public PlayerData get(UUID uuid) {
        return data.get(uuid);
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public void clear() {
        data.clear();
    }
}
