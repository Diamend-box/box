package com.diamend.darksea.world.cultist;

/**
 * Decides, for any block position, whether the cultist caves are open there.
 * Pure arithmetic on (x, y, z) and a seed — no Bukkit — which is what lets the
 * cave shape be unit tested before a server ever runs it.
 *
 * <p>Two noise fields do the work, and the split matters. <em>Tunnels</em> come
 * from ridged noise — {@code |n| < threshold} — because the thin shell either
 * side of a zero crossing forms long connected passages rather than the
 * isolated bubbles a plain {@code n > threshold} test would give. <em>Caverns</em>
 * come from a second, much coarser field thresholded normally, which opens the
 * occasional room big enough to hold a vein and a fight. Tunnels alone would be
 * a claustrophobic maze; caverns alone would be disconnected pockets.
 *
 * <p>Everything is clamped inside a square bound and between a floor and a
 * roof, so the world is a sealed box: no sky, no digging out the side, and a
 * fixed amount of disk on a small server.
 */
public final class CultistCarve {

    /**
     * Defaults tuned by sweeping seeds and measuring the result: about 21% of
     * the band open, and over 95% of that reachable on foot from the arrival
     * chamber on every seed tried.
     *
     * <p>The tunnel threshold is the load-bearing number and it sits just above
     * a cliff. At 0.035 the network still measures 21% open but connectivity
     * collapses to single digits — the same volume of air, chopped into sealed
     * pockets, with the arrival chamber stranded in one of them. Lowering this
     * to make the caves "tighter" is the one change here that will quietly
     * produce an unplayable world, which is why a connectivity floor is
     * asserted in the tests rather than eyeballed.
     */
    public static final double TUNNEL_SCALE = 1 / 34.0;
    public static final double TUNNEL_THRESHOLD = 0.04;
    public static final double CAVERN_SCALE = 1 / 78.0;
    public static final double CAVERN_THRESHOLD = 0.35;

    /** Vertical squash: caves spread sideways more than they climb. */
    public static final double VERTICAL_SQUASH = 1.7;

    private final CaveNoise tunnels;
    private final CaveNoise caverns;
    private final int floorY;
    private final int roofY;
    private final int halfExtent;
    private final int chamberRadius;

    private final double tunnelScale;
    private final double tunnelThreshold;
    private final double cavernScale;
    private final double cavernThreshold;

    /**
     * @param seed          world seed; the same seed always carves the same caves
     * @param floorY        lowest open Y (bedrock sits below it)
     * @param roofY         highest open Y (solid roof sits above it)
     * @param halfExtent    half the world's width in blocks — 256 for a 512x512
     * @param chamberRadius radius of the guaranteed-open arrival chamber at the
     *                      origin, so the portal never drops anyone into stone
     */
    public CultistCarve(long seed, int floorY, int roofY, int halfExtent, int chamberRadius) {
        this(seed, floorY, roofY, halfExtent, chamberRadius,
                TUNNEL_SCALE, TUNNEL_THRESHOLD, CAVERN_SCALE, CAVERN_THRESHOLD);
    }

    public CultistCarve(long seed, int floorY, int roofY, int halfExtent, int chamberRadius,
                        double tunnelScale, double tunnelThreshold,
                        double cavernScale, double cavernThreshold) {
        this.tunnels = new CaveNoise(seed);
        this.caverns = new CaveNoise(seed ^ 0x5DEECE66DL);
        this.floorY = floorY;
        this.roofY = roofY;
        this.halfExtent = Math.max(1, halfExtent);
        this.chamberRadius = Math.max(0, chamberRadius);
        this.tunnelScale = tunnelScale;
        this.tunnelThreshold = tunnelThreshold;
        this.cavernScale = cavernScale;
        this.cavernThreshold = cavernThreshold;
    }

    /** Whether this column is inside the world's square bound. */
    public boolean inBounds(int x, int z) {
        return Math.abs(x) <= halfExtent && Math.abs(z) <= halfExtent;
    }

    /** Whether this position is inside the guaranteed-open arrival chamber. */
    public boolean inChamber(int x, int y, int z) {
        if (chamberRadius <= 0 || y < floorY || y > floorY + chamberRadius) {
            return false;
        }
        return x * x + z * z <= chamberRadius * chamberRadius;
    }

    /**
     * Whether the caves are open (air) at this block. False outside the bound
     * or outside the vertical band, so callers can fill those with bedrock and
     * stone without a second check.
     */
    public boolean isOpen(int x, int y, int z) {
        if (!inBounds(x, z) || y <= floorY || y >= roofY) {
            return false;
        }
        if (inChamber(x, y, z)) {
            return true;
        }
        // Ridged: the thin band either side of the zero crossing is a passage.
        double tunnel = tunnels.octaves(
                x * tunnelScale, y * tunnelScale * VERTICAL_SQUASH, z * tunnelScale, 3, 0.5);
        if (Math.abs(tunnel) < tunnelThreshold) {
            return true;
        }
        double cavern = caverns.octaves(
                x * cavernScale, y * cavernScale * VERTICAL_SQUASH, z * cavernScale, 2, 0.5);
        return cavern > cavernThreshold;
    }

    /**
     * The fraction of a sampled box that is open. Used by the tests to keep the
     * caves inside a sane band — a world that is 2% open is a solid block, and
     * one that is 60% open is a hollow shell with nothing to mine into.
     */
    public double openFraction(int fromX, int fromZ, int toX, int toZ, int step) {
        int open = 0;
        int total = 0;
        for (int x = fromX; x <= toX; x += step) {
            for (int z = fromZ; z <= toZ; z += step) {
                for (int y = floorY + 1; y < roofY; y += step) {
                    total++;
                    if (isOpen(x, y, z)) {
                        open++;
                    }
                }
            }
        }
        return total == 0 ? 0.0 : (double) open / total;
    }

    public int floorY() {
        return floorY;
    }

    public int roofY() {
        return roofY;
    }

    public int halfExtent() {
        return halfExtent;
    }

    public int chamberRadius() {
        return chamberRadius;
    }
}
