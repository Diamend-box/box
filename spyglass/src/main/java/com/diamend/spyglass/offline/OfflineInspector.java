package com.diamend.spyglass.offline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.diamend.spyglass.inspect.Query;
import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtList;
import com.diamend.spyglass.nbt.NbtPrinter;
import com.diamend.spyglass.nbt.NbtTag;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Attributes;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

/**
 * Reads a player who is not logged in, out of their save file.
 *
 * <p>This is the half of Spyglass that makes "any player" true rather than "any
 * player who happens to be online". Almost everything the live API would tell
 * you is in the file — inventory, ender chest, health, hunger, position,
 * effects, attributes, abilities, persistent data — so almost every section has
 * an offline answer. The few that genuinely only exist in memory (a live
 * scoreboard, effective permissions) say so instead of guessing.
 */
public final class OfflineInspector {

    private static final int MAX_ROWS = 400;

    /** Builds one section from a save file. */
    public Report section(OfflineSnapshot snapshot, Section section, Query query) {
        Report report = new Report();
        if (!snapshot.hasData() && section != Section.IDENTITY && section != Section.OVERVIEW) {
            report.header(title(section));
            report.note("No save data: " + snapshot.error());
            return report;
        }
        NbtCompound data = snapshot.data();
        switch (section) {
            case OVERVIEW -> overview(report, snapshot, data);
            case IDENTITY -> identity(report, snapshot, data);
            case CONNECTION -> connection(report, snapshot, data);
            case VITALS -> vitals(report, data);
            case POSITION -> position(report, data);
            case INVENTORY -> inventory(report, data, query);
            case ENDERCHEST -> enderChest(report, data, query);
            case ARMOR -> armor(report, data);
            case EFFECTS -> effects(report, data);
            case ATTRIBUTES -> attributes(report, data);
            case STATS -> stats(report, snapshot, query);
            case ADVANCEMENTS -> advancements(report, snapshot, query);
            case DATA -> data(report, data);
            case RECIPES -> recipes(report, data, query);
            case ITEM -> item(report, data, query);
            case PERMISSIONS -> {
                report.header("Permissions");
                report.note("Effective permissions only exist while a player is connected.");
                report.field("operator", Safe.text(() -> snapshot.player().isOp()));
            }
            case SCOREBOARD -> {
                report.header("Scoreboard");
                report.note("Live scoreboard scores are not kept in a player's save file.");
                scoreboardTags(report, data);
            }
            case NBT -> report.note("Use /spy <player> nbt [path] for the raw tree.");
            case ALL -> {
                for (Section part : Section.everything()) {
                    report.append(section(snapshot, part, query));
                }
            }
        }
        return report;
    }

