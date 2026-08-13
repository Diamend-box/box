package com.diamend.spyglass.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Walks into a parsed compound by a written path, so a report can be asked for
 * one branch instead of the whole tree: {@code abilities},
 * {@code Inventory.0.components}, {@code Inventory[0]/id}.
 *
 * <p>Dots, slashes and brackets all separate; a numeric step indexes a list.
 * Compound keys match exactly first and then case-insensitively, because nobody
 * remembers whether Mojang capitalised this one.
 */
public final class NbtPath {

    private NbtPath() {
    }

    /** The tag at that path, or null when nothing lives there. */
    public static NbtTag resolve(NbtCompound root, String path) {
        if (root == null) {
            return null;
        }
        NbtTag current = root.asTag();
        for (String step : split(path)) {
            if (current == null) {
                return null;
            }
            current = step(current, step);
        }
        return current;
    }

    private static NbtTag step(NbtTag current, String step) {
        NbtCompound compound = current.asCompound();
        if (compound != null) {
            if (compound.has(step)) {
                return compound.get(step);
            }
            String wanted = step.toLowerCase(Locale.ROOT);
            for (String key : compound.keys()) {
                if (key.toLowerCase(Locale.ROOT).equals(wanted)) {
                    return compound.get(key);
                }
            }
            return null;
        }
        NbtList list = current.asList();
        if (list != null) {
            Integer index = asIndex(step);
            return index == null ? null : list.get(index);
        }
        return null;
    }

    /** The path's steps, with empty ones dropped. */
    public static List<String> split(String path) {
        List<String> steps = new ArrayList<>();
        if (path == null) {
            return steps;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.' || c == '/' || c == '[' || c == ']') {
                if (!current.isEmpty()) {
                    steps.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            steps.add(current.toString());
        }
        return steps;
    }

    private static Integer asIndex(String step) {
        try {
            int index = Integer.parseInt(step);
            return index < 0 ? null : index;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
