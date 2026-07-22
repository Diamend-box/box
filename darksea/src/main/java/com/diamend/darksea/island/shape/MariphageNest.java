package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Order's breeding ground, dressed as the shrine it is. A low prismarine
 * reef gone to sculk, risen from the deep where the curse pools thickest — and
 * worked over by the Order of the Soul until it looms. A ring of skull-crowned
 * glyph steles rings the whole reef, tall enough to be read across the water;
 * a candle-lit sanctum of soul-flame and weeping stone fences the socket like
 * an altar; and the buried chambers below are offering niches, gilded and
 * skull-lit. Its heart is an open socket of sculk and sea-light that a
 * Mariphage Core always rises from — kill it and the reef simply grows
 * another. Four egg-chambers are buried under the reef floor, each reached by
 * a stepped shaft, holding what the plague hoards — two of them elected vaults,
 * the Order's deepest tithe.
 *
 * A rare stray in the Abyssal Reaches (tier 4) but the only landmark the
 * Sunless Trench builds at all (tier 5), so past the Trench's edge every
 * island you find is a nest. The Core is the shape's resident boss: the
 * spawner keeps exactly one standing whenever a player is near.
 */
final class MariphageNest implements DemoShape {

    // The four chamber shafts break the surface here (a chamber centred at
    // (cx,cz) climbs out on its +z face at (cx, cz+3)). Nothing the Order
    // strews on the deck may cap a mouth, or the vault below turns un-lootable
    // — island blocks can't be mined open.
    private static final int[][] SHAFT_MOUTHS = {{0, 8}, {-8, 1}, {8, 6}, {0, -5}};

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
    public int chestCount(int tier) {
        return 4;   // four buried offering-niches, richer than an ordinary reef
    }

