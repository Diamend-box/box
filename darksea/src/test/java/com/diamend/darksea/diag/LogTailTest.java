package com.diamend.darksea.diag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-memory warning buffer behind {@code /ds diag warnings}. The two
 * properties worth pinning: it keeps what the config loaders quietly dropped,
 * and it stays bounded while doing so.
 */
class LogTailTest {

    private static LogRecord record(Level level, String message) {
        return new LogRecord(level, message);
    }

    @Test
    void infoAndBelowAreIgnored() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.INFO, "Loaded 42 shop lines"));
        tail.publish(record(Level.FINE, "chatter"));
        tail.publish(record(Level.CONFIG, "chatter"));

        assertTrue(tail.clean());
        assertEquals(0, tail.totalCount());
        assertTrue(tail.entries().isEmpty());
    }

    @Test
    void warningsAndSeveresAreKeptAndCountedSeparately() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.WARNING, "shops.yml: unknown item id 'kelp_sword'"));
        tail.publish(record(Level.SEVERE, "startup step 'ore-nodes' failed"));
        tail.publish(record(Level.WARNING, "ores.yml: vein 'rustglass' skipped"));

        assertFalse(tail.clean());
        assertEquals(2, tail.warningCount());
        assertEquals(1, tail.severeCount());
        assertEquals(3, tail.totalCount());
        assertEquals(3, tail.entries().size());
    }

    @Test
    void entriesComeBackOldestFirst() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.WARNING, "first"));
        tail.publish(record(Level.WARNING, "second"));
        tail.publish(record(Level.WARNING, "third"));

        assertEquals(List.of("first", "second", "third"),
                tail.entries().stream().map(LogTail.Entry::message).toList());
    }

    @Test
    void theBufferIsBoundedAndDropsTheOldest() {
        LogTail tail = new LogTail(3);
        for (int i = 1; i <= 10; i++) {
            tail.publish(record(Level.WARNING, "warning " + i));
        }
        List<LogTail.Entry> entries = tail.entries();
        assertEquals(3, entries.size(), "a listener warning every tick must not grow forever");
        assertEquals(List.of("warning 8", "warning 9", "warning 10"),
                entries.stream().map(LogTail.Entry::message).toList());
    }

    @Test
    void theCountIsOfEverythingSeenNotOfWhatSurvived() {
        LogTail tail = new LogTail(2);
        for (int i = 0; i < 9; i++) {
            tail.publish(record(Level.WARNING, "w" + i));
        }
        assertEquals(2, tail.entries().size());
        assertEquals(9, tail.warningCount(),
                "'3 shown' must not be mistaken for '3 happened'");
    }

    @Test
    void tailReturnsTheMostRecentAndNeverOverruns() {
        LogTail tail = new LogTail();
        for (int i = 1; i <= 5; i++) {
            tail.publish(record(Level.WARNING, "w" + i));
        }
        assertEquals(List.of("w4", "w5"),
                tail.tail(2).stream().map(LogTail.Entry::message).toList());
        assertEquals(5, tail.tail(50).size(), "asking for more than exists returns all of it");
        assertTrue(tail.tail(0).isEmpty());
    }

    @Test
    void aRecordWithNoMessageStillProducesAReadableEntry() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.WARNING, null));
        assertEquals(1, tail.entries().size());
        assertEquals("(no message)", tail.entries().get(0).message());
    }

    @Test
    void aNullRecordIsIgnoredRatherThanThrowingInsideALogHandler() {
        LogTail tail = new LogTail();
        tail.publish(null);
        assertTrue(tail.clean());
    }

    @Test
    void severeIsFlaggedOnTheEntryItself() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.WARNING, "w"));
        tail.publish(record(Level.SEVERE, "s"));
        assertFalse(tail.entries().get(0).severe());
        assertTrue(tail.entries().get(1).severe());
    }

    @Test
    void resetClearsBothTheBufferAndTheCounters() {
        LogTail tail = new LogTail();
        tail.publish(record(Level.SEVERE, "boom"));
        tail.reset();
        assertTrue(tail.clean());
        assertEquals(0, tail.severeCount());
        assertTrue(tail.entries().isEmpty());
    }

    @Test
    void itCapturesThroughARealLoggerTheWayThePluginAttachesIt() {
        Logger logger = Logger.getLogger("darksea-logtail-test");
        logger.setUseParentHandlers(false);
        LogTail tail = new LogTail();
        logger.addHandler(tail);
        try {
            logger.info("ignored");
            logger.warning("ores.yml: vein 'rustglass' skipped");
            logger.severe("world 'darksea_caves' could not be created");
        } finally {
            logger.removeHandler(tail);
        }

        assertEquals(1, tail.warningCount());
        assertEquals(1, tail.severeCount());
        assertEquals("ores.yml: vein 'rustglass' skipped", tail.entries().get(0).message());
    }
}
