package com.diamend.boxcore.boost;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Temporary multipliers, running server-wide or for one player.
 *
 * <p>Boosts multiply together: a 2× global and a 2× personal boost make 4×.
 * That is the intuitive reading, and it is also the explosive one, so
 * {@code boosts.max-multiplier} exists as a hard ceiling no combination can
 * exceed. Raise it deliberately, not by accident.
 *
 * <p>Everything expires on the wall clock ({@link Boost}), which is what lets a
 * personal boost survive a relog and a global one survive a restart. Global
 * boosts are written to {@code boosts.yml} for exactly that reason — a restart
 * mid-event should not silently cancel the event.
 */
public class BoostsModule implements BoxModule {

    /** A recurring window from config that starts a global boost on its own. */
    private record Window(List<DayOfWeek> days,
                          LocalTime start,
                          long durationMillis,
                          List<BoostType> types,
                          double multiplier) {
    }

    /** A boost item as configured: what it grants, and how it looks. */
    public record ItemDefinition(BoostItems.Payload payload, BoostItems.Appearance appearance) {
    }

    private final BoxCorePlugin plugin;
    private final BoostItems items;

    /** Server-wide boosts. Copy-on-write: read every block break, written rarely. */
    private final List<Boost> global = new CopyOnWriteArrayList<>();
    private final List<Window> windows = new ArrayList<>();
    private final Map<String, ItemDefinition> definitions = new LinkedHashMap<>();

    private double maxMultiplier = 8.0;
    private boolean oresOnly = true;
    private boolean announce = true;
    private int checkTicks = 100;
    private ZoneId zone = ZoneId.systemDefault();

    private BukkitTask task;

