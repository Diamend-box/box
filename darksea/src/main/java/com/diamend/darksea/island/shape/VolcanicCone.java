package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A basalt volcano smoldering in the outer sea: magma veins streak the
 * flanks, columnar basalt breaks the surf around the base, and the crater
 * glows from a contained lava well. The chest sits on the crater floor —
 * you climb the cinder ridge, crest the rim, and drop in to claim it.
 */
final class VolcanicCone implements DemoShape {

    @Override
    public String id() {
        return "volcanic-cone";
    }

    @Override
    public int minTier() {
        return 3;
    }

    @Override
    public int maxTier() {
        return 4;
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        Palette p = Palette.forTier(tier);
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        int baseR = 8 + (tier - 3) + rng.nextInt(2);
        int height = 15 + 2 * (tier - 3) + rng.nextInt(2);
        int blackstoneBias = tier == 4 ? 42 : 18;

        s.disc(0, 0, 0, baseR + 2, VolcanicCone::blackSand, 0.3);  // volcanic beach, every tier
        s.blob(0, -3, 0, baseR + 2, 3, baseR + 2, r -> volcanicRock(r, blackstoneBias), 0.3);

        // The cone, with column-streaked magma veins near the surface.
        for (int y = 0; y <= height; y++) {
            double r = Math.max(1.5, baseR * Math.pow(1.0 - (double) y / (height + 2), 1.2));
            final double shell = r - 1.4;
            s.disc(0, y, 0, r, rn -> volcanicRock(rn, blackstoneBias), 0.1);
            for (int x = (int) -r - 1; x <= r + 1; x++) {
                for (int z = (int) -r - 1; z <= r + 1; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d >= shell && d <= r + 0.4 && s.solidAt(x, y, z) && veinAt(x, z)) {
                        s.put(x, y, z, "MAGMA_BLOCK");
                    }
                }
            }
        }

        // Crater: carved bowl, blackstone floor, a contained lava well.
        int floorY = height - 4;
        for (int y = height - 3; y <= height + 2; y++) {
            double r = 1.6 + (y - (height - 3)) * 0.9;
            for (int x = (int) -r; x <= r; x++) {
                for (int z = (int) -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        s.carve(x, y, z);
                    }
                }
            }
        }
        s.disc(0, floorY, 0, 2.8, ShapeSketch.solid("BLACKSTONE"), 0.1);
        s.put(1, floorY - 1, 1, "BLACKSTONE");
        s.put(1, floorY, 1, "LAVA");
        s.put(-1, floorY, 1, "MAGMA_BLOCK");
        s.put(0, floorY, -2, "MAGMA_BLOCK");

        // Chest on the crater floor, well clear of the lava well.
        s.put(-2, floorY, 0, "BLACKSTONE");
        s.carve(-2, floorY + 1, 0);
        s.carve(-2, floorY + 2, 0);
        Rel chest = new Rel(-2, floorY + 1, 0);

        // Cinder ridge: a climbable stair of jutting blocks up the south face.
        int wander = 0;
        for (int y = 1; y <= height; y++) {
            double r = Math.max(1.5, baseR * Math.pow(1.0 - (double) y / (height + 2), 1.2));
            wander += rng.nextInt(3) - 1;
            wander = Math.max(-2, Math.min(2, wander));
            s.put(wander, y, (int) Math.round(r), volcanicRock(rng, blackstoneBias));
        }

        // Fumaroles: two glowing vents bored into the flanks.
        for (int i = 0; i < 2; i++) {
            double a = rng.nextDouble() * Math.PI;  // north half, away from the ridge
            int vy = height / 2 + rng.nextInt(3) - 1;
            double r = baseR * Math.pow(1.0 - (double) vy / (height + 2), 1.2);
            int vx = (int) Math.round(Math.cos(a) * r);
            int vz = (int) -Math.round(Math.sin(a) * r);
            s.carve(vx, vy, vz);
            s.put((int) Math.round(Math.cos(a) * (r - 1)), vy, (int) -Math.round(Math.sin(a) * (r - 1)),
                    "MAGMA_BLOCK");
        }

        // Columnar basalt rising from the surf around the base.
        int columns = 4 + rng.nextInt(3);
        for (int i = 0; i < columns; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int cx = (int) Math.round(Math.cos(a) * (baseR + 2 + rng.nextInt(2)));
            int cz = (int) Math.round(Math.sin(a) * (baseR + 2 + rng.nextInt(2)));
            s.column(cx, -3, rng.nextInt(4), cz, ShapeSketch.solid("BASALT"));
        }

        // Mobs: one on the black beach, one on a flank shelf by the ridge.
        List<Rel> mobs = new ArrayList<>();
        double beachAngle = rng.nextDouble() * Math.PI * 2;
        int mx = (int) Math.round(Math.cos(beachAngle) * (baseR + 1));
        int mz = (int) Math.round(Math.sin(beachAngle) * (baseR + 1));
        mobs.add(s.stand(mx, mz, VolcanicCone::blackSand));
        int shelfY = height / 2;
        double shelfR = baseR * Math.pow(1.0 - (double) shelfY / (height + 2), 1.2);
        int sx = 2, sz = (int) Math.round(shelfR) + 1;
        s.fillBox(sx - 1, shelfY - 1, sz - 1, sx + 1, shelfY - 1, sz + 1,
                r -> volcanicRock(r, blackstoneBias));
        s.carveBox(sx - 1, shelfY, sz - 1, sx + 1, shelfY + 1, sz + 1);
        mobs.add(new Rel(sx, shelfY, sz));

        return ShapeBuild.of(s, chest, mobs);
    }

    private static String volcanicRock(Random rng, int blackstoneBias) {
        int roll = rng.nextInt(100);
        if (roll < blackstoneBias) {
            return "BLACKSTONE";
        }
        return roll < 97 ? "BASALT" : "SMOOTH_BASALT";
    }

    private static String blackSand(java.util.Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 25) {
            return "GRAVEL";
        }
        return roll < 65 ? "BLACKSTONE" : "BASALT";
    }

    /** Column-hash vein noise so magma streaks run vertically down the cone. */
    private static boolean veinAt(int x, int z) {
        long hash = x * 341873128712L + z * 132897987541L;
        hash ^= hash >>> 13;
        hash *= 0x5DEECE66DL;
        hash ^= hash >>> 15;
        return (hash & 0xFF) < 30;
    }
}
