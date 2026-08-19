package com.diamend.anticheat.player;

/**
 * Per-player vertical/horizontal movement history used by the Flight, Speed and
 * NoFall checks. Updated from position packets on Netty threads, so every
 * accessor is {@code synchronized}.
 *
 * <p>Everything here is derived purely from the stream of client position
 * packets — the plugin never trusts the client's {@code onGround} flag; ground
 * truth comes from {@link com.diamend.anticheat.world.BlockCache} instead.
 */
public final class MovementState {

    private boolean hasPosition;
    private double lastX;
    private double lastY;
    private double lastZ;

    /** Last tick's observed vertical velocity (newY - lastY). */
    private double lastMotionY;
    /** Consecutive position packets during which the player was airborne. */
    private int airTicks;
    /** Consecutive ticks the vertical motion exceeded the physics prediction. */
    private int liftTicks;
    /** Consecutive ticks the horizontal speed exceeded the allowed maximum. */
    private int fastTicks;

    /** When the server last granted this player velocity (knockback / explosion). */
    private long lastVelocityMs;
    /** When the player was last provably on the ground (server-side). */
    private long lastServerGroundMs;

    /** Fall distance we track ourselves, ignoring the client's claim. */
    private double serverFallDistance;

    public synchronized boolean hasPosition() {
        return hasPosition;
    }

    public synchronized double lastX() {
        return lastX;
    }

    public synchronized double lastY() {
        return lastY;
    }

    public synchronized double lastZ() {
        return lastZ;
    }

    public synchronized double lastMotionY() {
        return lastMotionY;
    }

    public synchronized int airTicks() {
        return airTicks;
    }

    public synchronized int liftTicks() {
        return liftTicks;
    }

    public synchronized void setLiftTicks(int ticks) {
        this.liftTicks = ticks;
    }

    public synchronized int fastTicks() {
        return fastTicks;
    }

    public synchronized void setFastTicks(int ticks) {
        this.fastTicks = ticks;
    }

    /** Update the stored position and return the vertical velocity just observed. */
    public synchronized double updatePosition(double x, double y, double z, boolean airborne) {
        double motionY = hasPosition ? (y - lastY) : 0.0D;
        this.lastMotionY = motionY;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.hasPosition = true;
        if (airborne) {
            this.airTicks++;
        } else {
            this.airTicks = 0;
        }
        return motionY;
    }

    public synchronized void markVelocity(long now) {
        this.lastVelocityMs = now;
    }

    public synchronized long lastVelocityMs() {
        return lastVelocityMs;
    }

    public synchronized void markServerGround(long now) {
        this.lastServerGroundMs = now;
        this.serverFallDistance = 0.0D;
    }

    public synchronized long lastServerGroundMs() {
        return lastServerGroundMs;
    }

    /** Accumulate fall distance from a downward motion, and read it back. */
    public synchronized double addFall(double dropped) {
        if (dropped > 0) {
            this.serverFallDistance += dropped;
        }
        return this.serverFallDistance;
    }

    public synchronized double serverFallDistance() {
        return serverFallDistance;
    }

    public synchronized void resetFall() {
        this.serverFallDistance = 0.0D;
    }
}
