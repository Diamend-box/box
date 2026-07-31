package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.ore.CompactRecipe;
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
 * What one compactor slot should fold: every recipe this server has, with the
 * ones already sitting in another slot of the same compactor marked and inert.
 *
 * <p>Letting the same recipe be slotted twice would look like it did something
 * and do nothing, since compaction folds each recipe once per pass however many
 * slots name it.
 */
public class RecipePickerMenu extends AbstractMenu {

    private static final int HEADER_SLOT = 4;
    private static final int CLEAR_SLOT = 49;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final CompressorModule module;
    private final int index;
    private final int slot;
    private final int page;

    /** Menu slot to the recipe id it offers. */
    private final Map<Integer, String> offered = new HashMap<>();
    private int pages = 1;

    public RecipePickerMenu(BoxCorePlugin plugin, CompressorModule module,
                            int index, int slot, int page) {
        super(plugin, 54, "<dark_gray>Box <gray>| <aqua>Slot " + (slot + 1));
        this.module = module;
        this.index = index;
        this.slot = slot;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        offered.clear();
        ItemStack held = player.getInventory().getItem(index);
        if (!module.compactors().isCompactor(held)) {
            set(HEADER_SLOT, Items.text(Material.BARRIER, "<red>Compactor gone",
                    List.of("<gray>It isn't in that slot any more."), false));
            closeButton(CLOSE_SLOT);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        List<String> filters = module.compactors().filters(held);
        List<CompactRecipe> recipes = new ArrayList<>(module.recipes().all());
        pages = Math.max(1, (recipes.size() + RECIPE_SLOTS.length - 1) / RECIPE_SLOTS.length);
        int start = Math.min(page, pages - 1) * RECIPE_SLOTS.length;

        set(HEADER_SLOT, Items.text(Material.HOPPER,
                "<aqua><bold>What should slot " + (slot + 1) + " fold?",
                List.of("<gray>Pick a recipe and everything you",
                        "<gray>gather of it folds up automatically."), false));

        for (int offset = 0; offset < RECIPE_SLOTS.length; offset++) {
            int position = start + offset;
            if (position >= recipes.size()) {
                break;
            }
            CompactRecipe recipe = recipes.get(position);
            int at = RECIPE_SLOTS[offset];
            int taken = filters.indexOf(recipe.id());
            if (taken >= 0 && taken != slot) {
                set(at, Items.text(recipe.input(), "<dark_gray>" + recipe.display(),
                        List.of("<red>Already in slot " + (taken + 1) + "."), false));
            } else {
                offered.put(at, recipe.id());
                set(at, icon(recipe, taken == slot));
            }
        }
        if (recipes.isEmpty()) {
            set(RECIPE_SLOTS[10], Items.text(Material.BARRIER, "<gray>No recipes yet",
                    List.of("<dark_gray>An admin adds them with",
                            "<dark_gray>/box compactor recipes."), false));
        }

        set(CLEAR_SLOT, Items.text(Material.GRAY_DYE, "<yellow>Empty this slot",
                List.of("<gray>Stop folding anything here."), false));
        if (page > 0) {
            set(PREV_SLOT, Items.text(Material.ARROW, "<yellow>« Previous", List.of(), false));
        }
        if (page < pages - 1) {
            set(NEXT_SLOT, Items.text(Material.ARROW, "<yellow>Next »", List.of(), false));
        }
        backButton(BACK_SLOT, "Compactor");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack icon(CompactRecipe recipe, boolean current) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Folds <white>" + Text.number(recipe.amount())
                + "</white> into <white>1</white>.");
        lore.add("");
        lore.add(current ? "<green>Already folding here" : "<yellow>Click to fold this");
        return Items.text(recipe.input(), "<aqua>" + recipe.display(), lore, current);
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
            openLater(player, new CompactorMenu(plugin, module, index));
            return;
        }
        if (raw == PREV_SLOT && page > 0) {
            click(player);
            openLater(player, new RecipePickerMenu(plugin, module, index, slot, page - 1));
            return;
        }
        if (raw == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new RecipePickerMenu(plugin, module, index, slot, page + 1));
            return;
        }
        if (raw == CLEAR_SLOT) {
            choose(player, "");
            return;
        }
        String recipeId = offered.get(raw);
        if (recipeId != null) {
            choose(player, recipeId);
        }
    }

    private void choose(Player player, String recipeId) {
        CompactorMenu.assign(plugin, module, player, index, slot, recipeId);
        click(player);
        openLater(player, new CompactorMenu(plugin, module, index));
    }
}
