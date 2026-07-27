package com.diamend.boxcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Config-backed chat messages.
 *
 * <p>Placeholders are written {@code <like_this>} and substituted as raw text
 * before the string is parsed, so a value that itself contains colour codes
 * (a node's display name, say) renders properly.
 */
public class Messages {

    private final Plugin plugin;

    public Messages(Plugin plugin) {
        this.plugin = plugin;
    }

    public String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    /** Raw (unparsed) message from config with placeholders already replaced. */
    public String raw(String path, Object... placeholders) {
        String message = plugin.getConfig().getString("messages." + path, "");
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace("<" + placeholders[i] + ">", String.valueOf(placeholders[i + 1]));
        }
        return message;
    }

    public Component get(String path, Object... placeholders) {
        return Text.parse(prefix() + raw(path, placeholders));
    }

    /** Sends a configured message, skipping it entirely when set to "". */
    public void send(CommandSender target, String path, Object... placeholders) {
        String message = raw(path, placeholders);
        if (message.isBlank()) {
            return;
        }
        target.sendMessage(Text.parse(prefix() + message));
    }

    /** Sends a literal string (already containing any placeholders) with the prefix. */
    public void sendLiteral(CommandSender target, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        target.sendMessage(Text.parse(prefix() + message));
    }

    /** Sends a literal string with no prefix. */
    public void sendPlain(CommandSender target, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        target.sendMessage(Text.parse(message));
    }

    /** {@code ""} for 1, {@code "s"} otherwise — for "<amount> point<plural>". */
    public static String plural(long amount) {
        return amount == 1 ? "" : "s";
    }
}
