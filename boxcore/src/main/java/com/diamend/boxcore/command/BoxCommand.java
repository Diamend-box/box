package com.diamend.boxcore.command;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.CollectionCategoryMenu;
import com.diamend.boxcore.gui.CollectionListMenu;
import com.diamend.boxcore.gui.HubMenu;
import com.diamend.boxcore.gui.SkillTreeMenu;
import com.diamend.boxcore.gui.TreePickerMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.skill.RespecCost;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillService;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /box} — the plugin's single command: menus for players, data tools for
 * staff.
 */
public class BoxCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "boxcore.admin";

    private final BoxCorePlugin plugin;

    public BoxCommand(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            requirePlayer(sender, player -> HubMenu.openFor(plugin, player));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skills", "tree", "trees" -> openSkills(sender, args);
            case "collections", "collection", "coll" -> collections(sender, args);
            case "points", "point" -> points(sender, args);
            case "respec" -> respec(sender);
            case "compress", "compressor" -> compress(sender, args);
            case "unlock" -> unlock(sender, args);
            case "reset" -> reset(sender, args);
            case "modules" -> modules(sender);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Player commands
    // ------------------------------------------------------------------

    private void openSkills(CommandSender sender, String[] args) {
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        if (skills == null) {
            messages().sendLiteral(sender, "<red>The skills module is disabled.");
            return;
        }
        requirePlayer(sender, player -> {
            if (args.length >= 2) {
                SkillTree tree = skills.trees().getTree(args[1]);
                if (tree == null) {
                    messages().sendLiteral(player, "<red>No skill tree called <white>" + args[1] + "<red>.");
                    return;
                }
                if (!tree.getPermission().isEmpty() && !player.hasPermission(tree.getPermission())) {
                    messages().send(player, "no-permission");
                    return;
                }
                new SkillTreeMenu(plugin, skills, tree).open(player);
                return;
            }
            new TreePickerMenu(plugin, skills).open(player);
        });
    }

    private void collections(CommandSender sender, String[] args) {
        CollectionsModule module = plugin.modules().get(CollectionsModule.class);
        if (module == null) {
            messages().sendLiteral(sender, "<red>The collections module is disabled.");
            return;
        }
        // /box collection set <player> <id> <amount>
        if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
            setCollection(sender, module, args);
            return;
        }
        requirePlayer(sender, player -> {
            if (args.length >= 2) {
                CollectionCategory category = module.collections().category(args[1]);
                if (category == null) {
                    messages().sendLiteral(player, "<red>No category called <white>" + args[1] + "<red>.");
                    return;
                }
                new CollectionListMenu(plugin, module, category, 0).open(player);
                return;
            }
            new CollectionCategoryMenu(plugin, module).open(player);
        });
    }

    private void respec(CommandSender sender) {
        requirePlayer(sender, player -> {
            if (!player.hasPermission("boxcore.respec")) {
                messages().send(player, "no-permission");
                return;
            }
            SkillsModule skills = plugin.modules().get(SkillsModule.class);
            if (skills == null) {
                messages().sendLiteral(player, "<red>The skills module is disabled.");
                return;
            }
            SkillService.RespecResult result = skills.service().respec(player);
            switch (result.outcome()) {
                case DISABLED -> messages().send(player, "respec-disabled");
                case NOTHING_TO_REFUND -> messages().send(player, "respec-nothing");
                case MISSING_ITEM -> {
                    RespecCost cost = skills.service().respecCost();
                    messages().send(player, "respec-needs-item",
                            "amount", cost.amount(),
                            "item", cost.displayName(),
                            "have", result.held());
                }
                case DONE -> messages().send(player, "respec-done",
                        "amount", result.refunded(), "plural", Messages.plural(result.refunded()));
            }
        });
    }

    // ------------------------------------------------------------------
    // Points
    // ------------------------------------------------------------------

    private void points(CommandSender sender, String[] args) {
        if (args.length == 1) {
            requirePlayer(sender, player -> {
                PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
                messages().sendLiteral(player, "<gray>Skill points — available <white>"
                        + profile.getAvailablePoints() + "<gray>, spent <white>"
                        + profile.getPointsSpent() + "<gray>, earned <white>"
                        + profile.getPointsEarned());
            });
            return;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            messages().sendLiteral(sender, "<red>Usage: /box points <give|take|set> <player> <amount>");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            messages().sendLiteral(sender, "<red><white>" + args[3] + "</white> isn't a number.");
            return;
        }
        withProfile(sender, args[2], profile -> {
            switch (mode) {
                case "give", "add" -> profile.addPoints(amount);
                case "take", "remove" -> profile.addPoints(-amount);
                case "set" -> profile.setAvailablePoints(amount);
                default -> {
                    messages().sendLiteral(sender, "<red>Use give, take or set.");
                    return;
                }
            }
            messages().sendLiteral(sender, "<green>" + args[2] + " now has <white>"
                    + profile.getAvailablePoints() + "<green> point(s) available.");
        });
    }

    // ------------------------------------------------------------------
    // Admin data tools
    // ------------------------------------------------------------------

    private void unlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        if (skills == null) {
            messages().sendLiteral(sender, "<red>The skills module is disabled.");
            return;
        }
        if (args.length < 3) {
            messages().sendLiteral(sender, "<red>Usage: /box unlock <player> <tree.node> [level]");
            return;
        }
        SkillNode node = skills.trees().getNode(args[2].toLowerCase(Locale.ROOT));
        if (node == null) {
            messages().sendLiteral(sender, "<red>No node called <white>" + args[2]
                    + "<red>. Node keys look like <white>combat.toughness<red>.");
            return;
        }
        int level = node.getMaxLevel();
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                messages().sendLiteral(sender, "<red><white>" + args[3] + "</white> isn't a number.");
                return;
            }
        }
        int target = level;
        withProfile(sender, args[1], profile -> {
            skills.service().setLevel(profile, node, target);
            Player online = Bukkit.getPlayer(profile.getUuid());
            if (online != null) {
                skills.effects().apply(online);
            }
            messages().sendLiteral(sender, "<green>Set <white>" + Text.plain(node.getDisplay())
                    + "<green> to level <white>" + Math.min(target, node.getMaxLevel())
                    + "<green> for <white>" + args[1] + "<green>.");
        });
    }

    private void setCollection(CommandSender sender, CollectionsModule module, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 5) {
            messages().sendLiteral(sender, "<red>Usage: /box collection set <player> <id> <amount>");
            return;
        }
        ItemCollection collection = module.collections().get(args[3]);
        if (collection == null) {
            messages().sendLiteral(sender, "<red>No collection called <white>" + args[3] + "<red>.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[4]);
        } catch (NumberFormatException ex) {
            messages().sendLiteral(sender, "<red><white>" + args[4] + "</white> isn't a number.");
            return;
        }
        withProfile(sender, args[2], profile -> {
            module.service().set(Bukkit.getPlayer(profile.getUuid()), profile, collection, amount);
            messages().sendLiteral(sender, "<green>Set <white>" + Text.plain(collection.getDisplay())
                    + "<green> to <white>" + Text.number(amount) + "<green> for <white>"
                    + args[2] + "<green>.");
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages().sendLiteral(sender, "<red>Usage: /box reset <player>");
            return;
        }
        UUID uuid = resolve(args[1]);
        if (uuid == null) {
            messages().send(sender, "unknown-player", "name", args[1]);
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        if (online != null && skills != null) {
            skills.effects().clear(online);
        }
        plugin.profiles().delete(uuid);
        if (online != null) {
            plugin.profiles().load(uuid);
            if (skills != null) {
                skills.effects().apply(online);
            }
        }
        messages().sendLiteral(sender, "<green>Wiped BoxCore data for <white>" + args[1] + "<green>.");
    }

    private void modules(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        messages().sendLiteral(sender, "<gray>Modules:");
        for (BoxModule module : plugin.modules().registered()) {
            boolean active = plugin.modules().isActive(module.id());
            messages().sendPlain(sender, "  " + (active ? "<green>●" : "<dark_gray>●")
                    + " <white>" + module.displayName() + " <dark_gray>(" + module.id() + ")");
            if (active) {
                for (String line : module.statusLines()) {
                    messages().sendPlain(sender, "    " + line);
                }
            }
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadConfig();
        plugin.ores().load(plugin.getConfig());
        plugin.modules().reloadAll();
        messages().sendLiteral(sender, "<green>Reloaded configuration and modules.");
    }

    /** {@code /box compress [on|off]} — per-player auto-compressor toggle. */
    private void compress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "players-only");
            return;
        }
        com.diamend.boxcore.ore.CompressorModule compressor = plugin.compressor();
        if (compressor == null) {
            messages().sendLiteral(sender, "<red>The auto-compressor is disabled on this server.");
            return;
        }
        boolean enabled;
        if (args.length >= 2) {
            String choice = args[1].toLowerCase(Locale.ROOT);
            if (choice.equals("on") || choice.equals("off")) {
                enabled = choice.equals("on");
                compressor.setEnabled(player, enabled);
            } else {
                messages().sendLiteral(sender, "<red>Usage: /box compress [on|off]");
                return;
            }
        } else {
            enabled = compressor.toggle(player);
        }
        messages().send(player, enabled ? "compressor-on" : "compressor-off");
    }

    private void help(CommandSender sender) {
        messages().sendLiteral(sender, "<gray>Commands:");
        messages().sendPlain(sender, "  <white>/box <gray>— open the hub");
        messages().sendPlain(sender, "  <white>/box skills [tree] <gray>— open your skill trees");
        messages().sendPlain(sender, "  <white>/box collections [category] <gray>— open your collections");
        messages().sendPlain(sender, "  <white>/box points <gray>— show your skill points");
        messages().sendPlain(sender, "  <white>/box respec <gray>— refund every node you own");
        messages().sendPlain(sender, "  <white>/box compress [on|off] <gray>— toggle the auto-compressor");
        if (sender.hasPermission(ADMIN)) {
            messages().sendPlain(sender, "  <white>/box points <give|take|set> <player> <n>");
            messages().sendPlain(sender, "  <white>/box unlock <player> <tree.node> [level]");
            messages().sendPlain(sender, "  <white>/box collection set <player> <id> <amount>");
            messages().sendPlain(sender, "  <white>/box reset <player>");
            messages().sendPlain(sender, "  <white>/box modules <gray>— list loaded modules");
            messages().sendPlain(sender, "  <white>/box reload");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            messages().send(sender, "players-only");
        }
    }

    /** Resolves a name to a UUID without blocking on a web lookup. */
    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    /**
     * Runs an edit against a player's profile, online or not, and persists it
     * immediately when the player isn't logged in.
     */
    private void withProfile(CommandSender sender, String name, Consumer<PlayerProfile> action) {
        UUID uuid = resolve(name);
        if (uuid == null) {
            messages().send(sender, "unknown-player", "name", name);
            return;
        }
        boolean online = plugin.profiles().isCached(uuid);
        PlayerProfile profile = plugin.profiles().loadDetached(uuid);
        action.accept(profile);
        if (!online) {
            plugin.profiles().saveNow(profile);
        }
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            options.addAll(List.of("skills", "collections", "points", "respec", "compress"));
            if (admin) {
                options.addAll(List.of("unlock", "collection", "reset", "modules", "reload"));
            }
            return filter(options, args[0]);
        }

        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "compress", "compressor" -> {
                    return filter(List.of("on", "off"), args[1]);
                }
                case "skills", "tree", "trees" -> {
                    if (skills != null) {
                        for (SkillTree tree : skills.trees().trees()) {
                            options.add(tree.getId());
                        }
                    }
                }
                case "collections", "coll" -> {
                    if (collections != null) {
                        for (CollectionCategory category : collections.collections().categories()) {
                            options.add(category.getId());
                        }
                        if (admin) {
                            options.add("set");
                        }
                    }
                }
                case "collection" -> {
                    if (admin) {
                        options.add("set");
                    }
                }
                case "points" -> {
                    if (admin) {
                        options.addAll(List.of("give", "take", "set"));
                    }
                }
                case "unlock", "reset" -> {
                    if (admin) {
                        options.addAll(onlineNames());
                    }
                }
                default -> {
                }
            }
            return filter(options, args[1]);
        }

        if (args.length == 3) {
            if (admin && (sub.equals("points") || sub.equals("collection")
                    || (sub.equals("collections") && args[1].equalsIgnoreCase("set")))) {
                options.addAll(onlineNames());
            } else if (admin && sub.equals("unlock") && skills != null) {
                options.addAll(skills.trees().nodesByKey().keySet());
            }
            return filter(options, args[2]);
        }

        if (args.length == 4 && admin && collections != null
                && (sub.equals("collection") || sub.equals("collections"))
                && args[1].equalsIgnoreCase("set")) {
            for (ItemCollection collection : collections.collections().all()) {
                options.add(collection.getId());
            }
            return filter(options, args[3]);
        }

        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
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
