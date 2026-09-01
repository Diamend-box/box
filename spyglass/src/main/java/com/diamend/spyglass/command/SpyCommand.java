package com.diamend.spyglass.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.diamend.spyglass.SpyglassPlugin;
import com.diamend.spyglass.config.SpyglassConfig;
import com.diamend.spyglass.inspect.ItemFormatter;
import com.diamend.spyglass.inspect.Query;
import com.diamend.spyglass.inspect.Targets;
import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtPath;
import com.diamend.spyglass.nbt.NbtPrinter;
import com.diamend.spyglass.nbt.NbtTag;
import com.diamend.spyglass.offline.OfflineSearch;
import com.diamend.spyglass.offline.OfflineSnapshot;
import com.diamend.spyglass.report.DumpFile;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.ReportDiff;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;
import com.diamend.spyglass.watch.Watch;
import com.diamend.spyglass.watch.WatchCategory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@code /spy} — the whole plugin's front door, written for a console first.
 *
 * <p>Output is plain aligned text rather than a menu, because the place this is
 * meant to be used has no mouse.
 */
public final class SpyCommand implements TabExecutor {

    private static final String USE = "spyglass.use";
    private static final String WATCH = "spyglass.watch";
    private static final String SENSITIVE = "spyglass.sensitive";
    private static final String ADMIN = "spyglass.admin";
    private static final String EXEMPT = "spyglass.exempt";

    /** How deep and how wide a raw NBT dump goes before it stops. */
    private static final int NBT_DEPTH = 12;
    private static final int NBT_ELEMENTS = 96;

    /** Enough to answer "who has one of these"; not enough to fill a terminal. */
    private static final int MAX_FIND_HITS = 200;

    /** A completion list longer than this is not a list, it is a wall. */
    private static final int MAX_COMPLETIONS = 100;

    private static final List<String> VERBS = List.of("list", "watch", "unwatch", "watching",
            "dump", "dumps", "diff", "find", "sections", "reload", "help");

    private final SpyglassPlugin plugin;

