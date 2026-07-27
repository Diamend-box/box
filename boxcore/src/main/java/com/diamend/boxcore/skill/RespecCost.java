package com.diamend.boxcore.skill;

import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The item a respec costs, read from {@code skills.respec-item}.
 *
 * <p>An optional display name narrows it further, so a server can hand out a
 * purpose-made "Respec Token" that a plain item of the same material won't
 * satisfy.
 */
public record RespecCost(Material material, int amount, String name) {

    /** A cost that's always met — respeccing is free. */
    public static final RespecCost FREE = new RespecCost(null, 0, "");

    public static RespecCost from(ConfigurationSection section) {
        if (section == null) {
            return FREE;
        }
        Material material = Items.material(section.getString("item"), null);
        int amount = Math.max(0, section.getInt("amount", 1));
        if (material == null || amount <= 0) {
            return FREE;
        }
        return new RespecCost(material, amount, section.getString("name", ""));
    }

    public boolean isFree() {
        return material == null || amount <= 0;
    }

    /** The cost's name for messages — the configured display name, or the item's. */
    public String displayName() {
        if (isFree()) {
            return "";
        }
        return name == null || name.isBlank() ? Text.prettify(material.name()) : name;
    }

    /** Whether a stack counts toward this cost. */
    public boolean matches(ItemStack stack) {
        if (isFree() || stack == null || stack.getType() != material) {
            return false;
        }
        if (name == null || name.isBlank()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return false;
        }
        // Compare the rendered text, so the config can be written with either
        // MiniMessage tags or legacy colour codes and still match.
        return Text.plain(name).equals(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName()));
    }

    /** How many matching items the player is carrying. */
    public int heldBy(Player player) {
        if (isFree()) {
            return 0;
        }
        int held = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack)) {
                held += stack.getAmount();
            }
        }
        return held;
    }

    public boolean canAfford(Player player) {
        return isFree() || heldBy(player) >= amount;
    }

    /**
     * Removes the cost from a player's inventory.
     *
     * @return true when the full amount was taken
     */
    public boolean take(Player player) {
        if (isFree()) {
            return true;
        }
        if (!canAfford(player)) {
            return false;
        }
        int owed = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && owed > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack)) {
                continue;
            }
            int taken = Math.min(owed, stack.getAmount());
            owed -= taken;
            if (taken >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - taken);
            }
        }
        player.getInventory().setStorageContents(contents);
        return owed == 0;
    }
}
