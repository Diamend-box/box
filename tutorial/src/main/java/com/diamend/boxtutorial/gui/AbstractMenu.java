package com.diamend.boxtutorial.gui;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Sounds;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Shared plumbing for the tutorial's menus: a lazily-built inventory, filler
 * panes, a close button and click feedback.
 */
public abstract class AbstractMenu implements Menu {

    protected final BoxTutorialPlugin plugin;
    private final int size;
    private final String title;
    private Inventory inventory;

    protected AbstractMenu(BoxTutorialPlugin plugin, int size, String title) {
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

    @Override
    public void open(Player player) {
        Inventory target = getInventory();
        target.clear();
        build(player);
        player.openInventory(target);
    }

    /** Fills the inventory for this viewer. Called every time it's opened. */
    protected abstract void build(Player player);

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
        set(slot, Items.text(Material.ARROW, "<yellow>« " + label, List.of(), false));
    }

    protected void closeButton(int slot) {
        set(slot, Items.text(Material.BARRIER, "<red>Close", List.of(), false));
    }

    protected void click(Player player) {
        Sounds.play(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
    }

    /** Opens another menu on the next tick — safe from inside a click handler. */
    protected void openLater(Player player, Menu menu) {
        plugin.getServer().getScheduler().runTask(plugin, () -> menu.open(player));
    }

    protected int size() {
        return size;
    }
}