    public SpyCommand(SpyglassPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(USE)) {
            error(sender, "You don't have permission to use /spy.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> help(sender);
            case "sections" -> sections(sender);
            case "list" -> list(sender, args);
            case "watch" -> watch(sender, args);
            case "unwatch" -> unwatch(sender, args);
            case "watching" -> watching(sender);
            case "dump" -> dump(sender, args);
            case "dumps" -> dumps(sender, args);
            case "diff" -> diff(sender, args);
            case "find" -> find(sender, args);
            case "reload" -> reload(sender);
            default -> inspect(sender, args);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Inspecting
    // ------------------------------------------------------------------

    private void inspect(CommandSender sender, String[] args) {
        String name = args[0];
        Section section = Section.OVERVIEW;
        if (args.length > 1) {
            section = Section.byName(args[1]);
            if (section == null) {
                error(sender, "No section called \"" + args[1] + "\". Try /spy sections.");
                return;
            }
        }
        // Everything after the section is a filter, a slot, or a path — plus an
        // optional trailing page number.
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(Math.min(2, args.length), args.length));
        int page = 1;
        if (section != Section.ITEM && !rest.isEmpty()) {
            Integer trailing = asInt(rest.get(rest.size() - 1));
            if (trailing != null && trailing > 0) {
                page = trailing;
                rest.remove(rest.size() - 1);
            }
        }
        String argument = rest.isEmpty() ? null : String.join(" ", rest);
        Query query = new Query(argument, canSeeSensitive(sender));

        Targets.Target target = resolve(name);
        if (target == null) {
            error(sender, "No player called \"" + name + "\" is online, and none has ever played here.");
            return;
        }
        if (!allowedToInspect(sender, target)) {
            return;
        }
        logUsage(sender, target, section);

        if (section == Section.NBT) {
            rawNbt(sender, target, argument, page);
            return;
        }
        if (target.isOnline()) {
            Report report = titled(target, section)
                    .append(plugin.online().section(target.online(), section, query));
            report.send(sender, page, plugin.settings().pageSize(), hint(target, section, argument));
            return;
        }
        Section wanted = section;
        int wantedPage = page;
        plugin.async(() -> {
            OfflineSnapshot snapshot = OfflineSnapshot.load(
                    plugin.files(), target.offline(), target.uuid(), target.name());
            Report report = titled(target, wanted)
                    .append(plugin.offline().section(snapshot, wanted, query));
            plugin.sync(() -> report.send(sender, wantedPage, plugin.settings().pageSize(),
                    hint(target, wanted, argument)));
        });
    }

    /**
     * The raw save tree. For a player who is online the file on disk is as old
     * as the last autosave, so we ask the server to write them out first.
     */
    private void rawNbt(CommandSender sender, Targets.Target target, String path, int page) {
        boolean saveFirst = plugin.settings().saveBeforeNbt() && target.isOnline();
        if (saveFirst) {
            Safe.run(() -> target.online().saveData());
        }
        boolean saved = saveFirst;
        plugin.async(() -> {
            OfflineSnapshot snapshot = OfflineSnapshot.load(
                    plugin.files(), target.offline(), target.uuid(), target.name());
            Report report = new Report().title(target.label() + " — nbt"
                    + (path == null ? "" : " " + path));
            if (!snapshot.hasData()) {
                report.note("No save data: " + snapshot.error());
                plugin.sync(() -> report.send(sender));
                return;
            }
            report.field("file", snapshot.dataFile().getPath());
            report.field("written", Fmt.stampWithAge(snapshot.savedAt())
                    + (saved ? " (saved just now for this read)" : ""));
            NbtCompound root = snapshot.data();
            NbtTag tag = path == null || path.isBlank() ? root.asTag() : NbtPath.resolve(root, path);
            if (tag == null) {
                report.note("Nothing at \"" + path + "\". Top level: "
                        + String.join(", ", root.keys()));
                plugin.sync(() -> report.send(sender));
                return;
            }
            report.blank();
            NbtPrinter printer = new NbtPrinter(NBT_DEPTH, NBT_ELEMENTS);
            for (String line : printer.print(path == null ? "" : path, tag)) {
                report.text(line);
            }
            plugin.sync(() -> report.send(sender, page, plugin.settings().pageSize(),
                    "/spy " + target.name() + " nbt" + (path == null ? "" : " " + path)));
        });
    }

    // ------------------------------------------------------------------
    // Everyone at once
    // ------------------------------------------------------------------

    private void list(CommandSender sender, String[] args) {
        String world = args.length > 1 ? args[1] : null;
        Report report = new Report().title("Online players");
        int shown = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String playerWorld = Safe.text(() -> player.getWorld().getName());
            if (world != null && !playerWorld.equalsIgnoreCase(world)) {
                continue;
            }
            shown++;
            report.text(String.format("%-16s %-10s %-18s %5s hp  %-10s %4s ms",
                    Safe.text(player::getName),
                    Fmt.clip(playerWorld, 10),
                    Safe.text(() -> {
                        Location location = player.getLocation();
                        return location.getBlockX() + " " + location.getBlockY()
                                + " " + location.getBlockZ();
                    }),
                    Fmt.num(Safe.number(player::getHealth, 0D)),
                    Safe.text(player::getGameMode),
                    Safe.integer(player::getPing, -1)));
        }
        if (shown == 0) {
            report.note(world == null ? "Nobody is online." : "Nobody is in world \"" + world + "\".");
        } else {
            report.note(shown + " player(s). Inspect one with /spy <name>.");
        }
        report.send(sender, 1, plugin.settings().pageSize(), null);
    }

