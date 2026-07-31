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
import java.util.Locale;
import java.util.Map;

/**
 * The recipe book, editable in game.
 *
 * <p>Adding a recipe by hand meant stopping the server, finding the right block
 * of YAML and getting the indentation right. Here it is: hold the item, click
 * add. Every change writes {@code compactor.yml} straight away, so a crash
 * between edits costs nothing.
 *
 * <p>Adding one is done entirely by holding an item, and so is styling it: the
 * skin, the name and the lore can all be lifted off something named in an anvil,
 * which is how an owner styles everything else. Typing is offered too, on the
 * recipe's own screen, for the one thing an anvil can't write — a name that
 * still reads correctly after the ratio changes.
 */
public class RecipeEditorMenu extends AbstractMenu {

    private static final int HEADER_SLOT = 4;
    private static final int ADD_SLOT = 49;
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
    private final int page;

    private final Map<Integer, String> shown = new HashMap<>();
    private int pages = 1;

    public RecipeEditorMenu(BoxCorePlugin plugin, CompressorModule module, int page) {
        super(plugin, 54, "<dark_gray>Box <gray>| <gold>Compaction recipes");
        this.module = module;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player player) {
        shown.clear();
        List<CompactRecipe> recipes = new ArrayList<>(module.recipes().all());
        pages = Math.max(1, (recipes.size() + RECIPE_SLOTS.length - 1) / RECIPE_SLOTS.length);
        int start = Math.min(page, pages - 1) * RECIPE_SLOTS.length;

        set(HEADER_SLOT, Items.text(Material.WRITABLE_BOOK, "<gold><bold>Compaction recipes",
                List.of("<gray>What this server folds up, and how much",
                        "<gray>of it makes one unit.",
                        "",
                        "<gray>Recipes: <white>" + recipes.size(),
                        "<dark_gray>Saved to compactor.yml as you edit."), false));

        for (int offset = 0; offset < RECIPE_SLOTS.length; offset++) {
            int position = start + offset;
            if (position >= recipes.size()) {
                break;
            }
            CompactRecipe recipe = recipes.get(position);
            int at = RECIPE_SLOTS[offset];
            shown.put(at, recipe.id());
            set(at, icon(recipe));
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

    private ItemStack icon(CompactRecipe recipe) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Folds <white>" + Text.number(recipe.amount())
                + "</white> into <white>1</white>.");
        lore.add("<dark_gray>id: " + recipe.id());
        Material skin = recipe.appearance().materialOr(recipe.input());
        if (skin != recipe.input()) {
            lore.add("<gray>Looks like: <white>" + Text.prettify(
                    skin.name().toLowerCase(Locale.ROOT)));
        }
        lore.add("");
        lore.add("<yellow>Click to edit it");
        lore.add("<red>Shift-click to delete it");
        return Items.text(recipe.input(), "<gold>" + recipe.display(), lore, false);
    }

    /** The add button, which describes what it would add from what's in hand. */
    private ItemStack addIcon(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        List<String> lore = new ArrayList<>();
        if (held == null || held.getType().isAir()) {
            lore.add("<gray>Hold the item you want to compact,");
            lore.add("<gray>then click here.");
            return Items.text(Material.LIME_DYE, "<green>Add a recipe", lore, false);
        }
        CompactRecipe existing = module.recipes().forInput(held.getType());
        if (existing != null) {
            lore.add("<red>Already compacts as <white>" + existing.id() + "</white>.");
            lore.add("<gray>Edit that one instead — an item can only");
            lore.add("<gray>have one recipe, or a slot naming it");
            lore.add("<gray>would be guessing which.");
            return Items.text(Material.BARRIER, "<red>Already on the books", lore, false);
        }
        lore.add("<gray>Adds a recipe for <white>"
                + Text.prettify(held.getType().name().toLowerCase(Locale.ROOT)) + "</white>,");
        lore.add("<gray>starting at <white>64</white> into <white>1</white>.");
        lore.add("");
        lore.add("<yellow>Click to add it");
        return Items.text(Material.LIME_DYE, "<green>Add a recipe", lore, true);
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
            openLater(player, new RecipeEditorMenu(plugin, module, page - 1));
            return;
        }
        if (raw == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new RecipeEditorMenu(plugin, module, page + 1));
            return;
        }
        if (raw == ADD_SLOT) {
            add(player);
            return;
        }
        String id = shown.get(raw);
        if (id == null) {
            return;
        }
        if (event.isShiftClick()) {
            module.recipes().remove(id);
            plugin.messages().send(player, "recipe-deleted", "id", id);
            click(player);
            redraw(player);
            return;
        }
        click(player);
        openLater(player, new RecipeEditMenu(plugin, module, id));
    }

    private void add(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.messages().send(player, "recipe-needs-item");
            return;
        }
        if (module.recipes().forInput(held.getType()) != null) {
            plugin.messages().send(player, "recipe-duplicate",
                    "id", module.recipes().forInput(held.getType()).id());
            return;
        }
        CompactRecipe recipe = new CompactRecipe(freeId(held.getType()), held.getType(), 64, null);
        if (!module.recipes().put(recipe)) {
            plugin.messages().send(player, "recipe-duplicate", "id", recipe.id());
            return;
        }
        plugin.messages().send(player, "recipe-added", "id", recipe.id());
        click(player);
        openLater(player, new RecipeEditMenu(plugin, module, recipe.id()));
    }

    /** A recipe id based on the material, made unique if that name is taken. */
    private String freeId(Material material) {
        String base = material.name().toLowerCase(Locale.ROOT);
        if (module.recipes().get(base) == null) {
            return base;
        }
        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = base + "_" + suffix;
            if (module.recipes().get(candidate) == null) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis();
    }

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }
}
