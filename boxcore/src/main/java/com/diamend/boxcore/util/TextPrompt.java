package com.diamend.boxcore.util;

import com.diamend.boxcore.BoxCorePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Asks a question and hands the answer back to whoever asked.
 *
 * <p>Menus can capture anything you can hold, but some things — a name, a
 * permission node, a line of description — have to be typed. This is the one
 * place that waits for typing, so every editor in the plugin asks the same way
 * and answers the same way.
 *
 * <p>The answer is typed into an <strong>anvil rename box</strong>, not into
 * chat. That is not a style preference. The first playtest of the chat version
 * confirmed exactly what it risked: every answer staff typed was relayed
 * straight to Discord by the chat bridge, permission nodes included. Cancelling
 * the chat event cannot fix that — a bridge listening at the same priority has
 * already seen the message, and there is no priority low enough to guarantee
 * otherwise. Text that never becomes a chat message cannot be relayed by
 * anything.
 *
 * <p>Chat survives as a fallback, used only when the anvil cannot be opened.
 * Losing the ability to edit anything in game would be a worse failure than the
 * leak, so a server where the anvil screen is unavailable still gets working
 * editors — and is told out loud that the answer will be visible.
 *
 * <p>The answer is always handed back on the main thread, because every caller
 * uses it to touch the world or reopen a menu.
 */
public class TextPrompt implements Listener {

    /** How long an unanswered question stays open. */
    private static final long TIMEOUT_MILLIS = 120_000L;

    private static final String CANCEL_WORD = "cancel";

    /** What the box holds when there is no current value to edit. */
    private static final String EMPTY_SEED = "...";

    /** The anvil's rename box will not take more than this. */
    public static final int MAX_LENGTH = 50;

    private final BoxCorePlugin plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    /**
     * A question someone is currently being asked.
     *
     * @param view the anvil they are typing into, or null when this fell back
     *             to chat — which is also what tells the two paths apart
     */
    private record Pending(Consumer<String> onAnswer,
                           Runnable onCancel,
                           long expiresAt,
                           InventoryView view) {

        boolean viaAnvil() {
            return view != null;
        }
    }

