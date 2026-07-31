package com.diamend.boxcore.data;

import com.diamend.boxcore.boost.Boost;
import com.diamend.boxcore.boost.BoostType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    /**
     * Fast-travel destinations this player has found.
     *
     * <p>Held as ids rather than as warps, so deleting a warp and adding it back
     * under the same name keeps everyone's discovery of it, and a warp that no
     * longer exists is simply an id nothing matches rather than a dangling
     * reference to clean up.
     */
    private final Set<String> discoveredWarps = ConcurrentHashMap.newKeySet();

    /**
     * Boosts this player is personally running.
     *
     * <p>Kept on the profile rather than in memory because they expire on the
     * wall clock: a thirty-minute boost has to keep running across a relog or a
     * restart, or every server hiccup would quietly refund itself.
     */
    private final List<Boost> boosts = new CopyOnWriteArrayList<>();

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
    // Fast travel
    // ------------------------------------------------------------------

    public Set<String> getDiscoveredWarps() {
        return Collections.unmodifiableSet(discoveredWarps);
    }

    public boolean hasDiscovered(String warpId) {
        return warpId != null && discoveredWarps.contains(warpId);
    }

    /** Records a find. Returns true only the first time, so callers can announce it. */
    public boolean discoverWarp(String warpId) {
        if (warpId == null || warpId.isBlank() || !discoveredWarps.add(warpId)) {
            return false;
        }
        dirty = true;
        return true;
    }

    public void setDiscoveredWarps(Collection<String> ids) {
        discoveredWarps.clear();
        if (ids != null) {
            discoveredWarps.addAll(ids);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Boosts
    // ------------------------------------------------------------------

    /** Every stored boost, expired ones included. */
    public List<Boost> getBoosts() {
        return Collections.unmodifiableList(boosts);
    }

    /** Boosts of a type that are still running at {@code now}. */
    public List<Boost> activeBoosts(BoostType type, long now) {
        List<Boost> active = new ArrayList<>();
        for (Boost boost : boosts) {
            if (boost.type() == type && boost.isActive(now)) {
                active.add(boost);
            }
        }
        return active;
    }

    public void addBoost(Boost boost) {
        if (boost != null) {
            boosts.add(boost);
            dirty = true;
        }
    }

    public void setBoosts(List<Boost> replacement) {
        boosts.clear();
        if (replacement != null) {
            boosts.addAll(replacement);
        }
        dirty = true;
    }

    /**
     * Drops boosts that have run out.
     *
     * @return true when something was actually removed, so callers can avoid
     *         marking a profile dirty every time they look at it
     */
    public boolean pruneBoosts(long now) {
        boolean removed = boosts.removeIf(boost -> !boost.isActive(now));
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    public void clearBoosts() {
        if (!boosts.isEmpty()) {
            boosts.clear();
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
