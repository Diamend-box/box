package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.ore.CompactRecipe;
import com.diamend.boxcore.ore.CompactorItem;
import com.diamend.boxcore.ore.CompressorModule;
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
 * One personal compactor, and what it is set to compact.
 *
 * <p>The compactor is identified by the inventory slot it was opened from
 * rather than by the item itself, and that slot is re-read on every click. A
 * player can rearrange their inventory with this open, and writing the new
 * assignments onto whatever happens to be sitting in the remembered slot would
 * be a good way to turn someone's pickaxe into a compactor.
 */
public class CompactorMenu extends AbstractMenu {

    private static final int HEADER_SLOT = 4;
    private static final int TOGGLE_SLOT = 8;
    private static final int INFO_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final int[] SLOT_POSITIONS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private final CompressorModule module;
    private final int index;

    /** Menu slot to the compactor slot it configures. */
    private final Map<Integer, Integer> positions = new HashMap<>();

    public CompactorMenu(BoxCorePlugin plugin, CompressorModule module, int index) {
        super(plugin, 54, "<dark_gray>Box <gray>| <aqua>Personal Compactor");
        this.module = module;
        this.index = index;
    }

    /** The compactor this menu is editing, or null if it has moved or gone. */
    private ItemStack compactor(Player player) {
        ItemStack held = player.getInventory().getItem(index);
        return module.compactors().isCompactor(held) ? held : null;
    }

    @Override
    protected void build(Player player) {
        positions.clear();
        ItemStack held = compactor(player);
        if (held == null) {
            set(HEADER_SLOT, Items.text(Material.BARRIER, "<red>Compactor gone",
                    List.of("<gray>It isn't in that slot any more."), false));
            closeButton(CLOSE_SLOT);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        CompactorItem compactors = module.compactors();
        set(HEADER_SLOT, header(held));
        set(TOGGLE_SLOT, toggleIcon(player));

        List<String> filters = compactors.filters(held);
        int slots = Math.min(filters.size(), SLOT_POSITIONS.length);
        for (int slot = 0; slot < slots; slot++) {
            int at = SLOT_POSITIONS[slot];
            positions.put(at, slot);
            set(at, slotIcon(slot, filters.get(slot)));
        }
        if (filters.size() > SLOT_POSITIONS.length) {
            // A tier configured with more slots than the page holds. Say so
            // rather than silently hide capacity someone paid for.
            set(46, Items.text(Material.PAPER, "<yellow>More slots than fit",
                    List.of("<gray>This compactor has <white>" + filters.size() + "</white> slots;",
                            "<gray>this page shows the first <white>"
                                    + SLOT_POSITIONS.length + "</white>."), false));
        }

        set(INFO_SLOT, infoIcon());
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private ItemStack header(ItemStack held) {
        CompactorItem compactors = module.compactors();
        int slots = compactors.slots(held);
        int used = compactors.active(held).size();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Slots in use: <white>" + used + "</white>/" + slots);
        lore.add("");
        lore.add("<gray>Anything you slot in here folds up");
        lore.add("<gray>as you gather it.");
        return Items.text(held.getType(), "<aqua><bold>Your compactor", lore, true);
    }

    private ItemStack toggleIcon(Player player) {
        boolean on = module.isEnabledFor(player);
        List<String> lore = new ArrayList<>();
        lore.add(on
                ? "<gray>Your compactors are running."
                : "<gray>Everything is left exactly as it drops.");
        if (module.inGrace(player)) {
            lore.add("<yellow>Paused — you just expanded some.");
        }
        lore.add("");
        lore.add(on ? "<yellow>Click to pause them" : "<yellow>Click to start them");
        return Items.text(on ? Material.LIME_DYE : Material.GRAY_DYE,
                on ? "<green><bold>Compacting: ON" : "<red><bold>Compacting: OFF", lore, on);
    }

    private ItemStack slotIcon(int slot, String recipeId) {
        CompactRecipe recipe = recipeId == null || recipeId.isBlank()
                ? null : module.recipes().get(recipeId);
        if (recipe == null) {
            return Items.text(Material.GRAY_DYE, "<gray>Slot " + (slot + 1) + " <dark_gray>— empty",
                    List.of("<gray>Nothing is folded here yet.",
                            "",
                            "<yellow>Click to choose what it compacts"), false);
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Folds <white>" + Text.number(recipe.amount())
                + "</white> into <white>1</white>.");
        lore.add("");
        lore.add("<yellow>Click to change it");
        lore.add("<yellow>Shift-click to empty the slot");
        return Items.text(recipe.input(), "<aqua>Slot " + (slot + 1) + " <dark_gray>— <white>"
                + recipe.display(), lore, true);
    }

    private ItemStack infoIcon() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Slot a recipe and everything you");
        lore.add("<gray>gather of it folds up automatically.");
        lore.add("");
        lore.add("<gray>A folded unit is worth exactly what");
        lore.add("<gray>went into it — nothing is gained or");
        lore.add("<gray>lost by compacting.");
        lore.add("");
        lore.add("<gray>Right-click a unit to expand one,");
        lore.add("<gray>or sneak and right-click for the stack.");
        if (module.graceSeconds() > 0) {
            lore.add("<dark_gray>Expanding pauses compacting for "
                    + module.graceSeconds() + "s.");
        }
        return Items.text(Material.BOOK, "<yellow>How it works", lore, false);
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
        if (raw == TOGGLE_SLOT) {
            boolean on = module.toggle(player);
            plugin.messages().send(player, on ? "compressor-on" : "compressor-off");
            click(player);
            redraw(player);
            return;
        }
        Integer slot = positions.get(raw);
        if (slot == null) {
            return;
        }
        if (event.isShiftClick()) {
            assign(player, slot, "");
            return;
        }
        click(player);
        openLater(player, new RecipePickerMenu(plugin, module, index, slot, 0));
    }

    /**
     * Writes one slot's assignment onto the compactor.
     *
     * <p>Public so the picker can hand its answer back without reaching into
     * this menu's internals.
     */
    public static void assign(BoxCorePlugin plugin, CompressorModule module,
                              Player player, int index, int slot, String recipeId) {
        ItemStack held = player.getInventory().getItem(index);
        if (!module.compactors().isCompactor(held)) {
            plugin.messages().send(player, "compactor-moved");
            return;
        }
        List<String> filters = new ArrayList<>(module.compactors().filters(held));
        if (slot < 0 || slot >= filters.size()) {
            return;
        }
        filters.set(slot, recipeId == null ? "" : recipeId);
        ItemStack updated = module.compactors().withFilters(held, filters);
        if (updated != null) {
            player.getInventory().setItem(index, updated);
            module.markPending(player);
        }
    }

    private void assign(Player player, int slot, String recipeId) {
        assign(plugin, module, player, index, slot, recipeId);
        click(player);
        redraw(player);
    }

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }
}
