package com.diamend.darksea.boat;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.armor.SeaArmor;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.config.DarkSeaSettings.BoatLevel;
import com.diamend.darksea.data.PlayerDataStore;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.relic.Relic;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
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

    private final DarkSeaPlugin plugin;
    private final PlayerDataStore data;
    /** Tidal Draught expiry timestamps; stale entries lapse on their own. */
    private final Map<UUID, Long> draughts = new ConcurrentHashMap<>();

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
        double multiplier = stats(levelOf(rider)).speed();
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

    /** Boat shield for the exposure formula: only while riding a boat in the sea. */
    public int shieldFor(Player player) {
        if (player.getVehicle() instanceof Boat boat
                && boat.getWorld().getName().equals(plugin.settings().worldName())) {
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
     * Consume a matching upgrade token from the main hand and raise the boat
     * level by one. Tokens are per-level and only apply in sequence.
     */
    public void upgrade(Player player) {
        int current = levelOf(player);
        int next = current + 1;
        boolean nextExists = plugin.settings().boat().levels().containsKey(next);
        ItemStack hand = player.getInventory().getItemInMainHand();
        switch (evaluateUpgrade(current, SeaArmor.tokenLevelOf(hand), nextExists)) {
            case AT_MAX -> plugin.messages().send(player, "boat-max");
            case WRONG_TOKEN ->
                    plugin.messages().send(player, "boat-need-token", "level", String.valueOf(next));
            case UPGRADED -> {
                hand.setAmount(hand.getAmount() - 1);
                setLevel(player, next);
                plugin.messages().send(player, "boat-upgraded",
                        "name", stats(next).name(), "level", String.valueOf(next));
            }
        }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (!boat.getWorld().getName().equals(plugin.settings().worldName())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider == null) {
            return;
        }
        double multiplier = effectiveMultiplier(rider);
        double wounded = plugin.naval() != null ? plugin.naval().slowFactor(boat) : 1.0;
        Vector velocity = boat.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        double factor = speedFactor(multiplier, horizontal,
                plugin.settings().boat().speedCapBase(), wounded);
        if (Math.abs(factor - 1.0) < 1e-9) {
            return;
        }
        boat.setVelocity(new Vector(velocity.getX() * factor, velocity.getY(), velocity.getZ() * factor));
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
                || !boat.getWorld().getName().equals(plugin.settings().worldName())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider == null) {
            return;
        }
        double toughness = stats(levelOf(rider)).toughness();
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
                || !boat.getWorld().getName().equals(plugin.settings().worldName())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider != null) {
            plugin.messages().send(rider, "boat-wrecked");
        }
    }

    /** The player riding a boat (the first passenger), or null if none. */
    private static Player boatRider(Boat boat) {
        List<Entity> passengers = boat.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof Player rider ? rider : null;
    }
}
