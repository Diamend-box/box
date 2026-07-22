package com.diamend.darksea.item;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The Soulwake Compass in the hand: right-click and the nearest living soul in
 * the Deep gives up its heading. A one-shot snapshot, not a live radar — the
 * quarry can slip the trail by moving — and the charge is consumed on use.
 * Players sheltering in the home sanctuary are no one's quarry, so a camper's
 * heading never leaks. When the sea is empty within reach the charge is kept.
 *
 * <p>The selection and readout math live in {@link HunterSense}; this shell
 * only gathers candidates and paints the result for the hunter alone.
 */
public final class SoulwakeService implements Listener {

    private final DarkSeaPlugin plugin;

    public SoulwakeService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!DarkSeaItems.SOULWAKE_COMPASS.equals(DarkSeaItems.idOf(hand))) {
            return;
        }
        // It never places or opens anything — reading the sea always wins the click.
        event.setCancelled(true);
        Player hunter = event.getPlayer();
        if (!hunter.getWorld().getName().equals(plugin.settings().worldName())) {
            plugin.messages().actionBar(hunter, "soulwake-not-in-sea");
            return;
        }
        Player quarry = nearestQuarry(hunter);
        if (quarry == null) {
            // An empty sea doesn't spend the charge — nothing to point at.
            plugin.messages().actionBar(hunter, "soulwake-empty");
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        reveal(hunter, quarry);
    }

    /** The closest huntable player: same sea, alive, not a spectator, out of sanctuary. */
    private Player nearestQuarry(Player hunter) {
        World world = hunter.getWorld();
        Location at = hunter.getLocation();
        double limitSq = DarkSeaItems.SOULWAKE_RANGE * DarkSeaItems.SOULWAKE_RANGE;
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player other : world.getPlayers()) {
            if (other == hunter || !other.isValid() || other.isDead()) {
                continue;
            }
            if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (inSanctuary(other.getLocation())) {
                continue;
            }
            double sq = other.getLocation().distanceSquared(at);
            if (sq <= limitSq && sq < bestSq) {
                bestSq = sq;
                best = other;
            }
        }
        return best;
    }

    private boolean inSanctuary(Location loc) {
        double radius = plugin.settings().combat().pvpSafeRadius();
        if (radius <= 0) {
            return false;
        }
        double dx = loc.getX() - plugin.settings().centerX();
        double dz = loc.getZ() - plugin.settings().centerZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private void reveal(Player hunter, Player quarry) {
        Location h = hunter.getLocation();
        Location q = quarry.getLocation();
        double dx = q.getX() - h.getX();
        double dy = q.getY() - h.getY();
        double dz = q.getZ() - h.getZ();
        plugin.messages().send(hunter, "soulwake-heading",
                "blocks", String.valueOf(HunterSense.blocks(dx, dy, dz)),
                "bearing", HunterSense.bearing(dx, dz),
                "elevation", HunterSense.elevation(dy));
        streak(hunter, dx, dz);
        hunter.playSound(h, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.7f, 1.5f);
    }

    /** A soul-flame streak pointing the way — shown to the hunter alone. */
    private void streak(Player hunter, double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-3) {
            return;
        }
        double ux = dx / len;
        double uz = dz / len;
        Location base = hunter.getLocation().add(0, 1.0, 0);
        for (double d = 1.0; d <= 6.0; d += 0.5) {
            Location point = base.clone().add(ux * d, 0, uz * d);
            hunter.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 2, 0.03, 0.03, 0.03, 0.0);
        }
    }
}
