package com.diamend.anticheat.violation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.player.PlayerData;

/**
 * Central place where a check's {@link CheckResult} becomes a consequence.
 *
 * <p>It updates the player's violation level, fires verbose/alert messages, and
 * — only in {@link PunishmentMode#ENFORCE} — carries out the configured
 * punishment actions. This is the single spot that honours the alert-only vs.
 * enforce toggle, so no individual check has to know which mode it is in.
 *
 * <p>Called from packetevents network threads. Violation-level maths is thread
 * safe; every Bukkit-side effect (messages, commands, kicks, bans) is bounced
 * onto the main thread. The one thing that must be decided synchronously is
 * whether to cancel the offending packet, which is returned to the caller.
 */
public final class ViolationManager {

    private final Plugin plugin;
    private final AlertManager alerts;
    private volatile AntiCheatConfig config;

    public ViolationManager(Plugin plugin, AlertManager alerts, AntiCheatConfig config) {
        this.plugin = plugin;
        this.alerts = alerts;
        this.config = config;
    }

    public void updateConfig(AntiCheatConfig config) {
        this.config = config;
    }

    /**
     * Process a check result for a player.
     *
     * @return {@code true} if the offending packet/event should be cancelled
     *         (only ever true in {@link PunishmentMode#ENFORCE}).
     */
    public boolean handle(Player player, PlayerData data, CheckType type,
                          CheckResult result, long now) {
        if (!result.failed()) {
            return false;
        }
        AntiCheatConfig cfg = config;
        CheckConfig checkCfg = cfg.check(type);
        if (checkCfg == null || !checkCfg.enabled()) {
            return false;
        }

        double previous = data.violations().level(type, now);
        double current = data.violations().add(type, result.amount(), now);

        String name = player.getName();
        String detail = result.detail();

        // Verbose: every flag. Alert: only once at/over the alert threshold.
        boolean crossedAlert = previous < checkCfg.alertThreshold()
                && current >= checkCfg.alertThreshold();
        runOnMain(() -> {
            alerts.sendVerbose(name, type, current, detail);
            if (crossedAlert) {
                alerts.sendAlert(name, type, current, detail);
            }
        });

        boolean enforce = cfg.mode() == PunishmentMode.ENFORCE;
        boolean cancel = false;
        if (enforce) {
            for (PunishmentAction action : checkCfg.actions()) {
                boolean thresholdMet = current >= action.threshold();
                boolean justCrossed = previous < action.threshold() && thresholdMet;
                if (action.type() == PunishmentAction.Type.CANCEL) {
                    // Cancel for as long as the player sits at/above the threshold.
                    if (thresholdMet) {
                        cancel = true;
                    }
                } else if (justCrossed) {
                    // One-shot actions fire once, on the tick the threshold is crossed.
                    execute(player, action, type);
                }
            }
        }
        return cancel;
    }

    private void execute(Player player, PunishmentAction action, CheckType type) {
        final String playerName = player.getName();
        final java.util.UUID uuid = player.getUniqueId();
        runOnMain(() -> {
            switch (action.type()) {
                case COMMAND -> {
                    String cmd = action.argument().replace("%player%", playerName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                case KICK -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        String reason = action.argument().isBlank()
                                ? "Unfair advantage detected (" + type.displayName() + ")"
                                : action.argument();
                        online.kick(net.kyori.adventure.text.Component.text(reason));
                    }
                }
                case BAN -> {
                    String reason = action.argument().isBlank()
                            ? "Unfair advantage detected (" + type.displayName() + ")"
                            : action.argument();
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                            .addBan(playerName, reason, null, "AntiCheat");
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        online.kick(net.kyori.adventure.text.Component.text(reason));
                    }
                }
                case CANCEL -> {
                    // Handled synchronously in handle(); nothing to do here.
                }
            }
        });
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
