package com.diamend.darksea.island.shape;

import java.util.Random;

/**
 * Per-tier material roles for the built-in island shapes ("full escalation":
 * the farther ring, the darker the sea). Material names are Bukkit
 * {@code Material} names as strings — kept as strings so this package stays
 * Bukkit-free; a CI test validates every emitted name against the real enum.
 */
public record Palette(int tier,
                      String rock, String rockAccent, String rockDetail,
                      String ground, String groundAccent,
                      String wood, String glow) {

    public static Palette forTier(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 1 -> new Palette(1, "STONE", "ANDESITE", "POLISHED_ANDESITE",
                    "SAND", "SANDSTONE", "OAK_PLANKS", "LANTERN");
            case 2 -> new Palette(2, "COBBLESTONE", "MOSSY_COBBLESTONE", "MOSS_BLOCK",
                    "SAND", "GRAVEL", "SPRUCE_PLANKS", "LANTERN");
            case 3 -> new Palette(3, "COBBLED_DEEPSLATE", "TUFF", "POLISHED_DEEPSLATE",
                    "GRAVEL", "BASALT", "DARK_OAK_PLANKS", "SOUL_LANTERN");
            default -> new Palette(4, "BLACKSTONE", "POLISHED_BLACKSTONE", "OBSIDIAN",
                    "SOUL_SAND", "BLACKSTONE", "DARK_OAK_PLANKS", "SOUL_LANTERN");
        };
    }

    /** Mostly rock with weathered accents — the default cliff texture. */
    public String rockMix(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 70) {
            return rock;
        }
        return roll < 94 ? rockAccent : rockDetail;
    }

    /** Beach/seabed texture: ground with occasional accent patches. */
    public String groundMix(Random rng) {
        return rng.nextInt(100) < 88 ? ground : groundAccent;
    }

    /**
     * Position-shaded cliff texture: 3x4x3 cells each roll one dominant
     * material, so weathering reads as patches and streaks instead of
     * per-block static.
     */
    public String rockPatch(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 3), Math.floorDiv(y, 4),
                Math.floorDiv(z, 3));
        int roll = rng.nextInt(100);
        if (cell < 25) {
            return roll < 78 ? rockAccent : rock;
        }
        if (roll < 90) {
            return rock;
        }
        return roll < 98 ? rockAccent : rockDetail;
    }

    /** Position-shaded beach texture — accent drifts, not speckle. */
    public String groundPatch(int x, int y, int z, Random rng) {
        int cell = ShapeSketch.cellNoise(Math.floorDiv(x, 4), y, Math.floorDiv(z, 4));
        if (cell < 18) {
            return rng.nextInt(100) < 75 ? groundAccent : ground;
        }
        return rng.nextInt(100) < 94 ? ground : groundAccent;
    }

    /** Size multiplier for "islands grow with the tier" escalation. */
    public double scale() {
        return 1.0 + 0.15 * (tier - 1);
    }
}
