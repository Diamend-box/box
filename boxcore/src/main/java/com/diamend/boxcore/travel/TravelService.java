package com.diamend.boxcore.travel;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Sounds;
import com.diamend.boxcore.util.Text;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starting, watching and finishing a trip.
 *
 * <p>A trip is a warmup rather than an instant teleport, and the warmup is the
 * feature's whole safety model: it cancels the moment you take damage or step
 * off the block you started on, and it refuses to start at all while you're
 * combat-tagged. Everything else here is bookkeeping around those two rules.
 */
public class TravelService {

    /** Why a trip stopped, so the right thing gets said. */
    public enum Outcome {
        STARTED,
        ARRIVED,
        IN_COMBAT,
        NO_PERMISSION,
        NOT_FOUND_YET,
        ALREADY_TRAVELLING,
        UNKNOWN_DESTINATION,
        MOVED,
        HURT,
        CANCELLED
    }

    /**
     * One trip in progress.
     *
     * @param onArrive run once the player is actually there, or null. This is
     *                 how a travel ticket gets spent: on arrival rather than on
     *                 use, so a trip cut short by a sword doesn't cost the
     *                 ticket as well as the fight.
     */
    private record Trip(Warp warp, Location from, int secondsLeft, BukkitTask task,
                        Runnable onArrive) {
    }

    private final BoxCorePlugin plugin;
    private final CombatTagger combat;

    private final Map<UUID, Trip> trips = new ConcurrentHashMap<>();

    private int warmupSeconds = 3;
    private boolean cancelOnMove = true;
    private boolean sounds = true;

