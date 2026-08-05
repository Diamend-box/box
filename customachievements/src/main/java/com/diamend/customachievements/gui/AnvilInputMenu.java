package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Text entry through an anvil's rename field instead of chat. Because nothing is
 * ever typed into chat, chat-bridge plugins (e.g. DiscordSRV) can't relay it.
 * Type in the rename box, then click the result slot to confirm.
 */
public class AnvilInputMenu implements Menu {

    private static final int SLOT_RESULT = 2;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final String title;
    private final Consumer<String> onDone;
    private final Runnable onCancel;

    private Inventory inventory;
    private boolean finished;

    public AnvilInputMenu(CustomAchievementsPlugin plugin, Player viewer, String title,
                          Consumer<String> onDone, Runnable onCancel) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.title = title;
        this.onDone = onDone;
        this.onCancel = onCancel;
    }

    @Override
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, InventoryType.ANVIL, Text.parse("<dark_green>" + title));
        // The left slot must hold a named item for the rename field to work.
        inventory.setItem(0, Items.of(Material.PAPER, Text.item("<white>...")));
        player.openInventory(inventory);
    }

    /** Called by the listener when the rename text changes; makes the result clickable. */
    public void onPrepare(PrepareAnvilEvent event) {
        String text = renameText();
        event.setResult(Items.of(Material.PAPER,
                Text.item(text == null || text.isBlank() ? "<gray>Type, then click here" : "<green>" + text),
                java.util.List.of(Text.item("<yellow>Click to confirm"))));
    }

    private String renameText() {
        if (inventory instanceof AnvilInventory anvil) {
            return anvil.getRenameText();
        }
        return null;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() != SLOT_RESULT) {
            return; // clicks are cancelled by cancelClick; nothing else to do
        }
        String text = renameText();
        finished = true;
        viewer.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> onDone.accept(text));
    }

    @Override
    public void onClose() {
        if (!finished) {
            Bukkit.getScheduler().runTask(plugin, onCancel);
        }
    }

    @Override
    public boolean cancelClick(InventoryClickEvent event) {
        return true; // never let items move in/out of the anvil
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
