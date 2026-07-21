package com.diamend.darksea.relic;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Undrowned Heart: the apex reward hidden past the Devouring Rim. Unlike
 * the carried, awake relics of {@link RelicService}, this one is <em>attuned</em>
 * — right-clicked once, consumed for good, and thereafter a permanent part of
 * the captain. Once attuned, a blow that would kill is refused like a Totem of
 * Undying: the damage is cancelled, effects are cleared, the captain is left at
 * a sliver of health with a totem's regeneration and absorption, and the sea
 * plays its resurrection flourish.
 *
 * <p>The catch is a cooldown ({@code relics.undrowned.cooldown-seconds}). The
 * last save is stamped into the player's data file, so relogging cannot wipe
 * the timer and chain saves. Between saves the captain is as mortal as anyone.
 */
public final class UndrownedHeartService implements Listener {

    private final DarkSeaPlugin plugin;

    public UndrownedHeartService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Pure logic (unit tested without a server)
    // ------------------------------------------------------------------

    /** A hit is lethal when it would drop current health to zero or below. */
    public static boolean wouldBeFatal(double currentHealth, double finalDamage) {
        return currentHealth - finalDamage <= 0.0;
    }

    /** Whether enough time has passed since the last save for the Heart to fire again. */
    public static boolean offCooldown(long nowMillis, long lastSaveMillis, long cooldownMillis) {
        return nowMillis - lastSaveMillis >= cooldownMillis;
    }

    /** Seconds still to wait before the Heart can save again (0 if ready). */
    public static long cooldownRemainingSeconds(long nowMillis, long lastSaveMillis, long cooldownMillis) {
        long remaining = cooldownMillis - (nowMillis - lastSaveMillis);
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    // ------------------------------------------------------------------
    // Attuning
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!DarkSeaItems.UNDROWNED_HEART.equals(DarkSeaItems.idOf(hand))) {
            return;
        }
        // It is never placed or used on a block — attuning always wins the click.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.data().isUndrowned(player.getUniqueId())) {
            plugin.messages().actionBar(player, "undrowned-already");
            return;
        }
        plugin.data().setUndrowned(player.getUniqueId(), true);
        hand.setAmount(hand.getAmount() - 1);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0.2);
        player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.8f);
        plugin.messages().send(player, "undrowned-attuned");
    }

    // ------------------------------------------------------------------
    // Refusing death
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!wouldBeFatal(player.getHealth(), event.getFinalDamage())) {
            return;
        }
        if (!plugin.data().isUndrowned(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = plugin.data().undrownedLastSave(player.getUniqueId());
        long cooldownMillis = plugin.settings().relics().undrownedCooldownSeconds() * 1000L;
        if (!offCooldown(now, last, cooldownMillis)) {
            // Spent — the captain drowns like anyone else this time.
            return;
        }

        event.setCancelled(true);
        plugin.data().setUndrownedLastSave(player.getUniqueId(), now);
        reviveLikeTotem(player);
    }

    private void reviveLikeTotem(Player player) {
        double reviveHealth = Math.min(plugin.settings().relics().undrownedReviveHealth(),
                player.getMaxHealth());
        player.setHealth(reviveHealth);
        player.setFireTicks(0);
        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 80, 0.5, 0.8, 0.5, 0.25);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        plugin.messages().send(player, "undrowned-saved");
    }
}
