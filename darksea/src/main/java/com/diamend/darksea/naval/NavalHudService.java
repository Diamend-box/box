package com.diamend.darksea.naval;

import com.diamend.darksea.DarkSeaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    /**
     * The last bar painted for a player, kept so an unchanged one is not
     * re-parsed. The action bar fades after about three seconds, so the text
     * has to keep being <em>sent</em> — but the MiniMessage behind it only has
     * to be turned into a component when it actually differs, and at cruise it
     * does not differ for seconds at a time.
     */
    private record Painted(String line, Component component) {
    }

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Long> holdUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Painted> lastPainted = new ConcurrentHashMap<>();

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
        lastPainted.remove(player.getUniqueId());   // the bar the ticker resumes onto is new
        plugin.messages().actionBar(player, key, placeholders);
    }

    private void tick() {
        if (!plugin.settings().naval().hud().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        // A player who logs out while still in a boat is never seen by the
        // loop below again, so their entries would sit in these maps for the
        // life of the server. Checked by size because that is free, and swept
        // only when it is actually wrong.
        int online = plugin.getServer().getOnlinePlayers().size();
        if (holdUntil.size() > online || lastPainted.size() > online) {
            Set<UUID> present = new HashSet<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                present.add(player.getUniqueId());
            }
            holdUntil.keySet().retainAll(present);
            lastPainted.keySet().retainAll(present);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Boat boat)
                    || !plugin.isDarkSea(boat.getWorld())) {
                holdUntil.remove(player.getUniqueId());
                lastPainted.remove(player.getUniqueId());
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
                    naval.maxHp(boat),
                    naval.speedCeiling(boat) < 1.0,
                    naval.surgeSecondsLeft(player),
                    naval.isHooked(boat));
            Painted painted = lastPainted.get(player.getUniqueId());
            if (painted == null || !painted.line().equals(line)) {
                painted = new Painted(line, plugin.messages().raw(line));
                lastPainted.put(player.getUniqueId(), painted);
            }
            player.sendActionBar(painted.component());
        }
    }
}
