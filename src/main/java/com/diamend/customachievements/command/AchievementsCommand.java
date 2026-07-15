package com.diamend.customachievements.command;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.gui.AchievementMenu;
import com.diamend.customachievements.gui.EditorMenu;
import com.diamend.customachievements.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /achievements} and its sub-commands.
 */
public class AchievementsCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_USE = "customachievements.use";
    private static final String PERM_ADMIN = "customachievements.admin";

    private final CustomAchievementsPlugin plugin;

    public AchievementsCommand(CustomAchievementsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            openMenu(sender, false);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu", "open" -> openMenu(sender, false);
            case "list" -> list(sender);
            case "admin", "manage" -> openMenu(sender, true);
            case "create", "new" -> create(sender);
            case "grant", "give" -> grant(sender, args);
            case "revoke", "take" -> revoke(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void openMenu(CommandSender sender, boolean admin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the achievements menu.");
            return;
        }
        if (admin && !player.hasPermission(PERM_ADMIN)) {
            noPermission(player);
            return;
        }
        if (!admin && !player.hasPermission(PERM_USE)) {
            noPermission(player);
            return;
        }
        new AchievementMenu(plugin, player, admin).open(player);
    }

    private void create(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the editor.");
            return;
        }
        if (!player.hasPermission(PERM_ADMIN)) {
            noPermission(player);
            return;
        }
        String id = "achievement_" + (plugin.getAchievementManager().count() + 1);
        while (plugin.getAchievementManager().exists(id)) {
            id = id + "_new";
        }
        new EditorMenu(plugin, player, new Achievement(id), true).open(player);
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Text.parse("<gold><bold>Custom Achievements <gray>(" + plugin.getAchievementManager().count() + ")"));
        PlayerData data = sender instanceof Player player
                ? plugin.getPlayerDataManager().get(player.getUniqueId())
                : null;
        for (Achievement achievement : plugin.getAchievementManager().all()) {
            String status;
            if (data != null && data.isCompleted(achievement.getId())) {
                status = "<green>✔";
            } else if (data != null && achievement.getTrigger().isProgress()) {
                status = "<yellow>" + data.getProgress(achievement.getId()) + "/" + achievement.requiredAmount();
            } else {
                status = "<red>✖";
            }
            sender.sendMessage(Text.parse(" <dark_gray>- " + status + " <white>" + achievement.getDisplayName()
                    + " <dark_gray>(" + achievement.getId() + ")"));
        }
    }

    private void grant(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Usage: /ca grant <player> <id>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.parse("<red>Player <white>" + args[1] + "<red> is not online."));
            return;
        }
        Achievement achievement = plugin.getAchievementManager().get(args[2]);
        if (achievement == null) {
            sender.sendMessage(Text.parse("<red>No achievement with id <white>" + args[2] + "<red>."));
            return;
        }
        if (plugin.getAchievementService().grant(target, achievement)) {
            sender.sendMessage(Text.parse("<green>Granted <white>" + achievement.getId()
                    + "<green> to <white>" + target.getName() + "<green>."));
        } else {
            sender.sendMessage(Text.parse("<yellow>" + target.getName() + " already has that achievement."));
        }
    }

    private void revoke(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Usage: /ca revoke <player> <id>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.parse("<red>Player <white>" + args[1] + "<red> is not online."));
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        data.revoke(args[2]);
        plugin.getPlayerDataManager().save(target.getUniqueId());
        sender.sendMessage(Text.parse("<green>Revoked <white>" + args[2]
                + "<green> from <white>" + target.getName() + "<green>."));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Usage: /ca reset <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.parse("<red>Player <white>" + args[1] + "<red> is not online."));
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        data.reset();
        plugin.getPlayerDataManager().save(target.getUniqueId());
        sender.sendMessage(Text.parse("<green>Reset all achievements for <white>" + target.getName() + "<green>."));
    }

    private void reload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        plugin.getAchievementManager().load();
        sender.sendMessage(Text.parse("<green>CustomAchievements reloaded. <gray>("
                + plugin.getAchievementManager().count() + " achievements)"));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.parse("<gold><bold>CustomAchievements"));
        sender.sendMessage(Text.parse("<yellow>/ca <gray>- Open your achievements menu"));
        sender.sendMessage(Text.parse("<yellow>/ca list <gray>- List achievements in chat"));
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(Text.parse("<yellow>/ca admin <gray>- Manage achievements (GUI)"));
            sender.sendMessage(Text.parse("<yellow>/ca create <gray>- Create a new achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca grant <player> <id> <gray>- Grant an achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca revoke <player> <id> <gray>- Revoke an achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca reset <player> <gray>- Reset a player's achievements"));
            sender.sendMessage(Text.parse("<yellow>/ca reload <gray>- Reload configuration"));
        }
    }

    private boolean hasAdmin(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            noPermission(sender);
            return false;
        }
        return true;
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Text.parse("<red>You don't have permission to do that."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(List.of("admin", "create", "grant", "revoke", "reset", "reload"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : subs) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (!sender.hasPermission(PERM_ADMIN)) {
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("grant") || sub.equals("revoke") || sub.equals("reset"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 3 && (sub.equals("grant") || sub.equals("revoke"))) {
            for (Achievement achievement : plugin.getAchievementManager().all()) {
                if (achievement.getId().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(achievement.getId());
                }
            }
        }
        return out;
    }
}
