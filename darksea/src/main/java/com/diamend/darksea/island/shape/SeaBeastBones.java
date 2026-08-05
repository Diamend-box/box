package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Whatever this was, something bigger killed it. The carcass lies curled in
 * a death-arc across a long sandbar: a bleached ribcage marches toward a
 * hollow skull big enough to walk into (lantern-light glows through its eye
 * sockets at night), and vertebrae sink into the sea off the tail. The
 * chest sits deep in the cranium, entered through the jaw.
 */
final class SeaBeastBones implements DemoShape {

    @Override
    public String id() {
        return "sea-beast-bones";
    }

    @Override
    public int minTier() {
        return 2;
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

        int rx = 16 + 2 * tier + rng.nextInt(2);   // half-length: t2 20-21 .. t4 24-25
        int rz = 6 + rng.nextInt(2);               // bar half-width
        int bend = 16 + 2 * tier + rng.nextInt(2); // how hard the carcass curls

        // The bar: a long domed spit curling in z, wet skirt at the waterline.
        // zc(x) is the carcass centerline the spine and ribs follow.
        for (int i = -2; i <= 2; i++) {
            double t = i / 2.0;
            s.blob(t * rx * 0.7, -3, zc(t * rx * 0.7, rx, bend), rx * 0.28, 2.5, rz + 3,
                    p::groundMix, 0.25);
        }
        for (int x = -rx; x <= rx; x++) {
            double w = rz * Math.sqrt(Math.max(0.0, 1.0 - (double) (x * x) / (rx * rx)));
            double center = zc(x, rx, bend);
            for (int dz = (int) -w - 3; dz <= w + 3; dz++) {
                int z = (int) Math.round(center) + dz;
                double u = Math.abs(dz) / Math.max(w, 0.001);
                if (u <= 1.4) {
                    s.put(x, -1, z, p.groundMix(rng));
                }
                if (u <= 1.0) {
                    s.put(x, 0, z, p.groundMix(rng));
                }
                if (u <= 0.5) {
                    s.put(x, 1, z, p.groundMix(rng));
                }
            }
        }

        // Ribs: bone arches over the centerline, taller toward the head (+x).
        int ribs = 4 + (tier + 1) / 2 + (tier == 4 ? 1 : 0);   // t2 5, t3 6, t4 7
        int firstRib = (int) (-rx * 0.62);
        int lastRib = (int) (rx * 0.34);
        for (int i = 0; i < ribs; i++) {
            int x = firstRib + (lastRib - firstRib) * i / (ribs - 1);
            double w = Math.max(3.5,
                    rz * Math.sqrt(Math.max(0.0, 1.0 - (double) (x * x) / (rx * rx))) + 1.5);
            int h = 6 + tier / 2 + Math.round(3.0f * i / (ribs - 1)) + rng.nextInt(2);
            int center = (int) Math.round(zc(x, rx, bend));
            int prevY = 0;
            for (double t = 0; t <= Math.PI + 0.01; t += Math.PI / 32) {
                int z = center + (int) Math.round(Math.cos(t) * w);
                int y = (int) Math.round(Math.sin(t) * h);
                s.put(x, y, z, "BONE_BLOCK");
                for (int fy = Math.min(prevY, y) + 1; fy < Math.max(prevY, y); fy++) {
                    s.put(x, fy, z, "BONE_BLOCK");  // keep steep sides connected
                }
                prevY = y;
            }
        }

        // Spine along the ridge, riding the curl, rising toward the skull.
        int spineStart = (int) (-rx * 0.8);
        int spineEnd = lastRib + 3;
        for (int x = spineStart; x <= spineEnd; x++) {
            double progress = (x - spineStart) / (double) (spineEnd - spineStart);
            int y = (int) Math.round(2 + progress * 5.5);
            int z = (int) Math.round(zc(x, rx, bend) + Math.sin(x * 0.45) * 1.2);
            s.put(x, y, z, "BONE_BLOCK");
            if (rng.nextInt(100) < 30) {
                s.put(x, y - 1, z, "BONE_BLOCK");  // thicker vertebrae here and there
            }
        }

        // Tail vertebrae sinking into the sea past the bar's end.
        for (int i = 1; i <= 8; i++) {
            int x = spineStart - i;
            int y = Math.max(-2, 2 - (i + 1) / 2);
            if (i % 2 == 1) {
                s.put(x, y, (int) Math.round(zc(x, rx, bend)), "BONE_BLOCK");
            }
        }

        // The skull: a hollow bone cave at the head end, jaw gaping seaward.
        int skullX = rx - 7;
        int skullZ = (int) Math.round(zc(skullX, rx, bend));
        s.fillBox(skullX - 4, 0, skullZ - 3, skullX + 3, 0, skullZ + 3,
                ShapeSketch.solid("BONE_BLOCK"));
        s.blob(skullX, 2.6, skullZ, 4.2, 3.2, 3.6, ShapeSketch.solid("BONE_BLOCK"), 0.1);
        s.carveBlob(skullX - 0.5, 2.2, skullZ, 2.9, 2.2, 2.2);
        s.carveBox(skullX + 2, 1, skullZ - 1, skullX + 5, 2, skullZ + 1);   // mouth
        s.fillBox(skullX + 2, 0, skullZ - 1, skullX + 5, 0, skullZ + 1,
                ShapeSketch.solid("BONE_BLOCK"));
        s.put(skullX + 5, 1, skullZ - 1, "BONE_BLOCK");                     // fangs
        s.put(skullX + 5, 1, skullZ + 1, "BONE_BLOCK");
        s.put(skullX + 4, 1, skullZ, "BONE_BLOCK");
        s.carveBox(skullX, 4, skullZ - 4, skullX + 1, 4, skullZ - 4);       // eye sockets
        s.carveBox(skullX, 4, skullZ + 4, skullX + 1, 4, skullZ + 4);
        s.put(skullX, 3, skullZ - 2, p.glow());                             // eyes glow at night
        s.put(skullX, 3, skullZ + 2, p.glow());
        s.carveBox(skullX - 2, 1, skullZ, skullX - 1, 2, skullZ);           // chest nook
        List<Rel> chests = new ArrayList<>();
        chests.add(new Rel(skullX - 2, 1, skullZ));

        // Bone-cutters worked this carcass once: a vertebra totem stands
        // near the skull, candles burned down at its feet.
        int totX = skullX - 4, totZ = skullZ + 4;
        int totBase = Math.max(0, s.topY(totX, totZ, 0));
        for (int y = 1; y <= 3; y++) {
            s.put(totX, totBase + y, totZ, "BONE_BLOCK");
        }
        s.put(totX, totBase + 4, totZ, "SKELETON_SKULL");
        s.put(totX + 1, totBase, totZ, p.groundMix(rng));
        s.put(totX + 1, totBase + 1, totZ, "BLACK_CANDLE");
        s.put(totX, totBase, totZ - 1, p.groundMix(rng));
        s.put(totX, totBase + 1, totZ - 1, "BLACK_CANDLE");

        // Tier 3+: whatever the beast swallowed is still in its gut — a
        // bone-walled cache under the mid-ribs, dug into from the south.
        java.util.function.Function<Random, String> gut =
                r -> r.nextInt(100) < 30 ? "BONE_BLOCK" : p.groundMix(r);
        int midX = (firstRib + lastRib) / 2;
        int zcMid = (int) Math.round(zc(midX, rx, bend));
        if (tier >= 3) {
            s.fillBox(midX - 2, -2, zcMid - 2, midX + 2, 1, zcMid + 2, gut);
            s.carveBox(midX - 1, -1, zcMid - 1, midX + 1, 0, zcMid + 1);
            s.put(midX, -1, zcMid + 3, gut.apply(rng));
            s.carveBox(midX, 0, zcMid + 3, midX, 2, zcMid + 3);
            s.carveBox(midX, -1, zcMid + 2, midX, 1, zcMid + 2);
            chests.add(new Rel(midX, -1, zcMid - 1));
            s.put(midX + 1, -1, zcMid + 1, p.glow());
        }

        // Tier 4: a third cache sinking with the tail, entered the same way.
        if (tier >= 4) {
            int tailX = spineStart + 3;
            int zcT = (int) Math.round(zc(tailX, rx, bend));
            s.fillBox(tailX - 2, -3, zcT - 2, tailX + 2, 0, zcT + 2, gut);
            // Three cells of ceiling, not two: a room two blocks high is a
            // room you cannot jump inside, so the last step up out of this
            // cache was unmakeable and the chest was a one-way drop.
            s.carveBox(tailX - 1, -2, zcT - 1, tailX + 1, 0, zcT + 1);
            // And the way in and out laid by ClimbPath, which cannot produce
            // a riser too tall to climb.
            ClimbPath gullet = new ClimbPath();
            gullet.connect(gut, tailX, -2, zcT - 1, tailX, 1, zcT + 3);
            gullet.cut(s, rng);
            chests.add(new Rel(tailX, -2, zcT - 1));
            s.put(tailX + 1, -2, zcT + 1, p.glow());
        }

        // Scattered bone fragments half-buried in the bar.
        for (int i = 0; i < 6; i++) {
            int fx = rng.nextInt(2 * rx) - rx;
            int fz = (int) Math.round(zc(fx, rx, bend)) + rng.nextInt(2 * rz + 1) - rz;
            int top = s.topY(fx, fz, -2);
            if (top >= 0 && !"BONE_BLOCK".equals(s.get(fx, top, fz))) {
                s.put(fx, top, fz, "BONE_BLOCK");
            }
        }

        // Mobs: one beneath the mid-ribs, one by the tail, one past the jaw.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(midX, zcMid, p::groundMix));
        mobs.add(s.stand(spineStart + 2, (int) Math.round(zc(spineStart + 2, rx, bend)) + 2,
                p::groundMix));
        mobs.add(s.stand(skullX + 7, skullZ, p::groundMix));

        s.shore(radiusBudget(), p::groundPatch);

        return ShapeBuild.of(s, chests, mobs);
    }

    /** Carcass centerline: a banana curve, ends bending toward +z. */
    private static double zc(double x, int rx, int bend) {
        double t = x / rx;
        return bend * (t * t - 0.5);
    }
}
