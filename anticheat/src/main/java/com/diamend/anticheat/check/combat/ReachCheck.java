package com.diamend.anticheat.check.combat;

import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * Flags attacks landed from further away than survival mode allows.
 *
 * <p>The caller computes the distance from the player's eye to the nearest
 * point of the target's hitbox (see
 * {@link com.diamend.anticheat.util.ReachCalculator}); this check only applies
 * the threshold. Vanilla survival reach is 3.0 blocks; the default
 * {@code max-distance} adds a small buffer to absorb latency and the fact that
 * we measure at packet receive time rather than the exact swing tick.
 */
public final class ReachCheck extends Check {

    public ReachCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.REACH, config);
    }

    /**
     * @param distance eye-to-hitbox distance in blocks, at attack time
     */
    public CheckResult inspect(double distance) {
        CheckConfig cfg = config();
        double max = cfg.get("max-distance", 3.05D);
        if (distance <= max) {
            return CheckResult.pass();
        }
        double over = distance - max;
        // Scale the violation with how egregious the reach is, but cap it so a
        // single glitchy sample can't jump straight to a punishment threshold.
        double amount = Math.min(1.0D + over * 2.0D, 5.0D);
        String detail = String.format(java.util.Locale.ROOT, "dist=%.2f max=%.2f", distance, max);
        return CheckResult.fail(amount, detail);
    }
}
