package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown before the achievement list when categories are in use: a tab per
 * category plus an "All" entry. Selecting one opens the filtered list.
 */
public class CategoryMenu implements Menu {

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;

    private Inventory inventory;
    private List<String> categories = new ArrayList<>();

    public CategoryMenu(CustomAchievementsPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    @Override
    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    private void rebuild() {
        categories = plugin.getAchievementManager().categories();
        int rows = Math.max(3, (int) Math.ceil((categories.size() + 2) / 9.0) + 1);
        int size = Math.min(54, rows * 9);
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size, Text.parse("<dark_aqua><bold>Categories"));
        }
        inventory.clear();

        PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());

        // "All" entry first.
        inventory.setItem(0, Items.of(Material.NETHER_STAR, Text.item("<gold><bold>All Achievements"),
                progressLore(null, data)));
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            inventory.setItem(i + 1, Items.of(Material.CHEST, Text.item("<yellow>" + category),
                    progressLore(category, data)));
        }
    }

    private List<Component> progressLore(String category, PlayerData data) {
        int total = 0;
        int unlocked = 0;
        for (Achievement achievement : plugin.getAchievementManager().all()) {
            if (category != null && !category.equalsIgnoreCase(achievement.getCategory())) {
                continue;
            }
            total++;
            if (data.isCompleted(achievement.getId())) {
                unlocked++;
            }
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.item("<gray>Unlocked: <green>" + unlocked + "<gray>/<white>" + total));
        lore.add(Component.empty());
        lore.add(Text.item("<yellow>Click to open"));
        return lore;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 0) {
            new AchievementMenu(plugin, viewer, false, null).open(viewer);
        } else if (slot >= 1 && slot <= categories.size()) {
            new AchievementMenu(plugin, viewer, false, categories.get(slot - 1)).open(viewer);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
