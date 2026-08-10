package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Routes inventory interactions to the {@link Menu} that owns them, and feeds
 * chat lines into the editor's pending prompts.
 */
public class GuiListener implements Listener {

    private final RoboBearPlugin plugin;

    public GuiListener(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            if (menu.cancelClick(event)) {
                event.setCancelled(true);
            }
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Only cancel drags for read-only menus; the reward editor permits
        // dragging items into its content area.
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu
                && menu.cancelClick(null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            menu.onClose();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = plugin.prompts().take(uuid);
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        // Back onto the main thread: the callback edits menus and config.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                callback.accept(null);
            } else {
                callback.accept(message);
            }
        });
    }
}
