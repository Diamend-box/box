package com.diamend.spyglass.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Statistics;

/**
 * What changed between two dumps.
 *
 * <p>The question a full report can't answer on its own is "what did they do
 * since yesterday?" — the report is thousands of lines and almost all of them
 * are the same lines as last time. This walks two {@link DumpFile}s and prints
 * only the differences: {@code ~} for a field whose value moved, {@code +} and
 * {@code -} for lines that appeared or went away.
 *
 * <p>Some fields move every time by their nature — the clock, the ping, how
 * long this login has lasted — and listing them would bury everything else, so
 * they are counted and hidden unless the caller asks for them.
 */
public final class ReportDiff {

    /** Per section, so one enormous NBT tree cannot drown the rest. */
    private static final int MAX_LINES_PER_SECTION = 40;

    /**
     * Fields that differ between any two dumps whatever the player did.
     *
     * <p>Two sorts end up here. Timestamps are rendered with their age
     * ("3d 4h ago"), so even a date that never moved reads as changed. And the
     * server's own counters — ticks lived, the hurt timer, the entity id a
     * player is given afresh on every login — move on their own.
     *
     * <p>Matched exactly rather than as substrings, because a rule loose enough
     * to catch "age" also hides {@code custom.damage_dealt}, which is one of the
     * things a diff exists to show.
     */
    private static final Set<String> VOLATILE_LABELS = Set.of(
            "generated", "saved", "save written", "written", "read at",
            "ping", "session so far", "this session",
            "first played", "first played (bukkit)", "last played (bukkit)",
            "last login", "last seen", "last death",
            "paper last login", "paper last seen", "entered nether at",
            "ticks lived", "no damage ticks", "fire ticks", "freeze ticks",
            "sleep timer", "hurt time", "death time", "portal cooldown", "entity id");

    /** The section whose labels are statistic names. */
    private static final String STATISTICS = "Statistics";

    private ReportDiff() {
    }

    /**
     * Builds the difference report.
     *
     * @param before     the older dump
     * @param beforeName what to call it in the header, usually its filename
     * @param after      the newer state — normally built just now
     * @param afterName  what to call that, usually {@code "now"}
     * @param all        true to include the fields that always move
     */
    public static Report between(DumpFile before, String beforeName,
            DumpFile after, String afterName, boolean all) {
        Report report = new Report().title(after.player() + " — diff");
        report.field("from", beforeName + "  (" + Fmt.stampWithAge(before.generated()) + ")");
        report.field("to", afterName + "  (" + Fmt.stampWithAge(after.generated()) + ")");
        if (before.uuid() != null && !before.uuid().isBlank()
                && after.uuid() != null && !before.uuid().equalsIgnoreCase(after.uuid())) {
            report.note("Careful: those are two different players ("
                    + before.uuid() + " and " + after.uuid() + ").");
        }

        Sections older = new Sections(before);
        Sections newer = new Sections(after);
        Set<String> sections = new LinkedHashSet<>(newer.order);
        sections.addAll(older.order);

        int changes = 0;
        int hidden = 0;
        for (String section : sections) {
            List<String> lines = new ArrayList<>();
            hidden += fields(lines, section, older.fields(section), newer.fields(section), all);
            texts(lines, older.texts(section), newer.texts(section));
            if (lines.isEmpty()) {
                continue;
            }
            changes += lines.size();
            report.header(section.isBlank() ? "(top)" : section);
            int printed = 0;
            for (String line : lines) {
                if (printed++ >= MAX_LINES_PER_SECTION) {
                    report.note("... and " + (lines.size() - MAX_LINES_PER_SECTION)
                            + " more change(s) in this section");
                    break;
                }
                report.text(line);
            }
        }

        if (changes == 0) {
            report.note(hidden == 0
                    ? "Nothing changed."
                    : "Nothing changed, apart from " + hidden + " field(s) that always do.");
            return report;
        }
        report.blank();
        report.note(changes + " change(s)"
                + (hidden > 0 ? ", plus " + hidden + " that always move (add \"all\" to see them)" : "")
                + ".");
        return report;
    }

    /**
     * Fields are matched by label, so a value that moved reads as one change
     * rather than as a removal and an addition.
     *
     * @return how many differing fields were hidden as always-moving
     */
    private static int fields(List<String> out, String section, Map<String, String> before,
            Map<String, String> after, boolean all) {
        int hidden = 0;
        Set<String> labels = new LinkedHashSet<>(after.keySet());
        labels.addAll(before.keySet());
        for (String label : labels) {
            String was = before.get(label);
            String now = after.get(label);
            if (Objects.equals(was, now)) {
                continue;
            }
            if (!all && isVolatile(section, label)) {
                hidden++;
                continue;
            }
            if (was == null) {
                out.add("+ " + pad(label) + Fmt.clip(now, 90));
            } else if (now == null) {
                out.add("- " + pad(label) + Fmt.clip(was, 90));
            } else {
                out.add("~ " + pad(label) + Fmt.clip(was, 40) + "  ->  " + Fmt.clip(now, 40));
            }
        }
        return hidden;
    }

    /**
     * Lines with no label — inventory rows, NBT, advancement entries — have
     * nothing to match on but themselves, so they are compared as a bag: a line
     * present three times before and once now counts as two removals.
     */
    private static void texts(List<String> out, Map<String, Integer> before, Map<String, Integer> after) {
        Set<String> all = new LinkedHashSet<>(after.keySet());
        all.addAll(before.keySet());
        for (String line : all) {
            int was = before.getOrDefault(line, 0);
            int now = after.getOrDefault(line, 0);
            for (int i = now; i < was; i++) {
                out.add("- " + line.trim());
            }
            for (int i = was; i < now; i++) {
                out.add("+ " + line.trim());
            }
        }
    }

    /** True for a field nobody wants to be told about on every comparison. */
    static boolean isVolatile(String section, String label) {
        String name = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (VOLATILE_LABELS.contains(name)) {
            return true;
        }
        // In the statistics section every label is a vanilla statistic name, and
        // the ones counting ticks are clocks: they move while a player is simply
        // logged in.
        return STATISTICS.equalsIgnoreCase(section) && Statistics.isTicks(name);
    }

    private static String pad(String label) {
        String name = label == null ? "" : label;
        return name.length() >= 24 ? name + "  " : name + " ".repeat(24 - name.length());
    }

    /** One dump, split by heading, with fields and unlabelled lines kept apart. */
    private static final class Sections {

        private final List<String> order = new ArrayList<>();
        private final Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        private final Map<String, Map<String, Integer>> texts = new LinkedHashMap<>();

        Sections(DumpFile dump) {
            for (DumpFile.Entry entry : dump.comparable()) {
                String section = entry.section() == null ? "" : entry.section();
                if (!fields.containsKey(section)) {
                    order.add(section);
                    fields.put(section, new LinkedHashMap<>());
                    texts.put(section, new LinkedHashMap<>());
                }
                if ("field".equals(entry.kind()) && entry.label() != null) {
                    // A label repeated inside one section (it happens in the raw
                    // NBT view) keeps the first value rather than losing both.
                    fields.get(section).putIfAbsent(entry.label(), entry.value());
                } else {
                    texts.get(section).merge(entry.value(), 1, Integer::sum);
                }
            }
        }

        Map<String, String> fields(String section) {
            return fields.getOrDefault(section, Map.of());
        }

        Map<String, Integer> texts(String section) {
            return texts.getOrDefault(section, Map.of());
        }
    }
}
