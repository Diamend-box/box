package com.diamend.anticheat.check;

import java.util.function.Supplier;

import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;

/**
 * Base class for every combat check. Holds the check's {@link CheckType} and a
 * live view of the current configuration, so a {@code /ac reload} is picked up
 * without recreating the checks.
 *
 * <p>Concrete checks expose their own {@code inspect(...)} method taking exactly
 * the primitives they need and returning a {@link CheckResult}. Keeping the
 * inputs primitive is deliberate: it makes the detection logic unit-testable
 * without a live server or a packet pipeline.
 */
public abstract class Check {

    private final CheckType type;
    private final Supplier<AntiCheatConfig> configSupplier;

    protected Check(CheckType type, Supplier<AntiCheatConfig> configSupplier) {
        this.type = type;
        this.configSupplier = configSupplier;
    }

    public CheckType type() {
        return type;
    }

    protected CheckConfig config() {
        return configSupplier.get().check(type);
    }

    /** Whether this check is currently enabled in config. */
    public boolean enabled() {
        CheckConfig cfg = config();
        return cfg != null && cfg.enabled();
    }
}
