package com.diamend.robobear.mob;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * The rules that make a challenge mob one player's problem and nobody else's.
 *
 * <p>Hiding an entity stops it being drawn. It does not stop it pathing to a
 * bystander, hitting them, dropping loot at their feet or eating the mine —
 * every one of those has to be refused explicitly, and every one of them would
 * otherwise be a bug reported as "something invisible is attacking me".
 *
 * <p>The handlers are ordered around one cheap question: is this entity tagged?
 * For the overwhelming majority of events on a busy server the answer is no and
 * the handler returns immediately.
 */
public class ChallengeMobListener implements Listener {

    private final RoboBearPlugin plugin;

    public ChallengeMobListener(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Who it goes after
    // ------------------------------------------------------------------

    /** A challenge mob only ever wants its owner. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = plugin.mobs().ownerOf(event.getEntity());
        if (owner == null) {
            return;
        }
        Entity target = event.getTarget();
        if (target != null && owner.equals(target.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Who it can hurt, and who can hurt it
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        ChallengeMobs mobs = plugin.mobs();

        UUID victimOwner = mobs.ownerOf(event.getEntity());
        if (victimOwner != null) {
            // Only its owner may hurt it. A bystander can't see it, so anything
            // that lands is an accident — a stray arrow, a splash, an explosion.
            Entity attacker = source(event.getDamager());
            if (attacker == null || !victimOwner.equals(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        UUID attackerOwner = mobs.ownerOf(source(event.getDamager()));
        if (attackerOwner != null && !attackerOwner.equals(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** The player behind a hit, seeing through arrows and thrown potions. */
    private static Entity source(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity entity ? entity : null;
        }
        return damager;
    }

    // ------------------------------------------------------------------
    // What can't kill it
    // ------------------------------------------------------------------

    /**
     * The accidents that would otherwise do the challenge's job for it.
     *
     * <p>Suffocation is the one that matters here: these follow you into a mine
     * that resets on a timer, and a reset entombs whatever is standing in it. A
     * hazard you beat by waiting for the mine to refill is not a hazard.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.mobs().isChallengeMob(event.getEntity())) {
            return;
        }
        switch (event.getCause()) {
            case SUFFOCATION, CRAMMING, FALL -> event.setCancelled(true);
            default -> { }
        }
    }

    /**
     * Daylight. Zombies and skeletons burn up in an open-cast mine, so left
     * alone the whole roster evaporates at noon and comes back at dusk.
     *
     * <p>Only spontaneous combustion is refused — the plain event. Lava and
     * flint-and-steel arrive as the by-block and by-entity subclasses and are
     * left to work, so fire is still a way to fight one.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent
                || event instanceof EntityCombustByEntityEvent) {
            return;
        }
        if (plugin.mobs().isChallengeMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // What it leaves behind
    // ------------------------------------------------------------------

    /**
     * No drops and no experience, unless an operator asks for them.
     *
     * <p>Kill credit is not handled here — that stays with the ordinary mob-kill
     * path, because a challenge mob counts for a kill objective the same way any
     * hostile does. This is only about not turning a hazard into a mob farm that
     * follows you around, and about not dropping loot a bystander can see fall
     * out of nothing.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.mobs().isChallengeMob(event.getEntity())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("mobs.drops", false)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        plugin.mobs().forget(event.getEntity());
    }

    // ------------------------------------------------------------------
    // What it must not touch
    // ------------------------------------------------------------------

    /**
     * The mine stays the way the mine plugin left it.
     *
     * <p>Silverfish burrow into stone, endermen walk off with it, creepers
     * rearrange it. The roster avoids the worst offenders, but an operator is
     * free to add one and this is what stops that being a mistake they discover
     * from a hole in a mine.
     */
    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.mobs().isChallengeMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (plugin.mobs().isChallengeMob(event.getEntity())) {
            // The bang and the damage stay; the crater doesn't.
            event.blockList().clear();
        }
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    /** Someone joining has no run, so every live mob is hidden from them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.mobs().hideAllFrom(event.getPlayer());
    }

    /**
     * Clears leftovers a crash stranded.
     *
     * <p>The registry lives in memory; the mobs live in the world. A hard stop
     * separates the two and leaves a tagged, invisible, hostile mob standing in
     * a mine. Anything tagged that has no live wave behind it is removed the
     * moment its chunk comes back.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Entity[] entities = event.getChunk().getEntities();
        if (entities.length == 0) {
            return;
        }
        plugin.mobs().sweep(entities);
    }
}
