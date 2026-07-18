package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Two dune mounds joined by a wading-depth sandbar — the gentle face of the
 * near sea. Gentle, but not free: the chest sits in a low hollow dug under a
 * rock cap on the far mound's seaward edge, so from the water you only see a
 * boulder pile. Driftwood and dead scrub sell the castaway look.
 */
final class TwinAtoll implements DemoShape {

    @Override
    public String id() {
        return "twin-atoll";
    }

    @Override
    public int minTier() {
        return 1;
    }

    @Override
    public int maxTier() {
        return 2;
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        Palette p = Palette.forTier(tier);
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        int ax = -(7 + rng.nextInt(2)), az = rng.nextInt(5) - 2;
        int bx = 7 + rng.nextInt(2), bz = rng.nextInt(5) - 2;
        int rA = 5 + rng.nextInt(2), rB = 4 + rng.nextInt(2);

        // Roots, mounds, and the bowed sandbar between them.
        s.blob(ax, -2.5, az, rA + 1.5, 2.5, rA + 1.5, p::groundMix, 0.18);
        s.blob(bx, -2.5, bz, rB + 1.5, 2.5, rB + 1.5, p::groundMix, 0.18);
        s.dome(ax, -1, az, rA + 1, 5, p::groundMix, 0.12);
        s.dome(bx, -1, bz, rB + 1, 4, p::groundMix, 0.12);
        double bow = (rng.nextBoolean() ? 1 : -1) * 2.5;
        for (double t = 0; t <= 1.0; t += 0.1) {
            double cx = ax + (bx - ax) * t;
            double cz = az + (bz - az) * t + Math.sin(t * Math.PI) * bow;
            s.disc(cx, -1, cz, 2.6, p::groundMix, 0.15);
            s.disc(cx, 0, cz, 1.7, p::groundMix, 0.15);
        }

        // Rock cap on mound A's seaward edge, hollow beneath, chest inside.
        int capX = ax - (rA - 1);
        int capZ = az + rng.nextInt(3) - 1;
        int capBase = Math.max(2, s.topY(capX, capZ, 2));
        s.blob(capX, capBase + 1.5, capZ, 2.7, 1.8, 2.7, p::rockMix, 0.18);
        s.fillBox(capX - 1, 0, capZ - 1, capX + 1, 0, capZ + 1, p::rockMix);
        s.carveBox(capX - 1, 1, capZ - 1, capX + 1, 2, capZ + 1);
        for (int y = 1; y <= 2; y++) {  // seal the landward side so it's a nook, not a tunnel
            s.put(capX + 2, y, capZ, p.rockMix(rng));
        }
        Rel chest = new Rel(capX + 1, 1, capZ);
        s.put(capX - 1, 1, capZ - 1, p.glow());

        // Driftwood runs and dead scrub on the beaches.
        for (int run = 0; run < 2; run++) {
            int wx = (run == 0 ? ax : bx) + rng.nextInt(5) - 2;
            int wz = (run == 0 ? az : bz) + (run == 0 ? rA : rB) - 2;
            int len = 2 + rng.nextInt(2);
            for (int i = 0; i < len; i++) {
                int lx = wx + i;
                int top = s.topY(lx, wz, -1);
                if (top >= 0) {
                    s.put(lx, top + 1, wz, "STRIPPED_OAK_LOG");
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            boolean onA = rng.nextBoolean();
            int sx = (onA ? ax : bx) + rng.nextInt(2 * (onA ? rA : rB) - 1) - (onA ? rA : rB) + 1;
            int sz = (onA ? az : bz) + rng.nextInt(2 * (onA ? rA : rB) - 1) - (onA ? rA : rB) + 1;
            int top = s.topY(sx, sz, -1);
            if (top >= 1 && s.solidAt(sx, top, sz)) {
                s.put(sx, top, sz, "SAND");  // dead bushes only root in sand
                s.put(sx, top + 1, sz, "DEAD_BUSH");
            }
        }

        // Mobs: one crowning mound B, one wading mid-bar.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(bx, bz, p::groundMix));
        mobs.add(s.stand((ax + bx) / 2, (az + bz) / 2 + (int) Math.round(bow), p::groundMix));

        return ShapeBuild.of(s, chest, mobs);
    }
}
