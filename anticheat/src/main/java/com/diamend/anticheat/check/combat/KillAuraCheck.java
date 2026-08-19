package com.diamend.anticheat.check.combat;

import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * Behavioural killaura detection. Combines three classic signals, evaluated on
 * each attack the player lands:
 * <ul>
 *   <li><b>Hit angle</b> — landing a hit on a target well outside the direction
 *       the player is facing.</li>
 *   <li><b>No swing</b> — dealing a hit with no recent swing animation, as some
 *       auras attack without animating the arm.</li>
 *   <li><b>Multi-target</b> — striking a different entity again within a few
 *       milliseconds, i.e. sweeping several targets faster than a human could
 *       re-aim.</li>
 * </ul>
 *
 * <p>KillAura is the fuzziest of the combat checks, so the defaults are
 * deliberately conservative — high angle tolerance and modest violation
 * amounts — to protect legitimate PvP from false positives.
 */
public final class KillAuraCheck extends Check {

    public KillAuraCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.KILLAURA, config);
    }

    /**
     * @param msSinceSwing   time since the player's last swing animation (ms)
     * @param hitAngleDeg    angle between where the player is looking and the
     *                       direction to the target's centre (degrees)
     * @param rapidSwitch    true if this hit is on a different entity than the
     *                       previous one, landed within the multi-target window
     */
    public CheckResult inspect(long msSinceSwing, double hitAngleDeg, boolean rapidSwitch) {
        CheckConfig cfg = config();

        double maxAngle = cfg.get("max-hit-angle", 80.0D);
        if (hitAngleDeg > maxAngle) {
            return CheckResult.fail(cfg.get("angle-vl", 1.0D),
                    String.format(java.util.Locale.ROOT, "angle=%.1f max=%.1f", hitAngleDeg, maxAngle));
        }

        long noSwingMs = (long) cfg.get("no-swing-ms", 1000.0D);
        if (msSinceSwing > noSwingMs) {
            return CheckResult.fail(cfg.get("no-swing-vl", 1.0D),
                    "no-swing " + msSinceSwing + "ms");
        }

        if (rapidSwitch) {
            return CheckResult.fail(cfg.get("multi-target-vl", 1.0D), "multi-target");
        }

        return CheckResult.pass();
    }

    /** The window (ms) within which hitting a second distinct entity is "rapid". */
    public long multiTargetWindowMs() {
        return (long) config().get("multi-target-ms", 150.0D);
    }
}
