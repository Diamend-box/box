package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Some drowned kingdom's sea-watch: a tall ruined keep (15/18/21 blocks by
 * tier) on a rubble motte, ringed by a collapsing bailey wall with a gate,
 * two roofless outbuildings and rubble everywhere. Inside, jutting wall
 * stones spiral past two part-rotted plank floors; the chest waits on the
 * first floor under the second's shadow — you can't see it from outside,
 * you have to climb past whatever lives on the ground floor.
 */
final class RuinedWatchtower implements DemoShape {

    @Override
    public String id() {
        return "ruined-watchtower";
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

        int moundR = 12 + tier + rng.nextInt(2);  // t2 14-15 .. t4 16-17
        int height = 9 + 3 * tier;                // wall top: exactly 15 / 18 / 21

        // Rubble motte: ground and broken stone in weathered patches.
        ShapeSketch.Shader rubble = (x, y, z, r) ->
                ShapeSketch.cellNoise(Math.floorDiv(x, 3), 1, Math.floorDiv(z, 3)) < 55
                        ? p.groundPatch(x, y, z, r) : p.rockPatch(x, y, z, r);
        s.blob(0, -3, 0, moundR - 1, 2.8, moundR - 1, p::rockPatch, 0.2);
        s.disc(0, -1, 0, moundR + 1, rubble, 0.22);
        s.disc(0, 0, 0, moundR - 2, rubble, 0.2);
        s.disc(0, 1, 0, moundR - 5, p::rockPatch, 0.18);

        // Bailey: a ruined curtain wall around the yard, height rotting away
        // column by column, with a clean gate gap aligned to the keep door.
        int wallR = moundR - 2;
        for (int x = -wallR - 1; x <= wallR + 1; x++) {
            for (int z = -wallR - 1; z <= wallR + 1; z++) {
                if (Math.round(Math.sqrt((double) x * x + z * z)) != wallR) {
                    continue;
                }
                if (z > 0 && Math.abs(x) < 3) {
                    continue;  // the gate
                }
                long h = Math.floorMod(x * 341873128712L + z * 132897987541L, 100);
                int wallH = h < 30 ? 0 : h < 55 ? 1 : h < 85 ? 2 : 3;
                int base = Math.max(1, s.topY(x, z, 0) + 1);
                for (int y = 0; y < wallH; y++) {
                    s.put(x, base + y, z, p.rockMix(rng));
                }
            }
        }

        // Two roofless outbuildings against the yard, one either side.
        int hutX = 0, hutZ = 0, hutFloor = 1;
        for (int b = 0; b < 2; b++) {
            int cx = (b == 0 ? -1 : 1) * (wallR - 4);
            int cz = b == 0 ? -2 : 2;
            int floorY = Math.max(1, s.topY(cx, cz, 0));
            if (b == 1) {
                hutX = cx;
                hutZ = cz;
                hutFloor = floorY;
            }
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 1; z++) {
                    s.put(x, floorY, z, p.rockDetail());
                    boolean edge = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 1;
                    if (edge && rng.nextInt(100) < 62) {
                        s.put(x, floorY + 1, z, p.rockMix(rng));
                        if (rng.nextInt(100) < 40) {
                            s.put(x, floorY + 2, z, p.rockMix(rng));
                        }
                    } else if (!edge) {
                        s.carve(x, floorY + 1, z);
                        s.carve(x, floorY + 2, z);
                    }
                }
            }
            if (b == 0) {
                s.put(cx, floorY + 1, cz, "BARREL");
                s.put(cx + 1, floorY + 1, cz - 1, "COBWEB");
            } else {
                s.put(cx, floorY + 1, cz, "COBWEB");
            }
        }

        // Keep shell: foundation, floor, weathered ring walls (mossier low).
        s.disc(0, 1, 0, 4.6, p::rockMix, 0.1);
        s.disc(0, 2, 0, 4.2, ShapeSketch.solid(p.rockDetail()), 0.05);
        for (int y = 3; y <= height; y++) {
            final int wy = y;
            s.wallRing(0, y, 0, 4, r -> {
                int roll = r.nextInt(100);
                if (roll < (wy < 7 ? 28 : 12)) {
                    return p.rockAccent();
                }
                return roll < 95 ? p.rock() : p.rockDetail();
            });
        }
        for (int x = -3; x <= 3; x++) {  // hollow interior, clearing any spill
            for (int z = -3; z <= 3; z++) {
                if (x * x + z * z <= 10) {
                    for (int y = 3; y <= height + 3; y++) {
                        s.carve(x, y, z);
                    }
                }
            }
        }

        // Doorway south, with steps up the motte to reach it.
        s.carve(0, 3, 4);
        s.carve(0, 4, 4);
        s.carve(1, 3, 4);
        s.carve(1, 4, 4);
        s.put(0, 1, 5, p.rockMix(rng));
        s.put(0, 0, 6, p.groundMix(rng));

        // Arrow slits, the big breach, and its rubble spill eastward.
        s.carve(4, height - 4, 0);
        s.carve(-4, height - 4, 0);
        s.carve(0, height - 4, -4);
        s.carve(0, height - 8, -4);
        s.carveBlob(4.2, height - 1.5, rng.nextInt(3) - 1, 2.6, 3.0, 2.6);
        for (int i = 0; i < 10; i++) {
            int rx = 5 + rng.nextInt(5);
            int rz = rng.nextInt(9) - 4;
            int top = s.topY(rx, rz, -1);
            if (top >= -1) {
                s.put(rx, top + 1, rz, p.rockMix(rng));
            }
        }

        // Ragged crenellation on what's left of the top.
        s.wallRing(0, height + 1, 0, 4, r -> r.nextInt(100) < 55 ? p.rock() : ShapeSketch.AIR);

        // Two part-rotted plank floors. Each keeps a climbing hole above the
        // spiral of jutting wall stones; the chest zone and its roof are
        // always solid so the loot stays hidden and dry.
        int floor1 = 3 + (height - 3) / 3;
        int floor2 = 3 + 2 * (height - 3) / 3;
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int f = 0; f < 2; f++) {
            int fy = f == 0 ? floor1 : floor2;
            double holeAngle = phase + (fy - 3) * (Math.PI / 3);
            int hx = (int) Math.round(Math.cos(holeAngle) * 2);
            int hz = (int) Math.round(Math.sin(holeAngle) * 2);
            if (hz < 0) {  // keep the hole off the chest corner
                hx = -hx;
                hz = -hz;
            }
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (x * x + z * z > 10) {
                        continue;
                    }
                    boolean nearHole = Math.abs(x - hx) <= 1 && Math.abs(z - hz) <= 1;
                    if (!nearHole && rng.nextInt(100) < 70) {
                        s.put(x, fy, z, p.wood());
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {  // guaranteed chest platform + roof
            for (int z = -3; z <= -1; z++) {
                s.put(x, floor1, z, p.wood());
                s.put(x, floor2, z, p.wood());
            }
        }

        // Jutting wall stones spiral up the inside — the way up.
        int step = 0;
        for (int y = 3; y <= height - 2; y++, step++) {
            double angle = phase + step * (Math.PI / 3);
            int lx = (int) Math.round(Math.cos(angle) * 3);
            int lz = (int) Math.round(Math.sin(angle) * 3);
            if (!s.solidAt(lx, y, lz)) {
                s.put(lx, y, lz, p.rockDetail());
            }
        }

        // Chest on the first floor against the north wall, lantern beside it.
        List<Rel> chests = new ArrayList<>();
        s.carve(0, floor1 + 1, -2);
        s.carve(0, floor1 + 2, -2);
        chests.add(new Rel(0, floor1 + 1, -2));
        s.carve(1, floor1 + 1, -2);
        s.put(1, floor1 + 1, -2, p.glow());

        // Tier 3+: a crypt under the north yard — the garrison buried its
        // dead with their pay, and someone left a shrine down there too. A
        // stepped trench in the yard leads down.
        if (tier >= 3) {
            s.fillBox(-2, -4, -9, 2, 0, -5, p::rockMix);
            s.carveBox(-1, -3, -8, 1, -2, -6);
            s.put(0, 0, -4, p.rockMix(rng));
            s.carveBox(0, 1, -4, 0, 3, -4);
            s.put(0, -1, -5, p.rockMix(rng));
            s.carveBox(0, 0, -5, 0, 2, -5);
            s.put(0, -2, -6, p.rockMix(rng));
            s.carveBox(0, -1, -6, 0, 1, -6);
            s.put(0, -3, -7, p.rockMix(rng));
            chests.add(new Rel(0, -3, -8));
            s.put(1, -3, -8, p.glow());
            s.put(-1, -3, -6, "SKELETON_SKULL");
            s.put(1, -3, -6, "BLACK_CANDLE");
        }

        // Tier 4: a rubble cache dug in under the second outbuilding,
        // entered by two rough steps outside its south wall.
        if (tier >= 4) {
            int cx = hutX, cz = hutZ, floorY = hutFloor;
            s.fillBox(cx - 2, floorY - 3, cz - 2, cx + 2, floorY - 1, cz + 2, p::rockMix);
            s.carveBox(cx - 1, floorY - 2, cz - 1, cx + 1, floorY - 1, cz + 1);
            s.put(cx, floorY - 1, cz + 4, p.rockMix(rng));
            s.carveBox(cx, floorY, cz + 4, cx, floorY + 2, cz + 4);
            s.put(cx, floorY - 2, cz + 3, p.rockMix(rng));
            s.carveBox(cx, floorY - 1, cz + 3, cx, floorY + 1, cz + 3);
            s.carveBox(cx, floorY - 2, cz + 2, cx, floorY, cz + 2);
            chests.add(new Rel(cx, floorY - 2, cz - 1));
            s.put(cx + 1, floorY - 2, cz + 1, p.glow());
            s.put(cx - 1, floorY - 2, cz + 1, "SKELETON_SKULL");
        }

        // Mobs: one prowling the yard, one at the gate, one on the ground
        // floor between the door and the way up.
        List<Rel> mobs = new ArrayList<>();
        double yardAngle = Math.PI + rng.nextDouble() * Math.PI * 0.8;  // west-ish
        int yx = (int) Math.round(Math.cos(yardAngle) * (moundR - 4));
        int yz = (int) Math.round(Math.sin(yardAngle) * (moundR - 4));
        mobs.add(s.stand(yx, yz, p::groundMix));
        mobs.add(s.stand(0, wallR, p::groundMix));
        mobs.add(new Rel(0, 3, 1));

        s.shore(radiusBudget(), p::groundPatch);

        return ShapeBuild.of(s, chests, mobs);
    }
}
