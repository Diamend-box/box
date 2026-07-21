package com.diamend.darksea.naval;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings.NavalSettings;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boat-vs-boat combat: rams, wounded hulls, the surge, and the harpoon's
 * hook. The design in one breath — closing speed is the weapon (75% to the
 * defender's hull, 25% to your own, each softened by its own toughness, a
 * bleed of both onto the riders), any hull hit slows the boat for a few
 * seconds so a runner can be chipped and caught, and everyone carries one
 * surge on a cooldown (sprint or jump key at the tiller) that doubles as
 * the line-cutter when a harpoon has you.
 *
 * <p>Naval damage runs on its own hull-HP model ({@code naval.hull.max-hp},
 * healing after a quiet spell) because Bukkit offers no way to deal partial
 * damage to a boat entity; vanilla sword-whacking keeps the vanilla wobble
 * plus the toughness divisor in {@link com.diamend.darksea.boat.BoatService}.
 * The home sanctuary is a no-ram, no-hook zone, mirroring the PvP rule.
 */
public final class NavalCombatService implements Listener {

    private final DarkSeaPlugin plugin;

    /** Hull HP under naval fire; heals fully after a quiet regen window. */
    private record HullState(double hp, long lastHitMillis) {
    }

    /** A live wounded-hull slow: the harsher of any overlapping causes. */
    private record SlowState(long untilMillis, double factor) {
    }

    /** A harpoon line: who holds it and when it goes slack on its own. */
    private record Hook(UUID shooter, long untilMillis) {
    }

    private final Map<UUID, HullState> hulls = new ConcurrentHashMap<>();
    private final Map<UUID, SlowState> slows = new ConcurrentHashMap<>();
    private final Map<UUID, Hook> hooks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> surgeReadyAt = new ConcurrentHashMap<>();
    /** Last seen input flags per player, for rising-edge surge detection. */
    private final Map<UUID, Boolean> keyHeld = new ConcurrentHashMap<>();
    /** Per boat-pair ram cooldown, keyed by the two UUIDs in sorted order. */
    private final Map<String, Long> ramCooldowns = new ConcurrentHashMap<>();

    public NavalCombatService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    private NavalSettings naval() {
        return plugin.settings().naval();
    }

    // ------------------------------------------------------------------
    // Wounded hull: the speed tax on damage
    // ------------------------------------------------------------------

    /** The current wounded-hull speed factor for a boat (1.0 = healthy). */
    public double slowFactor(Boat boat) {
        SlowState slow = slows.get(boat.getUniqueId());
        if (slow == null) {
            return 1.0;
        }
        double factor = NavalMath.woundedFactor(System.currentTimeMillis(),
                slow.untilMillis(), slow.factor());
        if (factor >= 1.0) {
            slows.remove(boat.getUniqueId());
        }
        return factor;
    }

