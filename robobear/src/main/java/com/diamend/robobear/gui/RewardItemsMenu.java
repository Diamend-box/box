package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The reward editor: staff drop real items in, and those are what a milestone
 * pays.
 *
 * <p>Items rather than a number, on purpose. The BoxPvP spec (§18) rejects any
 * second currency, so RoboBear doesn't invent one — whatever this server already
 * treats as valuable goes in this box. It also means a payout can be anything
 * the server can make: raw ore, a named consumable, a crate key, a compactor.
 *
 * <p>Whatever goes in here is paid out the way {@code rewards.delivery} says,
 * which defaults to dropping it at the player's feet. Put whitelisted ore in and
 * it lands unbanked and at risk under §9 with no special-casing from this plugin.
 *
 * <p>Contents are captured on close, so there's no save button to forget.
 */
public class RewardItemsMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    private static final int SLOT_HELP = 45;
    private static final int SLOT_DONE = 49;

    private final RoboBearPlugin plugin;
    private final Player viewer;
    private final MilestoneRewards.Tier tier;

    private Inventory inventory;

    public RewardItemsMenu(RoboBearPlugin plugin, Player viewer, MilestoneRewards.Tier tier) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.tier = tier;
    }

    @Override
    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE,
                    Text.parse("<dark_green>Payout — round " + tier.round()));
            for (ItemStack item : tier.items()) {
                inventory.addItem(item.clone());
            }
        }
        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        boolean toGround = !"inventory".equalsIgnoreCase(
                plugin.getConfig().getString("rewards.delivery", "ground"));
        inventory.setItem(SLOT_HELP, Items.text(Material.BOOK, "<yellow>How this works", List.of(
                "<gray>Put the items this milestone should",
                "<gray>pay into the space above.",
                "<gray>They're saved when you close.",
                "",
                "<gray>Earned by clearing round <white>" + tier.round() + "<gray>.",
                toGround
                        ? "<gray>Delivery: <white>dropped on the floor"
                        : "<gray>Delivery: <white>straight to the inventory",
                "<dark_gray>Change that in config.yml.")));

        inventory.setItem(SLOT_DONE, Items.text(Material.EMERALD, "<green><bold>Done", List.of(
                "<gray>Saves and goes back.")));
        player.openInventory(inventory);
    }

    @Override
    public boolean cancelClick(InventoryClickEvent event) {
        if (event == null) {
            return false; // permit drags into the content area
        }
        int raw = event.getRawSlot();
        // Lock the control row; everything above it is a real, editable slot.
        return raw >= CONTENT_SIZE && raw < SIZE;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() == SLOT_DONE) {
            viewer.closeInventory(); // onClose captures and returns
        }
    }

    @Override
    public void onClose() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < CONTENT_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        tier.items().clear();
        tier.items().addAll(items);
        plugin.milestones().save();

        if (viewer.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> new MilestoneEditorMenu(plugin).open(viewer));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
