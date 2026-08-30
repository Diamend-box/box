package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The destination list, editable in game.
 *
 * <p>Making a place used to mean standing there and typing an id, and
 * everything else about it — its description, who can use it, how close you
 * have to get — meant stopping the server and editing {@code warps.yml}. This
 * is the same job done from where you're standing.
 *
 * <p>Deleting is deliberately not here. A list you click through to edit is a
 * list you will misclick eventually, and a warp is a lot of work to lose;
 * delete lives inside the destination's own screen, behind a shift-click.
 */
public class WarpEditorMenu extends AbstractMenu {

    private static final int HEADER_SLOT = 4;
    private static final int ADD_SLOT = 49;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final int[] WARP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final TravelModule module;
    private final int page;

    private final Map<Integer, String> shown = new HashMap<>();
    private int pages = 1;

    public WarpEditorMenu(BoxCorePlugin plugin, TravelModule module, int page) {
        super(plugin, 54, "<dark_gray>Box <gray>| <gold>Destinations");
        this.module = module;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        shown.clear();
        List<Warp> warps = new ArrayList<>(module.warps().all());
        pages = Math.max(1, (warps.size() + WARP_SLOTS.length - 1) / WARP_SLOTS.length);
        int start = Math.min(page, pages - 1) * WARP_SLOTS.length;

        set(HEADER_SLOT, Items.text(Material.FILLED_MAP, "<gold><bold>Destinations",
                List.of("<gray>Everywhere players can travel to.",
                        "",
                        "<gray>Destinations: <white>" + warps.size(),
                        "<dark_gray>Saved to warps.yml as you edit."), false));

        for (int offset = 0; offset < WARP_SLOTS.length; offset++) {
            int position = start + offset;
            if (position >= warps.size()) {
                break;
            }
            Warp warp = warps.get(position);
            int at = WARP_SLOTS[offset];
            shown.put(at, warp.id());
            set(at, icon(warp));
        }

        set(ADD_SLOT, addIcon(player));
        if (page > 0) {
            set(PREV_SLOT, Items.text(Material.ARROW, "<yellow>« Previous", List.of(), false));
        }
        if (page < pages - 1) {
            set(NEXT_SLOT, Items.text(Material.ARROW, "<yellow>Next »", List.of(), false));
        }
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private ItemStack icon(Warp warp) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>id: " + warp.id());
        lore.add("<gray>At <white>" + warp.where());
        lore.add("<gray>Found within <white>" + Math.round(warp.radius()) + "</white> blocks.");
        lore.add(warp.permission().isEmpty()
                ? "<gray>Open to <white>everyone"
                : "<gray>Needs <white>" + warp.permission());
        if (!warp.description().isEmpty()) {
            lore.add("<gray>Description: <white>" + warp.description().size() + "</white> line(s)");
        }
        lore.add("");
        lore.add("<yellow>Click to edit it");
        lore.add("<yellow>Shift-click to teleport there");
        return Items.text(warp.icon(), "<gold>" + warp.display(), lore, false);
    }

    /** The add button, describing what it would make from where you're stood. */
    private ItemStack addIcon(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Puts a destination where you're standing,");
        lore.add("<gray>facing the way you're facing.");
        lore.add("");
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held != null && !held.getType().isAir()) {
            lore.add("<gray>Icon: <white>" + Items.prettyName(held.getType()));
        } else {
            lore.add("<dark_gray>Hold something to use it as the icon.");
        }
        lore.add("<yellow>Click, then type a name in chat");
        return Items.text(Material.LIME_DYE, "<green>Add a destination here", lore, true);
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
        if (raw == PREV_SLOT && page > 0) {
            click(player);
            openLater(player, new WarpEditorMenu(plugin, module, page - 1));
            return;
        }
        if (raw == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new WarpEditorMenu(plugin, module, page + 1));
            return;
        }
        if (raw == ADD_SLOT) {
            askForName(player);
            return;
        }
        String id = shown.get(raw);
        if (id == null) {
            return;
        }
        Warp warp = module.warps().get(id);
        if (warp == null) {
            redraw(player);
            return;
        }
        if (event.isShiftClick()) {
            click(player);
            player.closeInventory();
            player.teleport(warp.location());
            plugin.messages().sendLiteral(player,
                    "<green>Teleported to <white>" + warp.id() + "<green>.");
            return;
        }
        click(player);
        openLater(player, new WarpEditMenu(plugin, module, id));
    }

    /**
     * Asks for a name and makes the destination from it.
     *
     * <p>The typed name is both the id and what players see, so making one is a
     * single answer rather than an interview. Renaming afterwards changes only
     * the visible half.
     */
    private void askForName(Player player) {
        click(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        Material icon = held == null || held.getType().isAir()
                ? Material.ENDER_PEARL : held.getType();
        plugin.prompts().ask(player,
                "<gold>What should this destination be called?",
                answer -> create(player, answer, icon),
                () -> new WarpEditorMenu(plugin, module, page).open(player));
    }

    private void create(Player player, String name, Material icon) {
        String id = idFrom(name);
        if (id.isEmpty()) {
            plugin.messages().sendLiteral(player,
                    "<red>That name has nothing in it a file can be keyed by. "
                            + "Use letters or numbers.");
            new WarpEditorMenu(plugin, module, page).open(player);
            return;
        }
        if (module.warps().get(id) != null) {
            plugin.messages().sendLiteral(player, "<red>There's already a destination called "
                    + "<white>" + id + "<red>.");
            new WarpEditorMenu(plugin, module, page).open(player);
            return;
        }
        Warp warp = new Warp(id, name, icon, List.of(), module.placementFor(player), "",
                plugin.getConfig().getDouble("travel.default-radius", 8.0));
        module.warps().put(warp);
        module.discover(player, warp);
        plugin.messages().sendLiteral(player,
                "<green>Created <white>" + id + "<green> where you're standing.");
        new WarpEditMenu(plugin, module, id).open(player);
    }

    /**
     * Turns a typed name into an id a file can hold: {@code Old Mine} →
     * {@code old_mine}.
     *
     * <p>Colours come off first. Somebody naming a place {@code &7Abandoned
     * Mines} means the place is called Abandoned Mines and is grey; they don't
     * mean its id is {@code 7abandoned_mines}. Left in, the stray digit follows
     * the destination into every command that has to name it.
     */
    public static String idFrom(String name) {
        String cleaned = Text.plain(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }
}
