package com.diamend.boxcore.collection;

import java.util.List;

/**
 * One threshold in a collection: gather {@code amount} of the item and the
 * rewards are paid out, once.
 *
 * @param amount   lifetime total that unlocks this tier
 * @param points   skill points paid out
 * @param xp       vanilla experience paid out
 * @param message  optional extra chat line (MiniMessage)
 * @param commands console commands run on reaching the tier
 */
public record CollectionTier(long amount, int points, int xp, String message, List<String> commands) {

    public CollectionTier(long amount, int points) {
        this(amount, points, 0, "", List.of());
    }

    public boolean hasRewards() {
        return points > 0 || xp > 0 || !commands.isEmpty();
    }
}
