package com.diamend.darksea.zone;

import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * One concentric danger ring. {@code maxRadius < 0} marks the unbounded
 * outermost ring. {@code requiredTier} is the sea-armor tier that fully
 * negates this ring's effects — unless {@code bypassProtection} is set, in
 * which case armor and boat shield don't reduce it at all and everyone in the
 * ring suffers its full effects (the lethal outer rim: crossable only by
 * out-healing the damage with golden apples, never by gear).
 */
public record Zone(String id, String displayName, double maxRadius, int requiredTier,
                   List<ZoneEffect> effects, boolean bypassProtection) {

    public record ZoneEffect(PotionEffectType type, int amplifier) {
    }

    public boolean unbounded() {
        return maxRadius < 0;
    }
}
