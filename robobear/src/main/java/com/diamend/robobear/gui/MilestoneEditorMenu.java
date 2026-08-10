package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff view of the ladder: which rounds pay, and what.
 *
 * <p>The whole reward side of the plugin is edited here, in game. Nothing about
 * a payout needs a text editor or a restart, which is the same standard BoxCore
 * holds its compaction recipes and warps to.
 */
public class MilestoneEditorMenu extends AbstractMenu {

    private static final int PER_PAGE = 45;
    private static final int SLOT_ADD = 49;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CLOSE = 53;

    private List<MilestoneRewards.Tier> shown = List.of();

    public MilestoneEditorMenu(RoboBearPlugin plugin) {
        super(plugin, 54, "<dark_gray>Milestone Payouts");
    }

    @Override
    protected void build(Player player) {
        List<MilestoneRewards.Tier> all = plugin.milestones().all();
        shown = new ArrayList<>(all.subList(0, Math.min(all.size(), PER_PAGE)));

        for (int i = 0; i < shown.size(); i++) {
            set(i, icon(shown.get(i)));
        }

        if (shown.isEmpty()) {
            set(22, Items.text(Material.BARRIER, "<red>No payouts yet", List.of(
                    "<gray>A run currently pays nothing.",
                    "<yellow>Add one below.")));
        }

        set(SLOT_INFO, Items.text(Material.COMPASS, "<yellow>How the ladder works", List.of(
                "<gray>A payout is earned by clearing",
                "<gray>its round, and is kept even if the",
                "<gray>run ends later.",
                "",
                "<gray>Mines RoboBear can see: <white>" + plugin.mines().size(),
                "<gray>Source: <white>" + plugin.mines().activeSource())));

        set(SLOT_ADD, Items.text(Material.EMERALD, "<green><bold>+ Add a payout", List.of(
                "<gray>You'll be asked for a round number.")));
        closeButton(SLOT_CLOSE);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack icon(MilestoneRewards.Tier tier) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Earned by clearing round <white>" + tier.round() + "<gray>.");
        lore.add("");
        if (tier.items().isEmpty()) {
            lore.add("<red>Pays nothing yet.");
        } else {
            lore.add("<gray>Pays:");
            for (ItemStack item : tier.items()) {
                lore.add("<dark_gray> • <white>" + item.getAmount() + "× " + Items.describe(item));
            }
        }
        if (!tier.commands().isEmpty()) {
            lore.add("<gray>And runs <white>" + tier.commands().size() + "<gray> command(s).");
        }
        lore.add("");
        lore.add("<gray>Left-click to edit the items");
        lore.add("<gray>Right-click to rename it");
        lore.add("<gray>Shift-right-click to delete it");

        return Items.text(tier.items().isEmpty() ? Material.GRAY_DYE : Material.GOLD_INGOT,
                "<yellow>" + tier.name(), lore, !tier.items().isEmpty());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_ADD) {
            click(player);
            promptForRound(player);
            return;
        }
        if (slot < 0 || slot >= shown.size()) {
            return;
        }
        MilestoneRewards.Tier tier = shown.get(slot);

        if (event.isShiftClick() && event.isRightClick()) {
            click(player);
            plugin.milestones().remove(tier.round());
            player.sendMessage(Text.parse("<red>Removed the payout at round "
                    + tier.round() + "."));
            refresh(player);
            return;
        }
        if (event.isRightClick()) {
            click(player);
            plugin.prompts().ask(player, "<yellow>Type a name for the round "
                    + tier.round() + " payout.", input -> {
                if (input != null) {
                    tier.setName(input);
                    plugin.milestones().save();
                }
                open(player);
            });
            return;
        }
        click(player);
        new RewardItemsMenu(plugin, player, tier).open(player);
    }

    private void promptForRound(Player player) {
        plugin.prompts().ask(player,
                "<yellow>Which round should pay out? <gray>Type a number.", input -> {
                    if (input == null) {
                        open(player);
                        return;
                    }
                    int round;
                    try {
                        round = Integer.parseInt(input.trim());
                    } catch (NumberFormatException ignored) {
                        player.sendMessage(Text.parse("<red>'" + input + "' isn't a number."));
                        open(player);
                        return;
                    }
                    if (round < 1) {
                        player.sendMessage(Text.parse("<red>Rounds start at 1."));
                        open(player);
                        return;
                    }
                    if (plugin.milestones().at(round) != null) {
                        player.sendMessage(Text.parse("<red>Round " + round + " already pays out."));
                        open(player);
                        return;
                    }
                    MilestoneRewards.Tier tier = plugin.milestones().createOrGet(round);
                    plugin.milestones().save();
                    new RewardItemsMenu(plugin, player, tier).open(player);
                });
    }
}
