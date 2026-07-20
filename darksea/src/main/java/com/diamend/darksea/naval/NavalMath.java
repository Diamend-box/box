package com.diamend.darksea.naval;

import org.bukkit.util.Vector;

/**
 * The pure arithmetic of naval combat, kept Bukkit-server-free so plain
 * JUnit can hold the balance promises:
 *
 * <ul>
 *   <li>A ram's force is the CLOSING speed — how fast two hulls actually
 *       converge — never raw speed, so boats sailing in formation don't
 *       grind each other down.</li>
 *   <li>Damage splits defender-heavy (75/25 by default): charging always
 *       costs the rammer something, and each side's toughness only softens
 *       its own share.</li>
 *   <li>A wounded hull is a slow hull: the slow factor caps speed below
 *       the healthy ceiling for a few seconds after a hit.</li>
 * </ul>
 */
public final class NavalMath {

    private NavalMath() {
    }

    /**
     * How fast boat A is converging on boat B, in blocks/tick: the component
     * of the relative velocity along the line from A to B. Positive means
     * closing, negative or zero means holding distance or separating. Two
     * boats occupying the same point read as their full relative speed.
     */
    public static double closingSpeed(Vector posA, Vector velA, Vector posB, Vector velB) {
        Vector toB = posB.clone().subtract(posA);
        toB.setY(0);
        Vector relative = velA.clone().subtract(velB);
        relative.setY(0);
        double distance = toB.length();
        if (distance < 1e-6) {
            return relative.length();
        }
        return relative.dot(toB) / distance;
    }

    /**
     * A boat's own contribution to a collision: its velocity component toward
     * the other hull, floored at zero. The boat contributing more is the
     * attacker — it chose the charge.
     */
    public static double approachSpeed(Vector pos, Vector vel, Vector otherPos) {
        Vector toOther = otherPos.clone().subtract(pos);
        toOther.setY(0);
        double distance = toOther.length();
        if (distance < 1e-6) {
            return Math.max(0, vel.clone().setY(0).length());
        }
        return Math.max(0, vel.clone().setY(0).dot(toOther) / distance);
    }

    /** The total force of a ram: closing speed scaled to damage, capped. */
    public static double ramBaseDamage(double closingSpeed, double damagePerSpeed, double maxDamage) {
        return Math.min(maxDamage, Math.max(0, closingSpeed) * damagePerSpeed);
    }

    /**
     * One side's hull damage from a ram: its share of the base (defender 0.75,
     * attacker 0.25 by default), softened by its OWN toughness only —
     * a Stormrunner's plating never protects the Rowboat that hit it.
     */
    public static double hullShare(double baseDamage, double share, double toughness) {
        return baseDamage * share / Math.max(1.0, toughness);
    }

    /**
     * The speed factor of a wounded hull: {@code woundedFactor} while the
     * slow is live, 1.0 once it lapses. {@code factor} is clamped to
     * [0.05, 1.0] so a config typo can't freeze or speed up boats.
     */
    public static double woundedFactor(long nowMillis, long slowUntilMillis, double factor) {
        if (nowMillis >= slowUntilMillis) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.05, factor));
    }

    /**
     * The horizontal knockback a rammed boat receives: away from the
     * attacker, scaled by {@code strength}, with a touch of lift so the hull
     * visibly lurches. Zero-distance collisions shove along the attacker's
     * heading instead so the vector is never NaN.
     */
    public static Vector ramKnockback(Vector attackerPos, Vector attackerVel, Vector victimPos,
                                      double strength) {
        Vector away = victimPos.clone().subtract(attackerPos);
        away.setY(0);
        if (away.lengthSquared() < 1e-9) {
            away = attackerVel.clone().setY(0);
            if (away.lengthSquared() < 1e-9) {
                return new Vector(0, 0, 0);
            }
        }
        return away.normalize().multiply(strength).setY(0.1);
    }

    /** Whether a surge is off cooldown. */
    public static boolean surgeReady(long nowMillis, long readyAtMillis) {
        return nowMillis >= readyAtMillis;
    }
}
