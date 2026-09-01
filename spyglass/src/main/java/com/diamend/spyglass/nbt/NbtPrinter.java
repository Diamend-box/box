package com.diamend.spyglass.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a parsed tag as an indented tree, in the spirit of SNBT but built to
 * be read in a terminal rather than pasted back into a command.
 *
 * <pre>
 * abilities: compound(6)
 *   flying: 0b
 *   flySpeed: 0.05f
 * Inventory: list(3 x compound)
 *   [0]: compound(4)
 *     id: "minecraft:diamond_sword"
 *     count: 1
 * </pre>
 *
 * <p>Depth and element limits keep a console readable when someone points this
 * at a shulker box full of shulker boxes.
 */
public final class NbtPrinter {

    private static final String INDENT = "  ";

    private final int maxDepth;
    private final int maxElements;

    public NbtPrinter(int maxDepth, int maxElements) {
        this.maxDepth = Math.max(1, maxDepth);
        this.maxElements = Math.max(1, maxElements);
    }

    /** The tree under {@code name}, one string per line, already indented. */
    public List<String> print(String name, NbtTag tag) {
        List<String> lines = new ArrayList<>();
        append(lines, name, tag, 0);
        return lines;
    }

    private void append(List<String> lines, String name, NbtTag tag, int depth) {
        String prefix = INDENT.repeat(depth) + (name == null || name.isEmpty() ? "" : name + ": ");
        if (tag == null) {
            lines.add(prefix + "(nothing)");
            return;
        }
        switch (tag.type()) {
            case COMPOUND -> {
                NbtCompound compound = tag.asCompound();
                int size = compound == null ? 0 : compound.size();
                lines.add(prefix + "compound(" + size + ")");
                if (compound == null || size == 0) {
                    return;
                }
                if (depth + 1 > maxDepth) {
                    lines.add(INDENT.repeat(depth + 1) + "... " + size + " more, deeper than the limit");
                    return;
                }
                for (String key : compound.keys()) {
                    append(lines, key, compound.get(key), depth + 1);
                }
            }
            case LIST -> {
                NbtList list = tag.asList();
                int size = list == null ? 0 : list.size();
                lines.add(prefix + "list(" + size
                        + (size == 0 ? "" : " x " + list.elementType().label()) + ")");
                if (list == null || size == 0) {
                    return;
                }
                if (depth + 1 > maxDepth) {
                    lines.add(INDENT.repeat(depth + 1) + "... " + size + " more, deeper than the limit");
                    return;
                }
                int shown = Math.min(size, maxElements);
                for (int i = 0; i < shown; i++) {
                    append(lines, "[" + i + "]", list.get(i), depth + 1);
                }
                if (shown < size) {
                    lines.add(INDENT.repeat(depth + 1) + "... " + (size - shown) + " more");
                }
            }
            default -> lines.add(prefix + scalar(tag));
        }
    }

    /** One tag on one line: {@code 20.0f}, {@code "minecraft:stone"}, {@code int[4] [...]}. */
    public String scalar(NbtTag tag) {
        if (tag == null) {
            return "(nothing)";
        }
        return switch (tag.type()) {
            case BYTE -> tag.asInt(0) + "b";
            case SHORT -> tag.asInt(0) + "s";
            case INT -> String.valueOf(tag.asInt(0));
            case LONG -> tag.asLong(0L) + "L";
            case FLOAT -> trim(tag.asFloat(0f)) + "f";
            case DOUBLE -> trim(tag.asDouble(0d)) + "d";
            case STRING -> "\"" + tag.asString("") + "\"";
            case BYTE_ARRAY -> bytes(tag.asByteArray());
            case INT_ARRAY -> ints(tag.asIntArray());
            case LONG_ARRAY -> longs(tag.asLongArray());
            case COMPOUND -> "compound(" + (tag.asCompound() == null ? 0 : tag.asCompound().size()) + ")";
            case LIST -> "list(" + (tag.asList() == null ? 0 : tag.asList().size()) + ")";
            case END -> "end";
        };
    }

    private String bytes(byte[] array) {
        if (array == null) {
            return "byte[0]";
        }
        StringBuilder out = new StringBuilder("byte[").append(array.length).append("]");
        int shown = Math.min(array.length, maxElements);
        if (shown > 0) {
            out.append(" [");
            for (int i = 0; i < shown; i++) {
                out.append(i == 0 ? "" : " ")
                        .append(String.format(Locale.ROOT, "%02x", array[i]));
            }
            out.append(shown < array.length ? " ...]" : "]");
        }
        return out.toString();
    }

    private String ints(int[] array) {
        if (array == null) {
            return "int[0]";
        }
        StringBuilder out = new StringBuilder("int[").append(array.length).append("] [");
        int shown = Math.min(array.length, maxElements);
        for (int i = 0; i < shown; i++) {
            out.append(i == 0 ? "" : ", ").append(array[i]);
        }
        return out.append(shown < array.length ? ", ...]" : "]").toString();
    }

    private String longs(long[] array) {
        if (array == null) {
            return "long[0]";
        }
        StringBuilder out = new StringBuilder("long[").append(array.length).append("] [");
        int shown = Math.min(array.length, maxElements);
        for (int i = 0; i < shown; i++) {
            out.append(i == 0 ? "" : ", ").append(array[i]).append('L');
        }
        return out.append(shown < array.length ? ", ...]" : "]").toString();
    }

    /** 20.0 rather than 20.000000476837158, and 0.05 rather than 0.05000000074505806. */
    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        String text = String.format(Locale.ROOT, "%.5f", value);
        while (text.endsWith("0") && !text.endsWith(".0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
