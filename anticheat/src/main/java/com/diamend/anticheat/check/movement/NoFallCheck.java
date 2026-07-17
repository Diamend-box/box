package com.diamend.anticheat.check.movement;

import java.util.Locale;
import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * Catches NoFall, which avoids fall damage by claiming {@code onGround=true}
 * while still in mid-air so the server resets the player's fall distance.
 *
 * <p>The plugin never trusts the client's ground flag. Instead the caller
 * computes ground truth from {@link com.diamend.anticheat.world.BlockCache}
 * (is there actually a solid block under the player?) and tracks fall distance
 * itself. Two independent signals are flagged here:
 * <ul>
 *   <li>the client claims to be standing while our block cache proves there is
 *       no ground beneath it and it has fallen a meaningful distance; and</li>
 *   <li>the client claims to be standing while still moving sharply downward —
 *       physically impossible regardless of the cache.</li>
 * </ul>
 * When it fires, the listener also declines to reset the server-side fall
 * distance, so vanilla fall damage still lands.
 */
public final class NoFallCheck extends Check {

    public NoFallCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.NOFALL, config);
    }

    /**
     * @param clientGround the {@code onGround} boolean from the packet
     * @param serverGround whether a solid block was actually found beneath
     * @param groundKnown  whether the block cache had data to answer with
     * @param motionY      observed vertical velocity this tick
     * @param fallDistance server-tracked fall distance (blocks)
     */
    public CheckResult inspect(boolean clientGround, boolean serverGround,
                               boolean groundKnown, double motionY, double fallDistance) {
        if (!clientGround || serverGround) {
            // Either the client admits it's airborne (NoFall never does), or it
            // really is on the ground. Nothing to see.
            return CheckResult.pass();
        }
        CheckConfig cfg = config();
        double minFall = cfg.get("min-fall", 2.0D);
        double fallingThreshold = cfg.get("falling-threshold", -0.4D);
        double vl = cfg.get("vl", 2.0D);

        // Physically impossible: "standing" while dropping fast. Cache-independent.
        if (motionY <= fallingThreshold) {
            String detail = String.format(Locale.ROOT,
                    "onGround claimed while motionY=%.3f", motionY);
            return CheckResult.fail(vl, detail);
        }
        // Provable via the world snapshot: no ground under a claimed-standing,
        // meaningfully-fallen player.
        if (groundKnown && fallDistance >= minFall) {
            String detail = String.format(Locale.ROOT,
                    "onGround claimed, no block below, fall=%.1f", fallDistance);
            return CheckResult.fail(vl, detail);
        }
        return CheckResult.pass();
    }
}
