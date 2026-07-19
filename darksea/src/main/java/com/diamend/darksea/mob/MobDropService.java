package com.diamend.darksea.mob;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

/**
 * Rolls plugin-side drops when a DarkSea-spawned mob dies. HIGHEST priority
 * so the additions land after MythicMobs has already rewritten the drop
 * list ({@code PreventOtherDrops} would otherwise eat them).
 */
public final class MobDropService implements Listener {

    private final DarkSeaPlugin plugin;
    private final Random rng = new Random();

    public MobDropService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        String mobType = event.getEntity().getPersistentDataContainer()
                .get(MobDrops.MOB_ID_KEY, PersistentDataType.STRING);
        if (mobType == null) {
            return;
        }
        List<MobDrops.Line> lines = plugin.mobDrops().get(mobType);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        event.getDrops().addAll(MobDrops.roll(lines, rng));
    }
}
