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

        // The high altar east of the way: a two-step dais ringed by unlit
        // candles and skulls, corner pillars with soul lanterns, a cauldron
        // gone dry, a bone heap — the place they actually practiced. One
        // deck slab south of the dais is missing: the way down to the
        // reliquary, chest three.
        int cx = 7, cz = 0;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            int kx = cx + (int) Math.round(Math.cos(a) * 4.5);
            int kz = cz + (int) Math.round(Math.sin(a) * 4.5);
            s.put(kx, 2, kz, i % 4 == 3 ? "SKELETON_SKULL" : "BLACK_CANDLE");
        }
        for (int x = cx - 2; x <= cx + 2; x++) {      // dais, first step
            for (int z = cz - 2; z <= cz + 2; z++) {
                boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
                s.put(x, 2, z, corner ? "CHISELED_POLISHED_BLACKSTONE" : "POLISHED_BLACKSTONE");
            }
        }
        for (int x = cx - 1; x <= cx + 1; x++) {      // dais, second step
            for (int z = cz - 1; z <= cz + 1; z++) {
                s.put(x, 3, z, "POLISHED_BLACKSTONE_BRICKS");
            }
        }
        s.put(cx, 4, cz, "CHISELED_POLISHED_BLACKSTONE");  // the altar table
        s.put(cx, 5, cz, "GILDED_BLACKSTONE");
        s.put(cx, 6, cz, "WITHER_SKELETON_SKULL");
        for (int c = 0; c < 4; c++) {                 // corner pillars
            int px2 = cx + (c % 2 == 0 ? -2 : 2), pz2 = cz + (c < 2 ? -2 : 2);
            s.put(px2, 3, pz2, "POLISHED_BASALT");
            s.put(px2, 4, pz2, "POLISHED_BASALT");
            if (c % 2 == 0) {
                s.put(px2, 5, pz2, "SOUL_LANTERN");
            }
        }
        s.put(cx + 1, 4, cz - 1, "CAULDRON");
        s.put(cx - 3, 2, cz + 3, "BONE_BLOCK");
        s.put(cx - 3, 2, cz + 4, "BONE_BLOCK");

        // The reliquary beneath the altar: casing, chamber, and a stepped
        // way in through the missing slab south of the dais.
        s.fillBox(cx - 2, -3, cz - 2, cx + 2, 0, cz + 2, ShapeSketch.solid("BLACKSTONE"));
        s.carveBox(cx - 1, -2, cz - 1, cx + 1, -1, cz + 1);
        s.carveBox(cx, 1, cz + 3, cx, 3, cz + 3);
        s.put(cx, 0, cz + 3, "BLACKSTONE");
        s.carveBox(cx, 0, cz + 2, cx, 2, cz + 2);
        s.put(cx, -1, cz + 2, "BLACKSTONE");
        s.carveBox(cx, -1, cz + 1, cx, 1, cz + 1);
        s.put(cx, -2, cz + 1, "BLACKSTONE");
        Rel reliquary = new Rel(cx, -2, cz - 1);
        s.put(cx - 1, -2, cz + 1, "SOUL_LANTERN");
        s.put(cx + 1, -2, cz, "BONE_BLOCK");

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

        // One grave is more than a grave: a crypt under the first headstone,
        // reached by rough steps cut down from the deck at its foot.
        int gx0 = (int) Math.round(Math.cos(-Math.PI * 0.42) * (platR - 3));
        int gz0 = (int) Math.round(Math.sin(-Math.PI * 0.42) * (platR - 3));
        s.fillBox(gx0 - 2, -3, gz0 - 1, gx0 + 2, 0, gz0 + 3, ShapeSketch.solid("BLACKSTONE"));
        s.put(gx0, 1, gz0, "SOUL_SAND");        // re-lay the disturbed grave bed
        s.put(gx0, 1, gz0 + 1, "SOUL_SAND");
        s.carveBox(gx0 - 1, -2, gz0, gx0 + 1, -1, gz0 + 2);
        s.carveBox(gx0, 1, gz0 + 4, gx0, 3, gz0 + 4);
        s.put(gx0, 0, gz0 + 4, "BLACKSTONE");
        s.carveBox(gx0, 0, gz0 + 3, gx0, 2, gz0 + 3);
        s.put(gx0, -1, gz0 + 3, "BLACKSTONE");
        s.carveBox(gx0, -1, gz0 + 2, gx0, 1, gz0 + 2);
        s.put(gx0, -2, gz0 + 2, "BLACKSTONE");
        Rel crypt = new Rel(gx0, -2, gz0);
        s.put(gx0 + 1, -2, gz0 + 1, "SOUL_LANTERN");
        s.put(gx0 - 1, -2, gz0 + 1, "BONE_BLOCK");

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
        List<Rel> chests = new ArrayList<>();
        chests.add(new Rel(0, -2, 4));
        s.put(-1, -2, 4, "SOUL_LANTERN");
        s.put(-1, -2, 3, "SCULK_SENSOR");
        s.put(1, -2, 3, "SCULK_CATALYST");
        chests.add(crypt);
        chests.add(reliquary);

        // Mobs pace the deck: one by the graves, one on the west deck near
        // the dwellings, one keeping the old vigil at the altar.
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(platR - 5, -3, ShapeSketch.solid("POLISHED_BLACKSTONE")));
        mobs.add(s.stand(-(platR - 5), 0, ShapeSketch.solid("POLISHED_BLACKSTONE")));
        mobs.add(s.stand(10, 4, ShapeSketch.solid("POLISHED_BLACKSTONE")));

        return ShapeBuild.of(s, chests, mobs);
    }

    /** Patch-shaded deck: worn brickwork drifts across the polished floor. */
    private static String deck(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), y, Math.floorDiv(z, 3));
        if (cell < 22) {
            int roll = rng.nextInt(100);
            if (roll < 55) {
                return "POLISHED_BLACKSTONE_BRICKS";
            }
            return roll < 85 ? "CRACKED_POLISHED_BLACKSTONE_BRICKS" : "BLACKSTONE";
        }
        return rng.nextInt(100) < 85 ? "POLISHED_BLACKSTONE" : "BLACKSTONE";
    }
}
