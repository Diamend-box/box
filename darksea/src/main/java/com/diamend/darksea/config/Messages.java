package com.diamend.darksea.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * MiniMessage-based message dispatch. Raw strings come from the config's
 * {@code messages:} section; {@code {placeholder}} tokens are substituted
 * before parsing, so placeholder values may themselves contain MiniMessage
 * (zone display names do).
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private volatile Map<String, String> raw;

    public Messages(Map<String, String> raw) {
        this.raw = raw;
    }

    public void reload(Map<String, String> newRaw) {
        this.raw = newRaw;
    }

    /** Chat message with the configured prefix. Placeholders as key,value pairs. */
    public void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(MM.deserialize(raw.getOrDefault("prefix", "") + fill(key, placeholders)));
    }

    /** Chat message without the prefix (status screens, list entries). */
    public void sendBare(CommandSender to, String key, String... placeholders) {
        to.sendMessage(component(key, placeholders));
    }

    public void actionBar(Player to, String key, String... placeholders) {
        to.sendActionBar(component(key, placeholders));
    }

    /** Deserializes an already-built MiniMessage string (the naval HUD line). */
    public Component raw(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public Component component(String key, String... placeholders) {
        return MM.deserialize(fill(key, placeholders));
    }

    private String fill(String key, String... placeholders) {
        String text = raw.getOrDefault(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return text;
    }
}
