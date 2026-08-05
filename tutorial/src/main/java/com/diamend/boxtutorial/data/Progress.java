package com.diamend.boxtutorial.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How far one player got.
 *
 * <p>Held in memory for everyone the plugin has ever seen — a record is a name,
 * a handful of flags and a short list of step ids, so the whole file is small
 * enough that keeping it loaded is cheaper than reading it per join.
 */
public class Progress {

    private final UUID uuid;
    private String name = "";

    private final Set<String> completed = new LinkedHashSet<>();
    private final Set<String> skipped = new LinkedHashSet<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private boolean started;
    private boolean finished;
    private boolean stopped;

    /** Milliseconds spent online while the tutorial was running. */
    private long playedMillis;

    public Progress(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    public Set<String> completed() {
        return Collections.unmodifiableSet(completed);
    }

    public boolean isComplete(String stepId) {
        return stepId != null && completed.contains(stepId);
    }

    /** Marks a step done. Returns false when it already was. */
    public boolean complete(String stepId) {
        if (stepId == null || !completed.add(stepId)) {
            return false;
        }
        counts.remove(stepId);
        return true;
    }

    /**
     * Steps the player stepped over rather than did.
     *
     * <p>Kept apart from {@link #completed()} so the checklist can keep telling
     * the truth: a skipped step is out of the way, but it isn't a tick.
     */
    public Set<String> skipped() {
        return Collections.unmodifiableSet(skipped);
    }

    public boolean isSkipped(String stepId) {
        return stepId != null && skipped.contains(stepId);
    }

    public void markSkipped(String stepId) {
        if (stepId != null) {
            skipped.add(stepId);
        }
    }

    public int count(String stepId) {
        Integer value = counts.get(stepId);
        return value == null ? 0 : value;
    }

    /** Adds to a step's counter and returns the new total. */
    public int addCount(String stepId, int amount) {
        int total = Math.max(0, count(stepId) + amount);
        counts.put(stepId, total);
        return total;
    }

    public void setCount(String stepId, int amount) {
        if (amount <= 0) {
            counts.remove(stepId);
        } else {
            counts.put(stepId, amount);
        }
    }

    public Map<String, Integer> counts() {
        return Collections.unmodifiableMap(counts);
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public boolean started() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public boolean finished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    /** True when the player asked the tutorial to leave them alone. */
    public boolean stopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public long playedMillis() {
        return playedMillis;
    }

    public void addPlayedMillis(long millis) {
        if (millis > 0) {
            this.playedMillis += millis;
        }
    }

    public void setPlayedMillis(long millis) {
        this.playedMillis = Math.max(0L, millis);
    }

    /** Back to never having seen the tutorial. */
    public void reset() {
        completed.clear();
        skipped.clear();
        counts.clear();
        started = false;
        finished = false;
        stopped = false;
        playedMillis = 0L;
    }
}
