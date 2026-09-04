package com.diamend.robobear.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * The plugin's configurable text, resolved once per reload.
 *
 * <p>Placeholders are angle-bracketed ({@code <challenge>}, {@code <time>}) and
 * substituted before MiniMessage parsing, so a message can colour around them.
 */
public class Messages {

    private final JavaPlugin plugin;
    private final Map<String, String> defaults = new HashMap<>();
    private String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        defaults.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("messages");
        if (section == null) {
            prefix = "";
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                defaults.put(key, value);
            }
        }
        prefix = defaults.getOrDefault("prefix", "");
    }

    /**
     * Text for keys added after a server's config.yml was written.
     *
     * <p>{@code saveDefaultConfig()} never rewrites a config that already
     * exists, so any message introduced in a later version is simply absent on
     * every server that upgraded rather than installed fresh. Without a fallback
     * here those players would be sent the key itself.
     */
    private static final Map<String, String> BUILT_IN = Map.of(
            "pass-received",
            "<green>You were given <white><pass></white> — <gray>/rb start<green> when you're ready.",
            "mobs-incoming",
            "<dark_red>Something's coming.");

    /** The raw configured string for a key, its built-in text, or the key itself. */
    public String raw(String key) {
        String configured = defaults.get(key);
        return configured != null ? configured : BUILT_IN.getOrDefault(key, key);
    }

    /** Builds a message, substituting {@code <name>}-style placeholders. */
    public Component get(String key, Object... replacements) {
        return Text.parse(fill(raw(key), replacements));
    }

    /** Builds a message with the configured prefix in front. */
    public Component prefixed(String key, Object... replacements) {
        return Text.parse(prefix + fill(raw(key), replacements));
    }

    /** Sends a prefixed message, unless the configured text is empty. */
    public void send(CommandSender target, String key, Object... replacements) {
        String body = raw(key);
        if (body.isEmpty()) {
            return;
        }
        target.sendMessage(Text.parse(prefix + fill(body, replacements)));
    }

    /**
     * Substitutes {@code key, value} pairs into a template. Values are inserted
     * verbatim, so a message can wrap one in its own colour tags.
     */
    private static String fill(String template, Object... replacements) {
        if (replacements == null || replacements.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String token = "<" + replacements[i] + ">";
            String value = String.valueOf(replacements[i + 1]);
            result = result.replace(token, value);
        }
        return result;
    }
}
