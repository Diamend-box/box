package com.diamend.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.check.combat.AimCheck;
import com.diamend.anticheat.check.combat.AutoClickerCheck;
import com.diamend.anticheat.check.combat.KillAuraCheck;
import com.diamend.anticheat.check.combat.ReachCheck;
import com.diamend.anticheat.command.AntiCheatCommand;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.exempt.ExemptionManager;
import com.diamend.anticheat.listener.PlayerStateListener;
import com.diamend.anticheat.packet.CombatPacketListener;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.violation.PunishmentMode;
import com.diamend.anticheat.violation.ViolationManager;

/**
 * Packet-level combat anticheat.
 *
 * <p>v1 ships four combat checks (Reach, AutoClicker, Aim, KillAura) built on
 * packetevents, a decaying violation-level system, latency/lag/state exemptions,
 * staff alerts, and a response mode that defaults to alert-only but can be
 * switched to full enforcement from config or {@code /ac mode enforce}.
 */
public final class AntiCheatPlugin extends JavaPlugin {

    private volatile AntiCheatConfig config;

    private PlayerDataManager players;
    private ExemptionManager exemptions;
    private ViolationManager violations;
    private AlertManager alerts;

    private boolean packetEventsInitialised;

    @Override
    public void onLoad() {
        // packetevents must be set up in onLoad so its netty handlers are in
        // place before any player can connect.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = AntiCheatConfig.load(getConfig());

        this.alerts = new AlertManager(getServer(), getLogger());
        this.players = new PlayerDataManager(config);
        this.exemptions = new ExemptionManager(getServer(), config);
        this.violations = new ViolationManager(this, alerts, config);

        ReachCheck reachCheck = new ReachCheck(this::config);
        AutoClickerCheck autoClickerCheck = new AutoClickerCheck(this::config);
        AimCheck aimCheck = new AimCheck(this::config);
        KillAuraCheck killAuraCheck = new KillAuraCheck(this::config);

        CombatPacketListener packetListener = new CombatPacketListener(
                players, exemptions, violations,
                reachCheck, autoClickerCheck, aimCheck, killAuraCheck);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        PacketEvents.getAPI().init();
        this.packetEventsInitialised = true;

        getServer().getPluginManager().registerEvents(
                new PlayerStateListener(players, alerts), this);

        AntiCheatCommand command = new AntiCheatCommand(this, alerts);
        if (getCommand("anticheat") != null) {
            getCommand("anticheat").setExecutor(command);
            getCommand("anticheat").setTabCompleter(command);
        }

        // Cover players already online (e.g. after a /reload).
        long now = System.currentTimeMillis();
        for (Player online : getServer().getOnlinePlayers()) {
            players.create(online.getUniqueId(), now);
        }

        getLogger().info("AntiCheat enabled in " + config.mode() + " mode.");
    }

    @Override
    public void onDisable() {
        if (packetEventsInitialised && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
        if (players != null) {
            players.clear();
        }
    }

    /** Current immutable config snapshot; the checks read this live. */
    public AntiCheatConfig config() {
        return config;
    }

    public PlayerDataManager players() {
        return players;
    }

    /** Re-read config.yml from disk and push the new snapshot everywhere. */
    public void reloadAntiCheat() {
        reloadConfig();
        this.config = AntiCheatConfig.load(getConfig());
        players.updateConfig(config);
        exemptions.updateConfig(config);
        violations.updateConfig(config);
        getLogger().info("AntiCheat reloaded; mode is " + config.mode() + ".");
    }

    /** Persist a new response mode to config.yml and apply it immediately. */
    public void setMode(PunishmentMode mode) {
        getConfig().set("mode", mode == PunishmentMode.ENFORCE ? "enforce" : "alert-only");
        saveConfig();
        reloadAntiCheat();
    }
}
