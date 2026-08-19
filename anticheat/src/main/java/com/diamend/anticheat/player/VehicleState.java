package com.diamend.anticheat.player;

/**
 * Tracks the vertical motion of the entity a player is riding, for the Vehicle
 * (BoatFly) check. Populated from {@code VehicleMove} packets on Netty threads.
 */
public final class VehicleState {

    private boolean hasPosition;
    private double lastY;
    private double lastMotionY;
    private int airTicks;
    private int liftTicks;

    /** Update with a new vehicle Y and return the vertical velocity observed. */
    public synchronized double update(double y, boolean airborne) {
        double motionY = hasPosition ? (y - lastY) : 0.0D;
        this.lastMotionY = motionY;
        this.lastY = y;
        this.hasPosition = true;
        if (airborne) {
            this.airTicks++;
        } else {
            this.airTicks = 0;
        }
        return motionY;
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

    public synchronized void reset() {
        this.hasPosition = false;
        this.airTicks = 0;
        this.liftTicks = 0;
        this.lastMotionY = 0.0D;
    }
}
