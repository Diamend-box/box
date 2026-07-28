package com.diamend.darksea.diag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup ledger behind {@code /ds diag}. Its whole job is to still be
 * readable when the plugin came up wrong, so the cases that matter are the
 * degraded ones.
 */
class StartupReportTest {

    @Test
    void aFreshReportIsHealthyAndEmpty() {
        StartupReport report = new StartupReport();
        assertTrue(report.healthy());
        assertEquals(0, report.stepCount());
        assertEquals(0, report.failureCount());
        assertTrue(report.steps().isEmpty());
        assertNull(report.slowest());
    }

    @Test
    void stepsComeBackInTheOrderTheyRan() {
        StartupReport report = new StartupReport();
        report.ok("world-init", 40L);
        report.ok("npc-spawn", 5L);
        report.ok("ore-nodes", 12L);

        List<StartupReport.Step> steps = report.steps();
        assertEquals(List.of("world-init", "npc-spawn", "ore-nodes"),
                steps.stream().map(StartupReport.Step::name).toList());
    }

    @Test
    void oneFailureMakesTheReportUnhealthyWithoutLosingTheSuccesses() {
        StartupReport report = new StartupReport();
        report.ok("world-init", 40L);
        report.failed("ore-nodes", "NullPointerException: caves world is null", 2L);
        report.ok("naval-hud", 1L);

        assertFalse(report.healthy());
        assertEquals(3, report.stepCount());
        assertEquals(1, report.failureCount());
        assertEquals(3, report.steps().size());
    }

    @Test
    void failuresListsOnlyTheFailedStepsWithTheirDetail() {
        StartupReport report = new StartupReport();
        report.ok("world-init", 1L);
        report.failed("portal-pad", "IllegalStateException: no return pad", 3L);
        report.failed("ore-nodes", "NullPointerException", 1L);

        List<StartupReport.Step> failures = report.failures();
        assertEquals(2, failures.size());
        assertEquals("portal-pad", failures.get(0).name());
        assertEquals("IllegalStateException: no return pad", failures.get(0).detail());
        assertTrue(failures.get(0).failed());
        assertTrue(failures.get(1).failed());
    }

    @Test
    void aNullDetailBecomesEmptyRatherThanPrintingTheWordNull() {
        StartupReport report = new StartupReport();
        report.failed("command", null, 0L);
        assertEquals("", report.failures().get(0).detail());
    }

    @Test
    void timingsSumAndTheSlowestStepIsFindable() {
        StartupReport report = new StartupReport();
        report.ok("config-files", 3L);
        report.ok("world-init", 820L);
        report.failed("ore-nodes", "boom", 7L);

        assertEquals(830L, report.totalMillis());
        assertNotNull(report.slowest());
        assertEquals("world-init", report.slowest().name());
    }

    @Test
    void theSummaryLeadsWithFailedWhenAnythingFailed() {
        StartupReport clean = new StartupReport();
        clean.ok("a", 1L);
        clean.ok("b", 1L);
        assertEquals("2 of 2 startup steps OK in 2ms", clean.summary());

        StartupReport broken = new StartupReport();
        broken.ok("a", 1L);
        broken.failed("b", "boom", 1L);
        assertTrue(broken.summary().contains("1 FAILED"),
                "a degraded summary must say so: " + broken.summary());
        assertTrue(broken.summary().startsWith("1 of 2"), broken.summary());
    }

    @Test
    void theReturnedStepListIsACopyAndCannotBeEditedFromOutside() {
        StartupReport report = new StartupReport();
        report.ok("a", 1L);
        List<StartupReport.Step> steps = report.steps();
        try {
            steps.add(new StartupReport.Step("b", StartupReport.Outcome.OK, "", 0L));
            org.junit.jupiter.api.Assertions.fail("diag output must not be mutable by its reader");
        } catch (UnsupportedOperationException expected) {
            // exactly right
        }
        assertEquals(1, report.stepCount());
    }

    @Test
    void clearReturnsItToTheFreshState() {
        StartupReport report = new StartupReport();
        report.failed("a", "boom", 1L);
        report.clear();
        assertTrue(report.healthy());
        assertEquals(0, report.stepCount());
    }
}
