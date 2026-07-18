package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Some drowned kingdom's sea-watch, still standing on its rubble mound. The
 * breach in the upper wall and the collapsed floor read from a distance; the
 * chest doesn't — it's inside on the ground floor, past the doorway and
 * whatever spawned in there with it. Jutting wall stones spiral up the
 * inside to a broken lookout.
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

        int moundR = 6 + tier + rng.nextInt(2);
        int height = 6 + 2 * tier + rng.nextInt(2);  // wall top: t2 ~10, t4 ~14

        // Rubble mound: mixed ground and broken stone, rock root below.
        s.blob(0, -3, 0, moundR - 1, 2.5, moundR - 1, p::rockMix, 0.2);
        s.disc(0, -1, 0, moundR, r -> r.nextInt(100) < 55 ? p.groundMix(r) : p.rockMix(r), 0.25);
        s.disc(0, 0, 0, moundR - 2, r -> r.nextInt(100) < 50 ? p.groundMix(r) : p.rockMix(r), 0.22);
        s.disc(0, 1, 0, moundR - 4, p::rockMix, 0.2);

        // Tower shell: foundation, floor, weathered ring walls (mossier low).
        s.disc(0, 1, 0, 3.6, p::rockMix, 0.1);
        s.disc(0, 2, 0, 3.2, ShapeSketch.solid(p.rockDetail()), 0.05);
        for (int y = 3; y <= height; y++) {
            final int wy = y;
            s.wallRing(0, y, 0, 3, r -> {
                int roll = r.nextInt(100);
                if (roll < (wy < 6 ? 28 : 12)) {
                    return p.rockAccent();
                }
                return roll < 95 ? p.rock() : p.rockDetail();
            });
        }
        for (int x = -2; x <= 2; x++) {  // hollow interior, clearing any spill
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z <= 5) {
                    for (int y = 3; y <= height + 3; y++) {
                        s.carve(x, y, z);
                    }
                }
            }
        }

        // Doorway south, with a step up the mound to reach it.
        s.carve(0, 3, 3);
        s.carve(0, 4, 3);
        s.put(0, 1, 4, p.rockMix(rng));
        s.put(0, 0, 5, p.groundMix(rng));

        // Arrow slits, the big breach, and its rubble spill eastward.
        s.carve(3, height - 3, 0);
        s.carve(-3, height - 3, 0);
        s.carve(0, height - 3, -3);
        s.carveBlob(3.2, height - 1.0, rng.nextInt(3) - 1, 2.4, 2.6, 2.4);
        for (int i = 0; i < 8; i++) {
            int rx = 4 + rng.nextInt(4);
            int rz = rng.nextInt(7) - 3;
            int top = s.topY(rx, rz, -1);
            if (top >= -1) {
                s.put(rx, top + 1, rz, p.rockMix(rng));
            }
        }

        // Ragged crenellation on what's left of the top.
        s.wallRing(0, height + 1, 0, 3, r -> r.nextInt(100) < 55 ? p.rock() : ShapeSketch.AIR);

        // Jutting wall stones spiral up inside; a broken plank floor up top.
        double phase = rng.nextDouble() * Math.PI * 2;
        int step = 0;
        for (int y = 3; y <= height - 2; y++, step++) {
            double angle = phase + step * (Math.PI / 3);
            int lx = (int) Math.round(Math.cos(angle) * 2);
            int lz = (int) Math.round(Math.sin(angle) * 2);
            s.put(lx, y, lz, p.rockDetail());
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z <= 5 && !s.solidAt(x, height - 3, z) && rng.nextInt(100) < 55) {
                    s.put(x, height - 3, z, p.wood());
                }
            }
        }

        // Chest against the north inner wall, lantern beside it.
        s.put(0, 2, -2, p.rockDetail());
        s.carve(0, 3, -2);
        s.carve(0, 4, -2);
        Rel chest = new Rel(0, 3, -2);
        s.put(1, 2, -2, p.rockDetail());
        s.carve(1, 3, -2);
        s.put(1, 3, -2, p.glow());

        // Mobs: one prowling the rubble yard, one inside with the loot.
        List<Rel> mobs = new ArrayList<>();
        double yardAngle = Math.PI + rng.nextDouble() * Math.PI;  // west-ish, away from rubble
        int yx = (int) Math.round(Math.cos(yardAngle) * (moundR - 2));
        int yz = (int) Math.round(Math.sin(yardAngle) * (moundR - 2));
        mobs.add(s.stand(yx, yz, p::groundMix));
        mobs.add(new Rel(0, 3, 1));

        return ShapeBuild.of(s, chest, mobs);
    }
}
