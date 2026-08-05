package com.diamend.boxtutorial.gui;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.guide.StepTrigger;
import com.diamend.boxtutorial.guide.TutorialStep;
import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tutorial as a checklist: every step, what's done, what's next.
 *
 * <p>Upcoming steps are shown rather than hidden. A new player's first question
 * is "how much of this is there?", and a list that reveals itself one row at a
 * time answers that with "who knows" — which is exactly the feeling the plugin
 * exists to remove.
 */
public class TutorialMenu extends AbstractMenu {

    private static final int[] STEP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int HEADER_SLOT = 4;
    private static final int TOPICS_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int ACTION_SLOT = 53;

    /** Menu slot to the step it shows. */
    private final Map<Integer, String> offered = new HashMap<>();
    private final int page;
    private int pages = 1;

    public TutorialMenu(BoxTutorialPlugin plugin, int page) {
        super(plugin, 54, plugin.messages().raw("menu-title").isBlank()
                ? "<dark_gray>Tutorial" : plugin.messages().raw("menu-title"));
        this.page = Math.max(0, page);
    }

    public static void openFor(BoxTutorialPlugin plugin, Player player) {
        new TutorialMenu(plugin, 0).open(player);
    }

    @Override
    protected void build(Player player) {
        offered.clear();
        List<TutorialStep> steps = plugin.tutorial().steps();
        Progress progress = plugin.store().get(player.getUniqueId());
        TutorialStep current = plugin.service().isActive(progress)
                ? plugin.service().current(progress) : null;

        pages = Math.max(1, (int) Math.ceil(steps.size() / (double) STEP_SLOTS.length));
        int start = Math.min(page, pages - 1) * STEP_SLOTS.length;

        set(HEADER_SLOT, header(progress));
        for (int index = 0; index < STEP_SLOTS.length; index++) {
            int stepIndex = start + index;
            if (stepIndex >= steps.size()) {
                break;
            }
            TutorialStep step = steps.get(stepIndex);
            offered.put(STEP_SLOTS[index], step.id());
            set(STEP_SLOTS[index], icon(step, progress, current, stepIndex + 1));
        }

        if (!plugin.tutorial().topics().isEmpty()) {
            set(TOPICS_SLOT, Items.text(Material.WRITABLE_BOOK,
                    "<gold>What do these words mean?",
                    List.of("<gray>Short explanations of the things",
                            "<gray>every boxpvp server assumes you know.",
                            "",
                            "<yellow>Click to browse " + plugin.tutorial().topics().size()
                                    + " topics"),
                    false));
        }
        if (pages > 1) {
            if (page > 0) {
                set(PREV_SLOT, Items.text(Material.ARROW, "<yellow>« Previous page",
                        List.of("<gray>Page " + page + " of " + pages), false));
            }
            if (page < pages - 1) {
                set(NEXT_SLOT, Items.text(Material.ARROW, "<yellow>Next page »",
                        List.of("<gray>Page " + (page + 2) + " of " + pages), false));
            }
        }
        set(ACTION_SLOT, actionButton(progress));
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack header(Progress progress) {
        int total = plugin.tutorial().stepCount();
        int done = plugin.service().completedCount(progress);
        double fraction = total == 0 ? 1.0 : done / (double) total;

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Steps done: <white>" + done + "<gray>/<white>" + total);
        lore.add("<green>" + Text.progressBar(fraction, 10, "■", "<dark_gray>■")
                + " <white>" + plugin.service().percent(progress) + "%");
        lore.add("");
        if (progress.finished()) {
            lore.add("<green>You've been through the whole thing.");
            lore.add("<gray>Everything here stays readable.");
        } else if (progress.stopped()) {
            lore.add("<gray>The tutorial is paused.");
            lore.add("<gray>Nothing you did was lost.");
        } else {
            lore.add("<gray>Click a step to see what it wants.");
        }
        return Items.text(Material.KNOWLEDGE_BOOK, "<gold>Your progress", lore, false);
    }

    /** One step's tile: done, current, or still ahead. */
    private ItemStack icon(TutorialStep step, Progress progress, TutorialStep current, int number) {
        boolean done = progress.isComplete(step.id());
        boolean skipped = progress.isSkipped(step.id());
        boolean active = current != null && current.id().equals(step.id());

        String name = skipped ? "<gray>✖ " + Text.plain(step.name())
                : done ? "<green>✔ " + step.name()
                : active ? "<yellow>➤ " + step.name()
                : "<gray>" + Text.plain(step.name());

        List<String> lore = new ArrayList<>(step.description());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        if (!step.trigger().isPassive()) {
            lore.add("<gray>Goal: <white>" + step.objective());
        }
        if (active && step.counts()) {
            int count = Math.min(progress.count(step.id()), step.amount());
            lore.add("<gray>Progress: <white>" + count + "<gray>/<white>" + step.amount()
                    + " <dark_gray>" + Text.progressBar(count / (double) step.amount(),
                            10, "|", "."));
        }
        if (!step.hint().isBlank() && (active || done)) {
            lore.add(step.hint());
        }
        if (!step.command().isBlank() && active) {
            lore.add("<gray>Try: <white>" + slashed(step.command()));
        }

        lore.add("");
        if (skipped) {
            lore.add("<gray>Skipped. <dark_gray>Click to read it again.");
        } else if (done) {
            lore.add("<green>Done. <gray>Click to read it again.");
        } else if (active) {
            lore.add(step.trigger() == StepTrigger.READ
                    ? "<yellow>Click when you've read it."
                    : "<yellow>Click for the details in chat.");
            if (plugin.service().canSkip(step)) {
                lore.add("<dark_gray>Shift-click to skip this step.");
            }
        } else if (current != null && plugin.service().sequential()) {
            lore.add("<dark_gray>Comes later — you're on step "
                    + plugin.tutorial().number(current) + ".");
        } else {
            lore.add("<dark_gray>Not done yet.");
        }
        if (step.optional()) {
            lore.add("<dark_gray>Optional.");
        }

        return Items.withCount(Items.text(step.icon(), name, lore, active), number);
    }

    private ItemStack actionButton(Progress progress) {
        boolean active = plugin.service().isActive(progress);
        if (active) {
            return Items.text(Material.LIGHT_GRAY_DYE, "<red>Stop the tutorial",
                    List.of("<gray>Turns off the bar and the reminders.",
                            "<gray>Your progress is kept, and",
                            "<gray><white>/tutorial start<gray> brings it back."),
                    false);
        }
        if (!plugin.getConfig().getBoolean("allow-restart", true)) {
            return Items.text(Material.LIGHT_GRAY_DYE, "<gray>Tutorial finished", List.of(), false);
        }
        return Items.text(Material.LIME_DYE, "<green>Start it again",
                List.of("<gray>Runs the whole thing from step one.",
                        "<gray>Rewards are not handed out twice."),
                false);
    }

    private static String slashed(String command) {
        return command.startsWith("/") ? command : "/" + command;
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == TOPICS_SLOT && !plugin.tutorial().topics().isEmpty()) {
            click(player);
            openLater(player, new TopicsMenu(plugin, 0));
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            click(player);
            openLater(player, new TutorialMenu(plugin, page - 1));
            return;
        }
        if (slot == NEXT_SLOT && page < pages - 1) {
            click(player);
            openLater(player, new TutorialMenu(plugin, page + 1));
            return;
        }
        if (slot == ACTION_SLOT) {
            handleAction(player);
            return;
        }
        String id = offered.get(slot);
        if (id != null) {
            handleStep(player, id, event.isShiftClick());
        }
    }

    private void handleAction(Player player) {
        Progress progress = plugin.store().get(player.getUniqueId());
        click(player);
        if (plugin.service().isActive(progress)) {
            player.closeInventory();
            plugin.service().stop(player);
            return;
        }
        if (!plugin.getConfig().getBoolean("allow-restart", true)) {
            return;
        }
        player.closeInventory();
        plugin.service().start(player, true);
    }

    private void handleStep(Player player, String id, boolean shift) {
        TutorialStep step = plugin.tutorial().step(id);
        if (step == null) {
            return;
        }
        Progress progress = plugin.store().get(player.getUniqueId());
        TutorialStep current = plugin.service().isActive(progress)
                ? plugin.service().current(progress) : null;
        boolean active = current != null && current.id().equals(step.id());
        click(player);

        if (active && shift && plugin.service().canSkip(step)) {
            plugin.service().skipStep(player, step);
            openLater(player, new TutorialMenu(plugin, page));
            return;
        }
        if (active && step.trigger() == StepTrigger.READ) {
            plugin.service().completeStep(player, step, true);
            openLater(player, new TutorialMenu(plugin, page));
            return;
        }
        player.closeInventory();
        plugin.service().announce(player, step);
    }
}
