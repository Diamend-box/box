package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionTier;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Every tier of a single collection: what it costs, what it pays, and which
 * ones the player has already banked.
 */
public class CollectionDetailMenu extends AbstractMenu {

    private static final int[] TIER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final CollectionsModule collections;
    private final CollectionCategory category;
    private final ItemCollection collection;
    private final int listPage;

    public CollectionDetailMenu(BoxCorePlugin plugin, CollectionsModule collections,
                                CollectionCategory category, ItemCollection collection, int listPage) {
        super(plugin, 45, "<dark_gray>Box <gray>| " + collection.getDisplay());
        this.collections = collections;
        this.category = category;
        this.collection = collection;
        this.listPage = listPage;
    }

    @Override
    protected void build(Player player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        long amount = profile.getCollected(collection.getId());
        int reached = collection.tierFor(amount);

        List<String> header = new ArrayList<>();
        header.add("<gray>Gathered: <white>" + Text.number(amount));
        header.add("<gray>Tier: <white>" + (reached == 0 ? "—" : Text.roman(reached))
                + "<gray>/<white>" + Text.roman(Math.max(1, collection.tierCount())));
        header.add("");
        header.add("<gray>Counts these items:");
        int listed = 0;
        for (Material material : collection.getMaterials()) {
            if (listed++ >= 8) {
                header.add("<dark_gray>…and " + (collection.getMaterials().size() - 8) + " more");
                break;
            }
            header.add("<dark_gray>• " + Text.prettify(material.name()));
        }
        set(4, Items.text(collection.getIcon(), collection.getDisplay(), header, reached > 0));

        for (int index = 0; index < collection.getTiers().size() && index < TIER_SLOTS.length; index++) {
            CollectionTier tier = collection.getTiers().get(index);
            boolean earned = index < reached;
            boolean current = index == reached;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Requires: <white>" + Text.number(tier.amount()) + " <gray>gathered");
            if (tier.points() > 0) {
                lore.add("<gray>Reward: <white>" + tier.points() + " <gray>skill point"
                        + Messages.plural(tier.points()));
            }
            if (tier.xp() > 0) {
                lore.add("<gray>Reward: <white>" + tier.xp() + " <gray>XP");
            }
            if (!tier.commands().isEmpty()) {
                lore.add("<gray>Reward: <white>bonus items");
            }
            lore.add("");
            if (earned) {
                lore.add("<green>✔ Claimed");
            } else if (current) {
                lore.add("<yellow>» In progress <gray>("
                        + Text.number(collection.remainingFor(amount)) + " to go)");
                lore.add("<gray>" + Text.progressBar(collection.progress(amount), 20,
                        "<gold>■", "<dark_gray>■"));
            } else {
                lore.add("<dark_gray>Locked");
            }

            Material icon = earned ? Material.LIME_STAINED_GLASS_PANE
                    : current ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            set(TIER_SLOTS[index], Items.text(icon,
                    "<white>Tier " + Text.roman(index + 1), lore, current));
        }

        if (collection.getTiers().isEmpty()) {
            set(22, Items.text(Material.BARRIER, "<red>No tiers configured",
                    List.of("<gray>This collection counts but", "<gray>doesn't pay out yet."), false));
        }

        set(36, Items.text(Material.ARROW, "<yellow>« " + Text.plain(category.getDisplay()),
                List.of(), false));
        closeButton(44);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 44) {
            player.closeInventory();
        } else if (event.getRawSlot() == 36) {
            click(player);
            openLater(player, new CollectionListMenu(plugin, collections, category, listPage));
        }
    }
}
