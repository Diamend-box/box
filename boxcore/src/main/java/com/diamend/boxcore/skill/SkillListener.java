package com.diamend.boxcore.skill;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps a player's unlocked effects in sync with their profile.
 *
 * <p>Effects are re-applied a tick after join and respawn: on join because the
 * player's attributes aren't final until the server has finished loading them,
 * and on respawn because death clears potion effects.
 */
public class SkillListener implements Listener {

    private final Plugin plugin;
    private final ProfileManager profiles;
    private final SkillService skills;
    private final EffectApplier effects;

    public SkillListener(Plugin plugin, ProfileManager profiles, SkillService skills, EffectApplier effects) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.skills = skills;
        this.effects = effects;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refresh(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Strip our attribute modifiers before the server saves the player, so
        // nothing of ours is left in their data if BoxCore is ever removed.
        effects.clear(event.getPlayer());
    }

    private void refresh(Player player, boolean reconcile) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (reconcile) {
                PlayerProfile profile = profiles.get(player.getUniqueId());
                skills.reconcile(profile);
            }
            effects.apply(player);
        });
    }
}
