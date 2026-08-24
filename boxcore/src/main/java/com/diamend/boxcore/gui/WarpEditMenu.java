package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One destination: what it looks like, where it is, who can use it and how
 * close counts as finding it.
 *
 * <p>Everything that can be taken from the world is: the icon comes off what
 * you're holding, the position from where you're standing. Only the three
 * things that have to be typed — a name, a description line, a permission node
 * — go out to chat, and each of those comes straight back here afterwards.
 */
public class WarpEditMenu extends AbstractMenu {

    private static final int PREVIEW_SLOT = 4;

    private static final int ICON_SLOT = 10;
    private static final int RENAME_SLOT = 12;
    private static final int MOVE_SLOT = 14;
    private static final int TELEPORT_SLOT = 16;

    private static final int RADIUS_SLOT = 22;
    private static final int DESCRIPTION_SLOT = 30;
    private static final int PERMISSION_SLOT = 32;

    private static final int DELETE_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    /** Menu slot to how much it changes the discovery radius by. */
    private static final Map<Integer, Integer> STEPS = Map.of(
            19, -10, 20, -5, 21, -1,
            23, 1, 24, 5, 25, 10);

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 250;

    private final TravelModule module;
    private final String id;

    public WarpEditMenu(BoxCorePlugin plugin, TravelModule module, String id) {
        super(plugin, 54, "<dark_gray>Box <gray>| <gold>Destination");
        this.module = module;
        this.id = id;
    }

    private Warp warp() {
        return module.warps().get(id);
    }

    @Override
    protected void build(Player player) {
        Warp warp = warp();
        if (warp == null) {
            set(PREVIEW_SLOT, Items.text(Material.BARRIER, "<red>Destination gone",
                    List.of("<gray>It isn't on the list any more."), false));
            closeButton(CLOSE_SLOT);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        set(PREVIEW_SLOT, preview(warp));
        set(ICON_SLOT, iconButton(player, warp));
        set(RENAME_SLOT, Items.text(Material.NAME_TAG, "<yellow>Rename it",
                List.of("<gray>Shown as <white>" + warp.display(),
                        "<dark_gray>The id stays " + warp.id() + ".",
                        "",
                        "<yellow>Click, then type it in chat"), false));
        set(MOVE_SLOT, Items.text(Material.ENDER_EYE, "<yellow>Move it here",
                List.of("<gray>Currently at <white>" + warp.where(),
                        "<gray>Puts it where you're standing, facing",
                        "<gray>the way you're facing.",
                        "",
                        "<yellow>Click to move it"), false));
        set(TELEPORT_SLOT, Items.text(Material.ENDER_PEARL, "<yellow>Go there",
                List.of("<gray>Takes you there now — no warmup,",
                        "<gray>no combat check.",
                        "",
                        "<yellow>Click to travel"), false));

        for (Map.Entry<Integer, Integer> step : STEPS.entrySet()) {
            set(step.getKey(), stepIcon(warp, step.getValue()));
        }
        set(RADIUS_SLOT, Items.text(Material.TARGET,
                "<gold>Found within " + Math.round(warp.radius()) + " blocks",
                List.of("<gray>How close a player has to get for",
                        "<gray>this place to open up for them.",
                        "<dark_gray>Bigger is easier to stumble across."), false));

        set(DESCRIPTION_SLOT, descriptionButton(warp));
        set(PERMISSION_SLOT, permissionButton(warp));

        set(DELETE_SLOT, Items.text(Material.BARRIER, "<red>Delete this destination",
                List.of("<gray>Players who found it keep the find —",
                        "<gray>make it again under the same id and it",
                        "<gray>comes back for them.",
                        "",
                        "<red>Shift-click to delete"), false));
        set(BACK_SLOT, Items.text(Material.ARROW, "<yellow>« Destinations", List.of(), false));
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    /** The warp exactly as a player sees it in the travel menu. */
    private ItemStack preview(Warp warp) {
        List<String> lore = new ArrayList<>(warp.description());
        if (lore.isEmpty()) {
            lore.add("<dark_gray>No description.");
        }
        lore.add("");
        lore.add("<dark_gray>This is how players see it.");
        return Items.text(warp.icon(), "<green>" + warp.display(), lore, false);
    }

    private ItemStack iconButton(Player player, Warp warp) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Currently <white>" + Items.prettyName(warp.icon()));
        lore.add("");
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            lore.add("<dark_gray>Hold something first.");
        } else {
            lore.add("<yellow>Click to use " + Items.prettyName(held.getType()));
        }
        return Items.text(Material.ITEM_FRAME, "<yellow>Set the icon from your hand", lore, false);
    }

    private ItemStack stepIcon(Warp warp, int step) {
        int result = clamp(warp.radius() + step);
        String label = (step > 0 ? "<green>+" : "<red>") + step;
        return Items.text(step > 0
                        ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                label, List.of(result == Math.round(warp.radius())
                        ? "<dark_gray>Already at the limit."
                        : "<gray>Makes it <white>" + result + "</white> blocks."), false);
    }

