package com.diamend.anticheat.check.combat;

import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.util.AimAnalyzer;

/**
 * Looks for non-human rotation. Two signals:
 * <ul>
 *   <li><b>Impossible pitch</b> — a pitch outside [-90, 90] the vanilla client
 *       can never send; a hard, near-certain flag.</li>
 *   <li><b>GCD structure</b> — consecutive yaw deltas that share a suspiciously
 *       large common divisor, as produced by rotations computed in software.
 *       This is a soft heuristic, so the per-event violation is small and needs
 *       to recur before it reaches the alert threshold.</li>
 * </ul>
 */
public final class AimCheck extends Check {

    public AimCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.AIM, config);
    }

    /**
     * @param prevYawDelta the yaw change one rotation ago (degrees)
     * @param yawDelta     the most recent yaw change (degrees)
     * @param pitch        the current absolute pitch (degrees)
     */
    public CheckResult inspect(float prevYawDelta, float yawDelta, float pitch) {
        CheckConfig cfg = config();

        if (AimAnalyzer.isImpossiblePitch(pitch)) {
            return CheckResult.fail(cfg.get("impossible-pitch-vl", 5.0D),
                    String.format(java.util.Locale.ROOT, "pitch=%.1f", pitch));
        }

        double minMovement = cfg.get("min-yaw-delta", 0.6D);
        // Need two genuinely different, non-trivial turns to judge structure.
        if (Math.abs(yawDelta) < minMovement || Math.abs(prevYawDelta) < minMovement
                || Math.abs(yawDelta - prevYawDelta) < 1.0e-4F) {
            return CheckResult.pass();
        }
        long gcd = AimAnalyzer.rotationGcd(prevYawDelta, yawDelta);
        // gcd is in units of 1e-6 degrees; 0.02 degrees == 20000 units.
        long minGcd = (long) cfg.get("min-gcd-units", 20_000.0D);
        if (gcd >= minGcd) {
            return CheckResult.fail(cfg.get("gcd-vl", 0.5D),
                    "gcd=" + gcd);
        }
        return CheckResult.pass();
    }
}
