package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Which mines the challenge may send players to.
 *
 * <p>The problem this solves is specific to a real server: a prison or boxpvp
 * setup has dozens of mines, most of them behind a rank. Sending someone to a
 * mine they cannot enter is a round they lose to nothing, so an admin needs to
 * say which mines are fair game — and needs to say it in game, over a list that
 * is 70 long, without editing a file.
 *
 * <p>Paged rather than scrolling, since a chest has no scrollbar, with the two
 * bulk buttons that make either workflow short: switch everything off and pick a
 * few back on, or leave it open and knock out the ones that don't fit.
 */
public class MineToggleMenu extends AbstractMenu {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 46;
    private static final int SLOT_ENABLE_ALL = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_DISABLE_ALL = 50;
    private static final int SLOT_NEXT = 53;

    private int page;
    private List<MineRegion> shown = List.of();
    private int pages = 1;

    public MineToggleMenu(RoboBearPlugin plugin) {
        super(plugin, 54, "<dark_gray>Mines in the Challenge");
    }

    @Override
    protected void build(Player player) {
        List<MineRegion> all = plugin.mines().all();
        pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        int from = page * PER_PAGE;
        shown = new ArrayList<>(all.subList(from, Math.min(all.size(), from + PER_PAGE)));
        for (int i = 0; i < shown.size(); i++) {
            set(i, icon(shown.get(i)));
        }

        if (all.isEmpty()) {
            set(22, Items.text(Material.BARRIER, "<red>No mines to choose from", List.of(
                    "<gray>RoboBear can't see any mines at all.",
                    "<yellow>Run /rb mines debug to find out why.")));
        }

        if (page > 0) {
            set(SLOT_PREV, Items.text(Material.ARROW, "<yellow>« Page " + page, List.of()));
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, Items.text(Material.ARROW, "<yellow>Page " + (page + 2) + " »", List.of()));
        }

        set(SLOT_ENABLE_ALL, Items.text(Material.LIME_DYE, "<green>Enable every mine", List.of(
                "<gray>Puts all <white>" + all.size() + "<gray> back in the pool.")));
        set(SLOT_DISABLE_ALL, Items.text(Material.GRAY_DYE, "<red>Disable every mine", List.of(
                "<gray>Then switch on just the ones you want.",
                "",
                "<yellow>Shift-click to confirm.")));
        set(SLOT_INFO, info(all));
        closeButton(SLOT_CLOSE);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack info(List<MineRegion> all) {
        int enabled = plugin.mines().enabledSize();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>In the pool: <white>" + enabled + "<gray> of <white>" + all.size());
        lore.add("<gray>Source: <white>" + plugin.mines().activeSource());
        lore.add("<gray>Page <white>" + (page + 1) + "<gray> of <white>" + pages);
        lore.add("");
        if (enabled == 0 && !all.isEmpty()) {
            lore.add("<red>Nothing is switched on, so no mining");
            lore.add("<red>objectives can be rolled at all.");
        } else {
            lore.add("<gray>Objectives are only ever set in");
            lore.add("<gray>mines that are switched on.");
        }
        lore.add("");
        lore.add("<dark_gray>A run already under way in a mine");
        lore.add("<dark_gray>you switch off is not interrupted.");
        return Items.text(Material.COMPASS, "<yellow>The objective pool", lore);
    }

    private ItemStack icon(MineRegion mine) {
        boolean on = plugin.mines().toggles().isEnabled(mine.id());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + mine.boundsDescription());
        lore.add("<dark_gray>" + Text.number(mine.volume()) + " blocks");
        lore.add("");
        lore.add(on ? "<green>In the objective pool" : "<red>Excluded");
        lore.add("");
        lore.add(on ? "<gray>Click to exclude it" : "<gray>Click to put it back in");
        return Items.text(on ? Material.LIME_DYE : Material.GRAY_DYE,
                (on ? "<green>" : "<dark_gray>") + mine.id(), lore, on);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_CLOSE -> {
                click(player);
                player.closeInventory();
                return;
            }
            case SLOT_PREV -> {
                if (page > 0) {
                    click(player);
                    page--;
                    refresh(player);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (page < pages - 1) {
                    click(player);
                    page++;
                    refresh(player);
                }
                return;
            }
            case SLOT_ENABLE_ALL -> {
                click(player);
                int changed = plugin.mines().toggles().enableAll();
                player.sendMessage(Text.parse("<green>Every mine is in the pool again "
                        + "<gray>(" + changed + " switched back on)."));
                refresh(player);
                return;
            }
            case SLOT_DISABLE_ALL -> {
                // Guarded because it can silently remove every mining objective
                // from the game, and the click that does it is one slot from
                // the one that undoes it.
                if (!event.isShiftClick()) {
                    deny(player);
                    player.sendMessage(Text.parse("<yellow>Shift-click that to switch "
                            + "every mine off."));
                    return;
                }
                click(player);
                int changed = plugin.mines().toggles().disableAll(plugin.mines().all());
                player.sendMessage(Text.parse("<red>Switched off " + changed
                        + " mine(s). <gray>Click the ones you want back in."));
                refresh(player);
                return;
            }
            default -> {
            }
        }

        if (slot < 0 || slot >= shown.size() || slot >= PER_PAGE) {
            return;
        }
        MineRegion mine = shown.get(slot);
        boolean on = plugin.mines().toggles().isEnabled(mine.id());
        plugin.mines().toggles().setEnabled(mine.id(), !on);
        click(player);
        refresh(player);
    }
}
