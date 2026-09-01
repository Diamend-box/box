package com.diamend.robobear.command;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.EntryPass;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.data.PlayerData;
import com.diamend.robobear.gui.MilestoneEditorMenu;
import com.diamend.robobear.gui.MineToggleMenu;
import com.diamend.robobear.gui.QuestEditorMenu;
import com.diamend.robobear.gui.StartMenu;
import com.diamend.robobear.gui.UpgradeEditorMenu;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.mob.MobArchetype;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /robobear} and its subcommands.
 *
 * <p>The bare command opens the menu, because that's what a player wants 99
 * times in 100. Everything else is either a shortcut past a menu or an admin
 * job that has no menu.
 */
public class RoboBearCommand implements CommandExecutor, TabCompleter {

    private final RoboBearPlugin plugin;

    /** Corner selections for building a manual mine, kept only in memory. */
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public RoboBearCommand(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.parse("<red>Only a player can open the menu. "
                        + "Try /rb mines or /rb reload."));
                return true;
            }
            if (!player.hasPermission("robobear.use")) {
                plugin.messages().send(player, "no-permission");
                return true;
            }
            new StartMenu(plugin).open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "start" -> start(sender);
            case "retire", "stop" -> retire(sender);
            case "cancel", "quit" -> cancel(sender);
            case "stats" -> stats(sender, args);
            case "mines" -> listMines(sender, args);
            case "pass" -> pass(sender, args);
            case "upgrades", "workshop" -> upgrades(sender);
            case "quests", "objectives" -> quests(sender);
            case "mobs" -> mobs(sender, args);
            case "milestones", "edit", "admin" -> milestones(sender);
            case "pos1" -> setCorner(sender, 0);
            case "pos2" -> setCorner(sender, 1);
            case "mine" -> mine(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(Text.parse("<red>Unknown subcommand. <gray>Try /rb on its own."));
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------
    // Player commands
    // ------------------------------------------------------------------

    private boolean start(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can start a run."));
            return true;
        }
        if (!player.hasPermission("robobear.use")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (plugin.service().isRunning(player)) {
            plugin.service().reopen(player);
            return true;
        }
        plugin.service().start(player);
        return true;
    }

    private boolean retire(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can do that."));
            return true;
        }
        plugin.service().retire(player);
        return true;
    }

    private boolean cancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can do that."));
            return true;
        }
        if (!plugin.service().isRunning(player)) {
            plugin.messages().send(player, "not-running");
            return true;
        }
        plugin.service().fail(player, "failed-cancel");
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length > 1) {
            if (!sender.hasPermission("robobear.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            target = plugin.data().resolve(args[1]);
            if (target == null) {
                sender.sendMessage(Text.parse("<red>The server has never seen '" + args[1] + "'."));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Text.parse("<red>Name someone: /rb stats <player>"));
            return true;
        }

        PlayerData data = plugin.data().get(target.getUniqueId());
        String name = target.getName() == null ? "that player" : target.getName();
        sender.sendMessage(Text.parse("<gold>RoboBear — <white>" + name));
        sender.sendMessage(Text.parse("<gray>  Runs: <white>" + data.runs()));
        sender.sendMessage(Text.parse("<gray>  Deepest round: <white>" + data.bestRound()));
        sender.sendMessage(Text.parse("<gray>  Rounds cleared overall: <white>" + data.totalRounds()));
        sender.sendMessage(Text.parse("<gray>  Payouts taken: <white>" + data.totalMilestones()));
        if (data.bestRunSeconds() > 0) {
            sender.sendMessage(Text.parse("<gray>  Fastest run: <white>"
                    + Text.duration(data.bestRunSeconds())));
        }

        if (target instanceof Player online) {
            RoboRun run = plugin.service().runOf(online);
            if (run != null) {
                sender.sendMessage(Text.parse("<gray>  In a run: <white>round " + run.round()
                        + "<gray>, <gold>" + run.cogs() + " cogs"));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Admin commands
    // ------------------------------------------------------------------

    private boolean milestones(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can open the editor."));
            return true;
        }
        if (!player.hasPermission("robobear.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        new MilestoneEditorMenu(plugin).open(player);
        return true;
    }

    /** Opens the picker for which workshop upgrades are on sale. */
    private boolean upgrades(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can open the editor."));
            return true;
        }
        if (!player.hasPermission("robobear.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        new UpgradeEditorMenu(plugin).open(player);
        return true;
    }

    /** Opens the quest editor: which job types are offered, and for what. */
    private boolean quests(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can open the editor."));
            return true;
        }
        if (!player.hasPermission("robobear.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        new QuestEditorMenu(plugin).open(player);
        return true;
    }

    /**
     * What the challenge is currently sending, and a way to clear it.
     *
     * <p>The clear exists because these mobs are invisible to everyone but their
     * owner, which makes a leftover the single hardest thing on the server to
     * diagnose by looking at it.
     */
    private boolean mobs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            int cleared = plugin.mobs().despawnAll() + plugin.mobs().sweepEverything();
            sender.sendMessage(Text.parse("<yellow>Cleared <white>" + cleared
                    + "</white> challenge mob(s)."));
            return true;
        }

        if (!plugin.mobs().enabled()) {
            sender.sendMessage(Text.parse("<gray>Challenge mobs are switched off"
                    + (plugin.mobs().roster().isEmpty() ? " — the roster is empty." : ".")));
            return true;
        }
        sender.sendMessage(Text.parse("<gold>Challenge mobs <gray>— <white>"
                + plugin.mobs().liveCount() + "</white> alive right now."));
        for (MobArchetype archetype : plugin.mobs().roster()) {
            String when = archetype.elite()
                    ? "<light_purple>milestone rounds"
                    : archetype.weight() <= 0
                            ? "<dark_gray>never (weight 0)"
                            : "<gray>round " + archetype.minRound() + "+, weight "
                                    + archetype.weight();
            sender.sendMessage(Text.parse("<dark_gray> • " + archetype.name()
                    + " <dark_gray>(" + archetype.type() + ") " + when));
        }
        sender.sendMessage(Text.parse("<dark_gray>/rb mobs clear <gray>removes any left over."));
        return true;
    }

    private boolean listMines(CommandSender sender, String[] args) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("debug")) {
            return debugMines(sender);
        }
        if (args.length > 1 && (args[1].equalsIgnoreCase("edit")
                || args[1].equalsIgnoreCase("gui"))) {
            return editMines(sender);
        }
        sender.sendMessage(Text.parse("<gold>Mines from <white>"
                + plugin.mines().activeSource() + "<gold>:"));
        if (plugin.mines().size() == 0) {
            sender.sendMessage(Text.parse("<gray>  none"));
            sender.sendMessage(Text.parse("<gray>Run <white>/rb mines debug<gray> to find out why."));
            return true;
        }
        for (MineRegion mine : plugin.mines().all()) {
            boolean on = plugin.mines().toggles().isEnabled(mine.id());
            sender.sendMessage(Text.parse("<dark_gray> • "
                    + (on ? "<yellow>" : "<dark_gray>") + mine.id()
                    + " <gray>" + mine.boundsDescription()
                    + " <dark_gray>(" + Text.number(mine.volume()) + " blocks)"
                    + (on ? "" : " <red>[off]")));
        }
        int enabled = plugin.mines().enabledSize();
        sender.sendMessage(Text.parse("<gray>In the objective pool: <white>" + enabled
                + "<gray> of <white>" + plugin.mines().size()
                + "<gray>. Change it with <white>/rb mines edit<gray>."));
        if (enabled == 0) {
            sender.sendMessage(Text.parse("<red>Every mine is switched off, so no mining "
                    + "objectives can be rolled."));
        }
        return true;
    }

    /** Opens the picker for which mines objectives may be set in. */
    private boolean editMines(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can open the picker."));
            return true;
        }
        new MineToggleMenu(plugin).open(player);
        return true;
    }

    /**
     * Explains, on demand, exactly what the MineResetLite reader can see.
     *
     * <p>The startup warning fires once and can be scrolled past or rotated out
     * of the log before anyone goes looking. This can be asked for at any time,
     * and its output is what's needed to add support for an unrecognised build.
     */
    private boolean debugMines(CommandSender sender) {
        sender.sendMessage(Text.parse("<gold>RoboBear mine detection"));
        sender.sendMessage(Text.parse("<gray>Configured source: <white>"
                + plugin.getConfig().getString("mines.source", "auto")
                + " <gray>— active: <white>" + plugin.mines().activeSource()));
        sender.sendMessage(Text.parse("<gray>Manual regions defined: <white>"
                + plugin.mines().manualProvider().mines().size()));
        sender.sendMessage(Text.parse("<dark_gray>— MineResetLite —"));

        for (String line : plugin.mines().mineResetLiteProvider().diagnose()) {
            sender.sendMessage(Text.parse("<gray>" + Text.escape(line)));
            plugin.getLogger().info("[mines debug] " + line);
        }
        sender.sendMessage(Text.parse("<dark_gray>The same report is in the server log."));
        return true;
    }

    /**
     * {@code /rb pass give [player] [amount]} — the only way a valid pass comes
     * into existence.
     *
     * <p>Passes are stamped when issued and checked by that stamp, so there is
     * no other route: a crafted or renamed lookalike is refused at entry. That
     * makes this the mint, which is why it is admin-only and why the amount
     * defaults to exactly one entry rather than a stack.
     */
    private boolean pass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(Text.parse("<red>Usage: /rb pass give [player] [amount]"));
            return true;
        }

        EntryPass pass = plugin.service().pass();
        if (pass.prototype() == null) {
            sender.sendMessage(Text.parse("<red>Entry is free. <gray>Set "
                    + "<white>run.entry-item.item<gray> in config.yml to have a pass at all."));
            return true;
        }

        Player target;
        if (args.length > 2) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Text.parse("<red>'" + args[2] + "' isn't online."));
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Text.parse("<red>Name a player: /rb pass give <player> [amount]"));
            return true;
        }

        int amount = pass.cost();
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3].trim());
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Text.parse("<red>'" + args[3] + "' isn't a number."));
                return true;
            }
            if (amount < 1) {
                sender.sendMessage(Text.parse("<red>Give at least one."));
                return true;
            }
            if (amount > EntryPass.MAX_GIVE) {
                sender.sendMessage(Text.parse("<red>At most " + EntryPass.MAX_GIVE
                        + " at a time. <gray>That's already a full inventory of them."));
                return true;
            }
        }

        int stored = pass.give(target, amount);
        int dropped = amount - stored;
        sender.sendMessage(Text.parse("<green>Gave <white>" + amount + "× "
                + Items.describe(pass.prototype()) + "<green> to <white>"
                + target.getName() + "<green>."
                + (dropped > 0 ? " <gray>(" + dropped + " on the floor — inventory was full.)" : "")));
        if (!target.equals(sender)) {
            plugin.messages().send(target, "pass-received", "pass", pass.label());
        }
        if (!pass.requireTag()) {
            sender.sendMessage(Text.parse("<yellow>Note: <gray>run.entry-item.require-tag is "
                    + "<white>false<gray>, so a renamed lookalike still works as a pass."));
        }
        return true;
    }

    private boolean setCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can select a corner."));
            return true;
        }
        if (!player.hasPermission("robobear.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        Location[] corners = selections.computeIfAbsent(player.getUniqueId(),
                key -> new Location[2]);
        corners[index] = player.getLocation().getBlock().getLocation();
        Location at = corners[index];
        player.sendMessage(Text.parse("<green>Corner " + (index + 1) + " set to <white>"
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + "<green>."));
        if (corners[0] != null && corners[1] != null) {
            player.sendMessage(Text.parse("<gray>Now run <white>/rb mine set <id><gray>."));
        }
        return true;
    }

    private boolean mine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse("<red>Only a player can do that."));
            return true;
        }
        if (!player.hasPermission("robobear.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Text.parse("<red>Usage: /rb mine <set|delete> <id>"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String id = args[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        if (action.equals("delete")) {
            if (plugin.mines().manualProvider().remove(id)) {
                plugin.mines().refresh();
                player.sendMessage(Text.parse("<green>Deleted the manual mine <white>" + id + "<green>."));
            } else {
                player.sendMessage(Text.parse("<red>No manual mine called '" + id + "'."));
            }
            return true;
        }
        if (!action.equals("set")) {
            player.sendMessage(Text.parse("<red>Usage: /rb mine <set|delete> <id>"));
            return true;
        }

        Location[] corners = selections.get(player.getUniqueId());
        if (corners == null || corners[0] == null || corners[1] == null) {
            player.sendMessage(Text.parse("<red>Select both corners first, with "
                    + "<white>/rb pos1<red> and <white>/rb pos2<red>."));
            return true;
        }
        if (corners[0].getWorld() == null || corners[1].getWorld() == null
                || !corners[0].getWorld().equals(corners[1].getWorld())) {
            player.sendMessage(Text.parse("<red>Those corners are in different worlds."));
            return true;
        }

        MineRegion region = MineRegion.between(id, id, corners[0].getWorld().getName(),
                corners[0].getBlockX(), corners[0].getBlockY(), corners[0].getBlockZ(),
                corners[1].getBlockX(), corners[1].getBlockY(), corners[1].getBlockZ());
        plugin.mines().manualProvider().put(region);
        plugin.mines().refresh();

        player.sendMessage(Text.parse("<green>Saved <white>" + id + "<green> — "
                + region.boundsDescription() + " <dark_gray>(" + Text.number(region.volume())
                + " blocks)"));
        if (!"manual".equals(plugin.mines().activeSource())) {
            player.sendMessage(Text.parse("<yellow>Note: <gray>the active mine source is <white>"
                    + plugin.mines().activeSource() + "<gray>, so this won't be used until you set "
                    + "<white>mines.source: manual<gray> in config.yml."));
        }
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Usage: /rb reset <player>"));
            return true;
        }
        OfflinePlayer target = plugin.data().resolve(args[1]);
        if (target == null) {
            sender.sendMessage(Text.parse("<red>The server has never seen '" + args[1] + "'."));
            return true;
        }
        plugin.data().reset(target.getUniqueId());
        if (target instanceof Player online) {
            plugin.service().abandon(online.getUniqueId());
        }
        sender.sendMessage(Text.parse("<green>Wiped RoboBear data for <white>"
                + target.getName() + "<green>."));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.reloadEverything();
        sender.sendMessage(Text.parse("<green>Reloaded configuration. <gray>Found <white>"
                + plugin.mines().size() + "<gray> mine(s) and <white>"
                + plugin.milestones().size() + "<gray> milestone tier(s)."));
        return true;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.add("start");
            options.add("retire");
            options.add("cancel");
            options.add("stats");
            if (sender.hasPermission("robobear.admin")) {
                options.addAll(List.of("mines", "milestones", "upgrades", "quests", "mobs",
                        "pass", "pos1", "pos2", "mine", "reset", "reload"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "mine" -> {
                    if (sender.hasPermission("robobear.admin")) {
                        options.addAll(List.of("set", "delete"));
                    }
                }
                case "mines" -> {
                    if (sender.hasPermission("robobear.admin")) {
                        options.addAll(List.of("edit", "debug"));
                    }
                }
                case "mobs" -> {
                    if (sender.hasPermission("robobear.admin")) {
                        options.add("clear");
                    }
                }
                case "pass" -> {
                    if (sender.hasPermission("robobear.admin")) {
                        options.add("give");
                    }
                }
                case "stats", "reset" -> {
                    for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                        options.add(online.getName());
                    }
                }
                default -> {
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mine")
                && args[1].equalsIgnoreCase("delete")
                && sender.hasPermission("robobear.admin")) {
            for (MineRegion mine : plugin.mines().manualProvider().mines()) {
                options.add(mine.id());
            }
            return filter(options, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pass")
                && args[1].equalsIgnoreCase("give")
                && sender.hasPermission("robobear.admin")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
            return filter(options, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("pass")
                && args[1].equalsIgnoreCase("give")
                && sender.hasPermission("robobear.admin")) {
            int cost = plugin.service().pass().cost();
            options.addAll(List.of(String.valueOf(cost), String.valueOf(cost * 5),
                    String.valueOf(cost * 10)));
            return filter(options, args[3]);
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