    private ItemStack descriptionButton(Warp warp) {
        List<String> lore = new ArrayList<>();
        if (warp.description().isEmpty()) {
            lore.add("<dark_gray>Nothing written yet.");
        } else {
            for (String line : warp.description()) {
                lore.add("<white>" + line);
            }
        }
        lore.add("");
        lore.add("<yellow>Click to add a line");
        lore.add("<yellow>Right-click to remove the last one");
        lore.add("<red>Shift-click to clear it");
        return Items.text(Material.WRITABLE_BOOK, "<yellow>Description", lore, false);
    }

    private ItemStack permissionButton(Warp warp) {
        List<String> lore = new ArrayList<>();
        if (warp.permission().isEmpty()) {
            lore.add("<gray>Open to <white>everyone");
        } else {
            lore.add("<gray>Needs <white>" + warp.permission());
            lore.add("<dark_gray>Players without it never find it,");
            lore.add("<dark_gray>rather than finding a locked door.");
        }
        lore.add("");
        lore.add("<yellow>Click to type a permission");
        lore.add("<yellow>Shift-click for <white>" + suggestedPermission(warp));
        lore.add("<red>Right-click to open it to everyone");
        return Items.text(Material.IRON_BARS, "<yellow>Who can use it", lore, false);
    }

    private String suggestedPermission(Warp warp) {
        return "boxcore.warp." + warp.id();
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Warp warp = warp();
        if (warp == null) {
            player.closeInventory();
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == BACK_SLOT) {
            click(player);
            openLater(player, new WarpEditorMenu(plugin, module, 0));
            return;
        }
        if (raw == DELETE_SLOT) {
            if (!event.isShiftClick()) {
                plugin.messages().sendLiteral(player,
                        "<gray>Shift-click to delete <white>" + warp.id() + "<gray>.");
                return;
            }
            module.warps().remove(warp.id());
            plugin.messages().sendLiteral(player,
                    "<yellow>Deleted <white>" + warp.id() + "<yellow>.");
            click(player);
            openLater(player, new WarpEditorMenu(plugin, module, 0));
            return;
        }
        if (raw == TELEPORT_SLOT) {
            click(player);
            player.closeInventory();
            player.teleport(warp.location());
            return;
        }
        if (raw == MOVE_SLOT) {
            save(player, warp.withLocation(player.getLocation().clone()));
            plugin.messages().sendLiteral(player,
                    "<green>Moved <white>" + warp.id() + "<green> to where you're standing.");
            return;
        }
        if (raw == ICON_SLOT) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                plugin.messages().sendLiteral(player, "<red>Hold the item you want as the icon.");
                return;
            }
            save(player, warp.withIcon(held.getType()));
            return;
        }
        if (raw == RENAME_SLOT) {
            askRename(player, warp);
            return;
        }
        if (raw == DESCRIPTION_SLOT) {
            editDescription(player, warp, event);
            return;
        }
        if (raw == PERMISSION_SLOT) {
            editPermission(player, warp, event);
            return;
        }
        Integer step = STEPS.get(raw);
        if (step != null) {
            int radius = clamp(warp.radius() + step);
            if (radius != Math.round(warp.radius())) {
                save(player, warp.withRadius(radius));
            }
        }
    }

    // ------------------------------------------------------------------
    // The three things that have to be typed
    // ------------------------------------------------------------------

    private void askRename(Player player, Warp warp) {
        click(player);
        plugin.prompts().ask(player,
                "<gold>What should <white>" + warp.id() + "</white> be called?",
                warp.display(),
                answer -> {
                    Warp current = warp();
                    if (current != null) {
                        module.warps().put(current.withDisplay(answer));
                        plugin.messages().sendLiteral(player,
                                "<green>Now shown as <white>" + answer + "<green>.");
                    }
                    reopen(player);
                },
                () -> reopen(player));
    }

    private void editDescription(Player player, Warp warp, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            save(player, warp.withDescription(List.of()));
            return;
        }
        if (event.isRightClick()) {
            if (warp.description().isEmpty()) {
                return;
            }
            List<String> lines = new ArrayList<>(warp.description());
            lines.remove(lines.size() - 1);
            save(player, warp.withDescription(lines));
            return;
        }
        click(player);
        plugin.prompts().ask(player,
                "<gold>What should the next line say?",
                answer -> {
                    Warp current = warp();
                    if (current != null) {
                        List<String> lines = new ArrayList<>(current.description());
                        lines.add(answer);
                        module.warps().put(current.withDescription(lines));
                    }
                    reopen(player);
                },
                () -> reopen(player));
    }

    private void editPermission(Player player, Warp warp, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            save(player, warp.withPermission(suggestedPermission(warp)));
            return;
        }
        if (event.isRightClick()) {
            save(player, warp.withPermission(""));
            return;
        }
        click(player);
        plugin.prompts().ask(player,
                "<gold>Which permission should it need?",
                warp.permission(),
                answer -> {
                    Warp current = warp();
                    if (current != null) {
                        module.warps().put(current.withPermission(answer));
                        plugin.messages().sendLiteral(player,
                                "<green>Now needs <white>" + answer + "<green>.");
                    }
                    reopen(player);
                },
                () -> reopen(player));
    }

    private void reopen(Player player) {
        new WarpEditMenu(plugin, module, id).open(player);
    }

    private void save(Player player, Warp updated) {
        module.warps().put(updated);
        click(player);
        getInventory().clear();
        build(player);
    }

    private static int clamp(double radius) {
        return (int) Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, Math.round(radius)));
    }
}
