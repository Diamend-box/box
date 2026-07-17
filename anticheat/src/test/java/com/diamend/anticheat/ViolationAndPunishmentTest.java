package com.diamend.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.violation.PunishmentAction;
import com.diamend.anticheat.violation.PunishmentMode;
import com.diamend.anticheat.violation.ViolationTracker;

class ViolationAndPunishmentTest {

    @Test
    void violationsAccumulate() {
        ViolationTracker tracker = new ViolationTracker(0.0D);
        assertEquals(3.0D, tracker.add(CheckType.REACH, 3.0D, 1_000L), 1.0e-9);
        assertEquals(5.0D, tracker.add(CheckType.REACH, 2.0D, 1_000L), 1.0e-9);
    }

    @Test
    void violationsDecayOverTime() {
        ViolationTracker tracker = new ViolationTracker(0.05D);
        tracker.add(CheckType.REACH, 10.0D, 0L);
        // 100 seconds later, 100 * 0.05 = 5 points have decayed.
        assertEquals(5.0D, tracker.level(CheckType.REACH, 100_000L), 1.0e-9);
    }

    @Test
    void decayNeverGoesNegative() {
        ViolationTracker tracker = new ViolationTracker(1.0D);
        tracker.add(CheckType.AIM, 2.0D, 0L);
        assertEquals(0.0D, tracker.level(CheckType.AIM, 10_000L), 1.0e-9);
    }

    @Test
    void resetClearsLevels() {
        ViolationTracker tracker = new ViolationTracker(0.0D);
        tracker.add(CheckType.KILLAURA, 7.0D, 0L);
        tracker.reset();
        assertEquals(0.0D, tracker.level(CheckType.KILLAURA, 0L), 1.0e-9);
    }

    @Test
    void modeDefaultsToAlertOnly() {
        assertEquals(PunishmentMode.ALERT_ONLY, PunishmentMode.fromConfig(null));
        assertEquals(PunishmentMode.ALERT_ONLY, PunishmentMode.fromConfig("nonsense"));
        assertEquals(PunishmentMode.ALERT_ONLY, PunishmentMode.fromConfig("alert-only"));
        assertEquals(PunishmentMode.ENFORCE, PunishmentMode.fromConfig("enforce"));
    }

    @Test
    void punishmentActionParsing() {
        PunishmentAction cancel = PunishmentAction.parse("10 cancel");
        assertEquals(10, cancel.threshold());
        assertEquals(PunishmentAction.Type.CANCEL, cancel.type());

        PunishmentAction kick = PunishmentAction.parse("25 kick Reaching too far");
        assertEquals(25, kick.threshold());
        assertEquals(PunishmentAction.Type.KICK, kick.type());
        assertEquals("Reaching too far", kick.argument());

        PunishmentAction command = PunishmentAction.parse("30 command ban %player%");
        assertEquals(PunishmentAction.Type.COMMAND, command.type());
        assertEquals("ban %player%", command.argument());
    }

    @Test
    void invalidPunishmentLinesAreNull() {
        assertNull(PunishmentAction.parse(null));
        assertNull(PunishmentAction.parse(""));
        assertNull(PunishmentAction.parse("kick only"));      // no threshold
        assertNull(PunishmentAction.parse("10 fly away"));    // unknown type
    }

    @Test
    void enforceIsAliasedSafely() {
        assertTrue(PunishmentMode.fromConfig("ENFORCE") == PunishmentMode.ENFORCE);
        assertTrue(PunishmentMode.fromConfig("punish") == PunishmentMode.ENFORCE);
    }
}
