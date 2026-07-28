package com.diamend.boxcore.data;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything BoxCore stores about one player: their skill-point ledger, the
 * nodes they own and their collection progress.
 *
 * <p>Points are kept as a ledger (lifetime earned vs. spent) rather than a
 * single balance. That way a respec, a node whose cost changed, or a node the
 * owner deleted from {@code trees.yml} can all be reconciled by recomputing
 * "spent" from what the player actually owns — the player can never end up
 * owing points or silently losing them.
 */
public class PlayerProfile {

    private final UUID uuid;

    private String name = "";
    private int pointsEarned;
    private int pointsSpent;

    /** Node key ({@code tree.node}) → owned level. */
    private final Map<String, Integer> nodes = new ConcurrentHashMap<>();
    /** Collection id → lifetime amount gathered. */
    private final Map<String, Long> collections = new ConcurrentHashMap<>();
    /** Collection id → highest tier already paid out. */
    private final Map<String, Integer> collectionTiers = new ConcurrentHashMap<>();

    /** Whether the auto-compressor runs for this player. */
    private volatile boolean compressorEnabled = true;

    private volatile boolean dirty;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    // ------------------------------------------------------------------
    // Points
    // ------------------------------------------------------------------

    public int getPointsEarned() {
        return pointsEarned;
    }

    public int getPointsSpent() {
        return pointsSpent;
    }

    /** Points the player can still spend. */
    public int getAvailablePoints() {
        return Math.max(0, pointsEarned - pointsSpent);
    }

    public void addPoints(int amount) {
        if (amount == 0) {
            return;
        }
        pointsEarned = Math.max(0, pointsEarned + amount);
        dirty = true;
    }

    /** Sets the *available* balance, keeping everything already spent intact. */
    public void setAvailablePoints(int amount) {
        pointsEarned = pointsSpent + Math.max(0, amount);
        dirty = true;
    }

    public void setPointsEarned(int amount) {
        pointsEarned = Math.max(0, amount);
        dirty = true;
    }

    public void setPointsSpent(int amount) {
        pointsSpent = Math.max(0, amount);
        dirty = true;
    }

    public boolean spend(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (getAvailablePoints() < amount) {
            return false;
        }
        pointsSpent += amount;
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------------
    // Skill nodes
    // ------------------------------------------------------------------

    public int getNodeLevel(String nodeKey) {
        return nodes.getOrDefault(nodeKey, 0);
    }

    public boolean hasNode(String nodeKey) {
        return getNodeLevel(nodeKey) > 0;
    }

    public void setNodeLevel(String nodeKey, int level) {
        if (level <= 0) {
            nodes.remove(nodeKey);
        } else {
            nodes.put(nodeKey, level);
        }
        dirty = true;
    }

    public Map<String, Integer> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public void clearNodes() {
        nodes.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    public long getCollected(String collectionId) {
        return collections.getOrDefault(collectionId, 0L);
    }

    public void setCollected(String collectionId, long amount) {
        if (amount <= 0) {
            collections.remove(collectionId);
        } else {
            collections.put(collectionId, amount);
        }
        dirty = true;
    }

    /** Adds to a collection and returns the new total. */
    public long addCollected(String collectionId, long amount) {
        long total = Math.max(0, getCollected(collectionId) + amount);
        setCollected(collectionId, total);
        return total;
    }

    public int getAwardedTier(String collectionId) {
        return collectionTiers.getOrDefault(collectionId, 0);
    }

    public void setAwardedTier(String collectionId, int tier) {
        if (tier <= 0) {
            collectionTiers.remove(collectionId);
        } else {
            collectionTiers.put(collectionId, tier);
        }
        dirty = true;
    }

    public Map<String, Long> getCollections() {
        return Collections.unmodifiableMap(collections);
    }

    public Map<String, Integer> getCollectionTiers() {
        return Collections.unmodifiableMap(collectionTiers);
    }

    /** Total items gathered across every collection. */
    public long getTotalCollected() {
        long total = 0;
        for (long value : collections.values()) {
            total += value;
        }
        return total;
    }

    public void clearCollections() {
        collections.clear();
        collectionTiers.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Auto-compressor
    // ------------------------------------------------------------------

    public boolean isCompressorEnabled() {
        return compressorEnabled;
    }

    public void setCompressorEnabled(boolean enabled) {
        if (enabled != this.compressorEnabled) {
            this.compressorEnabled = enabled;
            dirty = true;
        }
    }

    // ------------------------------------------------------------------

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }
}
