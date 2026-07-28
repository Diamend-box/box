package com.diamend.darksea.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened during {@code onEnable}, step by step, kept so it can be read
 * back in game.
 *
 * <p>The reason this exists: Paper disables an entire plugin when
 * {@code onEnable} throws, so one bad island schematic or a caves world that
 * will not create used to take the whole Dark Sea down with it. Startup now
 * runs each step behind a catch and records the outcome here instead. A failed
 * step is a feature that is missing for the session, not a dead plugin — and
 * {@code /ds diag} says which one, to whoever is standing in game without
 * console access.
 *
 * <p>Deliberately Bukkit-free: this is the part worth being sure of, and it
 * compiles and tests without a server.
 *
 * <p>Thread-safe. Steps are recorded from the main thread during enable, but
 * read from whatever thread a command lands on.
 */
public final class StartupReport {

    /** How a step ended. There is no partial credit. */
    public enum Outcome { OK, FAILED }

    /**
     * One startup step.
     *
     * @param detail for a failure, the exception type and message; empty on success
     * @param millis wall time the step took, for spotting the slow one
     */
    public record Step(String name, Outcome outcome, String detail, long millis) {

        public boolean failed() {
            return outcome == Outcome.FAILED;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    /** Records a step that completed. */
    public synchronized void ok(String name, long millis) {
        steps.add(new Step(name, Outcome.OK, "", millis));
    }

    /** Records a step that threw. {@code detail} is shown verbatim in game. */
    public synchronized void failed(String name, String detail, long millis) {
        steps.add(new Step(name, Outcome.FAILED, detail == null ? "" : detail, millis));
    }

    /** Every step in the order it ran. */
    public synchronized List<Step> steps() {
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /** Only the steps that threw — what {@code /ds diag} leads with. */
    public synchronized List<Step> failures() {
        List<Step> out = new ArrayList<>();
        for (Step step : steps) {
            if (step.failed()) {
                out.add(step);
            }
        }
        return out;
    }

    public synchronized int stepCount() {
        return steps.size();
    }

    public synchronized int failureCount() {
        return failures().size();
    }

    /** True when every recorded step completed. Vacuously true before enable. */
    public synchronized boolean healthy() {
        return failureCount() == 0;
    }

    /** Total wall time of all recorded steps. */
    public synchronized long totalMillis() {
        long total = 0;
        for (Step step : steps) {
            total += step.millis();
        }
        return total;
    }

    /** The slowest step, or null if nothing has been recorded. */
    public synchronized Step slowest() {
        Step worst = null;
        for (Step step : steps) {
            if (worst == null || step.millis() > worst.millis()) {
                worst = step;
            }
        }
        return worst;
    }

    /** One line for the console at the end of enable, and for the diag header. */
    public synchronized String summary() {
        int failed = failureCount();
        int total = steps.size();
        if (failed == 0) {
            return total + " of " + total + " startup steps OK in " + totalMillis() + "ms";
        }
        return (total - failed) + " of " + total + " startup steps OK in " + totalMillis()
                + "ms — " + failed + " FAILED";
    }

    /** Forgets everything. Only used by tests and a hypothetical re-enable. */
    public synchronized void clear() {
        steps.clear();
    }
}
