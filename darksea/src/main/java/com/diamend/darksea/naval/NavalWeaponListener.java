package com.diamend.darksea.naval;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * The anti-ship arsenal: chainshot (cripples a hull's speed), hullpiercer
 * (heavy hull damage through half the plating) and the harpoon gun (hooks a
 * boat and reels it in — countered by spending a surge to snap the line).
 * Ammo identity rides the projectile's PersistentDataContainer, stamped at
 * the moment of firing from the consumed arrow (or from the gun itself for
 * harpoons), so hoppers, pickups and creative middle-clicks can't forge one
 * mid-flight. All of it is loot: naval ammo is part of a run's haul.
 */
public final class NavalWeaponListener implements Listener {

    /** String PDC tag on a projectile: which naval payload it carries. */
    static final NamespacedKey AMMO_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:naval_ammo"));
    static final String PAYLOAD_HARPOON = "harpoon";

    private final DarkSeaPlugin plugin;
    private final NavalCombatService naval;

    public NavalWeaponListener(DarkSeaPlugin plugin, NavalCombatService naval) {
        this.plugin = plugin;
        this.naval = naval;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)
                || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        // The gun outranks the ammo: a harpoon gun always fires a harpoon.
        if (DarkSeaItems.HARPOON_GUN.equals(DarkSeaItems.idOf(event.getBow()))) {
            projectile.getPersistentDataContainer()
                    .set(AMMO_KEY, PersistentDataType.STRING, PAYLOAD_HARPOON);
            return;
        }
        String ammoId = DarkSeaItems.idOf(event.getConsumable());
        if (DarkSeaItems.CHAINSHOT_ARROW.equals(ammoId)
                || DarkSeaItems.HULLPIERCER_ARROW.equals(ammoId)) {
            projectile.getPersistentDataContainer()
                    .set(AMMO_KEY, PersistentDataType.STRING, ammoId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Boat boat)
                || !naval.inDarkSea(boat.getLocation())) {
            return;
        }
        String payload = event.getEntity().getPersistentDataContainer()
                .get(AMMO_KEY, PersistentDataType.STRING);
        if (payload == null) {
            return;
        }
        Player rider = NavalCombatService.boatRider(boat);
        Player shooter = event.getEntity().getShooter() instanceof Player p ? p : null;
        if (rider == null || shooter == null || rider == shooter) {
            return;  // naval ammo only bites ridden enemy hulls
        }
        // The sanctuary blocks naval weapons the same way it blocks PvP.
        if (naval.withinSanctuary(boat.getLocation())
                || naval.withinSanctuary(shooter.getLocation())) {
            return;
        }
        event.getEntity().remove();  // the payload spends itself on the hull
        switch (payload) {
            case DarkSeaItems.CHAINSHOT_ARROW -> {
                var settings = plugin.settings().naval();
                naval.slow(boat, settings.chainshotSlowSeconds(), settings.chainshotSpeedFactor());
                boat.getWorld().playSound(boat.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.6f);
                plugin.messages().actionBar(rider, "naval-chainshot-hit");
            }
            case DarkSeaItems.HULLPIERCER_ARROW -> {
                double toughness = plugin.boat().stats(plugin.boat().levelOf(rider)).toughness();
                // Cuts through plating: only a fraction of the toughness bonus counts.
                double factor = plugin.settings().naval().hullpiercerToughnessFactor();
                double effective = 1.0 + (Math.max(1.0, toughness) - 1.0) * factor;
                naval.damageHull(boat, plugin.settings().naval().hullpiercerDamage() / effective,
                        shooter);
                boat.getWorld().playSound(boat.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
            }
            case PAYLOAD_HARPOON -> {
                double range = plugin.settings().naval().harpoon().range();
                if (shooter.getWorld() != boat.getWorld()
                        || shooter.getLocation().distance(boat.getLocation()) > range) {
                    return;  // the line only reaches so far
                }
                naval.hook(boat, shooter);
                plugin.messages().send(rider, "naval-hooked");
                plugin.messages().send(shooter, "naval-hooked-them");
                boat.getWorld().playSound(boat.getLocation(),
                        Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.8f);
            }
            default -> {
            }
        }
    }
}