    private static String title(Section section) {
        String id = section.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void overview(Report report, OfflineSnapshot snapshot, NbtCompound data) {
        report.header("Overview (offline)");
        report.field("uuid", snapshot.uuid());
        report.field("saved", Fmt.stampWithAge(snapshot.savedAt()));
        if (data == null) {
            report.note("No save data: " + snapshot.error());
            return;
        }
        report.field("game mode", gameMode(data.integer("playerGameType", -1)));
        report.field("health", Fmt.bar(data.floatValue("Health", 0f), maxHealth(data), 10));
        report.field("hunger", Fmt.bar(data.integer("foodLevel", 0), 20, 10)
                + "  saturation " + Fmt.num(data.floatValue("foodSaturationLevel", 0f)));
        report.field("experience", "level " + data.integer("XpLevel", 0)
                + " (" + Fmt.percent(data.floatValue("XpP", 0f)) + " to next, "
                + Fmt.count(data.integer("XpTotal", 0)) + " total)");
        report.field("position", position(data));
        report.field("held item", heldItem(data));
        report.field("inventory", inventorySummary(data));
        report.field("ender chest", countItems(data.list("EnderItems")) + " item(s)");
        report.field("effects", effectSummary(data));
        report.field("data version", data.integer("DataVersion", 0));
        report.note("Sections: " + String.join(", ", Section.names()));
    }

    private void identity(Report report, OfflineSnapshot snapshot, NbtCompound data) {
        report.header("Identity (offline)");
        report.field("name", snapshot.name());
        report.field("uuid", snapshot.uuid());
        report.field("operator", Safe.text(() -> snapshot.player().isOp()));
        report.field("whitelisted", Safe.text(() -> snapshot.player().isWhitelisted()));
        report.field("banned", Safe.text(() -> snapshot.player().isBanned()));
        report.field("has played", Safe.text(() -> snapshot.player().hasPlayedBefore()));
        report.field("first played", Fmt.stampWithAge(Safe.call(() -> snapshot.player().getFirstPlayed(), 0L)));
        report.field("save file", snapshot.dataFile() == null
                ? Safe.UNKNOWN : snapshot.dataFile().getPath());
        report.field("saved", Fmt.stampWithAge(snapshot.savedAt()));
        if (data == null) {
            report.note("No save data: " + snapshot.error());
            return;
        }
        NbtCompound bukkit = data.compound("bukkit");
        if (bukkit != null) {
            report.field("last known name", bukkit.string("lastKnownName", Safe.UNKNOWN));
            report.field("first played (bukkit)", Fmt.stampWithAge(bukkit.longValue("firstPlayed", 0L)));
            report.field("last played (bukkit)", Fmt.stampWithAge(bukkit.longValue("lastPlayed", 0L)));
        }
        report.field("game mode", gameMode(data.integer("playerGameType", -1)));
        report.field("data version", data.integer("DataVersion", 0));
    }

    private void connection(Report report, OfflineSnapshot snapshot, NbtCompound data) {
        report.header("Connection (offline)");
        report.field("online", "no");
        report.field("last login", Fmt.stampWithAge(Safe.call(() -> snapshot.player().getLastLogin(), 0L)));
        report.field("last seen", Fmt.stampWithAge(Safe.call(() -> snapshot.player().getLastSeen(), 0L)));
        report.field("save written", Fmt.stampWithAge(snapshot.savedAt()));
        if (data != null) {
            NbtCompound paper = data.compound("Paper");
            if (paper != null) {
                report.field("paper last login", Fmt.stampWithAge(paper.longValue("LastLogin", 0L)));
                report.field("paper last seen", Fmt.stampWithAge(paper.longValue("LastSeen", 0L)));
            }
        }
        report.note("Addresses are not kept in a save file — only a live session has one.");
    }

    private void vitals(Report report, NbtCompound data) {
        report.header("Vitals (offline)");
        report.field("health", Fmt.bar(data.floatValue("Health", 0f), maxHealth(data), 20));
        report.field("food", Fmt.bar(data.integer("foodLevel", 0), 20, 20));
        report.field("saturation", Fmt.num(data.floatValue("foodSaturationLevel", 0f)));
        report.field("exhaustion", Fmt.num(data.floatValue("foodExhaustionLevel", 0f)));
        report.field("air", data.integer("Air", 0) + " ticks");
        report.field("fire", data.integer("Fire", 0) + " ticks");
        report.field("hurt time", data.integer("HurtTime", 0) + " ticks");
        report.field("death time", data.integer("DeathTime", 0) + " ticks");
        report.field("score", Fmt.count(data.integer("Score", 0)));
        report.field("level", data.integer("XpLevel", 0));
        report.field("exp to next level", Fmt.percent(data.floatValue("XpP", 0f)));
        report.field("total experience", Fmt.count(data.integer("XpTotal", 0)));
        report.field("game mode", gameMode(data.integer("playerGameType", -1)));
        report.field("previous game mode", gameMode(data.integer("previousPlayerGameType", -1)));
        report.field("invulnerable", Fmt.yesNo(data.bool("Invulnerable", false)));
        report.field("sleep timer", data.integer("SleepTimer", 0) + " ticks");
        report.field("seen credits", Fmt.yesNo(data.bool("seenCredits", false)));
        report.field("portal cooldown", data.integer("PortalCooldown", 0) + " ticks");
        NbtCompound abilities = data.compound("abilities");
        if (abilities != null) {
            report.field("flying", Fmt.yesNo(abilities.bool("flying", false)));
            report.field("may fly", Fmt.yesNo(abilities.bool("mayfly", false)));
            report.field("instant build", Fmt.yesNo(abilities.bool("instabuild", false)));
            report.field("may build", Fmt.yesNo(abilities.bool("mayBuild", true)));
            report.field("invulnerable (ability)", Fmt.yesNo(abilities.bool("invulnerable", false)));
            report.field("fly speed", Fmt.num(abilities.floatValue("flySpeed", 0f)));
            report.field("walk speed", Fmt.num(abilities.floatValue("walkSpeed", 0f)));
        }
    }

    private void position(Report report, NbtCompound data) {
        report.header("Position (offline)");
        report.field("dimension", dimension(data));
        report.field("x y z", position(data));
        NbtList rotation = data.list("Rotation");
        if (rotation != null && rotation.size() >= 2) {
            report.field("facing", Fmt.num(rotation.floatAt(0, 0f)) + " yaw, "
                    + Fmt.num(rotation.floatAt(1, 0f)) + " pitch");
        }
        NbtList motion = data.list("Motion");
        if (motion != null && motion.size() >= 3) {
            report.field("motion", Fmt.num(motion.doubleAt(0, 0)) + " "
                    + Fmt.num(motion.doubleAt(1, 0)) + " " + Fmt.num(motion.doubleAt(2, 0)));
        }
        report.field("on ground", Fmt.yesNo(data.bool("OnGround", false)));
        report.field("fall distance", Fmt.num(data.floatValue("FallDistance", 0f)) + " blocks");
        report.field("respawn point", respawn(data));
        report.field("last death", deathLocation(data));
        NbtCompound nether = data.compound("enteredNetherPosition");
        if (nether != null) {
            report.field("entered nether at", Fmt.coord(nether.doubleValue("x", 0))
                    + " " + Fmt.coord(nether.doubleValue("y", 0))
                    + " " + Fmt.coord(nether.doubleValue("z", 0)));
        }
        NbtCompound vehicle = data.compound("RootVehicle");
        if (vehicle != null) {
            NbtCompound entity = vehicle.compound("Entity");
            report.field("riding", entity == null ? "yes" : entity.string("id", "yes"));
        }
    }

    private void inventory(Report report, NbtCompound data, Query query) {
        report.header("Inventory (offline)");
        report.field("held slot", data.integer("SelectedItemSlot", 0));
        report.field("summary", inventorySummary(data));
        report.blank();
        listItems(report, data.list("Inventory"), query, true);
    }

    private void enderChest(Report report, NbtCompound data, Query query) {
        report.header("Ender chest (offline)");
        listItems(report, data.list("EnderItems"), query, false);
    }

    private void armor(Report report, NbtCompound data) {
        report.header("Armour and hands (offline)");
        NbtList inventory = data.list("Inventory");
        int held = data.integer("SelectedItemSlot", 0);
        report.field("main hand", itemInSlot(inventory, held));
        report.field("off hand", itemInSlot(inventory, NbtItems.OFFHAND_SLOT));
        report.field("helmet", itemInSlot(inventory, 103));
        report.field("chestplate", itemInSlot(inventory, 102));
        report.field("leggings", itemInSlot(inventory, 101));
        report.field("boots", itemInSlot(inventory, 100));
    }

    private void effects(Report report, NbtCompound data) {
        report.header("Potion effects (offline)");
        NbtTag tag = data.firstOf("active_effects", "ActiveEffects", "Effects");
        NbtList effects = tag == null ? null : tag.asList();
        if (effects == null || effects.isEmpty()) {
            report.note("No active effects.");
            return;
        }
        for (NbtCompound effect : effects.compounds()) {
            String id = effect.string("id", null);
            if (id == null) {
                // Pre-1.20.2 stored a numeric effect id instead of a name.
                id = "id " + effect.integer("Id", -1);
            }
            int duration = effect.has("duration")
                    ? effect.integer("duration", 0) : effect.integer("Duration", 0);
            int amplifier = effect.has("amplifier")
                    ? effect.integer("amplifier", 0) : effect.integer("Amplifier", 0);
            report.field(Fmt.shortKey(id), "level " + (amplifier + 1) + ", "
                    + (duration < 0 ? "infinite" : Fmt.ticks(duration))
                    + (effect.bool("ambient", false) ? ", ambient" : ""));
        }
    }

    private void attributes(Report report, NbtCompound data) {
        report.header("Attributes (offline)");
        NbtTag tag = data.firstOf("attributes", "Attributes");
        NbtList attributes = tag == null ? null : tag.asList();
        if (attributes == null || attributes.isEmpty()) {
            report.note("No attributes stored — the player has vanilla defaults.");
            return;
        }
        for (NbtCompound attribute : attributes.compounds()) {
            String id = attribute.string("id", null);
            if (id == null) {
                id = attribute.string("Name", "?");
            }
            double base = attribute.has("base")
                    ? attribute.doubleValue("base", 0) : attribute.doubleValue("Base", 0);
            report.field(Attributes.fold(id), "base " + Fmt.num(base));
            NbtList modifiers = attribute.list("modifiers");
            if (modifiers == null) {
                modifiers = attribute.list("Modifiers");
            }
            if (modifiers != null) {
                for (NbtCompound modifier : modifiers.compounds()) {
                    String name = modifier.string("id", modifier.string("Name", "?"));
                    double amount = modifier.has("amount")
                            ? modifier.doubleValue("amount", 0) : modifier.doubleValue("Amount", 0);
                    report.text("    + " + name + " " + Fmt.num(amount)
                            + " (operation " + modifier.string("operation",
                                    String.valueOf(modifier.integer("Operation", 0))) + ")");
                }
            }
        }
    }

    private void stats(Report report, OfflineSnapshot snapshot, Query query) {
        report.header("Statistics (offline)");
        StatsFile stats = snapshot.stats();
        if (stats.isEmpty()) {
            report.note("No statistics file for this player.");
            return;
        }
        List<Map.Entry<String, Long>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> entry : stats.values().entrySet()) {
            if (query.matches(entry.getKey()) && entry.getValue() != 0L) {
                rows.add(entry);
            }
        }
        report.field("matching", rows.size() + " of " + stats.values().size() + " statistic(s)");
        rows.sort(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
                .thenComparing(Map.Entry::getKey));
        int printed = 0;
        for (Map.Entry<String, Long> entry : rows) {
            if (printed++ >= MAX_ROWS) {
                report.note("... and " + (rows.size() - MAX_ROWS) + " more; add a filter to narrow it");
                break;
            }
            report.field(entry.getKey(), statValue(entry.getKey(), entry.getValue()));
        }
        report.note("Names are vanilla's, as written in stats/<uuid>.json.");
    }

