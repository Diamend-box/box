package com.diamend.boxcore.util;

import com.diamend.boxcore.BoxCorePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asks a question in chat and hands the answer back to whoever asked.
 *
 * <p>Menus can capture anything you can hold, but some things — a name, a
 * permission node, a line of description — have to be typed. This is the one
 * place that waits for typing, so every editor in the plugin asks the same way
 * and answers the same way.
 *
 * <p>Chat arrives off the main thread. The answer is always handed back on the
 * main thread, because every caller uses it to touch the world or reopen a menu.
 */
public class ChatPrompt implements Listener {

    /** How long an unanswered question stays open. */
    private static final long TIMEOUT_MILLIS = 120_000L;

    private static final String CANCEL_WORD = "cancel";

    private final BoxCorePlugin plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    private record Pending(Consumer<String> onAnswer, Runnable onCancel, long expiresAt) {
    }

    public ChatPrompt(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    public void ask(Player player, String question, Consumer<String> onAnswer) {
        ask(player, question, onAnswer, null);
    }

    /**
     * Asks a question, closing whatever menu the asker is looking at.
     *
     * @param onCancel run instead of {@code onAnswer} if they cancel — usually
     *                 reopening the menu they came from
     */
    public void ask(Player player, String question, Consumer<String> onAnswer, Runnable onCancel) {
        if (player == null || onAnswer == null) {
            return;
        }
        // Chat is behind an open inventory screen; a question nobody can read is
        // just a menu that stopped responding.
        player.closeInventory();
        waiting.put(player.getUniqueId(),
                new Pending(onAnswer, onCancel, System.currentTimeMillis() + TIMEOUT_MILLIS));
        plugin.messages().sendLiteral(player, question);
        plugin.messages().sendPlain(player,
                "  <dark_gray>Type it in chat, or <white>cancel<dark_gray> to leave it alone.");
    }

    public boolean isWaiting(Player player) {
        return player != null && live(player.getUniqueId()) != null;
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            waiting.remove(uuid);
        }
    }

    /** The open question for this player, dropping it if it has lapsed. */
    private Pending live(UUID uuid) {
        Pending pending = waiting.get(uuid);
        if (pending == null) {
            return null;
        }
        if (System.currentTimeMillis() > pending.expiresAt()) {
            // Lapsed questions disappear quietly. Eating a chat message an hour
            // later because someone once opened a menu would be worse than
            // forgetting the question.
            waiting.remove(uuid);
            return null;
        }
        return pending;
    }

    // ------------------------------------------------------------------
    // Answering
    // ------------------------------------------------------------------

    /**
     * Feeds an answer in.
     *
     * @return whether anyone was waiting for one — the caller cancels the chat
     *         message only when they were
     */
    public boolean deliver(Player player, String text) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        Pending pending = live(uuid);
        if (pending == null) {
            return false;
        }
        waiting.remove(uuid);

        String answer = text == null ? "" : text.trim();
        boolean cancelled = answer.isEmpty() || answer.equalsIgnoreCase(CANCEL_WORD);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (cancelled) {
                plugin.messages().sendLiteral(player, "<gray>Left as it was.");
                if (pending.onCancel() != null) {
                    pending.onCancel().run();
                }
                return;
            }
            pending.onAnswer().accept(answer);
        });
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (deliver(event.getPlayer(), text)) {
            // It was an answer, not something to say to the server.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }
}
