package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The deep sea's landmark: a polished blackstone platform barely proud of
 * the waves, ringed by obsidian shards, with a crying-obsidian menhir
 * burning soul fire at its crown. The chest is out of sight in a sculk-grown
 * vault beneath the deck — a stepped trench behind the monolith leads down.
 * Zone 4 only.
 */
final class AbyssalMonolith implements DemoShape {

    @Override
    public String id() {
        return "abyssal-monolith";
    }

    @Override
    public int minTier() {
        return 4;
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

        int platR = 8 + rng.nextInt(2);

        // Pedestal underwater, structural slab, then the finished deck.
        s.blob(0, -2.5, 0, platR - 0.5, 2.8, platR - 0.5,
                r -> r.nextInt(100) < 30 ? "OBSIDIAN" : "BLACKSTONE", 0.25);
        s.disc(0, 0, 0, platR, ShapeSketch.solid("BLACKSTONE"), 0.1);
        s.disc(0, 1, 0, platR, AbyssalMonolith::deck, 0.06);
        s.wallRing(0, 1, 0, platR, ShapeSketch.solid("CHISELED_POLISHED_BLACKSTONE"));

        // The monolith, tapering, crowned with soul fire.
        int monoY = 14 + rng.nextInt(3);
        for (int y = 2; y <= monoY; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -4; z <= -3; z++) {
                    boolean taper = y > monoY - 3 && x == 1;  // crown thins to 2x2, not a post
                    if (!taper) {
                        s.put(x, y, z, rng.nextInt(100) < 15 ? "CRYING_OBSIDIAN" : "OBSIDIAN");
                    }
                }
            }
        }
        s.put(0, monoY + 1, -4, "SOUL_SOIL");
        s.put(0, monoY + 2, -4, "SOUL_FIRE");

        // Shard ring: broken obsidian teeth standing along the deck edge.
        int shards = 6 + rng.nextInt(3);
        for (int i = 0; i < shards; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(a) * (platR - 1));
            int sz = (int) Math.round(Math.sin(a) * (platR - 1));
            if (sz < 0 && Math.abs(sx) < 3) {
                continue;  // keep the monolith's sightline clear
            }
            s.column(sx, 2, 2 + rng.nextInt(3), sz, ShapeSketch.solid("OBSIDIAN"));
        }
        s.put(2, 2, -3, p.glow());
        s.put(-2, 2, -3, p.glow());
        for (int i = 0; i < 5; i++) {  // sculk creep betrays the vault below
            int vx = rng.nextInt(5) - 2;
            int vz = 1 + rng.nextInt(Math.max(1, platR - 3));
            s.put(vx, 1, vz, "SCULK");
        }

        // The vault: solid casing under the south deck, hollowed to a
        // 3-wide chamber with a sculk floor.
        s.fillBox(-2, -3, 1, 2, 0, 5, ShapeSketch.solid("BLACKSTONE"));
        s.carveBox(-1, -2, 2, 1, -1, 4);
        for (int x = -1; x <= 1; x++) {
            for (int z = 2; z <= 4; z++) {
                s.put(x, -3, z, rng.nextInt(100) < 70 ? "SCULK" : "BLACKSTONE");
            }
        }

        // Stepped trench down: deck (stand y2) -> y1 -> y0 -> y-1 -> vault floor.
        s.put(0, 0, 1, "BLACKSTONE");
        s.carveBox(0, 1, 1, 0, 3, 1);
        s.put(0, -1, 2, "BLACKSTONE");
        s.carveBox(0, 0, 2, 0, 2, 2);
        s.put(0, -2, 3, "BLACKSTONE");
        s.carveBox(0, -1, 3, 0, 1, 3);

        // Chest at the vault's back wall, watched by sculk.
        Rel chest = new Rel(0, -2, 4);
        s.put(-1, -2, 4, "SOUL_LANTERN");
        s.put(-1, -2, 3, "SCULK_SENSOR");
        s.put(1, -2, 3, "SCULK_CATALYST");

        // Mobs pace the deck on both flanks.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(new Rel(platR - 3, 2, 2));
        mobs.add(new Rel(-(platR - 3), 2, 0));

        return ShapeBuild.of(s, chest, mobs);
    }

    private static String deck(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 62) {
            return "POLISHED_BLACKSTONE";
        }
        if (roll < 82) {
            return "BLACKSTONE";
        }
        return roll < 93 ? "POLISHED_BLACKSTONE_BRICKS" : "CRACKED_POLISHED_BLACKSTONE_BRICKS";
    }
}
