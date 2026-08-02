package com.diamend.darksea.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pointing a player at the vault lever. Getting the axes backwards would
 * send people the wrong way across a 70-block island, which is worse than
 * the silence it replaces.
 */
class BearingTest {

    @Test
    void theFourCardinalsMatchMinecraftsAxes() {
        // North is -Z, east is +X. Every one of these is a direction a player
        // would read off F3 and expect to agree with the message.
        assertEquals("north", Bearing.compass(0, -10));
        assertEquals("south", Bearing.compass(0, 10));
        assertEquals("east", Bearing.compass(10, 0));
        assertEquals("west", Bearing.compass(-10, 0));
    }

    @Test
    void theDiagonalsReadTheWayPeopleSayThem() {
        assertEquals("north-east", Bearing.compass(10, -10));
        assertEquals("south-east", Bearing.compass(10, 10));
        assertEquals("south-west", Bearing.compass(-10, 10));
        assertEquals("north-west", Bearing.compass(-10, -10));
    }

    @Test
    void sectorsAreCentredOnTheirDirectionNotOffsetFromIt() {
        // A hair either side of due north must still read north, and the
        // sector boundary must sit at 22.5 degrees.
        assertEquals("north", Bearing.compass(1, -100));
        assertEquals("north", Bearing.compass(-1, -100));
        assertEquals("north", Bearing.compass(20, -100));
        assertEquals("north-east", Bearing.compass(60, -100));
    }

    @Test
    void standingOnItSaysSo() {
        assertEquals("here", Bearing.compass(0, 0));
    }

    @Test
    void distanceIsHorizontalAndWhole() {
        assertEquals(5, Bearing.blocks(3, 4));
        assertEquals(10, Bearing.blocks(-10, 0));
        assertEquals(0, Bearing.blocks(0, 0));
        assertEquals(14, Bearing.blocks(10, 10));
    }
}
