package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.challenge.Upgrade;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Between rounds: spend Cogs, then decide whether to go again.
 *
 * <p>Cogs never leave this screen. They are earned inside a run and spent inside
 * the same run, which is what keeps RoboBear on the right side of the server's
 * "no second currency" rule — there is nothing here to bank, trade or carry out.
 *
 * <p>The other half of the screen is the actual decision the mode is built
 * around: keep climbing for the next milestone, or retire and keep what you have.
 */
public class UpgradeShopMenu extends AbstractMenu {

    private static final int UPGRADE_START = 10;
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_RETIRE = 29;
    private static final int SLOT_NEXT = 33;

    private final RoboRun run;
    private final List<Upgrade> shown = new ArrayList<>();

    public UpgradeShopMenu(RoboBearPlugin plugin, RoboRun run) {
        super(plugin, 45, "<dark_gray>Workshop — round " + run.round() + " cleared");
        this.run = run;
    }

    @Override
    protected void build(Player player) {
        shown.clear();
        set(SLOT_HEADER, header());

        int slot = UPGRADE_START;
        for (Upgrade upgrade : plugin.service().upgrades().enabled()) {
            if (slot >= UPGRADE_START + 7) {
                break;
            }
            shown.add(upgrade);
            set(slot, icon(upgrade));
            slot++;
        }
        if (shown.isEmpty()) {
            set(13, Items.text(Material.BARRIER, "<gray>Nothing on sale", List.of(
                    "<gray>This server's workshop is closed.",
                    "<gray>Cogs still count toward nothing —",
                    "<gray>the ladder is the whole game here.")));
        }

        set(SLOT_RETIRE, Items.text(Material.OAK_DOOR, "<yellow>Retire", List.of(
                "<gray>Stop here and walk away.",
                "<gray>Everything you've already been",
                "<gray>paid is yours to keep.",
                "",
                "<gray>Rounds cleared: <white>" + run.round(),
                "<gray>Payouts taken: <white>" + run.milestonesEarned().size())));

        set(SLOT_NEXT, Items.text(Material.LIME_DYE, "<green><bold>Next round »", nextLore(), true));
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack header() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Cogs: <gold>" + run.cogs());
        lore.add("<gray>Rounds cleared: <white>" + run.round());
        lore.add("");
        lore.add("<dark_gray>Cogs are spent here and nowhere else.");
        lore.add("<dark_gray>They don't survive the run.");
        return Items.text(Material.GOLD_NUGGET, "<gold><bold>" + run.cogs() + " Cogs", lore);
    }

    private List<String> nextLore() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Round <white>" + (run.round() + 1) + "<gray> — harder, and worth more.");

        MilestoneRewards.Tier next = plugin.milestones().nextAfter(run.round());
        if (next != null) {
            int away = next.round() - run.round();
            lore.add("");
            lore.add("<gray>" + next.name() + " <gray>is <white>" + away
                    + "<gray> round" + (away == 1 ? "" : "s") + " away.");
        }
        lore.add("");
        lore.add("<red>Fail and you keep only what you've been paid.");
        return lore;
    }

    private ItemStack icon(Upgrade upgrade) {
        int level = run.levelOf(upgrade);
        boolean maxed = level >= upgrade.maxLevel();
        int cost = plugin.service().costOf(run, upgrade);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + upgrade.description());
        lore.add("");
        lore.add("<gray>Level: <white>" + level + "<gray>/<white>" + upgrade.maxLevel());

        if (maxed) {
            lore.add("");
            lore.add("<dark_gray>Fully upgraded.");
        } else {
            lore.add("<gray>Next level: <gold>" + cost + " cogs");
            lore.add("");
            lore.add(run.cogs() >= cost
                    ? "<yellow>Click to buy."
                    : "<red>You can't afford it yet.");
        }
        return Items.text(upgrade.icon(),
                (maxed ? "<green>" : "<yellow>") + upgrade.displayName(), lore, level > 0);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == SLOT_NEXT) {
            click(player);
            plugin.service().nextRound(player);
            return;
        }
        if (slot == SLOT_RETIRE) {
            click(player);
            player.closeInventory();
            plugin.service().retire(player);
            return;
        }
        int index = slot - UPGRADE_START;
        if (index >= 0 && index < shown.size()) {
            if (plugin.service().buy(player, shown.get(index))) {
                click(player);
            } else {
                deny(player);
            }
            refresh(player);
        }
    }
}
