package com.diamend.darksea.bounty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure bookkeeping for player bounties: a Chronon price on a head, poolable by
 * many sponsors and paid out whole to whoever lands the kill. No Bukkit here —
 * {@link BountyService} owns persistence and the world-facing rules.
 */
public final class BountyLedger {

    private final Map<UUID, Integer> bounties = new ConcurrentHashMap<>();

    /** Adds {@code amount} to a head's bounty; returns the new running total. */
    public int place(UUID target, int amount) {
        if (target == null || amount <= 0) {
            throw new IllegalArgumentException("bounty amount must be positive");
        }
        return bounties.merge(target, amount, Integer::sum);
    }

    /** The current bounty on a head, 0 if none. */
    public int get(UUID target) {
        return target == null ? 0 : bounties.getOrDefault(target, 0);
    }

    /** True if a head currently carries a bounty. */
    public boolean has(UUID target) {
        return get(target) > 0;
    }

    /** Removes and returns a head's bounty (0 if none) — the whole payout. */
    public int claim(UUID target) {
        Integer prior = target == null ? null : bounties.remove(target);
        return prior == null ? 0 : prior;
    }

    /** The {@code n} richest bounties, highest first. */
    public List<Map.Entry<UUID, Integer>> top(int n) {
        List<Map.Entry<UUID, Integer>> all = new ArrayList<>(bounties.entrySet());
        all.sort(Comparator.comparingInt((Map.Entry<UUID, Integer> e) -> e.getValue()).reversed());
        return n < all.size() ? new ArrayList<>(all.subList(0, Math.max(0, n))) : all;
    }

    /** How many heads carry a bounty. */
    public int size() {
        return bounties.size();
    }

    /** A snapshot for persistence: UUID string -&gt; amount, in descending order. */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> e : top(bounties.size())) {
            out.put(e.getKey().toString(), e.getValue());
        }
        return out;
    }

    /** Replaces the ledger from a persisted snapshot, skipping bad rows. */
    public void load(Map<String, Integer> snapshot) {
        bounties.clear();
        if (snapshot == null) {
            return;
        }
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            try {
                bounties.put(UUID.fromString(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) {
                // malformed UUID — drop it rather than fail the whole load
            }
        }
    }
}
