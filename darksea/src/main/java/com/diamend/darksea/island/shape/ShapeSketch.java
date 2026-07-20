package com.diamend.darksea.island.shape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * Mutable scratch space a shape draws into: a map of relative offsets to
 * material names, plus organic-geometry helpers. Later writes win, so shapes
 * layer like paint: skirt, then rock mass, then carved interiors, then decor.
 * Carving only writes AIR where something was already placed or above the
 * waterline — it never punches holes into open ocean below y 0.
 */
final class ShapeSketch {

    static final String AIR = "AIR";

    /** A material chosen by position — lets textures form patches, not static. */
    interface Shader {
        String at(int x, int y, int z, Random rng);
    }

    /**
     * Deterministic 0..99 hash per texture cell. Feeding block coordinates
     * divided down (e.g. {@code floorDiv(x,3)}) gives every 3x4x3 region one
     * roll, so material variation reads as weathered patches instead of
     * per-block noise.
     */
    static int cellNoise(int cx, int cy, int cz) {
        long h = cx * 341873128712L + cy * 914534023L + cz * 132897987541L;
        h ^= h >>> 13;
        h *= 0x5DEECE66DL;
        h ^= h >>> 15;
        return (int) ((h & 0x7FFFFFFFL) % 100);
    }

    private final Map<Rel, String> blocks = new LinkedHashMap<>();
    private final Random rng;
    private int maxY = Integer.MIN_VALUE;
    private int minY = Integer.MAX_VALUE;

    ShapeSketch(long seed) {
        this.rng = new Random(seed);
    }

    Random rng() {
        return rng;
    }

    Map<Rel, String> blocks() {
        return blocks;
    }

    void put(int x, int y, int z, String material) {
        blocks.put(new Rel(x, y, z), material);
        if (y > maxY) {
            maxY = y;
        }
        if (y < minY) {
            minY = y;
        }
    }

    String get(int x, int y, int z) {
        return blocks.get(new Rel(x, y, z));
    }

    boolean solidAt(int x, int y, int z) {
        String material = get(x, y, z);
        return material != null && !AIR.equals(material);
    }

    void carve(int x, int y, int z) {
        if (y >= 0 || blocks.containsKey(new Rel(x, y, z))) {
            put(x, y, z, AIR);
        }
    }

    /** Highest solid y in the column, or {@code fallback} if it's empty. */
    int topY(int x, int z, int fallback) {
        for (int y = maxY; y >= minY; y--) {
            if (solidAt(x, y, z)) {
                return y;
            }
        }
        return fallback;
    }

    /**
     * Column-coherent radius noise in {@code 1 ± amount}: every block in a
     * vertical column shares the same offset, so jittered masses read as
     * craggy rock faces instead of static.
     */
    private double columnNoise(int x, int z, double amount) {
        if (amount <= 0) {
            return 1.0;
        }
        long hash = x * 341873128712L + z * 132897987541L;
        hash ^= hash >>> 13;
        hash *= 0x5DEECE66DL;
        hash ^= hash >>> 15;
        double unit = (hash & 0xFFFF) / (double) 0xFFFF;  // 0..1
        return 1.0 + (unit * 2.0 - 1.0) * amount;
    }

    /** An irregular ellipsoid of material — the basic rock mass. */
    void blob(double cx, double cy, double cz, double rx, double ry, double rz,
              Function<Random, String> material, double jitter) {
        blob(cx, cy, cz, rx, ry, rz, shader(material), jitter);
    }

