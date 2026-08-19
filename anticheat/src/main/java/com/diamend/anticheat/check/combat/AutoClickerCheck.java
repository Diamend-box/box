package com.diamend.anticheat.check.combat;

import java.util.function.Supplier;

import com.diamend.anticheat.check.Check;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.util.CpsAnalyzer;

/**
 * Detects autoclickers two ways:
 * <ul>
 *   <li><b>Rate</b> — sustained clicks-per-second above a human ceiling.</li>
 *   <li><b>Regularity</b> — a coefficient of variation of the click intervals
 *       below what a human hand produces. Many autoclickers hold a nearly
 *       constant interval, which shows up as near-zero variation even at
 *       otherwise plausible click rates. This is the harder signal to fake.</li>
 * </ul>
 */
public final class AutoClickerCheck extends Check {

    public AutoClickerCheck(Supplier<AntiCheatConfig> config) {
        super(CheckType.AUTOCLICKER, config);
    }

    /**
     * @param clickTimes recent click timestamps in ms, oldest first
     */
    public CheckResult inspect(long[] clickTimes) {
        CheckConfig cfg = config();
        double maxCps = cfg.get("max-cps", 16.0D);
        double minCov = cfg.get("min-cov", 0.08D);
        int minSamples = (int) cfg.get("min-samples", 10);

        CpsAnalyzer.Result result = CpsAnalyzer.analyze(clickTimes);
        if (result.sampleCount() < minSamples) {
            return CheckResult.pass();
        }
        if (result.cps() > maxCps) {
            return CheckResult.fail(1.0D,
                    String.format(java.util.Locale.ROOT, "cps=%.1f max=%.1f", result.cps(), maxCps));
        }
        // Only treat "too regular" as suspicious when the player is actually
        // clicking fast enough for it to matter (half the ceiling), so slow,
        // deliberate clicking never trips it.
        if (result.cps() >= maxCps / 2.0D && result.coefficientOfVariation() < minCov) {
            return CheckResult.fail(1.0D, String.format(java.util.Locale.ROOT,
                    "cov=%.3f min=%.3f cps=%.1f", result.coefficientOfVariation(), minCov, result.cps()));
        }
        return CheckResult.pass();
    }
}
