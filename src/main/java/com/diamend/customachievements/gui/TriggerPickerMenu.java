package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.TriggerType;
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
import java.util.function.Consumer;

/** A simple menu for choosing a {@link TriggerType} by clicking its icon. */
public class TriggerPickerMenu implements Menu {

    private static final int SIZE = 36;
    private static final int SLOT_BACK = 31;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final Consumer<TriggerType> onPick;
    private final Runnable onBack;

    private Inventory inventory;

    public TriggerPickerMenu(CustomAchievementsPlugin plugin, Player viewer,
                             Consumer<TriggerType> onPick, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
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
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_green>Choose a Trigger"));
        }
        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        TriggerType[] triggers = TriggerType.values();
        for (int i = 0; i < triggers.length; i++) {
            TriggerType trigger = triggers[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<dark_gray>" + trigger.name()));
            lore.add(Text.item("<yellow>Click to choose"));
            inventory.setItem(i, Items.of(trigger.icon(), Text.item("<aqua>" + trigger.display()), lore));
        }
        inventory.setItem(SLOT_BACK, Items.of(Material.BARRIER, Text.item("<red>Back")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            onBack.run();
            return;
        }
        TriggerType[] triggers = TriggerType.values();
        if (slot >= 0 && slot < triggers.length) {
            onPick.accept(triggers[slot]);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