    /** The blob, shaded by position — patchy rock faces instead of static. */
    void blob(double cx, double cy, double cz, double rx, double ry, double rz,
              Shader material, double jitter) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        int z0 = (int) Math.floor(cz - rz - 1), z1 = (int) Math.ceil(cz + rz + 1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double reach = columnNoise(x, z, jitter);
                for (int y = y0; y <= y1; y++) {
                    double dx = (x - cx) / (rx * reach);
                    double dy = (y - cy) / ry;
                    double dz = (z - cz) / (rz * reach);
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        put(x, y, z, material.at(x, y, z, rng));
                    }
                }
            }
        }
    }

    /** Same ellipsoid, but carving to air (interiors, cave mouths). */
    void carveBlob(double cx, double cy, double cz, double rx, double ry, double rz) {
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        int z0 = (int) Math.floor(cz - rz), z1 = (int) Math.ceil(cz + rz);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        carve(x, y, z);
                    }
                }
            }
        }
    }

    /** One irregular horizontal layer. */
    void disc(double cx, int y, double cz, double radius,
              Function<Random, String> material, double jitter) {
        disc(cx, y, cz, radius, shader(material), jitter);
    }

    /** The disc, shaded by position. */
    void disc(double cx, int y, double cz, double radius, Shader material, double jitter) {
        int x0 = (int) Math.floor(cx - radius - 1), x1 = (int) Math.ceil(cx + radius + 1);
        int z0 = (int) Math.floor(cz - radius - 1), z1 = (int) Math.ceil(cz + radius + 1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double reach = radius * columnNoise(x, z, jitter);
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= reach * reach) {
                    put(x, y, z, material.at(x, y, z, rng));
                }
            }
        }
    }

    /** A dome: stacked shrinking discs — sand mounds, hills. */
    void dome(double cx, int baseY, double cz, double radius, int height,
              Function<Random, String> material, double jitter) {
        for (int dy = 0; dy <= height; dy++) {
            double t = dy / (double) (height + 1);
            double r = radius * Math.sqrt(1.0 - t * t);
            if (r < 0.6) {
                break;
            }
            disc(cx, baseY + dy, cz, r, material, jitter);
        }
    }

    /** The dome, shaded by position. */
    void dome(double cx, int baseY, double cz, double radius, int height,
              Shader material, double jitter) {
        for (int dy = 0; dy <= height; dy++) {
            double t = dy / (double) (height + 1);
            double r = radius * Math.sqrt(1.0 - t * t);
            if (r < 0.6) {
                break;
            }
            disc(cx, baseY + dy, cz, r, material, jitter);
        }
    }

    void fillBox(int x0, int y0, int z0, int x1, int y1, int z1,
                 Function<Random, String> material) {
        fillBox(x0, y0, z0, x1, y1, z1, shader(material));
    }

    /** The box, shaded by position. */
    void fillBox(int x0, int y0, int z0, int x1, int y1, int z1, Shader material) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    put(x, y, z, material.at(x, y, z, rng));
                }
            }
        }
    }

    void carveBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    carve(x, y, z);
                }
            }
        }
    }

    void column(int x, int y0, int y1, int z, Function<Random, String> material) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            put(x, y, z, material.apply(rng));
        }
    }

    /** A voxel line between two points — branches, fallen pillars, rib spokes. */
    void line(int x0, int y0, int z0, int x1, int y1, int z1,
              Function<Random, String> material) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0),
                Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0))));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            put((int) Math.round(x0 + (x1 - x0) * t),
                    (int) Math.round(y0 + (y1 - y0) * t),
                    (int) Math.round(z0 + (z1 - z0) * t),
                    material.apply(rng));
        }
    }

    /** One ring of a circular wall (radius measured to block centers). */
    void wallRing(double cx, int y, double cz, int radius, Function<Random, String> material) {
        for (int x = (int) cx - radius; x <= cx + radius; x++) {
            for (int z = (int) cz - radius; z <= cz + radius; z++) {
                double dx = x - cx, dz = z - cz;
                if (Math.round(Math.sqrt(dx * dx + dz * dz)) == radius) {
                    put(x, y, z, material.apply(rng));
                }
            }
        }
    }

    static Function<Random, String> solid(String material) {
        return rng -> material;
    }

    private static Shader shader(Function<Random, String> material) {
        return (x, y, z, rng) -> material.apply(rng);
    }

    /**
     * Shoreline pass: grows a beach apron out from the land edge, stepping
     * from the dry waterline down into underwater shallows, so islands wade
     * into the sea instead of dropping off a cliff. The apron writes only
     * into empty cells and never past {@code maxRadius}, so it can neither
     * bury structures nor bust the shape's radius budget — safe to call as
     * the very last stroke of a build. Per-column width jitter makes the
     * waterline wander instead of tracing the rock exactly.
     */
    void shore(int maxRadius, Shader beach) {
        java.util.Set<Rel> visited = new java.util.HashSet<>();
        java.util.List<Rel> frontier = new java.util.ArrayList<>();
        for (Map.Entry<Rel, String> e : blocks.entrySet()) {
            Rel r = e.getKey();
            if (r.y() >= 0 && !AIR.equals(e.getValue())) {
                Rel col = new Rel(r.x(), 0, r.z());
                if (visited.add(col)) {
                    frontier.add(col);
                }
            }
        }
        // Each BFS ring steps the profile down: waterline, then shallows.
        int[] profile = {0, 0, -1, -1, -2};
        for (int d = 1; d <= profile.length; d++) {
            java.util.List<Rel> next = new java.util.ArrayList<>();
            for (Rel cell : frontier) {
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = cell.x() + step[0], z = cell.z() + step[1];
                    Rel col = new Rel(x, 0, z);
                    if (!visited.add(col)
                            || Math.max(Math.abs(x), Math.abs(z)) > maxRadius) {
                        continue;
                    }
                    int width = 3 + cellNoise(x, 7, z) % 3;  // 3..5 columns wide
                    if (d <= width) {
                        for (int y = -3; y <= profile[d - 1]; y++) {
                            if (!blocks.containsKey(new Rel(x, y, z))) {
                                put(x, y, z, beach.at(x, y, z, rng));
                            }
                        }
                    }
                    next.add(col);
                }
            }
            frontier = next;
        }
    }

    /** Blocks nothing should stand on (decor, damage sources, liquids). */
    private static final java.util.Set<String> BAD_FOOTING = java.util.Set.of(
            "LAVA", "WATER", "MAGMA_BLOCK", "DEAD_BUSH", "SEA_PICKLE", "SOUL_FIRE",
            "SOUL_SOIL", "LANTERN", "SOUL_LANTERN", "SCULK_SENSOR", "SCULK_SHRIEKER",
            "COBWEB", "MOSS_CARPET", "LILY_PAD", "BROWN_MUSHROOM", "RED_MUSHROOM",
            "HANGING_ROOTS", "SEAGRASS", "BLACK_CANDLE", "CANDLE", "SKELETON_SKULL",
            "SOUL_CAMPFIRE", "WITHER_SKELETON_SKULL", "WITHER_ROSE", "CAULDRON",
            "CHAIN");

    /**
     * A guaranteed-safe standing cell in the column: on top of whatever is
     * there (creating or repairing the footing if it's water or decor), with
     * clear air above by construction. Shapes use this for mob spawns so
     * random features can never bury them.
     */
    Rel stand(int x, int z, Function<Random, String> ground) {
        int top = topY(x, z, -1);
        if (top < 0) {
            put(x, 0, z, ground.apply(rng));
            top = 0;
        }
        String footing = get(x, top, z);
        if (footing != null && BAD_FOOTING.contains(footing)) {
            put(x, top, z, ground.apply(rng));
        }
        return new Rel(x, top + 1, z);
    }
}
