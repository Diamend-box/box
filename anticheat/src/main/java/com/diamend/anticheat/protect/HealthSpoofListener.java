package com.diamend.anticheat.protect;

import java.util.List;
import java.util.function.Supplier;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;

import com.diamend.anticheat.config.AntiCheatConfig;

/**
 * Anti health-indicator protection.
 *
 * <p>Clients like Meteor render other players' exact HP by reading the
 * {@code EntityMetadata} health field the server sends. This listener rewrites
 * that field to full health on the way out, so every remote player always
 * appears to be at 20.0 HP on a cheater's screen. The server's real health
 * value is untouched — only the copy being broadcast to <em>other</em> clients
 * is masked, and a player still sees their own true health.
 *
 * <p>Runs on packetevents' send pipeline (a Netty thread), never the main
 * thread. Index 9 is the LivingEntity health field in the 1.21 entity-data
 * protocol.
 */
public final class HealthSpoofListener extends PacketListenerAbstract {

    /** Protocol entity-data index of LivingEntity health on 1.21. */
    private static final int HEALTH_INDEX = 9;

    private final Supplier<AntiCheatConfig> config;

    public HealthSpoofListener(Supplier<AntiCheatConfig> config) {
        super(PacketListenerPriority.HIGH);
        this.config = config;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        AntiCheatConfig cfg = config.get();
        if (cfg == null || !cfg.healthSpoofEnabled()) {
            return;
        }
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
            return;
        }
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        try {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            int entityId = wrapper.getEntityId();
            // Never touch the viewer's own health bar.
            if (entityId == viewer.getEntityId()) {
                return;
            }
            List<EntityData> metadata = wrapper.getEntityMetadata();
            if (metadata == null || metadata.isEmpty()) {
                return;
            }
            boolean carriesHealth = false;
            for (EntityData data : metadata) {
                if (data.getIndex() == HEALTH_INDEX && data.getValue() instanceof Float) {
                    carriesHealth = true;
                    break;
                }
            }
            if (!carriesHealth) {
                return;
            }
            // Only mask other *players* (the ESP HP tags target players); leave
            // mob health alone so vanilla mob-health rendering still works.
            Entity target = resolve(viewer, entityId);
            if (!(target instanceof Player)) {
                return;
            }
            float shown = (float) cfg.healthSpoofValue();
            for (EntityData data : metadata) {
                if (data.getIndex() == HEALTH_INDEX && data.getValue() instanceof Float) {
                    data.setValue(shown);
                }
            }
            wrapper.setEntityMetadata(metadata);
        } catch (Throwable ignored) {
            // A malformed/unexpected metadata packet must never break the send.
        }
    }

    private Entity resolve(Player viewer, int entityId) {
        try {
            return SpigotReflectionUtil.getEntityById(viewer.getWorld(), entityId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
