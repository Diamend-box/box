package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The collections landing page: one icon per category, showing how many of its
 * collections the player has maxed.
 */
public class CollectionCategoryMenu extends AbstractMenu {

    private final CollectionsModule collections;
    private final Map<Integer, CollectionCategory> slots = new HashMap<>();

    public CollectionCategoryMenu(BoxCorePlugin plugin, CollectionsModule collections) {
        super(plugin, 27, "<dark_gray>Box <gray>| <gold>Collections");
        this.collections = collections;
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        List<CollectionCategory> categories = collections.collections().categories();

        int slot = categories.size() <= 7 ? 10 + (7 - categories.size()) / 2 : 9;
        for (CollectionCategory category : categories) {
            if (slot >= 26) {
                break;
            }
            List<ItemCollection> members = collections.collections().inCategory(category.getId());
            int started = 0;
            int maxed = 0;
            int tiers = 0;
            int tierTotal = 0;
            for (ItemCollection collection : members) {
                long amount = profile.getCollected(collection.getId());
                if (amount > 0) {
                    started++;
                }
                int tier = collection.tierFor(amount);
                tiers += tier;
                tierTotal += collection.tierCount();
                if (tier >= collection.tierCount() && collection.tierCount() > 0) {
                    maxed++;
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Collections: <white>" + started + "<gray>/<white>" + members.size()
                    + " <dark_gray>started");
            lore.add("<gray>Tiers: <white>" + tiers + "<gray>/<white>" + tierTotal);
            lore.add("<gray>Maxed: <white>" + maxed);
            lore.add("<gray>" + Text.progressBar(tierTotal == 0 ? 0 : (double) tiers / tierTotal,
                    20, "<gold>■", "<dark_gray>■"));
            lore.add("");
            lore.add("<yellow>Click to open");

            slots.put(slot, category);
            set(slot, Items.text(category.getIcon(), category.getDisplay(), lore, tiers > 0));
            slot++;
        }

        if (categories.isEmpty()) {
            set(13, Items.text(Material.BARRIER, "<red>No collections",
                    List.of("<gray>None are configured."), false));
        }

        set(4, Items.text(Material.CHEST, "<gold>Your collections",
                List.of("<gray>Items gathered: <white>" + Text.number(profile.getTotalCollected()),
                        "<gray>Skill points available: <white>" + profile.getAvailablePoints()), true));
        backButton(18, "Hub");
        closeButton(26);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == 26) {
            player.closeInventory();
            return;
        }
        if (raw == 18) {
            click(player);
            openLater(player, new HubMenu(plugin));
            return;
        }
        CollectionCategory category = slots.get(raw);
        if (category == null) {
            return;
        }
        click(player);
        openLater(player, new CollectionListMenu(plugin, collections, category, 0));
    }
}
