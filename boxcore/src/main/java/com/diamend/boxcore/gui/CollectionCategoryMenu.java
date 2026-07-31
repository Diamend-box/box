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

    /** The row categories sit in, and where they spill to when there are many. */
    private static final int[] ROW = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] OVERFLOW = {19, 20, 21, 22, 23, 24, 25};

    private static final int BACK_SLOT = 18;
    private static final int CLOSE_SLOT = 26;

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

        int[] layout = layout(categories.size());
        for (int index = 0; index < layout.length; index++) {
            CollectionCategory category = categories.get(index);
            int slot = layout[index];
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
        }

        if (categories.isEmpty()) {
            set(13, Items.text(Material.BARRIER, "<red>No collections",
                    List.of("<gray>None are configured."), false));
        }

        List<String> header = new ArrayList<>();
        header.add("<gray>Items gathered: <white>"
                + Text.number(collections.collections().itemsCollected(profile)));
        header.add("<gray>Skill points available: <white>" + profile.getAvailablePoints());
        if (categories.size() > layout.length) {
            // Say so rather than let a category quietly not exist.
            header.add("<red>Showing <white>" + layout.length + "</white> of <white>"
                    + categories.size() + "</white> categories.");
        }
        set(4, Items.text(Material.CHEST, "<gold>Your collections", header, true));
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Which slots to put {@code count} categories in.
     *
     * <p>A short row is centred on the middle column, under the header, so the
     * menu doesn't read as though something is missing off the right-hand side.
     * An even count can't sit exactly on a column, so the spare space goes to
     * the left of the group.
     *
     * <p>More than a row's worth spills onto a second row rather than running on
     * into the back and close buttons. The old layout ran straight through them:
     * a tenth category was drawn under the back arrow and could not be clicked,
     * and an eighteenth was dropped without a word.
     */
    private static int[] layout(int count) {
        if (count <= 0) {
            return new int[0];
        }
        if (count > ROW.length) {
            int shown = Math.min(count, ROW.length + OVERFLOW.length);
            int[] slots = new int[shown];
            for (int index = 0; index < shown; index++) {
                slots[index] = index < ROW.length ? ROW[index] : OVERFLOW[index - ROW.length];
            }
            return slots;
        }
        int start = (ROW.length - count + 1) / 2;
        int[] slots = new int[count];
        for (int index = 0; index < count; index++) {
            slots[index] = ROW[start + index];
        }
        return slots;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == BACK_SLOT) {
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