    public TravelService(BoxCorePlugin plugin, CombatTagger combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    public void configure(int warmupSeconds, boolean cancelOnMove) {
        this.warmupSeconds = Math.max(0, warmupSeconds);
        this.cancelOnMove = cancelOnMove;
    }

    public void setSounds(boolean sounds) {
        this.sounds = sounds;
    }

    /**
     * Feedback for a trip.
     *
     * <p>The warmup is a few seconds of standing still with an actionbar line
     * you may well not be looking at. A sound is what tells you it started, that
     * it is still going, and — the one that matters in a fight — that it just
     * stopped.
     */
    private void sound(Player player, Sound sound, float volume, float pitch) {
        if (sounds) {
            Sounds.play(player, sound, volume, pitch);
        }
    }

    public int warmupSeconds() {
        return warmupSeconds;
    }

    public CombatTagger combat() {
        return combat;
    }

    public boolean isTravelling(Player player) {
        return player != null && trips.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------

    /**
     * Begins a trip, or explains why it can't.
     *
     * <p>A zero warmup arrives immediately — a server that doesn't want the
     * delay shouldn't pay for a scheduled task to deliver it.
     */
    public Outcome begin(Player player, Warp warp) {
        return begin(player, warp, null, false);
    }

    /**
     * Begins a trip somebody paid for.
     *
     * @param onArrive         run when they get there, so the thing that bought
     *                         the trip is spent on arrival rather than on use
     * @param ignorePermission whether to skip the warp's permission check —
     *                         true for a ticket, because holding the ticket
     *                         <em>is</em> the permission. Neither the combat
     *                         check nor the found check is ever skipped: a
     *                         ticket that teleports you out of a fight would be
     *                         worth more as an escape than as travel, and one
     *                         that takes you somewhere you have never found
     *                         would sell the reward for finding it.
     */
    public Outcome begin(Player player, Warp warp, Runnable onArrive, boolean ignorePermission) {
        if (player == null || warp == null) {
            return Outcome.UNKNOWN_DESTINATION;
        }
        if (!ignorePermission && !warp.allows(player)) {
            return Outcome.NO_PERMISSION;
        }
        if (!plugin.profiles().get(player.getUniqueId()).hasDiscovered(warp.id())) {
            // The gate for the whole feature: somewhere has to be on your list
            // before anything can take you to it. Checked here rather than at
            // each caller so no future route round the back can skip it.
            plugin.messages().send(player, "travel-not-found",
                    "warp", Text.plain(warp.display()));
            sound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return Outcome.NOT_FOUND_YET;
        }
        if (combat.isTagged(player)) {
            plugin.messages().send(player, "travel-in-combat",
                    "time", Durations.format(combat.remainingMillis(player)));
            sound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return Outcome.IN_COMBAT;
        }
        if (isTravelling(player)) {
            return Outcome.ALREADY_TRAVELLING;
        }
        if (warmupSeconds <= 0) {
            arrive(player, warp, onArrive);
            return Outcome.ARRIVED;
        }

        UUID uuid = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> tick(uuid), 20L, 20L);
        trips.put(uuid, new Trip(warp, player.getLocation().clone(), warmupSeconds, task,
                onArrive));
        showCountdown(player, warp, warmupSeconds);
        plugin.messages().send(player, "travel-starting",
                "warp", Text.plain(warp.display()),
                "seconds", warmupSeconds);
        return Outcome.STARTED;
    }

    private void tick(UUID uuid) {
        Trip trip = trips.get(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (trip == null) {
            return;
        }
        if (player == null || !player.isOnline()) {
            stop(uuid);
            return;
        }
        int left = trip.secondsLeft() - 1;
        if (left <= 0) {
            stop(uuid);
            arrive(player, trip.warp(), trip.onArrive());
            return;
        }
        trips.put(uuid, new Trip(trip.warp(), trip.from(), left, trip.task(), trip.onArrive()));
        showCountdown(player, trip.warp(), left);
    }

    /**
     * Puts the countdown on the actionbar, and asks the boost countdown to hold
     * off — two things writing there once a second means neither is readable.
     */
    private void showCountdown(Player player, Warp warp, int secondsLeft) {
        player.sendActionBar(Text.parse("<aqua>Travelling to <white>"
                + Text.plain(warp.display()) + "</white> — <yellow>" + secondsLeft + "s"));
        // A tick per second, climbing as you get closer, so the trip is audible
        // to you and to anyone stood next to you deciding whether to hit you.
        double done = warmupSeconds <= 0 ? 1.0
                : (warmupSeconds - secondsLeft) / (double) warmupSeconds;
        sound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, (float) (0.9 + 0.8 * done));
        if (plugin.boosts() != null) {
            plugin.boosts().notifier().hold(player, 1500L);
        }
    }

    // ------------------------------------------------------------------
    // Stopping
    // ------------------------------------------------------------------

    /** Ends a trip without arriving, telling the player why. */
    public void cancel(Player player, Outcome reason) {
        if (player == null || !isTravelling(player)) {
            return;
        }
        stop(player.getUniqueId());
        String path = switch (reason) {
            case MOVED -> "travel-cancelled-moved";
            case HURT -> "travel-cancelled-hurt";
            default -> "travel-cancelled";
        };
        plugin.messages().send(player, path);
        player.sendActionBar(Text.parse("<red>Travel cancelled."));
        sound(player, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.2f);
    }

    private void stop(UUID uuid) {
        Trip trip = trips.remove(uuid);
        if (trip != null && trip.task() != null) {
            trip.task().cancel();
        }
    }

    /** Drops a trip silently — for quits, where nobody is left to tell. */
    public void forget(UUID uuid) {
        stop(uuid);
        combat.forget(uuid);
    }

    public void clear() {
        for (UUID uuid : Map.copyOf(trips).keySet()) {
            stop(uuid);
        }
        combat.clear();
    }

    private void arrive(Player player, Warp warp, Runnable onArrive) {
        Location to = warp.location();
        if (to == null || to.getWorld() == null) {
            // Nothing happened, so nothing is spent — whatever bought this trip
            // is still in their inventory to try again with.
            plugin.messages().send(player, "travel-unavailable",
                    "warp", Text.plain(warp.display()));
            return;
        }
        player.teleport(to);
        if (onArrive != null) {
            onArrive.run();
        }
        plugin.messages().send(player, "travel-arrived", "warp", Text.plain(warp.display()));
        player.sendActionBar(Text.parse("<green>Arrived at <white>"
                + Text.plain(warp.display())));
        sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------
    // What the listener reports
    // ------------------------------------------------------------------

    /** Called when the player takes damage of any kind. */
    public void onHurt(Player player) {
        if (isTravelling(player)) {
            cancel(player, Outcome.HURT);
        }
    }

    /**
     * Called when the player moves. Only a change of block counts: standing
     * still in Minecraft still produces a stream of move events from looking
     * around, and cancelling a trip because someone turned their head would be
     * indistinguishable from a bug.
     */
    public void onMove(Player player, Location to) {
        if (!cancelOnMove || !isTravelling(player)) {
            return;
        }
        Trip trip = trips.get(player.getUniqueId());
        if (trip == null || to == null) {
            return;
        }
        Location from = trip.from();
        if (from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            cancel(player, Outcome.MOVED);
        }
    }
}
