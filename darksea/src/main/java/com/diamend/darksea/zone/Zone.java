package com.diamend.darksea.zone;

import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * One concentric danger ring. {@code maxRadius < 0} marks the unbounded
 * outermost ring. {@code requiredTier} is the sea-armor tier that fully
 * negates this ring's effects.
 */
public record Zone(String id, String displayName, double maxRadius, int requiredTier, List<ZoneEffect> effects) {

    public record ZoneEffect(PotionEffectType type, int amplifier) {
    }

    public boolean unbounded() {
        return maxRadius < 0;
    }
}
