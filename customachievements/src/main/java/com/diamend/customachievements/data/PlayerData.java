package com.diamend.customachievements.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks a single player's achievement progress and unlocked achievements.
 */
public class PlayerData {

    private final UUID uuid;
    private final Set<String> completed = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    private String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public boolean isCompleted(String id) {
        return completed.contains(key(id));
    }

    public Set<String> getCompleted() {
        return completed;
    }

    public int getProgress(String id) {
        return progress.getOrDefault(key(id), 0);
    }

    public Map<String, Integer> getProgressMap() {
        return progress;
    }

    /** Adds progress toward a key and returns the new total. */
    public int addProgress(String id, int amount) {
        int updated = getProgress(id) + amount;
        progress.put(key(id), updated);
        dirty = true;
        return updated;
    }

    /** Sets progress for a key to an exact value (used by threshold objectives). */
    public void setProgress(String id, int value) {
        progress.put(key(id), value);
        dirty = true;
    }

    /** Composite progress key for a single requirement of an achievement. */
    public static String requirementKey(String achievementId, int index) {
        return achievementId.toLowerCase(Locale.ROOT) + "#" + index;
    }

    public void setCompleted(String id) {
        completed.add(key(id));
        progress.remove(key(id));
        dirty = true;
    }

    public void revoke(String id) {
        completed.remove(key(id));
        progress.remove(key(id));
        dirty = true;
    }

    public void reset() {
        completed.clear();
        progress.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }
}
