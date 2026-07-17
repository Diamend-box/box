package com.diamend.anticheat.alert;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.diamend.anticheat.check.CheckType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Formats and delivers anticheat notifications to staff.
 *
 * <p>Two streams:
 * <ul>
 *   <li><b>Alerts</b> — sent once a check crosses its alert threshold, to every
 *       online player holding {@link #ALERTS_PERMISSION}.</li>
 *   <li><b>Verbose</b> — every single flag, sent only to staff who opted in via
 *       {@code /ac verbose}. Handy while tuning thresholds.</li>
 * </ul>
 * All sends must happen on the main server thread.
 */
public final class AlertManager {

    public static final String ALERTS_PERMISSION = "anticheat.alerts";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Server server;
    private final Logger logger;
    private final Set<UUID> verboseListeners = ConcurrentHashMap.newKeySet();

    public AlertManager(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /** Toggle verbose output for a staff member. Returns the new state. */
    public boolean toggleVerbose(UUID uuid) {
        if (verboseListeners.remove(uuid)) {
            return false;
        }
        verboseListeners.add(uuid);
        return true;
    }

    public boolean isVerbose(UUID uuid) {
        return verboseListeners.contains(uuid);
    }

    public void setVerbose(UUID uuid, boolean on) {
        if (on) {
            verboseListeners.add(uuid);
        } else {
            verboseListeners.remove(uuid);
        }
    }

    public void clearVerbose(UUID uuid) {
        verboseListeners.remove(uuid);
    }

    /** A threshold-crossing alert shown to all staff with the alerts permission. */
    public void sendAlert(String suspectName, CheckType check, double violationLevel, String detail) {
        Component message = MINI.deserialize(
                "<dark_gray>[<red>AC</red>]</dark_gray> <yellow><suspect></yellow> "
                        + "<gray>failed</gray> <white><check></white> "
                        + "<gray>(vl <red><vl></red>)</gray> <dark_gray><detail></dark_gray>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("suspect", suspectName),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("check", check.displayName()),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("vl", format(violationLevel)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("detail", detail == null ? "" : detail));
        for (Player online : server.getOnlinePlayers()) {
            if (online.hasPermission(ALERTS_PERMISSION)) {
                online.sendMessage(message);
            }
        }
        logger.info("[Alert] " + suspectName + " failed " + check.displayName()
                + " (vl " + format(violationLevel) + ") " + (detail == null ? "" : detail));
    }

    /** A per-flag verbose line shown only to opted-in staff. */
    public void sendVerbose(String suspectName, CheckType check, double violationLevel, String detail) {
        if (verboseListeners.isEmpty()) {
            return;
        }
        Component message = MINI.deserialize(
                "<dark_gray>[<red>AC/V</red>]</dark_gray> <gray><suspect> · <check> · vl <vl> · <detail></gray>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("suspect", suspectName),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("check", check.displayName()),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("vl", format(violationLevel)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("detail", detail == null ? "" : detail));
        for (Player online : server.getOnlinePlayers()) {
            if (verboseListeners.contains(online.getUniqueId())
                    && online.hasPermission(ALERTS_PERMISSION)) {
                online.sendMessage(message);
            }
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
