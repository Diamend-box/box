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
import org.bukkit.event.Listener;
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

    /**
     * Consume a matching upgrade token from the main hand and raise the boat
     * level by one. Tokens are per-level and only apply in sequence.
     */
    public void upgrade(Player player) {
        int current = levelOf(player);
        int next = current + 1;
        if (!plugin.settings().boat().levels().containsKey(next)) {
            plugin.messages().send(player, "boat-max");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (SeaArmor.tokenLevelOf(hand) != next) {
            plugin.messages().send(player, "boat-need-token", "level", String.valueOf(next));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        setLevel(player, next);
        plugin.messages().send(player, "boat-upgraded",
                "name", stats(next).name(), "level", String.valueOf(next));
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) {
            return;
        }
        if (!boat.getWorld().getName().equals(plugin.settings().worldName())) {
            return;
        }
        List<Entity> passengers = boat.getPassengers();
        if (passengers.isEmpty() || !(passengers.get(0) instanceof Player rider)) {
            return;
        }
        double multiplier = effectiveMultiplier(rider);
        if (multiplier <= 1.0) {
            return;
        }
        Vector velocity = boat.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontal < 1e-3) {
            return;
        }
        double cap = plugin.settings().boat().speedCapBase() * multiplier;
        double factor = Math.min(multiplier, cap / horizontal);
        if (factor <= 1.0) {
            return;
        }
        boat.setVelocity(new Vector(velocity.getX() * factor, velocity.getY(), velocity.getZ() * factor));
    }
}
