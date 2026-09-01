package com.diamend.spyglass.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/**
 * Attribute lookups that survive the renaming.
 *
 * <p>Max health has been {@code GENERIC_MAX_HEALTH}, {@code generic.max_health}
 * and now {@code minecraft:max_health}, and a player's save file may still hold
 * either of the older spellings. Everything here works from the registry and
 * compares folded names, so all three land on the same attribute.
 */
public final class Attributes {

    private static Map<String, Attribute> index;

    private Attributes() {
    }

    /** {@code minecraft:generic.max_health} and {@code MAX_HEALTH} both fold to {@code max_health}. */
    public static String fold(String name) {
        if (name == null) {
            return "";
        }
        String value = name.trim().toLowerCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        for (String prefix : new String[] { "generic_", "player_", "zombie_", "horse_" }) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
    }

    /** The attribute that name refers to, in any of its spellings, or null. */
    public static Attribute byName(String name) {
        String key = fold(name);
        if (key.isEmpty()) {
            return null;
        }
        if (index == null) {
            index = buildIndex();
        }
        return index.get(key);
    }

    /** Every attribute the server knows about, in registry order. */
    public static List<Attribute> all() {
        List<Attribute> out = new ArrayList<>();
        Safe.run(() -> {
            for (Attribute attribute : Registry.ATTRIBUTE) {
                out.add(attribute);
            }
        });
        return out;
    }

    /** The short, readable name: {@code max_health}. */
    public static String name(Attribute attribute) {
        return attribute == null ? "?" : fold(attribute.getKey().getKey());
    }

    private static Map<String, Attribute> buildIndex() {
        Map<String, Attribute> map = new HashMap<>();
        for (Attribute attribute : all()) {
            map.putIfAbsent(fold(attribute.getKey().getKey()), attribute);
        }
        return map;
    }
}
