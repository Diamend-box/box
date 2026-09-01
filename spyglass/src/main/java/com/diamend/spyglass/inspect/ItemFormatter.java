package com.diamend.spyglass.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;

import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

import net.kyori.adventure.text.Component;

/**
 * Turns a live {@link ItemStack} into something worth reading in a terminal:
 * one dense line for a slot listing, or a full page for a single slot.
 */
public final class ItemFormatter {

    private ItemFormatter() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    /**
     * One line: {@code diamond_sword x1  dur 1550/1561  "Excalibur"
     * {sharpness 5, unbreaking 3}  lore:2  pdc:1}.
     */
    public static String line(ItemStack item) {
        if (isEmpty(item)) {
            return "empty";
        }
        StringBuilder out = new StringBuilder();
        out.append(Safe.text(() -> item.getType().getKey().getKey()));
        int amount = Safe.integer(item::getAmount, 1);
        if (amount != 1) {
            out.append(" x").append(amount);
        }
        Safe.run(() -> {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            String durability = durability(item, meta);
            if (durability != null) {
                out.append("  ").append(durability);
            }
            if (meta.hasDisplayName()) {
                out.append("  \"").append(Fmt.clip(Fmt.plain(meta.displayName()), 40)).append('"');
            }
            String enchants = enchantments(meta);
            if (!enchants.isEmpty()) {
                out.append("  {").append(enchants).append('}');
            }
            if (meta.isUnbreakable()) {
                out.append("  unbreakable");
            }
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                out.append("  lore:").append(lore.size());
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc != null && !pdc.getKeys().isEmpty()) {
                out.append("  pdc:").append(pdc.getKeys().size());
            }
            int contents = containerCount(meta);
            if (contents > 0) {
                out.append("  holds:").append(contents);
            }
        });
        return out.toString();
    }

    /** Everything known about one item, as its own little report. */
    public static void detail(Report report, ItemStack item) {
        if (isEmpty(item)) {
            report.note("The slot is empty.");
            return;
        }
        report.field("material", Safe.text(() -> item.getType().getKey().toString()));
        report.field("amount", Safe.integer(item::getAmount, 0)
                + " (stacks to " + Safe.integer(() -> item.getType().getMaxStackSize(), 0) + ")");

        ItemMeta meta = Safe.call(item::getItemMeta, null);
        if (meta == null) {
            report.note("The item has no metadata.");
            return;
        }
        report.field("meta type", meta.getClass().getSimpleName());

        Safe.run(() -> {
            String durability = durability(item, meta);
            if (durability != null) {
                report.field("durability", durability);
            }
        });
        Safe.run(() -> {
            if (meta.hasDisplayName()) {
                report.field("display name", Fmt.plain(meta.displayName()));
            }
            if (meta.hasItemName()) {
                report.field("item name", Fmt.plain(meta.itemName()));
            }
        });
        Safe.run(() -> {
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                report.field("lore", lore.size() + " line(s)");
                for (Component line : lore) {
                    report.text("    " + Fmt.plain(line));
                }
            }
        });
        Safe.run(() -> {
            String enchants = enchantments(meta);
            if (!enchants.isEmpty()) {
                report.field("enchantments", enchants);
            }
        });
        Safe.run(() -> {
            if (meta.isUnbreakable()) {
                report.field("unbreakable", "yes");
            }
            if (meta.hasCustomModelData()) {
                report.field("custom model data", meta.getCustomModelData());
            }
            if (!meta.getItemFlags().isEmpty()) {
                report.field("flags", meta.getItemFlags().toString());
            }
        });
        Safe.run(() -> {
            if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
                report.field("attribute modifiers", meta.getAttributeModifiers().size());
                meta.getAttributeModifiers().forEach((attribute, modifier) ->
                        report.text("    " + attribute.getKey().getKey() + " "
                                + modifier.getOperation() + " " + Fmt.num(modifier.getAmount())
                                + " (" + modifier.getSlotGroup() + ")"));
            }
        });
        Safe.run(() -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc != null && !pdc.getKeys().isEmpty()) {
                report.field("persistent data", pdc.getKeys().size() + " key(s)");
                for (NamespacedKey key : pdc.getKeys()) {
                    report.text("    " + key + " = " + PdcReader.read(pdc, key));
                }
            }
        });
        Safe.run(() -> {
            if (meta instanceof SkullMeta skull && skull.getOwningPlayer() != null) {
                report.field("skull owner", skull.getOwningPlayer().getName()
                        + " (" + skull.getOwningPlayer().getUniqueId() + ")");
            }
            if (meta instanceof BookMeta book) {
                report.field("book", Fmt.clip(Fmt.plain(book.title()), 40)
                        + " by " + Fmt.clip(Fmt.plain(book.author()), 40)
                        + ", " + book.getPageCount() + " page(s)");
            }
            if (meta instanceof PotionMeta potion) {
                report.field("base potion", String.valueOf(potion.getBasePotionType()));
                for (var effect : potion.getCustomEffects()) {
                    report.text("    " + effect.getType().getKey().getKey()
                            + " " + (effect.getAmplifier() + 1)
                            + " for " + Fmt.ticks(effect.getDuration()));
                }
            }
        });
        Safe.run(() -> {
            List<ItemStack> contents = containerContents(meta);
            if (!contents.isEmpty()) {
                report.field("contents", contents.size() + " item(s) inside");
                for (int i = 0; i < contents.size(); i++) {
                    report.text("    " + line(contents.get(i)));
                }
            }
        });
    }

    /** {@code dur 1550/1561}, or null for an item that cannot break. */
    private static String durability(ItemStack item, ItemMeta meta) {
        short max = item.getType().getMaxDurability();
        if (max <= 0 || !(meta instanceof Damageable damageable) || !damageable.hasDamage()) {
            return null;
        }
        return "dur " + (max - damageable.getDamage()) + "/" + max;
    }

    /** {@code sharpness 5, unbreaking 3}, including a book's stored enchantments. */
    private static String enchantments(ItemMeta meta) {
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (meta instanceof EnchantmentStorageMeta stored && !stored.getStoredEnchants().isEmpty()) {
            enchants = stored.getStoredEnchants();
        }
        if (enchants.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(enchants.size());
        enchants.forEach((enchantment, level) ->
                parts.add(Fmt.shortKey(enchantment.getKey().getKey()) + " " + level));
        return String.join(", ", parts);
    }

    /** What is inside a shulker box (or other container item), if anything. */
    private static List<ItemStack> containerContents(ItemMeta meta) {
        List<ItemStack> out = new ArrayList<>();
        if (meta instanceof BlockStateMeta blockState && blockState.hasBlockState()
                && blockState.getBlockState() instanceof Container container) {
            for (ItemStack inside : container.getInventory().getContents()) {
                if (!isEmpty(inside)) {
                    out.add(inside);
                }
            }
        }
        return out;
    }

    private static int containerCount(ItemMeta meta) {
        return Safe.integer(() -> containerContents(meta).size(), 0);
    }
}
