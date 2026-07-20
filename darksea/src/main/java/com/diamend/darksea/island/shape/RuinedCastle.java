package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The rare landmark island: a drowned Naxian fortress, far bigger than
 * anything else the sea builds (~70x70 at ring 2 up to ~79x79 at ring 4).
 * A square curtain wall with four corner towers rings a courtyard holding
 * a roofless keep (throne dais over a sunken vault), a great hall over an
 * undercroft, and a chapel the Order has since reconsecrated. Every chest
 * sits in a buried or walled room — 4/5/6 of them by tier, two elected as
 * vaults — and the garrison is denser and drawn from one ring deeper than
 * the water it stands in.
 */
final class RuinedCastle implements DemoShape {

    /** Castle masonry per tier — bricks, not raw rock. */
    private record Stones(String brick, String cracked, String mossy, String detail) {

        static Stones forTier(int tier) {
            return switch (Math.max(2, Math.min(4, tier))) {
                case 2 -> new Stones("STONE_BRICKS", "CRACKED_STONE_BRICKS",
                        "MOSSY_STONE_BRICKS", "CHISELED_STONE_BRICKS");
                case 3 -> new Stones("DEEPSLATE_BRICKS", "CRACKED_DEEPSLATE_BRICKS",
                        "DEEPSLATE_TILES", "CHISELED_DEEPSLATE");
                default -> new Stones("POLISHED_BLACKSTONE_BRICKS",
                        "CRACKED_POLISHED_BLACKSTONE_BRICKS", "POLISHED_BLACKSTONE",
                        "CHISELED_POLISHED_BLACKSTONE");
            };
        }

