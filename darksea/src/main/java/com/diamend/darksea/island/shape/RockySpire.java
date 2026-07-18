package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A jagged rock pillar rising well clear of the water, with two companion
 * stacks offshore. The loot chest hides two rooms deep: a sea-cave mouth
 * leads to a grotto, and a side passage doglegs off it into a smaller
 * chamber — nothing is visible from a boat. Wide parkour ledges spiral
 * toward the summit where a sentry mob waits.
 *
 * Size steps 2 blocks of base radius per tier while the random jitter is
 * only 1, so across any two seeds a higher-tier spire is never smaller.
 */
final class RockySpire implements DemoShape {

    @Override
    public String id() {
        return "rocky-spire";
    }

    @Override
    public int minTier() {
        return 1;
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

        int baseR = 9 + 2 * tier + rng.nextInt(2);      // t1 11-12 .. t4 17-18
        int height = 14 + 4 * tier + rng.nextInt(3);    // t1 18-20 .. t4 30-32

        // Beach skirt, wet rim, and the underwater root the stack grows from.
        s.disc(0, -1, 0, baseR + 6, p::groundPatch, 0.2);
        s.disc(0, 0, 0, baseR + 5, p::groundPatch, 0.2);
        s.blob(0, -3, 0, baseR + 3, 3.5, baseR + 3, p::rockPatch, 0.2);

        // Main mass: stacked, offset blobs tapering hard to a tip — tall and
        // narrow reads "spire"; patch shading keeps the faces from turning
        // into per-block static.
        int lean1x = rng.nextInt(3) - 1, lean1z = rng.nextInt(3) - 1;
        int lean2x = lean1x + rng.nextInt(3) - 1, lean2z = lean1z + rng.nextInt(3) - 1;
        s.blob(0, 1, 0, baseR, 5.0, baseR, p::rockPatch, 0.2);
        s.blob(lean1x, height * 0.36, lean1z, baseR * 0.64, height * 0.26, baseR * 0.64,
                p::rockPatch, 0.18);
        s.blob(lean2x, height * 0.66, lean2z, baseR * 0.42, height * 0.2, baseR * 0.42,
                p::rockPatch, 0.16);
        s.blob(lean2x, height * 0.88, lean2z, Math.max(2.4, baseR * 0.18), height * 0.14,
                Math.max(2.4, baseR * 0.18), p::rockPatch, 0.14);

        // Two companion stacks offshore: one tall sibling, one stub.
        double sideAngle = rng.nextDouble() * Math.PI * 2;
        int sx = (int) Math.round(Math.cos(sideAngle) * (baseR + 3));
        int sz = (int) Math.round(Math.sin(sideAngle) * (baseR + 3));
        s.blob(sx, -2, sz, 4.2, 3.0, 4.2, p::rockPatch, 0.2);
        s.blob(sx, height * 0.2, sz, 2.6, height * 0.26, 2.6, p::rockPatch, 0.2);
        int s2x = (int) Math.round(Math.cos(sideAngle + 2.5) * (baseR + 2));
        int s2z = (int) Math.round(Math.sin(sideAngle + 2.5) * (baseR + 2));
        s.blob(s2x, -1, s2z, 2.6, 2.4, 2.6, p::rockPatch, 0.2);
        s.blob(s2x, 2.4, s2z, 1.6, 2.2, 1.6, p::rockPatch, 0.2);

        // Grotto network. A mouth tunnel bores in from a random side to a
        // first pocket; a side passage doglegs off it to the chest chamber,
        // so the loot is two turns deep in the dark.
        int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] mouth = cardinals[rng.nextInt(4)];
        int mx = mouth[0], mz = mouth[1];
        int qx = Math.abs(mz), qz = Math.abs(mx);   // perpendicular axis
        int side = rng.nextBoolean() ? 1 : -1;      // which way the dogleg turns

        for (int d = baseR - 4; d <= baseR + 2; d++) {
            for (int w = 0; w <= 1; w++) {
                int cx = mx * d + qx * w, cz = mz * d + qz * w;
                s.put(cx, 0, cz, p.rockMix(rng));   // guaranteed dry floor
                s.carve(cx, 1, cz);
                s.carve(cx, 2, cz);
            }
        }
        int c1x = mx * (baseR - 5), c1z = mz * (baseR - 5);
        s.carveBlob(c1x, 1.8, c1z, 2.8, 2.0, 2.8);
        s.disc(c1x, 0, c1z, 2.6, p::rockMix, 0.1);
        for (int off = 1; off <= 5; off++) {
            int cx = c1x + qx * side * off, cz = c1z + qz * side * off;
            s.put(cx, 0, cz, p.rockMix(rng));
            s.carve(cx, 1, cz);
            s.carve(cx, 2, cz);
        }
        int c2x = c1x + qx * side * 6, c2z = c1z + qz * side * 6;
        s.carveBlob(c2x, 1.8, c2z, 2.6, 2.0, 2.6);
        s.disc(c2x, 0, c2z, 2.4, p::rockMix, 0.1);
        for (int[] room : new int[][]{{c1x, c1z}, {c2x, c2z}}) {
            for (int dx = -3; dx <= 3; dx++) {      // guaranteed grotto ceiling —
                for (int dz = -3; dz <= 3; dz++) {  // the carve can nick the skin
                    if (dx * dx + dz * dz > 11) {
                        continue;
                    }
                    for (int y = 3; y <= 4; y++) {
                        if (!s.solidAt(room[0] + dx, y, room[1] + dz)) {
                            s.put(room[0] + dx, y, room[1] + dz, p.rockMix(rng));
                        }
                    }
                }
            }
        }

