package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A forested island something is eating from the inside. Dark oaks stand in
 * every stage of dying — bare snags, half-shed canopies riddled with rot
 * holes, shelf fungus on the trunks, cobwebs and hanging roots in the
 * branches — around a stagnant pool and a giant hollow heart-tree whose
 * innards glow with shroomlight. The chest is inside the heart. By tier 4
 * the blight is winning: soul soil underfoot, sculk at the roots, and a
 * shrieker listening beside the loot.
 */
final class CorruptedForest implements DemoShape {

    @Override
    public String id() {
        return "corrupted-forest";
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

        int islandR = 14 + 2 * tier + rng.nextInt(2);  // t2 18-19 .. t4 22-23

        // Beach ring, forest floor terraces, underwater root.
        s.blob(0, -3, 0, islandR - 1, 3.2, islandR - 1, p::groundMix, 0.18);
        s.disc(0, -1, 0, islandR, p::groundMix, 0.15);
        s.disc(0, 0, 0, islandR - 2, r -> floor(r, tier), 0.15);
        s.disc(0, 1, 0, islandR - 6, r -> floor(r, tier), 0.12);
        double moundAngle = rng.nextDouble() * Math.PI * 2;
        s.dome((int) Math.round(Math.cos(moundAngle) * islandR * 0.45), 1,
                (int) Math.round(Math.sin(moundAngle) * islandR * 0.45), 5.5, 3,
                r -> floor(r, tier), 0.12);

        // A stagnant pool sunk into the upper terrace, mud-bottomed,
        // hard-rimmed so it can't drain, one lily pad going nowhere.
        double poolAngle = moundAngle + Math.PI * (0.6 + rng.nextDouble() * 0.8);
        int px = (int) Math.round(Math.cos(poolAngle) * 7);
        int pz = (int) Math.round(Math.sin(poolAngle) * 7);
        for (int x = px - 2; x <= px + 2; x++) {
            for (int z = pz - 2; z <= pz + 2; z++) {
                boolean rim = Math.abs(x - px) == 2 || Math.abs(z - pz) == 2;
                if (rim) {
                    s.put(x, 1, z, "MUD");
                } else {
                    s.put(x, 0, z, "MUD");
                    s.put(x, 1, z, "WATER");
                    s.carve(x, 2, z);
                }
            }
        }
        s.put(px, 2, pz, "LILY_PAD");

        // Floor decor before the trees so it never lands on a canopy:
        // mushrooms, moss carpet, and the odd half-buried bone.
        for (int i = 0; i < 8 + 2 * tier; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 5 + rng.nextDouble() * (islandR - 9);
            int dx = (int) Math.round(Math.cos(a) * r);
            int dz = (int) Math.round(Math.sin(a) * r);
            int top = s.topY(dx, dz, -9);
            if (top < 0 || top > 3 || !s.solidAt(dx, top, dz)) {
                continue;
            }
            String ground = s.get(dx, top, dz);
            if ("WATER".equals(ground) || "MUD".equals(ground)) {
                continue;
            }
            int roll = rng.nextInt(100);
            if (roll < 40) {
                s.put(dx, top + 1, dz, "BROWN_MUSHROOM");
            } else if (roll < 55) {
                s.put(dx, top + 1, dz, "RED_MUSHROOM");
            } else if (roll < 90 && tier <= 3) {
                s.put(dx, top + 1, dz, "MOSS_CARPET");
            } else if (roll >= 90) {
                s.put(dx, top, dz, "BONE_BLOCK");
            }
        }
        for (int i = 0; i < 6; i++) {  // dead scrub on the beach ring
            double a = rng.nextDouble() * Math.PI * 2;
            int bx = (int) Math.round(Math.cos(a) * (islandR - 4));
            int bz = (int) Math.round(Math.sin(a) * (islandR - 4));
            int top = s.topY(bx, bz, -1);
            if (top >= 0 && s.solidAt(bx, top, bz)) {
                s.put(bx, top, bz, "COARSE_DIRT");
                s.put(bx, top + 1, bz, "DEAD_BUSH");
            }
        }

        // The heart-tree: a fat hollow trunk ring with a root collar, a
        // rotten crown, and the chest inside its chamber.
        int heartH = 10 + tier;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 2 || Math.abs(x) + Math.abs(z) == 4) {
                    continue;  // ring of trunk columns, corners open
                }
                for (int y = 1; y <= 4; y++) {
                    s.put(x, y, z, trunk(rng));
                }
            }
        }
        s.fillBox(-1, 0, -1, 1, 0, 1, ShapeSketch.solid(tier >= 4 ? "SCULK" : "ROOTED_DIRT"));
        for (int y = 1; y <= 3; y++) {  // the way in, facing south
            s.carve(0, y, 2);
            s.carve(1, y, 2);
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y <= 4; y++) {
                    s.carve(x, y, z);
                }
            }
        }
        s.fillBox(-1, 5, -1, 1, 6, 1, ShapeSketch.solid("DARK_OAK_LOG"));  // root collar roof
        for (int y = 7; y <= heartH; y++) {  // merged upper trunk
            s.put(0, y, 0, "DARK_OAK_LOG");
            s.put(1, y, 0, trunk(rng));
            s.put(-1, y, 0, trunk(rng));
            s.put(0, y, 1, trunk(rng));
            s.put(0, y, -1, trunk(rng));
        }
        for (int i = 0; i < 4; i++) {  // root spokes buttressing the base
            double a = Math.PI / 4 + i * Math.PI / 2;
            int ex = (int) Math.round(Math.cos(a) * 5);
            int ez = (int) Math.round(Math.sin(a) * 5);
            s.line(ex, 1, ez, (int) Math.round(Math.cos(a) * 2.5), 2,
                    (int) Math.round(Math.sin(a) * 2.5), ShapeSketch.solid("DARK_OAK_LOG"));
        }
        // Rotten crown: leaf mass with holes, branch spokes, hanging roots.
        s.blob(0, heartH + 1, 0, 5.2, 2.4, 5.2, CorruptedForest::rottenLeaves, 0.15);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2 + 0.4;
            int ex = (int) Math.round(Math.cos(a) * 5);
            int ez = (int) Math.round(Math.sin(a) * 5);
            s.line(0, heartH, 0, ex, heartH + 1, ez, ShapeSketch.solid("DARK_OAK_LOG"));
        }
        s.carveBlob(2.5, heartH + 2.5, 1.5, 1.8, 1.4, 1.8);   // rot holes in the crown
        s.carveBlob(-2.0, heartH + 1.5, -2.5, 1.6, 1.2, 1.6);
        for (int i = 0; i < 5; i++) {
            int hx = rng.nextInt(7) - 3, hz = rng.nextInt(7) - 3;
            int top = s.topY(hx, hz, -9);
            if (top >= heartH - 1 && !s.solidAt(hx, top - 1, hz)) {
                s.put(hx, top - 1, hz, "HANGING_ROOTS");
            }
        }

        // Inside the heart: the chest, the glow, and — at tier 4 — the
        // things the blight put there to listen.
        Rel chest = new Rel(-1, 1, -1);
        s.put(1, 1, 1, "SHROOMLIGHT");
        s.put(0, 4, 0, "SHROOMLIGHT");  // heartglow above the chamber
        if (tier >= 4) {
            s.put(1, 1, -1, "SCULK_SHRIEKER");
            s.put(-1, 1, 1, "SCULK_CATALYST");
            s.disc(0, 1, 3, 2.2, r -> r.nextInt(100) < 55 ? "SCULK" : floor(r, tier), 0.2);
        }

        // The dying wood around it: trees on a golden-angle spiral, each in
        // its own stage of decay.
        int trees = 10 + 2 * tier;
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < trees; i++) {
            double r = 6.5 + (islandR - 10) * Math.sqrt((i + 1) / (double) trees);
            double a = phase + i * 2.39996;
            int tx = (int) Math.round(Math.cos(a) * r);
            int tz = (int) Math.round(Math.sin(a) * r);
            if (Math.hypot(tx - px, tz - pz) < 3.5) {
                continue;  // not in the pool
            }
            int ground = s.topY(tx, tz, -9);
            if (ground < 0 || ground > 4) {
                continue;
            }
            tree(s, rng, tier, tx, ground, tz);
        }

        // Mobs: three, in clearings — columns are re-picked a few degrees
        // around if a tree took the spot.
        List<Rel> mobs = new ArrayList<>();
        for (int m = 0; m < 3; m++) {
            double a = phase + 1.1 + m * Math.PI * 2 / 3;
            int mx = 0, mz = 0;
            for (int attempt = 0; attempt < 24; attempt++) {
                double aa = a + attempt * 0.26;
                mx = (int) Math.round(Math.cos(aa) * islandR * 0.55);
                mz = (int) Math.round(Math.sin(aa) * islandR * 0.55);
                int top = s.topY(mx, mz, -9);
                if (top >= 0 && top <= 3 && !"WATER".equals(s.get(mx, top, mz))) {
                    break;
                }
            }
            mobs.add(s.stand(mx, mz, r -> floor(r, tier)));
        }

        return ShapeBuild.of(s, chest, mobs);
    }

    /** One dying tree: bare snag, broken stump, or shedding canopy. */
    private void tree(ShapeSketch s, Random rng, int tier, int x, int ground, int z) {
        int kind = rng.nextInt(100);
        if (kind < 20) {  // broken stump with its fallen trunk beside it
            int h = 2 + rng.nextInt(2);
            for (int y = 1; y <= h; y++) {
                s.put(x, ground + y, z, trunk(rng));
            }
            int dx = rng.nextInt(3) - 1, dz = dx == 0 ? (rng.nextBoolean() ? 1 : -1) : 0;
            for (int i = 2; i <= 5; i++) {
                int fx = x + dx * i, fz = z + dz * i;
                int top = s.topY(fx, fz, -9);
                if (top >= 0) {
                    s.put(fx, top + 1, fz, "DARK_OAK_LOG");
                    if (i == 3 && rng.nextBoolean()) {
                        s.put(fx, top + 2, fz, "BROWN_MUSHROOM");
                    }
                }
            }
            return;
        }

        int h = 5 + rng.nextInt(4);
        for (int y = 1; y <= h; y++) {
            s.put(x, ground + y, z, trunk(rng));
        }
        if (tier >= 3 && rng.nextInt(100) < 45) {  // sculk pooling at the roots
            s.disc(x, ground, z, 1.6, r -> r.nextInt(100) < 60 ? "SCULK" : floor(r, tier), 0.3);
        }
        if (rng.nextInt(100) < 55) {  // shelf fungus partway up
            int fy = ground + 2 + rng.nextInt(Math.max(1, h - 3));
            int fx = rng.nextBoolean() ? 1 : -1;
            s.put(x + fx, fy, z, "MUSHROOM_STEM");
            if (rng.nextBoolean()) {
                s.put(x + fx, fy, z + (rng.nextBoolean() ? 1 : -1), "MUSHROOM_STEM");
            }
        }

        int topY = ground + h;
        if (kind < 55) {  // bare snag: stub branches, webs, no leaves left
            for (int b = 0; b < 2; b++) {
                double a = rng.nextDouble() * Math.PI * 2;
                int bx = (int) Math.round(Math.cos(a) * 2);
                int bz = (int) Math.round(Math.sin(a) * 2);
                s.line(x, topY - 1 - b, z, x + bx, topY - b, z + bz,
                        ShapeSketch.solid("DARK_OAK_LOG"));
            }
            if (rng.nextInt(100) < 35) {
                s.put(x + 1, topY - 1, z, "COBWEB");
            }
        } else {  // still shedding: ragged canopy tufts on branch arms
            for (int b = 0; b < 2; b++) {
                double a = rng.nextDouble() * Math.PI * 2;
                int bx = (int) Math.round(Math.cos(a) * 2);
                int bz = (int) Math.round(Math.sin(a) * 2);
                s.line(x, topY - 1, z, x + bx, topY, z + bz, ShapeSketch.solid("DARK_OAK_LOG"));
                s.blob(x + bx, topY + 1, z + bz, 2.0, 1.3, 2.0, CorruptedForest::rottenLeaves, 0.2);
            }
            s.blob(x, topY + 1.5, z, 2.7, 1.6, 2.7, CorruptedForest::rottenLeaves, 0.2);
            if (rng.nextInt(100) < 45) {
                int rx = x + rng.nextInt(3) - 1, rz = z + rng.nextInt(3) - 1;
                int t = s.topY(rx, rz, -9);
                if (t > ground + 2 && !s.solidAt(rx, t - 1, rz)) {
                    s.put(rx, t - 1, rz, "HANGING_ROOTS");
                }
            }
        }
    }

    private static String trunk(Random rng) {
        return rng.nextInt(100) < 25 ? "STRIPPED_DARK_OAK_LOG" : "DARK_OAK_LOG";
    }

    private static String rottenLeaves(Random rng) {
        return rng.nextInt(100) < 16 ? ShapeSketch.AIR : "DARK_OAK_LEAVES";
    }

    /** Forest floor mix — the blight escalates with the tier. */
    private static String floor(Random rng, int tier) {
        int roll = rng.nextInt(100);
        if (tier <= 2) {
            if (roll < 50) {
                return "PODZOL";
            }
            return roll < 75 ? "COARSE_DIRT" : roll < 90 ? "MOSS_BLOCK" : "GRASS_BLOCK";
        }
        if (tier == 3) {
            if (roll < 40) {
                return "PODZOL";
            }
            return roll < 65 ? "COARSE_DIRT" : roll < 82 ? "ROOTED_DIRT"
                    : roll < 94 ? "MOSS_BLOCK" : "MUD";
        }
        if (roll < 30) {
            return "COARSE_DIRT";
        }
        return roll < 55 ? "SOUL_SOIL" : roll < 75 ? "ROOTED_DIRT"
                : roll < 90 ? "MUD" : "SCULK";
    }
}
