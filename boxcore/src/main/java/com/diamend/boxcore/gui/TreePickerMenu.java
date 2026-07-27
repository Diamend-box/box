package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the skill trees a player can open, with their progress in each.
 */
public class TreePickerMenu extends AbstractMenu {

    private final SkillsModule skills;
    private final Map<Integer, SkillTree> slots = new HashMap<>();

    public TreePickerMenu(BoxCorePlugin plugin, SkillsModule skills) {
        super(plugin, 27, "<dark_gray>Box <gray>| <light_purple>Skill Trees");
        this.skills = skills;
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        List<SkillTree> trees = new ArrayList<>();
        for (SkillTree tree : skills.trees().trees()) {
            if (tree.getPermission().isEmpty() || player.hasPermission(tree.getPermission())) {
                trees.add(tree);
            }
        }

        int slot = trees.size() <= 5 ? 11 + (5 - trees.size()) / 2 : 9;
        for (SkillTree tree : trees) {
            if (slot >= 26) {
                break;
            }
            int owned = 0;
            int levels = 0;
            int maxLevels = 0;
            for (SkillNode node : tree.nodes()) {
                int level = profile.getNodeLevel(node.key());
                if (level > 0) {
                    owned++;
                }
                levels += level;
                maxLevels += node.getMaxLevel();
            }
            List<String> lore = new ArrayList<>(tree.getDescription());
            lore.add("");
            lore.add("<gray>Nodes: <white>" + owned + "<gray>/<white>" + tree.nodeCount());
            lore.add("<gray>Levels: <white>" + levels + "<gray>/<white>" + maxLevels);
            lore.add("<gray>Spent here: <white>" + skills.service().pointsSpentIn(profile, tree));
            lore.add("<gray>" + Text.progressBar(maxLevels == 0 ? 0 : (double) levels / maxLevels,
                    20, "<green>■", "<dark_gray>■"));
            lore.add("");
            lore.add("<yellow>Click to open");

            slots.put(slot, tree);
            set(slot, Items.text(tree.getIcon(), tree.getDisplay(), lore, owned > 0));
            slot++;
        }

        if (trees.isEmpty()) {
            set(13, Items.text(Material.BARRIER, "<red>No skill trees",
                    List.of("<gray>None are configured, or none", "<gray>are available to you."), false));
        }

        set(4, Items.text(Material.EXPERIENCE_BOTTLE, "<green>Your points",
                List.of("<gray>Available: <white>" + profile.getAvailablePoints(),
                        "<gray>Spent: <white>" + profile.getPointsSpent(),
                        "<gray>Earned overall: <white>" + profile.getPointsEarned()), true));
        backButton(18, "Hub");
        closeButton(26);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == 26) {
            player.closeInventory();
            return;
        }
        if (raw == 18) {
            click(player);
            openLater(player, new HubMenu(plugin));
            return;
        }
        SkillTree tree = slots.get(raw);
        if (tree == null) {
            return;
        }
        click(player);
        openLater(player, new SkillTreeMenu(plugin, skills, tree));
    }
}
