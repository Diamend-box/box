package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.ore.CompactRecipe;
import com.diamend.boxcore.ore.CompressedOre;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One recipe: how much makes a unit, and what that unit looks like.
 *
 * <p>The look can be captured whole from an item: name something in an anvil,
 * hold it, click, and the skin, the name, the lore and the model data all come
 * across. The name and the lore can also be typed, which is the only way to use
 * the <code>&lt;ratio&gt;</code> and <code>&lt;ore&gt;</code> tokens — an anvil
 * writes a fixed string, and "Worth 64 coal" stops being true the moment the
 * ratio changes.
 *
 * <p>Editing a recipe does not reach units already in circulation: what a unit
 * is worth is written on it at creation, so old stacks keep working and simply
 * look like the older recipe.
 */
public class RecipeEditMenu extends AbstractMenu {

    private static final int PREVIEW_SLOT = 4;
    private static final int AMOUNT_SLOT = 22;
    private static final int NAME_SLOT = 27;
    private static final int LOOK_SLOT = 29;
    private static final int GLOW_SLOT = 31;
    private static final int RESET_SLOT = 33;
    private static final int LORE_SLOT = 35;
    private static final int DELETE_SLOT = 40;
    private static final int BACK_SLOT = 36;
    private static final int CLOSE_SLOT = 44;

    /** Menu slot to how much it changes the amount by. */
    private static final Map<Integer, Integer> STEPS = Map.of(
            19, -64, 20, -10, 21, -1,
            23, 1, 24, 10, 25, 64);

    private static final int MIN_AMOUNT = 2;
    private static final int MAX_AMOUNT = 10_000;

    private final CompressorModule module;
    private final String id;

    public RecipeEditMenu(BoxCorePlugin plugin, CompressorModule module, String id) {
        super(plugin, 45, "<dark_gray>Box <gray>| <gold>Recipe");
        this.module = module;
        this.id = id;
    }

    private CompactRecipe recipe() {
        return module.recipes().get(id);
    }

