package com.diamend.anticheat.check.world;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.util.MathUtil;

/**
 * Detects Scaffold — automated block-bridging that places blocks perfectly while
 * sprinting forward or looking away from the placement, with no human delay.
 *
 * <p>Two independent signals, each a pure decision here while the caller feeds
 * the primitives:
 * <ul>
 *   <li><b>Delay</b> — vanilla bridging has a natural, jittery rhythm. Placements
 *       spaced only a couple of milliseconds apart, or with almost no variation
 *       across a run, are automated.</li>
 *   <li><b>Raytrace face</b> — the angle between where the player is actually
 *       looking and the block face they claim to place against. A scaffold that
 *       bridges while staring straight ahead or at the sky produces an angle a
 *       human hand never could (see
 *       {@link com.diamend.anticheat.util.RotationUtil}).</li>
 * </ul>
 */
public final class ScaffoldCheck extends Check {

    public ScaffoldCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.SCAFFOLD, config);
    }

    /**
     * Timing signal.
     *
     * @param intervals recent inter-placement intervals (ms), oldest first
     */
    public CheckResult inspectDelay(long[] intervals) {
        if (intervals.length == 0) {
            return CheckResult.pass();
        }
        CheckConfig cfg = config();
        long instantMs = (long) cfg.get("instant-ms", 10);
        int minSamples = (int) cfg.get("delay-min-samples", 5);
        double minCov = cfg.get("delay-min-cov", 0.05D);
        double instantVl = cfg.get("instant-vl", 1.0D);
        double regularVl = cfg.get("regular-vl", 1.0D);

        long latest = intervals[intervals.length - 1];
        if (latest >= 0 && latest <= instantMs) {
            String detail = String.format(Locale.ROOT, "place interval=%dms", latest);
            return CheckResult.fail(instantVl, detail);
        }
        if (intervals.length >= minSamples) {
            double[] asDouble = new double[intervals.length];
            for (int i = 0; i < intervals.length; i++) {
                asDouble[i] = intervals[i];
            }
            double cov = MathUtil.coefficientOfVariation(asDouble);
            if (cov < minCov) {
                String detail = String.format(Locale.ROOT, "placement cov=%.3f (too regular)", cov);
                return CheckResult.fail(regularVl, detail);
            }
        }
        return CheckResult.pass();
    }

    /**
     * Geometry signal.
     *
     * @param angleDeg angle between look direction and the clicked block face
     */
    public CheckResult inspectAngle(double angleDeg) {
        CheckConfig cfg = config();
        double maxAngle = cfg.get("max-face-angle", 85.0D);
        double angleVl = cfg.get("angle-vl", 1.5D);
        if (angleDeg > maxAngle) {
            String detail = String.format(Locale.ROOT, "look-to-face angle=%.1f", angleDeg);
            return CheckResult.fail(angleVl, detail);
        }
        return CheckResult.pass();
    }
}
