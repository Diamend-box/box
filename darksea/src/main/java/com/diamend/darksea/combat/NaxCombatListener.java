package com.diamend.darksea.combat;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.armor.VironicArmor;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.relic.Relic;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Custom melee behavior, all three effects independent and stacking in one
 * pass:
 *
 * 1. The Mariphage Stinger deals exactly its 2 hearts as TRUE damage — the
 *    hit's armor, resistance and enchantment reductions are zeroed, so 4.0
 *    is what lands regardless of what the target wears.
 * 2. A full Vironic set adds its blessing (+2 damage per set tier) while
 *    the wearer stands in the Dark Sea.
 * 3. An active Vector of the Mariphage infects whatever the carrier
 *    strikes: Poison II and a touch of Slowness — the Core's plague burst,
 *    turned outward.
 *
 * Only direct player melee: projectiles arrive with the projectile as
 * damager and never enter here.
 */
public final class NaxCombatListener implements Listener {

    /** The Stinger's whole hit: 2 hearts, straight through defense. */
    public static final double STINGER_TRUE_DAMAGE = 4.0;

    private static final EntityDamageEvent.DamageModifier[] PIERCED_MODIFIERS = {
            EntityDamageEvent.DamageModifier.ARMOR,
            EntityDamageEvent.DamageModifier.RESISTANCE,
            EntityDamageEvent.DamageModifier.MAGIC,
    };

    private final DarkSeaPlugin plugin;

    public NaxCombatListener(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings("deprecation") // per-modifier damage is deprecated but has no replacement
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (DarkSeaItems.MARIPHAGE_STINGER.equals(
                DarkSeaItems.idOf(player.getInventory().getItemInMainHand()))) {
            event.setDamage(STINGER_TRUE_DAMAGE);
            for (EntityDamageEvent.DamageModifier modifier : PIERCED_MODIFIERS) {
                if (event.isApplicable(modifier)) {
                    event.setDamage(modifier, 0.0);
                }
            }
        }

        if (player.getWorld().getName().equals(plugin.settings().worldName())) {
            double bonus = VironicArmor.setBonusDamage(player.getInventory().getArmorContents());
            if (bonus > 0) {
                event.setDamage(event.getDamage() + bonus);
            }
        }

        if (plugin.relics().isActive(player, Relic.Boost.VECTOR)
                && event.getEntity() instanceof LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true, true));
        }
    }
}