        /** Patch-shaded masonry: weathering in 3x4x3 cells, not static. */
        String patch(int x, int y, int z, Random rng) {
            int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), Math.floorDiv(y, 4),
                    Math.floorDiv(z, 3));
            int roll = rng.nextInt(100);
            if (cell < 22) {
                return roll < 70 ? cracked : brick;
            }
            if (cell >= 78) {
                return roll < 60 ? mossy : brick;
            }
            return roll < 88 ? brick : cracked;
        }
    }

    @Override
    public String id() {
        return "ruined-castle";
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
    public int rarityWeight() {
        return 3;  // ~5% of picks against five-six shapes at weight 10
    }

    @Override
    public int chestCount(int tier) {
        return tier + 2;  // 4 / 5 / 6
    }

    @Override
    public int vaultChestCount() {
        return 2;
    }

    @Override
    public int mobTierBoost() {
        return 1;
    }

    @Override
    public int mobCapBonus() {
        return 4;
    }

    @Override
    public double wealthFloor() {
        return 1.4;
    }

    @Override
    public int radiusBudget() {
        return 44;  // the plinth reaches ~40; the beach apron needs the rest
    }

    @Override
    public ShapeBuild build(int tier, long seed) {
        Palette p = Palette.forTier(tier);
        Stones st = Stones.forTier(tier);
        ShapeSketch s = new ShapeSketch(seed);
        Random rng = s.rng();

        int plinthR = 25 + 3 * tier;         // 31 / 34 / 37 → spans ~66/72/78
        int w = plinthR - 6;                 // curtain wall half-extent
        int wallH = 5 + tier;                // curtain wall height
        int towerH = wallH + 6;              // full corner tower height

        // --- Plinth and shore: the rock the fortress was raised on. ---
        s.blob(0, -3, 0, plinthR - 1, 3.5, plinthR - 1, p::rockPatch, 0.12);
        s.disc(0, -1, 0, plinthR + 1, p::groundPatch, 0.05);
        s.disc(0, 0, 0, plinthR, p::rockPatch, 0.05);
        // Flat courtyard slab the whole bailey stands on.
        s.fillBox(-w - 1, 1, -w - 1, w + 1, 1, w + 1, st::patch);

        // --- Curtain wall: two blocks thick, rotting column by column. ---
        for (int ring = 0; ring < 2; ring++) {
            int r = w - ring;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r) {
                        continue;
                    }
                    if (z > 0 && z >= r - 1 && Math.abs(x) <= 2) {
                        continue;  // the gate opening, south
                    }
                    long h = Math.floorMod(x * 341873128712L + z * 132897987541L, 100);
                    int keep = h < 12 ? 1 + (int) (h % 3)          // stubs
                            : h < 40 ? wallH - 2 - (int) (h % 2)   // part-fallen
                            : wallH;                               // still standing
                    int base = Math.max(1, s.topY(x, z, 0) + 1);
                    for (int y = 0; y < keep; y++) {
                        s.put(x, base + y, z, st.patch(x, base + y, z, rng));
                    }
                    // Crenellation survives on the outer ring of full sections.
                    if (ring == 0 && keep == wallH && (x + z & 1) == 0) {
                        s.put(x, base + keep, z, st.brick());
                    }
                }
            }
        }

        // --- Two breaches, rubble spilling outward. ---
        int[][] breaches = {{w, -10}, {-w, 8}};
        for (int[] breach : breaches) {
            s.carveBlob(breach[0], 4, breach[1], 2.6, 3.2, 3.4);
            int dir = breach[0] > 0 ? 1 : -1;
            for (int i = 0; i < 8; i++) {
                int rx = breach[0] + dir * (1 + rng.nextInt(4));
                int rz = breach[1] + rng.nextInt(7) - 3;
                int top = s.topY(rx, rz, -1);
                if (top >= -1) {
                    s.put(rx, top + 1, rz, st.patch(rx, top + 1, rz, rng));
                }
            }
        }

        // --- Corner towers: one per corner, each ruined its own amount. ---
        int c = w - 1;
        int[][] corners = {{c, c}, {c, -c}, {-c, c}, {-c, -c}};
        for (int[] corner : corners) {
            int cx = corner[0], cz = corner[1];
            int h = switch (rng.nextInt(4)) {
                case 0 -> 3 + rng.nextInt(3);  // collapsed to a shell
                case 1 -> towerH - 3;
                default -> towerH;
            };
            for (int x = cx - 3; x <= cx + 3; x++) {
                for (int z = cz - 3; z <= cz + 3; z++) {
                    boolean shell = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                    for (int y = 2; y <= 1 + h; y++) {
                        if (shell) {
                            if (y < 1 + h || (x + z & 1) == 0) {  // ragged top
                                s.put(x, y, z, st.patch(x, y, z, rng));
                            }
                        } else {
                            s.carve(x, y, z);
                        }
                    }
                    s.put(x, 1, z, st.mossy());
                }
            }
            // Doorway facing the courtyard center.
            int dx = cx > 0 ? -3 : 3, dz = cz > 0 ? -3 : 3;
            s.carve(cx + dx, 2, cz);
            s.carve(cx + dx, 3, cz);
            s.carve(cx, 2, cz + dz);
            s.carve(cx, 3, cz + dz);
        }

        // --- Gatehouse: two square towers flanking the south gate. ---
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 4 : -8, x1 = side == 0 ? 8 : -4;
            for (int x = x0; x <= x1; x++) {
                for (int z = w - 4; z <= w; z++) {
                    boolean shell = x == x0 || x == x1 || z == w - 4 || z == w;
                    for (int y = 2; y <= 1 + wallH + 3; y++) {
                        if (shell) {
                            s.put(x, y, z, st.patch(x, y, z, rng));
                        } else {
                            s.carve(x, y, z);
                        }
                    }
                    s.put(x, 1, z, st.detail());
                    s.put(x, 2 + wallH + 3, z, st.brick());  // roof slab
                }
            }
        }

        // --- The keep: roofless great tower against the north wall. ---
        int kz0 = -w + 4, kz1 = -w + 17;   // north edge .. toward court
        int keepH = 10 + tier;
        for (int x = -8; x <= 8; x++) {
            for (int z = kz0; z <= kz1; z++) {
                boolean shell = x == -8 || x == 8 || z == kz0 || z == kz1;
                if (shell) {
                    // South face is mostly down; the others rot near the top.
                    int h = z == kz1 ? 3 + rng.nextInt(3)
                            : keepH - (rng.nextInt(100) < 30 ? rng.nextInt(3) : 0);
                    for (int y = 2; y <= 1 + h; y++) {
                        s.put(x, y, z, st.patch(x, y, z, rng));
                    }
                } else {
                    for (int y = 2; y <= 1 + keepH; y++) {
                        s.carve(x, y, z);
                    }
                }
                s.put(x, 1, z, st.detail());
            }
        }
        // Doorway in the ruined south face.
        s.carveBox(-1, 2, kz1, 1, 4, kz1);
        // A part-rotted upper floor, reached by jutting wall stones.
        int floorY = 1 + keepH / 2;
        for (int x = -7; x <= 7; x++) {
            for (int z = kz0 + 1; z <= kz1 - 1; z++) {
                if (rng.nextInt(100) < 55) {
                    s.put(x, floorY, z, p.wood());
                }
            }
        }
        for (int i = 0; i < keepH / 2 - 1; i++) {  // climbing stones, west wall
            s.put(-7, 2 + i, kz0 + 2 + i, st.detail());
        }
        // Throne dais at the north end: steps, seat, side braziers.
        int dz = kz0 + 3;
        s.fillBox(-3, 2, kz0 + 1, 3, 2, dz + 1, ShapeSketch.solid(st.detail()));
        s.fillBox(-2, 3, kz0 + 1, 2, 3, dz, ShapeSketch.solid(st.brick()));
        s.put(0, 4, kz0 + 1, st.detail());   // the seat's back
        s.put(-3, 3, dz + 1, p.glow());
        s.put(3, 3, dz + 1, p.glow());

        // --- Great hall: long roofless nave, west courtyard. ---
        int hx0 = -w + 4, hx1 = -w + 16;
        for (int x = hx0; x <= hx1; x++) {
            for (int z = -6; z <= 8; z++) {
                boolean shell = x == hx0 || x == hx1 || z == -6 || z == 8;
                if (shell) {
                    int h = 3 + (rng.nextInt(100) < 45 ? 1 : 0);
                    for (int y = 2; y <= 1 + h; y++) {
                        s.put(x, y, z, st.patch(x, y, z, rng));
                    }
                } else {
                    s.carve(x, 2, z);
                    s.carve(x, 3, z);
                    s.carve(x, 4, z);
                }
                s.put(x, 1, z, st.mossy());
            }
        }
        s.carveBox(hx1, 2, 0, hx1, 3, 2);  // east door to the court
        for (int i = 0; i < 4; i++) {       // two rows of column stubs
            int px = hx0 + 3 + i * 3;
            s.column(px, 2, 2 + rng.nextInt(3), -3, ShapeSketch.solid(st.brick()));
            s.column(px, 2, 2 + rng.nextInt(3), 5, ShapeSketch.solid(st.brick()));
        }
        s.put(hx0 + 2, 2, 1, "BARREL");

        // --- Chapel of the Order: east courtyard, reconsecrated. ---
        int cx0 = w - 15, cx1 = w - 5;
        for (int x = cx0; x <= cx1; x++) {
            for (int z = -8; z <= 2; z++) {
                boolean shell = x == cx0 || x == cx1 || z == -8 || z == 2;
                if (shell) {
                    int h = 4 - (rng.nextInt(100) < 35 ? 1 : 0);
                    for (int y = 2; y <= 1 + h; y++) {
                        s.put(x, y, z, st.patch(x, y, z, rng));
                    }
                } else {
                    s.carveBox(x, 2, z, x, 5, z);
                }
                s.put(x, 1, z, st.detail());
            }
        }
        s.carveBox(cx0, 2, -3, cx0, 3, -2);  // west door to the court
        // Altar against the east wall: skull, candles, a toppled stele.
        int ax = cx1 - 2;
        s.fillBox(ax - 1, 2, -4, ax, 2, -2, ShapeSketch.solid(st.detail()));
        s.put(ax, 3, -3, "WITHER_SKELETON_SKULL");
        s.put(ax - 1, 3, -4, "BLACK_CANDLE");
        s.put(ax - 1, 3, -2, "BLACK_CANDLE");
        s.line(cx0 + 2, 2, -6, cx0 + 4, 2, -6, ShapeSketch.solid(st.detail()));
        s.put(cx0 + 3, 2, 0, "SOUL_FIRE");
        s.put(cx0 + 3, 1, 0, "SOUL_SOIL");

        // --- Courtyard scatter: rubble heaps and a well ring. ---
        for (int i = 0; i < 14; i++) {
            int rx = rng.nextInt(2 * w - 20) - (w - 10);
            int rz = rng.nextInt(2 * w - 20) - (w - 10);
            int top = s.topY(rx, rz, 1);
            if (top <= 2) {
                s.put(rx, top + 1, rz, rng.nextInt(100) < 70
                        ? st.cracked() : p.rockAccent());
            }
        }
        int wellZ = 12;
        s.wallRing(0, 2, wellZ, 2, ShapeSketch.solid(st.brick()));
        for (int y = 1; y >= -2; y--) {
            s.put(0, y, wellZ, "WATER");
        }

        // ------------------------------------------------------------------
        // Chest rooms — drawn last so nothing above can breach them.
        // ------------------------------------------------------------------
        List<Rel> chests = new ArrayList<>();

        // 1. The keep vault, sunk beneath the throne dais. A stair hole
        //    east of the dais drops into it.
        int vz = kz0 + 2;
        s.fillBox(-3, -3, vz - 1, 3, 1, vz + 3, p::rockMix);
        s.carveBox(-2, -2, vz, 2, -1, vz + 2);
        s.put(4, 1, vz + 4, st.detail());
        s.carveBox(4, 2, vz + 4, 4, 3, vz + 4);
        s.put(3, 0, vz + 4, st.detail());
        s.carveBox(3, 1, vz + 4, 3, 2, vz + 4);
        s.carveBox(2, -1, vz + 3, 2, 0, vz + 4);
        chests.add(new Rel(0, -2, vz + 1));
        s.put(-1, -2, vz + 1, p.glow());
        if (tier >= 4) {
            s.put(1, -2, vz, "GILDED_BLACKSTONE");
        }

        // 2. The undercroft below the great hall, entered by a stair
        //    trench at the hall's south side.
        int ux = hx0 + 8;
        s.fillBox(ux - 3, -3, -3, ux + 3, 0, 3, p::rockMix);
        s.carveBox(ux - 2, -2, -2, ux + 2, -1, 2);
        s.put(ux + 4, 0, 4, st.brick());
        s.carveBox(ux + 4, 1, 4, ux + 4, 2, 4);
        s.put(ux + 3, -1, 4, st.brick());
        s.carveBox(ux + 3, 0, 4, ux + 3, 1, 4);
        s.carveBox(ux + 2, -1, 3, ux + 2, 0, 4);
        chests.add(new Rel(ux - 1, -2, 0));
        s.put(ux + 1, -2, 0, p.glow());
        s.put(ux - 1, -2, 2, "COBWEB");

        // 3. The chapel reliquary: a crawl space under the altar, opened
        //    from the nave floor in front of it.
        s.fillBox(ax - 3, -2, -5, cx1, 1, -1, p::rockMix);
        s.carveBox(ax - 2, -1, -4, ax, 0, -2);
        s.carveBox(ax - 3, 0, -3, ax - 3, 1, -3);
        s.carveBox(ax - 3, -1, -3, ax - 2, 0, -3);
        chests.add(new Rel(ax, -1, -3));
        s.put(ax - 1, -1, -4, p.glow());

        // 4. The gatehouse cache, inside the east gate tower's ground room.
        s.carveBox(5, 2, w - 3, 7, 4, w - 1);
        s.carveBox(4, 2, w - 3, 4, 3, w - 3);  // doorway to the gate passage
        chests.add(new Rel(6, 2, w - 2));
        s.put(7, 2, w - 3, p.glow());

        // 5. Tier 3+: the crypt, off the northeast yard down a stepped trench.
        if (tier >= 3) {
            int gx = 13, gz = -13;
            s.fillBox(gx - 2, -4, gz - 2, gx + 2, 0, gz + 2, p::rockMix);
            s.carveBox(gx - 1, -3, gz - 1, gx + 1, -2, gz + 1);
            s.put(gx, 0, gz + 4, p.rockMix(rng));
            s.carveBox(gx, 1, gz + 4, gx, 2, gz + 4);
            s.put(gx, -1, gz + 3, p.rockMix(rng));
            s.carveBox(gx, 0, gz + 3, gx, 1, gz + 3);
            s.put(gx, -2, gz + 2, p.rockMix(rng));
            s.carveBox(gx, -1, gz + 2, gx, 0, gz + 2);
            s.carveBox(gx, -2, gz + 1, gx, -1, gz + 1);
            chests.add(new Rel(gx, -3, gz));
            s.put(gx - 1, -3, gz, p.glow());
            s.put(gx + 1, -3, gz, "SKELETON_SKULL");
        }

        // 6. Tier 4: the cistern beside the well shaft — swim down, then
        //    south through a flooded gap into the dry vault.
        if (tier >= 4) {
            s.fillBox(-2, -4, wellZ + 1, 2, 0, wellZ + 5, p::rockMix);
            s.carveBox(-1, -3, wellZ + 2, 1, -2, wellZ + 4);
            s.carveBox(0, -3, wellZ + 1, 0, -2, wellZ + 1);
            chests.add(new Rel(0, -3, wellZ + 4));
            s.put(1, -3, wellZ + 2, p.glow());
        }

        // --- The garrison: denser than any other island. ---
        List<Rel> mobs = new ArrayList<>();
        mobs.add(s.stand(-11, 11, p::groundMix));          // west court
        mobs.add(s.stand(12, -2, p::groundMix));           // east court
        mobs.add(s.stand(0, w - 6, p::groundMix));         // inside the gate
        mobs.add(s.stand(0, kz1 + 3, p::groundMix));       // keep forecourt
        mobs.add(s.stand(hx1 + 2, 10, p::groundMix));      // hall corner
        mobs.add(s.stand(cx0 - 2, -10, p::groundMix));     // chapel yard
        mobs.add(s.stand(w - 1, 0, r -> st.brick()));      // the east wall walk

        s.shore(radiusBudget(), p::groundPatch);

        return ShapeBuild.of(s, chests, mobs);
    }
}
