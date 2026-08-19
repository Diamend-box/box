package com.diamend.anticheat.check.movement;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * A deliberately coarse horizontal-speed check.
 *
 * <p>Vanilla sprinting is ~0.28 blocks/tick, with brief sprint-jump bursts a
 * little higher; ice, speed potions, riptide and pearls push it higher still.
 * Rather than model every legitimate boost, this check sets a generous ceiling
 * that only blatant Speed/horizontal-Flight (which routinely move 1+ blocks per
 * tick) can breach, and requires the breach to persist for several ticks before
 * flagging. The caller supplies the per-tick horizontal distance and maintains
 * the streak; exemptions (vehicles, elytra, recent knockback) are applied
 * upstream so this stays a pure threshold decision.
 */
public final class SpeedCheck extends Check {

    public SpeedCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.SPEED, config);
    }

    /**
     * @param horizontal per-tick horizontal distance (blocks)
     * @param fastTicks  consecutive ticks over the cap, maintained by the caller
     */
    public CheckResult inspect(double horizontal, int fastTicks) {
        CheckConfig cfg = config();
        int minFastTicks = (int) cfg.get("min-fast-ticks", 4);
        double vl = cfg.get("vl", 1.0D);
        if (fastTicks >= minFastTicks) {
            String detail = String.format(Locale.ROOT, "h=%.3f/tick x%d", horizontal, fastTicks);
            return CheckResult.fail(vl, detail);
        }
        return CheckResult.pass();
    }

    /** The configured per-tick horizontal ceiling; read by the caller to build the streak. */
    public double maxHorizontal() {
        return config().get("max-horizontal", 0.7D);
    }

    /** Ignore speed for this long after the server granted velocity (knockback). */
    public long velocityGraceMs() {
        return (long) config().get("velocity-grace-ms", 1200D);
    }
}
