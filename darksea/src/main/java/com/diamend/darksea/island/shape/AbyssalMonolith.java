package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The deep sea's landmark, and somebody's dead church. A wide blackstone
 * deck barely proud of the waves carries a crying-obsidian menhir burning
 * soul fire — and everything a drowned congregation left behind: a candle
 * ring around a skull altar, glyph steles (half of them toppled), a
 * processional way inlaid in the deck, two roofless dwellings with their
 * last possessions, and a row of graves. The chest is out of sight in a
 * sculk-grown vault beneath the deck; a stepped trench on the processional
 * way leads down. Zone 4 only.
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

        int platR = 15 + rng.nextInt(2);

        // Pedestal underwater, structural slab, then the finished deck.
        s.blob(0, -2.5, 0, platR - 0.5, 2.8, platR - 0.5,
                r -> r.nextInt(100) < 30 ? "OBSIDIAN" : "BLACKSTONE", 0.2);
        s.disc(0, 0, 0, platR, ShapeSketch.solid("BLACKSTONE"), 0.08);
        s.disc(0, 1, 0, platR, AbyssalMonolith::deck, 0.05);
        s.wallRing(0, 1, 0, platR, ShapeSketch.solid("CHISELED_POLISHED_BLACKSTONE"));

        // The processional way: an inlaid basalt path from the south edge,
        // over the vault, to the monolith's feet.
        for (int z = -4; z <= platR - 3; z++) {
            s.put(0, 1, z, "POLISHED_BASALT");
            String edge = z % 4 == 0 ? "CHISELED_POLISHED_BLACKSTONE"
                    : "POLISHED_BLACKSTONE_BRICKS";
            s.put(-1, 1, z, edge);
            s.put(1, 1, z, edge);
        }

        // The monolith: a 3x3 base tapering to a 2x2 crown of soul fire.
        int monoY = 17 + rng.nextInt(3);
        for (int y = 2; y <= monoY; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -7; z <= -5; z++) {
                    boolean taper = y > monoY - 4 && (x == 1 || z == -5);
                    if (!taper) {
                        s.put(x, y, z, rng.nextInt(100) < 15 ? "CRYING_OBSIDIAN" : "OBSIDIAN");
                    }
                }
            }
        }
        s.put(0, monoY + 1, -6, "SOUL_SOIL");
        s.put(0, monoY + 2, -6, "SOUL_FIRE");
        s.put(2, 2, -6, "COBWEB");
        s.put(-2, 2, -5, "COBWEB");

        // Ritual circle east of the way: unlit black candles and skulls
        // around a gilded altar. Nobody has lit these in a long time.
        int cx = 7, cz = 0;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            int kx = cx + (int) Math.round(Math.cos(a) * 3.5);
            int kz = cz + (int) Math.round(Math.sin(a) * 3.5);
            s.put(kx, 2, kz, i % 4 == 3 ? "SKELETON_SKULL" : "BLACK_CANDLE");
        }
        s.put(cx, 2, cz, "CHISELED_POLISHED_BLACKSTONE");
        s.put(cx, 3, cz, "GILDED_BLACKSTONE");
        s.put(cx, 4, cz, "SKELETON_SKULL");

        // Glyph steles around the deck; the years have pushed half of them
        // over, so their capstones lie where they fell.
        double stelePhase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 5; i++) {
            double a = stelePhase + i * Math.PI * 2 / 5;
            int sx = (int) Math.round(Math.cos(a) * (platR - 4));
            int sz = (int) Math.round(Math.sin(a) * (platR - 4));
            if (Math.abs(sx) < 3 && sz > -3) {
                continue;  // never on the processional way
            }
            if (Math.hypot(sx - cx, sz - cz) < 6) {
                continue;  // nor through the ritual circle
            }
            if (rng.nextInt(100) < 40) {
                s.put(sx, 2, sz, "POLISHED_BASALT");  // stump
                int dx = (int) Math.round(Math.cos(a + 1.4));
                int dz = (int) Math.round(Math.sin(a + 1.4));
                s.line(sx + dx, 2, sz + dz, sx + dx * 3, 2, sz + dz * 3,
                        ShapeSketch.solid("POLISHED_BASALT"));
                s.put(sx + dx * 4, 2, sz + dz * 4, "CHISELED_POLISHED_BLACKSTONE");
            } else {
                s.column(sx, 2, 4, sz, ShapeSketch.solid("POLISHED_BASALT"));
                s.put(sx, 5, sz, "CHISELED_POLISHED_BLACKSTONE");
            }
        }

        // Two roofless dwellings on the west deck: low walls rotted down
        // column by column, and what the owners couldn't take with them.
        for (int b = 0; b < 2; b++) {
            int hx = -8, hz = b == 0 ? -5 : 4;
            for (int x = hx - 2; x <= hx + 2; x++) {
                for (int z = hz - 2; z <= hz + 2; z++) {
                    boolean edge = x == hx - 2 || x == hx + 2 || z == hz - 2 || z == hz + 2;
                    if (rng.nextInt(100) < 45) {
                        s.put(x, 1, z, "CRACKED_POLISHED_BLACKSTONE_BRICKS");
                    }
                    if (edge) {
                        long h = Math.floorMod(x * 341873128712L + z * 132897987541L, 100);
                        if (h < 58 && !(z == hz + 2 && Math.abs(x - hx) < 1)) {  // door gap
                            s.put(x, 2, z, "POLISHED_BLACKSTONE_BRICKS");
                            if (h < 30) {
                                s.put(x, 3, z, "CRACKED_POLISHED_BLACKSTONE_BRICKS");
                            }
                        }
                    }
                }
            }
            if (b == 0) {
                s.put(hx - 1, 2, hz - 1, "BARREL");
                s.put(hx + 1, 2, hz + 1, "COBWEB");
                s.put(hx, 2, hz, "CANDLE");
            } else {
                s.put(hx - 1, 2, hz, "LECTERN");
                s.put(hx + 1, 2, hz - 1, "COBWEB");
                s.put(hx + 1, 2, hz + 1, "SOUL_LANTERN");
            }
        }

        // Graves along the north-east rim: soul-sand beds, headstone stubs,
        // one skull someone set down and never came back for.
        for (int g = 0; g < 4; g++) {
            double a = -Math.PI * 0.42 + g * 0.24;
            int gx = (int) Math.round(Math.cos(a) * (platR - 3));
            int gz = (int) Math.round(Math.sin(a) * (platR - 3));
            s.put(gx, 1, gz, "SOUL_SAND");
            s.put(gx, 1, gz + 1, "SOUL_SAND");
            s.put(gx, 2, gz - 1, "BLACKSTONE_WALL");
            if (g == 2) {
                s.put(gx, 2, gz + 1, "SKELETON_SKULL");
            }
        }

        // Obsidian shards along the rim, clear of everything man-made.
        int shards = 5 + rng.nextInt(3);
        for (int i = 0; i < shards; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(a) * (platR - 1));
            int sz = (int) Math.round(Math.sin(a) * (platR - 1));
            if (sz < 0 && Math.abs(sx) < 4) {
                continue;  // keep the monolith's sightline clear
            }
            s.column(sx, 2, 2 + rng.nextInt(3), sz, ShapeSketch.solid("OBSIDIAN"));
        }
        for (int i = 0; i < 7; i++) {  // sculk creep betrays the vault below
            int vx = rng.nextInt(5) - 2;
            int vz = 1 + rng.nextInt(Math.max(1, platR - 4));
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

        // Mobs pace the deck: one by the graves, one on the west deck near
        // the dwellings, one keeping the old vigil at the circle.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(platR - 5, -3, AbyssalMonolith::deck));
        mobs.add(s.stand(-(platR - 5), 0, AbyssalMonolith::deck));
        mobs.add(s.stand(10, 4, AbyssalMonolith::deck));

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
