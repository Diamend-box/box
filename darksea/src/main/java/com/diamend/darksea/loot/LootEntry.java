package com.diamend.darksea.loot;

import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.item.DarkSeaItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.inventory.ItemStack;

/**
 * One weighted line of a loot table: a vanilla item stack (optionally renamed
 * and lored — flavor salvage), a sea-armor piece (the progression engine —
 * ring N chests are the source of tier N armor), or a
 * CUSTOM registry item ({@link DarkSeaItems} — Chronons, relics, weapons,
 * consumables; identity guaranteed by PDC, so a typo'd id fails at parse
 * time where CI catches it, never as a silently plain item in a chest), or a
 * SNAPSHOT — a base64 image of a whole ItemStack, captured from an admin's own
 * inventory by {@code /ds loot}. A snapshot carries name, lore, enchants,
 * model data and any other plugin's NBT, which is what lets the sea drop gear
 * this plugin knows nothing about.
 *
 * <p>{@code customId} does double duty: a registry id for CUSTOM, the base64
 * payload for SNAPSHOT. They are never both in play on one entry.
 */
public record LootEntry(Type type, Material material, int min, int max,
                        String name, List<String> lore,
                        int armorTier, String customId, int weight) {

    public enum Type {
        ITEM, ARMOR, CUSTOM, SNAPSHOT
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Parses one entry map from loot.yml; throws IllegalArgumentException on bad input. */
    public static LootEntry parse(Map<?, ?> map) {
        String typeName = String.valueOf(map.get("type")).toUpperCase(Locale.ROOT);
        Type type = Type.valueOf(typeName);
        int weight = Math.max(1, toInt(map.get("weight"), 1));
        switch (type) {
            case ITEM -> {
                Material material = Material.matchMaterial(String.valueOf(map.get("material")));
                if (material == null) {
                    throw new IllegalArgumentException("unknown material: " + map.get("material"));
                }
                int min = Math.max(1, toInt(map.get("min"), 1));
                int max = Math.max(min, toInt(map.get("max"), min));
                Object rawName = map.get("name");
                String name = rawName != null ? String.valueOf(rawName) : null;
                List<String> lore = new ArrayList<>();
                if (map.get("lore") instanceof List<?> lines) {
                    for (Object line : lines) {
                        lore.add(String.valueOf(line));
                    }
                }
                return new LootEntry(type, material, min, max, name, List.copyOf(lore), 0, null, weight);
            }
            case ARMOR -> {
                int tier = toInt(map.get("tier"), 1);
                return new LootEntry(type, null, 0, 0, null, List.of(), tier, null, weight);
            }
            case CUSTOM -> {
                String id = map.get("id") != null ? String.valueOf(map.get("id")) : null;
                if (id == null || !DarkSeaItems.allIds().contains(id)) {
                    throw new IllegalArgumentException("unknown custom item id: " + id);
                }
                int min = Math.max(1, toInt(map.get("min"), 1));
                int max = Math.max(min, toInt(map.get("max"), min));
                return new LootEntry(type, null, min, max, null, List.of(), 0, id, weight);
            }
            case SNAPSHOT -> {
                String data = map.get("data") != null ? String.valueOf(map.get("data")) : null;
                if (data == null || data.isBlank()) {
                    throw new IllegalArgumentException("snapshot entry has no data");
                }
                int min = Math.max(1, toInt(map.get("min"), 1));
                int max = Math.max(min, toInt(map.get("max"), min));
                return new LootEntry(type, null, min, max, null, List.of(), 0, data, weight);
            }
        }
        throw new IllegalArgumentException("unknown entry type: " + typeName);
    }

    /** Rolls with no island context (Chronon piles at face value). */
    public ItemStack roll(Random rng, ArmorSettings armor) {
        return roll(rng, armor, 1.0);
    }

    /**
     * Rolls this entry into a concrete item. May return null (undefined armor
     * tier). {@code chrononScale} is the island's wealth: some islands were
     * rich once and their Chronon piles still show it — only currency scales,
     * everything else ignores it.
     */
    public ItemStack roll(Random rng, ArmorSettings armor, double chrononScale) {
        return switch (type) {
            case ITEM -> {
                ItemStack item = new ItemStack(material, min + rng.nextInt(max - min + 1));
                if (name != null || !lore.isEmpty()) {
                    ItemMeta meta = item.getItemMeta();
                    if (name != null) {
                        meta.displayName(noItalic(MM.deserialize(name)));
                    }
                    if (!lore.isEmpty()) {
                        List<Component> lines = new ArrayList<>(lore.size());
                        for (String line : lore) {
                            lines.add(noItalic(MM.deserialize(line)));
                        }
                        meta.lore(lines);
                    }
                    item.setItemMeta(meta);
                }
                yield item;
            }
            case ARMOR -> {
                SeaArmor.Piece[] pieces = SeaArmor.Piece.values();
                yield SeaArmor.createPiece(armor, armorTier, pieces[rng.nextInt(pieces.length)]);
            }
            case CUSTOM -> {
                int amount = min + rng.nextInt(max - min + 1);
                if (DarkSeaItems.CHRONON.equals(customId)) {
                    amount = Math.max(1, (int) Math.round(amount * chrononScale));
                }
                yield DarkSeaItems.create(customId, amount);
            }
            case SNAPSHOT -> {
                ItemStack stack = decode(customId);
                if (stack != null) {
                    stack.setAmount(Math.min(stack.getMaxStackSize(),
                            min + rng.nextInt(max - min + 1)));
                }
                yield stack;
            }
        };
    }

    /**
     * Rebuilds a snapshot's stack, or null if the payload is unreadable — a
     * hand-edited loot-custom.yml, or an item from a plugin that has since
     * been removed. Null is survivable: {@code rollLoot} drops it and the
     * chest is one item lighter, which beats a stack trace per refill.
     */
    public static ItemStack decode(String base64) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * This entry as a loot.yml map — the inverse of {@link #parse}, so what
     * the in-game editor writes reads back as exactly the same entry.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        switch (type) {
            case ITEM -> {
                map.put("material", material.name());
                map.put("min", min);
                map.put("max", max);
                if (name != null) {
                    map.put("name", name);
                }
                if (!lore.isEmpty()) {
                    map.put("lore", List.copyOf(lore));
                }
            }
            case ARMOR -> map.put("tier", armorTier);
            case CUSTOM -> {
                map.put("id", customId);
                map.put("min", min);
                map.put("max", max);
            }
            case SNAPSHOT -> {
                map.put("data", customId);
                map.put("min", min);
                map.put("max", max);
            }
        }
        map.put("weight", weight);
        return map;
    }

    /** The same entry at a different weight — the editor's weight buttons. */
    public LootEntry withWeight(int newWeight) {
        return new LootEntry(type, material, min, max, name, lore, armorTier,
                customId, Math.max(1, newWeight));
    }

    /** The same entry at a different stack size, keeping min and max in order. */
    public LootEntry withAmount(int newMin, int newMax) {
        int lo = Math.max(1, newMin);
        return new LootEntry(type, material, lo, Math.max(lo, newMax), name, lore,
                armorTier, customId, weight);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
