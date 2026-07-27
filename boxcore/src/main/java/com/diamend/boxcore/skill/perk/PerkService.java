package com.diamend.boxcore.skill.perk;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import com.diamend.boxcore.skill.NodeEffects;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillTreeManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out which perks a player has, and caches the answer.
 *
 * <p>Perks are read on some very hot paths — every hit landed, every block
 * broken — so walking a player's node map each time would be wasteful. The
 * totals are computed once and thrown away whenever the player's nodes change,
 * which {@code EffectApplier} already knows how to notice.
 */
public class PerkService {

    /** How often {@link Perk#SELF_REPAIR} ticks, in server ticks. */
    private static final long MEND_INTERVAL_TICKS = 100L;

    private final ProfileManager profiles;
    private final SkillTreeManager trees;
    private final Map<UUID, PerkSet> cache = new ConcurrentHashMap<>();
    /** When each player's Second Chance comes off cooldown (epoch millis). */
    private final Map<UUID, Long> secondChanceReady = new ConcurrentHashMap<>();

    private BukkitTask mendTask;

    public PerkService(ProfileManager profiles, SkillTreeManager trees) {
        this.profiles = profiles;
        this.trees = trees;
    }

    public PerkSet of(Player player) {
        return player == null ? PerkSet.EMPTY : of(player.getUniqueId());
    }

    public PerkSet of(UUID uuid) {
        return uuid == null ? PerkSet.EMPTY : cache.computeIfAbsent(uuid, this::compute);
    }

    /** Drops the cached totals; the next read recomputes them. */
    public void invalidate(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
        }
    }

    public void invalidateAll() {
        cache.clear();
    }

    /** Forgets a player entirely, cooldowns included (they logged out). */
    public void forget(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
            secondChanceReady.remove(uuid);
        }
    }

    private PerkSet compute(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        Map<Perk, Double> totals = new EnumMap<>(Perk.class);
        for (Map.Entry<String, Integer> entry : profile.getNodes().entrySet()) {
            SkillNode node = trees.getNode(entry.getKey());
            if (node == null || entry.getValue() <= 0) {
                continue; // deleted from trees.yml, or not actually owned
            }
            int level = Math.min(entry.getValue(), node.getMaxLevel());
            for (NodeEffects.PerkBonus bonus : node.getEffects().perks()) {
                totals.merge(bonus.perk(), bonus.amountFor(level),
                        bonus.perk().stacking()::combine);
            }
        }
        return totals.isEmpty() ? PerkSet.EMPTY : new PerkSet(totals);
    }

    // ------------------------------------------------------------------
    // Second Chance
    // ------------------------------------------------------------------

    /**
     * Claims a player's Second Chance if it's off cooldown, starting the next
     * cooldown as a side effect.
     *
     * @return true when the death should be cancelled
     */
    public boolean useSecondChance(UUID uuid, double cooldownMinutes) {
        long now = System.currentTimeMillis();
        Long ready = secondChanceReady.get(uuid);
        if (ready != null && now < ready) {
            return false;
        }
        long cooldown = (long) Math.max(1.0, cooldownMinutes) * 60_000L;
        secondChanceReady.put(uuid, now + cooldown);
        return true;
    }

    /** Seconds until a player's Second Chance is ready again, or 0. */
    public long secondChanceCooldown(UUID uuid) {
        Long ready = secondChanceReady.get(uuid);
        if (ready == null) {
            return 0L;
        }
        return Math.max(0L, (ready - System.currentTimeMillis()) / 1000L);
    }

    // ------------------------------------------------------------------
    // Self repair
    // ------------------------------------------------------------------

    /**
     * Starts the {@link Perk#SELF_REPAIR} timer. It's a timer rather than an
     * event because there is no "item was used" event that covers every way an
     * item takes wear.
     */
    public void startMending(Plugin plugin) {
        stop();
        mendTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                int points = (int) Math.round(of(player).value(Perk.SELF_REPAIR));
                if (points > 0) {
                    mend(player.getInventory().getItemInMainHand(), points);
                }
            }
        }, MEND_INTERVAL_TICKS, MEND_INTERVAL_TICKS);
    }

    public void stop() {
        if (mendTask != null) {
            mendTask.cancel();
            mendTask = null;
        }
    }

    private void mend(ItemStack stack, int points) {
        if (stack == null || stack.getType().isAir() || stack.getType().getMaxDurability() <= 0) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return;
        }
        damageable.setDamage(Math.max(0, damageable.getDamage() - points));
        stack.setItemMeta(meta);
    }
}
