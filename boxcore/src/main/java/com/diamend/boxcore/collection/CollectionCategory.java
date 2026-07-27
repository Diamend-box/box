package com.diamend.boxcore.collection;

import org.bukkit.Material;

/**
 * A tab in the collections menu.
 */
public class CollectionCategory {

    private final String id;
    private String display;
    private Material icon = Material.CHEST;
    private int order;

    public CollectionCategory(String id) {
        this.id = id;
        this.display = id;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
