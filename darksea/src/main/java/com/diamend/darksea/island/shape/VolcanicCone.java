package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A basalt volcano smoldering in the outer sea. The crater holds a real
 * lava well ringed by a walkable ledge, molten seeps glow on the terraced
 * flanks, and columnar basalt breaks the surf. Loot is never on show: a
 * lava tube at the shore doglegs into a magma-lit chamber (the main chest,
 * behind a molten seam in the floor), a fumarole grotto halfway up hides a
 * second cache, and at tier 4 a branch off the tube hides a third. The
 * cinder ridge up the south face ends at a fire-cult shrine on the rim.
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
        return 5;
    }

    @Override
    public int rarityWeight(int tier) {
        // A rare intruder out in the Trench (ring 5), where the Mariphage
        // nest takes about half the ring; an ordinary shape in its home rings.
        return tier >= 5 ? 2 : rarityWeight();
    }

    @Override
    public String bossMob() {
        return "MariphageCore";   // only *sometimes* raised — see residentBossChance
    }

    @Override
    public String bossFallback() {
        return "WARDEN";
    }

    @Override
    public double residentBossChance(int tier) {
        // A dead volcano the Order overran out in the Trench sometimes hides a
        // Core in its magma-lit heart; never in the calmer rings.
        return tier >= 5 ? 0.12 : 0.0;
    }

    @Override
    public int mobCapBonus(int tier) {
        // Overrun by the plague out in the Trench; an ordinary cone elsewhere.
        return tier >= 5 ? 12 : 0;
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        Palette p = Palette.forTier(tier);
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        int baseR = 12 + 2 * (tier - 2) + rng.nextInt(2);   // t3 14-15, t4 16-17
        int height = 14 + 4 * (tier - 2) + rng.nextInt(2);  // t3 18-19, t4 22-23
        int bias = tier >= 4 ? 45 : 22;                     // blackstone patch share
        ShapeSketch.Shader rock = (x, y, z, r) -> volcanic(x, y, z, r, bias);

        // Black-sand beach ring and the underwater root.
        s.disc(0, -1, 0, baseR + 5, VolcanicCone::blackSand, 0.25);
        s.disc(0, 0, 0, baseR + 3, VolcanicCone::blackSand, 0.2);
        s.blob(0, -3, 0, baseR + 3, 3, baseR + 3, rock, 0.25);

        // The cone: concave profile, column-streaked magma veins on the skin.
        for (int y = 0; y <= height; y++) {
            double r = coneR(baseR, height, y);
            final double shell = r - 1.4;
            s.disc(0, y, 0, r, rock, 0.1);
            for (int x = (int) -r - 1; x <= r + 1; x++) {
                for (int z = (int) -r - 1; z <= r + 1; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d >= shell && d <= r + 0.4 && s.solidAt(x, y, z) && veinAt(x, z)) {
                        s.put(x, y, z, "MAGMA_BLOCK");
                    }
                }
            }
        }

        // Crater: carved bowl, then a real lava well — nine blocks of melt
        // with a forced containment ring that doubles as the rim ledge.
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
        int wellY = floorY + 1;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int d2 = x * x + z * z;
                if (d2 <= 2) {
                    s.put(x, floorY, z, "BLACKSTONE");
                    s.put(x, wellY, z, "LAVA");
                } else if (d2 <= 9 && !s.solidAt(x, wellY, z)) {
                    s.put(x, wellY, z, volcanic(x, wellY, z, rng, bias));
                }
            }
        }
        s.put(1, wellY, 1, "MAGMA_BLOCK");   // crusted islands in the melt
        s.put(-1, wellY, -1, "MAGMA_BLOCK");

        // The lava tube: mouth on the north shore (never the ridge side),
        // boring inward at beach level, doglegging to a chamber under the
        // cone. Floor is guaranteed the whole way.
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
                s.put(wx, 0, wz, volcanic(wx, 0, wz, rng, bias));
                s.carve(wx, 1, wz);
                s.carve(wx, 2, wz);
            }
        }
        int side = rng.nextBoolean() ? 1 : -1;
        int bendX = (int) Math.round(tx * 5), bendZ = (int) Math.round(tz * 5);
        for (int off = 1; off <= 3; off++) {
            int cx = bendX + qx * side * off, cz = bendZ + qz * side * off;
            s.put(cx, 0, cz, volcanic(cx, 0, cz, rng, bias));
            s.carve(cx, 1, cz);
            s.carve(cx, 2, cz);
        }
        int chX = bendX + qx * side * 4, chZ = bendZ + qz * side * 4;
        s.carveBlob(chX, 2.2, chZ, 3.4, 2.2, 3.4);
        s.disc(chX, 0, chZ, 3.4, rock, 0.1);

        // A molten seam splits the chamber floor between door and chest.
        for (int seam = 0; seam <= 1; seam++) {
            int lx = chX + qx * side * seam, lz = chZ + qz * side * seam;
            if (!s.solidAt(lx, -1, lz)) {
                s.put(lx, -1, lz, "BLACKSTONE");
            }
            s.put(lx, 0, lz, "LAVA");
        }
        for (int i = 0; i < 3; i++) {  // magma-crusted patches around it
            int mx = chX + rng.nextInt(5) - 2, mz = chZ + rng.nextInt(5) - 2;
            s.put(mx, 0, mz, "MAGMA_BLOCK");
        }

        // Main chest on a polished pedestal at the chamber's back wall.
        List<Rel> chests = new ArrayList<>();
        int chestX = chX + qx * side * 2, chestZ = chZ + qz * side * 2;
        s.put(chestX, 0, chestZ, p.rockDetail());
        s.carve(chestX, 1, chestZ);
        s.carve(chestX, 2, chestZ);
        chests.add(new Rel(chestX, 1, chestZ));
        int glowX = chX - (int) Math.round(tx), glowZ = chZ - (int) Math.round(tz);
        s.put(glowX, 0, glowZ, "BLACKSTONE");
        s.carve(glowX, 1, glowZ);
        s.put(glowX, 1, glowZ, p.glow());

        // Cinder ridge: a two-wide climbable stair up the south face, ending
        // at a shrine on the rim — the fire cult prayed to the well below.
        int wander = 0;
        for (int y = 1; y <= height; y++) {
            double r = coneR(baseR, height, y);
            wander += rng.nextInt(3) - 1;
            wander = Math.max(-2, Math.min(2, wander));
            int rz = (int) Math.round(r);
            s.put(wander, y, rz, volcanic(wander, y, rz, rng, bias));
            s.put(wander + 1, y, rz, volcanic(wander + 1, y, rz, rng, bias));
        }
        s.put(0, height - 1, 3, "POLISHED_BLACKSTONE");
        s.put(0, height, 3, "CHISELED_POLISHED_BLACKSTONE");
        s.put(0, height + 1, 3, "WITHER_SKELETON_SKULL");
        s.put(1, height - 1, 3, "POLISHED_BLACKSTONE");
        s.put(1, height, 3, "BLACK_CANDLE");

        // Fumaroles: glowing vents bored into the flanks.
        for (int i = 0; i < 3; i++) {
            double a = rng.nextDouble() * Math.PI;  // north half, away from the ridge
            int vy = height / 2 + rng.nextInt(4) - 1;
            double r = coneR(baseR, height, vy);
            int vx = (int) Math.round(Math.cos(a) * r);
            int vz = (int) -Math.round(Math.sin(a) * r);
            s.carve(vx, vy, vz);
            s.put((int) Math.round(Math.cos(a) * (r - 1)), vy, (int) -Math.round(Math.sin(a) * (r - 1)),
                    "MAGMA_BLOCK");
        }

        // The fumarole grotto: a wider vent halfway up the north face opens
        // into a small chamber — the second cache sits at its back.
        double ga = Math.PI * (0.25 + rng.nextDouble() * 0.5);
        int gy = height / 2 - 1;
        double grr = coneR(baseR, height, gy);
        double ux = Math.cos(ga), uz = -Math.sin(ga);
        int gcx = (int) Math.round(ux * (grr - 3.5)), gcz = (int) Math.round(uz * (grr - 3.5));
        s.carveBlob(gcx, gy + 1.6, gcz, 2.6, 1.8, 2.6);
        s.disc(gcx, gy, gcz, 2.8, rock, 0.1);
        for (int dx = -2; dx <= 2; dx++) {      // guaranteed grotto ceiling
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 6) {
                    continue;
                }
                for (int y = gy + 3; y <= gy + 4; y++) {
                    if (!s.solidAt(gcx + dx, y, gcz + dz)) {
                        s.put(gcx + dx, y, gcz + dz, volcanic(gcx + dx, y, gcz + dz, rng, bias));
                    }
                }
            }
        }
        for (int step = 1; step <= 4; step++) {  // the vent mouth, floored
            int mx2 = (int) Math.round(ux * (grr - 3.5 + step));
            int mz2 = (int) Math.round(uz * (grr - 3.5 + step));
            s.put(mx2, gy, mz2, volcanic(mx2, gy, mz2, rng, bias));
            s.carve(mx2, gy + 1, mz2);
            s.carve(mx2, gy + 2, mz2);
        }
        int bx2 = (int) Math.round(ux * (grr - 5.5)), bz2 = (int) Math.round(uz * (grr - 5.5));
        s.put(bx2, gy, bz2, p.rockDetail());
        s.carve(bx2, gy + 1, bz2);
        s.carve(bx2, gy + 2, bz2);
        chests.add(new Rel(bx2, gy + 1, bz2));
        int glX = gcx + (int) Math.round(uz), glZ = gcz - (int) Math.round(ux);
        if (!s.solidAt(glX, gy, glZ)) {
            s.put(glX, gy, glZ, volcanic(glX, gy, glZ, rng, bias));
        }
        s.carve(glX, gy + 1, glZ);
        s.put(glX, gy + 1, glZ, p.glow());

        // Molten seeps: pockets of standing lava on the terraced flanks —
        // placed after every carve, every neighbor forced solid, so nothing
        // ever runs downhill or bleeds into a chamber.
        int seeps = 6 + rng.nextInt(3);
        for (int i = 0; i < seeps; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            if (Math.sin(a) > 0.6) {
                continue;  // stay off the ridge face
            }
            int sy = 3 + rng.nextInt(Math.max(1, height - 8));
            double r = coneR(baseR, height, sy);
            int px = (int) Math.round(Math.cos(a) * (r - 0.8));
            int pz = (int) Math.round(Math.sin(a) * (r - 0.8));
            if (Math.abs(sy - gy) <= 4 && Math.hypot(px - gcx, pz - gcz) < 6) {
                continue;  // keep melt away from the grotto's walls
            }
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!s.solidAt(px + n[0], sy, pz + n[1])) {
                    s.put(px + n[0], sy, pz + n[1], volcanic(px + n[0], sy, pz + n[1], rng, bias));
                }
            }
            if (!s.solidAt(px, sy - 1, pz)) {
                s.put(px, sy - 1, pz, volcanic(px, sy - 1, pz, rng, bias));
            }
            s.put(px, sy, pz, "LAVA");
            s.carve(px, sy + 1, pz);
        }

        // Tier 4: a collapsed branch off the tube's dogleg hides the third
        // cache on the far side of the bend.
        if (tier >= 4) {
            for (int off = 1; off <= 2; off++) {
                int cx = bendX - qx * side * off, cz = bendZ - qz * side * off;
                s.put(cx, 0, cz, volcanic(cx, 0, cz, rng, bias));
                s.carve(cx, 1, cz);
                s.carve(cx, 2, cz);
            }
            int ccx = bendX - qx * side * 3, ccz = bendZ - qz * side * 3;
            s.carveBlob(ccx, 1.8, ccz, 2.2, 1.6, 2.2);
            s.disc(ccx, 0, ccz, 2.2, rock, 0.1);
            int c3x = ccx - qx * side, c3z = ccz - qz * side;
            s.put(c3x, 0, c3z, p.rockDetail());
            s.carve(c3x, 1, c3z);
            s.carve(c3x, 2, c3z);
            chests.add(new Rel(c3x, 1, c3z));
            s.put(ccx + (int) Math.round(tx), 0, ccz + (int) Math.round(tz), "MAGMA_BLOCK");
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
        mobs.add(s.stand(mx, mz, ShapeSketch.solid("BLACKSTONE")));
        int shelfY = height / 2;
        double shelfR = coneR(baseR, height, shelfY);
        int sx = 2, sz = (int) Math.round(shelfR) + 1;
        s.fillBox(sx - 1, shelfY - 1, sz - 1, sx + 1, shelfY - 1, sz + 1, rock);
        s.carveBox(sx - 1, shelfY, sz - 1, sx + 1, shelfY + 1, sz + 1);
        mobs.add(new Rel(sx, shelfY, sz));
        int gx = chX - qx * side, gz = chZ - qz * side;
        s.put(gx, 0, gz, "BLACKSTONE");
        s.carve(gx, 1, gz);
        s.carve(gx, 2, gz);
        mobs.add(new Rel(gx, 1, gz));

        if (tier >= 5) {
            // Overrun: the dead volcano the Order reached first crawls with the
            // plague — a ring of watchers posted around the black beach.
            for (double a : new double[]{0.6, 2.1, 3.6, 5.1}) {
                int px = (int) Math.round(Math.cos(a) * (baseR + 1));
                int pz = (int) Math.round(Math.sin(a) * (baseR + 1));
                mobs.add(s.stand(px, pz, ShapeSketch.solid("BLACKSTONE")));
            }
        }

        s.shore(radiusBudget(), VolcanicCone::blackSand);

        return ShapeBuild.of(s, chests, mobs);
    }

    /** The cone's concave radius profile at a given height. */
    private static double coneR(int baseR, int height, int y) {
        return Math.max(1.8, baseR * Math.pow(1.0 - (double) y / (height + 2), 1.2));
    }

    /** Patch-shaded volcanic rock: blackstone drifts across a basalt body. */
    private static String volcanic(int x, int y, int z, Random rng, int bias) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), Math.floorDiv(y, 4),
                Math.floorDiv(z, 3));
        if (cell < bias) {
            return rng.nextInt(100) < 85 ? "BLACKSTONE" : "BASALT";
        }
        int roll = rng.nextInt(100);
        if (roll < 88) {
            return "BASALT";
        }
        return roll < 96 ? "BLACKSTONE" : "SMOOTH_BASALT";
    }

    /** Patch-shaded black sand: gravel drifts in the blackstone shingle. */
    private static String blackSand(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 4), y, Math.floorDiv(z, 4));
        if (cell < 25) {
            return rng.nextInt(100) < 80 ? "GRAVEL" : "BLACKSTONE";
        }
        return rng.nextInt(100) < 60 ? "BLACKSTONE" : "BASALT";
    }

    /**
     * Magma veins by angular sector: a fifth of the cone's 18 sectors run
     * molten, so the glow reads as unbroken rivulets pouring down the
     * flanks instead of scattered orange speckle.
     */
    private static boolean veinAt(int x, int z) {
        int sector = (int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI * 2) * 18);
        return ShapeSketch.cellNoise(sector, 7, sector * 31) < 21
                || ShapeSketch.cellNoise(Math.floorMod(sector - 1, 18), 7,
                        Math.floorMod(sector - 1, 18) * 31) < 21;
    }
}
