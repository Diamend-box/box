package com.diamend.robobear.command;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.data.PlayerData;
import com.diamend.robobear.gui.MilestoneEditorMenu;
import com.diamend.robobear.gui.StartMenu;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Text;
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
            case "mines" -> listMines(sender);
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

    private boolean listMines(CommandSender sender) {
        if (!sender.hasPermission("robobear.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(Text.parse("<gold>Mines from <white>"
                + plugin.mines().activeSource() + "<gold>:"));
        if (plugin.mines().size() == 0) {
            sender.sendMessage(Text.parse("<gray>  none"));
            return true;
        }
        for (MineRegion mine : plugin.mines().all()) {
            sender.sendMessage(Text.parse("<dark_gray> • <yellow>" + mine.id()
                    + " <gray>" + mine.boundsDescription()
                    + " <dark_gray>(" + Text.number(mine.volume()) + " blocks)"));
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
                options.addAll(List.of("mines", "milestones", "pos1", "pos2", "mine",
                        "reset", "reload"));
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
