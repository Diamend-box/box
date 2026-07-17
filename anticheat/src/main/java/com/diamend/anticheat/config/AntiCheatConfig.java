package com.diamend.anticheat.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.violation.PunishmentAction;
import com.diamend.anticheat.violation.PunishmentMode;

/**
 * Immutable snapshot of config.yml. Rebuilt on {@code /ac reload}. Parsing all
 * lives here so the rest of the plugin deals in typed values, and so the
 * parsing rules can be exercised without a live server (a
 * {@link FileConfiguration} is trivial to build in a test).
 */
public final class AntiCheatConfig {

    private final PunishmentMode mode;
    private final double decayPerSecond;
    private final long loginGraceMillis;
    private final long teleportGraceMillis;
    private final int maxPing;
    private final double minTps;
    private final boolean verboseByDefault;
    private final Map<CheckType, CheckConfig> checks;

    // --- Passive protections ---------------------------------------------
    private final boolean healthSpoofEnabled;
    private final double healthSpoofValue;
    private final boolean cullingEnabled;
    private final double cullingMaxDistance;
    private final double cullingMinDistance;
    private final long cullingIntervalTicks;

    private AntiCheatConfig(PunishmentMode mode, double decayPerSecond,
                            long loginGraceMillis, long teleportGraceMillis,
                            int maxPing, double minTps, boolean verboseByDefault,
                            Map<CheckType, CheckConfig> checks,
                            boolean healthSpoofEnabled, double healthSpoofValue,
                            boolean cullingEnabled, double cullingMaxDistance,
                            double cullingMinDistance, long cullingIntervalTicks) {
        this.mode = mode;
        this.decayPerSecond = decayPerSecond;
        this.loginGraceMillis = loginGraceMillis;
        this.teleportGraceMillis = teleportGraceMillis;
        this.maxPing = maxPing;
        this.minTps = minTps;
        this.verboseByDefault = verboseByDefault;
        this.checks = checks;
        this.healthSpoofEnabled = healthSpoofEnabled;
        this.healthSpoofValue = healthSpoofValue;
        this.cullingEnabled = cullingEnabled;
        this.cullingMaxDistance = cullingMaxDistance;
        this.cullingMinDistance = cullingMinDistance;
        this.cullingIntervalTicks = cullingIntervalTicks;
    }

    public PunishmentMode mode() {
        return mode;
    }

    public double decayPerSecond() {
        return decayPerSecond;
    }

    public long loginGraceMillis() {
        return loginGraceMillis;
    }

    public long teleportGraceMillis() {
        return teleportGraceMillis;
    }

    /** Skip checks for players whose ping is above this (ms). 0 disables the exemption. */
    public int maxPing() {
        return maxPing;
    }

    /** Skip checks while the server TPS is below this value. */
    public double minTps() {
        return minTps;
    }

    public boolean verboseByDefault() {
        return verboseByDefault;
    }

    public CheckConfig check(CheckType type) {
        return checks.get(type);
    }

    // --- Passive protection accessors ------------------------------------

    /** Mask other players' health to full in outgoing EntityMetadata. */
    public boolean healthSpoofEnabled() {
        return healthSpoofEnabled;
    }

    /** The health value shown to other clients (default 20.0 = full). */
    public double healthSpoofValue() {
        return healthSpoofValue;
    }

    /** Hide out-of-line-of-sight players so ESP has nothing to draw. */
    public boolean cullingEnabled() {
        return cullingEnabled;
    }

    /** Only cull targets within this many blocks (beyond it, always visible). */
    public double cullingMaxDistance() {
        return cullingMaxDistance;
    }

    /** Never cull targets closer than this (avoids pop-in when turning). */
    public double cullingMinDistance() {
        return cullingMinDistance;
    }

    /** How often the culling sweep runs, in server ticks. */
    public long cullingIntervalTicks() {
        return cullingIntervalTicks;
    }

    /** Build a snapshot from a loaded {@link FileConfiguration}. */
    public static AntiCheatConfig load(FileConfiguration cfg) {
        PunishmentMode mode = PunishmentMode.fromConfig(cfg.getString("mode", "alert-only"));
        double decay = cfg.getDouble("violations.decay-per-second", 0.05D);
        long loginGrace = cfg.getLong("exemptions.login-grace-ms", 3000L);
        long teleportGrace = cfg.getLong("exemptions.teleport-grace-ms", 1500L);
        int maxPing = cfg.getInt("exemptions.max-ping", 400);
        double minTps = cfg.getDouble("exemptions.min-tps", 16.0D);
        boolean verboseDefault = cfg.getBoolean("alerts.verbose-by-default", false);

        Map<CheckType, CheckConfig> checks = new EnumMap<>(CheckType.class);
        ConfigurationSection checksSection = cfg.getConfigurationSection("checks");
        for (CheckType type : CheckType.values()) {
            checks.put(type, loadCheck(checksSection, type));
        }

        boolean healthSpoof = cfg.getBoolean("protections.health-indicators.enabled", true);
        double healthValue = cfg.getDouble("protections.health-indicators.shown-health", 20.0D);
        boolean culling = cfg.getBoolean("protections.anti-esp.enabled", false);
        double cullMax = cfg.getDouble("protections.anti-esp.max-distance", 64.0D);
        double cullMin = cfg.getDouble("protections.anti-esp.min-distance", 6.0D);
        long cullInterval = cfg.getLong("protections.anti-esp.interval-ticks", 4L);

        return new AntiCheatConfig(mode, decay, loginGrace, teleportGrace,
                maxPing, minTps, verboseDefault, checks,
                healthSpoof, healthValue, culling, cullMax, cullMin, cullInterval);
    }

    private static CheckConfig loadCheck(ConfigurationSection checksSection, CheckType type) {
        ConfigurationSection section = checksSection == null
                ? null
                : checksSection.getConfigurationSection(type.configKey());
        if (section == null) {
            // Absent section -> check disabled with empty settings.
            return new CheckConfig(false, Double.MAX_VALUE,
                    new LinkedHashMap<>(), new ArrayList<>());
        }
        boolean enabled = section.getBoolean("enabled", true);
        double alertThreshold = section.getDouble("alert-threshold", 5.0D);

        Map<String, Double> settings = new LinkedHashMap<>();
        ConfigurationSection settingsSection = section.getConfigurationSection("settings");
        if (settingsSection != null) {
            for (String key : settingsSection.getKeys(false)) {
                if (settingsSection.isDouble(key) || settingsSection.isInt(key)
                        || settingsSection.isLong(key)) {
                    settings.put(key.toLowerCase(Locale.ROOT), settingsSection.getDouble(key));
                }
            }
        }

        List<PunishmentAction> actions = new ArrayList<>();
        for (String line : section.getStringList("actions")) {
            PunishmentAction action = PunishmentAction.parse(line);
            if (action != null) {
                actions.add(action);
            }
        }
        return new CheckConfig(enabled, alertThreshold, settings, actions);
    }
}
