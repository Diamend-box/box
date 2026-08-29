package com.diamend.customachievements.command;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.gui.AchievementMenu;
import com.diamend.customachievements.gui.EditorMenu;
import com.diamend.customachievements.gui.Menu;
import com.diamend.customachievements.gui.RewardClaimMenu;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        // Standalone /reopen (and /careopen) command.
        if (command.getName().equalsIgnoreCase("careopen")) {
            reopen(sender);
            return true;
        }

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
            case "trigger", "fire" -> trigger(sender, args);
            case "backfill", "seed" -> backfill(sender, args);
            case "grant", "give" -> grant(sender, args);
            case "revoke", "take" -> revoke(sender, args);
            case "reset" -> reset(sender, args);
            case "top", "leaderboard" -> top(sender);
            case "info", "inspect" -> info(sender, args);
            case "claim" -> claim(sender);
            case "reopen" -> reopen(sender);
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
        if (!admin && plugin.getAchievementManager().hasCategories()) {
            new com.diamend.customachievements.gui.CategoryMenu(plugin, player).open(player);
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
            } else if (data != null) {
                int total = achievement.getRequirements().size();
                int done = 0;
                for (int i = 0; i < total; i++) {
                    if (data.getProgress(PlayerData.requirementKey(achievement.getId(), i))
                            >= achievement.getRequirements().get(i).requiredAmount()) {
                        done++;
                    }
                }
                status = "<yellow>" + done + "/" + total + " objectives";
            } else {
                status = "<red>✖";
            }
            sender.sendMessage(Text.parse(" <dark_gray>- " + status + " <white>" + achievement.getDisplayName()
                    + " <dark_gray>(" + achievement.getId() + ")"));
        }
    }

    /**
     * {@code /ca backfill [player] [redo]} — re-runs the statistics seeding and
     * reports what it read for every unfinished objective, so a total that
     * didn't appear can be diagnosed instead of guessed at. {@code redo} seeds
     * objectives already seeded once, which is the only way to retry after
     * fixing whatever made the first attempt come up empty.
     */
    private void backfill(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        boolean redo = args.length > 1 && args[args.length - 1].equalsIgnoreCase("redo");
        String name = args.length > 1 && !args[1].equalsIgnoreCase("redo") ? args[1] : null;
        Player target;
        if (name != null) {
            target = Bukkit.getPlayerExact(name);
            if (target == null) {
                sender.sendMessage(Text.parse("<red><white>" + name + "<red> is not online."));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Text.parse("<red>From the console, name a player: /ca backfill <player> [redo]"));
            return;
        }
        if (!plugin.getConfig().getBoolean("backfill-from-statistics", true)) {
            sender.sendMessage(Text.parse("<yellow>Note: <white>backfill-from-statistics<yellow> is off in "
                    + "config.yml, so this won't run on join. Running now because you asked."));
        }
        List<String> report = plugin.getAchievementService().seedWithReport(target, redo);
        sender.sendMessage(Text.parse("<gold>Backfill for <white>" + target.getName()
                + "<gold>" + (redo ? " <gray>(redo)" : "") + ":"));
        if (report.isEmpty()) {
            sender.sendMessage(Text.parse("<gray>  Nothing to do — every achievement is already completed."));
            return;
        }
        for (String line : report) {
            sender.sendMessage(Text.parse("<gray>  " + line));
        }
    }

    /**
     * {@code /ca trigger <player> <key> [amount|set <value>]} — fires a custom
     * trigger key. Anything that can run a console command can drive an
     * achievement through this: Skript, other plugins' reward commands, command
     * blocks, datapacks.
     */
    private void trigger(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Usage: /ca trigger <player> <key> [amount|set <value>]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            // Progress needs the player's loaded data (and an unlock needs
            // someone to hand the rewards to), so this one is online-only.
            sender.sendMessage(Text.parse("<red><white>" + args[1] + "<red> is not online."));
            return;
        }
        String key = args[2];
        boolean set = args.length > 3 && args[3].equalsIgnoreCase("set");
        String number = set ? (args.length > 4 ? args[4] : null) : (args.length > 3 ? args[3] : "1");
        if (number == null) {
            sender.sendMessage(Text.parse("<red>Usage: /ca trigger <player> <key> set <value>"));
            return;
        }
        int value;
        try {
            value = Integer.parseInt(number.trim());
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.parse("<red><white>" + number + "<red> is not a whole number."));
            return;
        }
        if (set) {
            if (value < 0) {
                sender.sendMessage(Text.parse("<red>A value to set can't be negative."));
                return;
            }
            plugin.getAchievementService().setCustom(target, key, value);
            sender.sendMessage(Text.parse("<green>Set <white>" + key + "<green> to <white>" + value
                    + "<green> for <white>" + target.getName() + "<green>."));
            return;
        }
        if (value <= 0) {
            sender.sendMessage(Text.parse("<red>An amount to add must be 1 or more."));
            return;
        }
        plugin.getAchievementService().handleCustom(target, key, value);
        sender.sendMessage(Text.parse("<green>Fired <white>" + key + "<green> x<white>" + value
                + "<green> for <white>" + target.getName() + "<green>."));
    }

    private void grant(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Usage: /ca grant <player> <id>"));
            return;
        }
        Achievement achievement = plugin.getAchievementManager().get(args[2]);
        if (achievement == null) {
            sender.sendMessage(Text.parse("<red>No achievement with id <white>" + args[2] + "<red>."));
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            if (plugin.getAchievementService().grant(online, achievement)) {
                sender.sendMessage(Text.parse("<green>Granted <white>" + achievement.getId()
                        + "<green> to <white>" + online.getName() + "<green>."));
            } else {
                sender.sendMessage(Text.parse("<yellow>" + online.getName() + " already has that achievement."));
            }
            return;
        }
        UUID uuid = resolveOffline(sender, args[1]);
        if (uuid == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().loadDetached(uuid);
        if (data.isCompleted(achievement.getId())) {
            sender.sendMessage(Text.parse("<yellow>" + args[1] + " already has that achievement."));
            return;
        }
        data.setCompleted(achievement.getId());
        plugin.getPlayerDataManager().saveNow(data);
        sender.sendMessage(Text.parse("<green>Granted <white>" + achievement.getId() + "<green> to <white>"
                + args[1] + " <gray>(offline — item/command rewards skipped)."));
    }

    private void revoke(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Usage: /ca revoke <player> <id>"));
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            data.revoke(args[2]);
            plugin.getPlayerDataManager().save(online.getUniqueId());
            sender.sendMessage(Text.parse("<green>Revoked <white>" + args[2]
                    + "<green> from <white>" + online.getName() + "<green>."));
            return;
        }
        UUID uuid = resolveOffline(sender, args[1]);
        if (uuid == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().loadDetached(uuid);
        data.revoke(args[2]);
        plugin.getPlayerDataManager().saveNow(data);
        sender.sendMessage(Text.parse("<green>Revoked <white>" + args[2]
                + "<green> from <white>" + args[1] + " <gray>(offline)."));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Usage: /ca reset <player>"));
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
            data.reset();
            plugin.getPlayerDataManager().save(online.getUniqueId());
            sender.sendMessage(Text.parse("<green>Reset all achievements for <white>" + online.getName() + "<green>."));
            return;
        }
        UUID uuid = resolveOffline(sender, args[1]);
        if (uuid == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().loadDetached(uuid);
        data.reset();
        plugin.getPlayerDataManager().saveNow(data);
        sender.sendMessage(Text.parse("<green>Reset all achievements for <white>" + args[1] + " <gray>(offline)."));
    }

    /** Resolves an offline player's UUID, or messages an error and returns null. */
    private UUID resolveOffline(CommandSender sender, String name) {
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(Text.parse("<red>Player <white>" + name + "<red> has never joined this server."));
            return null;
        }
        return offline.getUniqueId();
    }

    private void top(CommandSender sender) {
        if (!sender.hasPermission(PERM_USE)) {
            noPermission(sender);
            return;
        }
        sender.sendMessage(Text.parse("<gray>Building the leaderboard..."));
        // Snapshot the current achievement ids on the main thread so the async
        // count only credits achievements that still exist (deleted ones don't
        // inflate a player's total).
        Set<String> validIds = plugin.getAchievementManager().ids();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map.Entry<UUID, Integer>> ranked =
                    new ArrayList<>(plugin.getPlayerDataManager().completedCounts(validIds).entrySet());
            ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Text.parse("<gold><bold>Achievement Leaderboard"));
                int rank = 1;
                for (Map.Entry<UUID, Integer> entry : ranked) {
                    if (rank > 10 || entry.getValue() <= 0) {
                        break;
                    }
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    if (name == null) {
                        name = entry.getKey().toString().substring(0, 8);
                    }
                    sender.sendMessage(Text.parse(" <yellow>" + rank + ".<white> " + name
                            + " <gray>— <green>" + entry.getValue()));
                    rank++;
                }
                if (rank == 1) {
                    sender.sendMessage(Text.parse("<gray>No completions yet."));
                }
            });
        });
    }

    private void info(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_USE)) {
            noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Usage: /ca info <id>"));
            return;
        }
        Achievement achievement = plugin.getAchievementManager().get(args[1]);
        if (achievement == null) {
            sender.sendMessage(Text.parse("<red>No achievement with id <white>" + args[1] + "<red>."));
            return;
        }
        boolean admin = sender.hasPermission(PERM_ADMIN);

        sender.sendMessage(Text.parse("<gold><bold>" + achievement.getDisplayName()
                + " <dark_gray>(" + achievement.getId() + ")"));
        for (String line : achievement.getDescription()) {
            sender.sendMessage(Text.parse("<gray>" + line));
        }
        if (!achievement.getCategory().isBlank()) {
            sender.sendMessage(Text.parse("<gray>Category: <white>" + achievement.getCategory()));
        }
        if (achievement.isHidden()) {
            sender.sendMessage(Text.parse("<gray>Secret: <white>yes"));
        }

        sender.sendMessage(Text.parse("<yellow>Objectives <gray>(all required):"));
        int index = 1;
        for (var requirement : achievement.getRequirements()) {
            sender.sendMessage(Text.parse(" <dark_gray>" + index + ". <white>" + requirement.describe()));
            index++;
        }

        java.util.List<String> rewards = new ArrayList<>();
        if (achievement.getRewardXp() > 0) {
            rewards.add(achievement.getRewardXp() + " XP");
        }
        if (!achievement.getRewardItems().isEmpty()) {
            rewards.add(achievement.getRewardItems().size() + " item(s)");
        }
        if (!rewards.isEmpty()) {
            sender.sendMessage(Text.parse("<yellow>Rewards: <white>" + String.join(", ", rewards)));
        }
        if (admin && !achievement.getRewardCommands().isEmpty()) {
            sender.sendMessage(Text.parse("<yellow>Reward commands:"));
            for (String command : achievement.getRewardCommands()) {
                sender.sendMessage(Text.parse(" <dark_gray>- <white>/" + command));
            }
        }
        sender.sendMessage(Text.parse("<gray>Broadcasts on unlock: <white>"
                + (achievement.isAnnounce() ? "yes" : "no")));
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can claim rewards.");
            return;
        }
        if (!player.hasPermission(PERM_USE)) {
            noPermission(player);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasPendingRewards()) {
            player.sendMessage(Text.parse("<gray>You have no unclaimed rewards."));
            return;
        }
        new RewardClaimMenu(plugin, player).open(player);
    }

    private void reopen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can reopen a menu.");
            return;
        }
        if (!player.hasPermission(PERM_USE)) {
            noPermission(player);
            return;
        }
        Menu menu = plugin.getLastMenu(player.getUniqueId());
        if (menu == null) {
            player.sendMessage(Text.parse("<gray>There's no recent achievements menu to reopen."));
            return;
        }
        menu.open(player);
    }

    private void reload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        plugin.getAchievementManager().load();
        // Achievements edited on disk may be new to players who are already on.
        plugin.getAchievementService().backfillOnline();
        sender.sendMessage(Text.parse("<green>CustomAchievements reloaded. <gray>("
                + plugin.getAchievementManager().count() + " achievements)"));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.parse("<gold><bold>CustomAchievements"));
        sender.sendMessage(Text.parse("<yellow>/ca <gray>- Open your achievements menu"));
        sender.sendMessage(Text.parse("<yellow>/ca list <gray>- List achievements in chat"));
        sender.sendMessage(Text.parse("<yellow>/ca info <id> <gray>- Show an achievement's details"));
        sender.sendMessage(Text.parse("<yellow>/ca top <gray>- Completion leaderboard"));
        sender.sendMessage(Text.parse("<yellow>/ca claim <gray>- Collect unclaimed reward items"));
        sender.sendMessage(Text.parse("<yellow>/reopen <gray>- Reopen the last menu you closed"));
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(Text.parse("<yellow>/ca admin <gray>- Manage achievements (GUI)"));
            sender.sendMessage(Text.parse("<yellow>/ca create <gray>- Create a new achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca grant <player> <id> <gray>- Grant an achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca revoke <player> <id> <gray>- Revoke an achievement"));
            sender.sendMessage(Text.parse("<yellow>/ca reset <player> <gray>- Reset a player's achievements"));
            sender.sendMessage(Text.parse("<yellow>/ca trigger <player> <key> <gray>- Fire a custom trigger"));
            sender.sendMessage(Text.parse("<yellow>/ca backfill <player> [redo] <gray>- Re-seed from statistics"));
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
            List<String> subs = new ArrayList<>(List.of("list", "info", "top", "claim", "reopen"));
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(List.of("admin", "create", "grant", "revoke", "reset", "trigger",
                        "backfill", "reload"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : subs) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        // /ca info <id> is available to everyone with use permission.
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (Achievement achievement : plugin.getAchievementManager().all()) {
                if (achievement.getId().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(achievement.getId());
                }
            }
            return out;
        }
        if (!sender.hasPermission(PERM_ADMIN)) {
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 3 && sub.equals("backfill")) {
            out.add("redo");
        } else if (args.length == 2 && (sub.equals("grant") || sub.equals("revoke") || sub.equals("reset")
                || sub.equals("trigger") || sub.equals("backfill"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 3 && sub.equals("trigger")) {
            // Suggest the keys the server's own achievements actually listen for.
            for (String key : plugin.getAchievementManager().customTriggerKeys()) {
                if (key.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(key);
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
