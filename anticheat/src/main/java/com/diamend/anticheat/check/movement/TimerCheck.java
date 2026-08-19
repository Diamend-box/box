package com.diamend.anticheat.check.movement;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * Timer / packet-balance check (see {@link com.diamend.anticheat.player.TimerState}).
 *
 * <p>The player's token balance is minted by real time (one per 50 ms) and
 * spent by each movement packet. A client speeding up its game tick spends
 * faster than time mints, driving the balance below zero. This check flags once
 * the balance falls past a negative threshold. The listener then drops (cancels)
 * the excess packet in enforce mode and relaxes the balance so a single burst
 * doesn't produce a runaway alert storm.
 */
public final class TimerCheck extends Check {

    public TimerCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.TIMER, config);
    }

    /**
     * @param balance the token balance after accounting for this packet;
     *                strongly negative means packets outran real time
     */
    public CheckResult inspect(double balance) {
        CheckConfig cfg = config();
        double threshold = cfg.get("min-balance", -5.0D);
        double vl = cfg.get("vl", 1.0D);
        if (balance <= threshold) {
            String detail = String.format(Locale.ROOT, "balance=%.1f (<= %.1f)", balance, threshold);
            return CheckResult.fail(vl, detail);
        }
        return CheckResult.pass();
    }

    /** Balance to pull back to after a flag, so alerts don't spam every packet. */
    public double relaxTo() {
        return config().get("relax-to", -2.0D);
    }
}
