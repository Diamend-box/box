package com.diamend.boxcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Name → registry-object lookups that tolerate the way Mojang and Bukkit have
 * renamed things across versions.
 *
 * <p>Attributes are the awkward case: they used to be the enum constant
 * {@code GENERIC_MAX_HEALTH} with the key {@code minecraft:generic.max_health},
 * and are now the registry entry {@code minecraft:max_health}. Configs written
 * against either spelling — and either the dotted or the underscored form —
 * resolve to the same attribute here, so a server owner's file doesn't break
 * under them on the next update.
 */
public final class Registries {

    private static Map<String, Attribute> attributes;
    private static Map<String, PotionEffectType> effects;

    private Registries() {
    }

    /**
     * Folds a config-written name down to a comparable form: trimmed,
     * lower-cased, no namespace, dots and dashes as underscores.
     *
     * <p>This is the right function for names that aren't attributes — it
     * leaves the name otherwise intact, so something legitimately called
     * {@code player_damage} survives.
     */
    public static String simplify(String name) {
        if (name == null) {
            return "";
        }
        String value = name.trim().toLowerCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    /**
     * {@link #simplify} plus dropping the legacy
     * {@code generic_}/{@code player_}/{@code zombie_} attribute prefixes.
     *
     * <p>Attributes only: the prefixes are Mojang's old attribute namespacing,
     * and stripping them from anything else quietly mangles the name.
     */
    public static String normalize(String name) {
        String value = simplify(name);
        for (String prefix : new String[] { "generic_", "player_", "zombie_", "horse_" }) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
    }

    /** Looks up a vanilla attribute by any of its historical names, or null. */
    public static Attribute attribute(String name) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return null;
        }
        if (attributes == null) {
            attributes = index(Registry.ATTRIBUTE);
        }
        return attributes.get(key);
    }

    /** Looks up a potion effect type by name (e.g. {@code haste}), or null. */
    public static PotionEffectType potionEffect(String name) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return null;
        }
        if (effects == null) {
            effects = index(Registry.EFFECT);
        }
        return effects.get(key);
    }

    /**
     * Parses an attribute modifier operation. Accepts the Bukkit constant names
     * plus the friendlier aliases used in the shipped configs.
     */
    public static AttributeModifier.Operation operation(String name) {
        switch (normalize(name)) {
            case "", "add", "add_number", "flat", "addition" -> {
                return AttributeModifier.Operation.ADD_NUMBER;
            }
            case "add_scalar", "multiply_base", "percent_base", "scalar" -> {
                return AttributeModifier.Operation.ADD_SCALAR;
            }
            case "multiply_scalar_1", "multiply_total", "multiply", "percent_total", "total" -> {
                return AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            }
            default -> {
                return null;
            }
        }
    }

    /** True when the operation expresses a fraction (so it should render as a %). */
    public static boolean isPercent(AttributeModifier.Operation operation) {
        return operation != AttributeModifier.Operation.ADD_NUMBER;
    }

    /** Human-readable attribute name for lore, e.g. {@code Max Health}. */
    public static String displayName(Attribute attribute) {
        return attribute == null ? "?" : Text.prettify(normalize(attribute.getKey().getKey()));
    }

    /** Human-readable potion effect name for lore, e.g. {@code Night Vision}. */
    public static String displayName(PotionEffectType effect) {
        return effect == null ? "?" : Text.prettify(normalize(effect.getKey().getKey()));
    }

    /**
     * Indexes a registry by every spelling we might see: the raw key, the
     * normalized key, and the {@code generic.}-prefixed legacy key.
     */
    private static <T extends org.bukkit.Keyed> Map<String, T> index(Registry<T> registry) {
        Map<String, T> map = new HashMap<>();
        try {
            for (T entry : registry) {
                NamespacedKey key = entry.getKey();
                map.putIfAbsent(normalize(key.getKey()), entry);
                map.putIfAbsent(key.getKey().toLowerCase(Locale.ROOT), entry);
            }
        } catch (Throwable ex) {
            // A registry that can't be iterated (very old or very unusual server)
            // leaves the map empty; callers report the unresolved name instead.
            return map;
        }
        return map;
    }
}
