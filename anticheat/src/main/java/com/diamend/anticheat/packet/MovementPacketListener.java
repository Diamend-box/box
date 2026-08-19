package com.diamend.anticheat.packet;

import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.check.movement.FlightCheck;
import com.diamend.anticheat.check.movement.NoFallCheck;
import com.diamend.anticheat.check.movement.SpeedCheck;
import com.diamend.anticheat.check.movement.TimerCheck;
import com.diamend.anticheat.check.movement.VehicleCheck;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.exempt.ExemptionManager;
import com.diamend.anticheat.player.MovementState;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.player.VehicleState;
import com.diamend.anticheat.util.PhysicsUtil;
import com.diamend.anticheat.violation.PunishmentMode;
import com.diamend.anticheat.violation.ViolationManager;
import com.diamend.anticheat.world.BlockCache;

/**
 * Bridges the client movement packets to the movement checks: Timer (every
 * flying packet), Flight/Speed/NoFall (position packets) and Vehicle (vehicle
 * move packets).
 *
 * <p>Runs on packetevents' Netty threads — off the main server thread. Ground
 * truth for "is the player actually on a block?" comes from the async-safe
 * {@link BlockCache} snapshot, never from the client's {@code onGround} flag or
 * a live world read. The only main-thread hop is to re-instate fall damage when
 * NoFall is enforced, and to cancel offending packets (handled by
 * {@link ViolationManager}).
 */
public final class MovementPacketListener extends PacketListenerAbstract {

    private final Plugin plugin;
    private final Supplier<AntiCheatConfig> config;
    private final PlayerDataManager players;
    private final ExemptionManager exemptions;
    private final ViolationManager violations;
    private final BlockCache blockCache;

    private final FlightCheck flightCheck;
    private final SpeedCheck speedCheck;
    private final NoFallCheck noFallCheck;
    private final TimerCheck timerCheck;
    private final VehicleCheck vehicleCheck;

    public MovementPacketListener(Plugin plugin, Supplier<AntiCheatConfig> config,
                                  PlayerDataManager players, ExemptionManager exemptions,
                                  ViolationManager violations, BlockCache blockCache,
                                  FlightCheck flightCheck, SpeedCheck speedCheck,
                                  NoFallCheck noFallCheck, TimerCheck timerCheck,
                                  VehicleCheck vehicleCheck) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.config = config;
        this.players = players;
        this.exemptions = exemptions;
        this.violations = violations;
        this.blockCache = blockCache;
        this.flightCheck = flightCheck;
        this.speedCheck = speedCheck;
        this.noFallCheck = noFallCheck;
        this.timerCheck = timerCheck;
        this.vehicleCheck = vehicleCheck;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean exempt = exemptions.isExempt(player, data, now);

        Object type = event.getPacketType();
        boolean isFlying = type == PacketType.Play.Client.PLAYER_FLYING
                || type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                || type == PacketType.Play.Client.PLAYER_ROTATION;

