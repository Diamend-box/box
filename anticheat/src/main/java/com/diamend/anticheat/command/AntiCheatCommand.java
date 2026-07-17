package com.diamend.anticheat.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.diamend.anticheat.AntiCheatPlugin;
import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.gui.AntiCheatGui;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.violation.PunishmentMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@code /anticheat} (alias {@code /ac}) staff command. Subcommands:
 * <ul>
 *   <li>{@code verbose} — toggle per-flag output for yourself</li>
 *   <li>{@code info <player>} — show a player's current violation levels</li>
 *   <li>{@code reset <player>} — clear a player's violation levels</li>
 *   <li>{@code mode [alert-only|enforce]} — view or switch the response mode</li>
 *   <li>{@code checks} — list checks and whether each is enabled</li>
 *   <li>{@code reload} — reload config.yml</li>
 * </ul>
 */
public final class AntiCheatCommand implements TabExecutor {

    private static final String CMD_PERM = "anticheat.command";
    private static final String ADMIN_PERM = "anticheat.admin";

    private final AntiCheatPlugin plugin;
    private final AlertManager alerts;
    private final AntiCheatGui gui;

    public AntiCheatCommand(AntiCheatPlugin plugin, AlertManager alerts, AntiCheatGui gui) {
        this.plugin = plugin;
        this.alerts = alerts;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CMD_PERM)) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            // Bare /ac opens the GUI for players (the "ease of use" front door),
            // and prints help for the console.
            if (sender instanceof Player player) {
                gui.openMain(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu", "panel" -> handleGui(sender);
            case "verbose" -> handleVerbose(sender);
            case "info" -> handleInfo(sender, args);
            case "reset" -> handleReset(sender, args);
            case "mode" -> handleMode(sender, args);
            case "checks" -> handleChecks(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the GUI.", NamedTextColor.RED));
            return;
        }
        gui.openMain(player);
    }

    private void handleVerbose(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle verbose.", NamedTextColor.RED));
            return;
        }
        boolean on = alerts.toggleVerbose(player.getUniqueId());
        player.sendMessage(prefix().append(Component.text(
                "Verbose alerts " + (on ? "enabled." : "disabled."),
                on ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix().append(Component.text("Usage: /ac info <player>", NamedTextColor.GRAY)));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix().append(Component.text("Player not online.", NamedTextColor.RED)));
            return;
        }
        PlayerData data = plugin.players().get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(prefix().append(Component.text("No data for that player.", NamedTextColor.RED)));
            return;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(prefix().append(Component.text(
                "Violation levels for " + target.getName() + ":", NamedTextColor.YELLOW)));
        for (CheckType type : CheckType.values()) {
            double vl = data.violations().level(type, now);
            sender.sendMessage(Component.text("  " + type.displayName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(String.format(Locale.ROOT, "%.1f", vl),
                            vl > 0 ? NamedTextColor.RED : NamedTextColor.GREEN)));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix().append(Component.text("Usage: /ac reset <player>", NamedTextColor.GRAY)));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix().append(Component.text("Player not online.", NamedTextColor.RED)));
            return;
        }
        PlayerData data = plugin.players().get(target.getUniqueId());
        if (data != null) {
            data.violations().reset();
        }
        sender.sendMessage(prefix().append(Component.text(
                "Reset violations for " + target.getName() + ".", NamedTextColor.GREEN)));
    }

    private void handleMode(CommandSender sender, String[] args) {
        AntiCheatConfig cfg = plugin.config();
        if (args.length < 2) {
            sender.sendMessage(prefix().append(Component.text(
                    "Current mode: " + cfg.mode(), NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("  Use /ac mode alert-only | enforce", NamedTextColor.GRAY));
            return;
        }
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(prefix().append(Component.text(
                    "Changing mode requires " + ADMIN_PERM + ".", NamedTextColor.RED)));
            return;
        }
        PunishmentMode requested = PunishmentMode.fromConfig(args[1]);
        plugin.setMode(requested);
        sender.sendMessage(prefix().append(Component.text(
                "Mode set to " + requested + ".", NamedTextColor.GREEN)));
    }

    private void handleChecks(CommandSender sender) {
        AntiCheatConfig cfg = plugin.config();
        sender.sendMessage(prefix().append(Component.text("Checks:", NamedTextColor.YELLOW)));
        for (CheckType type : CheckType.values()) {
            boolean enabled = cfg.check(type) != null && cfg.check(type).enabled();
            sender.sendMessage(Component.text("  " + type.displayName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(enabled ? "enabled" : "disabled",
                            enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERM)) {
            sender.sendMessage(prefix().append(Component.text(
                    "Reload requires " + ADMIN_PERM + ".", NamedTextColor.RED)));
            return;
        }
        plugin.reloadAntiCheat();
        sender.sendMessage(prefix().append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix().append(Component.text("Commands:", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("  /ac gui", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac verbose", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac info <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac reset <player>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac mode [alert-only|enforce]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac checks", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /ac reload", NamedTextColor.GRAY));
    }

    private Component prefix() {
        return Component.text("[AC] ", NamedTextColor.RED);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(CMD_PERM)) {
            return out;
        }
        if (args.length == 1) {
            for (String sub : List.of("gui", "verbose", "info", "reset", "mode", "checks", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            out.add("alert-only");
            out.add("enforce");
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
