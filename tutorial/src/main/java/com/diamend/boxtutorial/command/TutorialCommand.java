package com.diamend.boxtutorial.command;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.gui.ItemsMenu;
import com.diamend.boxtutorial.gui.TopicsMenu;
import com.diamend.boxtutorial.gui.TutorialMenu;
import com.diamend.boxtutorial.guide.Topic;
import com.diamend.boxtutorial.guide.TutorialStep;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /tutorial} — the menu for players, and the handful of tools staff need
 * to run it (start it for someone, mark a step done, reset, reload).
 */
public class TutorialCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "boxtutorial.admin";

    private final BoxTutorialPlugin plugin;

    public TutorialCommand(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            requirePlayer(sender, this::openOrEnter);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "open" -> requirePlayer(sender, player -> TutorialMenu.openFor(plugin, player));
            case "start", "restart" -> start(sender, args, true);
            case "continue", "resume" -> start(sender, args, false);
            case "stop", "quit" -> requirePlayer(sender, plugin.service()::stop);
            case "next", "skipstep" -> skipStep(sender);
            case "topics", "glossary" -> topics(sender);
            case "what", "explain", "help" -> explain(sender, args);
            case "status", "progress" -> status(sender, args);
            case "steps" -> steps(sender);
            case "items" -> items(sender);
            case "item" -> item(sender, args);
            case "complete", "done" -> complete(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Player commands
    // ------------------------------------------------------------------

    /**
     * Bare {@code /tutorial}: the one command a new player will be told about,
     * so it has to do the obvious thing from wherever they are.
     *
     * <p>Not started, or stopped earlier — take them in. Half way through and
     * standing at spawn — take them back in. Already in the arena — show the
     * board. Finished — show the board, which is where the restart button is.
     */
    private void openOrEnter(Player player) {
        Progress progress = plugin.store().get(player.getUniqueId());
        if (progress.finished()) {
            TutorialMenu.openFor(plugin, player);
            return;
        }
        if (!plugin.service().isActive(progress)) {
            plugin.service().start(player, false);
            return;
        }
        if (plugin.service().isInArena(player)) {
            TutorialMenu.openFor(plugin, player);
            return;
        }
        plugin.service().enter(player);
    }

    /** {@code /tutorial start [player]} — staff may aim it at someone else. */
    private void start(CommandSender sender, String[] args, boolean fresh) {
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-offline", "player", args[1]);
                return;
            }
            plugin.service().start(target, fresh);
            plugin.messages().sendLiteral(sender,
                    "<green>Started the tutorial for <white>" + target.getName() + "<green>.");
            return;
        }
        requirePlayer(sender, player -> {
            Progress progress = plugin.store().get(player.getUniqueId());
            if (fresh && progress.finished()
                    && !plugin.getConfig().getBoolean("allow-restart", true)
                    && !player.hasPermission(ADMIN)) {
                plugin.messages().send(player, "restart-not-allowed");
                return;
            }
            plugin.service().start(player, fresh && progress.started());
        });
    }

    /** Steps past whatever the player is stuck on, when that's allowed. */
    private void skipStep(CommandSender sender) {
        requirePlayer(sender, player -> {
            Progress progress = plugin.store().get(player.getUniqueId());
            TutorialStep current = plugin.service().isActive(progress)
                    ? plugin.service().current(progress) : null;
            if (current == null) {
                plugin.messages().send(player, "not-running");
                return;
            }
            if (!plugin.service().skipStep(player, current)) {
                plugin.messages().send(player, "cannot-skip");
            }
        });
    }

    private void topics(CommandSender sender) {
        List<Topic> topics = plugin.tutorial().topics();
        if (topics.isEmpty()) {
            plugin.messages().sendLiteral(sender, "<gray>No topics are set up.");
            return;
        }
        if (sender instanceof Player player) {
            new TopicsMenu(plugin, 0).open(player);
            return;
        }
        plugin.messages().sendLiteral(sender, "<gold>Topics:");
        for (Topic topic : topics) {
            plugin.messages().sendPlain(sender,
                    "  <yellow>" + topic.id() + " <dark_gray>- <white>" + topic.title());
        }
    }

    /** {@code /tutorial what <topic>} — the glossary, without opening a menu. */
    private void explain(CommandSender sender, String[] args) {
        if (args.length < 2) {
            topics(sender);
            return;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Topic topic = plugin.tutorial().topic(query);
        if (topic == null) {
            plugin.messages().send(sender, "no-such-topic", "topic", query);
            return;
        }
        plugin.service().explain(sender, topic);
    }

    private void status(CommandSender sender, String[] args) {
        UUID uuid;
        String name;
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            OfflinePlayer target = resolve(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-unknown", "player", args[1]);
                return;
            }
            uuid = target.getUniqueId();
            name = target.getName() == null ? args[1] : target.getName();
        } else if (sender instanceof Player player) {
            uuid = player.getUniqueId();
            name = player.getName();
        } else {
            summary(sender);
            return;
        }

        Progress progress = plugin.store().get(uuid);
        TutorialStep current = plugin.service().current(progress);
        plugin.messages().sendLiteral(sender, "<gold>" + name + "<gray>: <white>"
                + plugin.service().completedCount(progress) + "<gray>/<white>"
                + plugin.tutorial().stepCount() + " <gray>steps <dark_gray>("
                + plugin.service().percent(progress) + "%)");
        if (progress.finished()) {
            plugin.messages().sendPlain(sender, "  <green>Finished the tutorial.");
        } else if (progress.stopped()) {
            plugin.messages().sendPlain(sender, "  <gray>Stopped it early.");
        } else if (!progress.started()) {
            plugin.messages().sendPlain(sender, "  <gray>Hasn't started it.");
        } else if (current != null) {
            plugin.messages().sendPlain(sender, "  <gray>On step <white>"
                    + plugin.tutorial().number(current) + "<gray>: " + current.name());
            plugin.messages().sendPlain(sender, "  <gray>" + current.objective());
        }
    }

    /** What the console gets from a bare {@code /tutorial status}. */
    private void summary(CommandSender sender) {
        int running = 0;
        int finished = 0;
        for (Progress progress : plugin.store().all()) {
            if (progress.finished()) {
                finished++;
            } else if (plugin.service().isActive(progress)) {
                running++;
            }
        }
        plugin.messages().sendLiteral(sender, "<gold>BoxTutorial<gray>: <white>"
                + plugin.tutorial().stepCount() + "<gray> steps, <white>"
                + plugin.tutorial().topics().size() + "<gray> topics.");
        plugin.messages().sendPlain(sender, "  <gray>Players part-way through: <white>" + running);
        plugin.messages().sendPlain(sender, "  <gray>Players finished: <white>" + finished);
    }

    // ------------------------------------------------------------------
    // Staff commands
    // ------------------------------------------------------------------

    private void steps(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        List<TutorialStep> steps = plugin.tutorial().steps();
        plugin.messages().sendLiteral(sender, "<gold>Steps (" + steps.size() + "):");
        int number = 1;
        for (TutorialStep step : steps) {
            String target = step.trigger().usesTarget() && !step.target().isBlank()
                    ? " <dark_gray>" + step.target() : "";
            plugin.messages().sendPlain(sender, "  <gray>" + number++ + ". <yellow>" + step.id()
                    + " <dark_gray>- <white>" + Text.plain(step.name())
                    + " <gray>[" + step.trigger().name()
                    + (step.amount() > 1 ? " x" + step.amount() : "") + "]" + target);
        }
        for (String warning : plugin.tutorial().warnings()) {
            plugin.messages().sendPlain(sender, "  <red>! <gray>" + warning);
        }
    }

    /** {@code /tutorial items} — the binding screen. */
    private void items(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (sender instanceof Player player) {
            ItemsMenu.openFor(plugin, player);
            return;
        }
        listItems(sender);
    }

    /**
     * {@code /tutorial item set|clear|give|list <id>} — the same thing for
     * people who would rather type than click, and the only way to do it from
     * the console.
     */
    private void item(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            listItems(sender);
            return;
        }
        if (args.length < 3) {
            plugin.messages().sendLiteral(sender,
                    "<gray>Usage: <white>/tutorial item <set|clear|give> <id>");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.items().isKnown(id)) {
            plugin.messages().sendLiteral(sender, "<gray>No item slot called <white>" + id
                    + "<gray>. Try <white>/tutorial item list<gray>.");
            return;
        }
        switch (action) {
            case "set", "bind" -> requirePlayer(sender, player -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    plugin.messages().sendLiteral(player,
                            "<gray>Hold the item you want to use, then run it again.");
                    return;
                }
                plugin.items().bind(id, held);
                plugin.instances().refreshTraders();
                plugin.messages().sendLiteral(player,
                        "<green>The tutorial's <white>" + id + "<green> is now that item.");
            });
            case "clear", "reset" -> {
                plugin.items().clear(id);
                plugin.instances().refreshTraders();
                plugin.messages().sendLiteral(sender,
                        "<gray>Back to the default for <white>" + id + "<gray>.");
            }
            case "give" -> {
                Player target = args.length >= 4
                        ? Bukkit.getPlayerExact(args[3])
                        : (sender instanceof Player player ? player : null);
                if (target == null) {
                    plugin.messages().send(sender, "player-offline",
                            "player", args.length >= 4 ? args[3] : "you");
                    return;
                }
                ItemStack copy = plugin.items().get(id);
                if (copy == null) {
                    plugin.messages().sendLiteral(sender, "<gray>That slot has no item.");
                    return;
                }
                target.getInventory().addItem(copy);
                plugin.messages().sendLiteral(sender,
                        "<green>Gave <white>" + target.getName() + "<green> the " + id + ".");
            }
            default -> plugin.messages().sendLiteral(sender,
                    "<gray>Usage: <white>/tutorial item <set|clear|give|list> <id>");
        }
    }

    private void listItems(CommandSender sender) {
        plugin.messages().sendLiteral(sender, "<gold>Tutorial items:");
        for (String id : plugin.items().ids()) {
            plugin.messages().sendPlain(sender, "  <yellow>" + id + " <dark_gray>- <white>"
                    + plugin.items().label(id)
                    + (plugin.items().isBound(id) ? " <green>(custom)" : " <dark_gray>(default)"));
        }
    }

    private void complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().sendLiteral(sender, "<gray>Usage: <white>/tutorial complete <player> <step>");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-unknown", "player", args[1]);
            return;
        }
        TutorialStep step = plugin.tutorial().step(args[2]);
        if (step == null) {
            plugin.messages().send(sender, "no-such-step", "step", args[2]);
            return;
        }
        boolean changed = plugin.service().completeOffline(target.getUniqueId(), step);
        plugin.messages().sendLiteral(sender, changed
                ? "<green>Marked <white>" + step.id() + "<green> done for <white>" + args[1]
                : "<gray>They had already done <white>" + step.id() + "<gray>.");
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().sendLiteral(sender, "<gray>Usage: <white>/tutorial reset <player>");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-unknown", "player", args[1]);
            return;
        }
        plugin.service().reset(target.getUniqueId());
        plugin.messages().sendLiteral(sender,
                "<green>Reset the tutorial for <white>" + args[1] + "<green>.");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadEverything();
        plugin.messages().sendLiteral(sender, "<green>Reloaded configuration: <white>"
                + plugin.tutorial().stepCount() + "<green> step(s), <white>"
                + plugin.tutorial().topics().size() + "<green> topic(s).");
        for (String warning : plugin.tutorial().warnings()) {
            plugin.messages().sendPlain(sender, "  <red>! <gray>" + warning);
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private void help(CommandSender sender) {
        plugin.messages().sendLiteral(sender, "<gold>Tutorial commands");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial <dark_gray>- <gray>open the tutorial");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial topics <dark_gray>- <gray>what the words mean");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial what <topic> <dark_gray>- <gray>explain one of them");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial status <dark_gray>- <gray>how far you got");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial start <dark_gray>- <gray>run it from the top");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial next <dark_gray>- <gray>skip the step you're on");
        plugin.messages().sendPlain(sender, "  <yellow>/tutorial stop <dark_gray>- <gray>turn it off");
        if (sender.hasPermission(ADMIN)) {
            plugin.messages().sendPlain(sender, "  <yellow>/tutorial steps <dark_gray>- <gray>list the steps (staff)");
            plugin.messages().sendPlain(sender, "  <yellow>/tutorial items <dark_gray>- <gray>set the reward items (staff)");
            plugin.messages().sendPlain(sender, "  <yellow>/tutorial complete <player> <step> <dark_gray>- <gray>(staff)");
            plugin.messages().sendPlain(sender, "  <yellow>/tutorial reset <player> <dark_gray>- <gray>(staff)");
            plugin.messages().sendPlain(sender, "  <yellow>/tutorial reload <dark_gray>- <gray>(staff)");
        }
    }

    /**
     * Finds a player by name: online first, then the server's own cache of
     * people who have logged in. Never invents a UUID for a name nobody has
     * seen — resetting the progress of a typo helps nobody.
     */
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        try {
            return Bukkit.getOfflinePlayerIfCached(name);
        } catch (Throwable ex) {
            return null; // older or unusual server implementations
        }
    }

    private void requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
            return;
        }
        plugin.messages().sendLiteral(sender, "<gray>That one is for players. Try <white>/tutorial status<gray>.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            options.addAll(List.of("menu", "start", "continue", "next", "stop",
                    "topics", "what", "status"));
            if (admin) {
                options.addAll(List.of("steps", "items", "item", "complete", "reset", "reload"));
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "what", "explain", "help" -> {
                    for (Topic topic : plugin.tutorial().topics()) {
                        options.add(topic.id());
                    }
                }
                case "item" -> {
                    options.addAll(List.of("set", "clear", "give", "list"));
                }
                case "status", "complete", "reset", "start", "restart" -> {
                    if (admin || sub.equals("status")) {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            options.add(online.getName());
                        }
                    }
                }
                default -> {
                    return List.of();
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && admin && (sub.equals("complete") || sub.equals("done"))) {
            for (TutorialStep step : plugin.tutorial().steps()) {
                options.add(step.id());
            }
            return filter(options, args[2]);
        }
        if (args.length == 3 && admin && sub.equals("item")) {
            options.addAll(plugin.items().ids());
            return filter(options, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
