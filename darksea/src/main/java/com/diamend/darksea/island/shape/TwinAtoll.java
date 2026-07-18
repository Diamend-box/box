package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A broken atoll ring: two dune mounds face each other across a sheltered
 * lagoon, joined by low sandbar arcs with storm-torn channels you can row
 * through. The chest hides in a crawl-hollow under a boulder pile on the
 * far mound's seaward flank — from the water it's just rocks. Driftwood,
 * dead scrub and seagrass sell the castaway look.
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

        int ringR = 13 + 3 * (tier - 1) + rng.nextInt(2);  // t1 13-14, t2 16-17
        int rA = 8 + tier + rng.nextInt(2);                // west mound (the chest one)
        int rB = 7 + tier + rng.nextInt(2);                // east mound

        // The two mounds, rooted underwater, domed above.
        int ax = -ringR, bx = ringR;
        int az = rng.nextInt(5) - 2, bz = rng.nextInt(5) - 2;
        s.blob(ax, -2.5, az, rA + 1, 2.8, rA + 1, p::groundMix, 0.12);
        s.blob(bx, -2.5, bz, rB + 1, 2.8, rB + 1, p::groundMix, 0.12);
        s.dome(ax, -1, az, rA + 1, 6, p::groundMix, 0.12);
        s.dome(bx, -1, bz, rB + 1, 5, p::groundMix, 0.12);

        // Sandbar arcs close the ring north and south (mounds sit at angles
        // PI and 0). The south arc is storm-broken: two channels let a boat
        // slip into the lagoon.
        int gap1 = 3 + rng.nextInt(3), gap2 = 8 + rng.nextInt(3);
        for (int arc = 0; arc < 2; arc++) {
            for (int i = 0; i <= 14; i++) {
                if (arc == 1 && (Math.abs(i - gap1) < 2 || Math.abs(i - gap2) < 2)) {
                    continue;  // the channels
                }
                double t = i / 14.0;
                double a = arc == 0 ? Math.PI + t * Math.PI   // north half, via -z
                        : Math.PI - t * Math.PI;              // south half, via +z
                double wobble = 1.0 + 0.05 * Math.sin(t * Math.PI * 3 + (seed % 7));
                double cx = Math.cos(a) * ringR * wobble;
                double cz = Math.sin(a) * ringR * wobble * 1.18;  // storm-stretched ring
                s.disc(cx, -1, cz, 3.2, p::groundMix, 0.15);
                s.disc(cx, 0, cz, 2.4, p::groundMix, 0.15);
                if (rng.nextInt(100) < 40) {
                    s.disc(cx, 1, cz, 1.2, p::groundMix, 0.1);  // dunelets
                }
            }
        }

        // Lagoon floor: shallow it up to wading depth with a sandy bottom.
        s.disc(0, -3, 0, ringR - 2, p::groundMix, 0.15);
        s.disc(0, -2, 0, ringR - 4, p::groundMix, 0.15);

        // Seagrass and pickle-light in the shallows.
        for (int i = 0; i < 8; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = rng.nextDouble() * (ringR - 6);
            int gx = (int) Math.round(Math.cos(a) * r);
            int gz = (int) Math.round(Math.sin(a) * r);
            int top = s.topY(gx, gz, -9);
            if (top >= -3 && top <= -2 && s.solidAt(gx, top, gz)) {
                s.put(gx, top + 1, gz, "SEAGRASS");
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int px = (int) Math.round(Math.cos(a) * (ringR - 5));
            int pz = (int) Math.round(Math.sin(a) * (ringR - 5));
            s.put(px, -2, pz, p.rock());
            s.put(px, -1, pz, "SEA_PICKLE");
        }

        // Boulder pile on mound A's seaward flank; crawl-hollow underneath,
        // chest at the landward end, entrance facing the open sea.
        int capX = ax - (rA - 2);
        int capZ = az + rng.nextInt(3) - 1;
        int capBase = Math.max(1, s.topY(capX, capZ, 1));
        s.fillBox(capX - 2, capBase - 1, capZ - 2, capX + 2, capBase, capZ + 2, p::rockMix);
        s.blob(capX, capBase + 1.6, capZ, 3.2, 2.2, 3.2, p::rockMix, 0.15);
        s.blob(capX - 2, capBase + 1.0, capZ + 1, 2.2, 1.6, 2.2, p::rockMix, 0.15);
        s.carveBox(capX - 1, capBase + 1, capZ - 1, capX + 1, capBase + 2, capZ + 1);
        for (int y = capBase + 1; y <= capBase + 2; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                s.put(capX + 2, y, capZ + dz, p.rockMix(rng));  // seal landward
            }
        }
        for (int d = 2; d <= 4; d++) {
            s.put(capX - d, capBase, capZ, p.rockMix(rng));     // crawl floor
            s.carve(capX - d, capBase + 1, capZ);               // seaward crawl gap
        }
        Rel chest = new Rel(capX + 1, capBase + 1, capZ);
        s.put(capX - 1, capBase + 1, capZ - 1, p.glow());

        // A weathered stone circle crowns the east mound — whoever raised it
        // is long gone; one skull still watches from a fallen stone.
        for (int i = 0; i < 4; i++) {
            int ox = i == 0 ? 3 : i == 1 ? -3 : 0;
            int oz = i == 2 ? 3 : i == 3 ? -3 : 0;
            int gx = bx + ox, gz = bz + oz;
            int base = s.topY(gx, gz, 0);
            int h = 1 + (ShapeSketch.cellNoise(gx, 0, gz) % 2);
            for (int y = 1; y <= h; y++) {
                s.put(gx, base + y, gz, "MOSSY_COBBLESTONE");
            }
            if (i == 3) {
                s.put(gx, base + h + 1, gz, "SKELETON_SKULL");
            }
        }

        // Driftwood runs and dead scrub on both beaches.
        for (int run = 0; run < 3; run++) {
            int cx = (run == 0 ? ax : run == 1 ? bx : 0);
            int cz = (run == 0 ? az + rA - 2 : run == 1 ? bz + rB - 2 : -ringR);
            int wx = cx + rng.nextInt(5) - 2;
            int len = 3 + rng.nextInt(3);
            for (int i = 0; i < len; i++) {
                int lx = wx + i;
                int top = s.topY(lx, cz, -1);
                if (top >= 0) {
                    s.put(lx, top + 1, cz, "STRIPPED_OAK_LOG");
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            boolean onA = rng.nextBoolean();
            int cx = onA ? ax : bx, cz = onA ? az : bz, cr = onA ? rA : rB;
            int sx = cx + rng.nextInt(2 * cr - 1) - cr + 1;
            int sz = cz + rng.nextInt(2 * cr - 1) - cr + 1;
            int top = s.topY(sx, sz, -1);
            if (top >= 1 && s.solidAt(sx, top, sz)) {
                s.put(sx, top, sz, "SAND");  // dead bushes only root in sand
                s.put(sx, top + 1, sz, "DEAD_BUSH");
            }
        }

        // Mobs: one crowning mound B, one on the north arc, one wading the
        // south channel shallows at tier 2.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(bx, bz, p::groundMix));
        mobs.add(s.stand(0, -ringR, p::groundMix));
        if (tier >= 2) {
            mobs.add(s.stand(0, ringR, p::groundMix));
        }

        return ShapeBuild.of(s, List.of(chest), mobs);
    }
}
