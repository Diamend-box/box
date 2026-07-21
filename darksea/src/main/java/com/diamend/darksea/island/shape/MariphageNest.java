package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Order's breeding ground: a low prismarine reef gone to sculk, risen
 * from the deep where the curse pools thickest. Its heart is an open socket
 * of sculk and sea-light that a Mariphage Core always rises from — kill it
 * and the reef simply grows another. Three egg-chambers are buried under the
 * reef floor, each reached by a stepped shaft, holding what the plague hoards.
 *
 * A rare stray in the Abyssal Reaches (tier 4) but the only landmark the
 * Sunless Trench builds at all (tier 5), so past the Trench's edge every
 * island you find is a nest. The Core is the shape's resident boss: the
 * spawner keeps exactly one standing whenever a player is near.
 */
final class MariphageNest implements DemoShape {

    @Override
    public String id() {
        return "mariphage-nest";
    }

    @Override
    public int minTier() {
        return 4;
    }

    @Override
    public int maxTier() {
        return 5;
    }

    @Override
    public int rarityWeight(int tier) {
        // Rare among the Reaches' seven shapes; the Trench builds nothing else.
        return tier >= 5 ? 10 : 2;
    }

    @Override
    public String bossMob() {
        return "MariphageCore";
    }

    @Override
    public String bossFallback() {
        return "WARDEN";
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        Palette p = Palette.forTier(tier);
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        // The reef grows wider the deeper it sits: ~13-14 in the Reaches,
        // ~16-17 in the Trench. platR is the first draw, so a given position
        // seed always steps up by exactly 3 from tier 4 to tier 5.
        int platR = 13 + 3 * (tier - 4) + rng.nextInt(2);

        // --- The reef mass: a drowned shelf, a waterline base, a set-back
        //     deck. The outer rings sit at or below the waves so the reef
        //     wades into the sea instead of walling it off. ---
        s.blob(0, -2.5, 0, platR - 1, 2.8, platR - 1, MariphageNest::stone, 0.2);
        s.disc(0, -1, 0, platR + 2, p::groundPatch, 0.12);   // submerged shelf
        s.disc(0, 0, 0, platR, MariphageNest::stone, 0.08);  // waterline base
        s.disc(0, 1, 0, platR - 1, MariphageNest::reef, 0.05);  // the reef floor
        s.wallRing(0, 1, 0, platR - 1, ShapeSketch.solid("DARK_PRISMARINE"));

        // --- The heart: an open socket of sculk and sea-light the Core rises
        //     from, ringed by shriekers that never stop listening. Left clear
        //     to the sky so the warden always has headroom. ---
        s.put(0, 0, 0, "SEA_LANTERN");            // a glow trapped under the floor
        s.put(0, 1, 0, "SCULK_CATALYST");         // the socket itself
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < ring.length; i++) {
            int hx = ring[i][0], hz = ring[i][1];
            s.put(hx, 1, hz, "SCULK");
            if (i % 2 == 0) {
                s.put(hx, 2, hz, i % 4 == 0 ? "SCULK_SHRIEKER" : "SCULK_SENSOR");
            }
        }
        for (int i = 0; i < 4; i++) {             // veins creeping out from it
            int vx = rng.nextInt(7) - 3, vz = rng.nextInt(7) - 3;
            if (Math.abs(vx) > 1 || Math.abs(vz) > 1) {
                s.put(vx, 1, vz, "SCULK");
            }
        }

