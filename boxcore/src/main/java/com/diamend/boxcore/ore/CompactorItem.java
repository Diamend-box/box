package com.diamend.boxcore.ore;

import com.diamend.boxcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The personal compactor: an item you carry that folds up whichever recipes you
 * have slotted into it.
 *
 * <p>The assignments live on the item rather than in the player's profile, so a
 * compactor means the same thing wherever it is — handed to someone else,
 * parked in a chest, or held by an admin testing a recipe. What it is set to is
 * a property of the object, not of whoever happens to be holding it.
 *
 * <p>Slot <em>count</em>, unlike the assignments, is read from config by tier
 * rather than off the item. Retuning how many slots a tier grants should reach
 * the compactors already in circulation — the alternative is a server owner
 * changing a number and wondering why nothing moved. The count written on the
 * item is only a fallback for a tier that has since been deleted.
 */
public final class CompactorItem {

    private final NamespacedKey tierKey;
    private final NamespacedKey slotsKey;
    private final NamespacedKey filterKey;

    private final CompactorTiers tiers;
    private final CompactRecipes recipes;

    public CompactorItem(Plugin plugin, CompactorTiers tiers, CompactRecipes recipes) {
        this.tierKey = new NamespacedKey(plugin, "compactor");
        this.slotsKey = new NamespacedKey(plugin, "compactor_slots");
        this.filterKey = new NamespacedKey(plugin, "compactor_filter");
        this.tiers = tiers;
        this.recipes = recipes;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /** A fresh compactor of a tier, with nothing slotted into it. */
    public ItemStack create(CompactorTier tier) {
        if (tier == null) {
            return null;
        }
        ItemStack item = new ItemStack(tier.material(), 1);
        return write(item, tier, List.of());
    }

    /** The same compactor with a different set of assignments. */
    public ItemStack withFilters(ItemStack source, List<String> filters) {
        if (!isCompactor(source)) {
            return source;
        }
        return write(source.clone(), tierOf(source), filters);
    }

    private ItemStack write(ItemStack item, CompactorTier tier, List<String> filters) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // No metadata means no tag, and an untagged compactor is just a
            // hopper. Hand back nothing rather than a thing that looks right
            // and does nothing.
            return null;
        }
        int slots = tier == null ? filters.size() : tier.slots();
        List<String> trimmed = new ArrayList<>(filters.subList(0, Math.min(filters.size(), slots)));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(tierKey, PersistentDataType.INTEGER, tier == null ? 1 : tier.level());
        data.set(slotsKey, PersistentDataType.INTEGER, slots);
        data.set(filterKey, PersistentDataType.STRING, String.join(",", trimmed));

        meta.displayName(Text.item(tier == null ? "<aqua>Personal Compactor" : tier.name()));
        meta.lore(lore(slots, trimmed));
        if (tier != null && tier.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> lore(int slots, List<String> filters) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.item("<gray>Compacts what you slot into it,"));
        lore.add(Text.item("<gray>as you gather it."));
        lore.add(Component.empty());
        for (int index = 0; index < slots; index++) {
            String id = index < filters.size() ? filters.get(index) : "";
            CompactRecipe recipe = id.isBlank() ? null : recipes.get(id);
            if (recipe == null) {
                lore.add(Text.item("<dark_gray>" + (index + 1) + ". empty"));
            } else {
                lore.add(Text.item("<gray>" + (index + 1) + ". <white>" + recipe.display()
                        + " <dark_gray>(" + Text.number(recipe.amount()) + " → 1)"));
            }
        }
        lore.add(Component.empty());
        lore.add(Text.item("<yellow>Right-click to configure"));
        lore.add(Text.item("<dark_gray>Can't be dropped or lost on death."));
        return lore;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public boolean isCompactor(ItemStack item) {
        PersistentDataContainer data = data(item);
        return data != null && data.has(tierKey, PersistentDataType.INTEGER);
    }

    public int tierLevel(ItemStack item) {
        PersistentDataContainer data = data(item);
        if (data == null) {
            return 0;
        }
        Integer stored = data.get(tierKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    /** The configured tier this item belongs to, or null when it was removed. */
    public CompactorTier tierOf(ItemStack item) {
        return tiers.get(tierLevel(item));
    }

    /**
     * How many recipes this compactor holds. Read from its tier so retuning
     * reaches items already in circulation; the number written on the item is
     * used only when the tier no longer exists.
     */
    public int slots(ItemStack item) {
        CompactorTier tier = tierOf(item);
        if (tier != null) {
            return tier.slots();
        }
        PersistentDataContainer data = data(item);
        Integer stored = data == null ? null : data.get(slotsKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, stored);
    }

    /**
     * The recipe ids slotted into this compactor, one entry per slot, blank
     * where a slot is empty. Always exactly {@link #slots(ItemStack)} long, so
     * callers can index it without checking.
     */
    public List<String> filters(ItemStack item) {
        int slots = slots(item);
        List<String> result = new ArrayList<>(slots);
        PersistentDataContainer data = data(item);
        String stored = data == null ? null : data.get(filterKey, PersistentDataType.STRING);
        List<String> parts = stored == null || stored.isEmpty()
                ? List.of() : Arrays.asList(stored.split(",", -1));
        for (int index = 0; index < slots; index++) {
            String id = index < parts.size() ? parts.get(index).trim() : "";
            result.add(id);
        }
        return result;
    }

    /** The recipes this compactor will actually act on, skipping empty slots. */
    public List<CompactRecipe> active(ItemStack item) {
        List<CompactRecipe> active = new ArrayList<>();
        for (String id : filters(item)) {
            CompactRecipe recipe = id.isBlank() ? null : recipes.get(id);
            if (recipe != null) {
                active.add(recipe);
            }
        }
        return active;
    }

    private PersistentDataContainer data(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}
