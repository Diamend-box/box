package com.diamend.boxcore.skill;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named group of {@link SkillNode}s, rendered as one GUI page.
 */
public class SkillTree {

    private final String id;
    private String display;
    private List<String> description = new ArrayList<>();
    private Material icon = Material.BOOK;
    private int rows = 6;
    private int order;
    private String permission = "";
    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillTree(String id) {
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

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
    }

    public int getSize() {
        return rows * 9;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public void add(SkillNode node) {
        nodes.put(node.getId(), node);
    }

    public SkillNode get(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<SkillNode> nodes() {
        return nodes.values();
    }

    public int nodeCount() {
        return nodes.size();
    }
}
