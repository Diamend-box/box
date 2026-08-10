package com.diamend.robobear.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What RoboBear remembers about one player between runs.
 *
 * <p>Runs themselves are never stored — a run has a clock, and a clock that
 * survives a logout isn't a clock. What persists is the record left behind: how
 * far they've climbed, how many milestone tiers they've taken, and when they
 * last went in.
 */
public class PlayerData {

    private final UUID uuid;
    private String name = "";

    private int runs;
    private int bestRound;
    private int totalRounds;
    private long lastRunAt;
    private long bestRunSeconds;

    /** Milestone round → how many times they've been paid for reaching it. */
    private final Map<Integer, Integer> milestones = new HashMap<>();

    private boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clean() {
        this.dirty = false;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public int runs() {
        return runs;
    }

    public int bestRound() {
        return bestRound;
    }

    public int totalRounds() {
        return totalRounds;
    }

    public long lastRunAt() {
        return lastRunAt;
    }

    public long bestRunSeconds() {
        return bestRunSeconds;
    }

    public int milestoneCount(int round) {
        return milestones.getOrDefault(round, 0);
    }

    public boolean hasMilestone(int round) {
        return milestoneCount(round) > 0;
    }

    public int totalMilestones() {
        int total = 0;
        for (int count : milestones.values()) {
            total += count;
        }
        return total;
    }

    /** The deepest milestone round they've ever been paid for, or 0. */
    public int deepestMilestone() {
        int deepest = 0;
        for (Map.Entry<Integer, Integer> entry : milestones.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() > deepest) {
                deepest = entry.getKey();
            }
        }
        return deepest;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Records one finished round. */
    public void recordRound(int round, long now) {
        totalRounds++;
        if (round > bestRound) {
            bestRound = round;
        }
        lastRunAt = now;
        dirty = true;
    }

    public void recordMilestone(int round) {
        milestones.merge(round, 1, Integer::sum);
        dirty = true;
    }

    /**
     * Records a run ending.
     *
     * @param roundsCleared how many rounds they actually finished
     * @param seconds       how long the whole run took
     */
    public void recordRunEnd(int roundsCleared, long seconds, long now) {
        runs++;
        lastRunAt = now;
        if (roundsCleared > 0 && (bestRunSeconds == 0 || seconds < bestRunSeconds)) {
            bestRunSeconds = seconds;
        }
        dirty = true;
    }

    /** Seconds until another run may be started, or 0 when it's ready. */
    public long cooldownRemaining(long cooldownSeconds, long now) {
        if (cooldownSeconds <= 0 || lastRunAt == 0L) {
            return 0;
        }
        long ready = lastRunAt + (cooldownSeconds * 1000L);
        return now >= ready ? 0 : (ready - now + 999) / 1000;
    }

    public void clear() {
        runs = 0;
        bestRound = 0;
        totalRounds = 0;
        lastRunAt = 0;
        bestRunSeconds = 0;
        milestones.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Raw access, for the storage layer only
    // ------------------------------------------------------------------

    public Map<Integer, Integer> milestonesMap() {
        return milestones;
    }

    public void restore(int runs, int bestRound, int totalRounds, long lastRunAt, long bestRunSeconds) {
        this.runs = runs;
        this.bestRound = bestRound;
        this.totalRounds = totalRounds;
        this.lastRunAt = lastRunAt;
        this.bestRunSeconds = bestRunSeconds;
    }
}
