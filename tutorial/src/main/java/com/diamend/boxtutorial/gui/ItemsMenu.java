package com.diamend.boxtutorial.gui;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.items.ItemRegistry;
import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where staff point the tutorial's rewards at their own items.
 *
 * <p>Binding is done by holding the item and clicking the slot, not by dropping
 * it in. The menu never takes anything: whatever you're holding stays in your
 * hand, and a copy of it becomes what the trade hands out. A staff member who
 * mis-clicks loses nothing, which is the only acceptable behaviour for a screen
 * that handles somebody's one-of-a-kind custom sword.
 */
public class ItemsMenu extends AbstractMenu {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int HEADER_SLOT = 4;
    private static final int CLOSE_SLOT = 49;

    private final Map<Integer, String> offered = new HashMap<>();

    public ItemsMenu(BoxTutorialPlugin plugin) {
        super(plugin, 54, "<dark_gray>Tutorial <gray>| <gold>Items");
    }

    public static void openFor(BoxTutorialPlugin plugin, Player player) {
        new ItemsMenu(plugin).open(player);
    }

    @Override
    protected void build(Player player) {
        offered.clear();
        ItemRegistry registry = plugin.items();
        List<String> ids = registry.ids();

        set(HEADER_SLOT, Items.text(Material.KNOWLEDGE_BOOK, "<gold>The tutorial's items",
                List.of("<gray>These are what the trader hands out.",
                        "<gray>Point any of them at your own item and",
                        "<gray>every trade uses it from then on.",
                        "",
                        "<gray>Nothing here is ever taken from you."),
                false));

        for (int index = 0; index < SLOTS.length && index < ids.size(); index++) {
            String id = ids.get(index);
            offered.put(SLOTS[index], id);
            set(SLOTS[index], tile(registry, id));
        }
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    /** One slot: what it currently is, and the three things a click can do. */
    private ItemStack tile(ItemRegistry registry, String id) {
        ItemStack current = registry.get(id);
        ItemStack icon = current == null
                ? Items.text(Material.BARRIER, "<red>" + registry.label(id), List.of(), false)
                : current.clone();

        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>item:" + id);
        lore.add(registry.isBound(id)
                ? "<green>Set to your own item."
                : "<gray>Using the built-in default.");
        if (!registry.statsFor(id).isEmpty()) {
            lore.add("");
            lore.add("<gray>Stats while in the off hand:");
            registry.statsFor(id).forEach((stat, value) ->
                    lore.add("  <white>" + (value >= 0 ? "+" : "") + trim(value)
                            + " <gray>" + Text.prettify(stat)));
        }
        lore.add("");
        lore.add("<yellow>Click holding an item <gray>to use that item");
        lore.add("<yellow>Click empty-handed <gray>for a copy of this one");
        if (registry.isBound(id)) {
            lore.add("<yellow>Right-click <gray>to go back to the default");
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (current != null) {
                // Keep the item's own name; add the slot's label above the lore.
                lore.add(0, "<gray>Slot: <white>" + registry.label(id));
            }
            List<net.kyori.adventure.text.Component> parsed = new ArrayList<>();
            for (String line : lore) {
                parsed.add(Text.item(line));
            }
            meta.lore(parsed);
            icon.setItemMeta(meta);
        }
        icon.setAmount(1);
        return icon;
    }

    private static String trim(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0e-6
                ? String.valueOf((long) Math.rint(value)) : String.valueOf(value);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        String id = offered.get(event.getRawSlot());
        if (id == null) {
            return;
        }
        click(player);
        ItemStack held = player.getInventory().getItemInMainHand();

        if (event.isRightClick() && plugin.items().isBound(id)) {
            plugin.items().clear(id);
            plugin.instances().refreshTraders();
            plugin.messages().sendLiteral(player,
                    "<gray>Back to the default for <white>" + id + "<gray>.");
        } else if (held != null && !held.getType().isAir()) {
            plugin.items().bind(id, held);
            plugin.instances().refreshTraders();
            plugin.messages().sendLiteral(player,
                    "<green>The tutorial's <white>" + id + "<green> is now the item you're holding.");
        } else {
            ItemStack copy = plugin.items().get(id);
            if (copy != null) {
                player.getInventory().addItem(copy);
                plugin.messages().sendLiteral(player,
                        "<gray>Gave you a copy of <white>" + id + "<gray>.");
            }
        }
        openLater(player, new ItemsMenu(plugin));
    }
}
