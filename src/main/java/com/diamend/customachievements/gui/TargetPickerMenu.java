package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
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
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A paginated, searchable grid for picking a target value (material, entity,
 * skill, dimension, ...). Selecting an option (or "ANY") invokes the pick
 * callback; the back button returns to the caller.
 */
public class TargetPickerMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_SEARCH = 47;
    private static final int SLOT_ANY = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_BACK = 50;
    private static final int SLOT_NEXT = 53;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final String title;
    private final List<TargetOption> all;
    private final Consumer<String> onPick;
    private final Runnable onBack;

    private Inventory inventory;
    private String filter = "";
    private int page;
    private List<TargetOption> filtered = new ArrayList<>();

    public TargetPickerMenu(CustomAchievementsPlugin plugin, Player viewer, String title,
                            List<TargetOption> options, Consumer<String> onPick, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.title = title;
        this.all = options;
        this.onPick = onPick;
        this.onBack = onBack;
    }

    @Override
    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    private void rebuild() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_aqua>" + title));
        }
        inventory.clear();

        filtered = new ArrayList<>();
        String needle = filter.toLowerCase(Locale.ROOT);
        for (TargetOption option : all) {
            if (needle.isEmpty() || option.value().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(option);
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) CONTENT_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * CONTENT_SIZE;
        int end = Math.min(start + CONTENT_SIZE, filtered.size());
        for (int i = start; i < end; i++) {
            TargetOption option = filtered.get(i);
            inventory.setItem(i - start, Items.of(option.icon(),
                    Text.item("<yellow>" + option.value()),
                    List.of(Text.item("<gray>Click to select"))));
        }

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, Items.of(Material.ARROW, Text.item("<yellow>« Previous")));
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, Items.of(Material.ARROW, Text.item("<yellow>Next »")));
        }
        List<Component> searchLore = new ArrayList<>();
        searchLore.add(Text.item(filter.isEmpty() ? "<gray>No filter" : "<gray>Filter: <white>" + filter));
        searchLore.add(Text.item("<yellow>Click to type a search"));
        searchLore.add(Text.item("<yellow>Right-click to clear"));
        inventory.setItem(SLOT_SEARCH, Items.of(Material.OAK_SIGN, Text.item("<aqua>Search"), searchLore));
        inventory.setItem(SLOT_ANY, Items.of(Material.NETHER_STAR, Text.item("<light_purple>ANY"),
                List.of(Text.item("<gray>Match any target"))));
        inventory.setItem(SLOT_INFO, Items.of(Material.BOOK, Text.item("<gold>Page " + (page + 1) + "/" + totalPages),
                List.of(Text.item("<gray>" + filtered.size() + " option(s)"))));
        inventory.setItem(SLOT_BACK, Items.of(Material.BARRIER, Text.item("<red>Back")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) CONTENT_SIZE));
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    page--;
                    rebuild();
                }
            }
            case SLOT_NEXT -> {
                if (page < totalPages - 1) {
                    page++;
                    rebuild();
                }
            }
            case SLOT_ANY -> onPick.accept("ANY");
            case SLOT_BACK -> onBack.run();
            case SLOT_SEARCH -> {
                if (event.getClick().isRightClick()) {
                    filter = "";
                    page = 0;
                    rebuild();
                } else {
                    viewer.closeInventory();
                    viewer.sendMessage(Text.parse("<gold>» <yellow>Type a search term (or <white>cancel<yellow>):"));
                    plugin.getChatInput().await(viewer.getUniqueId(), input -> {
                        if (input != null) {
                            filter = input.trim();
                            page = 0;
                        }
                        open(viewer);
                    });
                }
            }
            default -> {
                if (slot >= 0 && slot < CONTENT_SIZE) {
                    int index = page * CONTENT_SIZE + slot;
                    if (index < filtered.size()) {
                        onPick.accept(filtered.get(index).value());
                    }
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
