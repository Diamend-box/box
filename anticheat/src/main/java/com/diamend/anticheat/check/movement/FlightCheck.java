package com.diamend.anticheat.check.movement;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.config.AntiCheatConfig;

/**
 * Predictive vertical-physics check — the counter to Meteor/Wurst "Jetpack" and
 * "Flight".
 *
 * <p>Those modules keep a player aloft by feeding a constant small upward (or
 * neutral) velocity every tick. Vanilla physics does the opposite: once a player
 * is airborne and not jumping, vertical velocity is
 * {@code (motionY - 0.08) * 0.98} — it goes negative within a tick or two and
 * keeps falling. The packet listener computes, each position packet, how far the
 * observed vertical velocity <em>exceeds</em> that prediction
 * ({@link com.diamend.anticheat.util.PhysicsUtil#verticalExcess}) while the
 * player is provably off the ground, and counts consecutive offending ticks.
 *
 * <p>This check turns that streak into a verdict. A short streak is a jump or a
 * bit of knockback and is ignored; a sustained streak is a player defying
 * gravity. A single very large excess (teleport-style lift) is flagged on its
 * own. Keeping the raw physics in the listener and the verdict here means the
 * decision logic is a pure function that unit tests can drive directly.
 */
public final class FlightCheck extends Check {

    public FlightCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.FLIGHT, config);
    }

    /**
     * @param excess    how much this tick's vertical velocity beat the physics
     *                  prediction (blocks/tick); positive means "rose more than
     *                  gravity allows"
     * @param liftTicks number of consecutive ticks {@code excess} has stayed
     *                  above the tolerance, maintained by the caller
     */
    public CheckResult inspect(double excess, int liftTicks) {
        CheckConfig cfg = config();
        double bigExcess = cfg.get("big-excess", 0.5D);
        int minLiftTicks = (int) cfg.get("min-lift-ticks", 6);
        double sustainedVl = cfg.get("sustained-vl", 1.0D);
        double bigVl = cfg.get("big-vl", 4.0D);

        // A single tick of strong lift (much more than a jump's 0.42) is enough.
        if (excess >= bigExcess) {
            String detail = String.format(Locale.ROOT, "excess=%.3f (instant lift)", excess);
            return CheckResult.fail(bigVl, detail);
        }
        // Otherwise require a sustained hover/ascent that vanilla can't produce.
        if (liftTicks >= minLiftTicks) {
            String detail = String.format(Locale.ROOT, "lift=%.3f x%d ticks", excess, liftTicks);
            return CheckResult.fail(sustainedVl, detail);
        }
        return CheckResult.pass();
    }

    /** Vertical excess above which a tick counts toward the lift streak. */
    public double liftTolerance() {
        return config().get("lift-tolerance", 0.05D);
    }

    /** Ignore lift for this long after the server granted velocity (knockback). */
    public long velocityGraceMs() {
        return (long) config().get("velocity-grace-ms", 1200D);
    }
}