    private void find(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /spy find <item> [player|all|saves]");
            return;
        }
        String wanted = args[1].toLowerCase(Locale.ROOT);
        String who = args.length > 2 ? args[2] : "all";
        if (who.equalsIgnoreCase("saves") || who.equalsIgnoreCase("offline")) {
            findInSaves(sender, wanted);
            return;
        }
        Report report = new Report().title("Searching for \"" + wanted + "\"");
        int hits = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!who.equalsIgnoreCase("all") && !player.getName().equalsIgnoreCase(who)) {
                continue;
            }
            if (isExempt(sender, player)) {
                continue;
            }
            hits += findIn(report, player, wanted);
        }
        if (hits == 0) {
            report.note("Nothing matching \"" + wanted + "\" in any online player's inventory, "
                    + "ender chest, or anything they are carrying it inside. "
                    + "Try /spy find " + wanted + " saves for everyone else.");
        } else {
            report.note(hits + " stack(s) found among the players online. "
                    + "Search the save files too with /spy find " + wanted + " saves.");
        }
        report.send(sender, 1, plugin.settings().pageSize(), null);
    }

    private int findIn(Report report, Player player, String wanted) {
        int hits = 0;
        hits += findIn(report, player, wanted, "inventory",
                Safe.call(() -> player.getInventory().getContents(), new ItemStack[0]));
        hits += findIn(report, player, wanted, "enderchest",
                Safe.call(() -> player.getEnderChest().getContents(), new ItemStack[0]));
        return hits;
    }

    private int findIn(Report report, Player player, String wanted, String where, ItemStack[] contents) {
        int hits = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (ItemFormatter.isEmpty(item)) {
                continue;
            }
            // Not just the stack itself: a shulker box in slot 13 is where the
            // thing you are looking for usually is.
            String trail = ItemFormatter.matchTrail(item, wanted);
            if (trail == null) {
                continue;
            }
            hits++;
            report.text(String.format("%-16s %-10s slot %-4d %s",
                    Safe.text(player::getName), where, slot, ItemFormatter.line(item) + trail));
        }
        return hits;
    }

    /**
     * The same search, over every save on the disk rather than over the handful
     * of people who happen to be connected.
     *
     * <p>This is the one command here that can be genuinely expensive, so it is
     * bounded by {@code find.max-saves} and {@code find.time-budget} and says
     * plainly when it stopped early. What it reads is cached against each file's
     * timestamp, so asking again costs nothing.
     */
    private void findInSaves(CommandSender sender, String wanted) {
        // Gathered here, on the main thread, because it is the only place the
        // permissions of the people currently online can be asked about.
        Set<UUID> hidden = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isExempt(sender, player)) {
                hidden.add(player.getUniqueId());
            }
        }
        int maxSaves = plugin.settings().findMaxSaves();
        long budget = plugin.settings().findSeconds() * 1000L;
        info(sender, "Reading save files for \"" + wanted + "\"...");
        plugin.async(() -> {
            OfflineSearch.Result result =
                    plugin.search().search(wanted, maxSaves, budget, MAX_FIND_HITS);
            Report report = new Report().title("Searching saves for \"" + wanted + "\"");
            int shown = 0;
            for (OfflineSearch.Hit hit : result.hits()) {
                if (hidden.contains(hit.uuid())) {
                    continue;
                }
                shown++;
                report.text(String.format("%-16s %-10s slot %-4s %s",
                        Fmt.clip(hit.name(), 16), hit.where(), slotText(hit.slot()), hit.line()));
            }
            report.note(shown == 0
                    ? "Nothing matching \"" + wanted + "\" in " + result.scanned() + " save(s)."
                    : shown + " stack(s) across " + result.scanned() + " save(s) of "
                            + result.total() + ".");
            if (result.failed() > 0) {
                report.note(result.failed() + " save(s) could not be read — being written as we "
                        + "looked, or written by a version this cannot parse.");
            }
            if (!result.complete()) {
                report.note("Incomplete: " + result.stopped() + ".");
            }
            report.note("These are the files on disk, so an online player's row is as old as "
                    + "their last save; /spy find " + wanted + " reads those live.");
            plugin.sync(() -> report.send(sender, 1, plugin.settings().pageSize(), null));
        });
    }

    private static String slotText(int slot) {
        return slot == Integer.MIN_VALUE ? "?" : String.valueOf(slot);
    }

    // ------------------------------------------------------------------
    // Dumping
    // ------------------------------------------------------------------

    private void dump(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /spy dump <player>");
            return;
        }
        Targets.Target target = resolveOrComplain(sender, args[1]);
        if (target == null || !allowedToInspect(sender, target)) {
            return;
        }
        Report live = livePart(sender, target);
        info(sender, "Building a full report on " + target.name() + "...");
        plugin.async(() -> {
            Report report = fullReport(sender, target, live);
            try {
                File written = plugin.dumps().write(target.name(), target.uuid().toString(), report);
                plugin.sync(() -> info(sender, "Wrote " + report.size() + " lines to "
                        + written.getPath() + " (and the same again as .json)"));
            } catch (Exception ex) {
                plugin.sync(() -> error(sender, "Could not write the dump: " + ex.getMessage()));
            }
        });
    }

    /**
     * The half of a full report that must be read from the live server object,
     * and so has to happen on the main thread. Null when they are not on.
     */
    private Report livePart(CommandSender sender, Targets.Target target) {
        if (!target.isOnline()) {
            return null;
        }
        Report live = plugin.online().section(
                target.online(), Section.ALL, new Query(null, canSeeSensitive(sender)));
        if (plugin.settings().saveBeforeNbt()) {
            Safe.run(() -> target.online().saveData());
        }
        return live;
    }

    /** Everything there is on one player, in one report. Call it off the main thread. */
    private Report fullReport(CommandSender sender, Targets.Target target, Report live) {
        Query query = new Query(null, canSeeSensitive(sender));
        Report report = new Report().title(target.label() + " — full report");
        report.field("generated", Fmt.stamp(System.currentTimeMillis()));
        report.field("uuid", target.uuid());
        OfflineSnapshot snapshot = OfflineSnapshot.load(
                plugin.files(), target.offline(), target.uuid(), target.name());
        if (live != null) {
            report.append(live);
            report.header("Save file");
            report.field("file", snapshot.dataFile() == null
                    ? Safe.UNKNOWN : snapshot.dataFile().getPath());
            report.field("written", Fmt.stampWithAge(snapshot.savedAt()));
        } else {
            report.append(plugin.offline().section(snapshot, Section.ALL, query));
        }
        if (snapshot.hasData()) {
            report.header("Raw NBT");
            NbtPrinter printer = new NbtPrinter(NBT_DEPTH, NBT_ELEMENTS);
            for (String line : printer.print("", snapshot.data().asTag())) {
                report.text(line);
            }
        }
        return report;
    }

    private void dumps(CommandSender sender, String[] args) {
        String player = args.length > 1 ? args[1] : null;
        List<File> files = plugin.dumps().list(player);
        Report report = new Report().title(player == null ? "Dumps" : "Dumps of " + player);
        report.field("folder", plugin.dumps().folder().getPath());
        if (files.isEmpty()) {
            report.note(player == null
                    ? "No dumps yet. Write one with /spy dump <player>."
                    : "No dumps of " + player + " yet.");
        } else {
            for (File file : files) {
                report.text(String.format("%-44s %s", file.getName(),
                        Fmt.stampWithAge(file.lastModified())));
            }
            report.note("Compare one with /spy diff <player> [file].");
        }
        report.send(sender, 1, plugin.settings().pageSize(), null);
    }

    /**
     * What changed since a dump. Builds the player's state now, compares it
     * against the dump named (or the newest one there is), and prints only the
     * differences.
     */
    private void diff(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /spy diff <player> [dump-file] [all]");
            return;
        }
        Targets.Target target = resolveOrComplain(sender, args[1]);
        if (target == null || !allowedToInspect(sender, target)) {
            return;
        }
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(Math.min(2, args.length), args.length));
        boolean all = rest.removeIf(word -> word.equalsIgnoreCase("all"));
        String wanted = rest.isEmpty() ? null : rest.get(0);

        File dump = wanted == null ? plugin.dumps().latest(target.name()) : plugin.dumps().resolve(wanted);
        if (dump == null) {
            error(sender, wanted == null
                    ? "No dump of " + target.name() + " to compare against. Write one with /spy dump "
                            + target.name() + "."
                    : "No dump called \"" + wanted + "\" in " + plugin.dumps().folder().getPath()
                            + ". List them with /spy dumps.");
            return;
        }
        Report live = livePart(sender, target);
        info(sender, "Comparing " + target.name() + " against " + dump.getName() + "...");
        plugin.async(() -> {
            Report now = fullReport(sender, target, live);
            Report report;
            try {
                DumpFile before = DumpFile.read(dump.toPath());
                report = ReportDiff.between(before, dump.getName(),
                        DumpFile.of(target.name(), target.uuid().toString(), now), "now", all);
            } catch (Exception ex) {
                report = new Report().title(target.label() + " — diff")
                        .note("Could not read " + dump.getName() + ": " + ex.getMessage());
            }
            Report finished = report;
            plugin.sync(() -> finished.send(sender, 1, plugin.settings().pageSize(), null));
        });
    }

    /**
     * Works out who a name or UUID means.
     *
     * <p>Falls back to the server's own {@code usercache.json} when Bukkit will
     * not enumerate them, so a name tab completion offered is a name the command
     * accepts — the two read the same list.
     */
    private Targets.Target resolve(String name) {
        Targets.Target target = Targets.resolve(plugin.getServer(), name);
        if (target != null) {
            return target;
        }
        UUID cached = plugin.names().uuid(name);
        if (cached == null) {
            return null;
        }
        Targets.Target found = Targets.resolve(plugin.getServer(), cached.toString());
        // The cache knew the name; the server, by definition, did not.
        return found == null ? null : found.named(plugin.names().name(cached));
    }

    /** The same, telling the sender when nobody answers to it. */
    private Targets.Target resolveOrComplain(CommandSender sender, String name) {
        Targets.Target target = resolve(name);
        if (target == null) {
            error(sender, "No player called \"" + name + "\" is online, and none has ever played here.");
        }
        return target;
    }

    // ------------------------------------------------------------------
    // Watching
    // ------------------------------------------------------------------

    private void watch(CommandSender sender, String[] args) {
        if (!sender.hasPermission(WATCH)) {
            error(sender, "You don't have permission to watch players.");
            return;
        }
        if (args.length < 2) {
            error(sender, "Usage: /spy watch <player> [" + String.join("|", WatchCategory.names()) + "]");
            return;
        }
        Targets.Target target = resolve(args[1]);
        String name = target == null ? args[1] : target.name();
        if (target != null && !allowedToInspect(sender, target)) {
            return;
        }
        Set<WatchCategory> categories = args.length > 2
                ? WatchCategory.parse(Arrays.asList(args).subList(2, args.length))
                : plugin.settings().defaultCategories();
        if (categories.isEmpty()) {
            error(sender, "No categories in that list. Choose from: "
                    + String.join(", ", WatchCategory.names()));
            return;
        }
        plugin.watches().add(sender, target == null ? null : target.uuid(), name, categories);
        info(sender, "Watching " + name + " (" + WatchCategory.describe(categories) + ").");
        if (target == null || !target.isOnline()) {
            info(sender, name + " is not online; the watch starts when they join.");
        }
    }

    private void unwatch(CommandSender sender, String[] args) {
        if (!sender.hasPermission(WATCH)) {
            error(sender, "You don't have permission to watch players.");
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
            int stopped = plugin.watches().removeAll(sender);
            info(sender, stopped == 0 ? "You weren't watching anybody."
                    : "Stopped " + stopped + " watch(es).");
            return;
        }
        Targets.Target target = resolve(args[1]);
        String name = target == null ? args[1] : target.name();
        boolean stopped = plugin.watches().remove(sender, target == null ? null : target.uuid(), name);
        info(sender, stopped ? "No longer watching " + name + "." : "You weren't watching " + name + ".");
    }

    private void watching(CommandSender sender) {
        List<Watch> watches = plugin.watches().watches();
        Report report = new Report().title("Active watches");
        if (watches.isEmpty()) {
            report.note("Nobody is being watched.");
        } else {
            for (Watch watch : watches) {
                report.text(String.format("%-16s watched by %-16s  %s  (since %s)",
                        watch.targetName(),
                        watch.isConsole() ? "console" : watch.watcherName(),
                        WatchCategory.describe(watch.categories()),
                        Fmt.duration(System.currentTimeMillis() - watch.since()) + " ago"));
            }
        }
        report.send(sender, 1, plugin.settings().pageSize(), null);
    }

    // ------------------------------------------------------------------
    // Odds and ends
    // ------------------------------------------------------------------

    private void sections(CommandSender sender) {
        Report report = new Report().title("Sections");
        for (Section section : Section.values()) {
            report.field(section.id() + (section.offline() ? "" : " *"), section.summary());
        }
        report.note("* needs the player to be online. Everything else can be read from their save file.");
        report.note("Usage: /spy <player> <section> [filter] [page]");
        report.send(sender, 1, plugin.settings().pageSize(), null);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            error(sender, "Reloading needs " + ADMIN + ".");
            return;
        }
        plugin.reloadSpyglass();
        SpyglassConfig settings = plugin.settings();
        info(sender, "Reloaded configuration. Page size " + settings.pageSize()
                + ", default watch categories " + WatchCategory.describe(settings.defaultCategories()) + ".");
    }

    private void help(CommandSender sender) {
        Report report = new Report().title("Spyglass");
        report.note("Read any player's data — online from the server, offline from their save file.");
        report.blank();
        report.field("/spy <player> [section]", "inspect; default section is overview");
        report.field("/spy <player> nbt [path]", "the raw save tree, whole or one branch");
        report.field("/spy <player> item <slot>", "everything about one item");
        report.field("/spy <player> stats <filter>", "filter any long section; add a page number");
        report.field("/spy sections", "what you can ask for");
        report.field("/spy list [world]", "everyone online at a glance");
        report.field("/spy find <item> [who]", "find an item; \"saves\" searches the whole disk");
        report.field("/spy watch <player> [cats]", "follow what they do, live");
        report.field("/spy unwatch <player|all>", "stop following");
        report.field("/spy watching", "who is being followed");
        report.field("/spy dump <player>", "write the whole report, raw NBT included, to a file");
        report.field("/spy dumps [player]", "the dumps on disk, newest first");
        report.field("/spy diff <player> [file]", "what changed since that dump; add \"all\"");
        report.field("/spy reload", "re-read config.yml");
        report.send(sender);
    }

    // ------------------------------------------------------------------
    // Guards and plumbing
    // ------------------------------------------------------------------

    /** The console always sees everything; a player needs the node. */
    private boolean canSeeSensitive(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return !plugin.settings().maskIp() || sender.hasPermission(SENSITIVE);
        }
        return sender.hasPermission(SENSITIVE) && !plugin.settings().maskIp();
    }

    /**
     * {@code spyglass.exempt} hides a player from other players' inspections —
     * never from the console, which is the server owner by definition.
     */
    private boolean isExempt(CommandSender sender, Player target) {
        if (sender instanceof ConsoleCommandSender || target == null) {
            return false;
        }
        return target.hasPermission(EXEMPT) && !sender.equals(target);
    }

    private boolean allowedToInspect(CommandSender sender, Targets.Target target) {
        if (target.isOnline() && isExempt(sender, target.online())) {
            error(sender, target.name() + " cannot be inspected.");
            return false;
        }
        return true;
    }

    private void logUsage(CommandSender sender, Targets.Target target, Section section) {
        if (plugin.settings().logUsage()) {
            plugin.getLogger().info(sender.getName() + " inspected " + target.name()
                    + " (" + section.id() + ")");
        }
    }

    private Report titled(Targets.Target target, Section section) {
        return new Report().title(target.label() + " — " + section.id());
    }

    private String hint(Targets.Target target, Section section, String argument) {
        return "/spy " + target.name() + " " + section.id()
                + (argument == null ? "" : " " + argument);
    }

    private static Integer asInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[Spyglass] ", NamedTextColor.AQUA)
                .append(Component.text(message, NamedTextColor.WHITE)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[Spyglass] ", NamedTextColor.AQUA)
                .append(Component.text(message, NamedTextColor.RED)));
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(USE)) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(VERBS);
            addPlayerNames(out, args[0]);
            return prefixed(out, args[0]);
        }
        String verb = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (verb) {
                case "watch", "dump", "dumps", "diff" -> addPlayerNames(out, args[1]);
                case "unwatch" -> {
                    out.add("all");
                    for (Watch watch : plugin.watches().watchesBy(sender)) {
                        out.add(watch.targetName());
                    }
                }
                case "list", "find", "watching", "sections", "reload", "help" -> {
                    // nothing useful to offer
                }
                default -> out.addAll(Section.names());
            }
            return prefixed(out, args[1]);
        }
        if (verb.equals("find") && args.length == 3) {
            out.add("all");
            out.add("saves");
            addPlayerNames(out, args[2]);
            return prefixed(out, args[2]);
        }
        if (verb.equals("watch")) {
            out.addAll(WatchCategory.names());
            return prefixed(out, args[args.length - 1]);
        }
        if (verb.equals("diff")) {
            // "all" is worth offering at either position; the dump to compare
            // against only makes sense in the first.
            out.add("all");
            if (args.length == 3) {
                for (File file : plugin.dumps().list(args[1])) {
                    out.add(file.getName());
                }
            }
            return prefixed(out, args[args.length - 1]);
        }
        if (args.length == 3 && Section.byName(args[1]) == Section.NBT) {
            // Offer the top-level tags of a save we have already read? Keep it
            // simple and suggest the ones every save has.
            out.addAll(List.of("Inventory", "EnderItems", "abilities", "attributes",
                    "active_effects", "recipeBook", "BukkitValues", "Pos", "bukkit", "Paper"));
            return prefixed(out, args[2]);
        }
        return out;
    }

    /**
     * Everyone online, then everyone this server remembers.
     *
     * <p>The point of the plugin is that a name does not have to be logged in to
     * be inspectable, so completion should not pretend otherwise. The offline
     * half comes from the cache the server already keeps, filtered here rather
     * than afterwards because there can be tens of thousands of them.
     */
    private void addPlayerNames(List<String> out, String typed) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            out.add(player.getName());
        }
        String wanted = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        int added = 0;
        for (String name : plugin.names().names()) {
            if (added >= MAX_COMPLETIONS) {
                break;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                out.add(name);
                added++;
            }
        }
    }

    private static List<String> prefixed(List<String> options, String typed) {
        String wanted = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(wanted)) {
                out.add(option);
            }
        }
        return out;
    }
}
