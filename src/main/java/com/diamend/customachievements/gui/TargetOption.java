package com.diamend.customachievements.gui;

import org.bukkit.Material;

import java.util.List;

/**
 * One selectable option in the {@link TargetPickerMenu}: the value that gets
 * stored on the objective, plus how to present it.
 *
 * @param value the raw target written to the objective (e.g. {@code OAK_LOG} or {@code #LOGS})
 * @param icon  the item shown in the grid
 * @param label the text shown to the player; falls back to {@code value} when null
 * @param lore  extra explanatory lines shown under the name
 */
public record TargetOption(String value, Material icon, String label, List<String> lore) {

    public TargetOption {
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public TargetOption(String value) {
        this(value, Material.PAPER, null, List.of());
    }

    public TargetOption(String value, Material icon) {
        this(value, icon, null, List.of());
    }

    /** The name shown in the picker. */
    public String display() {
        return label == null || label.isBlank() ? value : label;
    }
}
