package com.diamend.customachievements.gui;

import org.bukkit.Material;

/** One selectable option in the {@link TargetPickerMenu} (a value plus its icon). */
public record TargetOption(String value, Material icon) {

    public TargetOption(String value) {
        this(value, Material.PAPER);
    }
}
