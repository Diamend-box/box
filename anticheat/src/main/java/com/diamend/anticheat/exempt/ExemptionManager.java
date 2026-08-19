package com.diamend.anticheat.exempt;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.player.PlayerData;

/**
 * Decides whether a player should be skipped by the combat checks right now.
 *
 * <p>Exemptions are what keep a combat anticheat from crying wolf: teleports,
 * lag spikes, high latency, elytra flight and the like all produce movement or
 * timing that looks abnormal but isn't cheating. When in doubt the anticheat
 * stays quiet.
 */
public final class ExemptionManager {

    public static final String BYPASS_PERMISSION = "anticheat.bypass";

    private final Server server;
    private volatile AntiCheatConfig config;

    public ExemptionManager(Server server, AntiCheatConfig config) {
        this.server = server;
        this.config = config;
    }

    public void updateConfig(AntiCheatConfig config) {
        this.config = config;
    }

    /**
     * @return the reason this player is currently exempt, or {@code null} if
     *         the checks should run for them.
     */
    public ExemptReason exemptReason(Player player, PlayerData data, long now) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return ExemptReason.BYPASS_PERMISSION;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return ExemptReason.GAMEMODE;
        }
        if (player.isDead() || player.getHealth() <= 0.0D) {
            return ExemptReason.DEAD;
        }
        if (player.isGliding()) {
            return ExemptReason.GLIDING;
        }
        if (player.isRiptiding()) {
            return ExemptReason.RIPTIDE;
        }
        if (player.isInsideVehicle()) {
            return ExemptReason.VEHICLE;
        }
        AntiCheatConfig cfg = config;
        if (now - data.joinTime() < cfg.loginGraceMillis()) {
            return ExemptReason.LOGIN_GRACE;
        }
        if (now - data.lastTeleport() < cfg.teleportGraceMillis()) {
            return ExemptReason.RECENT_TELEPORT;
        }
        if (cfg.maxPing() > 0 && player.getPing() > cfg.maxPing()) {
            return ExemptReason.HIGH_PING;
        }
        if (isServerLagging(cfg.minTps())) {
            return ExemptReason.SERVER_LAG;
        }
        return null;
    }

    public boolean isExempt(Player player, PlayerData data, long now) {
        return exemptReason(player, data, now) != null;
    }

    private boolean isServerLagging(double minTps) {
        try {
            double[] tps = server.getTPS();
            return tps.length > 0 && tps[0] < minTps;
        } catch (NoSuchMethodError | UnsupportedOperationException ex) {
            // Non-Paper server without getTPS(); treat as not lagging.
            return false;
        }
    }
}
