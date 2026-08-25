package com.diamend.customachievements.data;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    // Requirements already seeded from this player's lifetime statistics. Tracked
    // separately from progress because "has progress" can't stand in for "was
    // seeded": a single kill before the backfill ran would otherwise lock the
    // objective out of it forever.
    private final Set<String> backfilled = new HashSet<>();
    // Reward items that couldn't fit in the player's inventory when unlocked,
    // held here until they claim them (so they're never dropped/lost).
    private final List<ItemStack> pendingRewards = new ArrayList<>();
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

    /** Whether this requirement has already been seeded from the player's statistics. */
    public boolean isBackfilled(String marker) {
        return backfilled.contains(key(marker));
    }

    /** Records that a requirement has been seeded, so it is never seeded twice. */
    public void markBackfilled(String marker) {
        if (backfilled.add(key(marker))) {
            dirty = true;
        }
    }

    public Set<String> getBackfilled() {
        return backfilled;
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
        pendingRewards.clear();
        // The backfill markers deliberately survive a reset: re-seeding the
        // player straight back from their statistics would undo it on next join.
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Unclaimed reward items (inventory-full overflow storage)
    // ------------------------------------------------------------------

    /** The live list of reward items waiting to be claimed. */
    public List<ItemStack> getPendingRewards() {
        return pendingRewards;
    }

    public boolean hasPendingRewards() {
        return !pendingRewards.isEmpty();
    }

    /** Queues a reward item for later claiming. */
    public void addPendingReward(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        pendingRewards.add(item.clone());
        dirty = true;
    }

    /** Replaces the pending-reward list wholesale (used by the claim menu). */
    public void setPendingRewards(List<ItemStack> items) {
        pendingRewards.clear();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    pendingRewards.add(item.clone());
                }
            }
        }
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
