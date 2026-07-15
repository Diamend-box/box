package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional MythicMobs integration. The hook is wired up with reflection so
 * this plugin compiles and runs without MythicMobs installed; when MythicMobs
 * is present, its {@code MythicMobDeathEvent} feeds
 * {@link TriggerType#MYTHIC_MOB_KILL} achievements using the mob's internal
 * (config) name as the target key. Both MythicMobs 5.x and 4.x are supported.
 */
public final class MythicMobsHook {

    private static final String[] EVENT_CLASSES = {
            "io.lumine.mythic.bukkit.events.MythicMobDeathEvent",               // MythicMobs 5.x
            "io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDeathEvent" // MythicMobs 4.x
    };

    private MythicMobsHook() {
    }

    /** Registers the death-event listener if MythicMobs is installed. */
    public static void register(Plugin plugin, AchievementService service) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        Class<? extends Event> eventClass = findEventClass();
        if (eventClass == null) {
            plugin.getLogger().warning("MythicMobs is installed but its death event class was not found; "
                    + "MYTHIC_MOB_KILL achievements will not trigger.");
            return;
        }
        try {
            Method getMobType = eventClass.getMethod("getMobType");
            Method getInternalName = getMobType.getReturnType().getMethod("getInternalName");
            Method getKiller = eventClass.getMethod("getKiller");

            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return; // Unrelated event delivered to the same handler list.
                }
                try {
                    if (!(getKiller.invoke(event) instanceof Player player)) {
                        return;
                    }
                    Object mobType = getMobType.invoke(event);
                    if (mobType == null) {
                        return;
                    }
                    String internalName = String.valueOf(getInternalName.invoke(mobType));
                    service.handle(player, TriggerType.MYTHIC_MOB_KILL, internalName, 1);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to read a MythicMobs death event", ex);
                }
            };

            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.MONITOR, executor, plugin, false);
            plugin.getLogger().info("Hooked into MythicMobs via " + eventClass.getName() + ".");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not hook into MythicMobs; "
                    + "MYTHIC_MOB_KILL achievements will not trigger.", ex);
        }
    }

    private static Class<? extends Event> findEventClass() {
        for (String name : EVENT_CLASSES) {
            try {
                return Class.forName(name).asSubclass(Event.class);
            } catch (ClassNotFoundException | ClassCastException ignored) {
                // Try the next known class name.
            }
        }
        return null;
    }
}
