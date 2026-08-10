package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.challenge.Objective;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The choice at the top of every round: two objectives, one clock, pick one.
 *
 * <p>The offers are spread across difficulties and the harder one pays more
 * Cogs, so this is the run's real decision — bank a safe round, or reach for the
 * upgrade you want and risk the clock. The clock is stopped while this is open.
 */
public class ObjectiveChoiceMenu extends AbstractMenu {

    private static final int ROW_START = 9;
    private static final int ROW_WIDTH = 9;
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_REROLL = 22;

    private final RoboRun run;
    private final List<Integer> slots = new ArrayList<>();

    public ObjectiveChoiceMenu(RoboBearPlugin plugin, RoboRun run) {
        super(plugin, 27, "<dark_gray>Round " + run.round() + " — pick one");
        this.run = run;
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        List<Objective> offers = run.offers();

        set(SLOT_HEADER, header());

        // Centre the offers across the middle row, whatever the count.
        int count = Math.min(offers.size(), ROW_WIDTH);
        int first = ROW_START + Math.max(0, (ROW_WIDTH - (count * 2 - 1)) / 2);
        for (int i = 0; i < count; i++) {
            int slot = first + (i * 2);
            if (slot >= ROW_START + ROW_WIDTH) {
                break;
            }
            slots.add(slot);
            set(slot, offerIcon(offers.get(i), i));
        }

        set(SLOT_REROLL, run.rerollsLeft() > 0
                ? Items.text(Material.ENDER_PEARL, "<yellow>Reroll", List.of(
                "<gray>Roll a different pair.",
                "<gray>Rerolls left: <white>" + run.rerollsLeft()))
                : Items.text(Material.GRAY_DYE, "<dark_gray>No rerolls left", List.of(
                "<gray>You've used them all this run.")));

        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack header() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Cogs: <gold>" + run.cogs());
        lore.add("<gray>Clock per round: <white>"
                + Text.duration(plugin.getConfig().getInt("run.round-seconds", 300)
                + run.bonusSeconds()));

        MilestoneRewards.Tier next = plugin.milestones().nextAfter(run.round() - 1);
        if (next != null) {
            lore.add("");
            lore.add("<gray>Next payout: <white>" + next.name()
                    + " <gray>at round <white>" + next.round());
        }
        lore.add("");
        lore.add("<dark_gray>The clock is stopped while you choose.");
        return Items.text(Material.COMPASS, "<gold><bold>Round " + run.round(), lore);
    }

    private ItemStack offerIcon(Objective objective, int index) {
        List<String> lore = new ArrayList<>();
        lore.add("<white>" + objective.describe(plugin.mines()));
        lore.add("");
        lore.add("<gray>Pays: <gold>" + objective.cogs() + " cogs");
        if (!objective.isValid(plugin.mines())) {
            lore.add("");
            lore.add("<red>That mine doesn't exist right now.");
        } else {
            lore.add("");
            lore.add("<yellow>Click to take it.");
        }
        String label = index == 0 ? "<green>The safer one" : "<red>The greedy one";
        return Items.text(objective.icon(), label, lore, index > 0);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == SLOT_REROLL) {
            plugin.service().reroll(player);
            return;
        }
        int index = slots.indexOf(slot);
        if (index >= 0) {
            click(player);
            plugin.service().choose(player, index);
        }
    }
}
