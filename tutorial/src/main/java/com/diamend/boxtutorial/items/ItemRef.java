package com.diamend.boxtutorial.items;

import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * A reference to an item in config: either a plain material, or one of the
 * registry's named slots.
 *
 * <pre>
 *   OAK_LOG 64          64 oak logs, whatever they are
 *   item:charm          whatever `charm` currently means
 *   item:compressed_log 1
 * </pre>
 *
 * <p>Named slots are resolved when they're used rather than when they're read,
 * so binding your own charm changes what the trade hands over without a reload.
 */
public record ItemRef(String itemId, Material material, int amount) {

    private static final String PREFIX = "item:";

    /** Parses a config value. Returns null when it names nothing usable. */
    public static ItemRef parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] parts = spec.trim().split("[\\s*]+");
        String name = parts[0].trim();
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (name.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            String id = Text.lower(name.substring(PREFIX.length())).trim();
            return id.isEmpty() ? null : new ItemRef(id, null, amount);
        }
        Material found = Items.material(name, null);
        return found == null ? null : new ItemRef(null, found, amount);
    }

    public boolean isCustom() {
        return itemId != null;
    }

    /** The actual stack this refers to right now, or null if it's unresolvable. */
    public ItemStack resolve(ItemRegistry registry) {
        if (isCustom()) {
            return registry == null ? null : registry.get(itemId, amount);
        }
        return new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
    }

    /** True when that stack is what this refers to, ignoring how many. */
    public boolean matches(ItemRegistry registry, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return isCustom()
                ? registry != null && registry.matches(itemId, stack)
                : stack.getType() == material;
    }

    /** {@code 64x Oak Log}, or the bound item's own name for a named slot. */
    public String describe(ItemRegistry registry) {
        String name = isCustom()
                ? (registry == null ? Text.prettify(itemId) : registry.label(itemId))
                : Text.prettify(material.name().toLowerCase(Locale.ROOT));
        return amount + "x " + name;
    }
}