    public TextPrompt(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Asking
    // ------------------------------------------------------------------

    public void ask(Player player, String question, Consumer<String> onAnswer) {
        ask(player, question, "", onAnswer, null);
    }

    public void ask(Player player, String question, Consumer<String> onAnswer, Runnable onCancel) {
        ask(player, question, "", onAnswer, onCancel);
    }

    /**
     * Asks a question, closing whatever menu the asker is looking at.
     *
     * @param seed     what the box starts out holding — the current value, so
     *                 changing one word doesn't mean retyping the whole line
     * @param onCancel run instead of {@code onAnswer} if they back out, usually
     *                 reopening the menu they came from
     */
    public void ask(Player player, String question, String seed,
                    Consumer<String> onAnswer, Runnable onCancel) {
        if (player == null || onAnswer == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + TIMEOUT_MILLIS;
        // Also say it in chat. The anvil's title carries the question while the
        // screen is open, but chat is where they can scroll back to it once it
        // closes, and it is all the fallback path has.
        plugin.messages().sendLiteral(player, question);

        InventoryView view = openAnvil(player, question, seed);
        if (view != null) {
            waiting.put(player.getUniqueId(), new Pending(onAnswer, onCancel, expiresAt, view));
            return;
        }
        // Chat is behind an open inventory screen; a question nobody can read is
        // just a menu that stopped responding.
        player.closeInventory();
        waiting.put(player.getUniqueId(), new Pending(onAnswer, onCancel, expiresAt, null));
        plugin.messages().sendPlain(player,
                "  <dark_gray>Type it in chat, or <white>cancel<dark_gray> to leave it alone.");
        plugin.messages().sendPlain(player,
                "  <red>Careful: chat here may be relayed off the server.");
    }

    /**
     * Opens the anvil, or returns null to say the caller should use chat.
     *
     * <p>Everything here is best-effort on purpose. The anvil screen is the
     * nice path, not the load-bearing one, so a server implementation that will
     * not hand one over falls back rather than leaving an editor that cannot be
     * used at all.
     */
    private InventoryView openAnvil(Player player, String question, String seed) {
        try {
            // The question goes in the title because an open inventory screen
            // hides chat: a question sent to chat a tick earlier is not on
            // screen at the moment it needs to be read.
            AnvilView view = MenuType.ANVIL.create(player, Text.parse(question));
            view.setRepairCost(0);
            view.getTopInventory().setItem(0, label(seed));
            player.openInventory(view);
            return view;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The item the rename box reads its starting text from.
     *
     * <p>The name has to be plain text: the anvil renders it as literal
     * characters, so a MiniMessage tag would show as itself and end up inside
     * the answer.
     */
    private ItemStack label(String seed) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String text = seed == null || seed.isBlank() ? EMPTY_SEED : seed;
            meta.displayName(Component.text(
                    text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text));
            item.setItemMeta(meta);
        }
        return item;
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
     * @return whether anyone was waiting for one — the chat fallback cancels
     *         the message only when they were
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
        plugin.getServer().getScheduler().runTask(plugin, () -> finish(player, pending, answer, cancelled));
        return true;
    }

    /** The one place an answer turns into a callback, whichever path it came by. */
    private void finish(Player player, Pending pending, String answer, boolean cancelled) {
        if (cancelled) {
            plugin.messages().sendLiteral(player, "<gray>Left as it was.");
            if (pending.onCancel() != null) {
                pending.onCancel().run();
            }
            return;
        }
        pending.onAnswer().accept(answer);
    }

    // ------------------------------------------------------------------
    // The anvil
    // ------------------------------------------------------------------

    /**
     * Puts something in the output slot so there is a button to press.
     *
     * <p>Vanilla leaves that slot empty when a rename costs nothing or changes
     * nothing, which would make a perfectly good answer unclickable.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (pendingFor(event.getView()) == null) {
            return;
        }
        event.setResult(label(renameText(event.getView())));
        if (event.getView() instanceof AnvilView anvil) {
            anvil.setRepairCost(0);
        }
    }

    /**
     * Takes the answer when they click the output slot.
     *
     * <p>Every click in this screen is cancelled. The anvil is being used as a
     * text box, and an item carried out of it — the result, or the name tag in
     * the input slot — would be an item minted from nothing.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Pending pending = pendingFor(event.getView());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return;
        }
        String answer = renameText(event.getView());
        String trimmed = answer == null ? "" : answer.trim();
        boolean cancelled = trimmed.isEmpty()
                || trimmed.equals(EMPTY_SEED)
                || trimmed.equalsIgnoreCase(CANCEL_WORD);

        // Drop the question before closing, or the close handler reads this as
        // backing out and runs the cancel path over the top of the answer.
        waiting.remove(player.getUniqueId());
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> finish(player, pending, trimmed, cancelled));
    }

    /** Closing the anvil without clicking the result means backing out. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Pending pending = pendingFor(event.getView());
        if (pending == null) {
            return;
        }
        waiting.remove(player.getUniqueId());
        // The close is still resolving; opening a menu from inside it is the
        // one thing Bukkit reliably refuses to do.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                finish(player, pending, "", true);
            }
        });
    }

    /** The pending question this view belongs to, or null. */
    private Pending pendingFor(InventoryView view) {
        if (view == null || !(view.getPlayer() instanceof Player player)) {
            return null;
        }
        Pending pending = live(player.getUniqueId());
        return pending != null && pending.viaAnvil() && view.equals(pending.view())
                ? pending
                : null;
    }

    private String renameText(InventoryView view) {
        return view instanceof AnvilView anvil ? anvil.getRenameText() : null;
    }

    // ------------------------------------------------------------------
    // The chat fallback
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Pending pending = live(event.getPlayer().getUniqueId());
        if (pending == null || pending.viaAnvil()) {
            // Someone typing while an anvil is open is talking, not answering.
            return;
        }
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
