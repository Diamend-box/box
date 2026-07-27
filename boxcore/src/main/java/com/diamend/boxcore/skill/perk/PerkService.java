package com.diamend.boxcore.skill.perk;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import com.diamend.boxcore.skill.NodeEffects;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillTreeManager;
import org.bukkit.entity.Player;

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
 *
 * <p>This is also where the per-perk cooldowns live, for the perks that fire on
 * a trigger rather than applying continuously.
 */
public class PerkService {

    private final ProfileManager profiles;
    private final SkillTreeManager trees;
    private final Map<UUID, PerkSet> cache = new ConcurrentHashMap<>();
    /** Player → perk → when it comes off cooldown (epoch millis). */
    private final Map<UUID, Map<Perk, Long>> cooldowns = new ConcurrentHashMap<>();

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
            cooldowns.remove(uuid);
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
    // Cooldowns
    // ------------------------------------------------------------------

    /**
     * Claims a triggered perk if it's off cooldown, starting the next cooldown
     * as a side effect.
     *
     * @return true when the perk should fire
     */
    public boolean claim(UUID uuid, Perk perk, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Map<Perk, Long> forPlayer = cooldowns.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>());
        Long ready = forPlayer.get(perk);
        if (ready != null && now < ready) {
            return false;
        }
        forPlayer.put(perk, now + Math.max(0L, cooldownMillis));
        return true;
    }

    /** Seconds until a triggered perk is ready again, or 0. */
    public long cooldownRemaining(UUID uuid, Perk perk) {
        Map<Perk, Long> forPlayer = cooldowns.get(uuid);
        Long ready = forPlayer == null ? null : forPlayer.get(perk);
        if (ready == null) {
            return 0L;
        }
        return Math.max(0L, (ready - System.currentTimeMillis()) / 1000L);
    }
}
