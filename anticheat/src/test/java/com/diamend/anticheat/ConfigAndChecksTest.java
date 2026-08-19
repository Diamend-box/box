package com.diamend.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.check.combat.AimCheck;
import com.diamend.anticheat.check.combat.AutoClickerCheck;
import com.diamend.anticheat.check.combat.KillAuraCheck;
import com.diamend.anticheat.check.combat.ReachCheck;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.violation.PunishmentMode;

/**
 * Exercises config parsing and the check decision logic together, using a
 * hand-built {@link YamlConfiguration} (no running server needed).
 */
class ConfigAndChecksTest {

    private AntiCheatConfig sampleConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mode", "enforce");
        yaml.set("violations.decay-per-second", 0.05D);
        yaml.set("exemptions.login-grace-ms", 3000L);
        yaml.set("exemptions.teleport-grace-ms", 1500L);
        yaml.set("exemptions.max-ping", 400);
        yaml.set("exemptions.min-tps", 16.0D);

        yaml.set("checks.reach.enabled", true);
        yaml.set("checks.reach.alert-threshold", 5.0D);
        yaml.set("checks.reach.settings.max-distance", 3.05D);
        yaml.set("checks.reach.actions", List.of("10 cancel", "25 kick Reaching"));

        yaml.set("checks.autoclicker.enabled", true);
        yaml.set("checks.autoclicker.settings.max-cps", 16.0D);
        yaml.set("checks.autoclicker.settings.min-cov", 0.08D);
        yaml.set("checks.autoclicker.settings.min-samples", 10);

        yaml.set("checks.aim.enabled", true);
        yaml.set("checks.aim.settings.impossible-pitch-vl", 5.0D);
        yaml.set("checks.aim.settings.min-yaw-delta", 0.6D);
        yaml.set("checks.aim.settings.min-gcd-units", 20000.0D);
        yaml.set("checks.aim.settings.gcd-vl", 0.5D);

        yaml.set("checks.killaura.enabled", true);
        yaml.set("checks.killaura.settings.max-hit-angle", 80.0D);
        yaml.set("checks.killaura.settings.no-swing-ms", 1000.0D);
        yaml.set("checks.killaura.settings.multi-target-ms", 150.0D);

        return AntiCheatConfig.load(yaml);
    }

    @Test
    void topLevelConfigParsed() {
        AntiCheatConfig cfg = sampleConfig();
        assertEquals(PunishmentMode.ENFORCE, cfg.mode());
        assertEquals(0.05D, cfg.decayPerSecond(), 1.0e-9);
        assertEquals(3000L, cfg.loginGraceMillis());
        assertEquals(1500L, cfg.teleportGraceMillis());
        assertEquals(400, cfg.maxPing());
        assertEquals(16.0D, cfg.minTps(), 1.0e-9);
    }

    @Test
    void checkActionsParsed() {
        AntiCheatConfig cfg = sampleConfig();
        assertEquals(2, cfg.check(CheckType.REACH).actions().size());
        assertTrue(cfg.check(CheckType.REACH).enabled());
    }

    @Test
    void missingCheckSectionDisablesIt() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mode", "alert-only");
        AntiCheatConfig cfg = AntiCheatConfig.load(yaml);
        assertFalse(cfg.check(CheckType.REACH).enabled());
    }

    @Test
    void reachCheckHonoursThreshold() {
        AntiCheatConfig cfg = sampleConfig();
        ReachCheck reach = new ReachCheck(() -> cfg);
        assertTrue(reach.inspect(3.5D).failed());
        assertFalse(reach.inspect(3.0D).failed());
    }

    @Test
    void autoClickerFlagsHighCps() {
        AntiCheatConfig cfg = sampleConfig();
        AutoClickerCheck check = new AutoClickerCheck(() -> cfg);
        long[] fast = new long[12];
        for (int i = 0; i < fast.length; i++) {
            fast[i] = i * 40L; // 25 cps
        }
        assertTrue(check.inspect(fast).failed());
    }

    @Test
    void autoClickerIgnoresTooFewSamples() {
        AntiCheatConfig cfg = sampleConfig();
        AutoClickerCheck check = new AutoClickerCheck(() -> cfg);
        long[] few = {0, 40, 80};
        assertFalse(check.inspect(few).failed());
    }

    @Test
    void aimFlagsImpossiblePitchAndSyntheticGcd() {
        AntiCheatConfig cfg = sampleConfig();
        AimCheck aim = new AimCheck(() -> cfg);
        assertTrue(aim.inspect(0.0F, 0.0F, 100.0F).failed());   // impossible pitch
        assertTrue(aim.inspect(2.0F, 4.0F, 10.0F).failed());    // synthetic gcd
        assertFalse(aim.inspect(1.234F, 2.717F, 10.0F).failed()); // human turn
    }

    @Test
    void killAuraSignals() {
        AntiCheatConfig cfg = sampleConfig();
        KillAuraCheck aura = new KillAuraCheck(() -> cfg);
        assertTrue(aura.inspect(100L, 90.0D, false).failed());  // bad angle
        assertTrue(aura.inspect(2000L, 10.0D, false).failed()); // no swing
        assertTrue(aura.inspect(100L, 10.0D, true).failed());   // multi-target
        assertFalse(aura.inspect(100L, 10.0D, false).failed()); // clean hit
        assertEquals(150L, aura.multiTargetWindowMs());
    }
}
