package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Shared plumbing for RoboBear's menus: a lazily-built inventory, filler panes,
 * a back button and click feedback.
 */
public abstract class AbstractMenu implements Menu {

    protected final RoboBearPlugin plugin;
    private final int size;
    private final String title;
    private Inventory inventory;

    protected AbstractMenu(RoboBearPlugin plugin, int size, String title) {
        this.plugin = plugin;
        this.size = size;
        this.title = title;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size, Text.parse(title));
        }
        return inventory;
    }

    protected int size() {
        return size;
    }

    @Override
    public void open(Player player) {
        Inventory target = getInventory();
        target.clear();
        build(player);
        player.openInventory(target);
    }

    /** Fills the inventory for this viewer. Called every time it's opened. */
    protected abstract void build(Player player);

    /** Rebuilds in place, for a menu that changed under an open viewer. */
    protected void refresh(Player player) {
        Inventory target = getInventory();
        target.clear();
        build(player);
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < size) {
            getInventory().setItem(slot, item);
        }
    }

    /** Puts a decorative pane in every empty slot. */
    protected void fillEmpty(Material material) {
        ItemStack filler = Items.filler(material);
        Inventory target = getInventory();
        for (int slot = 0; slot < size; slot++) {
            ItemStack existing = target.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                target.setItem(slot, filler);
            }
        }
    }

    protected void backButton(int slot, String label) {
        set(slot, Items.text(Material.ARROW, "<yellow>« " + label, List.of()));
    }

    protected void closeButton(int slot) {
        set(slot, Items.text(Material.BARRIER, "<red>Close", List.of()));
    }

    protected void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 1.6f);
    }

    protected void deny(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f);
    }

    protected void play(Player player, Sound sound, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, 0.4f, pitch);
        } catch (Throwable ignored) {
            // A missing sound key should never break a menu.
        }
    }
}
