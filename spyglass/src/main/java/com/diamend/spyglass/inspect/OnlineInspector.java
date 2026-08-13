package com.diamend.spyglass.inspect;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Attributes;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

/**
 * Reads a player who is logged in, straight off the live server objects.
 *
 * <p>Every value goes through {@link Safe}, so a field this server's fork does
 * not implement prints as {@code n/a} instead of taking the report down with it.
 */
public final class OnlineInspector {

    /** Beyond this many rows a section says "and N more" rather than listing them. */
    private static final int MAX_ROWS = 400;

    private final Server server;

    public OnlineInspector(Server server) {
        this.server = server;
    }

    /** Builds one section for a player. */
    public Report section(Player player, Section section, Query query) {
        Report report = new Report();
        switch (section) {
            case OVERVIEW -> overview(report, player, query);
            case IDENTITY -> identity(report, player, query);
            case CONNECTION -> connection(report, player, query);
            case VITALS -> vitals(report, player);
            case POSITION -> position(report, player);
            case INVENTORY -> inventory(report, player, query);
            case ENDERCHEST -> enderChest(report, player, query);
            case ARMOR -> armor(report, player);
            case EFFECTS -> effects(report, player);
            case ATTRIBUTES -> attributes(report, player);
            case STATS -> stats(report, player, query);
            case ADVANCEMENTS -> advancements(report, player, query);
            case PERMISSIONS -> permissions(report, player, query);
            case SCOREBOARD -> scoreboard(report, player);
            case DATA -> data(report, player);
            case RECIPES -> recipes(report, player, query);
            case ITEM -> item(report, player, query);
            case ALL -> {
                for (Section part : Section.everything()) {
                    report.append(section(player, part, query));
                }
            }
            case NBT -> report.note("Raw NBT is read from the save file; see /spy <player> nbt.");
        }
        return report;
    }

    // ------------------------------------------------------------------
    // Overview — the answer to "what is going on with this player"
    // ------------------------------------------------------------------

