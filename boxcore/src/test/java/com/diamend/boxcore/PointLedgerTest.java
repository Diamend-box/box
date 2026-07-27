package com.diamend.boxcore;

import com.diamend.boxcore.data.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skill-point ledger, tested without a server.
 *
 * <p>The ledger is "earned minus spent" rather than a single balance, and these
 * are the properties that has to hold: a player can never overdraw, and
 * changing one side never silently eats the other.
 */
class PointLedgerTest {

    private PlayerProfile profile() {
        return new PlayerProfile(UUID.randomUUID());
    }

    @Test
    void availableIsEarnedMinusSpent() {
        PlayerProfile profile = profile();
        profile.addPoints(10);
        assertTrue(profile.spend(4));
        assertEquals(10, profile.getPointsEarned());
        assertEquals(4, profile.getPointsSpent());
        assertEquals(6, profile.getAvailablePoints());
    }

    @Test
    void spendingMoreThanAvailableIsRefused() {
        PlayerProfile profile = profile();
        profile.addPoints(3);
        assertFalse(profile.spend(4), "can't spend what you don't have");
        assertEquals(0, profile.getPointsSpent());
        assertEquals(3, profile.getAvailablePoints());
    }

    @Test
    void takingPointsCannotDriveTheBalanceNegative() {
        PlayerProfile profile = profile();
        profile.addPoints(2);
        profile.addPoints(-10);
        assertEquals(0, profile.getPointsEarned());
        assertEquals(0, profile.getAvailablePoints());
    }

    @Test
    void settingAvailableKeepsSpentIntact() {
        PlayerProfile profile = profile();
        profile.addPoints(10);
        profile.spend(6);
        profile.setAvailablePoints(2);
        assertEquals(6, profile.getPointsSpent(), "spending history survives an admin set");
        assertEquals(2, profile.getAvailablePoints());
        assertEquals(8, profile.getPointsEarned());
    }

    @Test
    void nodeLevelsRoundTripAndZeroRemoves() {
        PlayerProfile profile = profile();
        profile.setNodeLevel("combat.toughness", 3);
        assertEquals(3, profile.getNodeLevel("combat.toughness"));
        assertTrue(profile.hasNode("combat.toughness"));
        profile.setNodeLevel("combat.toughness", 0);
        assertFalse(profile.hasNode("combat.toughness"));
        assertTrue(profile.getNodes().isEmpty());
    }

    @Test
    void collectionTotalsAccumulate() {
        PlayerProfile profile = profile();
        profile.addCollected("cobblestone", 40);
        profile.addCollected("cobblestone", 60);
        profile.addCollected("coal", 5);
        assertEquals(100, profile.getCollected("cobblestone"));
        assertEquals(105, profile.getTotalCollected());
    }

    @Test
    void dirtyFlagTracksUnsavedChanges() {
        PlayerProfile profile = profile();
        profile.markClean();
        assertFalse(profile.isDirty());
        profile.addPoints(1);
        assertTrue(profile.isDirty(), "a change marks the profile for the next autosave");
    }
}
