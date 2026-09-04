package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.data.PlayerData;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Robo Bear's front desk: what a run costs, what it pays, and how you've done.
 *
 * <p>Everything a run will ask of you is on this screen before you spend the
 * pass — the risk spec's principle 7 is that a tradeoff only exists if it's
 * visible, and an entry fee for an unknown ladder isn't a decision.
 */
public class StartMenu extends AbstractMenu {

    private static final int SLOT_HEADER = 4;
    private static final int SLOT_START = 11;
    private static final int SLOT_RECORD = 13;
    private static final int SLOT_LADDER = 15;
    private static final int SLOT_CLOSE = 22;

    public StartMenu(RoboBearPlugin plugin) {
        super(plugin, 27, "<dark_gray>Robo Bear");
    }

    @Override
    protected void build(Player player) {
        long now = System.currentTimeMillis();
        PlayerData data = plugin.data().get(player);

        set(SLOT_HEADER, Items.text(Material.IRON_GOLEM_SPAWN_EGG, "<gold><bold>Robo Bear", List.of(
                "<gray>A ladder of timed rounds.",
                "<gray>Each one offers a choice of jobs;",
                "<gray>finishing pays Cogs to spend on",
                "<gray>upgrades before the next.",
                "",
                "<gray>Miss a clock and the run is over —",
                "<gray>but every payout you've already",
                "<gray>taken is yours.")));

        set(SLOT_START, startIcon(player, data, now));
        set(SLOT_RECORD, recordIcon(data));
        set(SLOT_LADDER, ladderIcon(data));
        closeButton(SLOT_CLOSE);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack startIcon(Player player, PlayerData data, long now) {
        RoboRun run = plugin.service().runOf(player);
        if (run != null) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Round <white>" + run.round());
            lore.add("<gray>Cogs: <gold>" + run.cogs());
            lore.add("<gray>State: <white>" + switch (run.state()) {
                case CHOOSING -> "choosing a job";
                case RUNNING -> Text.clock(run.secondsLeft(now)) + " left";
                case SHOPPING -> "in the workshop";
            });
            lore.add("");
            lore.add("<yellow>Click to get back to it.");
            return Items.text(Material.CLOCK, "<green>Run in progress", lore, true);
        }

        long cooldown = data.cooldownRemaining(
                plugin.getConfig().getLong("run.cooldown-seconds", 0L), now);
        if (cooldown > 0) {
            return Items.text(Material.RED_DYE, "<red>Not yet", List.of(
                    "<gray>You can go again in <white>" + Text.duration(cooldown) + "<gray>."));
        }

        List<String> lore = new ArrayList<>();
        String pass = plugin.service().entryItemLabel();
        if ("nothing".equals(pass)) {
            lore.add("<gray>Costs nothing to enter.");
        } else {
            lore.add("<gray>Costs <white>" + pass + "<gray>.");
        }
        lore.add("<gray>Starts you with <gold>"
                + plugin.getConfig().getInt("run.starting-cogs", 10) + " cogs<gray>.");
        lore.add("<gray>Each round: <white>"
                + Text.duration(plugin.getConfig().getInt("run.round-seconds", 300)) + "<gray>.");
        lore.add("");
        if (plugin.mines().enabledSize() == 0
                && !plugin.getConfig().getBoolean("objectives.kill-mobs.enabled", true)) {
            lore.add("<red>Nothing is set up to do yet.");
        } else {
            lore.add("<yellow>Click to start.");
        }
        return Items.text(Material.LIME_DYE, "<green><bold>Start a run", lore, true);
    }

    private ItemStack recordIcon(PlayerData data) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Runs: <white>" + data.runs());
        lore.add("<gray>Deepest round: <white>" + data.bestRound());
        lore.add("<gray>Rounds cleared overall: <white>" + data.totalRounds());
        lore.add("<gray>Payouts taken: <white>" + data.totalMilestones());
        if (data.deepestMilestone() > 0) {
            MilestoneRewards.Tier deepest = plugin.milestones().at(data.deepestMilestone());
            lore.add("<gray>Best payout: <white>"
                    + (deepest == null ? "round " + data.deepestMilestone() : deepest.name()));
        }
        return Items.text(Material.BOOK, "<yellow>Your record", lore);
    }

    private ItemStack ladderIcon(PlayerData data) {
        List<String> lore = new ArrayList<>();
        List<MilestoneRewards.Tier> tiers = plugin.milestones().all();
        if (tiers.isEmpty()) {
            lore.add("<gray>No payouts have been set up.");
        } else {
            for (MilestoneRewards.Tier tier : tiers) {
                String mark = data.hasMilestone(tier.round()) ? "<green>✔" : "<dark_gray>✖";
                String note = tier.isEmpty() ? " <dark_gray>(empty)" : "";
                lore.add(mark + " <gray>Round <white>" + tier.round() + "<gray> — "
                        + tier.name() + note);
            }
        }
        lore.add("");
        lore.add("<dark_gray>Payouts land on the floor, unbanked.");
        return Items.text(Material.GOLD_INGOT, "<yellow>The ladder", lore);
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
        if (slot == SLOT_START) {
            RoboRun run = plugin.service().runOf(player);
            if (run != null) {
                click(player);
                plugin.service().reopen(player);
                return;
            }
            if (plugin.service().start(player)) {
                click(player);
            } else {
                deny(player);
                refresh(player);
            }
        }
    }
}
