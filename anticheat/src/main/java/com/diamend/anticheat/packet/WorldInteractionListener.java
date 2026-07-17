package com.diamend.anticheat.packet;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

import com.diamend.anticheat.check.CheckResult;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.check.world.ScaffoldCheck;
import com.diamend.anticheat.exempt.ExemptionManager;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.util.RotationUtil;
import com.diamend.anticheat.violation.ViolationManager;

/**
 * Scaffold detection driven by block-placement packets.
 *
 * <p>For each placement we measure two things the {@link ScaffoldCheck} judges:
 * the time since the previous placement (automation has an inhuman, uniform
 * rhythm) and the angle between where the player is looking and the block face
 * they claim to place against (bots bridge while facing the wrong way). All the
 * geometry is pure math on primitives pulled from the packet, so it runs on the
 * Netty thread without touching the main server thread; enforce mode cancels the
 * placement packet.
 */
public final class WorldInteractionListener extends PacketListenerAbstract {

    private final PlayerDataManager players;
    private final ExemptionManager exemptions;
    private final ViolationManager violations;
    private final ScaffoldCheck scaffoldCheck;

    public WorldInteractionListener(PlayerDataManager players, ExemptionManager exemptions,
                                    ViolationManager violations, ScaffoldCheck scaffoldCheck) {
        super(PacketListenerPriority.NORMAL);
        this.players = players;
        this.exemptions = exemptions;
        this.violations = violations;
        this.scaffoldCheck = scaffoldCheck;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();

        // Always record the timing so the rhythm history stays warm; only judge
        // when the player isn't exempt and the check is on.
        data.place().recordPlace(now);
        if (!scaffoldCheck.enabled() || exemptions.isExempt(player, data, now)) {
            return;
        }

        boolean cancel = false;

        CheckResult delay = scaffoldCheck.inspectDelay(data.place().intervals());
        cancel |= violations.handle(player, data, CheckType.SCAFFOLD, delay, now);

        double[] normal = null;
        Vector3i block = null;
        try {
            WrapperPlayClientPlayerBlockPlacement wrapper =
                    new WrapperPlayClientPlayerBlockPlacement(event);
            normal = normalOf(wrapper.getFace());
            block = wrapper.getBlockPosition();
        } catch (Throwable ignored) {
            // Unexpected placement packet shape; skip the geometry signal.
        }
        if (normal != null && block != null) {
            Location eye = player.getEyeLocation();
            double angle = RotationUtil.lookToFaceAngle(
                    eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(),
                    block.getX(), block.getY(), block.getZ(), normal);
            CheckResult angleResult = scaffoldCheck.inspectAngle(angle);
            cancel |= violations.handle(player, data, CheckType.SCAFFOLD, angleResult, now);
        }

        if (cancel) {
            event.setCancelled(true);
        }
    }

    /** Unit normal of a clicked face, or {@code null} if it isn't a cardinal face. */
    private double[] normalOf(BlockFace face) {
        if (face == null) {
            return null;
        }
        return switch (face.name()) {
            case "DOWN", "BOTTOM" -> new double[] { 0, -1, 0 };
            case "UP", "TOP" -> new double[] { 0, 1, 0 };
            case "NORTH" -> new double[] { 0, 0, -1 };
            case "SOUTH" -> new double[] { 0, 0, 1 };
            case "WEST" -> new double[] { -1, 0, 0 };
            case "EAST" -> new double[] { 1, 0, 0 };
            default -> null;
        };
    }
}
