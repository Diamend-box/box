package com.diamend.darksea.naval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the naval HUD line: hull pips visible in every state, color
 * thresholds, surge readout, and the harpooned override.
 */
class NavalHudTest {

    @Test
    @DisplayName("a healthy boat shows its name, a full green bar, and a ready surge")
    void healthyBoat() {
        String line = NavalHud.render("Stormrunner", 10, 10, false, 0, false);
        assertTrue(line.contains("Stormrunner"));
        assertTrue(line.contains("<green>" + "▮".repeat(10)));
        assertFalse(line.contains("▯"), "a full hull has no empty pips");
        assertTrue(line.contains("⚡ ready"));
        assertFalse(line.contains("wounded"));
        assertFalse(line.contains("HARPOONED"));
    }

    @Test
    @DisplayName("hull damage empties pips and the numeric HP matches")
    void damagedHull() {
        String line = NavalHud.render("Sloop", 6, 10, false, 0, false);
        assertTrue(line.contains("▮".repeat(6) + "</yellow><dark_gray>" + "▯".repeat(4)));
        assertTrue(line.contains("<white>6</white>"));
    }

    @Test
    @DisplayName("pip color turns yellow when pressed and red when nearly under")
    void hullColorThresholds() {
        assertEquals("green", NavalHud.hullColor(1.0));
        assertEquals("green", NavalHud.hullColor(0.71));
        assertEquals("yellow", NavalHud.hullColor(0.7));
        assertEquals("yellow", NavalHud.hullColor(0.36));
        assertEquals("red", NavalHud.hullColor(0.35));
        assertEquals("red", NavalHud.hullColor(0.05));
    }

    @Test
    @DisplayName("fractional HP rounds pips up — a chipped hull never looks whole-pip worse")
    void fractionalHpRoundsUp() {
        // 9.5/10 still shows all ten pips; the number carries the fraction.
        String line = NavalHud.render("Cutter", 9.5, 10, false, 0, false);
        assertTrue(line.contains("▮".repeat(10)));
        assertTrue(line.contains("<white>9.5</white>"));
    }

    @Test
    @DisplayName("the wounded flag appears with the slow and never alongside the harpoon alert")
    void woundedFlag() {
        assertTrue(NavalHud.render("Rowboat", 8, 10, true, 3, false).contains("⚠ hull wounded"));
        assertFalse(NavalHud.render("Rowboat", 8, 10, true, 3, true).contains("⚠ hull wounded"),
                "harpooned owns the alert slot; wounded yields");
    }

    @Test
    @DisplayName("the surge readout counts down and flips to ready at zero")
    void surgeReadout() {
        assertTrue(NavalHud.render("Sloop", 10, 10, false, 7, false).contains("⚡ 7s"));
        assertTrue(NavalHud.render("Sloop", 10, 10, false, 0, false).contains("⚡ ready"));
    }

    @Test
    @DisplayName("harpooned replaces the boat name but the hull pips stay visible")
    void harpoonedKeepsHullVisible() {
        String line = NavalHud.render("Stormrunner", 4, 10, true, 2, true);
        assertTrue(line.contains("HARPOONED"));
        assertTrue(line.contains("surge to cut"));
        assertFalse(line.contains("Stormrunner"), "the alert takes the name's slot");
        assertTrue(line.contains("▮".repeat(4)), "hull pips survive every state");
        assertTrue(line.contains("⚡ 2s"), "the cut tool's timer stays visible too");
    }

    @Test
    @DisplayName("HP at or below zero renders an empty red-numbered bar, not garbage")
    void zeroAndNegativeHp() {
        String line = NavalHud.render("Rowboat", 0, 10, true, 1, false);
        assertFalse(line.contains("▮"));
        assertTrue(line.contains("▯".repeat(10)));
        assertTrue(line.contains("<white>0</white>"));
        assertTrue(NavalHud.render("Rowboat", -2, 10, true, 1, false)
                .contains("<white>0</white>"), "negative HP clamps to 0");
    }

    @Test
    @DisplayName("whole HP prints without a decimal, halves keep one place")
    void hpTrimming() {
        assertEquals("7", NavalHud.trim(7.0));
        assertEquals("6.5", NavalHud.trim(6.5));
        assertEquals("0", NavalHud.trim(-1.0));
        assertEquals("3.3", NavalHud.trim(3.25000001));
    }
}
