package com.diamend.darksea.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The reset countdown: each warning mark fires once, when its time arrives. */
class SeaResetSchedulerTest {

    private static final List<Integer> WARNS = List.of(30, 10, 5, 1);

    private static long minutes(double m) {
        return (long) (m * 60_000);
    }

    @Test
    void noWarningBeforeTheFirstMark() {
        assertEquals(List.of(), SeaResetScheduler.dueWarnings(minutes(40), WARNS, Set.of()));
    }

    @Test
    void eachMarkFiresAsItsMinuteArrives() {
        assertEquals(List.of(30), SeaResetScheduler.dueWarnings(minutes(30), WARNS, Set.of()));
        assertEquals(List.of(10), SeaResetScheduler.dueWarnings(minutes(10), WARNS, Set.of(30)));
        assertEquals(List.of(5), SeaResetScheduler.dueWarnings(minutes(5), WARNS, Set.of(30, 10)));
        assertEquals(List.of(1), SeaResetScheduler.dueWarnings(minutes(1), WARNS, Set.of(30, 10, 5)));
    }

    @Test
    void aMarkAlreadyAnnouncedDoesNotRepeat() {
        assertEquals(List.of(), SeaResetScheduler.dueWarnings(minutes(30), WARNS, Set.of(30)));
    }

    @Test
    void quietBetweenMarks() {
        assertEquals(List.of(), SeaResetScheduler.dueWarnings(minutes(8), WARNS, Set.of(30, 10)));
    }

    @Test
    void aMarkFiresEvenIfTheClockSlippedPastItExactly() {
        // 29.9 minutes left rounds up to 30, so the 30-minute mark still fires.
        assertEquals(List.of(30), SeaResetScheduler.dueWarnings(minutes(29.9), WARNS, Set.of()));
    }

    @Test
    void aLaggedTickStillCatchesASkippedMark() {
        // The tick landed at 4 minutes without ever seeing exactly 5 — the
        // 5-minute mark is overdue and still fires (once), so nothing is lost.
        assertEquals(List.of(5), SeaResetScheduler.dueWarnings(minutes(4), WARNS, Set.of(30, 10)));
    }
}
