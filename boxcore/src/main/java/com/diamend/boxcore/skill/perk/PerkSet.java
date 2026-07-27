package com.diamend.boxcore.skill.perk;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The perks one player currently has, already totalled across every node they
 * own. Immutable — {@link PerkService} throws the whole set away and rebuilds
 * it when a player's nodes change.
 */
public final class PerkSet {

    public static final PerkSet EMPTY = new PerkSet(new EnumMap<>(Perk.class));

    private final Map<Perk, Double> values;

    PerkSet(Map<Perk, Double> values) {
        this.values = values;
    }

    public boolean has(Perk perk) {
        return values.containsKey(perk);
    }

    /** The perk's total, or 0 when the player doesn't have it. */
    public double value(Perk perk) {
        Double value = values.get(perk);
        return value == null ? 0.0 : value;
    }

    /**
     * The perk's total clamped to 0-1, for perks used as a probability. A
     * misconfigured {@code amount: 5} then means "always", not "500% of a roll".
     */
    public double chance(Perk perk) {
        return Math.max(0.0, Math.min(1.0, value(perk)));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<Perk, Double> values() {
        return Collections.unmodifiableMap(values);
    }
}
