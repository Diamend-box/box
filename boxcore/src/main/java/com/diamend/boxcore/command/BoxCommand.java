package com.diamend.boxcore.command;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.BoostMenu;
import com.diamend.boxcore.gui.CollectionCategoryMenu;
import com.diamend.boxcore.gui.CollectionListMenu;
import com.diamend.boxcore.gui.HubMenu;
import com.diamend.boxcore.gui.RecipeEditorMenu;
import com.diamend.boxcore.gui.SkillTreeMenu;
import com.diamend.boxcore.gui.TreePickerMenu;
import com.diamend.boxcore.gui.WarpEditMenu;
import com.diamend.boxcore.gui.WarpEditorMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.ore.CompactRecipe;
import com.diamend.boxcore.ore.CompactorTier;
import com.diamend.boxcore.ore.CompressedOre;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.skill.RespecCost;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillService;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * {@code /box} — the plugin's single command: menus for players, data tools for
 * staff.
 */
public class BoxCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "boxcore.admin";

    /** The standalone command that opens the travel menu, from plugin.yml. */
    public static final String TRAVEL_COMMAND = "fasttravel";

    private final BoxCorePlugin plugin;

    public BoxCommand(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isTravelCommand(command)) {
            // /fasttravel, /fastravel and /ft are a shortcut to one screen, so
            // anything typed after them is ignored rather than second-guessed.
            travel(sender);
            return true;
        }
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
            case "compactor" -> compactor(sender, args);
            case "give", "givecompressed" -> giveCompressed(sender, args);
            case "boost", "boosts" -> boost(sender, args);
            case "travel", "warps" -> travel(sender);
            case "warp" -> warp(sender, args);
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
        // /box collection clearplaced [chunk radius]
        if (args.length >= 2 && args[1].equalsIgnoreCase("clearplaced")) {
            clearPlaced(sender, module, args);
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

    /**
     * Forgets the player-placed flags around the sender.
     *
     * <p>For a mine that was rebuilt by something which never fired a place
     * event — a WorldEdit paste, a mine-reset plugin — where flags left by the
     * players who mined it last would otherwise refuse the fresh ore.
     */
    private void clearPlaced(CommandSender sender, CollectionsModule module, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        int radius = 2;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                messages().sendLiteral(sender, "<red><white>" + args[2] + "</white> isn't a number.");
                return;
            }
            // 8 is a 17x17 square — plenty for a mine, and it keeps a single
            // command from loading thousands of chunks on a small host.
            if (radius < 0 || radius > 8) {
                messages().sendLiteral(sender, "<red>Use a chunk radius between <white>0</white> and <white>8</white>.");
                return;
            }
        }
        int chunks = radius;
        requirePlayer(sender, player -> {
            int cleared = module.placedBlocks().clearChunks(player.getWorld(),
                    player.getLocation().getBlockX() >> 4,
                    player.getLocation().getBlockZ() >> 4, chunks);
            int span = chunks * 2 + 1;
            messages().sendLiteral(sender, "<green>Cleared <white>" + Text.number(cleared)
                    + "<green> placed-block flag(s) across <white>" + (span * span)
                    + "<green> chunk(s).");
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

    /**
     * {@code /box compress [on|off]} — open the compactor you're carrying, or
     * pause compacting outright.
     *
     * <p>Bare {@code /box compress} opens the compactor rather than flipping the
     * toggle: what it is set to fold is the question players actually have, and
     * {@code on}/{@code off} still answers the other one in one line.
     */
    private void compress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "players-only");
            return;
        }
        CompressorModule compressor = plugin.compressor();
        if (compressor == null) {
            messages().sendLiteral(sender, "<red>The compactor is disabled on this server.");
            return;
        }
        if (args.length < 2) {
            compressor.openFor(player);
            return;
        }
        String choice = args[1].toLowerCase(Locale.ROOT);
        if (!choice.equals("on") && !choice.equals("off")) {
            messages().sendLiteral(sender, "<red>Usage: /box compress [on|off]");
            return;
        }
        boolean enabled = choice.equals("on");
        compressor.setEnabled(player, enabled);
        messages().send(player, enabled ? "compressor-on" : "compressor-off");
    }

    /**
     * {@code /box compactor give <tier> [player]} — hand out a compactor.
     *
     * <p>Admin-only and deliberately the only way to get one for now. When
     * compactors become something players earn, whatever grants them calls this
     * same path rather than reimplementing what a compactor is.
     */
    private void compactor(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        CompressorModule compressor = plugin.compressor();
        if (compressor == null) {
            messages().sendLiteral(sender, "<red>The compactor is disabled on this server.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("recipes")) {
            requirePlayer(sender, player ->
                    new RecipeEditorMenu(plugin, compressor, 0).open(player));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("give")) {
            messages().sendLiteral(sender, "<red>Usage: /box compactor <give <tier> [player] | recipes>");
            messages().sendLiteral(sender, "<gray>Tiers: <white>" + tierNames(compressor));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2].trim());
        } catch (NumberFormatException ex) {
            messages().sendLiteral(sender, "<red><white>" + args[2] + "</white> isn't a tier number.");
            return;
        }
        CompactorTier tier = compressor.tiers().get(level);
        if (tier == null) {
            messages().sendLiteral(sender, "<red>No compactor tier <white>" + level
                    + "<red>. Try one of: <white>" + tierNames(compressor) + "<red>.");
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                messages().send(sender, "unknown-player", "name", args[3]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages().sendLiteral(sender, "<red>Name a player: /box compactor give <tier> <player>");
            return;
        }

        ItemStack item = compressor.compactors().create(tier);
        if (item == null) {
            messages().sendLiteral(sender, "<red>Couldn't build that compactor.");
            return;
        }
        compressor.give(target, item);
        messages().sendLiteral(sender, "<green>Gave a tier <white>" + level
                + "<green> compactor (<white>" + tier.slots() + "<green> slots) to <white>"
                + target.getName() + "<green>.");
    }

    private String tierNames(CompressorModule compressor) {
        List<String> levels = new ArrayList<>();
        for (CompactorTier tier : compressor.tiers().all()) {
            levels.add(String.valueOf(tier.level()));
        }
        return String.join(", ", levels);
    }

    /**
     * {@code /box give <item> [units] [player]} — mint compacted units.
     *
     * <p>A test tool. A compacted unit is otherwise only reachable by gathering
     * a full recipe's worth with a compactor slotted for it, which makes
     * checking a custom skin, a drop rule or an expand a twenty-minute errand.
     * The units it hands out are real: same tag, same amount, same value.
     */
    private void giveCompressed(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        CompressorModule compressor = plugin.compressor();
        if (compressor == null) {
            messages().sendLiteral(sender, "<red>The auto-compressor is disabled on this server.");
            return;
        }
        if (args.length < 2) {
            messages().sendLiteral(sender, "<red>Usage: /box give <item> [units] [player]");
            return;
        }
        CompactRecipe recipe = matchRecipe(compressor, args[1]);
        if (recipe == null) {
            messages().sendLiteral(sender, "<red>Nothing compacts <white>" + args[1]
                    + "</white>. Try one of: <white>"
                    + String.join(", ", recipeNames()) + "<red>.");
            return;
        }
        Material ore = recipe.input();
        int units = 1;
        if (args.length >= 3) {
            try {
                units = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                messages().sendLiteral(sender, "<red><white>" + args[2] + "</white> isn't a number.");
                return;
            }
        }
        // An inventory holds 36 stacks; more than that would only ever spill
        // onto the floor, so cap it there rather than carpeting the spawn.
        units = Math.max(1, Math.min(units, 36 * 64));

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                messages().send(sender, "unknown-player", "name", args[3]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages().sendLiteral(sender, "<red>Name a player: /box give <ore> [units] <player>");
            return;
        }

        ItemStack stack = compressor.createUnit(ore, units);
        if (stack == null) {
            messages().sendLiteral(sender, "<red>Couldn't build a compacted <white>"
                    + ore.name() + "<red>.");
            return;
        }
        compressor.give(target, stack);
        messages().sendLiteral(sender, "<green>Gave <white>" + Text.number(units)
                + "<green> compacted <white>" + CompressedOre.displayName(ore)
                + "<green> (<white>" + Text.number((long) units * recipe.amount())
                + "<green> items) to <white>" + target.getName() + "<green>.");
    }

    /** Resolves a give-command argument to a recipe by id or by item name. */
    private CompactRecipe matchRecipe(CompressorModule compressor, String text) {
        CompactRecipe byId = compressor.recipes().get(text);
        if (byId != null) {
            return byId;
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (CompactRecipe recipe : compressor.recipes().all()) {
            String display = CompressedOre.displayName(recipe.input())
                    .toLowerCase(Locale.ROOT).replace(' ', '_');
            if (display.equals(wanted) || recipe.input().name().equalsIgnoreCase(wanted)) {
                return recipe;
            }
        }
        return null;
    }


    // ------------------------------------------------------------------
    // Fast travel
    // ------------------------------------------------------------------

    /** Whether this invocation came in on the standalone fast travel command. */
    private boolean isTravelCommand(Command command) {
        return TRAVEL_COMMAND.equalsIgnoreCase(command.getName());
    }

    /** {@code /box travel} — the destinations you've found. */
    private void travel(CommandSender sender) {
        TravelModule module = plugin.travel();
        if (module == null) {
            messages().sendLiteral(sender, "<red>Fast travel is disabled on this server.");
            return;
        }
        requirePlayer(sender, module::openFor);
    }

    /**
     * {@code /box warp set|delete|list} — staff tools for the destination list.
     *
     * <p>{@code set} takes the location from where you're standing and the icon
     * from what you're holding, including its name if you've renamed it. Typing
     * coordinates and colour codes into chat is a worse version of both.
     */
    private void warp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        TravelModule module = plugin.travel();
        if (module == null) {
            messages().sendLiteral(sender, "<red>Fast travel is disabled on this server.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "edit";
        switch (action) {
            case "set" -> setWarp(sender, module, args);
            case "delete", "remove" -> deleteWarp(sender, module, args);
            case "list" -> listWarps(sender, module);
            case "edit", "menu", "gui" -> editWarps(sender, module, args);
            case "move", "here" -> moveWarp(sender, module, args);
            case "icon" -> iconWarp(sender, module, args);
            case "rename", "name" -> renameWarp(sender, module, args);
            case "desc", "description" -> descWarp(sender, module, args);
            case "perm", "permission" -> permWarp(sender, module, args);
            case "radius" -> radiusWarp(sender, module, args);
            case "tp", "teleport", "goto" -> tpWarp(sender, module, args);
            default -> messages().sendLiteral(sender, "<red>Usage: /box warp "
                    + "<edit|set|move|icon|rename|desc|perm|radius|tp|delete|list> [id]");
        }
    }

    /** {@code /box warp edit [id]} — the editor, on the list or on one place. */
    private void editWarps(CommandSender sender, TravelModule module, String[] args) {
        requirePlayer(sender, player -> {
            if (args.length >= 3) {
                Warp warp = module.warps().get(args[2]);
                if (warp == null) {
                    unknownWarp(sender, args[2]);
                    return;
                }
                new WarpEditMenu(plugin, module, warp.id()).open(player);
                return;
            }
            new WarpEditorMenu(plugin, module, 0).open(player);
        });
    }

    /**
     * The warp an id-taking subcommand names, or null with the reason already
     * sent. Every one of these commands starts the same way.
     */
    private Warp namedWarp(CommandSender sender, TravelModule module, String[] args, String usage) {
        if (args.length < 3) {
            messages().sendLiteral(sender, "<red>Usage: " + usage);
            return null;
        }
        Warp warp = module.warps().get(args[2]);
        if (warp == null) {
            unknownWarp(sender, args[2]);
        }
        return warp;
    }

    private void unknownWarp(CommandSender sender, String id) {
        messages().sendLiteral(sender, "<red>No warp called <white>" + id + "<red>.");
    }

    private void moveWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp move <id>");
        if (warp == null) {
            return;
        }
        requirePlayer(sender, player -> {
            module.warps().put(warp.withLocation(player.getLocation().clone()));
            messages().sendLiteral(sender, "<green>Moved <white>" + warp.id()
                    + "<green> to where you're standing.");
        });
    }

    private void iconWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp icon <id>");
        if (warp == null) {
            return;
        }
        requirePlayer(sender, player -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                messages().sendLiteral(sender, "<red>Hold the item you want as the icon.");
                return;
            }
            module.warps().put(warp.withIcon(held.getType()));
            messages().sendLiteral(sender, "<green>Icon for <white>" + warp.id()
                    + "<green> is now <white>" + Items.prettyName(held.getType()) + "<green>.");
        });
    }

    private void renameWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp rename <id> <name>");
        if (warp == null) {
            return;
        }
        if (args.length < 4) {
            messages().sendLiteral(sender, "<red>Usage: /box warp rename <id> <name>");
            return;
        }
        String display = join(args, 3);
        module.warps().put(warp.withDisplay(display));
        messages().sendLiteral(sender, "<green><white>" + warp.id()
                + "</white> is now shown as <white>" + display + "<green>.");
    }

    private void descWarp(CommandSender sender, TravelModule module, String[] args) {
        String usage = "/box warp desc <id> <add|remove|clear> [text]";
        Warp warp = namedWarp(sender, module, args, usage);
        if (warp == null) {
            return;
        }
        String mode = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "";
        List<String> lines = new ArrayList<>(warp.description());
        switch (mode) {
            case "add" -> {
                if (args.length < 5) {
                    messages().sendLiteral(sender,
                            "<red>Usage: /box warp desc <id> add <text>");
                    return;
                }
                lines.add(join(args, 4));
                module.warps().put(warp.withDescription(lines));
                messages().sendLiteral(sender, "<green>Added a line to <white>"
                        + warp.id() + "<green>.");
            }
            case "remove", "pop" -> {
                if (lines.isEmpty()) {
                    messages().sendLiteral(sender, "<gray>It has no description.");
                    return;
                }
                lines.remove(lines.size() - 1);
                module.warps().put(warp.withDescription(lines));
                messages().sendLiteral(sender, "<yellow>Dropped the last line.");
            }
            case "clear" -> {
                module.warps().put(warp.withDescription(List.of()));
                messages().sendLiteral(sender, "<yellow>Cleared the description.");
            }
            default -> messages().sendLiteral(sender, "<red>Usage: " + usage);
        }
    }

    private void permWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp perm <id> [permission|none]");
        if (warp == null) {
            return;
        }
        if (args.length < 4) {
            messages().sendLiteral(sender, warp.permission().isEmpty()
                    ? "<gray><white>" + warp.id() + "</white> is open to everyone."
                    : "<gray><white>" + warp.id() + "</white> needs <white>" + warp.permission());
            return;
        }
        String permission = args[3];
        if (permission.equalsIgnoreCase("none") || permission.equalsIgnoreCase("clear")) {
            module.warps().put(warp.withPermission(""));
            messages().sendLiteral(sender, "<green><white>" + warp.id()
                    + "</white> is now open to everyone.");
            return;
        }
        module.warps().put(warp.withPermission(permission));
        messages().sendLiteral(sender, "<green><white>" + warp.id()
                + "</white> now needs <white>" + permission + "<green>.");
    }

    private void radiusWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp radius <id> <blocks>");
        if (warp == null) {
            return;
        }
        if (args.length < 4) {
            messages().sendLiteral(sender, "<gray><white>" + warp.id() + "</white> is found within "
                    + "<white>" + Math.round(warp.radius()) + "</white> blocks.");
            return;
        }
        double radius;
        try {
            radius = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            messages().sendLiteral(sender, "<red>That isn't a number of blocks.");
            return;
        }
        if (radius < 1 || radius > 250) {
            messages().sendLiteral(sender, "<red>Pick something between 1 and 250 blocks.");
            return;
        }
        module.warps().put(warp.withRadius(radius));
        messages().sendLiteral(sender, "<green><white>" + warp.id()
                + "</white> is now found within <white>" + Math.round(radius) + "</white> blocks.");
    }

    private void tpWarp(CommandSender sender, TravelModule module, String[] args) {
        Warp warp = namedWarp(sender, module, args, "/box warp tp <id>");
        if (warp == null) {
            return;
        }
        requirePlayer(sender, player -> {
            player.teleport(warp.location());
            messages().sendLiteral(sender, "<green>Teleported to <white>" + warp.id() + "<green>.");
        });
    }

    /** Everything from {@code index} on, joined back into the sentence it was. */
    private String join(String[] args, int index) {
        StringBuilder text = new StringBuilder();
        for (int position = index; position < args.length; position++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[position]);
        }
        return text.toString();
    }

    private void setWarp(CommandSender sender, TravelModule module, String[] args) {
        if (args.length < 3) {
            messages().sendLiteral(sender, "<red>Usage: /box warp set <id>");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "players-only");
            return;
        }
        String id = args[2].trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || !id.matches("[a-z0-9_-]+")) {
            messages().sendLiteral(sender,
                    "<red>Use letters, numbers, - and _ for a warp id.");
            return;
        }

        Warp existing = module.warps().get(id);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean holding = held != null && !held.getType().isAir();
        // Setting a warp on top of an existing one moves it. Everything else
        // about it — its icon, name and settings — is left alone unless this
        // call actually carries a replacement.
        Material icon = holding ? held.getType()
                : existing == null ? Material.ENDER_PEARL : existing.icon();
        String display = id;
        if (holding && held.getItemMeta() != null && held.getItemMeta().hasDisplayName()) {
            display = Text.serialize(held.getItemMeta().displayName());
        }

        Warp warp = new Warp(id,
                existing == null ? display : existing.display(),
                icon,
                existing == null ? List.of() : existing.description(),
                player.getLocation().clone(),
                existing == null ? "" : existing.permission(),
                existing == null
                        ? plugin.getConfig().getDouble("travel.default-radius", 8.0)
                        : existing.radius());
        module.warps().put(warp);
        messages().sendLiteral(sender, (existing == null
                ? "<green>Created warp <white>" : "<green>Moved warp <white>") + id
                + "<green> to where you're standing.");
    }

    private void deleteWarp(CommandSender sender, TravelModule module, String[] args) {
        if (args.length < 3) {
            messages().sendLiteral(sender, "<red>Usage: /box warp delete <id>");
            return;
        }
        String id = args[2].trim().toLowerCase(Locale.ROOT);
        if (module.warps().remove(id)) {
            messages().sendLiteral(sender, "<yellow>Deleted warp <white>" + id + "<yellow>.");
        } else {
            messages().sendLiteral(sender, "<red>No warp called <white>" + id + "<red>.");
        }
    }

    private void listWarps(CommandSender sender, TravelModule module) {
        if (module.warps().size() == 0) {
            messages().sendLiteral(sender,
                    "<gray>No warps yet. Stand somewhere and run <white>/box warp set <id><gray>.");
            return;
        }
        messages().sendLiteral(sender, "<gray>Warps:");
        for (Warp warp : module.warps().all()) {
            org.bukkit.Location at = warp.location();
            messages().sendPlain(sender, "  <white>" + warp.id() + " <dark_gray>— "
                    + (at.getWorld() == null ? "?" : at.getWorld().getName())
                    + " " + Math.round(at.getX()) + ", " + Math.round(at.getY())
                    + ", " + Math.round(at.getZ()));
        }
    }

    // ------------------------------------------------------------------
    // Boosts
    // ------------------------------------------------------------------

    /**
     * {@code /box boost} — see what is running; the rest is staff-only.
     *
     * <pre>
     * /box boost                                   what's running for you
     * /box boost global &lt;type&gt; &lt;mult&gt; &lt;duration&gt;    server-wide
     * /box boost player &lt;name&gt; &lt;type&gt; &lt;mult&gt; &lt;dur&gt;  one player
     * /box boost item &lt;id&gt; [player] [amount]        hand over a boost item
     * /box boost clear [global|&lt;player&gt;]            end them early
     * </pre>
     */
    private void boost(CommandSender sender, String[] args) {
        BoostsModule boosts = plugin.boosts();
        if (boosts == null) {
            messages().sendLiteral(sender, "<red>Boosts are disabled on this server.");
            return;
        }
        if (args.length < 2) {
            showBoosts(sender, boosts);
            return;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages().send(sender, "no-permission");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "global", "server" -> boostGlobal(sender, boosts, args);
            case "player", "give" -> boostPlayer(sender, boosts, args);
            case "item" -> boostItem(sender, boosts, args);
            case "clear", "stop" -> boostClear(sender, boosts, args);
            default -> messages().sendLiteral(sender,
                    "<red>Use global, player, item or clear.");
        }
    }

    private void showBoosts(CommandSender sender, BoostsModule boosts) {
        Player player = sender instanceof Player self ? self : null;
        if (player != null) {
            new BoostMenu(plugin, boosts).open(player);
            return;
        }
        List<String> active = boosts.summaryFor(player);
        if (active.isEmpty()) {
            messages().send(sender, "boost-none");
            return;
        }
        messages().sendLiteral(sender, "<gray>Boosts running:");
        for (String line : active) {
            messages().sendPlain(sender, "  " + line);
        }
    }

    private void boostGlobal(CommandSender sender, BoostsModule boosts, String[] args) {
        if (args.length < 5) {
            messages().sendLiteral(sender,
                    "<red>Usage: /box boost global <type> <multiplier> <duration>");
            return;
        }
        List<BoostType> types = BoostsModule.typesFor(args[2]);
        if (types.isEmpty()) {
            messages().sendLiteral(sender, boostTypeError(args[2]));
            return;
        }
        double multiplier = parseMultiplier(sender, args[3], boosts);
        if (multiplier <= 0) {
            return;
        }
        long duration = parseDuration(sender, args[4]);
        if (duration <= 0) {
            return;
        }
        for (BoostType type : types) {
            boosts.addGlobal(type, multiplier, duration, "admin");
        }
        messages().sendLiteral(sender, "<green>Started a <white>" + Text.decimal(multiplier)
                + "x<green> global boost for <white>" + Durations.format(duration) + "<green>.");
    }

    private void boostPlayer(CommandSender sender, BoostsModule boosts, String[] args) {
        if (args.length < 6) {
            messages().sendLiteral(sender,
                    "<red>Usage: /box boost player <name> <type> <multiplier> <duration>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "unknown-player", "name", args[2]);
            return;
        }
        List<BoostType> types = BoostsModule.typesFor(args[3]);
        if (types.isEmpty()) {
            messages().sendLiteral(sender, boostTypeError(args[3]));
            return;
        }
        double multiplier = parseMultiplier(sender, args[4], boosts);
        if (multiplier <= 0) {
            return;
        }
        long duration = parseDuration(sender, args[5]);
        if (duration <= 0) {
            return;
        }
        for (BoostType type : types) {
            boosts.addPlayer(target, type, multiplier, duration, "admin");
        }
        messages().send(target, "boost-activated",
                "type", types.size() > 1 ? "everything" : types.get(0).display(),
                "multiplier", Text.decimal(multiplier),
                "duration", Durations.format(duration));
        messages().sendLiteral(sender, "<green>Gave <white>" + target.getName()
                + "<green> a <white>" + Text.decimal(multiplier) + "x<green> boost for <white>"
                + Durations.format(duration) + "<green>.");
    }

    private void boostItem(CommandSender sender, BoostsModule boosts, String[] args) {
        if (args.length < 3) {
            messages().sendLiteral(sender, "<red>Usage: /box boost item <id> [player] [amount]"
                    + (boosts.itemIds().isEmpty() ? "" : " <dark_gray>— "
                            + String.join(", ", boosts.itemIds())));
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                messages().send(sender, "unknown-player", "name", args[3]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages().sendLiteral(sender, "<red>Name a player: /box boost item <id> <player>");
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Math.max(1, Math.min(Integer.parseInt(args[4]), 36 * 64));
            } catch (NumberFormatException ex) {
                messages().sendLiteral(sender, "<red><white>" + args[4] + "</white> isn't a number.");
                return;
            }
        }
        ItemStack item = boosts.createItem(args[2], amount);
        if (item == null) {
            messages().sendLiteral(sender, "<red>No boost item called <white>" + args[2]
                    + "<red>. Configured: <white>"
                    + (boosts.itemIds().isEmpty() ? "none" : String.join(", ", boosts.itemIds()))
                    + "<red>.");
            return;
        }
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        messages().sendLiteral(sender, "<green>Gave <white>" + target.getName()
                + "<green> " + Text.number(amount) + " <white>" + args[2] + "<green>.");
    }

    private void boostClear(CommandSender sender, BoostsModule boosts, String[] args) {
        if (args.length < 3 || args[2].equalsIgnoreCase("global")) {
            int cleared = boosts.clearGlobal();
            messages().sendLiteral(sender, "<green>Ended <white>" + cleared
                    + "<green> global boost(s).");
            return;
        }
        // Online only: a boost runs on the wall clock and is worth nothing to
        // someone who isn't here, and reaching for an offline profile just to
        // clear one would pull it into the cache with nothing to write it back.
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages().send(sender, "unknown-player", "name", args[2]);
            return;
        }
        int cleared = boosts.clearPlayer(target.getUniqueId());
        messages().sendLiteral(sender, "<green>Ended <white>" + cleared
                + "<green> boost(s) for <white>" + target.getName() + "<green>.");
    }

    private String boostTypeError(String given) {
        return "<red><white>" + given + "</white> isn't a boost type. Use <white>"
                + String.join(", ", boostTypeNames()) + "<red>.";
    }

    private List<String> boostTypeNames() {
        List<String> names = new ArrayList<>();
        for (BoostType type : BoostType.values()) {
            names.add(type.id());
        }
        names.add("all");
        return names;
    }

    /** Parses a multiplier, complaining about anything that would do nothing. */
    private double parseMultiplier(CommandSender sender, String text, BoostsModule boosts) {
        double multiplier;
        try {
            multiplier = Double.parseDouble(text.toLowerCase(Locale.ROOT).replace("x", "").trim());
        } catch (NumberFormatException ex) {
            messages().sendLiteral(sender, "<red><white>" + text + "</white> isn't a number.");
            return 0;
        }
        if (multiplier <= 1.0) {
            messages().sendLiteral(sender,
                    "<red>A multiplier of <white>" + Text.decimal(multiplier)
                            + "x</white> would do nothing. Use more than 1.");
            return 0;
        }
        if (multiplier > boosts.maxMultiplier()) {
            messages().sendLiteral(sender, "<red>The cap is <white>"
                    + Text.decimal(boosts.maxMultiplier())
                    + "x<red> (boosts.max-multiplier).");
            return 0;
        }
        return multiplier;
    }

    private long parseDuration(CommandSender sender, String text) {
        long millis = Durations.parse(text);
        if (millis <= 0) {
            messages().sendLiteral(sender, "<red><white>" + text
                    + "</white> isn't a duration. Try <white>30m<red>, <white>2h<red> or <white>1d<red>.");
        }
        return millis;
    }

    /** Compactable items by the short name the give command accepts. */
    private List<String> warpIds(TravelModule travel) {
        List<String> ids = new ArrayList<>();
        if (travel == null) {
            return ids;
        }
        for (Warp warp : travel.warps().all()) {
            ids.add(warp.id());
        }
        return ids;
    }

    private List<String> recipeNames() {
        CompressorModule compressor = plugin.compressor();
        List<String> names = new ArrayList<>();
        if (compressor == null) {
            return names;
        }
        for (CompactRecipe recipe : compressor.recipes().all()) {
            names.add(CompressedOre.displayName(recipe.input())
                    .toLowerCase(Locale.ROOT).replace(' ', '_'));
        }
        return names;
    }

    private void help(CommandSender sender) {
        messages().sendLiteral(sender, "<gray>Commands:");
        messages().sendPlain(sender, "  <white>/box <gray>— open the hub");
        messages().sendPlain(sender, "  <white>/box skills [tree] <gray>— open your skill trees");
        messages().sendPlain(sender, "  <white>/box collections [category] <gray>— open your collections");
        messages().sendPlain(sender, "  <white>/box points <gray>— show your skill points");
        messages().sendPlain(sender, "  <white>/box respec <gray>— refund every node you own");
        messages().sendPlain(sender, "  <white>/box compress [on|off] <gray>— open your compactor (or pause it)");
        messages().sendPlain(sender, "  <white>/box boost <gray>— show the boosts running for you");
        messages().sendPlain(sender, "  <white>/box travel <dark_gray>(/ft) "
                + "<gray>— open the places you've found");
        if (sender.hasPermission(ADMIN)) {
            messages().sendPlain(sender, "  <white>/box points <give|take|set> <player> <n>");
            messages().sendPlain(sender, "  <white>/box unlock <player> <tree.node> [level]");
            messages().sendPlain(sender, "  <white>/box collection set <player> <id> <amount>");
            messages().sendPlain(sender, "  <white>/box collection clearplaced [chunk radius] "
                    + "<gray>— after a mine regen");
            messages().sendPlain(sender, "  <white>/box give <item> [units] [player] "
                    + "<gray>— compacted items, for testing");
            messages().sendPlain(sender, "  <white>/box compactor give <tier> [player] "
                    + "<gray>— hand out a personal compactor");
            messages().sendPlain(sender, "  <white>/box compactor recipes "
                    + "<gray>— add and edit what compacts");
            messages().sendPlain(sender, "  <white>/box warp <gray>— edit destinations in a menu");
            messages().sendPlain(sender, "  <white>/box warp <set|move|icon|rename> <id> "
                    + "<gray>— from where you stand and what you hold");
            messages().sendPlain(sender, "  <white>/box warp <desc|perm|radius|tp|delete> <id> "
                    + "<gray>— the rest of it");
            messages().sendPlain(sender, "  <white>/box boost global <type> <mult> <duration>");
            messages().sendPlain(sender, "  <white>/box boost player <name> <type> <mult> <duration>");
            messages().sendPlain(sender, "  <white>/box boost item <id> [player] [amount]");
            messages().sendPlain(sender, "  <white>/box boost clear [global|<player>]");
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
        if (isTravelCommand(command)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            options.addAll(List.of("skills", "collections", "points", "respec", "compress",
                    "boost", "travel"));
            if (admin) {
                options.addAll(List.of("give", "compactor", "warp", "unlock", "collection",
                        "reset", "modules", "reload"));
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
                case "give", "givecompressed" -> {
                    if (admin) {
                        options.addAll(recipeNames());
                    }
                }
                case "compactor" -> {
                    if (admin) {
                        options.addAll(List.of("give", "recipes"));
                    }
                }
                case "warp" -> {
                    if (admin) {
                        options.addAll(List.of("edit", "set", "move", "icon", "rename",
                                "desc", "perm", "radius", "tp", "delete", "list"));
                    }
                }
                case "boost", "boosts" -> {
                    if (admin) {
                        options.addAll(List.of("global", "player", "item", "clear"));
                    }
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
                            options.addAll(List.of("set", "clearplaced"));
                        }
                    }
                }
                case "collection" -> {
                    if (admin) {
                        options.addAll(List.of("set", "clearplaced"));
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

        if (admin && (sub.equals("boost") || sub.equals("boosts"))) {
            BoostsModule boosts = plugin.boosts();
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3) {
                return switch (mode) {
                    case "global", "server" -> filter(boostTypeNames(), args[2]);
                    case "player", "give" -> filter(onlineNames(), args[2]);
                    case "item" -> filter(boosts == null
                            ? List.<String>of() : new ArrayList<>(boosts.itemIds()), args[2]);
                    case "clear", "stop" -> {
                        List<String> targets = new ArrayList<>(List.of("global"));
                        targets.addAll(onlineNames());
                        yield filter(targets, args[2]);
                    }
                    default -> List.of();
                };
            }
            if (args.length == 4) {
                return switch (mode) {
                    case "global", "server" -> filter(List.of("2", "3", "1.5"), args[3]);
                    case "player", "give" -> filter(boostTypeNames(), args[3]);
                    case "item" -> filter(onlineNames(), args[3]);
                    default -> List.of();
                };
            }
            if (args.length == 5) {
                return switch (mode) {
                    case "global", "server" -> filter(List.of("30m", "1h", "2h"), args[4]);
                    case "player", "give" -> filter(List.of("2", "3", "1.5"), args[4]);
                    default -> List.of();
                };
            }
            if (args.length == 6 && (mode.equals("player") || mode.equals("give"))) {
                return filter(List.of("30m", "1h", "2h"), args[5]);
            }
            return List.of();
        }

        if (admin && sub.equals("warp")) {
            TravelModule travel = plugin.travel();
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && !mode.equals("set")) {
                return filter(warpIds(travel), args[2]);
            }
            if (args.length == 4) {
                return switch (mode) {
                    case "desc", "description" -> filter(List.of("add", "remove", "clear"), args[3]);
                    case "perm", "permission" -> {
                        List<String> suggestions = new ArrayList<>(List.of("none"));
                        suggestions.add("boxcore.warp." + args[2].toLowerCase(Locale.ROOT));
                        yield filter(suggestions, args[3]);
                    }
                    case "radius" -> filter(List.of("8", "16", "32"), args[3]);
                    default -> List.of();
                };
            }
            return List.of();
        }

        if (args.length == 3 && admin && (sub.equals("give") || sub.equals("givecompressed"))) {
            return filter(List.of("1", "8", "64"), args[2]);
        }

        if (args.length == 4 && admin && (sub.equals("give") || sub.equals("givecompressed"))) {
            return filter(onlineNames(), args[3]);
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
