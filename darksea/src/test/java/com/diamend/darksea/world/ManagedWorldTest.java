package com.diamend.darksea.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule table that keeps two worlds from inheriting each other's mechanics.
 * These assertions are the design written down: if someone later flips a flag
 * without meaning to, the failure names which rule moved.
 */
class ManagedWorldTest {

    @Test
    void theSeaKeepsEverythingItAlwaysHad() {
        ManagedWorld sea = ManagedWorld.DARK_SEA;
        assertTrue(sea.hasExposure());
        assertTrue(sea.hasRunLoot());
        assertTrue(sea.hasNavalCombat());
        assertTrue(sea.pvpEnabled());
        assertTrue(sea.losesItemsOnDeath());
        assertTrue(sea.hasIslands());
        assertFalse(sea.hasOreVeins());
    }

    /** The caves are an ordinary place you go to work, not a second sea. */
    @Test
    void thecavesAreOrdinary() {
        ManagedWorld caves = ManagedWorld.CULTIST;
        assertFalse(caves.hasExposure(), "no ring damage in the caves");
        assertFalse(caves.hasNavalCombat(), "no boats underground");
        assertFalse(caves.hasIslands());
        assertTrue(caves.hasOreVeins());
    }

    /**
     * The one rule worth stating twice: veins are contested, so PvP is on —
     * but a materials run is not an extraction run, so dying doesn't strip
     * what you mined.
     */
    @Test
    void cavesAreContestedButNotAnExtractionRun() {
        ManagedWorld caves = ManagedWorld.CULTIST;
        assertTrue(caves.pvpEnabled(), "veins should be worth fighting over");
        assertFalse(caves.losesItemsOnDeath(), "mining is not a second extraction loop");
        assertFalse(caves.hasRunLoot(), "ore is not run-scoped loot");
    }

    /** Both worlds are designed spaces; neither is a building canvas. */
    @Test
    void bothWorldsProtectTheirTerrain() {
        for (ManagedWorld world : ManagedWorld.values()) {
            assertTrue(world.protectsTerrain(), world.toString());
        }
    }

    /** Exactly one world owns islands, exactly one owns veins. */
    @Test
    void theTwoContentSystemsDoNotOverlap() {
        long islands = 0;
        long veins = 0;
        for (ManagedWorld world : ManagedWorld.values()) {
            if (world.hasIslands()) {
                islands++;
            }
            if (world.hasOreVeins()) {
                veins++;
            }
            assertFalse(world.hasIslands() && world.hasOreVeins(),
                    world + " runs both content systems");
        }
        assertNotEquals(0, islands);
        assertNotEquals(0, veins);
    }
}
