package com.diamend.robobear.mob;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * One entry in the roster of things the challenge can send after you.
 *
 * <p>The ladder gets harder by sending <i>different</i> enemies, not the same
 * husk with more hearts. A husk with forty health reads as a bug; something you
 * have not seen before reads as round seven. So an archetype is mostly a round
 * it becomes available on and a weight for how often it turns up after that,
 * and the escalation is the roster rather than a multiplier.
 *
 * @param id         key in {@code config.yml}, and what {@code /rb mobs} prints
 * @param name       what floats above it, with colour codes
 * @param type       the vanilla entity underneath
 * @param minRound   the first round it can appear on
 * @param weight     relative chance against the others available; 0 never rolls
 * @param bonusHealth extra health on top of the vanilla amount
 * @param elite      one-at-a-time, and only on a milestone round
 * @param heldItem   put in its main hand, or null to leave the vanilla kit
 */
public record MobArchetype(
        String id,
        String name,
        EntityType type,
        int minRound,
        int weight,
        double bonusHealth,
        boolean elite,
        Material heldItem) {

    /** Whether this archetype may be rolled into the ordinary population. */
    public boolean availableAt(int round) {
        return !elite && weight > 0 && round >= minRound;
    }
}
