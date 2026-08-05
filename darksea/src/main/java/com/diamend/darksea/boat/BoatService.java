package com.diamend.darksea.boat;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.config.DarkSeaSettings.BoatLevel;
import com.diamend.darksea.data.PlayerDataStore;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.relic.Relic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player boat level: speed scaling while sailing the Dark Sea and a
 * shield value folded into the exposure formula so an upgraded boat lets
 * you scout one ring farther than your armor alone.
 *
 * Boats aren't living entities (no movement-speed attribute), so speed is
 * applied by scaling horizontal velocity on vehicle movement, capped at
 * {@code speed-cap-base × multiplier} blocks/tick so repeated scaling can
 * never run away.
 */
public final class BoatService implements Listener {

    /** PDC tag on a boat entity: the UUID string of the player who owns it. */
    static final NamespacedKey OWNER_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:boat_owner"));

    private final DarkSeaPlugin plugin;
    private final PlayerDataStore data;
    /** Tidal Draught expiry timestamps; stale entries lapse on their own. */
    private final Map<UUID, Long> draughts = new ConcurrentHashMap<>();
    /** Carried thrust per rider, so the boost ramps and coasts instead of snapping. */
    private final Map<UUID, Drive> drives = new ConcurrentHashMap<>();

    public BoatService(DarkSeaPlugin plugin, PlayerDataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** A drunk Tidal Draught: temporary extra boat speed. */
    public void addDraught(Player player) {
        draughts.put(player.getUniqueId(),
                System.currentTimeMillis() + DarkSeaItems.DRAUGHT_SECONDS * 1000L);
    }

    private boolean draughtActive(Player player) {
        Long until = draughts.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            draughts.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * The rider's total speed multiplier: boat level, times the Harbor
     * Bell's Homeward Wind if that relic is awake and active, times a
     * running Tidal Draught. The velocity cap scales with the same number,
     * so boosts never break the anti-runaway clamp.
     */
    public double effectiveMultiplier(Player rider) {
        double multiplier = stats(levelOf(rider)).speed()
                + statPoints(rider).speed() * pts().speedPerPoint();
        if (plugin.relics() != null && plugin.relics().isActive(rider, Relic.Boost.BOAT)) {
            multiplier *= Relic.BOAT_BOOST_MULTIPLIER;
        }
        if (draughtActive(rider)) {
            multiplier *= DarkSeaItems.DRAUGHT_MULTIPLIER;
        }
        return multiplier;
    }

    public int levelOf(Player player) {
        return data.boatLevel(player.getUniqueId());
    }

    public void setLevel(Player player, int level) {
        data.setBoatLevel(player.getUniqueId(), level);
    }

    public BoatLevel stats(int level) {
        DarkSeaSettings.BoatSettings boat = plugin.settings().boat();
        BoatLevel stats = boat.levels().get(level);
        return stats != null ? stats : boat.levels().get(0);
    }

    // ------------------------------------------------------------------
    // Custom stat points: one per boat level, spent in the Outfit page
    // ------------------------------------------------------------------

    /** The four stats a captain can pour custom points into (shield stays tier-only). */
    public enum StatId { SPEED, TOUGHNESS, HP, RAM_POWER }

    private DarkSeaSettings.StatPointSettings pts() {
        return plugin.settings().boat().statPoints();
    }

    public PlayerDataStore.StatPoints statPoints(Player player) {
        return data.statPoints(player.getUniqueId());
    }

    /** Points earned so far: one per boat level. */
    public int statPointsEarned(Player player) {
        return levelOf(player);
    }

    /** Points earned but not yet committed to a stat. */
    public int statPointsFree(Player player) {
        return Math.max(0, statPointsEarned(player) - statPoints(player).total());
    }

    /** Spend one free point on a stat; false (no-op) if none are free. */
    public boolean allocate(Player player, StatId stat) {
        if (statPointsFree(player) <= 0) {
            return false;
        }
        PlayerDataStore.StatPoints p = statPoints(player);
        PlayerDataStore.StatPoints next = switch (stat) {
            case SPEED -> new PlayerDataStore.StatPoints(p.speed() + 1, p.toughness(), p.hp(), p.ramPower());
            case TOUGHNESS -> new PlayerDataStore.StatPoints(p.speed(), p.toughness() + 1, p.hp(), p.ramPower());
            case HP -> new PlayerDataStore.StatPoints(p.speed(), p.toughness(), p.hp() + 1, p.ramPower());
            case RAM_POWER -> new PlayerDataStore.StatPoints(p.speed(), p.toughness(), p.hp(), p.ramPower() + 1);
        };
        data.setStatPoints(player.getUniqueId(), next);
        return true;
    }

    /** The Chronon price to respec: per-point cost times points committed. */
    public int resetCost(Player player) {
        return statPoints(player).total() * pts().resetCostPerPoint();
    }

    /** Clears all committed points back to free (caller has billed the Chronons). */
    public void resetPoints(Player player) {
        data.setStatPoints(player.getUniqueId(), PlayerDataStore.StatPoints.ZERO);
    }

    /** Toughness with the rider's allocated points folded in. */
    public double effectiveToughness(Player player) {
        return stats(levelOf(player)).toughness()
                + statPoints(player).toughness() * pts().toughnessPerPoint();
    }

    /** Extra hull HP from the rider's allocated points. */
    public double hpBonus(Player player) {
        return statPoints(player).hp() * pts().hpPerPoint();
    }

    /** Extra ram power from the rider's allocated points. */
    public double ramPowerBonus(Player player) {
        return statPoints(player).ramPower() * pts().ramPowerPerPoint();
    }

    /** Boat shield for the exposure formula: only while riding a boat in the sea. */
    public int shieldFor(Player player) {
        if (player.getVehicle() instanceof Boat boat
                && plugin.isDarkSea(boat.getWorld())) {
            return stats(levelOf(player)).shield();
        }
        return 0;
    }

    /** Outcome of a boat-upgrade attempt — the pure decision, minus effects. */
    public enum UpgradeResult {
        /** The held token was the next level; the boat is raised. */
        UPGRADED,
        /** Already at the highest configured level. */
        AT_MAX,
        /** The held token isn't the exact next level (tokens apply in sequence). */
        WRONG_TOKEN
    }

    /**
     * Whether a held token upgrades the boat, given the current level, the
     * token's level (0 = not a token) and whether a next level exists. Tokens
     * only apply in sequence: a level-3 token does nothing on a level-1 boat.
     */
    public static UpgradeResult evaluateUpgrade(int currentLevel, int tokenLevel, boolean nextLevelExists) {
        if (!nextLevelExists) {
            return UpgradeResult.AT_MAX;
        }
        return tokenLevel == currentLevel + 1 ? UpgradeResult.UPGRADED : UpgradeResult.WRONG_TOKEN;
    }

    /**
     * Consume a matching upgrade token from anywhere in the inventory and raise
     * the boat level by one. Tokens are per-level and only apply in sequence.
     * Scanning the whole pack (not just the main hand) lets the boat wheel's
     * Upgrade button and {@code /ds boat upgrade} share this one path.
     */
    public void upgrade(Player player) {
        int current = levelOf(player);
        int next = current + 1;
        boolean nextExists = plugin.settings().boat().levels().containsKey(next);
        int slot = findTokenSlot(player, next);
        int tokenLevel = slot >= 0 ? next : 0;  // 0 = no matching token in the pack
        switch (evaluateUpgrade(current, tokenLevel, nextExists)) {
            case AT_MAX -> plugin.messages().send(player, "boat-max");
            case WRONG_TOKEN ->
                    plugin.messages().send(player, "boat-need-token", "level", String.valueOf(next));
            case UPGRADED -> {
                ItemStack token = player.getInventory().getItem(slot);
                token.setAmount(token.getAmount() - 1);
                setLevel(player, next);
                plugin.messages().send(player, "boat-upgraded",
                        "name", stats(next).name(), "level", String.valueOf(next));
            }
        }
    }

    /** The first inventory slot holding a boat token of {@code level}, or -1. */
    private int findTokenSlot(Player player, int level) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (SeaArmor.tokenLevelOf(contents[i]) == level) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the player carries a boat token of the given level anywhere. */
    public boolean hasUpgradeToken(Player player, int level) {
        return findTokenSlot(player, level) >= 0;
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (!plugin.isDarkSea(boat.getWorld())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider == null) {
            return;
        }
        double multiplier = effectiveMultiplier(rider);
        double wounded = plugin.naval() != null ? plugin.naval().speedCeiling(boat) : 1.0;

        // Measured from the move itself, NOT from getVelocity(). A boat with
        // a rider is driven by that rider's client, which reports positions;
        // the server's velocity vector stays near zero the whole voyage. Read
        // that way every boat looked stationary, boostFactor's "nearly still"
        // guard tripped on every tick, and no boat has ever been faster than
        // vanilla at any level — which is exactly what the second live test
        // found with a Maelstrom under it.
        Vector step = event.getTo().toVector().subtract(event.getFrom().toVector());
        double horizontal = Math.hypot(step.getX(), step.getZ());
        double factor = speedFactor(multiplier, horizontal,
                plugin.settings().boat().speedCapBase(), wounded);
        Vector velocity = boat.getVelocity();
        if (factor < 1.0) {
            // Braking a wounded hull. A plain scale of the existing motion, so
            // it slows without being steered — which way a damaged boat points
            // is still the rider's business, and this has to apply whether or
            // not they are driving or a wreck could coast at full speed.
            double x = (Math.abs(step.getX()) > 1e-6 ? step.getX() : velocity.getX()) * factor;
            double z = (Math.abs(step.getZ()) > 1e-6 ? step.getZ() : velocity.getZ()) * factor;
            boat.setVelocity(new Vector(x, velocity.getY(), z));
            return;
        }

        if (multiplier <= 1.0) {
            drives.remove(rider.getUniqueId());
            return;   // an unupgraded hull is left entirely to vanilla
        }

        // Thrust is a value the server keeps, not one it re-derives from what
        // it just watched the boat do. Nothing measured feeds into it.
        //
        // Two separate things were shaking the boat. The obvious one: the
        // target was the measured step scaled up, and the step is noisy,
        // because the client owns a ridden boat's physics — so the speed being
        // written back wobbled several times a second. The worse one was
        // hiding in boostFactor, which returns 1.0 ("leave it alone") once the
        // boat is at its cap. At cruise that alternated: a tick over the cap
        // was ignored and the hull slowed, the next tick was under and got
        // pushed, forever. The boat was being shoved and dropped a few times a
        // second by design, which is precisely what juddering is.
        //
        // So the target is now the cap itself — a constant for a given hull —
        // and the ramp toward it is monotone. Same speed, but arrived at
        // smoothly and then *held*, with no per-tick decision to make.
        double cap = plugin.settings().boat().speedCapBase() * multiplier * wounded;
        Drive drive = drives.computeIfAbsent(rider.getUniqueId(), id -> new Drive());
        drive.speed = driveStep(drive.speed, cap, rider.getCurrentInput().isForward());
        if (drive.speed <= RELEASE_SPEED) {
            // Nearly stopped: hand the boat back. Every tick we do not touch
            // is a tick the client is not being corrected, which is most of
            // what "smooth" means here.
            //
            // This used to hand back at the hull's *unaided* cap, and that was
            // the abrupt stop at the end of a coast. A rider who has let go of
            // W is giving the client no forward input, so the client's own
            // speed has already decayed to nothing — handing over at cruise
            // speed dropped the boat from cruise to a standstill in one tick.
            // Coasting the whole way down to a crawl means there is no step
            // left to fall off.
            drives.remove(rider.getUniqueId());
            return;
        }
        // Along the hull, not along the momentum. Scaling the measured step
        // accelerated the boat in the direction it was *already* travelling, so
        // a turn fought a thrust vector still pointing down the old heading and
        // the boat ploughed straight on through the corner — which is what made
        // a Maelstrom impossible to steer. Pushing along the boat's own facing
        // means turning the boat turns the thrust.
        Vector heading = boat.getLocation().getDirection().setY(0.0);
        if (heading.lengthSquared() < 1.0e-9) {
            return;
        }
        heading.normalize().multiply(drive.speed);
        boat.setVelocity(new Vector(heading.getX(), velocity.getY(), heading.getZ()));
    }

    /** How much of the gap to the target speed a tick of holding forward closes. */
    private static final double THROTTLE_UP = 0.22;

    /**
     * And how much of it a tick of not holding forward gives back. Sized so a
     * boat at cruise reaches {@link #RELEASE_SPEED} in a little over a second:
     * the coast now runs all the way to a crawl rather than stopping at the
     * hull's own cap, so a gentler rate would read as a boat that will not
     * slow down.
     */
    private static final double COAST_DOWN = 0.12;

    /** How fast speed above the cap — only a surge puts it there — bleeds off. */
    private static final double SURGE_BLEED = 0.05;

    /**
     * The speed at which a coasting boat is handed back to the client. Low
     * enough that the handover is not a step you can feel: whatever the client
     * is doing with a boat this slow, it is within a rounding error of what we
     * were writing.
     */
    static final double RELEASE_SPEED = 0.03;

    /**
     * One tick of the throttle: eases the carried speed toward the hull's cap
     * while forward is held, and toward a standstill when it is not.
     *
     * <p>Speed above the cap is only ever put there by a surge, and it bleeds
     * off more slowly than the throttle closes a gap — otherwise the burst
     * would be gone in three ticks and the surge would read as a stutter
     * rather than a shove. It decays rather than being clamped, so a surge is
     * something the boat carries out of and not something snapped away from
     * it.
     */
    static double driveStep(double current, double cap, boolean forward) {
        if (!forward) {
            return current * (1.0 - COAST_DOWN);
        }
        double rate = current > cap ? SURGE_BLEED : THROTTLE_UP;
        return current + (cap - current) * rate;
    }

    /**
     * Hands the throttle a speed from outside — the naval surge, which is a
     * one-off burst rather than a state of its own.
     *
     * <p>Without this the surge did nothing you could feel for more than a
     * tick: it set the boat's velocity, and then the very next movement tick
     * this service wrote the carried speed straight back over it. The burst
     * was being cancelled by the throttle a fortieth of a second later, which
     * is what "the surge resets momentum" is. Feeding it in here instead makes
     * the burst the throttle's new starting point, so it bleeds back down to
     * cruise the way a shove should.
     */
    public void primeDrive(Player player, double speed) {
        Drive drive = drives.computeIfAbsent(player.getUniqueId(), id -> new Drive());
        drive.speed = Math.max(drive.speed, speed);
    }

    /** One rider's carried thrust. Lives only while they are sailing. */
    private static final class Drive {
        private double speed;
    }

    /** Stepping off the boat ends the run; nothing should carry to the next one. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            drives.remove(player.getUniqueId());
        }
    }

    /**
     * The full per-tick velocity scale: {@link #boostFactor} toward a cap
     * shrunk by the wounded-hull factor, plus the one case where a boat is
     * actively slowed — a wounded hull over its reduced cap gets dragged
     * down to it. A healthy boat is never braked (vanilla streams and drops
     * may legitimately exceed the cap).
     */
    static double speedFactor(double multiplier, double horizontalSpeed,
                              double speedCapBase, double woundedFactor) {
        double cap = speedCapBase * multiplier * woundedFactor;
        if (woundedFactor < 1.0 && horizontalSpeed > cap && horizontalSpeed >= 1e-3) {
            return cap / horizontalSpeed;
        }
        return boostFactor(multiplier, horizontalSpeed, speedCapBase * woundedFactor);
    }

    /**
     * The per-tick velocity scale for a boosted boat: it accelerates toward
     * the cap ({@code speedCapBase × multiplier} blocks/tick) but never past
     * it, and never by more than the multiplier itself in one tick. Returns
     * 1.0 (leave velocity alone) when there's no boost, the boat is nearly
     * still, or it's already at or over the cap — so repeated scaling can
     * never run away.
     */
    static double boostFactor(double multiplier, double horizontalSpeed, double speedCapBase) {
        if (multiplier <= 1.0 || horizontalSpeed < 1e-3) {
            return 1.0;
        }
        double cap = speedCapBase * multiplier;
        double factor = Math.min(multiplier, cap / horizontalSpeed);
        return factor > 1.0 ? factor : 1.0;
    }

    /**
     * Hull toughness in PvP: a ridden boat in the Dark Sea takes a fraction of
     * incoming damage set by the rider's boat level, so a higher boat rides out
     * more hits before it's sunk. Empty boats and boats outside the sea take
     * damage normally.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)
                || !plugin.isDarkSea(boat.getWorld())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider == null) {
            return;
        }
        double toughness = effectiveToughness(rider);
        if (toughness > 1.0) {
            event.setDamage(event.getDamage() / toughness);
        }
    }

    /**
     * A boat sunk in the Dark Sea dumps its rider into the hostile water —
     * the point of sinking someone is to strand them. The exposure debuffs
     * take it from there; here we just tell them why they're suddenly swimming.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)
                || !plugin.isDarkSea(boat.getWorld())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider != null) {
            plugin.messages().send(rider, "boat-wrecked");
        }
    }

    /**
     * Stamps ownership the first time a player boards a Dark Sea boat, and never
     * overwrites it — an enemy can hop in and sail off, but can't claim the hull
     * out from under you. Ownership gates the boat wheel and stowing, not who
     * may ride.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)
                || !(event.getEntered() instanceof Player player)
                || !plugin.isDarkSea(boat.getWorld())) {
            return;
        }
        var pdc = boat.getPersistentDataContainer();
        if (!pdc.has(OWNER_KEY, PersistentDataType.STRING)) {
            pdc.set(OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        }
    }

    /** The owner's UUID stamped on a boat, or null if it was never boarded. */
    public static UUID ownerOf(Boat boat) {
        String raw = boat.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Whether this player owns the boat (was the first to board it). */
    public static boolean isOwner(Boat boat, Player player) {
        return player.getUniqueId().equals(ownerOf(boat));
    }

    /** Stamps a player as a boat's owner — used to claim an unowned hull. */
    public static void claim(Boat boat, Player player) {
        boat.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING,
                player.getUniqueId().toString());
    }

    /** The player riding a boat (the first passenger), or null if none. */
    private static Player boatRider(Boat boat) {
        List<Entity> passengers = boat.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof Player rider ? rider : null;
    }
}
