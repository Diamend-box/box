package com.diamend.boxcore.skill;

import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Registries;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code trees.yml} into {@link SkillTree}s.
 *
 * <p>Loading is deliberately forgiving: a node with an unrecognised attribute,
 * an out-of-range slot or a prerequisite that doesn't exist is reported in the
 * server log and skipped, leaving the rest of the tree usable. A typo in one
 * node should never cost a server its whole progression system.
 */
public class SkillTreeManager {

    private final Plugin plugin;
    private final Map<String, SkillTree> trees = new LinkedHashMap<>();
    private final Map<String, SkillNode> byKey = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public SkillTreeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        trees.clear();
        byKey.clear();
        warnings.clear();

        File file = new File(plugin.getDataFolder(), "trees.yml");
        if (!file.exists()) {
            plugin.saveResource("trees.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("trees");
        if (root == null) {
            plugin.getLogger().warning("trees.yml has no 'trees' section — no skill trees loaded.");
            return;
        }

        for (String treeId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(treeId);
            if (section == null) {
                continue;
            }
            try {
                SkillTree tree = readTree(treeId.toLowerCase(java.util.Locale.ROOT), section);
                trees.put(tree.getId(), tree);
                for (SkillNode node : tree.nodes()) {
                    byKey.put(node.key(), node);
                }
            } catch (RuntimeException ex) {
                warn("tree '" + treeId + "' could not be read: " + ex.getMessage());
            }
        }
        validateRequirements();

        for (String warning : warnings) {
            plugin.getLogger().warning("trees.yml: " + warning);
        }
        plugin.getLogger().info("Loaded " + trees.size() + " skill tree(s), "
                + byKey.size() + " node(s).");
    }

    private void warn(String message) {
        warnings.add(message);
    }

    private SkillTree readTree(String treeId, ConfigurationSection section) {
        SkillTree tree = new SkillTree(treeId);
        tree.setDisplay(section.getString("display", treeId));
        tree.setDescription(section.getStringList("description"));
        tree.setIcon(Items.material(section.getString("icon"), Material.BOOK));
        tree.setRows(section.getInt("rows", 6));
        tree.setOrder(section.getInt("order", 0));
        tree.setPermission(section.getString("permission", ""));

        ConfigurationSection nodes = section.getConfigurationSection("nodes");
        if (nodes == null) {
            warn("tree '" + treeId + "' has no nodes.");
            return tree;
        }
        Set<Integer> usedSlots = new HashSet<>();
        for (String nodeId : nodes.getKeys(false)) {
            ConfigurationSection nodeSection = nodes.getConfigurationSection(nodeId);
            if (nodeSection == null) {
                continue;
            }
            SkillNode node = readNode(tree, nodeId.toLowerCase(java.util.Locale.ROOT), nodeSection);
            if (node == null) {
                continue;
            }
            if (node.getSlot() < 0 || node.getSlot() >= tree.getSize()) {
                warn("node '" + node.key() + "' has slot " + node.getSlot()
                        + ", outside this tree's " + tree.getSize() + " slots — skipped.");
                continue;
            }
            if (!usedSlots.add(node.getSlot())) {
                warn("node '" + node.key() + "' reuses slot " + node.getSlot() + " — skipped.");
                continue;
            }
            tree.add(node);
        }
        return tree;
    }

    private SkillNode readNode(SkillTree tree, String nodeId, ConfigurationSection section) {
        SkillNode node = new SkillNode(tree.getId(), nodeId);
        node.setDisplay(section.getString("display", nodeId));
        node.setDescription(section.getStringList("description"));
        node.setIcon(Items.material(section.getString("icon"), Material.BOOK));
        if (section.isString("locked-icon")) {
            node.setLockedIcon(Items.material(section.getString("locked-icon"), null));
        }
        node.setSlot(section.getInt("slot", -1));
        node.setMaxLevel(section.getInt("max-level", 1));
        node.setRequiredPointsInTree(section.getInt("requires-points", 0));
        node.setPermission(section.getString("requires-permission", ""));

        List<Integer> costs = new ArrayList<>();
        if (section.isList("costs")) {
            for (int cost : section.getIntegerList("costs")) {
                costs.add(Math.max(0, cost));
            }
        }
        if (costs.isEmpty()) {
            costs.add(Math.max(0, section.getInt("cost", 1)));
        }
        node.setCosts(costs);

        List<SkillNode.Requirement> requirements = new ArrayList<>();
        for (String raw : section.getStringList("requires")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.trim();
            int level = 1;
            int colon = value.lastIndexOf(':');
            if (colon > 0) {
                try {
                    level = Math.max(1, Integer.parseInt(value.substring(colon + 1).trim()));
                    value = value.substring(0, colon).trim();
                } catch (NumberFormatException ignored) {
                    // "requires: [some:thing]" without a number — treat it as an id.
                }
            }
            requirements.add(new SkillNode.Requirement(value.toLowerCase(java.util.Locale.ROOT), level));
        }
        node.setRequirements(requirements);

        readEffects(node, section.getConfigurationSection("effects"));
        return node;
    }

    private void readEffects(SkillNode node, ConfigurationSection effects) {
        if (effects == null) {
            return;
        }

        ConfigurationSection attributes = effects.getConfigurationSection("attributes");
        if (attributes != null) {
            for (String name : attributes.getKeys(false)) {
                Attribute attribute = Registries.attribute(name);
                if (attribute == null) {
                    warn("node '" + node.key() + "' uses unknown attribute '" + name + "' — ignored.");
                    continue;
                }
                double amount;
                AttributeModifier.Operation operation;
                if (attributes.isConfigurationSection(name)) {
                    ConfigurationSection detail = attributes.getConfigurationSection(name);
                    amount = detail.getDouble("amount");
                    operation = Registries.operation(detail.getString("operation", "add_number"));
                    if (operation == null) {
                        warn("node '" + node.key() + "' uses unknown operation '"
                                + detail.getString("operation") + "' — treated as add_number.");
                        operation = AttributeModifier.Operation.ADD_NUMBER;
                    }
                } else {
                    amount = attributes.getDouble(name);
                    operation = AttributeModifier.Operation.ADD_NUMBER;
                }
                if (amount == 0.0) {
                    continue;
                }
                node.getEffects().attributes()
                        .add(new NodeEffects.AttributeBonus(attribute, amount, operation));
            }
        }

        for (Object raw : effects.getList("potions", List.of())) {
            String name;
            int amplifier = 0;
            int perLevel = 0;
            if (raw instanceof Map<?, ?> map) {
                Object effect = map.get("effect");
                name = effect == null ? null : String.valueOf(effect);
                amplifier = intOf(map.get("amplifier"), 0);
                perLevel = intOf(map.get("amplifier-per-level"), 0);
            } else {
                name = raw == null ? null : String.valueOf(raw);
            }
            PotionEffectType type = Registries.potionEffect(name);
            if (type == null) {
                warn("node '" + node.key() + "' uses unknown potion effect '" + name + "' — ignored.");
                continue;
            }
            node.getEffects().potions().add(new NodeEffects.PotionBonus(type, amplifier, perLevel));
        }

        node.getEffects().permissions().addAll(effects.getStringList("permissions"));
        node.getEffects().commands().addAll(effects.getStringList("commands"));
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** Drops prerequisites that point at nodes which don't exist. */
    private void validateRequirements() {
        for (SkillTree tree : trees.values()) {
            for (SkillNode node : tree.nodes()) {
                List<SkillNode.Requirement> valid = new ArrayList<>();
                for (SkillNode.Requirement requirement : node.getRequirements()) {
                    SkillNode target = tree.get(requirement.nodeId());
                    if (target == null) {
                        warn("node '" + node.key() + "' requires '" + requirement.nodeId()
                                + "', which isn't in this tree — requirement dropped.");
                        continue;
                    }
                    if (requirement.level() > target.getMaxLevel()) {
                        warn("node '" + node.key() + "' requires " + requirement.nodeId()
                                + ":" + requirement.level() + " but that node maxes at "
                                + target.getMaxLevel() + " — clamped.");
                        valid.add(new SkillNode.Requirement(requirement.nodeId(), target.getMaxLevel()));
                        continue;
                    }
                    valid.add(requirement);
                }
                node.setRequirements(valid);
            }
        }
    }

    // ------------------------------------------------------------------

    public SkillTree getTree(String id) {
        return id == null ? null : trees.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    /** Trees in display order (by {@code order}, then id). */
    public List<SkillTree> trees() {
        List<SkillTree> list = new ArrayList<>(trees.values());
        list.sort(Comparator.comparingInt(SkillTree::getOrder).thenComparing(SkillTree::getId));
        return list;
    }

    /** Looks up a node by its {@code tree.node} key. */
    public SkillNode getNode(String key) {
        return key == null ? null : byKey.get(key);
    }

    public Map<String, SkillNode> nodesByKey() {
        return byKey;
    }

    public int treeCount() {
        return trees.size();
    }

    public int nodeCount() {
        return byKey.size();
    }

    public List<String> warnings() {
        return warnings;
    }
}
