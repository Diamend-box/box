package com.diamend.boxtutorial.gui;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.guide.Topic;
import com.diamend.boxtutorial.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The glossary: one tile per idea, click it to have it explained in chat.
 *
 * <p>Explanations go to chat rather than into the lore of the tile, because
 * that's the copy a player can scroll back to while they're actually doing the
 * thing — lore vanishes the moment the menu closes.
 */
public class TopicsMenu extends AbstractMenu {

    private static final int[] TOPIC_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private final Map<Integer, String> offered = new HashMap<>();
    private final int page;
    private int pages = 1;

    public TopicsMenu(BoxTutorialPlugin plugin, int page) {
        super(plugin, 54, "<dark_gray>Tutorial <gray>| <gold>What things mean");
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        offered.clear();
        List<Topic> topics = plugin.tutorial().topics();
        pages = Math.max(1, (int) Math.ceil(topics.size() / (double) TOPIC_SLOTS.length));
        int start = Math.min(page, pages - 1) * TOPIC_SLOTS.length;

        for (int index = 0; index < TOPIC_SLOTS.length; index++) {
            int topicIndex = start + index;
            if (topicIndex >= topics.size()) {
                break;
            }
            Topic topic = topics.get(topicIndex);
            offered.put(TOPIC_SLOTS[index], topic.id());

            List<String> lore = new ArrayList<>(topic.lines());
            lore.add("");
            lore.add("<yellow>Click to read it in chat");
            set(TOPIC_SLOTS[index], Items.text(topic.icon(), topic.title(), lore, false));
        }

        backButton(BACK_SLOT, "Back to the tutorial");
        if (pages > 1) {
            if (page > 0) {
                set(PREV_SLOT, Items.text(Material.ARROW, "<yellow>« Previous page",
                        List.of("<gray>Page " + page + " of " + pages), false));
            }
            if (page < pages - 1) {
                set(NEXT_SLOT, Items.text(Material.ARROW, "<yellow>Next page »",
                        List.of("<gray>Page " + (page + 2) + " of " + pages), false));
            }
        }
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            click(player);
            openLater(player, new TutorialMenu(plugin, 0));
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            click(player);
            openLater(player, new TopicsMenu(plugin, page - 1));
            return;
        }
        if (slot == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new TopicsMenu(plugin, page + 1));
            return;
        }
        String id = offered.get(slot);
        Topic topic = id == null ? null : plugin.tutorial().topic(id);
        if (topic != null) {
            click(player);
            player.closeInventory();
            plugin.service().explain(player, topic);
        }
    }
}