    private void advancements(Report report, OfflineSnapshot snapshot, Query query) {
        report.header("Advancements (offline)");
        AdvancementsFile file = snapshot.advancements();
        if (file.isEmpty()) {
            report.note("No advancements file for this player.");
            return;
        }
        report.field("completed", String.valueOf(file.doneCount()));
        report.field("in progress", String.valueOf(file.startedCount()));
        report.field("recipe unlocks", file.recipeCount() + " (not listed)");
        List<String> lines = new ArrayList<>();
        for (AdvancementsFile.Entry entry : file.entries()) {
            if (AdvancementsFile.isRecipe(entry.key()) || !query.matches(entry.key())) {
                continue;
            }
            lines.add(entry.key() + "  " + (entry.done() ? "done" : entry.criteria() + " criteria")
                    + (entry.earned() == null ? "" : "  " + entry.earned()));
        }
        lines.sort(Comparator.naturalOrder());
        for (String line : lines.subList(0, Math.min(lines.size(), MAX_ROWS))) {
            report.text(line);
        }
        if (lines.isEmpty()) {
            report.note("Nothing to list" + (query.hasArgument()
                    ? " for \"" + query.argument() + "\"." : "."));
        }
    }

    private void data(Report report, NbtCompound data) {
        report.header("Player data (offline)");
        NbtCompound bukkitValues = data.compound("BukkitValues");
        if (bukkitValues == null || bukkitValues.isEmpty()) {
            report.field("persistent data", "0 key(s)");
        } else {
            report.field("persistent data", bukkitValues.size() + " key(s)");
            NbtPrinter printer = new NbtPrinter(4, 16);
            for (String key : bukkitValues.keys()) {
                report.text("  " + key + " = " + printer.scalar(bukkitValues.get(key)));
            }
        }
        scoreboardTags(report, data);
        report.field("uuid tag", new NbtPrinter(1, 4).scalar(data.get("UUID")));
        report.field("data version", data.integer("DataVersion", 0));
        report.note("Everything else is in the raw tree: /spy <player> nbt");
    }

