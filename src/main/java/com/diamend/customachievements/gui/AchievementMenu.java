package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The paginated grid that shows every achievement together with whether the
 * viewing player has unlocked it and, for progress achievements, how far along
 * they are. In admin mode clicking an achievement opens the editor and a
 * "Create New" button is shown.
 */
public class AchievementMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_CREATE = 46;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final boolean admin;

    private Inventory inventory;
    private int page;
    private List<Achievement> cachedList = new ArrayList<>();

    public AchievementMenu(CustomAchievementsPlugin plugin, Player viewer, boolean admin) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.admin = admin;
    }

    @Override
    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    private void rebuild() {
        cachedList = plugin.getAchievementManager().asList();
        Component title = admin
                ? Text.parse("<dark_red><bold>Manage Achievements")
                : Text.parse("<dark_aqua><bold>Achievements");
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, title);
        }
        inventory.clear();

        PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());

        int totalPages = Math.max(1, (int) Math.ceil(cachedList.size() / (double) CONTENT_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * CONTENT_SIZE;
        int end = Math.min(start + CONTENT_SIZE, cachedList.size());

        for (int i = start; i < end; i++) {
            Achievement achievement = cachedList.get(i);
            inventory.setItem(i - start, render(achievement, data));
        }

        // Navigation row background.
        ItemStack filler = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, Items.of(Material.ARROW,
                    Text.item("<yellow>« Previous Page")));
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, Items.of(Material.ARROW,
                    Text.item("<yellow>Next Page »")));
        }

        int unlocked = 0;
        for (Achievement achievement : cachedList) {
            if (data.isCompleted(achievement.getId())) {
                unlocked++;
            }
        }
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Text.item("<gray>Unlocked: <green>" + unlocked + "<gray>/<white>" + cachedList.size()));
        infoLore.add(Text.item("<gray>Page: <white>" + (page + 1) + "<gray>/<white>" + totalPages));
        if (admin) {
            infoLore.add(Component.empty());
            infoLore.add(Text.item("<yellow>Left-click an achievement to edit it."));
        }
        inventory.setItem(SLOT_INFO, Items.of(Material.BOOK,
                Text.item("<gold><bold>Achievement Progress"), infoLore));

        if (admin) {
            List<Component> createLore = new ArrayList<>();
            createLore.add(Text.item("<gray>Build a brand new achievement"));
            createLore.add(Text.item("<gray>using the editor menu."));
            inventory.setItem(SLOT_CREATE, Items.of(Material.LIME_CONCRETE,
                    Text.item("<green><bold>+ Create New Achievement"), createLore, true));
        }
    }

    private ItemStack render(Achievement achievement, PlayerData data) {
        boolean completed = data.isCompleted(achievement.getId());
        List<Component> lore = new ArrayList<>();
        for (String line : achievement.getDescription()) {
            lore.add(Text.item(line));
        }
        lore.add(Component.empty());

        List<Requirement> requirements = achievement.getRequirements();
        if (completed) {
            lore.add(Text.item("<green>✔ Unlocked"));
        } else if (requirements.size() == 1) {
            renderRequirementLine(lore, achievement, requirements.get(0), 0, data, false);
        } else {
            lore.add(Text.item("<gray>Objectives:"));
            for (int i = 0; i < requirements.size(); i++) {
                renderRequirementLine(lore, achievement, requirements.get(i), i, data, true);
            }
        }

        if (admin) {
            lore.add(Component.empty());
            lore.add(Text.item("<dark_gray>id: " + achievement.getId()));
            lore.add(Text.item("<dark_gray>objectives: " + requirements.size()));
            lore.add(Component.empty());
            lore.add(Text.item("<yellow>Click to edit"));
        }

        Material icon = achievement.getIcon() != null ? achievement.getIcon() : Material.NETHER_STAR;
        return Items.of(icon, Text.item(achievement.getDisplayName()), lore, completed);
    }

    private void renderRequirementLine(List<Component> lore, Achievement achievement, Requirement requirement,
                                       int index, PlayerData data, boolean multi) {
        int required = requirement.requiredAmount();
        int current = Math.min(data.getProgress(PlayerData.requirementKey(achievement.getId(), index)), required);
        boolean done = current >= required;
        if (multi) {
            String mark = done ? "<green>✔ " : "<gray>☐ ";
            String progress = requirement.getTrigger().isProgress() && required > 1
                    ? " <dark_gray>(" + current + "/" + required + ")"
                    : "";
            lore.add(Text.item(mark + "<gray>" + requirement.describe() + progress));
        } else if (requirement.getTrigger().isProgress()) {
            lore.add(Text.item("<gray>Progress: <yellow>" + current + "<gray>/<yellow>" + required));
            lore.add(Text.item(progressBar(current, required)));
        } else if (done) {
            lore.add(Text.item("<green>✔ Unlocked"));
        } else {
            lore.add(Text.item("<red>✖ Locked"));
        }
    }

    private String progressBar(int current, int required) {
        int length = 20;
        int filled = required <= 0 ? length : (int) Math.round((current / (double) required) * length);
        filled = Math.max(0, Math.min(length, filled));
        StringBuilder bar = new StringBuilder("<dark_gray>[<green>");
        for (int i = 0; i < length; i++) {
            if (i == filled) {
                bar.append("<gray>");
            }
            bar.append('|');
        }
        bar.append("<dark_gray>]");
        return bar.toString();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int totalPages = Math.max(1, (int) Math.ceil(cachedList.size() / (double) CONTENT_SIZE));
        if (slot == SLOT_PREV) {
            if (page > 0) {
                page--;
                rebuild();
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page < totalPages - 1) {
                page++;
                rebuild();
            }
            return;
        }
        if (admin && slot == SLOT_CREATE) {
            Achievement draft = new Achievement(suggestId());
            new EditorMenu(plugin, viewer, draft, true).open(viewer);
            return;
        }
        // Clicking an achievement tile.
        if (admin && slot >= 0 && slot < CONTENT_SIZE) {
            int index = page * CONTENT_SIZE + slot;
            if (index < cachedList.size()) {
                Achievement achievement = cachedList.get(index);
                new EditorMenu(plugin, viewer, achievement.copy(), false).open(viewer);
            }
        }
    }

    private String suggestId() {
        String base = "achievement";
        int n = 1;
        while (plugin.getAchievementManager().exists(base + "_" + n)) {
            n++;
        }
        return base + "_" + n;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
