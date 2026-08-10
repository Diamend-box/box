package com.diamend.robobear.challenge;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's climb, in flight.
 *
 * <p>A run is a ladder of rounds. Each round offers a choice of objectives, one
 * clock, and Cogs on completion; between rounds the shop is open and the clock
 * is stopped. The run ends when a clock runs out, the player dies, or they walk
 * away with what they've banked in milestone rewards.
 *
 * <p>Never persisted. A run has a clock, and a clock that survives a logout
 * isn't a clock.
 */
public class RoboRun {

    /** Where the run currently is. The clock only moves in {@link #RUNNING}. */
    public enum State {
        /** Choosing between the offered objectives. */
        CHOOSING,
        /** An objective is live and its clock is counting down. */
        RUNNING,
        /** Between rounds, spending Cogs. */
        SHOPPING
    }

    private final UUID player;
    private final long startedAt;

    private State state = State.CHOOSING;
    private int round = 1;
    private int cogs;
    private int rerollsLeft;

    private final Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
    private final List<Integer> milestonesEarned = new ArrayList<>();

    private List<Objective> offers = List.of();
    private Objective objective;
    private int progress;
    private long deadline;
    private boolean warned;

    /** Extra seconds on every round, bought with {@link Upgrade#OVERCLOCK}. */
    private int bonusSeconds;

    /**
     * Blocks placed by this player since the run began, packed one per long.
     *
     * <p>Run-scoped, not a tracker. BoxCore's {@code PlacedBlocks} is the
     * server's one persistent placed-block record and the spec (§17) is explicit
     * that there should only be one; this set lives for a few minutes, answers
     * one question, and is dropped with the run.
     */
    private final Set<Long> placedThisRun = new HashSet<>();
    private boolean placementLimitHit;

    public RoboRun(UUID player, long now, int startingCogs, int freeRerolls) {
        this.player = player;
        this.startedAt = now;
        this.cogs = Math.max(0, startingCogs);
        this.rerollsLeft = Math.max(0, freeRerolls);
    }

    // ------------------------------------------------------------------
    // Identity and shape
    // ------------------------------------------------------------------

    public UUID player() {
        return player;
    }

    public State state() {
        return state;
    }

    public int round() {
        return round;
    }

    public int cogs() {
        return cogs;
    }

    public int rerollsLeft() {
        return rerollsLeft;
    }

    public long startedAt() {
        return startedAt;
    }

    public long elapsedSeconds(long now) {
        return Math.max(0, (now - startedAt) / 1000);
    }

    public List<Integer> milestonesEarned() {
        return milestonesEarned;
    }

    // ------------------------------------------------------------------
    // Cogs
    // ------------------------------------------------------------------

    public void addCogs(int amount) {
        this.cogs = Math.max(0, cogs + amount);
    }

    public boolean spendCogs(int amount) {
        if (amount > cogs) {
            return false;
        }
        cogs -= amount;
        return true;
    }

    // ------------------------------------------------------------------
    // Upgrades
    // ------------------------------------------------------------------

    public int levelOf(Upgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void addLevel(Upgrade upgrade) {
        upgrades.merge(upgrade, 1, Integer::sum);
    }

    public Map<Upgrade, Integer> upgrades() {
        return upgrades;
    }

    public int bonusSeconds() {
        return bonusSeconds;
    }

    public void addBonusSeconds(int seconds) {
        this.bonusSeconds += seconds;
        // A battery bought mid-round should be felt in that round, not the next.
        if (state == State.RUNNING) {
            deadline += seconds * 1000L;
            warned = false;
        }
    }

    // ------------------------------------------------------------------
    // The round
    // ------------------------------------------------------------------

    public List<Objective> offers() {
        return offers;
    }

    public void setOffers(List<Objective> offers) {
        this.offers = offers == null ? List.of() : List.copyOf(offers);
        this.state = State.CHOOSING;
    }

    public boolean useReroll() {
        if (rerollsLeft <= 0) {
            return false;
        }
        rerollsLeft--;
        return true;
    }

    public Objective objective() {
        return objective;
    }

    public int progress() {
        return progress;
    }

    /** Locks in an objective and starts this round's clock. */
    public void begin(Objective chosen, int seconds, long now) {
        this.objective = chosen;
        this.progress = 0;
        this.warned = false;
        this.offers = List.of();
        this.state = State.RUNNING;
        this.deadline = now + (seconds + bonusSeconds) * 1000L;
    }

    /** Records one counting event. Returns true when the round is complete. */
    public boolean count() {
        progress++;
        return objective != null && progress >= objective.amount();
    }

    /** Ends the round and opens the shop; the clock stops here. */
    public void finishRound() {
        this.state = State.SHOPPING;
        this.objective = null;
        this.progress = 0;
    }

    /** Moves to the next round. Offers are set separately. */
    public void nextRound() {
        this.round++;
    }

    public double fraction() {
        if (objective == null) {
            return 0.0;
        }
        return Math.min(1.0, (double) progress / objective.amount());
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    public long deadline() {
        return deadline;
    }

    public long secondsLeft(long now) {
        if (state != State.RUNNING) {
            return 0;
        }
        return Math.max(0, (deadline - now + 999) / 1000);
    }

    public boolean isExpired(long now) {
        return state == State.RUNNING && now >= deadline;
    }

    public boolean warned() {
        return warned;
    }

    public void markWarned() {
        this.warned = true;
    }

    // ------------------------------------------------------------------
    // Self-placed blocks
    // ------------------------------------------------------------------

    /** Packs a block position into a long: 26 bits of x and z, 12 of y. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + 2048) & 0xFFF);
    }

    public static long pack(Block block) {
        return pack(block.getX(), block.getY(), block.getZ());
    }

    public void notePlaced(Block block, int limit) {
        if (placedThisRun.size() >= limit) {
            placementLimitHit = true;
            return;
        }
        placedThisRun.add(pack(block));
    }

    /**
     * Whether this block was placed during the run — consuming the record, so a
     * position freed by a mine reset isn't refused forever by a stale flag.
     */
    public boolean wasPlacedThisRun(Block block) {
        return placedThisRun.remove(pack(block));
    }

    public boolean placementLimitHit() {
        return placementLimitHit;
    }
}
