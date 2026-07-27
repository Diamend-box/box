package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionTier;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The collections inside one category, paginated 28 to a page.
 */
public class CollectionListMenu extends AbstractMenu {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final CollectionsModule collections;
    private final CollectionCategory category;
    private final int page;
    private final Map<Integer, ItemCollection> slots = new HashMap<>();
    private int pageCount = 1;

    public CollectionListMenu(BoxCorePlugin plugin, CollectionsModule collections,
                              CollectionCategory category, int page) {
        super(plugin, 54, "<dark_gray>Box <gray>| " + category.getDisplay());
        this.collections = collections;
        this.category = category;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        List<ItemCollection> members = collections.collections().inCategory(category.getId());
        pageCount = Math.max(1, (int) Math.ceil(members.size() / (double) CONTENT_SLOTS.length));
        int start = page * CONTENT_SLOTS.length;

        for (int index = 0; index < CONTENT_SLOTS.length; index++) {
            int memberIndex = start + index;
            if (memberIndex >= members.size()) {
                break;
            }
            ItemCollection collection = members.get(memberIndex);
            slots.put(CONTENT_SLOTS[index], collection);
            set(CONTENT_SLOTS[index], render(profile, collection));
        }

        if (page > 0) {
            set(48, Items.text(Material.SPECTRAL_ARROW, "<yellow>« Previous page",
                    List.of("<gray>Page " + page + " of " + pageCount), false));
        }
        if (page + 1 < pageCount) {
            set(50, Items.text(Material.SPECTRAL_ARROW, "<yellow>Next page »",
                    List.of("<gray>Page " + (page + 2) + " of " + pageCount), false));
        }
        set(45, Items.text(Material.ARROW, "<yellow>« Categories", List.of(), false));
        set(49, Items.text(Material.EXPERIENCE_BOTTLE, "<green>Skill points",
                List.of("<gray>Available: <white>" + profile.getAvailablePoints()), true));
        closeButton(53);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack render(PlayerProfile profile, ItemCollection collection) {
        long amount = profile.getCollected(collection.getId());
        int tier = collection.tierFor(amount);
        CollectionTier next = collection.nextTier(amount);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Gathered: <white>" + Text.number(amount));
        lore.add("<gray>Tier: <white>" + (tier == 0 ? "—" : Text.roman(tier))
                + "<gray>/<white>" + Text.roman(Math.max(1, collection.tierCount())));
        if (next == null) {
            lore.add("<green>✔ Fully collected");
        } else {
            lore.add("<gray>Next tier at <white>" + Text.number(next.amount())
                    + " <dark_gray>(" + Text.number(collection.remainingFor(amount)) + " to go)");
            lore.add("<gray>" + Text.progressBar(collection.progress(amount), 20,
                    "<gold>■", "<dark_gray>■"));
            if (next.points() > 0) {
                lore.add("<gray>Reward: <white>" + next.points() + " <gray>skill point"
                        + com.diamend.boxcore.util.Messages.plural(next.points()));
            }
        }
        lore.add("");
        lore.add("<yellow>Click for tier breakdown");

        ItemStack item = Items.text(collection.getIcon(), collection.getDisplay(), lore, tier > 0);
        if (tier > 0) {
            Items.withCount(item, tier);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        switch (raw) {
            case 53 -> {
                player.closeInventory();
                return;
            }
            case 45 -> {
                click(player);
                openLater(player, new CollectionCategoryMenu(plugin, collections));
                return;
            }
            case 48 -> {
                if (page > 0) {
                    click(player);
                    openLater(player, new CollectionListMenu(plugin, collections, category, page - 1));
                }
                return;
            }
            case 50 -> {
                if (page + 1 < pageCount) {
                    click(player);
                    openLater(player, new CollectionListMenu(plugin, collections, category, page + 1));
                }
                return;
            }
            default -> {
                ItemCollection collection = slots.get(raw);
                if (collection != null) {
                    click(player);
                    openLater(player, new CollectionDetailMenu(plugin, collections, category, collection, page));
                }
            }
        }
    }
}