    @Override
    protected void build(Player player) {
        CompactRecipe recipe = recipe();
        if (recipe == null) {
            set(PREVIEW_SLOT, Items.text(Material.BARRIER, "<red>Recipe gone",
                    List.of("<gray>It isn't on the books any more."), false));
            closeButton(CLOSE_SLOT);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        set(PREVIEW_SLOT, preview(recipe));
        for (Map.Entry<Integer, Integer> step : STEPS.entrySet()) {
            set(step.getKey(), stepIcon(recipe, step.getValue()));
        }
        set(AMOUNT_SLOT, Items.text(recipe.input(),
                "<gold>" + Text.number(recipe.amount()) + " <gray>→ <white>1",
                List.of("<gray>How much of <white>" + recipe.display()
                                + "</white> makes one unit.",
                        "<dark_gray>id: " + recipe.id()), false));

        set(NAME_SLOT, nameIcon(recipe));
        set(LORE_SLOT, loreIcon(recipe));
        set(LOOK_SLOT, lookIcon(player, recipe));
        set(GLOW_SLOT, Items.text(recipe.appearance().glow()
                        ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                recipe.appearance().glow() ? "<yellow>Glow: ON" : "<gray>Glow: OFF",
                List.of("<gray>Whether the unit shimmers.", "", "<yellow>Click to flip it"), false));
        set(RESET_SLOT, Items.text(Material.WATER_BUCKET, "<yellow>Reset the look",
                List.of("<gray>Back to the plain <white>" + recipe.display() + "</white> item,",
                        "<gray>with the built-in name and lore."), false));
        set(DELETE_SLOT, Items.text(Material.BARRIER, "<red>Delete this recipe",
                List.of("<gray>Compactors slotted with it will",
                        "<gray>simply stop folding it.",
                        "",
                        "<red>Shift-click to delete"), false));

        set(BACK_SLOT, Items.text(Material.ARROW, "<yellow>« Recipes", List.of(), false));
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    /** The actual item a player will get, built the way the real one is. */
    private ItemStack preview(CompactRecipe recipe) {
        ItemStack unit = plugin.ores().compressed()
                .create(recipe.input(), recipe.appearance(), recipe.amount(), 1);
        return unit == null
                ? Items.text(Material.BARRIER, "<red>Can't build this unit", List.of(), false)
                : unit;
    }

    private ItemStack stepIcon(CompactRecipe recipe, int step) {
        int result = clamp(recipe.amount() + step);
        String label = (step > 0 ? "<green>+" : "<red>") + step;
        return Items.text(step > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                label, List.of(result == recipe.amount()
                        ? "<dark_gray>Already at the limit."
                        : "<gray>Makes it <white>" + Text.number(result) + "</white> → 1."), false);
    }

    private ItemStack nameIcon(CompactRecipe recipe) {
        String name = recipe.appearance().name();
        List<String> lore = new ArrayList<>();
        lore.add(name == null || name.isBlank()
                ? "<gray>Now: <white>the built-in name"
                : "<gray>Now: <white>" + name);
        lore.add("");
        lore.add("<gray><white><ratio></white> becomes the amount and");
        lore.add("<gray><white><ore></white> the ore, so the name stays");
        lore.add("<gray>true when you change the ratio.");
        lore.add("");
        lore.add("<yellow>Click to type a name");
        lore.add("<yellow>Shift-click for the built-in one");
        return Items.text(Material.NAME_TAG, "<yellow>Name the unit", lore, false);
    }

    private ItemStack loreIcon(CompactRecipe recipe) {
        List<String> current = recipe.appearance().lore();
        List<String> lore = new ArrayList<>();
        if (current == null || current.isEmpty()) {
            lore.add("<gray>Now: <white>the built-in lines");
        } else {
            lore.add("<gray>Now:");
            for (String line : current) {
                lore.add("  <dark_gray>" + line);
            }
        }
        lore.add("");
        lore.add("<yellow>Click to add a line");
        lore.add("<yellow>Right-click to drop the last one");
        lore.add("<yellow>Shift-click for the built-in lines");
        return Items.text(Material.WRITABLE_BOOK, "<yellow>Describe the unit", lore, false);
    }

    private ItemStack lookIcon(Player player, CompactRecipe recipe) {
        ItemStack held = player.getInventory().getItemInMainHand();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Takes the skin, name, lore and model");
        lore.add("<gray>data from the item in your hand.");
        lore.add("");
        if (held == null || held.getType().isAir()) {
            lore.add("<dark_gray>Hold something first.");
        } else if (held.getType().isBlock()) {
            lore.add("<red>A placeable block can't be a skin —");
            lore.add("<red>placing one would move items out of");
            lore.add("<red>the inventory uncounted.");
        } else {
            lore.add("<yellow>Click to use "
                    + Text.prettify(held.getType().name().toLowerCase(Locale.ROOT)));
        }
        return Items.text(Material.ITEM_FRAME, "<yellow>Set the look from your hand", lore, false);
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CompactRecipe recipe = recipe();
        if (recipe == null) {
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
            openLater(player, new RecipeEditorMenu(plugin, module, 0));
            return;
        }
        if (raw == DELETE_SLOT) {
            if (!event.isShiftClick()) {
                plugin.messages().send(player, "recipe-confirm-delete");
                return;
            }
            module.recipes().remove(recipe.id());
            plugin.messages().send(player, "recipe-deleted", "id", recipe.id());
            click(player);
            openLater(player, new RecipeEditorMenu(plugin, module, 0));
            return;
        }
        if (raw == GLOW_SLOT) {
            CompressedOre.Appearance look = recipe.appearance();
            save(player, new CompactRecipe(recipe.id(), recipe.input(), recipe.amount(),
                    new CompressedOre.Appearance(look.material(), look.name(), look.lore(),
                            look.modelData(), !look.glow())));
            return;
        }
        if (raw == RESET_SLOT) {
            save(player, new CompactRecipe(recipe.id(), recipe.input(), recipe.amount(),
                    CompressedOre.Appearance.defaultFor(recipe.input())));
            return;
        }
        if (raw == LOOK_SLOT) {
            setLook(player, recipe);
            return;
        }
        if (raw == NAME_SLOT) {
            setName(player, recipe, event.isShiftClick());
            return;
        }
        if (raw == LORE_SLOT) {
            setLore(player, recipe, event.isShiftClick(), event.isRightClick());
            return;
        }
        Integer step = STEPS.get(raw);
        if (step != null) {
            int amount = clamp(recipe.amount() + step);
            if (amount != recipe.amount()) {
                save(player, new CompactRecipe(recipe.id(), recipe.input(), amount,
                        recipe.appearance()));
            }
        }
    }

    /**
     * Types the unit's name, or puts the built-in one back.
     *
     * <p>The answer is applied against the recipe as it is when the answer
     * arrives, not the copy this menu was drawn from — somebody else may have
     * edited it while the question was open, and only the name was being asked
     * about.
     */
    private void setName(Player player, CompactRecipe recipe, boolean clear) {
        if (clear) {
            save(player, withName(recipe, null));
            return;
        }
        click(player);
        plugin.prompts().ask(player,
                "<gold>What should a unit of <white>" + recipe.display() + "</white> be called?",
                answer -> {
                    CompactRecipe now = recipe();
                    if (now != null) {
                        module.recipes().put(withName(now, answer));
                    }
                    reopen(player);
                },
                () -> reopen(player));
    }

    /** Adds a line, drops the last one, or puts the built-in lines back. */
    private void setLore(Player player, CompactRecipe recipe, boolean clear, boolean removeLast) {
        List<String> lines = recipe.appearance().lore();
        if (clear) {
            save(player, withLore(recipe, null));
            return;
        }
        if (removeLast) {
            if (lines == null || lines.isEmpty()) {
                return;
            }
            List<String> shorter = new ArrayList<>(lines);
            shorter.remove(shorter.size() - 1);
            save(player, withLore(recipe, shorter.isEmpty() ? null : shorter));
            return;
        }
        click(player);
        plugin.prompts().ask(player,
                "<gold>What line should be added under the name?",
                answer -> {
                    CompactRecipe now = recipe();
                    if (now != null) {
                        List<String> longer = now.appearance().lore() == null
                                ? new ArrayList<>() : new ArrayList<>(now.appearance().lore());
                        longer.add(answer);
                        module.recipes().put(withLore(now, longer));
                    }
                    reopen(player);
                },
                () -> reopen(player));
    }

    private CompactRecipe withName(CompactRecipe recipe, String name) {
        CompressedOre.Appearance look = recipe.appearance();
        return new CompactRecipe(recipe.id(), recipe.input(), recipe.amount(),
                new CompressedOre.Appearance(look.material(), name, look.lore(),
                        look.modelData(), look.glow()));
    }

    private CompactRecipe withLore(CompactRecipe recipe, List<String> lore) {
        CompressedOre.Appearance look = recipe.appearance();
        return new CompactRecipe(recipe.id(), recipe.input(), recipe.amount(),
                new CompressedOre.Appearance(look.material(), look.name(), lore,
                        look.modelData(), look.glow()));
    }

    /** Reopens this screen after a question, which closed it to make chat readable. */
    private void reopen(Player player) {
        new RecipeEditMenu(plugin, module, id).open(player);
    }

    /** Copies the held item's presentation onto the recipe. */
    private void setLook(Player player, CompactRecipe recipe) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.messages().send(player, "recipe-needs-item");
            return;
        }
        if (held.getType().isBlock()) {
            plugin.messages().send(player, "recipe-placeable");
            return;
        }
        ItemMeta meta = held.getItemMeta();
        String name = null;
        List<String> lore = null;
        int modelData = 0;
        if (meta != null) {
            if (meta.hasDisplayName()) {
                name = Text.serialize(meta.displayName());
            }
            List<Component> lines = meta.lore();
            if (lines != null && !lines.isEmpty()) {
                lore = new ArrayList<>();
                for (Component line : lines) {
                    lore.add(Text.serialize(line));
                }
            }
            if (meta.hasCustomModelData()) {
                modelData = meta.getCustomModelData();
            }
        }
        save(player, new CompactRecipe(recipe.id(), recipe.input(), recipe.amount(),
                new CompressedOre.Appearance(held.getType(), name, lore, modelData,
                        recipe.appearance().glow())));
    }

    private void save(Player player, CompactRecipe updated) {
        if (!module.recipes().put(updated)) {
            plugin.messages().send(player, "recipe-duplicate", "id", updated.id());
            return;
        }
        click(player);
        getInventory().clear();
        build(player);
    }

    private static int clamp(int amount) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount));
    }
}
