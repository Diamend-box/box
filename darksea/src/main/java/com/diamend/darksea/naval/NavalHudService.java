package com.diamend.darksea.naval;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The always-on boat action bar: while a player rides a boat in the Dark
 * Sea, a repeating task repaints {@link NavalHud#render} every
 * {@code naval.hud.period-ticks}. Hull HP is visible in every state — the
 * bar is the sailor's one persistent instrument.
 *
 * <p>Transient naval messages (surge fired, cooldown denied) go through
 * {@link #flash}, which holds the ticker off that player's bar briefly so
 * the flash is actually readable instead of being repainted half a second
 * later.
 */
public final class NavalHudService {

    /** How long a flash owns the action bar before the ticker resumes. */
    static final long FLASH_HOLD_MILLIS = 1500;

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Long> holdUntil = new ConcurrentHashMap<>();

    public NavalHudService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the repaint task; a disabled HUD simply never paints. */
    public void start() {
        long period = Math.max(2, plugin.settings().naval().hud().periodTicks());
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    /** Sends a transient action-bar message and pauses the ticker under it. */
    public void flash(Player player, String key, String... placeholders) {
        holdUntil.put(player.getUniqueId(), System.currentTimeMillis() + FLASH_HOLD_MILLIS);
        plugin.messages().actionBar(player, key, placeholders);
    }

    private void tick() {
        if (!plugin.settings().naval().hud().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Boat boat)
                    || !boat.getWorld().getName().equals(plugin.settings().worldName())) {
                holdUntil.remove(player.getUniqueId());
                continue;
            }
            Long hold = holdUntil.get(player.getUniqueId());
            if (hold != null) {
                if (now < hold) {
                    continue;  // a flash still owns the bar
                }
                holdUntil.remove(player.getUniqueId());
            }
            NavalCombatService naval = plugin.naval();
            String line = NavalHud.render(
                    plugin.boat().stats(plugin.boat().levelOf(player)).name(),
                    naval.hullHp(boat),
                    plugin.settings().naval().hull().maxHp(),
                    naval.speedCeiling(boat) < 1.0,
                    naval.surgeSecondsLeft(player),
                    naval.isHooked(boat));
            player.sendActionBar(plugin.messages().raw(line));
        }
    }
}
