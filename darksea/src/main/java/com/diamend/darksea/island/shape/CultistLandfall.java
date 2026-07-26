package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The cultist landfall — where the Order came ashore, and the only way down
 * into the caves beneath.
 *
 * <p>Unlike every other shape this one is <em>not</em> in any ring's pool.
 * There is exactly one of it in the world, at a fixed position, placed the way
 * the home island is. That is why {@link #minTier()} sits above every
 * generated ring: {@code forTier} can never return it, so no amount of
 * rolling will ever put a second one in the water.
 *
 * <p>Read from a boat it is a black shelf of rock with a stepped ziggurat cut
 * into it, ringed by the stumps of pillars. At the top, sunk into the platform,
 * is a frame of crying obsidian around a floor of black concrete: the way
 * down. The building materials are the caves' own — basalt, blackstone, soul
 * soil — so the island reads as a piece of somewhere else pushed up through
 * the sea floor rather than anything the Naxome built.
 *
 * <p>Public where every other shape is package-private: the placer has to name
 * it to place the one of it, and the portal service has to know where its pad
 * sits.
 */
public final class CultistLandfall implements DemoShape {

    /** The id the registry stores and the portal service looks the island up by. */
    public static final String ID = "cultist-landfall";

    /** Where the portal floor sits, relative to the island origin. */
    public static final int PORTAL_Y = 9;

    /** Half-width of the portal's black floor — a 3x3 pad at the ziggurat's top. */
    public static final int PORTAL_HALF = 1;

    @Override
    public String id() {
        return ID;
    }

    /**
     * Above every ring the generator builds (islands-per-ring stops at 5), so
     * this shape is unreachable through {@code forTier} and can only ever be
     * placed deliberately.
     */
    @Override
    public int minTier() {
        return 9;
    }

    @Override
    public int maxTier() {
        return 9;
    }

    @Override
    public int rarityWeight() {
        return 0;
    }

    @Override
    public int chestCount(int tier) {
        return 3;
    }

    @Override
    public int vaultChestCount() {
        return 1;
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        // The shelf: a wide, low mass of black rock that barely clears the
        // water, so the ziggurat on top of it is what you actually see.
        s.disc(0, -1, 0, 26, CultistLandfall::shelfPatch, 0.18);
        s.disc(0, 0, 0, 24, CultistLandfall::shelfPatch, 0.18);
        s.blob(0, -4, 0, 22, 5.0, 22, CultistLandfall::shelfPatch, 0.2);
        s.dome(0, 1, 0, 20, 3, CultistLandfall::shelfPatch, 0.16);

        // The ziggurat: four shrinking tiers of dressed blackstone. Each step
        // is two blocks tall so it climbs without needing stairs cut into it.
        int[] radii = {14, 11, 8, 5};
        int y = 3;
        for (int radius : radii) {
            s.disc(0, y, 0, radius, CultistLandfall::dressedPatch, 0.06);
            s.disc(0, y + 1, 0, radius, CultistLandfall::dressedPatch, 0.06);
            y += 2;
        }

        // The summit platform the portal sits in.
        s.disc(0, y, 0, 5, CultistLandfall::dressedPatch, 0.04);

        // Pillar stumps ringing the second step — snapped off at different
        // heights, so the ring reads as ruined rather than decorative.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int px = (int) Math.round(Math.cos(angle) * 12);
            int pz = (int) Math.round(Math.sin(angle) * 12);
            int height = 3 + rng.nextInt(5);
            s.column(px, 5, 5 + height, pz, ShapeSketch.solid("POLISHED_BLACKSTONE"));
            if (rng.nextBoolean()) {
                s.put(px, 6 + height, pz, "CHISELED_POLISHED_BLACKSTONE");
            }
        }

        // The portal itself: a frame of crying obsidian sunk into the summit,
        // around a floor of black concrete. Deliberately not a nether portal
        // frame — vanilla portals do their own linking, which would fight the
        // fixed destination this one has to have.
        int frameY = PORTAL_Y;
        s.carveBox(-3, frameY, -3, 3, frameY + 4, 3);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                s.put(x, frameY - 1, z, edge ? "CRYING_OBSIDIAN" : "BLACK_CONCRETE");
            }
        }
        // Four short corner posts, so the mouth reads as a structure.
        for (int[] corner : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            s.column(corner[0], frameY, frameY + 2, corner[1],
                    ShapeSketch.solid("CRYING_OBSIDIAN"));
        }
        s.put(0, frameY + 3, 0, "SOUL_LANTERN");

        // Soul soil creeping out from the portal — the thing underneath
        // leaking upward.
        for (int i = 0; i < 40; i++) {
            int sx = rng.nextInt(19) - 9;
            int sz = rng.nextInt(19) - 9;
            int top = s.topY(sx, sz, Integer.MIN_VALUE);
            if (top > 0 && top < frameY) {
                s.put(sx, top, sz, rng.nextInt(4) == 0 ? "SOUL_SAND" : "SOUL_SOIL");
            }
        }

        // Three caches: two out on the shelf where the Order camped, and one
        // tucked under the ziggurat's lowest step.
        List<Rel> chests = new ArrayList<>();
        chests.add(cache(s, 16, 4));
        chests.add(cache(s, -13, -12));
        s.carveBox(-2, 3, 15, 2, 4, 17);
        s.put(0, 3, 16, "CHEST");
        chests.add(new Rel(0, 3, 16));

        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(0, 7, ShapeSketch.solid("POLISHED_BLACKSTONE")));
        mobs.add(s.stand(-7, -7, ShapeSketch.solid("POLISHED_BLACKSTONE")));
        mobs.add(s.stand(10, -3, ShapeSketch.solid("BASALT")));

        s.shore(30, CultistLandfall::shorePatch);
        return ShapeBuild.of(s, chests, mobs);
    }

    /** A chest sunk one block into the shelf, with air above it to open into. */
    private Rel cache(ShapeSketch s, int x, int z) {
        int top = s.topY(x, z, 0);
        s.carve(x, top + 1, z);
        s.carve(x, top + 2, z);
        s.put(x, top, z, "CHEST");
        return new Rel(x, top, z);
    }

    private static String shelfPatch(int x, int y, int z, Random rng) {
        int roll = ShapeSketch.cellNoise(Math.floorDiv(x, 3), Math.floorDiv(y, 4),
                Math.floorDiv(z, 3));
        if (roll < 45) {
            return "BASALT";
        }
        if (roll < 75) {
            return "SMOOTH_BASALT";
        }
        return roll < 92 ? "BLACKSTONE" : "POLISHED_BASALT";
    }

    private static String dressedPatch(int x, int y, int z, Random rng) {
        int roll = ShapeSketch.cellNoise(Math.floorDiv(x, 2), y, Math.floorDiv(z, 2));
        if (roll < 50) {
            return "POLISHED_BLACKSTONE";
        }
        if (roll < 78) {
            return "POLISHED_BLACKSTONE_BRICKS";
        }
        return roll < 93 ? "CRACKED_POLISHED_BLACKSTONE_BRICKS" : "GILDED_BLACKSTONE";
    }

    private static String shorePatch(int x, int y, int z, Random rng) {
        int roll = ShapeSketch.cellNoise(Math.floorDiv(x, 3), y, Math.floorDiv(z, 3));
        return roll < 60 ? "BLACK_CONCRETE_POWDER" : "GRAVEL";
    }
}
