package com.diamend.boxtutorial.arena;

import com.diamend.boxtutorial.items.ItemRef;
import com.diamend.boxtutorial.items.ItemRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * One line on the trader's screen: what it costs, and what you get.
 *
 * <p>These become real {@link MerchantRecipe}s on a real villager, so the
 * player sees the vanilla trade window they'd see anywhere else — the arrow,
 * the two cost slots, the result. Nothing to learn that isn't Minecraft.
 *
 * <p>Vanilla allows at most two ingredients per trade, which is also plenty:
 * the currency here is ore.
 */
public record TradeSpec(String id, List<ItemRef> cost, ItemRef result) {

    public TradeSpec {
        cost = List.copyOf(cost);
    }

    /**
     * Builds the vanilla recipe from whatever the named slots mean right now.
     *
     * @return the recipe, or null when something it refers to isn't defined
     */
    public MerchantRecipe toRecipe(ItemRegistry registry) {
        ItemStack product = result.resolve(registry);
        if (product == null) {
            return null;
        }
        List<ItemStack> ingredients = new ArrayList<>();
        for (int index = 0; index < Math.min(2, cost.size()); index++) {
            ItemStack item = cost.get(index).resolve(registry);
            if (item == null) {
                return null;
            }
            ingredients.add(item);
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        MerchantRecipe recipe = new MerchantRecipe(product, Integer.MAX_VALUE);
        recipe.setExperienceReward(false);
        recipe.setVillagerExperience(0);
        recipe.setIngredients(ingredients);
        return recipe;
    }

    /** True when paying for this needs one of the plugin's own items. */
    public boolean hasCustomCost() {
        for (ItemRef ref : cost) {
            if (ref.isCustom()) {
                return true;
            }
        }
        return false;
    }

    /** {@code 64x Oak Log} — how the trade reads in chat and in a step's lore. */
    public String describeCost(ItemRegistry registry) {
        StringBuilder out = new StringBuilder();
        for (ItemRef ref : cost) {
            if (out.length() > 0) {
                out.append(" + ");
            }
            out.append(ref.describe(registry));
        }
        return out.toString();
    }
}
