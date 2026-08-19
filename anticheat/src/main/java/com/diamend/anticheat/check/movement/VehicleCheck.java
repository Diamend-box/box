package com.diamend.anticheat.check.movement;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * BoatFly / entity-speed check — the vehicle counterpart to {@link FlightCheck}.
 *
 * <p>Meteor and Wurst sidestep player flight checks by riding a boat, pig or
 * horse and lifting the vehicle instead, because many anticheats forget to
 * inspect {@code VehicleMove} packets. This check applies the same predictive
 * physics using the stronger vehicle gravity: a boat that gains or holds height
 * while airborne, tick after tick, without anything under it, is being flown.
 * The listener maintains the offending-tick streak; this turns it into a
 * verdict, and enforce mode cancels the packet and dismounts the rider.
 */
public final class VehicleCheck extends Check {

    public VehicleCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.VEHICLE, config);
    }

    /**
     * @param excess    vehicle vertical velocity beyond the physics prediction
     * @param liftTicks consecutive offending ticks, maintained by the caller
     */
    public CheckResult inspect(double excess, int liftTicks) {
        CheckConfig cfg = config();
        double bigExcess = cfg.get("big-excess", 0.5D);
        int minLiftTicks = (int) cfg.get("min-lift-ticks", 5);
        double sustainedVl = cfg.get("sustained-vl", 1.5D);
        double bigVl = cfg.get("big-vl", 4.0D);

        if (excess >= bigExcess) {
            String detail = String.format(Locale.ROOT, "vehicle excess=%.3f", excess);
            return CheckResult.fail(bigVl, detail);
        }
        if (liftTicks >= minLiftTicks) {
            String detail = String.format(Locale.ROOT, "vehicle lift=%.3f x%d", excess, liftTicks);
            return CheckResult.fail(sustainedVl, detail);
        }
        return CheckResult.pass();
    }

    /** Vertical excess above which a vehicle tick counts toward the lift streak. */
    public double liftTolerance() {
        return config().get("lift-tolerance", 0.05D);
    }
}