    private void overview(Report report, Player player, Query query) {
        report.header("Overview");
        report.field("uuid", Safe.text(player::getUniqueId));
        report.field("game mode", Safe.text(player::getGameMode));
        report.field("health", Fmt.bar(health(player), maxHealth(player), 10)
                + absorption(player));
        report.field("hunger", Fmt.bar(Safe.integer(player::getFoodLevel, 0), 20, 10)
                + "  saturation " + Fmt.num(Safe.number(() -> (double) player.getSaturation(), 0D)));
        report.field("experience", "level " + Safe.integer(player::getLevel, 0)
                + " (" + Fmt.percent(Safe.number(() -> (double) player.getExp(), 0D))
                + " to next, " + Fmt.count(Safe.integer(player::getTotalExperience, 0)) + " total)");
        report.field("position", where(player));
        report.field("ping", Safe.integer(player::getPing, -1) + " ms");
        report.field("held item", Safe.text(() ->
                ItemFormatter.line(player.getInventory().getItemInMainHand())));
        report.field("inventory", inventorySummary(player));
        report.field("effects", Safe.text(() -> {
            Collection<PotionEffect> active = player.getActivePotionEffects();
            if (active.isEmpty()) {
                return "none";
            }
            List<String> names = new ArrayList<>();
            for (PotionEffect effect : active) {
                names.add(Fmt.shortKey(effect.getType().getKey().getKey()) + " " + (effect.getAmplifier() + 1));
            }
            return String.join(", ", names);
        }));
        report.field("flags", flags(player));
        report.field("address", address(player, query.sensitive()));
        report.note("Sections: " + String.join(", ", Section.names()));
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void identity(Report report, Player player, Query query) {
        report.header("Identity");
        report.field("name", Safe.text(player::getName));
        report.field("display name", Safe.text(() -> Fmt.plain(player.displayName())));
        report.field("tab list name", Safe.text(() -> Fmt.plain(player.playerListName())));
        report.field("uuid", Safe.text(player::getUniqueId));
        report.field("entity id", Safe.integer(player::getEntityId, -1));
        report.field("operator", Safe.text(player::isOp));
        report.field("whitelisted", Safe.text(player::isWhitelisted));
        report.field("banned", Safe.text(player::isBanned));
        report.field("game mode", Safe.text(player::getGameMode));
        report.field("first played", Fmt.stampWithAge(Safe.call(player::getFirstPlayed, 0L)));
        report.field("this session", Fmt.duration(sessionLength(player)));
        report.field("exempt from /spy", Safe.text(() -> player.hasPermission("spyglass.exempt")));
    }

    private void connection(Report report, Player player, Query query) {
        report.header("Connection");
        report.field("online", "yes");
        report.field("address", address(player, query.sensitive()));
        report.field("ping", Safe.integer(player::getPing, -1) + " ms");
        report.field("client brand", Safe.text(player::getClientBrandName));
        report.field("locale", Safe.text(player::getLocale));
        report.field("first played", Fmt.stampWithAge(Safe.call(player::getFirstPlayed, 0L)));
        report.field("last login", Fmt.stampWithAge(Safe.call(player::getLastLogin, 0L)));
        report.field("session so far", Fmt.duration(sessionLength(player)));
        report.field("view distance", Safe.text(player::getViewDistance));
        report.field("world", Safe.text(() -> player.getWorld().getName()));
    }

    private void vitals(Report report, Player player) {
        report.header("Vitals");
        report.field("health", Fmt.bar(health(player), maxHealth(player), 20) + absorption(player));
        report.field("food", Fmt.bar(Safe.integer(player::getFoodLevel, 0), 20, 20));
        report.field("saturation", Fmt.num(Safe.number(() -> (double) player.getSaturation(), 0D)));
        report.field("exhaustion", Fmt.num(Safe.number(() -> (double) player.getExhaustion(), 0D)));
        report.field("air", Safe.integer(player::getRemainingAir, 0)
                + "/" + Safe.integer(player::getMaximumAir, 0) + " ticks");
        report.field("fire ticks", Safe.integer(player::getFireTicks, 0)
                + " (max " + Safe.integer(player::getMaxFireTicks, 0) + ")");
        report.field("freeze ticks", Safe.integer(player::getFreezeTicks, 0)
                + " (max " + Safe.integer(player::getMaxFreezeTicks, 0) + ")");
        report.field("no damage ticks", Safe.integer(player::getNoDamageTicks, 0));
        report.field("last damage", Fmt.num(Safe.number(player::getLastDamage, 0D))
                + Safe.text(() -> player.getLastDamageCause() == null
                        ? "" : " from " + player.getLastDamageCause().getCause()));
        report.field("level", Safe.integer(player::getLevel, 0));
        report.field("exp to next level", Fmt.percent(Safe.number(() -> (double) player.getExp(), 0D)));
        report.field("total experience", Fmt.count(Safe.integer(player::getTotalExperience, 0)));
        report.field("game mode", Safe.text(player::getGameMode));
        report.field("allow flight", Safe.text(player::getAllowFlight));
        report.field("flying", Safe.text(player::isFlying));
        report.field("fly speed", Fmt.num(Safe.number(() -> (double) player.getFlySpeed(), 0D)));
        report.field("walk speed", Fmt.num(Safe.number(() -> (double) player.getWalkSpeed(), 0D)));
        report.field("invulnerable", Safe.text(player::isInvulnerable));
        report.field("gliding", Safe.text(player::isGliding));
        report.field("swimming", Safe.text(player::isSwimming));
        report.field("sneaking", Safe.text(player::isSneaking));
        report.field("sprinting", Safe.text(player::isSprinting));
        report.field("sleeping", Safe.text(player::isSleeping)
                + " (" + Safe.integer(player::getSleepTicks, 0) + " ticks)");
        report.field("dead", Safe.text(player::isDead));
        report.field("ticks lived", Fmt.ticks(Safe.integer(player::getTicksLived, 0)));
    }

    private void position(Report report, Player player) {
        report.header("Position");
        Location location = Safe.call(player::getLocation, null);
        if (location == null) {
            report.note("The server would not give a location for this player.");
            return;
        }
        report.field("world", Safe.text(() -> location.getWorld().getName())
                + " (" + Safe.text(() -> location.getWorld().getEnvironment()) + ")");
        report.field("x y z", Fmt.coord(location.getX()) + "  "
                + Fmt.coord(location.getY()) + "  " + Fmt.coord(location.getZ()));
        report.field("block", location.getBlockX() + " " + location.getBlockY()
                + " " + location.getBlockZ());
        report.field("chunk", Safe.text(() -> location.getChunk().getX() + " " + location.getChunk().getZ()));
        report.field("facing", Fmt.num(location.getYaw()) + " yaw, "
                + Fmt.num(location.getPitch()) + " pitch"
                + Safe.text(() -> " (" + player.getFacing() + ")"));
        report.field("biome", Safe.text(() -> location.getBlock().getBiome().getKey().toString()));
        report.field("light", Safe.text(() -> {
            Block block = location.getBlock();
            return block.getLightLevel() + " (sky " + block.getLightFromSky()
                    + ", blocks " + block.getLightFromBlocks() + ")";
        }));
        report.field("standing in", Safe.text(() -> location.getBlock().getType().getKey().getKey()));
        report.field("standing on", Safe.text(() ->
                location.clone().add(0, -1, 0).getBlock().getType().getKey().getKey()));
        report.field("on ground", Safe.text(player::isOnGround));
        report.field("fall distance", Fmt.num(Safe.number(() -> (double) player.getFallDistance(), 0D))
                + " blocks");
        report.field("velocity", Safe.text(() -> Fmt.num(player.getVelocity().getX()) + " "
                + Fmt.num(player.getVelocity().getY()) + " " + Fmt.num(player.getVelocity().getZ())));
        report.field("vehicle", Safe.text(() ->
                player.getVehicle() == null ? "none" : player.getVehicle().getType().toString()));
        report.field("passengers", Safe.text(() -> {
            List<Entity> riders = player.getPassengers();
            if (riders.isEmpty()) {
                return "none";
            }
            List<String> names = new ArrayList<>();
            for (Entity rider : riders) {
                names.add(rider.getType().toString());
            }
            return String.join(", ", names);
        }));
        report.field("respawn point", Safe.text(() -> describe(player.getRespawnLocation())));
        report.field("last death", Safe.text(() -> describe(player.getLastDeathLocation())));
        report.field("compass target", Safe.text(() -> describe(player.getCompassTarget())));
    }

    private void inventory(Report report, Player player, Query query) {
        report.header("Inventory");
        PlayerInventory inventory = Safe.call(player::getInventory, null);
        if (inventory == null) {
            report.note("No inventory.");
            return;
        }
        report.field("held slot", Safe.integer(inventory::getHeldItemSlot, 0));
        report.field("summary", inventorySummary(player));
        report.blank();
        listSlots(report, Safe.call(inventory::getContents, new ItemStack[0]), query, true);
    }

    private void enderChest(Report report, Player player, Query query) {
        report.header("Ender chest");
        Inventory chest = Safe.call(player::getEnderChest, null);
        if (chest == null) {
            report.note("No ender chest.");
            return;
        }
        listSlots(report, Safe.call(chest::getContents, new ItemStack[0]), query, false);
    }

    private void armor(Report report, Player player) {
        report.header("Armour and hands");
        PlayerInventory inventory = Safe.call(player::getInventory, null);
        if (inventory == null) {
            report.note("No inventory.");
            return;
        }
        report.field("main hand", Safe.text(() -> ItemFormatter.line(inventory.getItemInMainHand())));
        report.field("off hand", Safe.text(() -> ItemFormatter.line(inventory.getItemInOffHand())));
        report.field("helmet", Safe.text(() -> ItemFormatter.line(inventory.getHelmet())));
        report.field("chestplate", Safe.text(() -> ItemFormatter.line(inventory.getChestplate())));
        report.field("leggings", Safe.text(() -> ItemFormatter.line(inventory.getLeggings())));
        report.field("boots", Safe.text(() -> ItemFormatter.line(inventory.getBoots())));
    }

    private void effects(Report report, Player player) {
        report.header("Potion effects");
        Collection<PotionEffect> active = Safe.call(player::getActivePotionEffects, List.of());
        if (active.isEmpty()) {
            report.note("No active effects.");
            return;
        }
        for (PotionEffect effect : active) {
            Safe.run(() -> report.field(Fmt.shortKey(effect.getType().getKey().getKey()),
                    "level " + (effect.getAmplifier() + 1)
                            + ", " + (effect.getDuration() < 0 || effect.getDuration() == Integer.MAX_VALUE
                                    ? "infinite" : Fmt.ticks(effect.getDuration()))
                            + (effect.isAmbient() ? ", ambient" : "")
                            + (effect.hasParticles() ? "" : ", hidden particles")
                            + (effect.hasIcon() ? "" : ", no icon")));
        }
    }

    private void attributes(Report report, Player player) {
        report.header("Attributes");
        List<Attribute> all = Attributes.all();
        if (all.isEmpty()) {
            report.note("The attribute registry would not enumerate on this server.");
            return;
        }
        int shown = 0;
        for (Attribute attribute : all) {
            AttributeInstance instance = Safe.call(() -> player.getAttribute(attribute), null);
            if (instance == null) {
                continue;
            }
            shown++;
            String value = Fmt.num(Safe.number(instance::getValue, 0D))
                    + " (base " + Fmt.num(Safe.number(instance::getBaseValue, 0D)) + ")";
            report.field(Attributes.name(attribute), value);
            Safe.run(() -> {
                for (AttributeModifier modifier : instance.getModifiers()) {
                    report.text("    + " + modifier.getKey() + " " + modifier.getOperation()
                            + " " + Fmt.num(modifier.getAmount()));
                }
            });
        }
        if (shown == 0) {
            report.note("This player has no attributes the server will report.");
        }
    }

    private void stats(Report report, Player player, Query query) {
        report.header("Statistics");
        Map<String, Long> rows = new TreeMap<>();
        for (Statistic statistic : Statistic.values()) {
            Safe.run(() -> collect(rows, player, statistic));
        }
        rows.entrySet().removeIf(entry -> entry.getValue() == 0L || !query.matches(entry.getKey()));
        if (rows.isEmpty()) {
            report.note(query.hasArgument()
                    ? "No statistic matches \"" + query.argument() + "\"."
                    : "This player has no statistics yet.");
            return;
        }
        report.field("matching", rows.size() + " statistic(s)"
                + (query.hasArgument() ? " matching \"" + query.argument() + "\"" : ""));
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
                .thenComparing(Map.Entry::getKey));
        int printed = 0;
        for (Map.Entry<String, Long> entry : sorted) {
            if (printed++ >= MAX_ROWS) {
                report.note("... and " + (sorted.size() - MAX_ROWS)
                        + " more; narrow it with /spy <player> stats <filter>");
                break;
            }
            report.field(entry.getKey(), statValue(entry.getKey(), entry.getValue()));
        }
    }

