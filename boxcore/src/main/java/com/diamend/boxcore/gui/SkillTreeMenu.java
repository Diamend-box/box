package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillService;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One skill tree, laid out exactly as {@code trees.yml} places it.
 *
 * <p>Every node's lore is generated from its real effects at the level the
 * player owns (and at the next level), so a server owner never has to
 * hand-write "+2 max health" into a description and keep it in sync.
 */
public class SkillTreeMenu extends AbstractMenu {

    private final SkillsModule skills;
    private final SkillTree tree;
    private final Map<Integer, SkillNode> slots = new HashMap<>();

    public SkillTreeMenu(BoxCorePlugin plugin, SkillsModule skills, SkillTree tree) {
        super(plugin, tree.getSize(), "<dark_gray>Box <gray>| " + tree.getDisplay());
        this.skills = skills;
        this.tree = tree;
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        SkillService service = skills.service();

        for (SkillNode node : tree.nodes()) {
            slots.put(node.getSlot(), node);
            set(node.getSlot(), render(player, profile, service, node));
        }
        footer(player, profile, service);
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack render(Player player, PlayerProfile profile,
                                                  SkillService service, SkillNode node) {
        int level = profile.getNodeLevel(node.key());
        SkillService.UnlockCheck check = service.check(profile, node, player::hasPermission);
        boolean owned = level > 0;
        boolean locked = !owned && (check.reason() == SkillService.Denial.MISSING_REQUIREMENT
                || check.reason() == SkillService.Denial.NEEDS_POINTS_IN_TREE
                || check.reason() == SkillService.Denial.NO_PERMISSION);

        List<String> lore = new ArrayList<>(node.getDescription());
        if (!node.getDescription().isEmpty()) {
            lore.add("");
        }

        if (owned) {
            lore.add("<gray>Current" + (node.getMaxLevel() > 1
                    ? " <white>(" + Text.roman(level) + "<gray>/<white>"
                      + Text.roman(node.getMaxLevel()) + "<gray>)" : "") + "<gray>:");
            lore.addAll(node.getEffects().describe(level));
        } else {
            lore.add("<gray>Grants:");
            lore.addAll(node.getEffects().describe(1));
        }

        if (level < node.getMaxLevel()) {
            if (owned) {
                lore.add("");
                lore.add("<gray>Next level:");
                lore.addAll(node.getEffects().describe(level + 1));
            }
            lore.add("");
            lore.add("<gray>Cost: <white>" + check.cost() + " <gray>point"
                    + Messages.plural(check.cost()));
        }

        lore.add("");
        switch (check.reason()) {
            case ALLOWED -> lore.add(owned ? "<green>Click to upgrade" : "<green>Click to unlock");
            case MAX_LEVEL -> lore.add("<green>✔ Fully unlocked");
            case CANNOT_AFFORD -> lore.add("<red>Not enough points <gray>(you have "
                    + profile.getAvailablePoints() + ")");
            default -> lore.add("<red>✖ " + check.detail());
        }

        Material icon = node.getIcon();
        if (locked && node.getLockedIcon() != null) {
            icon = node.getLockedIcon();
        } else if (locked) {
            icon = Material.GRAY_DYE;
        }
        org.bukkit.inventory.ItemStack item = Items.text(icon, node.getDisplay()
                + (owned && node.getMaxLevel() > 1 ? " <gray>" + Text.roman(level) : ""), lore, owned);
        if (owned && node.getMaxLevel() > 1) {
            Items.withCount(item, level);
        }
        return item;
    }

    private void footer(Player player, PlayerProfile profile, SkillService service) {
        int last = size() - 1;
        putIfFree(last - 8, Items.text(Material.ARROW, "<yellow>« Skill trees", List.of(), false));
        putIfFree(last - 4, Items.text(Material.EXPERIENCE_BOTTLE, "<green>Skill points",
                List.of("<gray>Available: <white>" + profile.getAvailablePoints(),
                        "<gray>Spent in this tree: <white>" + service.pointsSpentIn(profile, tree),
                        "<gray>Spent overall: <white>" + profile.getPointsSpent()), true));
        if (plugin.getConfig().getBoolean("skills.allow-respec", true)
                && player.hasPermission("boxcore.respec")) {
            com.diamend.boxcore.skill.RespecCost cost = service.respecCost();
            List<String> lore = new ArrayList<>(List.of(
                    "<gray>Refund every node you own",
                    "<gray>and get your points back."));
            if (cost.isFree()) {
                lore.add("<gray>Costs nothing.");
            } else {
                int held = cost.heldBy(player);
                lore.add("<gray>Costs <white>" + cost.amount() + "x " + cost.displayName());
                lore.add(held >= cost.amount()
                        ? "<green>✔ You have " + held
                        : "<red>✖ You have " + held);
            }
            lore.add("");
            lore.add("<yellow>Run <white>/box respec");
            putIfFree(last - 2, Items.text(Material.GRINDSTONE, "<gold>Respec", lore, false));
        }
        putIfFree(last, Items.text(Material.BARRIER, "<red>Close", List.of(), false));
    }

    /** Footer slots give way to nodes — a config's layout always wins. */
    private void putIfFree(int slot, org.bukkit.inventory.ItemStack item) {
        if (!slots.containsKey(slot)) {
            set(slot, item);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= size()) {
            return;
        }
        SkillNode node = slots.get(raw);
        if (node != null) {
            skills.service().unlock(player, node);
            build(player); // refresh in place so the player sees the new state
            return;
        }
        int last = size() - 1;
        if (raw == last) {
            player.closeInventory();
        } else if (raw == last - 8) {
            click(player);
            openLater(player, new TreePickerMenu(plugin, skills));
        }
    }
}
