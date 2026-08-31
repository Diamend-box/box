package com.diamend.darksea.item;

import java.util.List;

/**
 * A configured override for what a registry item looks like in the hand: its
 * material, its display name, and its lore. Pure data — the material is carried
 * as a name and resolved at the Bukkit edge — so parsing and fallback are
 * testable without a server.
 *
 * <p>Every field is optional and a missing one means "keep what the plugin
 * ships". That is the whole point: an empty override changes nothing, so the
 * config can be edited a line at a time without having to restate the parts you
 * were happy with.
 *
 * <p>Only cosmetics live here. Item identity is the PDC tag, never the name, so
 * renaming a crystal cannot break a loot table, a shop rule or a saved chest —
 * and cannot be used to counterfeit anything with an anvil.
 *
 * @param materialName vanilla material to use, or null to keep the shipped one
 * @param name         MiniMessage display name, or null to keep the shipped one
 * @param lore         MiniMessage lore lines; empty means keep the shipped lore
 */
public record ItemDisplay(String materialName, String name, List<String> lore) {

    /** An override that changes nothing. */
    public static final ItemDisplay NONE = new ItemDisplay(null, null, List.of());

    public ItemDisplay {
        materialName = blankToNull(materialName);
        name = blankToNull(name);
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean hasMaterial() {
        return materialName != null;
    }

    public boolean hasName() {
        return name != null;
    }

    /**
     * Whether the lore is overridden. An explicitly empty list is "keep the
     * shipped lore" rather than "no lore" — clearing lore entirely is not worth
     * a footgun where every other blank field means the same thing.
     */
    public boolean hasLore() {
        return !lore.isEmpty();
    }

    /** The name to use, given what the plugin ships. */
    public String nameOr(String shipped) {
        return hasName() ? name : shipped;
    }

    /** The lore to use, given what the plugin ships. */
    public List<String> loreOr(List<String> shipped) {
        return hasLore() ? lore : shipped;
    }
}
