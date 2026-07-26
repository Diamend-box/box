package com.diamend.darksea.world.cultist;

/**
 * How long one crystal block takes to extract, given what the player is holding.
 *
 * <p><strong>Why this exists at all.</strong> Vanilla block hardness cannot be
 * overridden — it is baked into the block type — and at endgame pickaxe speeds
 * every block that reads as a crystal breaks instantly. So the caves do not use
 * vanilla mining time; {@link ExtractionChannel} drives its own timer and asks
 * this class how long to run for. That means we choose the curve rather than
 * inheriting it.
 *
 * <p><strong>The reference-gear framing.</strong> A crystal's configured
 * {@code mine-seconds} is not the time with a bare pick — it is the time with
 * the pickaxe a player is expected to have by the time they reach the caves,
 * declared in {@code ores.yml} as {@code reference-tool} and
 * {@code reference-efficiency}. Actual time scales by the ratio of reference
 * dig-speed to the player's. This is deliberately the right way round: the
 * shipped numbers become a <em>floor</em> on extraction time and therefore a
 * <em>ceiling</em> on supply. Better-than-reference gear is the only thing that
 * beats it, and {@link #FLOOR_SECONDS_MIN} stops even that reaching zero.
 *
 * <p>Bukkit-free on purpose — the whole tool curve is arithmetic over
 * (tier, Efficiency, Haste, Mining Fatigue), so it can be asserted in CI without
 * a server. The caller resolves the held item to a {@link Tier} by name.
 */
public final class MiningSpeed {

    /**
     * Pickaxe material tiers and their vanilla dig-speed bases. Gold is faster
     * than netherite in vanilla and is left that way — surprising, but it is the
     * number players already have intuitions about, and inventing our own tier
     * table would make the curve unpredictable for no gain.
     */
    public enum Tier {
        WOODEN(2.0),
        STONE(4.0),
        IRON(6.0),
        DIAMOND(8.0),
        NETHERITE(9.0),
        GOLDEN(12.0);

        private final double base;

        Tier(double base) {
            this.base = base;
        }

        public double base() {
            return base;
        }
    }

    /** No channel may ever be shorter than this, whatever the gear stack says. */
    public static final double FLOOR_SECONDS_MIN = 0.05;

    /**
     * No channel may ever be longer than this. A deep Mining Fatigue stack drives
     * the speed ratio toward zero, and without a ceiling the resulting channel
     * would outlive the player's patience and probably their session.
     */
    public static final double MAX_SECONDS = 60.0;

    private MiningSpeed() {
    }

    /**
     * Resolves a pickaxe material name to its tier, or {@code null} if the item
     * is not a pickaxe. Crystals are pickaxe-only: the caller refuses the
     * interaction outright rather than opening a very long channel, because a
     * player swinging a sword at a geode should be told why nothing is
     * happening.
     */
    public static Tier tierOf(String materialName) {
        if (materialName == null || !materialName.endsWith("_PICKAXE")) {
            return null;
        }
        String prefix = materialName.substring(0, materialName.length() - "_PICKAXE".length());
        for (Tier tier : Tier.values()) {
            if (tier.name().equals(prefix)) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Vanilla dig speed for a correct tool. Efficiency contributes
     * {@code level² + 1}, which is why the curve is so steep at high levels:
     * Efficiency 5 adds 26 to a netherite pickaxe's 9, while Efficiency 15 adds
     * 226. That spread is the intended gate on the caves — a mid-game pickaxe
     * works, and takes several times as long.
     *
     * @param haste   Haste level (1-based; 0 for none)
     * @param fatigue Mining Fatigue level (1-based; 0 for none)
     */
    public static double speed(Tier tier, int efficiency, int haste, int fatigue) {
        if (tier == null) {
            return 0.0;
        }
        double speed = tier.base();
        if (efficiency > 0) {
            speed += (double) efficiency * efficiency + 1.0;
        }
        if (haste > 0) {
            speed *= 1.0 + 0.2 * haste;
        }
        if (fatigue > 0) {
            // Vanilla caps the stacking at level 4; beyond that it is already
            // effectively zero and further levels only risk underflow.
            speed *= Math.pow(0.3, Math.min(fatigue, 4));
        }
        return speed;
    }

    /**
     * Extraction time for one block, in seconds.
     *
     * <p>Degrades safely rather than blowing up: a zero or non-finite player
     * speed (no tool, or a fatigue stack that has underflowed) yields
     * {@link #MAX_SECONDS} instead of infinity, and the result is always clamped
     * into {@code [floorSeconds, MAX_SECONDS]}.
     */
    public static double seconds(double referenceSeconds, double referenceSpeed,
                                 double playerSpeed, double floorSeconds) {
        double floor = Math.max(FLOOR_SECONDS_MIN, floorSeconds);
        double wanted = Math.max(floor, referenceSeconds);
        if (!Double.isFinite(playerSpeed) || playerSpeed <= 0.0
                || !Double.isFinite(referenceSpeed) || referenceSpeed <= 0.0) {
            return MAX_SECONDS;
        }
        double scaled = wanted * (referenceSpeed / playerSpeed);
        if (!Double.isFinite(scaled)) {
            return MAX_SECONDS;
        }
        return Math.min(MAX_SECONDS, Math.max(floor, scaled));
    }

    /** Ticks for a channel of this many seconds — at least one, so it always advances. */
    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }
}
