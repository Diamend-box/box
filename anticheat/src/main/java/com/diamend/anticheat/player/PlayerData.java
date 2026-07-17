package com.diamend.anticheat.player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.diamend.anticheat.violation.ViolationTracker;

/**
 * Per-player mutable state used by the combat checks.
 *
 * <p>Threading: rotation, click and attack updates arrive on packetevents'
 * network threads, while some reads happen on the main server thread (commands,
 * alerts). Every method that touches shared state is therefore
 * {@code synchronized} on this instance. The buffers are small and bounded, so
 * the contention is negligible.
 */
public final class PlayerData {

    /** How many recent click timestamps to keep for CPS / regularity analysis. */
    private static final int CLICK_HISTORY = 20;

    private final UUID uuid;
    private final ViolationTracker violations;

    // Movement / world sub-states, each self-synchronized. Kept separate so the
    // combat state below stays readable and the movement checks get their own
    // small, focused holders.
    private final MovementState movement = new MovementState();
    private final TimerState timer = new TimerState();
    private final VehicleState vehicle = new VehicleState();
    private final PlaceState place = new PlaceState();

    private final Deque<Long> clickTimes = new ArrayDeque<>(CLICK_HISTORY);

    private final long joinTime;
    private long lastTeleport;
    private long lastSwing;
    private long lastAttack;
    private int lastAttackedEntityId = -1;

    // Most recent two rotations, for delta analysis.
    private float lastYaw;
    private float lastPitch;
    private boolean hasRotation;
    private float lastYawDelta;
    private float lastPitchDelta;
    private float prevYawDelta;

    public PlayerData(UUID uuid, ViolationTracker violations, long joinTime) {
        this.uuid = uuid;
        this.violations = violations;
        this.joinTime = joinTime;
    }

    public UUID uuid() {
        return uuid;
    }

    public ViolationTracker violations() {
        return violations;
    }

    public long joinTime() {
        return joinTime;
    }

    public MovementState movement() {
        return movement;
    }

    public TimerState timer() {
        return timer;
    }

    public VehicleState vehicle() {
        return vehicle;
    }

    public PlaceState place() {
        return place;
    }

    public synchronized void markTeleport(long now) {
        this.lastTeleport = now;
    }

    public synchronized long lastTeleport() {
        return lastTeleport;
    }

    /** Record a swing (left-click / attack animation) and return its timestamp. */
    public synchronized void recordSwing(long now) {
        this.lastSwing = now;
        if (clickTimes.size() >= CLICK_HISTORY) {
            clickTimes.pollFirst();
        }
        clickTimes.addLast(now);
    }

    public synchronized long lastSwing() {
        return lastSwing;
    }

    /** Snapshot of the recent click timestamps, oldest first. */
    public synchronized long[] clickTimes() {
        long[] out = new long[clickTimes.size()];
        int i = 0;
        for (long t : clickTimes) {
            out[i++] = t;
        }
        return out;
    }

    public synchronized void recordAttack(int entityId, long now) {
        this.lastAttack = now;
        this.lastAttackedEntityId = entityId;
    }

    public synchronized long lastAttack() {
        return lastAttack;
    }

    public synchronized int lastAttackedEntityId() {
        return lastAttackedEntityId;
    }

    /**
     * Record a new rotation, updating the running yaw/pitch deltas.
     *
     * @return true once at least one previous rotation was already known, i.e.
     *         the returned deltas are meaningful.
     */
    public synchronized boolean recordRotation(float yaw, float pitch) {
        boolean had = hasRotation;
        if (had) {
            this.prevYawDelta = this.lastYawDelta;
            this.lastYawDelta = com.diamend.anticheat.util.MathUtil.wrapDegrees(yaw - lastYaw);
            this.lastPitchDelta = pitch - lastPitch;
        }
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.hasRotation = true;
        return had;
    }

    public synchronized float lastYawDelta() {
        return lastYawDelta;
    }

    public synchronized float lastPitchDelta() {
        return lastPitchDelta;
    }

    /** The yaw delta immediately before {@link #lastYawDelta()} (0 until two are known). */
    public synchronized float prevYawDelta() {
        return prevYawDelta;
    }
}
