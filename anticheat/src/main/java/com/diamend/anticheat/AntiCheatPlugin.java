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
import com.diamend.anticheat.check.movement.FlightCheck;
import com.diamend.anticheat.check.movement.NoFallCheck;
import com.diamend.anticheat.check.movement.SpeedCheck;
import com.diamend.anticheat.check.movement.TimerCheck;
import com.diamend.anticheat.check.movement.VehicleCheck;
import com.diamend.anticheat.check.world.ScaffoldCheck;
import com.diamend.anticheat.command.AntiCheatCommand;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.exempt.ExemptionManager;
import com.diamend.anticheat.gui.AntiCheatGui;
import com.diamend.anticheat.gui.GuiListener;
import com.diamend.anticheat.listener.PlayerStateListener;
import com.diamend.anticheat.packet.CombatPacketListener;
import com.diamend.anticheat.packet.MovementPacketListener;
import com.diamend.anticheat.packet.WorldInteractionListener;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.protect.EntityCullingTask;
import com.diamend.anticheat.protect.HealthSpoofListener;
import com.diamend.anticheat.violation.PunishmentMode;
import com.diamend.anticheat.violation.ViolationManager;
import com.diamend.anticheat.world.BlockCache;

/**
 * Packet-level anticheat for Paper 1.21.4, aimed squarely at the blatant free
 * clients (Meteor, Wurst).
 *
 * <p>Everything hangs off packetevents' Netty pipeline rather than Bukkit's
 * movement/combat events, and all the maths — physics prediction, raytraces,
 * statistics — runs off the main thread. The main thread is only ever touched to
 * cancel a packet or carry out a punishment.
 *
 * <p>Coverage: Combat (Reach, AutoClicker, Aim, KillAura), Movement (Flight,
 * Speed, NoFall, Timer, Vehicle/BoatFly), World (Scaffold), plus passive
 * protections that starve client cheats of data (health-indicator masking and
 * optional line-of-sight entity culling for anti-ESP). A decaying violation
 * system feeds staff alerts, and an alert-only-by-default response mode can be
 * flipped to full enforcement from the GUI, config, or {@code /ac mode enforce}.
 */
public final class AntiCheatPlugin extends JavaPlugin {

    private volatile AntiCheatConfig config;

    private PlayerDataManager players;
    private ExemptionManager exemptions;
    private ViolationManager violations;
    private AlertManager alerts;
    private BlockCache blockCache;
    private EntityCullingTask culling;
    private AntiCheatGui gui;

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
        this.blockCache = new BlockCache();
        this.culling = new EntityCullingTask(this, this::config);
        this.gui = new AntiCheatGui(this, alerts);

        // --- Checks (pure decision logic, read config live) ---------------
        ReachCheck reachCheck = new ReachCheck(this::config);
        AutoClickerCheck autoClickerCheck = new AutoClickerCheck(this::config);
        AimCheck aimCheck = new AimCheck(this::config);
        KillAuraCheck killAuraCheck = new KillAuraCheck(this::config);
        FlightCheck flightCheck = new FlightCheck(this::config);
        SpeedCheck speedCheck = new SpeedCheck(this::config);
        NoFallCheck noFallCheck = new NoFallCheck(this::config);
        TimerCheck timerCheck = new TimerCheck(this::config);
        VehicleCheck vehicleCheck = new VehicleCheck(this::config);
        ScaffoldCheck scaffoldCheck = new ScaffoldCheck(this::config);

        // --- Packet listeners (Netty threads) -----------------------------
        var eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(new CombatPacketListener(
                players, exemptions, violations,
                reachCheck, autoClickerCheck, aimCheck, killAuraCheck));
        eventManager.registerListener(new MovementPacketListener(
                this, this::config, players, exemptions, violations, blockCache,
                flightCheck, speedCheck, noFallCheck, timerCheck, vehicleCheck));
        eventManager.registerListener(new WorldInteractionListener(
                players, exemptions, violations, scaffoldCheck));
        eventManager.registerListener(new HealthSpoofListener(this::config));
        PacketEvents.getAPI().init();
        this.packetEventsInitialised = true;

        // --- Bukkit listeners (lifecycle bookkeeping only) ----------------
        getServer().getPluginManager().registerEvents(
                new PlayerStateListener(players, alerts, culling), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gui), this);

        // --- Scheduled main-thread tasks ----------------------------------
        // Refresh the async-safe block snapshot every tick so the movement
        // checks always have ground truth to read off-thread.
        getServer().getScheduler().runTaskTimer(this,
                () -> blockCache.refresh(getServer().getOnlinePlayers()), 1L, 1L);
        // The occlusion sweep (anti-ESP) must read the world, so it runs here on
        // a throttled cadence; it self-disables when the protection is off.
        long cullEvery = Math.max(1L, config.cullingIntervalTicks());
        getServer().getScheduler().runTaskTimer(this, culling, cullEvery, cullEvery);

        // --- Command ------------------------------------------------------
        AntiCheatCommand command = new AntiCheatCommand(this, alerts, gui);
        if (getCommand("anticheat") != null) {
            getCommand("anticheat").setExecutor(command);
            getCommand("anticheat").setTabCompleter(command);
        }

        // Cover players already online (e.g. after a /reload).
        long now = System.currentTimeMillis();
        for (Player online : getServer().getOnlinePlayers()) {
            players.create(online.getUniqueId(), now);
        }

        getLogger().info("AntiCheat enabled in " + config.mode() + " mode ("
                + CheckSummary.of(config) + ").");
    }

    @Override
    public void onDisable() {
        if (culling != null) {
            try {
                culling.revealAll();
            } catch (Throwable ignored) {
                // Server may be mid-shutdown; ignore.
            }
        }
        if (blockCache != null) {
            blockCache.clear();
        }
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

    /** Persist a boolean at an arbitrary config path (used by the GUI toggles). */
    public void setConfigBoolean(String path, boolean value) {
        getConfig().set(path, value);
        saveConfig();
        reloadAntiCheat();
    }

    /** Tiny helper for the enable banner: how many checks are on. */
    private static final class CheckSummary {
        static String of(AntiCheatConfig cfg) {
            int on = 0;
            int total = 0;
            for (var type : com.diamend.anticheat.check.CheckType.values()) {
                total++;
                var cc = cfg.check(type);
                if (cc != null && cc.enabled()) {
                    on++;
                }
            }
            return on + "/" + total + " checks active";
        }
    }
}
