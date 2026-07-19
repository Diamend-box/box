package com.diamend.darksea.item;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Drinking the registry consumables that need plugin help. The tonics
 * (Deepsight, Gillwater) carry real potion effects and need nothing from
 * us; the Tidal Draught's boat boost and the Sea-Salve's mending are
 * plugin-side.
 */
public final class ConsumableService implements Listener {

    private final DarkSeaPlugin plugin;

    public ConsumableService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        String id = DarkSeaItems.idOf(event.getItem());
        if (id == null) {
            return;
        }
        Player player = event.getPlayer();
        switch (id) {
            case DarkSeaItems.TIDAL_DRAUGHT -> {
                plugin.boat().addDraught(player);
                plugin.messages().actionBar(player, "draught-drunk",
                        "seconds", String.valueOf(DarkSeaItems.DRAUGHT_SECONDS));
            }
            case DarkSeaItems.SEA_SALVE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1800, 0));
            }
            default -> {
            }
        }
    }
}
