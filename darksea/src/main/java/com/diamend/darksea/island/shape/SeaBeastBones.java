package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Whatever this was, something bigger killed it. A bleached ribcage arcs
 * over a long sandbar, vertebrae trail off into the water, and the skull
 * lies at the head end — lantern-light glowing through its eye sockets at
 * night. The chest sits inside the skull, entered through the jaw.
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

        int rx = 10 + tier + rng.nextInt(2);   // half-length of the bar
        int rz = 4 + rng.nextInt(2);

        // The bar: a long domed spit, wet skirt at the waterline.
        s.blob(0, -3, 0, rx * 0.8, 2.5, rz + 2, p::groundMix, 0.3);
        for (int x = -rx; x <= rx; x++) {
            double w = rz * Math.sqrt(Math.max(0.0, 1.0 - (double) (x * x) / (rx * rx)));
            for (int z = (int) -w - 2; z <= w + 2; z++) {
                double u = Math.abs(z) / Math.max(w, 0.001);
                if (u <= 1.3) {
                    s.put(x, -1, z, p.groundMix(rng));
                }
                if (u <= 0.9) {
                    s.put(x, 0, z, p.groundMix(rng));
                }
                if (u <= 0.45) {
                    s.put(x, 1, z, p.groundMix(rng));
                }
            }
        }

        // Ribs: bone arches marching toward the head (+x), taller as they go.
        int ribs = tier >= 3 ? 5 : 4;
        int firstRib = (int) (-rx * 0.6);
        int lastRib = (int) (rx * 0.32);
        for (int i = 0; i < ribs; i++) {
            int x = firstRib + (lastRib - firstRib) * i / (ribs - 1);
            double w = Math.max(3.0,
                    rz * Math.sqrt(Math.max(0.0, 1.0 - (double) (x * x) / (rx * rx))) + 1);
            int h = 5 + Math.round(2.0f * i / (ribs - 1)) + rng.nextInt(2);
            int prevY = 0;
            for (double t = 0; t <= Math.PI + 0.01; t += Math.PI / 28) {
                int z = (int) Math.round(Math.cos(t) * w);
                int y = (int) Math.round(Math.sin(t) * h);
                s.put(x, y, z, "BONE_BLOCK");
                for (int fy = Math.min(prevY, y) + 1; fy < Math.max(prevY, y); fy++) {
                    s.put(x, fy, z, "BONE_BLOCK");  // keep steep sides connected
                }
                prevY = y;
            }
        }

        // Spine along the ridge, wavering, dipping toward the tail.
        int spineStart = (int) (-rx * 0.75);
        for (int x = spineStart; x <= lastRib + 2; x++) {
            double progress = (x - spineStart) / (double) (lastRib + 2 - spineStart);
            int y = (int) Math.round(2 + progress * 5.2);
            int z = (int) Math.round(Math.sin(x * 0.45) * 1.2);
            s.put(x, y, z, "BONE_BLOCK");
        }

        // Tail vertebrae sinking into the sea.
        for (int i = 1; i <= 6; i++) {
            int x = spineStart - i;
            int y = Math.max(-2, 2 - (i + 1) / 2);
            if (i % 2 == 1) {
                s.put(x, y, (int) Math.round(Math.sin(x * 0.45) * 1.2), "BONE_BLOCK");
            }
        }

        // The skull: hollow bone mass past the last rib, jaw gaping seaward.
        int skullX = lastRib + 5;
        s.blob(skullX, 2.0, 0, 2.9, 2.4, 2.7, ShapeSketch.solid("BONE_BLOCK"), 0.12);
        s.carveBlob(skullX, 1.8, 0, 1.8, 1.6, 1.6);
        s.fillBox(skullX - 1, 0, -1, skullX + 1, 0, 1, ShapeSketch.solid("BONE_BLOCK"));
        s.carveBox(skullX + 1, 1, -1, skullX + 3, 2, 1);              // mouth
        s.fillBox(skullX + 1, 0, -1, skullX + 3, 0, 1, ShapeSketch.solid("BONE_BLOCK"));
        s.put(skullX + 3, 1, -1, "BONE_BLOCK");                       // fangs
        s.put(skullX + 3, 1, 1, "BONE_BLOCK");
        s.carveBox(skullX + 1, 3, -2, skullX + 2, 3, -2);             // eye sockets
        s.carveBox(skullX + 1, 3, 2, skullX + 2, 3, 2);
        s.put(skullX, 3, -1, p.glow());                               // eyes glow at night
        s.put(skullX, 3, 1, p.glow());
        s.carveBox(skullX - 1, 1, 0, skullX, 2, 0);                   // room for the chest
        Rel chest = new Rel(skullX - 1, 1, 0);

        // Scattered bone fragments half-buried in the bar.
        for (int i = 0; i < 4; i++) {
            int fx = rng.nextInt(rx) - rx / 2;
            int fz = rng.nextInt(2 * rz + 1) - rz;
            int top = s.topY(fx, fz, -2);
            if (top >= 0 && !"BONE_BLOCK".equals(s.get(fx, top, fz))) {
                s.put(fx, top, fz, "BONE_BLOCK");
            }
        }

        // Mobs: one beneath the ribs, one out by the tail.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand((firstRib + lastRib) / 2, 0, p::groundMix));
        mobs.add(s.stand(spineStart + 2, 2, p::groundMix));

        return ShapeBuild.of(s, chest, mobs);
    }
}