        if (isFlying) {
            handleTimer(player, data, event, now, exempt);
        }
        if (type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            handlePosition(player, data, event, now, exempt);
        } else if (type == PacketType.Play.Client.VEHICLE_MOVE) {
            handleVehicle(player, data, event, now, exempt);
        }
    }

    // ---- Timer -----------------------------------------------------------

    private void handleTimer(Player player, PlayerData data, PacketReceiveEvent event,
                             long now, boolean exempt) {
        double balance = data.timer().onFlyingPacket(now);
        if (exempt || !timerCheck.enabled()) {
            return;
        }
        CheckResult result = timerCheck.inspect(balance);
        if (result.failed()) {
            data.timer().relax(timerCheck.relaxTo());
        }
        if (violations.handle(player, data, CheckType.TIMER, result, now)) {
            event.setCancelled(true);
        }
    }

    // ---- Flight / Speed / NoFall ----------------------------------------

    private void handlePosition(Player player, PlayerData data, PacketReceiveEvent event,
                                long now, boolean exempt) {
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        if (flying.getLocation() == null) {
            return;
        }
        double x = flying.getLocation().getX();
        double y = flying.getLocation().getY();
        double z = flying.getLocation().getZ();
        boolean clientGround = flying.isOnGround();

        UUID world = player.getWorld().getUID();
        MovementState mv = data.movement();

        double prevMotionY = mv.lastMotionY();
        double prevX = mv.lastX();
        double prevZ = mv.lastZ();
        boolean hadPos = mv.hasPosition();

        boolean serverGround = blockCache.solidBelow(world, x, y, z);
        boolean airborne = !serverGround;
        double motionY = mv.updatePosition(x, y, z, airborne);

        // Fall-distance bookkeeping (server-authoritative, ignores the client).
        if (serverGround) {
            mv.markServerGround(now);
        } else if (motionY < 0) {
            mv.addFall(-motionY);
        }

        if (exempt || !hadPos) {
            mv.setLiftTicks(0);
            mv.setFastTicks(0);
            return;
        }

        boolean recentVelocity = now - mv.lastVelocityMs() < flightCheck.velocityGraceMs();
        boolean cancel = false;

        // States that legitimately break gravity: swimming, climbing, cobweb,
        // and levitation / slow-falling potions. Flight must ignore these.
        boolean nonGravityState = blockCache.inLiquidOrClimbable(world, x, y, z)
                || hasVerticalPotion(player);

        // Flight / Jetpack: sustained defiance of vertical physics while airborne.
        if (flightCheck.enabled() && airborne && !recentVelocity && !nonGravityState) {
            double excess = PhysicsUtil.verticalExcess(prevMotionY, motionY);
            int lift = excess > flightCheck.liftTolerance() ? mv.liftTicks() + 1 : 0;
            mv.setLiftTicks(lift);
            CheckResult result = flightCheck.inspect(excess, lift);
            cancel |= violations.handle(player, data, CheckType.FLIGHT, result, now);
        } else {
            mv.setLiftTicks(0);
        }

        // Speed: blatant horizontal overspeed sustained across ticks.
        if (speedCheck.enabled() && !recentVelocity) {
            double horizontal = Math.hypot(x - prevX, z - prevZ);
            int fast = horizontal > speedCheck.maxHorizontal() ? mv.fastTicks() + 1 : 0;
            mv.setFastTicks(fast);
            CheckResult result = speedCheck.inspect(horizontal, fast);
            cancel |= violations.handle(player, data, CheckType.SPEED, result, now);
        } else {
            mv.setFastTicks(0);
        }

        // NoFall: claimed ground that the world snapshot / physics contradict.
        if (noFallCheck.enabled()) {
            boolean groundKnown = blockCache.groundKnown(world, x, y, z);
            double fall = mv.serverFallDistance();
            CheckResult result = noFallCheck.inspect(clientGround, serverGround,
                    groundKnown, motionY, fall);
            if (result.failed() && isEnforcing()) {
                reinstateFall(player, (float) fall);
            }
            cancel |= violations.handle(player, data, CheckType.NOFALL, result, now);
        }

        if (cancel) {
            event.setCancelled(true);
        }
    }

    // ---- Vehicle (BoatFly) ----------------------------------------------

    private void handleVehicle(Player player, PlayerData data, PacketReceiveEvent event,
                               long now, boolean exempt) {
        if (!vehicleCheck.enabled()) {
            return;
        }
        WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
        double y = move.getPosition().getY();

        VehicleState vs = data.vehicle();
        double prevMotionY = vs.lastMotionY();
        // Vehicles: we can't easily block-cache under an arbitrary vehicle hull,
        // so treat "airborne" as "was already airborne or is rising"; the physics
        // excess test does the real work.
        double motionY = vs.update(y, true);

        if (exempt) {
            vs.setLiftTicks(0);
            return;
        }

        double excess = PhysicsUtil.vehicleVerticalExcess(prevMotionY, motionY);
        int lift = excess > vehicleCheck.liftTolerance() ? vs.liftTicks() + 1 : 0;
        vs.setLiftTicks(lift);
        CheckResult result = vehicleCheck.inspect(excess, lift);
        if (violations.handle(player, data, CheckType.VEHICLE, result, now)) {
            // Cancel the move and dismount the rider on the main thread.
            event.setCancelled(true);
            dismount(player);
        }
    }

    // ---- Main-thread effects --------------------------------------------

    private boolean isEnforcing() {
        AntiCheatConfig cfg = config.get();
        return cfg != null && cfg.mode() == PunishmentMode.ENFORCE;
    }

    private void reinstateFall(Player player, float fall) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getFallDistance() < fall) {
                online.setFallDistance(fall);
            }
        });
    }

    private void dismount(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getVehicle() != null) {
                online.leaveVehicle();
            }
        });
    }

    /**
     * Whether the player has a potion effect that legitimately alters vertical
     * motion. Read off the main thread, so guarded — any hiccup is treated as
     * "assume affected" (never flag) rather than risking a false positive.
     */
    private boolean hasVerticalPotion(Player player) {
        try {
            return player.hasPotionEffect(PotionEffectType.LEVITATION)
                    || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
