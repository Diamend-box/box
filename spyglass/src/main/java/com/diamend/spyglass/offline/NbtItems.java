package com.diamend.spyglass.offline;

import java.util.ArrayList;
import java.util.List;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtList;
import com.diamend.spyglass.nbt.NbtPrinter;
import com.diamend.spyglass.nbt.NbtTag;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Renders an item as it appears in a save file, so an offline player's
 * inventory reads the same way an online one does.
 *
 * <p>Handles both shapes Mojang has used: the modern
 * {@code {id, count, components:{...}}} of 1.20.5 and later, and the older
 * {@code {id, Count, Damage, tag:{...}}} still sitting in saves that predate it.
 */
public final class NbtItems {

    /** The slot number a save uses for an item that is not in the grid. */
    public static final int OFFHAND_SLOT = -106;

    private NbtItems() {
    }

    public static String id(NbtCompound item) {
        return item == null ? "" : item.string("id", "");
    }

    public static int count(NbtCompound item) {
        if (item == null) {
            return 0;
        }
        // 1.20.5 renamed Count (byte) to count (int).
        NbtTag tag = item.firstOf("count", "Count");
        return tag == null ? 1 : tag.asInt(1);
    }

    /** The slot this item sits in; {@link Integer#MIN_VALUE} when it says nothing. */
    public static int slot(NbtCompound item) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        NbtTag tag = item.firstOf("Slot", "slot");
        return tag == null ? Integer.MIN_VALUE : tag.asInt(Integer.MIN_VALUE);
    }

    public static boolean isEmpty(NbtCompound item) {
        String id = id(item);
        return id.isEmpty() || id.endsWith(":air") || count(item) <= 0;
    }

    /** One line, in the same shape the online inspector prints. */
    public static String line(NbtCompound item) {
        if (isEmpty(item)) {
            return "empty";
        }
        StringBuilder out = new StringBuilder(Fmt.shortKey(id(item)));
        int count = count(item);
        if (count != 1) {
            out.append(" x").append(count);
        }

        NbtCompound components = item.compound("components");
        NbtCompound legacy = item.compound("tag");

        Integer damage = damage(item, components, legacy);
        if (damage != null && damage > 0) {
            out.append("  damage ").append(damage);
        }
        String name = customName(components, legacy);
        if (name != null && !name.isEmpty()) {
            out.append("  \"").append(Fmt.clip(name, 40)).append('"');
        }
        List<String> enchants = enchantments(components, legacy);
        if (!enchants.isEmpty()) {
            out.append("  {").append(String.join(", ", enchants)).append('}');
        }
        if (unbreakable(components, legacy)) {
            out.append("  unbreakable");
        }
        int inside = contents(components, legacy).size();
        if (inside > 0) {
            out.append("  holds:").append(inside);
        }
        if (components != null && components.has("minecraft:custom_data")) {
            out.append("  custom_data");
        }
        return out.toString();
    }

    /** Everything about one item, including the whole component tree. */
    public static void detail(Report report, NbtCompound item) {
        if (isEmpty(item)) {
            report.note("The slot is empty.");
            return;
        }
        report.field("id", id(item));
        report.field("count", count(item));
        int slot = slot(item);
        if (slot != Integer.MIN_VALUE) {
            report.field("slot", slot + " (" + slotLabel(slot) + ")");
        }
        NbtCompound components = item.compound("components");
        NbtCompound legacy = item.compound("tag");

        Integer damage = damage(item, components, legacy);
        if (damage != null) {
            report.field("damage", damage);
        }
        String name = customName(components, legacy);
        if (name != null && !name.isEmpty()) {
            report.field("custom name", name);
        }
        List<String> enchants = enchantments(components, legacy);
        if (!enchants.isEmpty()) {
            report.field("enchantments", String.join(", ", enchants));
        }
        for (String line : lore(components, legacy)) {
            report.text("    lore: " + line);
        }
        List<NbtCompound> inside = contents(components, legacy);
        if (!inside.isEmpty()) {
            report.field("contents", inside.size() + " item(s) inside");
            for (NbtCompound nested : inside) {
                report.text("    " + line(nested));
            }
        }
        NbtPrinter printer = new NbtPrinter(6, 32);
        report.blank();
        report.note("Raw tag:");
        for (String line : printer.print("item", item.asTag())) {
            report.text(line);
        }
    }

    /** What a vanilla save's slot number means. */
    public static String slotLabel(int slot) {
        if (slot == OFFHAND_SLOT) {
            return "offhand";
        }
        return switch (slot) {
            case 100 -> "boots";
            case 101 -> "legs";
            case 102 -> "chest";
            case 103 -> "helmet";
            default -> {
                if (slot >= 0 && slot <= 8) {
                    yield "hotbar";
                }
                yield slot >= 9 && slot <= 35 ? "pack" : "?";
            }
        };
    }

    // ------------------------------------------------------------------
    // Component and legacy-tag readers
    // ------------------------------------------------------------------

    private static Integer damage(NbtCompound item, NbtCompound components, NbtCompound legacy) {
        if (components != null && components.has("minecraft:damage")) {
            return components.integer("minecraft:damage", 0);
        }
        if (legacy != null && legacy.has("Damage")) {
            return legacy.integer("Damage", 0);
        }
        return item.has("Damage") ? item.integer("Damage", 0) : null;
    }

    private static boolean unbreakable(NbtCompound components, NbtCompound legacy) {
        if (components != null && components.has("minecraft:unbreakable")) {
            return true;
        }
        return legacy != null && legacy.bool("Unbreakable", false);
    }

    private static String customName(NbtCompound components, NbtCompound legacy) {
        if (components != null) {
            String json = components.string("minecraft:custom_name", null);
            if (json == null) {
                json = components.string("minecraft:item_name", null);
            }
            if (json != null) {
                return plainJson(json);
            }
        }
        if (legacy != null) {
            NbtCompound display = legacy.compound("display");
            if (display != null) {
                String json = display.string("Name", null);
                if (json != null) {
                    return plainJson(json);
                }
            }
        }
        return null;
    }

    private static List<String> lore(NbtCompound components, NbtCompound legacy) {
        List<String> out = new ArrayList<>();
        NbtList lines = null;
        if (components != null) {
            lines = components.list("minecraft:lore");
        }
        if (lines == null && legacy != null) {
            NbtCompound display = legacy.compound("display");
            lines = display == null ? null : display.list("Lore");
        }
        if (lines != null) {
            for (String line : lines.strings()) {
                out.add(plainJson(line));
            }
        }
        return out;
    }

    /**
     * {@code sharpness 5, unbreaking 3}, from whichever of the three layouts
     * this save uses.
     */
    private static List<String> enchantments(NbtCompound components, NbtCompound legacy) {
        List<String> out = new ArrayList<>();
        if (components != null) {
            for (String key : new String[] { "minecraft:enchantments", "minecraft:stored_enchantments" }) {
                NbtCompound block = components.compound(key);
                if (block == null) {
                    continue;
                }
                // 1.20.5 wrapped the map in "levels"; 1.21 dropped the wrapper.
                NbtCompound levels = block.compound("levels");
                NbtCompound source = levels != null ? levels : block;
                for (String enchantment : source.keys()) {
                    NbtTag level = source.get(enchantment);
                    if (level != null && level.isNumber()) {
                        out.add(Fmt.shortKey(enchantment) + " " + level.asInt(1));
                    }
                }
            }
        }
        if (out.isEmpty() && legacy != null) {
            NbtList list = legacy.list("Enchantments");
            if (list == null) {
                list = legacy.list("StoredEnchantments");
            }
            if (list != null) {
                for (NbtCompound entry : list.compounds()) {
                    out.add(Fmt.shortKey(entry.string("id", "?")) + " " + entry.integer("lvl", 1));
                }
            }
        }
        return out;
    }

    /** What is inside a shulker box or bundle held in this slot. */
    private static List<NbtCompound> contents(NbtCompound components, NbtCompound legacy) {
        List<NbtCompound> out = new ArrayList<>();
        if (components != null) {
            NbtList container = components.list("minecraft:container");
            if (container != null) {
                for (NbtCompound entry : container.compounds()) {
                    NbtCompound nested = entry.compound("item");
                    out.add(nested == null ? entry : nested);
                }
            }
            NbtList bundle = components.list("minecraft:bundle_contents");
            if (bundle != null) {
                out.addAll(bundle.compounds());
            }
        }
        if (out.isEmpty() && legacy != null) {
            NbtCompound blockEntity = legacy.compound("BlockEntityTag");
            NbtList items = blockEntity == null ? null : blockEntity.list("Items");
            if (items != null) {
                out.addAll(items.compounds());
            }
        }
        return out;
    }

    /**
     * Names and lore are stored as JSON chat components. Rendered as the text a
     * player would see; if it isn't JSON after all, as itself.
     */
    static String plainJson(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
            return trimmed;
        }
        return Safe.call(() -> Fmt.plain(GsonComponentSerializer.gson().deserialize(trimmed)), trimmed);
    }
}
