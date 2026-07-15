package com.diamend.customachievements.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks players who are currently being asked to type a value in chat (used
 * by the achievement editor for free-text fields). The registered callback is
 * invoked with the typed text, or {@code null} when the player types "cancel".
 */
public class ChatInputManager {

    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public void await(UUID uuid, Consumer<String> callback) {
        pending.put(uuid, callback);
    }

    public boolean isAwaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /** Removes and returns the pending callback for a player, or null if none. */
    public Consumer<String> take(UUID uuid) {
        return pending.remove(uuid);
    }

    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }
}
