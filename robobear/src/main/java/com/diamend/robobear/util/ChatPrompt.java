package com.diamend.robobear.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One-shot "type it in chat" prompts for the editor.
 *
 * <p>A prompt is consumed by the first thing the player types. Typing
 * {@code cancel} delivers {@code null} to the callback, which every caller
 * treats as "leave it alone" — so a prompt is never a trap you have to satisfy
 * to get your chat back.
 */
public class ChatPrompt {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompt(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Asks the player for a line of text, closing whatever menu they're in. */
    public void ask(Player player, String question, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        player.closeInventory();
        player.sendMessage(Text.parse(question));
        player.sendMessage(Text.parse("<dark_gray>Type <white>cancel<dark_gray> to leave it unchanged."));
    }

    /** Takes and removes a player's pending prompt, or null if they have none. */
    public Consumer<String> take(UUID uuid) {
        return pending.remove(uuid);
    }

    public boolean isWaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
