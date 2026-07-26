package com.diamend.darksea.world.cultist;

import java.util.Random;

/**
 * Improved Perlin noise in three dimensions, seeded from a long.
 *
 * <p>Hand-rolled rather than reached for from Bukkit deliberately. The cave
 * shape has to be decidable without a server so it can be unit tested — see
 * {@link CultistCarve} — and Bukkit's {@code SimplexOctaveGenerator} would
 * drag the whole API into the pure core. This is the standard algorithm, about
 * sixty lines, and identical output for identical seeds on any machine.
 */
public final class CaveNoise {

    private final int[] perm = new int[512];

    public CaveNoise(long seed) {
        int[] source = new int[256];
        for (int i = 0; i < 256; i++) {
            source[i] = i;
        }
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {   // Fisher-Yates, so every seed shuffles fully
            int j = rng.nextInt(i + 1);
            int swap = source[i];
            source[i] = source[j];
            source[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = source[i & 255];
        }
    }

    /** Noise at a point, in roughly [-1, 1]. */
    public double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int a = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;

        return lerp(w,
                lerp(v,
                        lerp(u, grad(perm[aa], xf, yf, zf),
                                grad(perm[ba], xf - 1, yf, zf)),
                        lerp(u, grad(perm[ab], xf, yf - 1, zf),
                                grad(perm[bb], xf - 1, yf - 1, zf))),
                lerp(v,
                        lerp(u, grad(perm[aa + 1], xf, yf, zf - 1),
                                grad(perm[ba + 1], xf - 1, yf, zf - 1)),
                        lerp(u, grad(perm[ab + 1], xf, yf - 1, zf - 1),
                                grad(perm[bb + 1], xf - 1, yf - 1, zf - 1))));
    }

    /**
     * Summed octaves, normalised back to roughly [-1, 1]. More octaves means
     * more small detail on the cave walls at a proportional cost per block.
     */
    public double octaves(double x, double y, double z, int octaves, double persistence) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double max = 0.0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            max += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return max == 0.0 ? 0.0 : total / max;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
