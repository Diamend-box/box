package com.diamend.anticheat.packet;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;

import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.check.combat.AimCheck;
import com.diamend.anticheat.check.combat.AutoClickerCheck;
import com.diamend.anticheat.check.combat.KillAuraCheck;
import com.diamend.anticheat.check.combat.ReachCheck;
import com.diamend.anticheat.exempt.ExemptionManager;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.util.ReachCalculator;
import com.diamend.anticheat.violation.ViolationManager;

/**
 * The single bridge between packetevents and the rest of the plugin.
 *
 * <p>Its whole job is to pull the primitives each check needs out of the
 * incoming packets, hand them to the (pure, testable) checks, and route any
 * failure through the {@link ViolationManager}. Deliberately thin: everything
 * that can be reasoned about and tested lives elsewhere, so the packet-library
 * specific surface is confined here.
 *
 * <p>Runs on packetevents' network threads. Reads of live Bukkit state (target
 * hitboxes, eye location) are inherently a tick-fresh snapshot; that is
 * acceptable for combat heuristics and is why borderline results carry small
 * violation amounts rather than instant punishment.
 */
public final class CombatPacketListener extends PacketListenerAbstract {

    private final PlayerDataManager players;
    private final ExemptionManager exemptions;
    private final ViolationManager violations;
    private final ReachCheck reachCheck;
    private final AutoClickerCheck autoClickerCheck;
    private final AimCheck aimCheck;
    private final KillAuraCheck killAuraCheck;

    public CombatPacketListener(PlayerDataManager players, ExemptionManager exemptions,
                                ViolationManager violations, ReachCheck reachCheck,
                                AutoClickerCheck autoClickerCheck, AimCheck aimCheck,
                                KillAuraCheck killAuraCheck) {
        super(PacketListenerPriority.NORMAL);
        this.players = players;
        this.exemptions = exemptions;
        this.violations = violations;
        this.reachCheck = reachCheck;
        this.autoClickerCheck = autoClickerCheck;
        this.aimCheck = aimCheck;
        this.killAuraCheck = killAuraCheck;
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

        if (exemptions.isExempt(player, data, now)) {
            // Still record swing/rotation state so buffers stay warm, but never flag.
            recordOnly(event, data);
            return;
        }

        Object type = event.getPacketType();
        if (type == PacketType.Play.Client.ANIMATION) {
            handleSwing(player, data, now);
        } else if (type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            handleRotation(player, data, event);
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            handleInteract(player, data, event, now);
        }
    }

    /** During exemptions we keep state fresh without running checks. */
    private void recordOnly(PacketReceiveEvent event, PlayerData data) {
        Object type = event.getPacketType();
        if (type == PacketType.Play.Client.ANIMATION) {
            data.recordSwing(System.currentTimeMillis());
        } else if (type == PacketType.Play.Client.PLAYER_ROTATION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.getLocation() != null) {
                data.recordRotation((float) flying.getLocation().getYaw(),
                        (float) flying.getLocation().getPitch());
            }
        }
    }

    private void handleSwing(Player player, PlayerData data, long now) {
        data.recordSwing(now);
        if (!autoClickerCheck.enabled()) {
            return;
        }
        CheckResult result = autoClickerCheck.inspect(data.clickTimes());
        violations.handle(player, data, CheckType.AUTOCLICKER, result, now);
    }

    private void handleRotation(Player player, PlayerData data, PacketReceiveEvent event) {
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        if (flying.getLocation() == null) {
            return;
        }
        float yaw = (float) flying.getLocation().getYaw();
        float pitch = (float) flying.getLocation().getPitch();
        boolean hadPrevious = data.recordRotation(yaw, pitch);
        if (!aimCheck.enabled() || !hadPrevious) {
            return;
        }
        CheckResult result = aimCheck.inspect(data.prevYawDelta(), data.lastYawDelta(), pitch);
        violations.handle(player, data, CheckType.AIM, result, System.currentTimeMillis());
    }

    private void handleInteract(Player player, PlayerData data, PacketReceiveEvent event, long now) {
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }
        int targetId = wrapper.getEntityId();
        Entity target = resolveEntity(player, targetId);

        // Snapshot state needed for killaura before we overwrite it.
        long msSinceSwing = now - data.lastSwing();
        int previousTarget = data.lastAttackedEntityId();
        long msSinceLastAttack = now - data.lastAttack();
        boolean rapidSwitch = previousTarget != -1 && previousTarget != targetId
                && msSinceLastAttack < killAuraCheck.multiTargetWindowMs();

        boolean cancel = false;

        if (target != null && reachCheck.enabled()) {
            double distance = eyeToHitbox(player, target);
            if (distance >= 0) {
                CheckResult reach = reachCheck.inspect(distance);
                cancel |= violations.handle(player, data, CheckType.REACH, reach, now);
            }
        }

        if (killAuraCheck.enabled()) {
            double angle = target != null ? hitAngle(player, target) : 0.0D;
            CheckResult aura = killAuraCheck.inspect(msSinceSwing, angle, rapidSwitch);
            cancel |= violations.handle(player, data, CheckType.KILLAURA, aura, now);
        }

        data.recordAttack(targetId, now);

        if (cancel) {
            event.setCancelled(true);
        }
    }

    private Entity resolveEntity(Player player, int entityId) {
        try {
            return SpigotReflectionUtil.getEntityById(player.getWorld(), entityId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Distance from the player's eye to the nearest point of the target hitbox, or -1 on failure. */
    private double eyeToHitbox(Player player, Entity target) {
        try {
            Vector eye = player.getEyeLocation().toVector();
            BoundingBox box = target.getBoundingBox();
            return ReachCalculator.distanceToBox(eye.getX(), eye.getY(), eye.getZ(),
                    box.getMinX(), box.getMinY(), box.getMinZ(),
                    box.getMaxX(), box.getMaxY(), box.getMaxZ());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** Angle (degrees) between where the player looks and the direction to the target centre. */
    private double hitAngle(Player player, Entity target) {
        try {
            Vector look = player.getEyeLocation().getDirection();
            Vector toTarget = target.getBoundingBox().getCenter()
                    .subtract(player.getEyeLocation().toVector());
            if (toTarget.lengthSquared() < 1.0e-6) {
                return 0.0D;
            }
            double angleRad = look.angle(toTarget);
            return Math.toDegrees(angleRad);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }
}