    private void collect(Map<String, Long> rows, Player player, Statistic statistic) {
        String base = statistic.name().toLowerCase(Locale.ROOT);
        switch (statistic.getType()) {
            case UNTYPED -> {
                int value = Safe.integer(() -> player.getStatistic(statistic), 0);
                if (value != 0) {
                    rows.put(base, (long) value);
                }
            }
            case BLOCK, ITEM -> {
                boolean blocks = statistic.getType() == Statistic.Type.BLOCK;
                for (Material material : Material.values()) {
                    if (material.isLegacy() || (blocks ? !material.isBlock() : !material.isItem())) {
                        continue;
                    }
                    int value = Safe.integer(() -> player.getStatistic(statistic, material), 0);
                    if (value != 0) {
                        rows.put(base + "." + material.getKey().getKey(), (long) value);
                    }
                }
            }
            case ENTITY -> {
                for (EntityType type : EntityType.values()) {
                    int value = Safe.integer(() -> player.getStatistic(statistic, type), 0);
                    if (value != 0) {
                        rows.put(base + "." + type.name().toLowerCase(Locale.ROOT), (long) value);
                    }
                }
            }
        }
    }

    private void advancements(Report report, Player player, Query query) {
        report.header("Advancements");
        int done = 0;
        int started = 0;
        int total = 0;
        int recipes = 0;
        List<String> lines = new ArrayList<>();
        Iterator<Advancement> iterator = Safe.call(server::advancementIterator, List.<Advancement>of().iterator());
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            NamespacedKey key = Safe.call(advancement::getKey, null);
            if (key == null) {
                continue;
            }
            if (key.getKey().startsWith("recipes/")) {
                recipes++;
                continue;
            }
            total++;
            AdvancementProgress progress = Safe.call(() -> player.getAdvancementProgress(advancement), null);
            if (progress == null) {
                continue;
            }
            boolean complete = Safe.flag(progress::isDone, false);
            int awarded = Safe.integer(() -> progress.getAwardedCriteria().size(), 0);
            int remaining = Safe.integer(() -> progress.getRemainingCriteria().size(), 0);
            if (complete) {
                done++;
            } else if (awarded > 0) {
                started++;
            }
            if (!query.matches(key.toString()) || (!complete && awarded == 0)) {
                continue;
            }
            Collection<String> criteria = Safe.call(progress::getRemainingCriteria, Set.of());
            lines.add(key + "  " + (complete ? "done" : awarded + "/" + (awarded + remaining))
                    + (complete ? "" : "  remaining: " + Fmt.clip(String.join(", ", criteria), 60)));
        }
        report.field("completed", done + "/" + total);
        report.field("in progress", String.valueOf(started));
        report.field("recipe advancements", recipes + " (not listed)");
        if (lines.isEmpty()) {
            report.note("Nothing to list" + (query.hasArgument()
                    ? " for \"" + query.argument() + "\"." : "."));
            return;
        }
        lines.sort(Comparator.naturalOrder());
        for (String line : lines) {
            report.text(line);
        }
    }

    private void permissions(Report report, Player player, Query query) {
        report.header("Permissions");
        report.field("operator", Safe.text(player::isOp));
        Set<PermissionAttachmentInfo> effective = Safe.call(player::getEffectivePermissions, Set.of());
        List<String> lines = new ArrayList<>();
        for (PermissionAttachmentInfo info : effective) {
            Safe.run(() -> {
                if (!query.matches(info.getPermission())) {
                    return;
                }
                String source = info.getAttachment() == null
                        ? "default"
                        : String.valueOf(info.getAttachment().getPlugin().getName());
                lines.add((info.getValue() ? "+ " : "- ") + info.getPermission() + "  (" + source + ")");
            });
        }
        report.field("effective nodes", effective.size()
                + (query.hasArgument() ? ", " + lines.size() + " matching" : ""));
        lines.sort(Comparator.naturalOrder());
        int printed = 0;
        for (String line : lines) {
            if (printed++ >= MAX_ROWS) {
                report.note("... and " + (lines.size() - MAX_ROWS)
                        + " more; narrow it with /spy <player> permissions <filter>");
                break;
            }
            report.text(line);
        }
    }

    private void scoreboard(Report report, Player player) {
        report.header("Scoreboard");
        Scoreboard board = Safe.call(player::getScoreboard, null);
        if (board == null) {
            report.note("No scoreboard.");
            return;
        }
        Team team = Safe.call(() -> board.getEntryTeam(player.getName()), null);
        report.field("team", team == null ? "none" : Safe.text(team::getName));
        if (team != null) {
            report.field("team display", Safe.text(() -> Fmt.plain(team.displayName())));
            report.field("team members", Safe.integer(() -> team.getEntries().size(), 0));
        }
        Set<Objective> objectives = Safe.call(board::getObjectives, Set.of());
        int shown = 0;
        for (Objective objective : objectives) {
            Score score = Safe.call(() -> objective.getScore(player.getName()), null);
            if (score == null || !Safe.flag(score::isScoreSet, false)) {
                continue;
            }
            shown++;
            report.field(Safe.text(objective::getName), Safe.integer(score::getScore, 0)
                    + "  (" + Safe.text(objective::getDisplaySlot) + ")");
        }
        if (shown == 0) {
            report.note("No objective has a score for this player.");
        }
    }

    private void data(Report report, Player player) {
        report.header("Player data");
        Safe.run(() -> {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            Collection<NamespacedKey> keys = pdc.getKeys();
            report.field("persistent data", keys.size() + " key(s)");
            for (NamespacedKey key : keys) {
                report.text("  " + key + " = " + Fmt.clip(PdcReader.read(pdc, key), 120));
            }
        });
        Safe.run(() -> {
            Collection<String> tags = player.getScoreboardTags();
            report.field("scoreboard tags", tags.isEmpty() ? "none" : String.join(", ", tags));
        });
        report.field("portal cooldown", Safe.integer(player::getPortalCooldown, 0) + " ticks");
        report.field("entity id", Safe.integer(player::getEntityId, -1));
        report.field("op", Safe.text(player::isOp));
        report.note("The full save tree is /spy " + Safe.text(player::getName) + " nbt");
    }

    private void recipes(Report report, Player player, Query query) {
        report.header("Recipes");
        Set<NamespacedKey> discovered = Safe.call(player::getDiscoveredRecipes, Set.of());
        report.field("unlocked", discovered.size() + " recipe(s)");
        if (!query.hasArgument()) {
            report.note("Add a filter to list them: /spy <player> recipes <text>");
            return;
        }
        List<String> lines = new ArrayList<>();
        for (NamespacedKey key : discovered) {
            if (query.matches(key.toString())) {
                lines.add(key.toString());
            }
        }
        lines.sort(Comparator.naturalOrder());
        report.field("matching", String.valueOf(lines.size()));
        for (String line : lines.subList(0, Math.min(lines.size(), MAX_ROWS))) {
            report.text(line);
        }
    }

    private void item(Report report, Player player, Query query) {
        Integer slot = query.slot();
        if (slot == null) {
            report.header("Item");
            report.note("Name a slot: /spy <player> item <0-40>. Slot 0-8 is the hotbar, "
                    + "9-35 the backpack, 36-39 armour, 40 the off hand.");
            return;
        }
        report.header("Item in slot " + slot + " (" + slotLabel(slot) + ")");
        ItemStack[] contents = Safe.call(() -> player.getInventory().getContents(), new ItemStack[0]);
        if (slot < 0 || slot >= contents.length) {
            report.note("That player has slots 0 to " + Math.max(0, contents.length - 1) + ".");
            return;
        }
        ItemFormatter.detail(report, contents[slot]);
    }

    // ------------------------------------------------------------------
    // Shared bits
    // ------------------------------------------------------------------

    private void listSlots(Report report, ItemStack[] contents, Query query, boolean playerLayout) {
        int empty = 0;
        int shown = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (ItemFormatter.isEmpty(item)) {
                empty++;
                continue;
            }
            String line = ItemFormatter.line(item);
            if (!query.matches(line)) {
                continue;
            }
            shown++;
            String label = playerLayout
                    ? String.format("%2d %-8s", slot, slotLabel(slot))
                    : String.format("%2d", slot);
            report.text(label + "  " + line);
        }
        if (shown == 0) {
            report.note(query.hasArgument()
                    ? "Nothing matches \"" + query.argument() + "\"."
                    : "Every slot is empty.");
        }
        report.note(empty + " empty slot(s) of " + contents.length + ".");
    }

    /** Which part of the inventory a raw slot index belongs to. */
    public static String slotLabel(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "hotbar";
        }
        if (slot <= 35) {
            return "pack";
        }
        return switch (slot) {
            case 36 -> "boots";
            case 37 -> "legs";
            case 38 -> "chest";
            case 39 -> "helmet";
            case 40 -> "offhand";
            default -> "?";
        };
    }

    private String inventorySummary(Player player) {
        return Safe.text(() -> {
            ItemStack[] contents = player.getInventory().getContents();
            int used = 0;
            int items = 0;
            for (ItemStack item : contents) {
                if (!ItemFormatter.isEmpty(item)) {
                    used++;
                    items += item.getAmount();
                }
            }
            return used + "/" + contents.length + " slots used, " + items + " item(s)";
        });
    }

    private String flags(Player player) {
        List<String> flags = new ArrayList<>();
        Safe.run(() -> {
            if (player.isFlying()) {
                flags.add("flying");
            }
            if (player.getAllowFlight()) {
                flags.add("may fly");
            }
            if (player.isInvulnerable()) {
                flags.add("invulnerable");
            }
            if (player.isSneaking()) {
                flags.add("sneaking");
            }
            if (player.isSprinting()) {
                flags.add("sprinting");
            }
            if (player.isSwimming()) {
                flags.add("swimming");
            }
            if (player.isGliding()) {
                flags.add("gliding");
            }
            if (player.isSleeping()) {
                flags.add("sleeping");
            }
            if (player.isDead()) {
                flags.add("dead");
            }
            if (player.isOp()) {
                flags.add("op");
            }
        });
        return flags.isEmpty() ? "none" : String.join(", ", flags);
    }

    private String where(Player player) {
        return Safe.text(() -> {
            Location location = player.getLocation();
            return location.getWorld().getName() + " " + location.getBlockX() + " "
                    + location.getBlockY() + " " + location.getBlockZ();
        });
    }

    private static String describe(Location location) {
        if (location == null) {
            return "none";
        }
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private double health(Player player) {
        return Safe.number(player::getHealth, 0D);
    }

    private double maxHealth(Player player) {
        return Safe.number(() -> {
            Attribute attribute = Attributes.byName("max_health");
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            return instance == null ? 20D : instance.getValue();
        }, 20D);
    }

    private String absorption(Player player) {
        double absorption = Safe.number(player::getAbsorptionAmount, 0D);
        return absorption > 0 ? "  +" + Fmt.num(absorption) + " absorption" : "";
    }

    /** How long this login has lasted, from the server's own login timestamp. */
    private long sessionLength(Player player) {
        long lastLogin = Safe.call(player::getLastLogin, 0L);
        return lastLogin <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - lastLogin);
    }

    private String address(Player player, boolean sensitive) {
        if (!sensitive) {
            return "(hidden — needs spyglass.sensitive)";
        }
        InetSocketAddress address = Safe.call(player::getAddress, null);
        return address == null ? Safe.UNKNOWN : address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    /** Ticks, centimetres or a plain count, depending on what the statistic counts. */
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
