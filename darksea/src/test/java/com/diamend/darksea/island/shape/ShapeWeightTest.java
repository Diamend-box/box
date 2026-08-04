package com.diamend.darksea.island.shape;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config's hold over which islands a ring raises.
 *
 * <p>Counts alone were never the whole of "how the sea is distributed" —
 * islands-per-ring says how many a ring gets, and this says what they are.
 */
class ShapeWeightTest {

    private static Map<String, Integer> countPicks(int tier, Map<String, Integer> weights) {
        Random rng = new Random(20260804L);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 5000; i++) {
            counts.merge(DemoShapes.pick(tier, rng, weights).id(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("a shape weighted to zero never appears in that ring")
    void zeroKeepsAShapeOut() {
        for (DemoShape shape : DemoShapes.forTier(4)) {
            Map<String, Integer> counts = countPicks(4, Map.of(shape.id(), 0));
            assertEquals(0, counts.getOrDefault(shape.id(), 0),
                    shape.id() + " was raised despite a weight of 0");
        }
    }

    @Test
    @DisplayName("weights are relative, so a heavier shape really does dominate")
    void weightsSkewTheRing() {
        Map<String, Integer> weights = new HashMap<>();
        for (DemoShape shape : DemoShapes.forTier(4)) {
            weights.put(shape.id(), 1);
        }
        String favoured = DemoShapes.forTier(4).get(0).id();
        weights.put(favoured, 100);
        Map<String, Integer> counts = countPicks(4, weights);
        assertTrue(counts.getOrDefault(favoured, 0) > 4000,
                "a 100-to-1 weighting should dominate the ring, saw "
                        + counts.getOrDefault(favoured, 0) + " of 5000");
    }

    @Test
    @DisplayName("shapes the config does not name keep their built-in rarity")
    void unnamedShapesAreUntouched() {
        // Naming one shape must not silently flatten every other shape's odds.
        Map<String, Integer> plain = countPicks(3, Map.of());
        Map<String, Integer> tweaked = countPicks(3, Map.of("ruined-castle", 0));
        for (DemoShape shape : DemoShapes.forTier(3)) {
            if (shape.id().equals("ruined-castle")) {
                continue;
            }
            int before = plain.getOrDefault(shape.id(), 0);
            int after = tweaked.getOrDefault(shape.id(), 0);
            assertTrue(after >= before,
                    shape.id() + " should be at least as common once a rival is removed");
        }
    }

    @Test
    @DisplayName("zeroing every shape falls back to the built-in weights, it does not throw")
    void anEmptyRingDoesNotBringTheServerDown() {
        Map<String, Integer> weights = new HashMap<>();
        for (DemoShape shape : DemoShapes.forTier(2)) {
            weights.put(shape.id(), 0);
        }
        // A sea with no shapes at all is expressed by islands-per-ring: 0, not
        // by zeroing the roster; this must degrade rather than fail the paste.
        assertEquals(5000, countPicks(2, weights).values().stream().mapToInt(Integer::intValue).sum());
    }
}