    public BoostsModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
        this.items = new BoostItems(plugin);
    }

    public BoostItems items() {
        return items;
    }

    @Override
    public String id() {
        return "boosts";
    }

    @Override
    public String displayName() {
        return "Boosts";
    }

    @Override
    public void enable() {
        loadConfig();
        loadGlobal();
        plugin.getServer().getPluginManager().registerEvents(new BoostListener(plugin, this), plugin);
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, checkTicks, checkTicks);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveGlobal();
    }

    @Override
    public void reload() {
        loadConfig();
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    private void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("boosts");
        maxMultiplier = Math.max(1.0, section == null ? 8.0 : section.getDouble("max-multiplier", 8.0));
        oresOnly = section == null || section.getBoolean("drops.ores-only", true);
        announce = section == null || section.getBoolean("announce", true);
        checkTicks = Math.max(20, section == null ? 100 : section.getInt("check-ticks", 100));

        String configured = section == null ? "" : section.getString("timezone", "");
        zone = ZoneId.systemDefault();
        if (configured != null && !configured.isBlank()) {
            try {
                zone = ZoneId.of(configured.trim());
            } catch (Exception ex) {
                plugin.getLogger().warning("boosts.timezone: unknown zone '" + configured
                        + "', using the server default (" + zone + ").");
            }
        }
        loadWindows(section);
        loadItems(section);
    }

    private void loadItems(ConfigurationSection section) {
        definitions.clear();
        ConfigurationSection root = section == null
                ? null
                : section.getConfigurationSection("items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String where = "boosts.items." + id;
            List<BoostType> types = typesFor(entry.getString("type", "all"));
            if (types.isEmpty()) {
                plugin.getLogger().warning(where + ".type names no boost type, skipping.");
                continue;
            }
            double multiplier = entry.getDouble("multiplier", 2.0);
            if (multiplier <= 1.0) {
                plugin.getLogger().warning(where + ".multiplier of " + multiplier
                        + " would do nothing, skipping.");
                continue;
            }
            long duration = Durations.parse(entry.getString("duration", "30m"));
            if (duration <= 0) {
                plugin.getLogger().warning(where + ".duration is not a duration, skipping.");
                continue;
            }
            definitions.put(id.toLowerCase(Locale.ROOT), new ItemDefinition(
                    new BoostItems.Payload(id, types, multiplier, duration),
                    readAppearance(entry.getConfigurationSection("item"))));
        }
    }

    private BoostItems.Appearance readAppearance(ConfigurationSection item) {
        if (item == null) {
            return BoostItems.Appearance.defaults();
        }
        Material material = Items.material(item.getString("material"), Material.EXPERIENCE_BOTTLE);
        List<String> lore = item.isList("lore") ? item.getStringList("lore") : null;
        return new BoostItems.Appearance(
                material,
                item.getString("name"),
                lore == null || lore.isEmpty() ? null : lore,
                Math.max(0, item.getInt("model-data", 0)),
                item.getBoolean("glow", true));
    }

    /** Builds a configured boost item, or null when no such item is configured. */
    public ItemStack createItem(String id, int amount) {
        ItemDefinition definition = definitions.get(id == null
                ? ""
                : id.trim().toLowerCase(Locale.ROOT));
        return definition == null
                ? null
                : items.create(definition.payload(), definition.appearance(), amount);
    }

    public Set<String> itemIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    /**
     * Starts every boost an item's payload describes, for one player.
     *
     * @return the boosts that were started
     */
    public List<Boost> activate(Player player, BoostItems.Payload payload) {
        List<Boost> started = new ArrayList<>();
        for (BoostType type : payload.types()) {
            started.add(addPlayer(player, type, payload.multiplier(),
                    payload.durationMillis(), "item:" + payload.id()));
        }
        return started;
    }

    private void loadWindows(ConfigurationSection section) {
        windows.clear();
        List<?> raw = section == null ? List.of() : section.getList("schedule", List.of());
        for (int i = 0; i < raw.size(); i++) {
            if (!(raw.get(i) instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Window window = readWindow(map, i);
            if (window != null) {
                windows.add(window);
            }
        }
    }

    private Window readWindow(java.util.Map<?, ?> map, int index) {
        String where = "boosts.schedule[" + index + "]";

        List<DayOfWeek> days = new ArrayList<>();
        Object rawDays = map.get("days");
        if (rawDays instanceof List<?> list) {
            for (Object day : list) {
                DayOfWeek parsed = parseDay(String.valueOf(day));
                if (parsed == null) {
                    plugin.getLogger().warning(where + ": unknown day '" + day + "', skipping it.");
                } else {
                    days.add(parsed);
                }
            }
        }
        if (days.isEmpty()) {
            days = List.of(DayOfWeek.values());
        }

        LocalTime start;
        try {
            start = LocalTime.parse(String.valueOf(map.getOrDefault("start", "18:00")));
        } catch (Exception ex) {
            plugin.getLogger().warning(where + ": '" + map.get("start")
                    + "' is not a HH:mm time, skipping this window.");
            return null;
        }

        long duration = Durations.parse(String.valueOf(map.getOrDefault("duration", "1h")));
        if (duration <= 0) {
            plugin.getLogger().warning(where + ": '" + map.get("duration")
                    + "' is not a duration, skipping this window.");
            return null;
        }

        List<BoostType> types = typesFor(String.valueOf(map.getOrDefault("type", "all")));
        if (types.isEmpty()) {
            plugin.getLogger().warning(where + ": '" + map.get("type")
                    + "' names no boost type, skipping this window.");
            return null;
        }

        double multiplier = map.get("multiplier") instanceof Number number
                ? number.doubleValue()
                : 2.0;
        if (multiplier <= 1.0) {
            plugin.getLogger().warning(where + ": multiplier " + multiplier
                    + " would do nothing, skipping this window.");
            return null;
        }
        return new Window(days, start, duration, types, multiplier);
    }

    private static DayOfWeek parseDay(String name) {
        try {
            return DayOfWeek.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The types a config or command word names: one type, or all of them. */
    public static List<BoostType> typesFor(String name) {
        if (BoostType.isAll(name)) {
            return List.of(BoostType.values());
        }
        BoostType type = BoostType.parse(name);
        return type == null ? List.of() : List.of(type);
    }

    // ------------------------------------------------------------------
    // The multiplier
    // ------------------------------------------------------------------

    /**
     * What this player's boosts come to for a type, with 1.0 meaning no change.
     *
     * <p>Every running boost of the type multiplies in — global first, then the
     * player's own — and the result is clamped to the configured ceiling.
     */
    public double multiplier(Player player, BoostType type) {
        long now = System.currentTimeMillis();
        double total = 1.0;
        for (Boost boost : global) {
            if (boost.type() == type && boost.isActive(now)) {
                total *= boost.multiplier();
            }
        }
        if (player != null) {
            PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
            for (Boost boost : profile.activeBoosts(type, now)) {
                total *= boost.multiplier();
            }
        }
        return Math.min(total, maxMultiplier);
    }

    /** The global-only multiplier, for display and for offline senders. */
    public double globalMultiplier(BoostType type) {
        return multiplier(null, type);
    }

    /** Applies this player's multiplier to an amount of items. */
    public long boosted(Player player, BoostType type, long amount) {
        double multiplier = multiplier(player, type);
        return multiplier == 1.0 ? amount : Boost.scale(amount, multiplier);
    }

    // ------------------------------------------------------------------
    // Granting
    // ------------------------------------------------------------------

    /** Starts a server-wide boost and announces it. */
    public Boost addGlobal(BoostType type, double multiplier, long millis, String source) {
        Boost boost = Boost.lasting(type, multiplier, millis, source);
        global.add(boost);
        saveGlobal();
        if (announce) {
            // Told to each player rather than Server#broadcast so the message
            // goes through the same prefix and config path as everything else.
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                plugin.messages().send(online, "boost-global-started",
                        "type", type.display(),
                        "multiplier", Text.decimal(multiplier),
                        "duration", Durations.format(millis));
            }
            plugin.getLogger().info("Global " + Text.decimal(multiplier) + "x "
                    + type.display() + " boost started for " + Durations.format(millis) + ".");
        }
        return boost;
    }

    /** Starts a boost for one player. */
    public Boost addPlayer(Player player, BoostType type, double multiplier, long millis, String source) {
        Boost boost = Boost.lasting(type, multiplier, millis, source);
        plugin.profiles().get(player.getUniqueId()).addBoost(boost);
        return boost;
    }

    /** Ends every global boost. Returns how many were running. */
    public int clearGlobal() {
        long now = System.currentTimeMillis();
        int running = 0;
        for (Boost boost : global) {
            if (boost.isActive(now)) {
                running++;
            }
        }
        global.clear();
        saveGlobal();
        return running;
    }

    /** Ends every boost a player is personally running. */
    public int clearPlayer(UUID uuid) {
        PlayerProfile profile = plugin.profiles().get(uuid);
        long now = System.currentTimeMillis();
        int running = profile.activeBoosts(BoostType.DROPS, now).size()
                + profile.activeBoosts(BoostType.COLLECTIONS, now).size();
        profile.clearBoosts();
        return running;
    }

    public List<Boost> globalBoosts() {
        long now = System.currentTimeMillis();
        List<Boost> active = new ArrayList<>();
        for (Boost boost : global) {
            if (boost.isActive(now)) {
                active.add(boost);
            }
        }
        return Collections.unmodifiableList(active);
    }

    public boolean oresOnly() {
        return oresOnly;
    }

    public double maxMultiplier() {
        return maxMultiplier;
    }

    // ------------------------------------------------------------------
    // Scheduling and expiry
    // ------------------------------------------------------------------

    /**
     * Drops expired global boosts and opens any scheduled window that is due.
     *
     * <p>A window is identified by its config index, so re-entering the same
     * window while its boost is still running is a no-op — the check can run
     * every few seconds without stacking a boost on itself, and a restart
     * mid-window picks the window back up rather than skipping it.
     */
    private void tick() {
        long now = System.currentTimeMillis();
        if (global.removeIf(boost -> !boost.isActive(now))) {
            saveGlobal();
        }
        if (windows.isEmpty()) {
            return;
        }
        LocalDateTime local = LocalDateTime.now(zone);
        for (int i = 0; i < windows.size(); i++) {
            Window window = windows.get(i);
            String source = "schedule:" + i;
            long remaining = remainingInWindow(window, local);
            if (remaining <= 0 || isRunning(source, now)) {
                continue;
            }
            for (BoostType type : window.types()) {
                addGlobal(type, window.multiplier(), remaining, source);
            }
        }
    }

    /** How long is left of this window right now, or 0 when it is not open. */
    private long remainingInWindow(Window window, LocalDateTime now) {
        // Check today and yesterday, so a window that starts at 23:00 and runs
        // for three hours is still open at 01:00 the next morning.
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            LocalDateTime day = now.minusDays(dayOffset);
            if (!window.days().contains(day.getDayOfWeek())) {
                continue;
            }
            LocalDateTime opens = day.toLocalDate().atTime(window.start());
            LocalDateTime closes = opens.plus(Duration.ofMillis(window.durationMillis()));
            if (!now.isBefore(opens) && now.isBefore(closes)) {
                return Duration.between(now, closes).toMillis();
            }
        }
        return 0;
    }

    private boolean isRunning(String source, long now) {
        for (Boost boost : global) {
            if (source.equals(boost.source()) && boost.isActive(now)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Global persistence
    // ------------------------------------------------------------------

    private File globalFile() {
        return new File(plugin.getDataFolder(), "boosts.yml");
    }

    private void loadGlobal() {
        global.clear();
        File file = globalFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read boosts.yml", ex);
            return;
        }
        long now = System.currentTimeMillis();
        ConfigurationSection section = config.getConfigurationSection("global");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            BoostType type = BoostType.parse(entry.getString("type"));
            if (type == null) {
                continue;
            }
            Boost boost = new Boost(type,
                    entry.getDouble("multiplier", 1.0),
                    entry.getLong("expires"),
                    entry.getString("source", ""));
            if (boost.isActive(now)) {
                global.add(boost);
            }
        }
    }

    private void saveGlobal() {
        YamlConfiguration config = new YamlConfiguration();
        long now = System.currentTimeMillis();
        int index = 0;
        for (Boost boost : global) {
            if (!boost.isActive(now)) {
                continue;
            }
            String base = "global." + index++ + ".";
            config.set(base + "type", boost.type().id());
            config.set(base + "multiplier", boost.multiplier());
            config.set(base + "expires", boost.expiresAt());
            config.set(base + "source", boost.source());
        }
        try {
            File folder = plugin.getDataFolder();
            if (folder.exists() || folder.mkdirs()) {
                config.save(globalFile());
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write boosts.yml", ex);
        }
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /** One line per running boost, for chat and menus. */
    public List<String> summaryFor(Player player) {
        long now = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        for (BoostType type : BoostType.values()) {
            double total = multiplier(player, type);
            if (total <= 1.0) {
                continue;
            }
            long soonest = Long.MAX_VALUE;
            for (Boost boost : global) {
                if (boost.type() == type && boost.isActive(now)) {
                    soonest = Math.min(soonest, boost.remainingMillis(now));
                }
            }
            if (player != null) {
                for (Boost boost : plugin.profiles()
                        .get(player.getUniqueId()).activeBoosts(type, now)) {
                    soonest = Math.min(soonest, boost.remainingMillis(now));
                }
            }
            lines.add("<gray>" + type.display() + ": <white>" + Text.decimal(total)
                    + "x <dark_gray>(" + Durations.format(soonest) + " left)");
        }
        return lines;
    }

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(16, Material.EXPERIENCE_BOTTLE,
                "<light_purple><bold>Boosts",
                List.of("<gray>Temporary multipliers on", "<gray>drops and collections."),
                player -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("");
                    List<String> active = summaryFor(player);
                    if (active.isEmpty()) {
                        lines.add("<dark_gray>Nothing running.");
                    } else {
                        lines.addAll(active);
                    }
                    return lines;
                },
                player -> {
                    List<String> active = summaryFor(player);
                    if (active.isEmpty()) {
                        plugin.messages().send(player, "boost-none");
                        return;
                    }
                    plugin.messages().sendLiteral(player, "<gray>Boosts running:");
                    for (String line : active) {
                        plugin.messages().sendPlain(player, "  " + line);
                    }
                });
    }

    @Override
    public List<String> statusLines() {
        List<Boost> active = globalBoosts();
        return List.of(active.size() + " global boost(s), " + windows.size()
                + " scheduled window(s), cap " + Text.decimal(maxMultiplier) + "x");
    }
}
