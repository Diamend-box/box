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
import com.diamend.spyglass.report.DumpFile;
import com.diamend.spyglass.report.DumpWriter;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.ReportDiff;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Attributes;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;
import com.diamend.spyglass.util.Statistics;
import com.diamend.spyglass.watch.Watch;
import com.diamend.spyglass.watch.WatchCategory;

/** The small pieces: naming, formatting, paging, and the dump file. */
class UnitsTest {

    private static final String UUID_NOTCH = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

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
    void statisticsGetTheNameTheFileUses() {
        // Bukkit's spelling on the left, the one in stats/<uuid>.json on the right.
        assertEquals("mined.stone", Statistics.typed("MINE_BLOCK", "stone"));
        assertEquals("killed.zombie", Statistics.typed("KILL_ENTITY", "ZOMBIE"));
        assertEquals("killed_by.creeper", Statistics.typed("ENTITY_KILLED_BY", "minecraft:creeper"));
        assertEquals("custom.play_time", Statistics.untyped("PLAY_ONE_MINUTE"));
        assertEquals("custom.open_shulker_box", Statistics.untyped("SHULKER_BOX_OPENED"));
        // Anything the two already agree about is just lower-cased.
        assertEquals("custom.jump", Statistics.untyped("JUMP"));
        assertEquals("custom.walk_one_cm", Statistics.untyped("WALK_ONE_CM"));
        // And a name that has already been folded survives a second folding,
        // which is what happens when an offline key is passed back through.
        assertEquals("custom.play_time", Statistics.untyped("play_time"));

        assertEquals("12.5 blocks", Statistics.value("custom.walk_one_cm", 1250));
        assertTrue(Statistics.value("custom.play_time", 1200).startsWith("1m"));
        assertEquals("48,210", Statistics.value("mined.stone", 48_210));
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

        File first = writer.write("Notch", UUID_NOTCH, dump("one"));
        File second = writer.write("Notch", UUID_NOTCH, dump("two"));
        File third = writer.write("Notch", UUID_NOTCH, dump("three"));

        assertTrue(third.isFile());
        assertEquals(List.of("  three"), Files.readAllLines(third.toPath()));
        File[] kept = folder.toFile().listFiles();
        // Two dumps, and each is a .txt with its .json beside it.
        assertEquals(4, kept == null ? 0 : kept.length, "keep: 2 means two pairs");
        assertFalse(first.isFile(), "the oldest dump is pruned");
        assertFalse(sidecarOf(first).isFile(), "and its json goes with it");
        assertTrue(second.isFile() || third.isFile());
    }

    @Test
    void aDumpFileNameIsSafeEvenForAUuidTarget(@TempDir Path folder) throws IOException {
        DumpWriter writer = new DumpWriter(folder.toFile(), 0);

        File file = writer.write(UUID_NOTCH, UUID_NOTCH, dump("x"));

        assertTrue(file.getName().startsWith("069a79f4-44e9-4726-a5be-fca90e38aaf5-"));
        assertTrue(file.getName().endsWith(".txt"));
        assertTrue(sidecarOf(file).isFile(), "the json sidecar is written too");
    }

    @Test
    void aDumpCanBeReadBackAsData(@TempDir Path folder) throws IOException {
        Report report = new Report().title("Notch — full report")
                .header("Vitals")
                .field("health", "17.5/20")
                .header("Inventory")
                .text(" 0 hotbar   diamond_sword")
                .note("32 empty slot(s).");
        Path path = folder.resolve("notch.json");

        DumpFile.of("Notch", UUID_NOTCH, report).write(path);
        DumpFile back = DumpFile.read(path);

        assertEquals("Notch", back.player());
        assertEquals(UUID_NOTCH, back.uuid());
        // Title and blank lines are layout; the other three lines are content.
        assertEquals(3, back.entries().size());
        assertEquals(2, back.comparable().size(), "the note is prose, not a value");
        DumpFile.Entry health = back.entries().get(0);
        assertEquals("Vitals", health.section());
        assertEquals("field", health.kind());
        assertEquals("health", health.label());
        assertEquals("17.5/20", health.value());
    }

    @Test
    void aDiffShowsOnlyWhatMoved() {
        DumpFile before = DumpFile.of("Notch", UUID_NOTCH, new Report()
                .header("Vitals").field("health", "20/20").field("ping", "31 ms")
                .header("Inventory").text(" 0 hotbar   diamond_sword").text(" 1 hotbar   bread x3"));
        DumpFile after = DumpFile.of("Notch", UUID_NOTCH, new Report()
                .header("Vitals").field("health", "11/20").field("ping", "180 ms")
                .header("Inventory").text(" 0 hotbar   diamond_sword").text(" 2 hotbar   tnt x16"));

        String text = String.join("\n", ReportDiff.between(before, "yesterday.json", after, "now", false).plain());

        assertTrue(text.contains("~ health"), text);
        assertTrue(text.contains("20/20  ->  11/20"), text);
        assertTrue(text.contains("- 1 hotbar   bread x3"), text);
        assertTrue(text.contains("+ 2 hotbar   tnt x16"), text);
        // The sword did not move, so it is not mentioned at all.
        assertFalse(text.contains("diamond_sword"), text);
        // Ping always moves, so it is counted rather than listed.
        assertFalse(text.contains("~ ping"), text);
        assertTrue(text.contains("1 that always move"), text);
    }

    @Test
    void aDiffCanBeAskedForEverything() {
        DumpFile before = DumpFile.of("Notch", UUID_NOTCH,
                new Report().header("Vitals").field("ping", "31 ms"));
        DumpFile after = DumpFile.of("Notch", UUID_NOTCH,
                new Report().header("Vitals").field("ping", "180 ms"));

        String quiet = String.join("\n", ReportDiff.between(before, "a", after, "b", false).plain());
        String loud = String.join("\n", ReportDiff.between(before, "a", after, "b", true).plain());

        assertTrue(quiet.contains("Nothing changed, apart from 1 field"), quiet);
        assertTrue(loud.contains("~ ping"), loud);
    }

    @Test
    void volatileFieldsAreTheOnesThatAlwaysMove() {
        assertTrue(ReportDiff.isVolatile("Connection", "ping"));
        assertTrue(ReportDiff.isVolatile("Overview", "first played"));
        assertTrue(ReportDiff.isVolatile("Statistics", "custom.play_time"));
        // A statistic that only moves when the player does something is news.
        assertFalse(ReportDiff.isVolatile("Statistics", "custom.damage_dealt"));
        assertFalse(ReportDiff.isVolatile("Vitals", "health"));
    }

    private static Report dump(String line) {
        return new Report().text(line);
    }

    private static File sidecarOf(File text) {
        String name = text.getName();
        return new File(text.getParentFile(), name.substring(0, name.lastIndexOf('.')) + ".json");
    }
}
