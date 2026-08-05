package com.diamend.customachievements.listener;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.gui.Menu;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Routes inventory interactions to the {@link Menu} that owns them, and feeds
 * chat messages into the editor's pending text prompts.
 */
public class GuiListener implements Listener {

    private final CustomAchievementsPlugin plugin;

    public GuiListener(CustomAchievementsPlugin plugin) {
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
        // Only cancel drags for read-only menus; editable menus (reward items)
        // permit dragging items into their content area.
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu
                && menu.cancelClick(null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            menu.onClose();
        }
    }

    @EventHandler
    public void onPrepareAnvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof com.diamend.customachievements.gui.AnvilInputMenu anvil) {
            anvil.onPrepare(event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = plugin.getChatInput().take(uuid);
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                callback.accept(null);
            } else {
                callback.accept(message);
            }
        });
    }
}
