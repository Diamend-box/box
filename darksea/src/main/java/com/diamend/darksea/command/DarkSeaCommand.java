package com.diamend.darksea.command;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.diag.LogTail;
import com.diamend.darksea.diag.StartupReport;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.config.Messages;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.npc.MarketClock;
import com.diamend.darksea.npc.NpcRegistry;
import com.diamend.darksea.npc.NpcType;
import com.diamend.darksea.npc.ShopStock;
import com.diamend.darksea.world.cultist.NodeRegistry;
import com.diamend.darksea.zone.Zone;
import com.diamend.darksea.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /darksea (alias /ds) — every player and admin entry point. */
public final class DarkSeaCommand implements CommandExecutor, TabCompleter {

    private final DarkSeaPlugin plugin;

    public DarkSeaCommand(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "help" -> help(sender);
            case "status" -> status(sender);
            case "tp" -> tp(sender);
            case "boat" -> boat(sender, args);
            case "bounty" -> bounty(sender, args);
            case "relic" -> relic(sender, args);
            case "generate" -> {
                if (requireAdmin(sender)) {
                    int limit = Integer.MAX_VALUE;
                    if (args.length >= 2) {
                        try {
                            limit = Math.max(1, Integer.parseInt(args[1]));
                        } catch (NumberFormatException ex) {
                            plugin.messages().send(sender, "generate-bad-count", "value", args[1]);
                            return true;
                        }
                    }
                    plugin.placer().generate(sender, limit);
                }
            }
            case "reset" -> reset(sender, args);
            case "island" -> island(sender, args);
            case "give" -> give(sender, args);
            case "npc" -> npc(sender, args);
            case "shop" -> shop(sender);
            case "loot" -> loot(sender);
            case "diag" -> diag(sender, args);
            case "reload" -> {
                if (requireAdmin(sender)) {
                    plugin.reloadAll();
                    msg.send(sender, "reload-done");
                }
            }
            default -> msg.send(sender, "unknown-command");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Player commands
    // ------------------------------------------------------------------

    private void help(CommandSender sender) {
        Messages msg = plugin.messages();
        msg.sendBare(sender, "help-header");
        helpLine(sender, "/ds status", "your zone, protection and boat", "darksea.use");
        helpLine(sender, "/ds boat", "open the boat wheel (upgrade, repair, stow)", "darksea.use");
        helpLine(sender, "/ds boat upgrade", "consume a token to upgrade your boat", "darksea.use");
        helpLine(sender, "/ds bounty [player] [amount]", "list bounties, or spend Chronons to place one", "darksea.use");
        helpLine(sender, "/ds relic revive", "wake the held relic at the calm center (costs Chronons)", "darksea.use");
        helpLine(sender, "/ds tp", "teleport to the home island", "darksea.tp");
        helpLine(sender, "/ds generate [count]", "place islands (all rings, or just <count> at a time)", "darksea.admin");
        helpLine(sender, "/ds reset <soft|full>", "re-paste islands / regenerate the sea", "darksea.admin");
        helpLine(sender, "/ds island list [tier] | tp <id>", "inspect placed islands", "darksea.admin");
        helpLine(sender, "/ds give <armor|token> <n> [player]", "grant sea armor or tokens", "darksea.admin");
        helpLine(sender, "/ds give item <id> [count] [player]", "grant any registry item (weapons, relics, chronons...)", "darksea.admin");
        helpLine(sender, "/ds boat set <player> <level>", "set a player's boat level", "darksea.admin");
        helpLine(sender, "/ds boat damage [amount]", "test tool: hull-damage the boat you're in", "darksea.admin");
        helpLine(sender, "/ds shop", "edit shop prices and stock in game", "darksea.admin");
        helpLine(sender, "/ds loot", "add your own items to the chest loot pools", "darksea.admin");
        helpLine(sender, "/ds diag [warnings]", "what came up, what didn't, and what it warned about", "darksea.admin");
        helpLine(sender, "/ds reload", "reload configuration", "darksea.admin");
    }

    private void helpLine(CommandSender sender, String usage, String description, String permission) {
        if (sender.hasPermission(permission)) {
            plugin.messages().sendBare(sender, "help-line", "usage", usage, "description", description);
        }
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        Messages msg = plugin.messages();
        DarkSeaSettings settings = plugin.settings();
        msg.sendBare(player, "status-header");

        DarkSeaSettings.BoatLevel boatStats = plugin.boat().stats(plugin.boat().levelOf(player));
        int protection = plugin.protection().tierOf(player);
        msg.sendBare(player, "status-protection", "tier", String.valueOf(protection));
        msg.sendBare(player, "status-boat",
                "name", boatStats.name(),
                "level", String.valueOf(plugin.boat().levelOf(player)),
                "shield", String.valueOf(boatStats.shield()));

        if (!plugin.isDarkSea(player.getWorld())) {
            msg.sendBare(player, "status-not-in-world");
            return;
        }
        double dx = player.getLocation().getX() - settings.centerX();
        double dz = player.getLocation().getZ() - settings.centerZ();
        double distSq = dx * dx + dz * dz;
        Zone zone = plugin.zoneManager().zoneAt(distSq);
        msg.sendBare(player, "status-zone",
                "zone", zone.displayName(),
                "distance", String.valueOf(Math.round(Math.sqrt(distSq))));

        int shield = plugin.boat().shieldFor(player);
        int exposure = ZoneManager.exposure(zone.requiredTier(), protection, shield);
        if (exposure <= 0 || player.hasPermission("darksea.bypass")) {
            msg.sendBare(player, "status-safe");
        } else {
            msg.sendBare(player, "status-danger", "exposure", String.valueOf(exposure));
        }
    }

    // ------------------------------------------------------------------
    // Bounties: /ds bounty (list) | /ds bounty <player> <amount> (place)
    // ------------------------------------------------------------------

    private void bounty(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            bountyList(player);
            return;
        }
        if (args.length < 3) {
            msg.send(player, "bounty-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(player, "bounty-no-target", "name", args[1]);
            return;
        }
        if (target.equals(player)) {
            msg.send(player, "bounty-self");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            msg.send(player, "bounty-bad-amount", "value", args[2]);
            return;
        }
        if (amount <= 0) {
            msg.send(player, "bounty-bad-amount", "value", args[2]);
            return;
        }
        if (!plugin.bounty().place(player, target, amount)) {
            msg.send(player, "bounty-need-chronons",
                    "cost", String.valueOf(amount),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
        }
    }

    private void bountyList(Player player) {
        Messages msg = plugin.messages();
        List<Map.Entry<UUID, Integer>> top = plugin.bounty().top(10);
        if (top.isEmpty()) {
            msg.send(player, "bounty-none");
            return;
        }
        msg.sendBare(player, "bounty-list-header");
        for (Map.Entry<UUID, Integer> entry : top) {
            OfflinePlayer head = Bukkit.getOfflinePlayer(entry.getKey());
            String name = head.getName() != null
                    ? head.getName() : entry.getKey().toString().substring(0, 8);
            msg.sendBare(player, "bounty-list-entry",
                    "name", name, "amount", String.valueOf(entry.getValue()));
        }
    }

    private void tp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("darksea.tp")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        World world = plugin.worldService().world();
        if (world == null) {
            plugin.messages().send(sender, "world-missing");
            return;
        }
        player.teleport(world.getSpawnLocation());
        plugin.messages().send(player, "tp-done");
    }

    private void boat(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.isEmpty() || action.equals("menu")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "players-only");
                return;
            }
            Boat boat = plugin.boatMenu().targetBoat(player);
            if (boat == null) {
                msg.send(player, "boat-menu-none");
                return;
            }
            plugin.boatMenu().open(player, boat);
            return;
        }
        if (action.equals("upgrade")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "players-only");
                return;
            }
            plugin.boat().upgrade(player);
            return;
        }
        if (action.equals("damage")) {
            boatDamage(sender, args);
            return;
        }
        if (action.equals("set")) {
            if (!requireAdmin(sender)) {
                return;
            }
            if (args.length < 4) {
                msg.send(sender, "unknown-command");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                msg.send(sender, "player-not-found", "player", args[2]);
                return;
            }
            Integer level = parseInt(sender, args[3]);
            if (level == null) {
                return;
            }
            if (!plugin.settings().boat().levels().containsKey(level)) {
                msg.send(sender, "invalid-level", "level", String.valueOf(level));
                return;
            }
            plugin.boat().setLevel(target, level);
            msg.send(sender, "boat-set",
                    "player", target.getName(),
                    "level", String.valueOf(level),
                    "name", plugin.boat().stats(level).name());
            return;
        }
        msg.send(sender, "unknown-command");
    }

    /**
     * /ds boat damage [amount] — admin-only test tool: deal naval hull damage
     * to the boat you're sitting in. Lets a single player watch the per-missing-HP
     * speed tax, the wounded HUD, regen, and the sink → wreck loop without a
     * second player charging them. Defaults to 1 HP a tap.
     */
    private void boatDamage(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg.send(sender, "players-only");
            return;
        }
        if (plugin.naval() == null || !(player.getVehicle() instanceof Boat boat)
                || !plugin.isDarkSea(boat.getWorld())) {
            msg.send(player, "boat-debug-none");
            return;
        }
        double amount = 1.0;
        if (args.length > 2) {
            try {
                amount = Math.max(0.1, Double.parseDouble(args[2]));
            } catch (NumberFormatException ex) {
                msg.send(sender, "invalid-number", "value", args[2]);
                return;
            }
        }
        plugin.naval().damageHull(boat, amount, null);
        if (!boat.isValid()) {
            msg.send(player, "boat-debug-sunk");
            return;
        }
        double hp = plugin.naval().hullHp(boat);
        msg.send(player, "boat-debug-damaged",
                "amount", trim(amount),
                "hp", trim(hp),
                "max", trim(plugin.naval().maxHp(boat)));
    }

    /** Formats a double without a trailing ".0" for whole numbers. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private void relic(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("revive")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "players-only");
                return;
            }
            plugin.relics().revive(player);
            return;
        }
        msg.send(sender, "unknown-command");
    }

    // ------------------------------------------------------------------
    // Admin commands
    // ------------------------------------------------------------------

    private void reset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Messages msg = plugin.messages();
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "soft" -> plugin.worldService().resetSoft(sender);
            case "full" -> {
                boolean confirmed = args.length > 2 && args[2].equalsIgnoreCase("confirm");
                if (!confirmed) {
                    msg.send(sender, "reset-full-warning");
                    return;
                }
                plugin.worldService().resetFull(sender);
            }
            default -> msg.send(sender, "unknown-command");
        }
    }

    private void island(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Messages msg = plugin.messages();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            List<IslandInstance> islands;
            if (args.length > 2) {
                Integer tier = parseInt(sender, args[2]);
                if (tier == null) {
                    return;
                }
                islands = plugin.registry().byTier(tier);
            } else {
                islands = plugin.registry().all();
            }
            msg.sendBare(sender, "island-list-header", "count", String.valueOf(islands.size()));
            for (IslandInstance island : islands) {
                msg.sendBare(sender, "island-list-entry",
                        "id", island.id(),
                        "template", island.template(),
                        "tier", String.valueOf(island.tier()),
                        "x", String.valueOf(island.origin().x()),
                        "z", String.valueOf(island.origin().z()));
            }
            return;
        }
        if (action.equals("tp")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "players-only");
                return;
            }
            if (args.length < 3) {
                msg.send(sender, "unknown-command");
                return;
            }
            IslandInstance island = plugin.registry().get(args[2]);
            if (island == null) {
                msg.send(sender, "island-not-found", "id", args[2]);
                return;
            }
            World world = plugin.worldService().world();
            if (world == null) {
                msg.send(sender, "world-missing");
                return;
            }
            int y = world.getHighestBlockYAt(island.origin().x(), island.origin().z()) + 1;
            player.teleport(new Location(world, island.origin().x() + 0.5, y, island.origin().z() + 0.5));
            msg.send(player, "island-tp", "id", island.id());
            return;
        }
        msg.send(sender, "unknown-command");
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Messages msg = plugin.messages();
        if (args.length < 3) {
            msg.send(sender, "unknown-command");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        if (kind.equals("item")) {
            giveItem(sender, args);
            return;
        }
        Integer number = parseInt(sender, args[2]);
        if (number == null) {
            return;
        }
        Player target;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                msg.send(sender, "player-not-found", "player", args[3]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            msg.send(sender, "players-only");
            return;
        }

        switch (kind) {
            case "armor" -> {
                DarkSeaSettings.ArmorStyle style = plugin.settings().armor().tiers().get(number);
                if (style == null) {
                    msg.send(sender, "invalid-tier", "tier", String.valueOf(number));
                    return;
                }
                List<ItemStack> set = SeaArmor.createSet(plugin.settings().armor(), number);
                giveItems(target, set);
                msg.send(sender, "give-armor",
                        "player", target.getName(),
                        "tier", String.valueOf(number),
                        "name", style.displayName());
            }
            case "token" -> {
                if (!plugin.settings().boat().levels().containsKey(number)) {
                    msg.send(sender, "invalid-level", "level", String.valueOf(number));
                    return;
                }
                giveItems(target, List.of(SeaArmor.createToken(number)));
                msg.send(sender, "give-token",
                        "player", target.getName(),
                        "level", String.valueOf(number));
            }
            default -> msg.send(sender, "unknown-command");
        }
    }

    /** /ds give item <id> [count] [player] — any DarkSeaItems registry id. */
    private void giveItem(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!DarkSeaItems.allIds().contains(id)) {
            msg.send(sender, "invalid-item", "id", id);
            return;
        }
        int count = 1;
        int playerArg = 3;
        if (args.length > 3) {
            try {
                count = Math.max(1, Integer.parseInt(args[3]));
                playerArg = 4;
            } catch (NumberFormatException ex) {
                // args[3] is the player name, count stays 1.
            }
        }
        Player target;
        if (args.length > playerArg) {
            target = Bukkit.getPlayerExact(args[playerArg]);
            if (target == null) {
                msg.send(sender, "player-not-found", "player", args[playerArg]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            msg.send(sender, "players-only");
            return;
        }
        // Stackables (Chronons) go out as one stack of `count`; everything
        // else as `count` single items.
        ItemStack sample = DarkSeaItems.create(id, count);
        if (sample.getMaxStackSize() > 1) {
            giveItems(target, List.of(sample));
        } else {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(DarkSeaItems.create(id, 1));
            }
            giveItems(target, items);
        }
        msg.send(sender, "give-item", "player", target.getName(),
                "id", id, "count", String.valueOf(count));
    }

    private void giveItems(Player target, List<ItemStack> items) {
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack item : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // /ds npc — placing the outpost's people
    // ------------------------------------------------------------------

    /**
     * Placement is a command rather than schematic markers on purpose: the
     * home island is hand-built and can be re-laid out any number of times,
     * and an admin standing in the right doorway is a more reliable anchor
     * than a marker block someone has to remember to place.
     */
    private void npc(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "create" -> npcCreate(sender, args);
            case "remove" -> npcRemove(sender, args);
            case "respawn" -> {
                plugin.npcs().despawnAll();
                int spawned = plugin.npcs().spawnAll();
                plugin.messages().send(sender, "npc-respawned",
                        "count", String.valueOf(spawned));
            }
            default -> npcList(sender);
        }
    }

    private void npcCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "npc-usage");
            return;
        }
        NpcType type = NpcType.byId(args[2]);
        if (type == null) {
            plugin.messages().send(sender, "npc-unknown-type", "type", args[2]);
            return;
        }
        NpcRegistry.Placement placement = plugin.npcs().create(player, type);
        plugin.messages().send(sender, "npc-created",
                "id", placement.id(), "type", type.id());
    }

    private void npcRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "npc-usage");
            return;
        }
        NpcRegistry.Placement removed = plugin.npcs().remove(args[2]);
        if (removed == null) {
            plugin.messages().send(sender, "npc-unknown-id", "id", args[2]);
            return;
        }
        plugin.messages().send(sender, "npc-removed", "id", removed.id());
    }

    private void npcList(CommandSender sender) {
        List<NpcRegistry.Placement> all = plugin.npcs().registry().all();
        plugin.messages().send(sender, "npc-list-header",
                "count", String.valueOf(all.size()));
        for (NpcRegistry.Placement placement : all) {
            plugin.messages().sendBare(sender, "npc-list-entry",
                    "id", placement.id(),
                    "type", placement.type().id(),
                    "world", placement.world(),
                    "x", String.valueOf((int) placement.x()),
                    "y", String.valueOf((int) placement.y()),
                    "z", String.valueOf((int) placement.z()));
        }
    }

    /**
     * /ds shop — the in-game price editor. Everything it can change lives in
     * shops.yml, so this is a convenience rather than a second source of
     * truth: hand-editing the file and reloading does exactly the same thing.
     */
    /**
     * /ds diag — the console log for someone who does not have the console.
     *
     * <p>Startup runs every step behind a catch (see {@code DarkSeaPlugin}),
     * which means the plugin can be up and a feature can be silently missing.
     * This is where that shows: which steps failed and why, then the live
     * counts worth eyeballing on a first boot — did the worlds create, did the
     * islands place, did the NPCs come back, did the crystal veins get built —
     * and finally the warnings the config loaders swallowed.
     */
    private void diag(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Messages msg = plugin.messages();
        LogTail tail = plugin.logTail();

        if (args.length >= 2 && args[1].equalsIgnoreCase("warnings")) {
            msg.sendBare(sender, "diag-warn-header",
                    "warnings", String.valueOf(tail.warningCount()),
                    "severes", String.valueOf(tail.severeCount()));
            List<LogTail.Entry> entries = tail.entries();
            if (entries.isEmpty()) {
                msg.sendBare(sender, "diag-none");
                return;
            }
            for (LogTail.Entry entry : entries) {
                msg.sendBare(sender, entry.severe() ? "diag-severe" : "diag-warning",
                        "message", entry.message());
            }
            return;
        }

        StartupReport report = plugin.startup();
        msg.sendBare(sender, "diag-header");
        msg.sendBare(sender, report.healthy() ? "diag-startup-ok" : "diag-startup-degraded",
                "summary", report.summary());
        for (StartupReport.Step failure : report.failures()) {
            msg.sendBare(sender, "diag-step-failed",
                    "step", failure.name(), "detail", failure.detail());
        }

        // Worlds. "missing" here means the step that creates it failed or the
        // server unloaded it since — either way nothing in that world works.
        diagLine(sender, "sea world", worldState(plugin.settings().worldName()));
        diagLine(sender, "caves world", worldState(plugin.settings().cultist().worldName()));

        diagLine(sender, "islands placed", String.valueOf(plugin.registry().all().size()));
        // Zero is not a neutral number here, which is why it says so. NPCs are
        // only ever placed by hand, and the artificer is the only way to wake a
        // relic — so a sea with none has an entire economy that silently does
        // not exist, and nothing else in this readout looks wrong. Two live
        // tests ran against a world with no NPCs in it at all.
        int npcs = plugin.npcs().registry().all().size();
        diagLine(sender, "npc placements", npcs == 0
                ? "0 — no shops or artificer exist; place them with /ds npc create <type>"
                : String.valueOf(npcs));

        // Crystal veins: total, and how many are sitting spent waiting to
        // regrow. All-spent on a fresh boot means the regrow timer is not
        // running, which is a failed step above rather than a tuning problem.
        List<NodeRegistry.Node> nodes = plugin.nodes().registry().all();
        long spent = nodes.stream().filter(node -> node.firstMined() > 0L).count();
        diagLine(sender, "crystal veins", nodes.size() + " placed, " + spent + " regrowing"
                + " (" + plugin.oreTables().totalVeins() + " configured)");

        // Shops, plus the black market's own clock — the one number that is
        // wrong in an obvious way if wall-time handling is off.
        ShopStock stock = plugin.shopStock();
        long interval = MarketClock.intervalMillis(stock.rotationHours());
        diagLine(sender, "shop lines", stock.lineCount() + " fixed, "
                + stock.rotatingPool().size() + " in the market pool");
        diagLine(sender, "next rotation",
                MarketClock.format(MarketClock.millisUntilNext(System.currentTimeMillis(), interval)));

        // MythicMobs, and — more usefully — whether it actually knows the
        // roster. A pack in the wrong folder is completely silent: every mob
        // quietly spawns its vanilla fallback, so the sea fills with husks
        // and nothing is ever logged. This is the line that says so.
        diagLine(sender, "mythicmobs", plugin.mobSpawner().mythicReport());

        diagLine(sender, "warnings logged", tail.warningCount() + " warning, "
                + tail.severeCount() + " severe");
        if (!tail.clean()) {
            msg.sendBare(sender, "diag-warn-hint");
        }
    }

    private void diagLine(CommandSender sender, String label, String value) {
        plugin.messages().sendBare(sender, "diag-line", "label", label, "value", value);
    }

    /** Loaded / missing, with the world's configured name, for the diag list. */
    private String worldState(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            return name + " (MISSING)";
        }
        return name + " (" + world.getPlayers().size() + " players)";
    }

    private void shop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.shopEditor().openPicker(player);
    }

    /**
     * /ds loot — the in-game loot-pool editor. Unlike {@code /ds shop} this is
     * not merely a convenience for a file: it is the only practical way to put
     * an item the plugin has no id for into a chest, because doing it by hand
     * means pasting a base64 stack image into YAML.
     */
    private void loot(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.lootEditor().openPicker(player);
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("darksea.admin")) {
            plugin.messages().send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "invalid-number", "value", raw);
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("darksea.admin");
        if (args.length == 1) {
            options.add("help");
            options.add("status");
            options.add("boat");
            options.add("bounty");
            options.add("relic");
            if (sender.hasPermission("darksea.tp")) {
                options.add("tp");
            }
            if (admin) {
                options.add("generate");
                options.add("reset");
                options.add("island");
                options.add("give");
                options.add("npc");
                options.add("shop");
                options.add("loot");
                options.add("diag");
                options.add("reload");
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "boat" -> {
                    options.add("menu");
                    options.add("upgrade");
                    if (admin) {
                        options.add("set");
                        options.add("damage");
                    }
                }
                case "relic" -> options.add("revive");
                case "diag" -> {
                    if (admin) {
                        options.add("warnings");
                    }
                }
                case "bounty" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        options.add(online.getName());
                    }
                }
                case "reset" -> {
                    if (admin) {
                        options.add("soft");
                        options.add("full");
                    }
                }
                case "island" -> {
                    if (admin) {
                        options.add("list");
                        options.add("tp");
                    }
                }
                case "give" -> {
                    if (admin) {
                        options.add("armor");
                        options.add("token");
                        options.add("item");
                    }
                }
                case "npc" -> {
                    if (admin) {
                        options.add("create");
                        options.add("remove");
                        options.add("list");
                        options.add("respawn");
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reset" -> {
                    if (admin && args[1].equalsIgnoreCase("full")) {
                        options.add("confirm");
                    }
                }
                case "island" -> {
                    if (admin && args[1].equalsIgnoreCase("tp")) {
                        for (IslandInstance island : plugin.registry().all()) {
                            options.add(island.id());
                        }
                    }
                }
                case "npc" -> {
                    if (admin && args[1].equalsIgnoreCase("create")) {
                        for (NpcType type : NpcType.values()) {
                            options.add(type.id());
                        }
                    } else if (admin && args[1].equalsIgnoreCase("remove")) {
                        for (NpcRegistry.Placement placement : plugin.npcs().registry().all()) {
                            options.add(placement.id());
                        }
                    }
                }
                case "give" -> {
                    if (admin && args[1].equalsIgnoreCase("armor")) {
                        for (Integer tier : plugin.settings().armor().tiers().keySet()) {
                            options.add(String.valueOf(tier));
                        }
                    } else if (admin && args[1].equalsIgnoreCase("token")) {
                        for (Integer level : plugin.settings().boat().levels().keySet()) {
                            if (level > 0) {
                                options.add(String.valueOf(level));
                            }
                        }
                    } else if (admin && args[1].equalsIgnoreCase("item")) {
                        options.addAll(DarkSeaItems.allIds());
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
