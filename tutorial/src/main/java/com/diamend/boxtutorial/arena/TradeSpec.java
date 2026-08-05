package com.diamend.boxtutorial.arena;

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
public record TradeSpec(String id, List<ItemStack> cost, ItemStack result) {

    public TradeSpec {
        cost = List.copyOf(cost);
    }

    /** Builds the vanilla recipe. Uses are effectively unlimited. */
    public MerchantRecipe toRecipe() {
        MerchantRecipe recipe = new MerchantRecipe(result.clone(), Integer.MAX_VALUE);
        recipe.setExperienceReward(false);
        recipe.setVillagerExperience(0);
        List<ItemStack> ingredients = new ArrayList<>();
        for (int index = 0; index < Math.min(2, cost.size()); index++) {
            ingredients.add(cost.get(index).clone());
        }
        recipe.setIngredients(ingredients);
        return recipe;
    }

    /** {@code 8x Oak Log} — how the trade reads in chat and in a step's lore. */
    public String describeCost() {
        StringBuilder out = new StringBuilder();
        for (ItemStack item : cost) {
            if (out.length() > 0) {
                out.append(" + ");
            }
            out.append(item.getAmount()).append("x ")
                    .append(com.diamend.boxtutorial.util.Text.prettify(
                            item.getType().name().toLowerCase(java.util.Locale.ROOT)));
        }
        return out.toString();
    }
}