        // --- Bleached-coral-and-sculk spires around the rim, clear of the
        //     heart's sightline and the buried chambers below. The coral is
        //     DEAD on purpose: these spires stand in open air above the
        //     waterline, and live coral blocks decay to their dead variant
        //     within a tick when they aren't touching water — so a live reef
        //     would rot gray on the server anyway. Dead coral both survives
        //     and fits the nest: a reef the plague already killed. ---
        String[] coral = {"DEAD_TUBE_CORAL_BLOCK", "DEAD_BRAIN_CORAL_BLOCK", "DEAD_HORN_CORAL_BLOCK",
                "DEAD_BUBBLE_CORAL_BLOCK", "DEAD_FIRE_CORAL_BLOCK"};
        int spires = 7 + rng.nextInt(3);
        for (int i = 0; i < spires; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(a) * (platR - 2));
            int sz = (int) Math.round(Math.sin(a) * (platR - 2));
            int h = 2 + rng.nextInt(3);
            String c = rng.nextInt(100) < 55 ? "SCULK" : coral[rng.nextInt(coral.length)];
            s.column(sx, 2, 1 + h, sz, ShapeSketch.solid(c));
            s.put(sx, 2 + h, sz, "SCULK_VEIN");
        }

        // Turtle-egg clutches strewn across the floor — the plague's spawn.
        for (int i = 0; i < 5; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int ex = (int) Math.round(Math.cos(a) * (platR - 5));
            int ez = (int) Math.round(Math.sin(a) * (platR - 5));
            if (Math.hypot(ex, ez) > 4) {   // never on the heart
                s.put(ex, 2, ez, "TURTLE_EGG");
            }
        }

        // --- Three egg-chambers, buried under the reef and roofed by it,
        //     each entered by a stepped shaft (mirrors the monolith's crypts).
        //     Well clear of one another and of the heart. ---
        List<Rel> chests = new ArrayList<>();
        chests.add(buryChamber(s, 0, 5, p.glow()));
        chests.add(buryChamber(s, -8, -2, p.glow()));
        chests.add(buryChamber(s, 8, 3, p.glow()));

        // --- Mobs: the Core rises at the heart (spawn 0 — the boss the
        //     spawner keeps standing), Vessels pace the reef beside it. ---
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(0, 0, ShapeSketch.solid("DARK_PRISMARINE")));
        mobs.add(s.stand(-5, -4, ShapeSketch.solid("DARK_PRISMARINE")));
        mobs.add(s.stand(5, 5, ShapeSketch.solid("DARK_PRISMARINE")));

        s.shore(radiusBudget(), p::groundPatch);

        return ShapeBuild.of(s, chests, mobs);
    }

    /**
     * A buried egg-chamber: solid casing, a hollow room, a stepped shaft up
     * to the reef deck on the +z face. Returns the chest cell at the room's
     * far wall — roofed by the reef above and walled on every side but the
     * shaft, exactly like the Abyssal Monolith's reliquary.
     */
    private static Rel buryChamber(ShapeSketch s, int cx, int cz, String glow) {
        s.fillBox(cx - 2, -3, cz - 2, cx + 2, 0, cz + 2, ShapeSketch.solid("DEEPSLATE"));
        s.carveBox(cx - 1, -2, cz - 1, cx + 1, -1, cz + 1);
        s.carveBox(cx, 1, cz + 3, cx, 3, cz + 3);
        s.put(cx, 0, cz + 3, "DEEPSLATE");
        s.carveBox(cx, 0, cz + 2, cx, 2, cz + 2);
        s.put(cx, -1, cz + 2, "DEEPSLATE");
        s.carveBox(cx, -1, cz + 1, cx, 1, cz + 1);
        s.put(cx, -2, cz + 1, "DEEPSLATE");
        s.put(cx - 1, -2, cz - 1, glow);
        s.put(cx + 1, -2, cz, "SCULK_CATALYST");
        return new Rel(cx, -2, cz - 1);
    }

    /** The reef's rock mass — prismarine gone dark, shot through with sculk. */
    private static String stone(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), y, Math.floorDiv(z, 3));
        if (cell < 25) {
            return rng.nextInt(100) < 55 ? "SCULK" : "DEEPSLATE";
        }
        return rng.nextInt(100) < 70 ? "PRISMARINE" : "DARK_PRISMARINE";
    }

    /** The finished reef floor: prismarine tiles the sculk keeps reclaiming. */
    private static String reef(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), y, Math.floorDiv(z, 3));
        if (cell < 30) {
            return rng.nextInt(100) < 60 ? "SCULK" : "DARK_PRISMARINE";
        }
        if (cell > 80) {
            return "PRISMARINE_BRICKS";
        }
        return rng.nextInt(100) < 80 ? "PRISMARINE" : "DARK_PRISMARINE";
    }
}