    @Override
    public int vaultChestCount() {
        return 2;   // the Order tithes deep — two of the four are vaults
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
    public String chestBonusItem() {
        // DarkSeaItems.SOULWAKE_COMPASS — a literal so this package stays Bukkit-free.
        return "soulwake_compass";
    }

    @Override
    public double chestBonusChance(boolean vault) {
        // The Order's hunting charm rode deepest in the tithe: better odds in
        // the two vaults than the plain offering-niches.
        return vault ? 0.5 : 0.15;
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
        //     from, its rim weeping crying obsidian, ringed by shriekers that
        //     never stop listening. Left clear to the sky so the warden always
        //     has headroom. ---
        s.put(0, 0, 0, "SEA_LANTERN");            // a glow trapped under the floor
        s.put(0, 1, 0, "SCULK_CATALYST");         // the socket itself
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < ring.length; i++) {
            int hx = ring[i][0], hz = ring[i][1];
            // Crying obsidian weeps purple at the cardinals — the Order's
            // colour — sculk creeping between.
            s.put(hx, 1, hz, i % 2 == 0 ? "CRYING_OBSIDIAN" : "SCULK");
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

        // --- The sanctum: a ring of black candles the Order keeps burning
        //     around the socket, and four skull-crowned glyph steles set at
        //     the corners to fence the Core's pit like an altar. The centre is
        //     never touched — the Core needs its column of sky to rise. ---
        int candles = 12;
        for (int i = 0; i < candles; i++) {
            double a = i * (Math.PI * 2 / candles);
            int cx = (int) Math.round(Math.cos(a) * 4);
            int cz = (int) Math.round(Math.sin(a) * 4);
            if (Math.abs(cx) <= 2 && Math.abs(cz) <= 2) {
                continue;   // keep clear of the shrieker ring
            }
            s.put(cx, 2, cz, "BLACK_CANDLE");
        }
        int[][] sanctum = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int[] c : sanctum) {
            glyphStele(s, rng, c[0], c[1], 4 + rng.nextInt(2));
            altarFoot(s, rng, c[0], c[1]);
        }

        // --- The looming ring: skull-crowned glyph steles the Order raises
        //     around the whole reef, tall enough to be seen across the water —
        //     taller out in the Trench. Any that would cap a chamber shaft are
        //     dropped. Low dead-coral clumps still cluster between them: a reef
        //     the plague already killed (live coral would rot gray in air). ---
        int stele = 4 + (tier - 4) * 2;
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4) + 0.15;
            int sx = (int) Math.round(Math.cos(a) * (platR - 2));
            int sz = (int) Math.round(Math.sin(a) * (platR - 2));
            if (nearMouth(sx, sz)) {
                continue;
            }
            glyphStele(s, rng, sx, sz, stele + rng.nextInt(3));
        }
        String[] coral = {"DEAD_TUBE_CORAL_BLOCK", "DEAD_BRAIN_CORAL_BLOCK", "DEAD_HORN_CORAL_BLOCK",
                "DEAD_BUBBLE_CORAL_BLOCK", "DEAD_FIRE_CORAL_BLOCK"};
        int clumps = 6 + rng.nextInt(3);
        for (int i = 0; i < clumps; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(a) * (platR - 4));
            int sz = (int) Math.round(Math.sin(a) * (platR - 4));
            if (nearMouth(sx, sz)) {
                continue;
            }
            int h = 1 + rng.nextInt(2);
            String c = rng.nextInt(100) < 45 ? "SCULK" : coral[rng.nextInt(coral.length)];
            s.column(sx, 2, 1 + h, sz, ShapeSketch.solid(c));
        }

        // Turtle-egg clutches strewn across the floor — the plague's spawn.
        for (int i = 0; i < 5; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int ex = (int) Math.round(Math.cos(a) * (platR - 5));
            int ez = (int) Math.round(Math.sin(a) * (platR - 5));
            if (Math.hypot(ex, ez) > 4 && !nearMouth(ex, ez)) {   // never on the heart or a shaft
                s.put(ex, 2, ez, "TURTLE_EGG");
            }
        }

        // --- Four egg-chambers, buried under the reef and roofed by it, each
        //     entered by a stepped shaft and dressed as an offering niche. ---
        List<Rel> chests = new ArrayList<>();
        chests.add(buryChamber(s, rng, 0, 5, p.glow()));
        chests.add(buryChamber(s, rng, -8, -2, p.glow()));
        chests.add(buryChamber(s, rng, 8, 3, p.glow()));
        chests.add(buryChamber(s, rng, 0, -8, p.glow()));

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
     * A looming glyph stele: a carved black pillar the Order raises to mark a
     * nest, a soul-flame set glowing in its shaft and a skull crowning it.
     */
    private static void glyphStele(ShapeSketch s, Random rng, int x, int z, int height) {
        int base = 2, top = base + height - 1;
        for (int y = base; y <= top; y++) {
            String m = rng.nextInt(100) < 28 ? "SCULK"
                    : rng.nextInt(100) < 50 ? "CHISELED_DEEPSLATE"
                    : rng.nextInt(100) < 55 ? "POLISHED_BLACKSTONE_BRICKS"
                    : "POLISHED_BLACKSTONE";
            s.put(x, y, z, m);
        }
        s.put(x, base + Math.max(1, height / 2), z, "SOUL_LANTERN");   // a soul-flame eye
        s.put(x, top + 1, z, rng.nextInt(100) < 65 ? "WITHER_SKELETON_SKULL" : "SKELETON_SKULL");
    }

    /**
     * The offering at a sanctum stele's foot: a gilded slab facing the socket,
     * a skull upon it and a black candle beside — the Order's altar in
     * miniature. Placed one step toward the centre so it never blocks the
     * stele's own footing or a Vessel's pace around the reef.
     */
    private static void altarFoot(ShapeSketch s, Random rng, int x, int z) {
        int fx = x - Integer.signum(x), fz = z - Integer.signum(z);
        s.put(fx, 2, fz, "GILDED_BLACKSTONE");
        s.put(fx, 3, fz, rng.nextBoolean() ? "WITHER_SKELETON_SKULL" : "SKELETON_SKULL");
        s.put(x - Integer.signum(x), 2, z, "BLACK_CANDLE");
    }

    /** True when a deck cell sits close enough to a shaft mouth to risk
     *  sealing the chamber below — the Order's clutter keeps its distance. */
    private static boolean nearMouth(int x, int z) {
        for (int[] m : SHAFT_MOUTHS) {
            if (Math.abs(x - m[0]) <= 2 && Math.abs(z - m[1]) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * A buried egg-chamber, dressed as an offering niche: solid casing, a
     * hollow room, a stepped shaft up to the reef deck on the +z face. Soul
     * light, a skull in the corner, a gilded wall behind the offering and a
     * chain hung from the roof — the Order's charnel wealth. Returns the chest
     * cell at the room's far wall, roofed by the reef above and walled on every
     * side but the shaft, exactly like the Abyssal Monolith's reliquary.
     */
    private static Rel buryChamber(ShapeSketch s, Random rng, int cx, int cz, String glow) {
        s.fillBox(cx - 2, -3, cz - 2, cx + 2, 0, cz + 2, ShapeSketch.solid("DEEPSLATE"));
        s.carveBox(cx - 1, -2, cz - 1, cx + 1, -1, cz + 1);
        s.carveBox(cx, 1, cz + 3, cx, 3, cz + 3);
        s.put(cx, 0, cz + 3, "DEEPSLATE");
        s.carveBox(cx, 0, cz + 2, cx, 2, cz + 2);
        s.put(cx, -1, cz + 2, "DEEPSLATE");
        s.carveBox(cx, -1, cz + 1, cx, 1, cz + 1);
        s.put(cx, -2, cz + 1, "DEEPSLATE");
        // Offering-niche dressing (never touches the chest cell or its
        // headroom, nor the shaft the player crawls out through).
        s.put(cx - 1, -2, cz - 1, "SOUL_LANTERN");       // the niche's cold light
        s.put(cx + 1, -2, cz, "SCULK_CATALYST");
        s.put(cx, -2, cz - 2, "GILDED_BLACKSTONE");      // gilded wall behind the offering
        s.put(cx - 1, -2, cz + 1, rng.nextInt(100) < 60  // a skull watching the corner
                ? "WITHER_SKELETON_SKULL" : "SKELETON_SKULL");
        s.put(cx - 1, -1, cz - 1, "CHAIN");              // hung from the roof over the light
        if (glow != null) {
            s.put(cx + 1, -2, cz - 2, glow);             // a tier ember set in the wall
        }
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