    private void scoreboardTags(Report report, NbtCompound data) {
        if (data == null) {
            return;
        }
        NbtList tags = data.list("Tags");
        report.field("scoreboard tags", tags == null || tags.isEmpty()
                ? "none" : String.join(", ", tags.strings()));
    }

    private void recipes(Report report, NbtCompound data, Query query) {
        report.header("Recipes (offline)");
        NbtCompound book = data.compound("recipeBook");
        if (book == null) {
            report.note("No recipe book in the save.");
            return;
        }
        NbtList recipes = book.list("recipes");
        List<String> unlocked = recipes == null ? List.of() : recipes.strings();
        report.field("unlocked", unlocked.size() + " recipe(s)");
        NbtList toDisplay = book.list("toBeDisplayed");
        report.field("new to them", (toDisplay == null ? 0 : toDisplay.size()) + " recipe(s)");
        if (!query.hasArgument()) {
            report.note("Add a filter to list them: /spy <player> recipes <text>");
            return;
        }
        List<String> matching = new ArrayList<>();
        for (String recipe : unlocked) {
            if (query.matches(recipe)) {
                matching.add(recipe);
            }
        }
        matching.sort(Comparator.naturalOrder());
        report.field("matching", String.valueOf(matching.size()));
        for (String recipe : matching.subList(0, Math.min(matching.size(), MAX_ROWS))) {
            report.text(recipe);
        }
    }

