package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An editable menu: staff drop items into the top area and they become the
 * achievement's reward items. Contents are captured when the menu closes.
 */
public class RewardItemsMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    private static final int SLOT_DONE = 49;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final Achievement draft;
    private final EditorMenu parent;

    private Inventory inventory;

    public RewardItemsMenu(CustomAchievementsPlugin plugin, Player viewer, Achievement draft, EditorMenu parent) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.draft = draft;
        this.parent = parent;
    }

    @Override
    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_green>Reward Items"));
            for (ItemStack item : draft.getRewardItems()) {
                inventory.addItem(item.clone());
            }
        }
        // Control row.
        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_DONE, Items.of(Material.EMERALD, Text.item("<green><bold>Done"),
                List.of(Text.item("<gray>Place reward items above."),
                        Text.item("<gray>They're saved when you close this menu."))));
        player.openInventory(inventory);
    }

    @Override
    public boolean cancelClick(InventoryClickEvent event) {
        if (event == null) {
            return false; // allow drags into the content area
        }
        int raw = event.getRawSlot();
        return raw >= CONTENT_SIZE && raw < SIZE; // lock the control row only
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() == SLOT_DONE) {
            viewer.closeInventory(); // triggers onClose, which captures + returns
        }
    }

    @Override
    public void onClose() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        draft.getRewardItems().clear();
        draft.getRewardItems().addAll(items);
        if (viewer.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> parent.open(viewer));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
