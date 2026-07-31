package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.TravelService;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where you can go, and where you haven't been yet.
 *
 * <p>Places you haven't found are shown rather than hidden, but without their
 * name or where they are. A blank in a list someone can see is a reason to go
 * exploring; a list that silently grows as you wander is just a list.
 */
public class TravelMenu extends AbstractMenu {

    private static final String ADMIN = "boxcore.admin";

    private static final int HEADER_SLOT = 4;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int EDIT_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final int[] WARP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final TravelModule module;
    private final int page;

    /** Menu slot to the warp id it offers; only discovered ones are listed. */
    private final Map<Integer, String> offered = new HashMap<>();
    private int pages = 1;

    public TravelMenu(BoxCorePlugin plugin, TravelModule module, int page) {
        super(plugin, 54, "<dark_gray>Box <gray>| <green>Fast travel");
        this.module = module;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        offered.clear();
        List<Warp> warps = module.visibleTo(player);
        pages = Math.max(1, (warps.size() + WARP_SLOTS.length - 1) / WARP_SLOTS.length);
        int start = Math.min(page, pages - 1) * WARP_SLOTS.length;

        set(HEADER_SLOT, header(player, warps.size()));

        for (int offset = 0; offset < WARP_SLOTS.length; offset++) {
            int position = start + offset;
            if (position >= warps.size()) {
                break;
            }
            Warp warp = warps.get(position);
            int at = WARP_SLOTS[offset];
            if (module.hasDiscovered(player, warp)) {
                offered.put(at, warp.id());
                set(at, found(player, warp));
            } else {
                set(at, unfound());
            }
        }
        if (warps.isEmpty()) {
            set(WARP_SLOTS[10], Items.text(Material.BARRIER, "<gray>Nowhere to go yet",
                    List.of("<dark_gray>No destinations are set up."), false));
        }

        if (page > 0) {
            set(PREV_SLOT, Items.text(Material.ARROW, "<yellow>« Previous", List.of(), false));
        }
        if (page < pages - 1) {
            set(NEXT_SLOT, Items.text(Material.ARROW, "<yellow>Next »", List.of(), false));
        }
        if (player.hasPermission(ADMIN)) {
            set(EDIT_SLOT, Items.text(Material.FILLED_MAP, "<gold>Edit destinations",
                    List.of("<gray>Add, move and describe places",
                            "<gray>without leaving the game.",
                            "",
                            "<dark_gray>Only staff see this."), false));
        }
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private ItemStack header(Player player, int total) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Found: <white>" + module.discoveredCount(player)
                + "</white>/" + total);
        lore.add("<gray>Walk into somewhere to add it here.");
        lore.add("");
        TravelService travel = module.travel();
        if (travel.warmupSeconds() > 0) {
            lore.add("<gray>Travelling takes <white>" + travel.warmupSeconds()
                    + "s</white>, and stops");
            lore.add("<gray>if you move or take damage.");
        }
        long tagged = module.combat().remainingMillis(player);
        if (tagged > 0) {
            lore.add("");
            lore.add("<red>In combat — <white>" + Durations.format(tagged) + "</white> left.");
        }
        return Items.text(Material.COMPASS, "<green><bold>Fast travel", lore, false);
    }

    private ItemStack found(Player player, Warp warp) {
        List<String> lore = new ArrayList<>(warp.description());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        if (module.combat().isTagged(player)) {
            lore.add("<red>Not while you're in combat.");
        } else if (module.travel().isTravelling(player)) {
            lore.add("<yellow>You're already on your way.");
        } else {
            lore.add("<yellow>Click to travel");
        }
        return Items.text(warp.icon(), "<green>" + warp.display(), lore, false);
    }

    private ItemStack unfound() {
        return Items.text(Material.GRAY_DYE, "<dark_gray>???",
                List.of("<gray>Somewhere you haven't been.",
                        "<dark_gray>Find it and it opens up here."), false);
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

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
        if (raw == EDIT_SLOT && player.hasPermission(ADMIN)) {
            click(player);
            openLater(player, new WarpEditorMenu(plugin, module, 0));
            return;
        }
        if (raw == PREV_SLOT && page > 0) {
            click(player);
            openLater(player, new TravelMenu(plugin, module, page - 1));
            return;
        }
        if (raw == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new TravelMenu(plugin, module, page + 1));
            return;
        }
        String id = offered.get(raw);
        if (id == null) {
            return;
        }
        Warp warp = module.warps().get(id);
        if (warp == null) {
            redraw(player);
            return;
        }
        // Close first: the warmup countdown goes to the actionbar, which is
        // behind an open inventory screen.
        click(player);
        player.closeInventory();
        module.travel().begin(player, warp);
    }

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }
}
