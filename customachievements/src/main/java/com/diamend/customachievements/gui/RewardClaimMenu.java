package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
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
import java.util.Map;

/**
 * Lets a player collect reward items that couldn't fit in their inventory when
 * an achievement unlocked. The pending items are laid out in the top area; the
 * player can take them out one at a time or use "Claim All". Whatever is left
 * when the menu closes is written back to storage, so nothing is ever lost.
 */
public class RewardClaimMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    private static final int SLOT_CLAIM_ALL = 49;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;

    private Inventory inventory;
    // How many pending items were laid into the content area on the last open;
    // anything beyond this in storage was never shown and must be preserved.
    private int loadedCount;

    public RewardClaimMenu(CustomAchievementsPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    private PlayerData data() {
        return plugin.getPlayerDataManager().get(viewer.getUniqueId());
    }

    @Override
    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_green><bold>Unclaimed Rewards"));
        }
        inventory.clear();

        List<ItemStack> pending = data().getPendingRewards();
        loadedCount = Math.min(CONTENT_SIZE, pending.size());
        for (int i = 0; i < loadedCount; i++) {
            inventory.setItem(i, pending.get(i).clone());
        }

        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        renderControls(pending.size());
        player.openInventory(inventory);
    }

    private void renderControls(int pendingCount) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.item("<gray>Items that didn't fit in your"));
        lore.add(Text.item("<gray>inventory when you unlocked an achievement."));
        lore.add(Component.empty());
        lore.add(Text.item("<yellow>Drag them out, or click to claim everything."));
        if (pendingCount > CONTENT_SIZE) {
            lore.add(Component.empty());
            lore.add(Text.item("<gray>Showing <white>" + CONTENT_SIZE + "<gray> of <white>"
                    + pendingCount + "<gray> — collect these and reopen for more."));
        }
        inventory.setItem(SLOT_CLAIM_ALL, Items.of(Material.EMERALD,
                Text.item("<green><bold>Claim All"), lore, true));
    }

    @Override
    public boolean cancelClick(InventoryClickEvent event) {
        if (event == null) {
            return true; // block drags spanning the control row
        }
        int raw = event.getRawSlot();
        // Lock the control row; let the player freely take items from the content area.
        return raw >= CONTENT_SIZE && raw < SIZE;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() == SLOT_CLAIM_ALL) {
            claimAll();
        }
    }

    /** Moves every shown item that fits into the player's inventory; leaves the rest. */
    private void claimAll() {
        boolean anyLeft = false;
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(item.clone());
            if (leftover.isEmpty()) {
                inventory.setItem(i, null);
            } else {
                inventory.setItem(i, leftover.values().iterator().next());
                anyLeft = true;
            }
        }
        if (!anyLeft) {
            // Everything collected — closing persists the now-empty storage.
            viewer.closeInventory();
        }
    }

    @Override
    public void onClose() {
        // Rebuild storage from what's left on screen plus anything we never showed.
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                remaining.add(item.clone());
            }
        }
        List<ItemStack> current = data().getPendingRewards();
        if (loadedCount < current.size()) {
            remaining.addAll(new ArrayList<>(current.subList(loadedCount, current.size())));
        }
        data().setPendingRewards(remaining);
        plugin.getPlayerDataManager().save(viewer.getUniqueId());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