    private void item(Report report, NbtCompound data, Query query) {
        Integer slot = query.slot();
        if (slot == null) {
            report.header("Item (offline)");
            report.note("Name a slot: /spy <player> item <slot>. A save numbers slots 0-8 hotbar, "
                    + "9-35 pack, 100-103 armour, -106 off hand.");
            return;
        }
        report.header("Item in slot " + slot + " (" + NbtItems.slotLabel(slot) + ")");
        NbtCompound item = findSlot(data.list("Inventory"), slot);
        if (item == null) {
            item = findSlot(data.list("EnderItems"), slot);
        }
        if (item == null) {
            report.note("Nothing in that slot.");
            return;
        }
        NbtItems.detail(report, item);
    }

    // ------------------------------------------------------------------
    // Shared bits
    // ------------------------------------------------------------------

    private void listItems(Report report, NbtList items, Query query, boolean showSlots) {
        if (items == null || items.isEmpty()) {
            report.note("Empty.");
            return;
        }
        int shown = 0;
        for (NbtCompound item : items.compounds()) {
            if (NbtItems.isEmpty(item)) {
                continue;
            }
            String line = NbtItems.line(item);
            if (!query.matches(line)) {
                continue;
            }
            shown++;
            int slot = NbtItems.slot(item);
            String label = showSlots && slot != Integer.MIN_VALUE
                    ? String.format("%4d %-8s", slot, NbtItems.slotLabel(slot))
                    : String.format("%4d %-8s", slot == Integer.MIN_VALUE ? 0 : slot, "");
            report.text(label + "  " + line);
        }
        if (shown == 0) {
            report.note(query.hasArgument()
                    ? "Nothing matches \"" + query.argument() + "\"."
                    : "Empty.");
        } else {
            report.note(shown + " stack(s).");
        }
    }

    private static NbtCompound findSlot(NbtList items, int slot) {
        if (items == null) {
            return null;
        }
        for (NbtCompound item : items.compounds()) {
            if (NbtItems.slot(item) == slot) {
                return item;
            }
        }
        return null;
    }

    private static String itemInSlot(NbtList items, int slot) {
        NbtCompound item = findSlot(items, slot);
        return item == null ? "empty" : NbtItems.line(item);
    }

    private static String inventorySummary(NbtCompound data) {
        NbtList inventory = data.list("Inventory");
        if (inventory == null) {
            return "no inventory in the save";
        }
        int stacks = 0;
        int items = 0;
        for (NbtCompound item : inventory.compounds()) {
            if (NbtItems.isEmpty(item)) {
                continue;
            }
            stacks++;
            items += NbtItems.count(item);
        }
        return stacks + " stack(s), " + items + " item(s)";
    }

