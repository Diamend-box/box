package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A jagged rock pillar rising well clear of the water, with a smaller
 * companion stack offshore. The loot chest hides in a sea-cave grotto carved
 * into the base — invisible from a boat; you have to land and circle the
 * rock to find the mouth. Parkour ledges spiral toward the summit where a
 * sentry mob waits.
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

        int baseR = (int) Math.round((5 + 0.5 * tier)) + rng.nextInt(2);
        int height = 12 + 2 * tier + rng.nextInt(3);

        // Beach skirt and underwater root the whole stack grows out of.
        s.disc(0, 0, 0, baseR + 2.5, p::groundMix, 0.35);
        s.blob(0, -2.5, 0, baseR + 2, 3.0, baseR + 2, p::rockMix, 0.2);

        // Main mass: stacked, offset blobs tapering hard to a tip — tall and
        // narrow reads "spire", low jitter keeps the faces solid.
        int lean1x = rng.nextInt(3) - 1, lean1z = rng.nextInt(3) - 1;
        int lean2x = lean1x + rng.nextInt(3) - 1, lean2z = lean1z + rng.nextInt(3) - 1;
        s.blob(0, 1, 0, baseR, 3.4, baseR, p::rockMix, 0.2);
        s.blob(lean1x, height * 0.38, lean1z, baseR * 0.62, height * 0.26, baseR * 0.62,
                p::rockMix, 0.18);
        s.blob(lean2x, height * 0.7, lean2z, baseR * 0.4, height * 0.2, baseR * 0.4,
                p::rockMix, 0.16);
        s.blob(lean2x, height * 0.9, lean2z, 1.6, height * 0.14, 1.6, p::rockMix, 0.14);

        // Companion stack a little offshore.
        double sideAngle = rng.nextDouble() * Math.PI * 2;
        int sx = (int) Math.round(Math.cos(sideAngle) * (baseR + 3.5));
        int sz = (int) Math.round(Math.sin(sideAngle) * (baseR + 3.5));
        s.blob(sx, -2, sz, 3.0, 2.5, 3.0, p::rockMix, 0.2);
        s.blob(sx, height * 0.18, sz, 1.9, height * 0.24, 1.9, p::rockMix, 0.2);

        // Grotto: mouth on a random side, pocket behind it, chest at the back.
        int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] mouth = cardinals[rng.nextInt(4)];
        int mx = mouth[0], mz = mouth[1];
        int px = mx * (baseR - 3), pz = mz * (baseR - 3);
        s.carveBlob(px, 1.6, pz, 2.4, 1.8, 2.4);
        for (int d = baseR - 3; d <= baseR + 1; d++) {
            for (int y = 1; y <= 2; y++) {
                s.carve(mx * d, y, mz * d);
                s.carve(mx * d + Math.abs(mz), y, mz * d + Math.abs(mx));
            }
        }
        int chestX = mx * (baseR - 4), chestZ = mz * (baseR - 4);
        s.put(chestX, 0, chestZ, p.rockDetail());
        s.carve(chestX, 1, chestZ);
        s.carve(chestX, 2, chestZ);
        Rel chest = new Rel(chestX, 1, chestZ);
        int glowX = chestX + Math.abs(mz), glowZ = chestZ + Math.abs(mx);
        s.put(glowX, 0, glowZ, p.rockDetail());
        s.carve(glowX, 1, glowZ);
        s.put(glowX, 1, glowZ, p.glow());

        // Spiral parkour ledges: walk a ray out from the axis at each height
        // and hang the step off the outermost rock face, so ledges always
        // touch the wall instead of floating.
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int y = 3; y <= height - 2; y += 2) {
            double angle = phase + y * 1.05;
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
                int lx = faceX + (int) Math.signum(Math.round(dirX));
                int lz = faceZ + (int) Math.signum(Math.round(dirZ));
                if (!s.solidAt(lx, y, lz)) {
                    s.put(lx, y, lz, p.rockMix(rng));
                }
            }
        }

        // Glowing sea pickles on the drowned rock.
        for (int i = 0; i < 5; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int wx = (int) Math.round(Math.cos(a) * (baseR + 1 + rng.nextInt(2)));
            int wz = (int) Math.round(Math.sin(a) * (baseR + 1 + rng.nextInt(2)));
            s.put(wx, -2, wz, p.rock());
            s.put(wx, -1, wz, "SEA_PICKLE");
        }

        // Mobs: one on the beach opposite the grotto, one on the summit.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(-mx * (baseR + 1), -mz * (baseR + 1), p::groundMix));
        mobs.add(s.stand(lean2x, lean2z, p::rockMix));

        return ShapeBuild.of(s, chest, mobs);
    }
}