        List<Rel> chests = new ArrayList<>();
        int chestX = c2x + qx * side, chestZ = c2z + qz * side;
        s.put(chestX, 0, chestZ, p.rockDetail());
        s.carve(chestX, 1, chestZ);
        s.carve(chestX, 2, chestZ);
        chests.add(new Rel(chestX, 1, chestZ));
        int glowX = c2x - mx, glowZ = c2z - mz;
        s.put(glowX, 0, glowZ, p.rockDetail());
        s.carve(glowX, 1, glowZ);
        s.put(glowX, 1, glowZ, p.glow());

        // Whoever found this grotto first left an offering niche in the
        // first pocket: a plinth, a skull, and a candle that burned out.
        int nx = c1x - qx * side, nz = c1z - qz * side;
        s.put(nx, 0, nz, p.rockDetail());
        s.carve(nx, 1, nz);
        s.put(nx, 1, nz, "SKELETON_SKULL");
        s.put(nx + mx, 0, nz + mz, p.rock());
        s.carve(nx + mx, 1, nz + mz);
        s.put(nx + mx, 1, nz + mz, "BLACK_CANDLE");

        // Tier 3+: a second cache in the first pocket, behind the offering.
        if (tier >= 3) {
            int c2ax = c1x - mx, c2az = c1z - mz;
            s.put(c2ax, 0, c2az, p.rockDetail());
            s.carve(c2ax, 1, c2az);
            s.carve(c2ax, 2, c2az);
            chests.add(new Rel(c2ax, 1, c2az));
        }

        // Tier 4: a wind-hollow near the shoulder of the spire — a carved
        // cell inside the upper mass, reached by a short tunnel off the
        // spiral ledges. Floor, ceiling and back wall are all guaranteed.
        if (tier >= 4) {
            int hy = (int) Math.round(height * 0.66);
            int hollowLen = (int) Math.round(baseR * 0.42) + 3;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    s.put(lean2x + dx, hy, lean2z + dz, p.rockPatch(lean2x + dx, hy,
                            lean2z + dz, rng));
                    s.carve(lean2x + dx, hy + 1, lean2z + dz);
                    s.carve(lean2x + dx, hy + 2, lean2z + dz);
                    for (int y = hy + 3; y <= hy + 4; y++) {
                        if (!s.solidAt(lean2x + dx, y, lean2z + dz)) {
                            s.put(lean2x + dx, y, lean2z + dz, p.rockMix(rng));
                        }
                    }
                }
            }
            for (int d = 2; d <= hollowLen; d++) {  // the way in, off the ledges
                int cx = lean2x + mx * d, cz = lean2z + mz * d;
                s.put(cx, hy, cz, p.rockMix(rng));
                s.carve(cx, hy + 1, cz);
                s.carve(cx, hy + 2, cz);
            }
            int hcx = lean2x - mx, hcz = lean2z - mz;
            chests.add(new Rel(hcx, hy + 1, hcz));
            s.put(lean2x + qx, hy + 1, lean2z + qz, p.glow());
        }

        // Spiral parkour ledges: walk a ray out from the axis at each height
        // and hang a two-wide step off the outermost rock face, so ledges
        // always touch the wall and are wide enough to actually land on.
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int y = 4; y <= height - 3; y += 2) {
            double angle = phase + y * 0.85;
            double dirX = Math.cos(angle), dirZ = Math.sin(angle);
            int faceX = 0, faceZ = 0;
            boolean found = false;
            for (double r = 1.0; r <= baseR + 2; r += 0.5) {
                int cx = (int) Math.round(dirX * r), cz = (int) Math.round(dirZ * r);
                if (s.solidAt(cx, y, cz)) {
                    faceX = cx;
                    faceZ = cz;
                    found = true;
                }
            }
            if (found) {
                int outX = (int) Math.signum(Math.round(dirX));
                int outZ = (int) Math.signum(Math.round(dirZ));
                int lx = faceX + outX, lz = faceZ + outZ;
                if (!s.solidAt(lx, y, lz)) {
                    s.put(lx, y, lz, p.rockMix(rng));
                    int tx = lx - (int) Math.signum(outZ), tz = lz + (int) Math.signum(outX);
                    if (!s.solidAt(tx, y, tz)) {
                        s.put(tx, y, tz, p.rockMix(rng));  // widen along the wall
                    }
                }
            }
        }

        // Glowing sea pickles on the drowned rock.
        for (int i = 0; i < 8; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int wx = (int) Math.round(Math.cos(a) * (baseR + 3 + rng.nextInt(3)));
            int wz = (int) Math.round(Math.sin(a) * (baseR + 3 + rng.nextInt(3)));
            s.put(wx, -2, wz, p.rock());
            s.put(wx, -1, wz, "SEA_PICKLE");
        }

        // Mobs: beach opposite the grotto, the summit sentry, and from tier 3
        // a third lurking on the companion stack.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(-mx * (baseR + 2), -mz * (baseR + 2), p::groundMix));
        mobs.add(s.stand(lean2x, lean2z, p::rockMix));
        if (tier >= 3) {
            mobs.add(s.stand(sx, sz, p::rockMix));
        }

        return ShapeBuild.of(s, chests, mobs);
    }
}
