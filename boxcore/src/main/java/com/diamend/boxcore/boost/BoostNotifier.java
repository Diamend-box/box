package com.diamend.boxcore.boost;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells players what their boosts are doing: a live actionbar while one runs, a
 * warning before it ends, and a line when it does.
 *
 * <p>A boost that ends silently is the standard complaint about boost plugins —
 * the player only finds out from a drop rate that quietly went back to normal,
 * which reads as the server cheating them.
 *
 * <p>Expiry is spotted by comparing each pass against the last one rather than
 * by scheduling a task per boost, so a boost that ends while its owner is
 * offline costs nothing and produces no message for nobody. Only players who
 * were online on the previous pass can be told a boost ended, which is exactly
 * the set of people for whom the news is still news.
 */
public class BoostNotifier {

    /** Below this the actionbar rewrites faster than a client redraws it. */
    private static final long MIN_INTERVAL = 10L;

    private final BoxCorePlugin plugin;
    private final BoostsModule module;

    private boolean actionbar = true;
    private boolean chat = true;
    private long warnMillis = 60_000L;
    private long intervalTicks = 20L;

    private BukkitTask task;

    /** What each online player had running last pass, keyed by boost. */
    private Map<UUID, Map<String, Boost>> lastSeen = new HashMap<>();
    private Map<String, Boost> lastGlobal = new LinkedHashMap<>();

    /** Boosts already warned about, so the warning is sent once each. */
    private final Map<UUID, Set<String>> warned = new HashMap<>();
    private final Set<String> warnedGlobal = new HashSet<>();

    /** Players whose actionbar is being left alone for a moment. */
    private final Map<UUID, Long> quiet = new HashMap<>();

    public BoostNotifier(BoxCorePlugin plugin, BoostsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Reads config and (re)schedules the pass. Safe to call repeatedly. */
    public void start() {
        stop();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("boosts.notify");
        actionbar = section == null || section.getBoolean("actionbar", true);
        chat = section == null || section.getBoolean("chat", true);
        warnMillis = Math.max(0L, section == null ? 60L : section.getLong("warn-seconds", 60L)) * 1000L;
        intervalTicks = Math.max(MIN_INTERVAL,
                section == null ? 20L : section.getLong("interval-ticks", 20L));
        if (!actionbar && !chat) {
            return; // nothing to say; don't burn a timer saying it
        }
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastSeen = new HashMap<>();
        lastGlobal = new LinkedHashMap<>();
        warned.clear();
        warnedGlobal.clear();
    }

    /**
     * Holds this player's boost actionbar back for a moment.
     *
     * <p>Called by anything else that wants the actionbar — without it a boost
     * countdown ticking every second would wipe out a "skill point earned" line
     * before it could be read.
     */
    public void hold(Player player, long millis) {
        if (player != null && millis > 0) {
            quiet.put(player.getUniqueId(), System.currentTimeMillis() + millis);
        }
    }

    // ------------------------------------------------------------------
    // The pass
    // ------------------------------------------------------------------

    /**
     * One pass: warn, announce what ended, and refresh the actionbar.
     *
     * <p>Public so a test can drive a pass without waiting on the scheduler.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        globalPass(now);

        Map<UUID, Map<String, Boost>> current = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerProfile profile = plugin.profiles().get(uuid);

            Map<String, Boost> active = new LinkedHashMap<>();
            for (BoostType type : BoostType.values()) {
                for (Boost boost : profile.activeBoosts(type, now)) {
                    active.put(key(boost), boost);
                }
            }
            if (chat) {
                announceEnded(player, lastSeen.get(uuid), active);
                warn(player, uuid, lastSeen.get(uuid), active, now);
            }
            if (actionbar && !isQuiet(uuid, now)) {
                showActionBar(player, profile, now);
            }
            current.put(uuid, active);
        }
        lastSeen = current;
        warned.keySet().retainAll(current.keySet());
        quiet.keySet().retainAll(current.keySet());
    }

    private void announceEnded(Player player, Map<String, Boost> previous, Map<String, Boost> active) {
        if (previous == null) {
            return; // first sight of this player; nothing to compare against
        }
        for (Map.Entry<String, Boost> entry : previous.entrySet()) {
            if (!active.containsKey(entry.getKey())) {
                plugin.messages().send(player, "boost-ended",
                        "type", entry.getValue().type().display(),
                        "multiplier", Text.decimal(entry.getValue().multiplier()));
            }
        }
    }

    private void warn(Player player, UUID uuid, Map<String, Boost> previous,
                      Map<String, Boost> active, long now) {
        if (warnMillis <= 0) {
            return;
        }
        Set<String> already = warned.computeIfAbsent(uuid, id -> new HashSet<>());
        already.retainAll(active.keySet());
        for (Map.Entry<String, Boost> entry : active.entrySet()) {
            Boost boost = entry.getValue();
            long left = boost.remainingMillis(now);
            if (left > warnMillis || already.contains(entry.getKey())) {
                continue;
            }
            already.add(entry.getKey());
            if (previous == null || !previous.containsKey(entry.getKey())) {
                continue; // it was already inside the warning window when it started
            }
            plugin.messages().send(player, "boost-ending",
                    "type", boost.type().display(),
                    "multiplier", Text.decimal(boost.multiplier()),
                    "time", Durations.format(left));
        }
    }

    /** The same two checks for server-wide boosts, told to everyone online. */
    private void globalPass(long now) {
        Map<String, Boost> active = new LinkedHashMap<>();
        for (Boost boost : module.globalBoosts()) {
            active.put(key(boost), boost);
        }
        if (chat) {
            for (Map.Entry<String, Boost> entry : lastGlobal.entrySet()) {
                if (!active.containsKey(entry.getKey())) {
                    broadcast("boost-global-ended",
                            "type", entry.getValue().type().display(),
                            "multiplier", Text.decimal(entry.getValue().multiplier()));
                }
            }
            if (warnMillis > 0) {
                warnedGlobal.retainAll(active.keySet());
                for (Map.Entry<String, Boost> entry : active.entrySet()) {
                    Boost boost = entry.getValue();
                    long left = boost.remainingMillis(now);
                    if (left > warnMillis || !warnedGlobal.add(entry.getKey())) {
                        continue;
                    }
                    if (lastGlobal.containsKey(entry.getKey())) {
                        broadcast("boost-global-ending",
                                "type", boost.type().display(),
                                "multiplier", Text.decimal(boost.multiplier()),
                                "time", Durations.format(left));
                    }
                }
            }
        }
        lastGlobal = active;
    }

    private void broadcast(String path, Object... placeholders) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            plugin.messages().send(online, path, placeholders);
        }
    }

    private void showActionBar(Player player, PlayerProfile profile, long now) {
        List<String> parts = new ArrayList<>();
        for (BoostType type : BoostType.values()) {
            double total = module.multiplierFor(profile, type);
            if (total <= 1.0) {
                continue;
            }
            parts.add("<light_purple>" + Text.decimal(total) + "x <white>" + type.display()
                    + " <gray>" + Durations.format(module.remainingFor(profile, type)));
        }
        if (parts.isEmpty()) {
            return;
        }
        player.sendActionBar(Text.parse(String.join(" <dark_gray>| ", parts)));
    }

    private boolean isQuiet(UUID uuid, long now) {
        Long until = quiet.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            quiet.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Identifies one running boost. Its end time is what makes it distinct — two
     * boosts of the same type ending in the same millisecond are, for the
     * purpose of telling someone about them, the same boost.
     */
    private static String key(Boost boost) {
        return boost.type().id() + "@" + boost.expiresAt();
    }
}
