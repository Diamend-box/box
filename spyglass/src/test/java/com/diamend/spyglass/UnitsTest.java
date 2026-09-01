package com.diamend.spyglass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diamend.spyglass.inspect.Query;
import com.diamend.spyglass.report.DumpWriter;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Attributes;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;
import com.diamend.spyglass.watch.Watch;
import com.diamend.spyglass.watch.WatchCategory;

/** The small pieces: naming, formatting, paging, and the dump file. */
class UnitsTest {

    @Test
    void sectionsAnswerToTheirAliases() {
        assertEquals(Section.INVENTORY, Section.byName("inv"));
        assertEquals(Section.INVENTORY, Section.byName("INVENTORY"));
        assertEquals(Section.POSITION, Section.byName("loc"));
        assertEquals(Section.NBT, Section.byName("raw"));
        assertNull(Section.byName("trousers"));
        assertNull(Section.byName(""));
        // Only two sections need a live player; the rest read from the save.
        assertTrue(Section.INVENTORY.offline());
        assertFalse(Section.PERMISSIONS.offline());
        assertFalse(Section.SCOREBOARD.offline());
    }

    @Test
    void watchCategoriesParseLeniently() {
        assertEquals(Set.of(WatchCategory.CHAT, WatchCategory.BLOCKS),
                WatchCategory.parse(List.of("chat", "blocks")));
        assertEquals(WatchCategory.values().length, WatchCategory.parse(List.of("all")).size());
        // A typo costs you that one category, not the whole watch.
        assertEquals(Set.of(WatchCategory.CHAT), WatchCategory.parse(List.of("chat", "chta")));
        assertTrue(WatchCategory.parse(List.of("nonsense")).isEmpty());
        assertEquals("all", WatchCategory.describe(WatchCategory.parse(List.of("all"))));
        assertEquals("none", WatchCategory.describe(Set.of()));
    }

    @Test
    void aWatchOnlyReportsWhatItWasAskedFor() {
        Watch watch = new Watch(Watch.CONSOLE, "CONSOLE", null, "Notch",
                Set.of(WatchCategory.CHAT));

        assertTrue(watch.wants(WatchCategory.CHAT));
        assertFalse(watch.wants(WatchCategory.MOVEMENT));
        assertTrue(watch.isConsole());
        assertTrue(watch.isFor(null, "notch"), "names match without case");
        assertFalse(watch.isFor(null, "someone else"));
    }

    @Test
    void aBusyPlayerCannotFloodTheConsole() {
        Watch watch = new Watch(Watch.CONSOLE, "CONSOLE", null, "Notch",
                Set.of(WatchCategory.COMBAT));
        long now = 1_000_000L;

        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            if (watch.allow(10, now)) {
                allowed++;
            }
        }

        assertEquals(10, allowed);
        assertEquals(40, watch.takeSuppressed());
        assertEquals(0, watch.takeSuppressed(), "counted once, then forgotten");
        // A second later the budget is fresh again.
        assertTrue(watch.allow(10, now + 1001L));
    }

    @Test
    void queriesFilterAndReadSlots() {
        Query filter = new Query("beef", false);
        assertTrue(filter.matches("cooked_beef x32"));
        assertFalse(filter.matches("diamond_sword"));
        assertNull(filter.slot());

        Query slot = new Query("36", false);
        assertEquals(36, slot.slot());

        assertTrue(Query.none().matches("anything at all"));
    }

    @Test
    void numbersReadLikeSomebodyWroteThem() {
        assertEquals("20", Fmt.num(20.0));
        assertEquals("17.5", Fmt.num(17.5));
        assertEquals("0.05", Fmt.num(0.05));
        assertEquals("48,210", Fmt.count(48_210));
        assertEquals("12.5 blocks", Fmt.centimetres(1250));
        assertEquals("1.25 km", Fmt.centimetres(125_000));
        assertEquals("2h 30m", Fmt.duration(9_000_000L));
        assertEquals("never", Fmt.stamp(0));
        assertTrue(Fmt.ticks(1200).startsWith("1m"));
        assertTrue(Fmt.bar(5, 20, 4).startsWith("[#---]"));
        assertEquals("diamond_sword", Fmt.shortKey("minecraft:diamond_sword"));
        assertEquals("a…", Fmt.clip("abcdef", 2));
    }

    @Test
    void attributeNamesFoldAcrossVersions() {
        assertEquals("max_health", Attributes.fold("minecraft:generic.max_health"));
        assertEquals("max_health", Attributes.fold("GENERIC_MAX_HEALTH"));
        assertEquals("max_health", Attributes.fold("minecraft:max_health"));
        assertEquals("movement_speed", Attributes.fold("generic.movement-speed"));
    }

    @Test
    void safeSwallowsWhatAForkDoesNotImplement() {
        assertEquals("fallback", Safe.call(() -> {
            throw new UnsupportedOperationException("not implemented");
        }, "fallback"));
        assertEquals(Safe.UNKNOWN, Safe.text(() -> null));
        assertEquals(7, Safe.integer(() -> 7, 0));
    }

    @Test
    void reportsPageWithoutLosingTheTitle() {
        Report report = new Report().title("Notch (online) — stats");
        for (int i = 0; i < 25; i++) {
            report.text("line " + i);
        }
        List<String> plain = report.plain();

        assertEquals("=== Notch (online) — stats ===", plain.get(0));
        assertEquals("  line 0", plain.get(1));
        assertEquals(26, plain.size());
    }

    @Test
    void dumpsAreWrittenAndOldOnesPrunedAway(@TempDir Path folder) throws IOException {
        DumpWriter writer = new DumpWriter(folder.toFile(), 2);

        File first = writer.write("Notch", List.of("one"));
        File second = writer.write("Notch", List.of("two"));
        File third = writer.write("Notch", List.of("three"));

        assertTrue(third.isFile());
        assertEquals(List.of("three"), Files.readAllLines(third.toPath()));
        File[] kept = folder.toFile().listFiles();
        assertEquals(2, kept == null ? 0 : kept.length, "keep: 2 means two files");
        assertFalse(first.isFile(), "the oldest dump is pruned");
        assertTrue(second.isFile() || third.isFile());
    }

    @Test
    void aDumpFileNameIsSafeEvenForAUuidTarget(@TempDir Path folder) throws IOException {
        DumpWriter writer = new DumpWriter(folder.toFile(), 0);

        File file = writer.write("069a79f4-44e9-4726-a5be-fca90e38aaf5", List.of("x"));

        assertTrue(file.getName().startsWith("069a79f4-44e9-4726-a5be-fca90e38aaf5-"));
        assertTrue(file.getName().endsWith(".txt"));
    }
}