    private static int countItems(NbtList list) {
        if (list == null) {
            return 0;
        }
        int items = 0;
        for (NbtCompound item : list.compounds()) {
            items += NbtItems.count(item);
        }
        return items;
    }

    private static String heldItem(NbtCompound data) {
        return itemInSlot(data.list("Inventory"), data.integer("SelectedItemSlot", 0));
    }

    private static String effectSummary(NbtCompound data) {
        NbtTag tag = data.firstOf("active_effects", "ActiveEffects", "Effects");
        NbtList effects = tag == null ? null : tag.asList();
        if (effects == null || effects.isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        for (NbtCompound effect : effects.compounds()) {
            names.add(Fmt.shortKey(effect.string("id", "?")));
        }
        return String.join(", ", names);
    }

    private static String position(NbtCompound data) {
        NbtList pos = data.list("Pos");
        if (pos == null || pos.size() < 3) {
            return Safe.UNKNOWN;
        }
        return dimension(data) + " " + Fmt.coord(pos.doubleAt(0, 0))
                + " " + Fmt.coord(pos.doubleAt(1, 0)) + " " + Fmt.coord(pos.doubleAt(2, 0));
    }

    private static String dimension(NbtCompound data) {
        NbtTag tag = data.get("Dimension");
        if (tag == null) {
            return Safe.UNKNOWN;
        }
        // Modern saves name the dimension; ancient ones number it.
        String name = tag.asString(null);
        if (name != null) {
            return name;
        }
        return switch (tag.asInt(0)) {
            case -1 -> "minecraft:the_nether";
            case 1 -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
    }

    private static String respawn(NbtCompound data) {
        // 1.21.2 folded the spawn tags into one "respawn" compound.
        NbtCompound respawn = data.compound("respawn");
        if (respawn != null) {
            int[] pos = respawn.get("pos") == null ? null : respawn.get("pos").asIntArray();
            String where = pos == null || pos.length < 3
                    ? Safe.UNKNOWN : pos[0] + " " + pos[1] + " " + pos[2];
            return respawn.string("dimension", "minecraft:overworld") + " " + where
                    + (respawn.bool("forced", false) ? " (forced)" : "");
        }
        if (!data.has("SpawnX")) {
            return "none (world spawn)";
        }
        return data.string("SpawnDimension", "minecraft:overworld") + " "
                + data.integer("SpawnX", 0) + " " + data.integer("SpawnY", 0)
                + " " + data.integer("SpawnZ", 0)
                + (data.bool("SpawnForced", false) ? " (forced)" : "");
    }

    private static String deathLocation(NbtCompound data) {
        NbtCompound death = data.compound("LastDeathLocation");
        if (death == null) {
            return "none";
        }
        NbtTag pos = death.get("pos");
        int[] coords = pos == null ? null : pos.asIntArray();
        String where = coords == null || coords.length < 3
                ? Safe.UNKNOWN : coords[0] + " " + coords[1] + " " + coords[2];
        return death.string("dimension", "?") + " " + where;
    }

    private static double maxHealth(NbtCompound data) {
        NbtTag tag = data.firstOf("attributes", "Attributes");
        NbtList attributes = tag == null ? null : tag.asList();
        if (attributes != null) {
            for (NbtCompound attribute : attributes.compounds()) {
                String id = attribute.string("id", attribute.string("Name", ""));
                if (Attributes.fold(id).equals("max_health")) {
                    return attribute.has("base")
                            ? attribute.doubleValue("base", 20) : attribute.doubleValue("Base", 20);
                }
            }
        }
        return 20D;
    }

    private static String gameMode(int mode) {
        return switch (mode) {
            case 0 -> "SURVIVAL";
            case 1 -> "CREATIVE";
            case 2 -> "ADVENTURE";
            case 3 -> "SPECTATOR";
            default -> Safe.UNKNOWN;
        };
    }

    /** Same treatment the online statistics get: ticks, centimetres or a count. */
    static String statValue(String name, long value) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("one_cm")) {
            return Fmt.centimetres(value);
        }
        if (lower.contains("time") || lower.contains("one_minute")) {
            return Fmt.ticks(value);
        }
        return Fmt.count(value);
    }
}