    /** Applies a slow, keeping the harsher factor and the later expiry. */
    public void slow(Boat boat, int seconds, double factor) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        slows.merge(boat.getUniqueId(), new SlowState(until, factor),
                (a, b) -> new SlowState(Math.max(a.untilMillis(), b.untilMillis()),
                        Math.min(a.factor(), b.factor())));
    }

    /** Any vanilla hull hit (arrows, melee) wounds the boat too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getVehicle() instanceof Boat boat && inDarkSea(boat.getLocation())
                && event.getDamage() > 0 && boatRider(boat) != null) {
            slow(boat, naval().hull().woundedSlowSeconds(), naval().hull().woundedSpeedFactor());
        }
    }

    // ------------------------------------------------------------------
    // Hull HP: naval weapons sink ships by inches
    // ------------------------------------------------------------------

    /**
     * Deals naval hull damage (already toughness-adjusted by the caller) and
     * sinks the boat at zero. Also wounds the hull. {@code attacker} may be
     * null (environmental); it's told when the target goes down.
     */
    public void damageHull(Boat boat, double damage, Player attacker) {
        if (damage <= 0 || !boat.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        double maxHp = naval().hull().maxHp();
        HullState state = hulls.get(boat.getUniqueId());
        double hp = state == null
                || now - state.lastHitMillis() > naval().hull().regenSeconds() * 1000L
                ? maxHp : state.hp();
        hp -= damage;
        slow(boat, naval().hull().woundedSlowSeconds(), naval().hull().woundedSpeedFactor());
        if (hp > 0) {
            hulls.put(boat.getUniqueId(), new HullState(hp, now));
            return;
        }
        sink(boat, attacker);
    }

    /**
     * Current hull HP for the HUD, applying the same lazy regen rule as
     * {@link #damageHull}: a hull quiet past the regen window reads full.
     */
    public double hullHp(Boat boat) {
        double maxHp = naval().hull().maxHp();
        HullState state = hulls.get(boat.getUniqueId());
        if (state == null) {
            return maxHp;
        }
        if (System.currentTimeMillis() - state.lastHitMillis()
                > naval().hull().regenSeconds() * 1000L) {
            hulls.remove(boat.getUniqueId(), state);
            return maxHp;
        }
        return state.hp();
    }

    /** Whole seconds until this player's surge is ready; 0 means ready now. */
    public int surgeSecondsLeft(Player player) {
        long readyAt = surgeReadyAt.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        return readyAt <= now ? 0 : (int) ((readyAt - now + 999) / 1000);
    }

    private void sink(Boat boat, Player attacker) {
        Player rider = boatRider(boat);
        Location loc = boat.getLocation();
        clearBoat(boat.getUniqueId());
        boat.remove();  // ejects the rider into the water — stranded
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.6f);
        if (rider != null) {
            plugin.messages().send(rider, "boat-wrecked");
        }
        if (attacker != null && attacker != rider) {
            plugin.messages().send(attacker, "naval-sunk-them");
        }
    }

    // ------------------------------------------------------------------
    // Ramming
    // ------------------------------------------------------------------

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat) || !inDarkSea(boat.getLocation())) {
            return;
        }
        Player rider = boatRider(boat);
        if (rider == null) {
            return;
        }
        for (Entity nearby : boat.getNearbyEntities(2.2, 1.5, 2.2)) {
            if (nearby instanceof Boat other && boatRider(other) != null) {
                tryRam(boat, other);
                return;  // one contact per move tick is plenty
            }
        }
    }

    private void tryRam(Boat a, Boat b) {
        // The sanctuary is a no-ram zone, same as its no-PvP rule.
        if (withinSanctuary(a.getLocation()) || withinSanctuary(b.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        String pair = pairKey(a.getUniqueId(), b.getUniqueId());
        Long coolUntil = ramCooldowns.get(pair);
        if (coolUntil != null && now < coolUntil) {
            return;
        }
        Vector posA = a.getLocation().toVector();
        Vector posB = b.getLocation().toVector();
        double closing = NavalMath.closingSpeed(posA, a.getVelocity(), posB, b.getVelocity());
        if (closing < naval().ram().minClosingSpeed()) {
            return;
        }
        ramCooldowns.put(pair, now + naval().ram().pairCooldownMillis());

        // Whoever contributed more approach chose the charge — the attacker.
        boolean aCharged = NavalMath.approachSpeed(posA, a.getVelocity(), posB)
                >= NavalMath.approachSpeed(posB, b.getVelocity(), posA);
        Boat attacker = aCharged ? a : b;
        Boat defender = aCharged ? b : a;

        double base = NavalMath.ramBaseDamage(closing,
                naval().ram().damagePerSpeed(), naval().ram().maxDamage());
        double defenderShare = naval().ram().defenderShare();
        applyRamSide(defender, base, defenderShare, boatRider(attacker));
        applyRamSide(attacker, base, 1.0 - defenderShare, boatRider(defender));

        if (defender.isValid()) {
            defender.setVelocity(defender.getVelocity().add(NavalMath.ramKnockback(
                    attacker.getLocation().toVector(), attacker.getVelocity(),
                    defender.getLocation().toVector(), naval().ram().knockback())));
        }
        Location at = defender.isValid() ? defender.getLocation() : attacker.getLocation();
        at.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
    }

    /** One side of a ram: its hull share, plus the bleed onto its rider. */
    private void applyRamSide(Boat boat, double base, double share, Player opponent) {
        Player rider = boatRider(boat);
        double toughness = rider != null
                ? plugin.boat().stats(plugin.boat().levelOf(rider)).toughness() : 1.0;
        double hullDamage = NavalMath.hullShare(base, share, toughness);
        if (rider != null) {
            double bleed = hullDamage * naval().ram().riderBleed();
            if (bleed > 0.1) {
                // Damage attribution keeps kill credit; the sanctuary PvP
                // listener never fires because rams are already blocked there.
                if (opponent != null) {
                    rider.damage(bleed, opponent);
                } else {
                    rider.damage(bleed);
                }
            }
        }
        damageHull(boat, hullDamage, opponent);
    }

    // ------------------------------------------------------------------
    // The surge
    // ------------------------------------------------------------------

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        boolean pressed = event.getInput().isSprint() || event.getInput().isJump();
        Boolean before = keyHeld.put(player.getUniqueId(), pressed);
        if (!pressed || Boolean.TRUE.equals(before)) {
            return;  // only the rising edge fires — works for toggle sprint too
        }
        if (!(player.getVehicle() instanceof Boat boat) || !inDarkSea(boat.getLocation())) {
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = surgeReadyAt.getOrDefault(player.getUniqueId(), 0L);
        if (!NavalMath.surgeReady(now, readyAt)) {
            plugin.hud().flash(player, "naval-surge-cooldown",
                    "seconds", String.valueOf((readyAt - now + 999) / 1000));
            return;
        }
        surgeReadyAt.put(player.getUniqueId(), now + naval().surge().cooldownSeconds() * 1000L);

        // Harpooned? The surge is the knife: the line snaps, that's the spend.
        Hook hook = hooks.remove(boat.getUniqueId());
        if (hook != null) {
            plugin.messages().send(player, "naval-line-snapped");
            Player shooter = plugin.getServer().getPlayer(hook.shooter());
            if (shooter != null) {
                plugin.messages().send(shooter, "naval-line-snapped-them");
            }
        }

        Vector heading = boat.getVelocity().clone().setY(0);
        if (heading.lengthSquared() < 1e-4) {
            heading = player.getLocation().getDirection().clone().setY(0);
        }
        if (heading.lengthSquared() < 1e-6) {
            return;  // looking straight down while motionless: nowhere to surge
        }
        double burst = plugin.settings().boat().speedCapBase()
                * plugin.boat().effectiveMultiplier(player)
                * naval().surge().boostFactor()
                * slowFactor(boat);  // a wounded hull surges weakly
        boat.setVelocity(heading.normalize().multiply(burst)
                .setY(boat.getVelocity().getY()));
        plugin.hud().flash(player, "naval-surge");
        boat.getWorld().playSound(boat.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1.0f, 0.8f);
    }

    // ------------------------------------------------------------------
    // Harpoon hooks (fired by NavalWeaponListener, snapped by the surge)
    // ------------------------------------------------------------------

    /** Hooks a boat and reels it toward the shooter until snap or slack. */
    public void hook(Boat victim, Player shooter) {
        long until = System.currentTimeMillis()
                + (long) (naval().harpoon().pullSeconds() * 1000);
        hooks.put(victim.getUniqueId(), new Hook(shooter.getUniqueId(), until));
        double strength = naval().harpoon().pullStrength();
        double slack = naval().harpoon().range() * 1.5;
        new BukkitRunnable() {
            @Override
            public void run() {
                Hook current = hooks.get(victim.getUniqueId());
                if (current == null || !current.shooter().equals(shooter.getUniqueId())) {
                    cancel();  // snapped, or replaced by a newer line
                    return;
                }
                if (System.currentTimeMillis() >= current.untilMillis()
                        || !victim.isValid() || !shooter.isOnline()
                        || shooter.getWorld() != victim.getWorld()
                        || shooter.getLocation().distance(victim.getLocation()) > slack) {
                    hooks.remove(victim.getUniqueId(), current);
                    cancel();
                    return;
                }
                Vector pull = shooter.getLocation().toVector()
                        .subtract(victim.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > 1) {
                    victim.setVelocity(victim.getVelocity().multiply(0.6)
                            .add(pull.normalize().multiply(strength)));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Whether this boat currently has a live harpoon line on it. */
    public boolean isHooked(Boat boat) {
        Hook hook = hooks.get(boat.getUniqueId());
        if (hook == null) {
            return false;
        }
        if (System.currentTimeMillis() >= hook.untilMillis()) {
            hooks.remove(boat.getUniqueId());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Housekeeping and shared geometry
    // ------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        keyHeld.remove(id);
        surgeReadyAt.remove(id);
    }

    private void clearBoat(UUID boatId) {
        hulls.remove(boatId);
        slows.remove(boatId);
        hooks.remove(boatId);
    }

    /** The player riding a boat (the first passenger), or null if none. */
    static Player boatRider(Boat boat) {
        List<Entity> passengers = boat.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof Player rider ? rider : null;
    }

    boolean withinSanctuary(Location loc) {
        double radius = plugin.settings().combat().pvpSafeRadius();
        if (radius <= 0) {
            return false;
        }
        double dx = loc.getX() - plugin.settings().centerX();
        double dz = loc.getZ() - plugin.settings().centerZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    boolean inDarkSea(Location loc) {
        World world = loc.getWorld();
        return world != null && world.getName().equals(plugin.settings().worldName());
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
