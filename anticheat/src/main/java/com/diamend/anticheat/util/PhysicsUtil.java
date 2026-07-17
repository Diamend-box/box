package com.diamend.anticheat.util;

/**
 * Vanilla movement constants and the tiny recurrences the movement checks
 * predict against. Pure and side-effect free, so the physics can be unit-tested
 * without a server.
 *
 * <p>Vanilla player vertical motion each tick, while airborne and not jumping,
 * is {@code motionY = (motionY - 0.08) * 0.98}. A standing jump sets motionY to
 * {@code +0.42}. Boats and most vehicles fall under a stronger gravity of
 * {@code 0.04} but with the same drag. These are the values Meteor/Wurst's
 * jetpack and boatfly modules violate by feeding a constant upward velocity.
 */
public final class PhysicsUtil {

    /** Downward acceleration applied to a player each tick (blocks/tick^2). */
    public static final double PLAYER_GRAVITY = 0.08D;
    /** Vertical drag multiplier applied after gravity each tick. */
    public static final double VERTICAL_DRAG = 0.98D;
    /** Upward velocity of a standing jump. */
    public static final double JUMP_VELOCITY = 0.42D;
    /** Gravity applied to boats / most rideable entities each tick. */
    public static final double VEHICLE_GRAVITY = 0.04D;

    private PhysicsUtil() {
    }

    /** Predicted next vertical velocity for an airborne, non-jumping player. */
    public static double predictNextMotionY(double motionY) {
        return (motionY - PLAYER_GRAVITY) * VERTICAL_DRAG;
    }

    /** Predicted next vertical velocity for an airborne vehicle (boat/horse/pig). */
    public static double predictNextVehicleMotionY(double motionY) {
        return (motionY - VEHICLE_GRAVITY) * VERTICAL_DRAG;
    }

    /**
     * How much a player's observed vertical velocity exceeds what vanilla
     * physics allows this tick. Positive means "rose/hovered more than gravity
     * permits" — the jetpack/fly signal. Negative or zero means the movement is
     * at or below the predicted descent and is fine.
     *
     * @param prevMotionY   last tick's observed vertical velocity
     * @param observedMotionY this tick's observed vertical velocity (newY - lastY)
     */
    public static double verticalExcess(double prevMotionY, double observedMotionY) {
        return observedMotionY - predictNextMotionY(prevMotionY);
    }

    /** As {@link #verticalExcess} but using the stronger vehicle gravity. */
    public static double vehicleVerticalExcess(double prevMotionY, double observedMotionY) {
        return observedMotionY - predictNextVehicleMotionY(prevMotionY);
    }
}
