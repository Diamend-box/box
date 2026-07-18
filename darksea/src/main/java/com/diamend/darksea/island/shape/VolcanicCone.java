package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A basalt volcano smoldering in the outer sea: magma veins streak the
 * flanks, columnar basalt breaks the surf, and the crater glows from a
 * contained lava well. The treasure is no longer on show — a lava tube
 * opens at the shore and doglegs into a magma-lit chamber under the
 * mountain where the chest sits with its guardian. The cinder ridge up the
 * south face is still the way to the crater view.
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

        int baseR = 12 + 2 * (tier - 2) + rng.nextInt(2);   // t3 14-15, t4 16-17
        int height = 14 + 4 * (tier - 2) + rng.nextInt(2);  // t3 18-19, t4 22-23
        int blackstoneBias = tier == 4 ? 42 : 18;

        // Black-sand beach ring and the underwater root.
        s.disc(0, -1, 0, baseR + 5, VolcanicCone::blackSand, 0.25);
        s.disc(0, 0, 0, baseR + 3, VolcanicCone::blackSand, 0.2);
        s.blob(0, -3, 0, baseR + 3, 3, baseR + 3, r -> volcanicRock(r, blackstoneBias), 0.25);

        // The cone: concave profile, column-streaked magma veins on the skin.
        for (int y = 0; y <= height; y++) {
            double r = Math.max(1.8, baseR * Math.pow(1.0 - (double) y / (height + 2), 1.2));
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
            double r = 2.0 + (y - (height - 3)) * 0.9;
            for (int x = (int) -r; x <= r; x++) {
                for (int z = (int) -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        s.carve(x, y, z);
                    }
                }
            }
        }
        s.disc(0, floorY, 0, 3.2, ShapeSketch.solid("BLACKSTONE"), 0.1);
        s.put(1, floorY - 1, 1, "BLACKSTONE");
        s.put(1, floorY, 1, "LAVA");
        s.put(-1, floorY, 1, "MAGMA_BLOCK");
        s.put(0, floorY, -2, "MAGMA_BLOCK");
        s.put(2, floorY, -1, "MAGMA_BLOCK");

        // The lava tube: mouth on the north-east-to-west shore (never the
        // ridge side), boring inward at beach level, doglegging to a chamber
        // under the cone. Floor is guaranteed the whole way.
        double tubeAngle = Math.PI + rng.nextDouble() * Math.PI;        // -z half
        double tx = Math.cos(tubeAngle), tz = Math.sin(tubeAngle);
        int qx, qz;                       // unit perpendicular, dominant axis only
        if (Math.abs(tx) >= Math.abs(tz)) {
            qx = 0;
            qz = 1;
        } else {
            qx = 1;
            qz = 0;
        }
        for (int d = baseR + 3; d >= 5; d--) {
            int cx = (int) Math.round(tx * d), cz = (int) Math.round(tz * d);
            for (int w = 0; w <= 1; w++) {
                int wx = cx + qx * w, wz = cz + qz * w;
                s.put(wx, 0, wz, volcanicRock(rng, blackstoneBias));
                s.carve(wx, 1, wz);
                s.carve(wx, 2, wz);
            }
        }
        int side = rng.nextBoolean() ? 1 : -1;
        int bendX = (int) Math.round(tx * 5), bendZ = (int) Math.round(tz * 5);
        for (int off = 1; off <= 3; off++) {
            int cx = bendX + qx * side * off, cz = bendZ + qz * side * off;
            s.put(cx, 0, cz, volcanicRock(rng, blackstoneBias));
            s.carve(cx, 1, cz);
            s.carve(cx, 2, cz);
        }
        int chX = bendX + qx * side * 4, chZ = bendZ + qz * side * 4;
        s.carveBlob(chX, 2.2, chZ, 3.4, 2.2, 3.4);
        s.disc(chX, 0, chZ, 3.4, r -> volcanicRock(r, blackstoneBias), 0.1);
        for (int i = 0; i < 4; i++) {  // magma-lit floor
            int mx = chX + rng.nextInt(5) - 2, mz = chZ + rng.nextInt(5) - 2;
            s.put(mx, 0, mz, "MAGMA_BLOCK");
        }

        // Chest on a polished pedestal at the chamber's back wall.
        int chestX = chX + qx * side * 2, chestZ = chZ + qz * side * 2;
        s.put(chestX, 0, chestZ, p.rockDetail());
        s.carve(chestX, 1, chestZ);
        s.carve(chestX, 2, chestZ);
        Rel chest = new Rel(chestX, 1, chestZ);
        int glowX = chX - (int) Math.round(tx), glowZ = chZ - (int) Math.round(tz);
        s.put(glowX, 0, glowZ, "BLACKSTONE");
        s.carve(glowX, 1, glowZ);
        s.put(glowX, 1, glowZ, p.glow());

        // Cinder ridge: a two-wide climbable stair up the south face.
        int wander = 0;
        for (int y = 1; y <= height; y++) {
            double r = Math.max(1.8, baseR * Math.pow(1.0 - (double) y / (height + 2), 1.2));
            wander += rng.nextInt(3) - 1;
            wander = Math.max(-2, Math.min(2, wander));
            s.put(wander, y, (int) Math.round(r), volcanicRock(rng, blackstoneBias));
            s.put(wander + 1, y, (int) Math.round(r), volcanicRock(rng, blackstoneBias));
        }

        // Fumaroles: glowing vents bored into the flanks.
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI;  // north half, away from the ridge
            int vy = height / 2 + rng.nextInt(4) - 1;
            double r = baseR * Math.pow(1.0 - (double) vy / (height + 2), 1.2);
            int vx = (int) Math.round(Math.cos(a) * r);
            int vz = (int) -Math.round(Math.sin(a) * r);
            s.carve(vx, vy, vz);
            s.put((int) Math.round(Math.cos(a) * (r - 1)), vy, (int) -Math.round(Math.sin(a) * (r - 1)),
                    "MAGMA_BLOCK");
        }

        // Columnar basalt rising from the surf around the base.
        int columns = 6 + rng.nextInt(3);
        for (int i = 0; i < columns; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int cx = (int) Math.round(Math.cos(a) * (baseR + 4 + rng.nextInt(2)));
            int cz = (int) Math.round(Math.sin(a) * (baseR + 4 + rng.nextInt(2)));
            s.column(cx, -3, rng.nextInt(4), cz, ShapeSketch.solid("BASALT"));
        }

        // Mobs: one on the black beach, one on a flank shelf by the ridge,
        // one guarding the chamber.
        List<Rel> mobs = new ArrayList<>();
        double beachAngle = rng.nextDouble() * Math.PI * 2;
        int mx = (int) Math.round(Math.cos(beachAngle) * (baseR + 2));
        int mz = (int) Math.round(Math.sin(beachAngle) * (baseR + 2));
        mobs.add(s.stand(mx, mz, VolcanicCone::blackSand));
        int shelfY = height / 2;
        double shelfR = baseR * Math.pow(1.0 - (double) shelfY / (height + 2), 1.2);
        int sx = 2, sz = (int) Math.round(shelfR) + 1;
        s.fillBox(sx - 1, shelfY - 1, sz - 1, sx + 1, shelfY - 1, sz + 1,
                r -> volcanicRock(r, blackstoneBias));
        s.carveBox(sx - 1, shelfY, sz - 1, sx + 1, shelfY + 1, sz + 1);
        mobs.add(new Rel(sx, shelfY, sz));
        int gx = chX - qx * side, gz = chZ - qz * side;
        s.put(gx, 0, gz, "BLACKSTONE");
        s.carve(gx, 1, gz);
        s.carve(gx, 2, gz);
        mobs.add(new Rel(gx, 1, gz));

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
