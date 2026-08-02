package com.diamend.darksea.mob;

/**
 * How many mobs an island will ever hand you before it is spent.
 *
 * The per-island CAP only limits how many stand at once, so killing one
 * frees its slot immediately and an island becomes an endless farm — the
 * first live test found exactly that. A budget is spent per spawn and does
 * not come back when a mob dies. Once it is gone the island is cleared and
 * stays cleared.
 *
 * The clock runs from the island's FIRST spawn rather than its last, the
 * same first-touch rule the crystal geodes use: a player who half-clears an
 * island and leaves does not push its recovery further away, and islands
 * touched at different moments recover at different moments without any
 * explicit stagger.
 *
 * Bukkit-free on purpose — the whole rule is unit-testable without a server.
 */
public final class SpawnBudget {

    private final int budget;
    private final long refillMillis;

    private int spent;
    private long firstSpentAt;
    /**
     * Tracked separately rather than using a sentinel timestamp: the clock
     * here is caller-supplied, so any specific "impossible" value is a real
     * value to somebody. Zero certainly is.
     */
    private boolean started;

    public SpawnBudget(int budget, long refillMillis) {
        this.budget = Math.max(1, budget);
        this.refillMillis = Math.max(0L, refillMillis);
    }

    /**
     * Refills first if the island is due, then spends one if anything is
     * left. Returns whether the caller may spawn.
     */
    public boolean tryConsume(long now) {
        refillIfDue(now);
        if (spent >= budget) {
            return false;
        }
        if (!started) {
            firstSpentAt = now;
            started = true;
        }
        spent++;
        return true;
    }

    /** Spawns still available, after any refill that is due. */
    public int remaining(long now) {
        refillIfDue(now);
        return Math.max(0, budget - spent);
    }

    /** Whether the island is spent and not yet due to refill. */
    public boolean exhausted(long now) {
        return remaining(now) == 0;
    }

    /**
     * Milliseconds until this island refills, or 0 when it has budget now.
     * Drives the {@code /ds status} readout more than anything in game.
     */
    public long millisUntilRefill(long now) {
        if (!exhausted(now)) {
            return 0L;
        }
        return Math.max(0L, (firstSpentAt + refillMillis) - now);
    }

    /** Hands the island straight back — used by resets, not by play. */
    public void reset() {
        spent = 0;
        started = false;
    }

    private void refillIfDue(long now) {
        if (started && now - firstSpentAt >= refillMillis) {
            spent = 0;
            started = false;
        }
    }
}
