package com.diamend.spyglass.report;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import com.diamend.spyglass.util.Fmt;

/**
 * A dump as data rather than as a page.
 *
 * <p>Every dump is written twice: once as the text a person reads, and once as
 * this. The text version cannot be read back — by the time a field has been
 * padded into a column and a raw NBT line has been indented, there is no way to
 * tell one from the other again — and a diff has to know exactly which value
 * used to sit under which label. So the structure is kept alongside the page
 * instead of being reverse-engineered out of it.
 *
 * <p>It is also the thing to point other tooling at: a stable, boring
 * {@code {"entries": [{"section", "kind", "label", "value"}]}} that does not
 * change shape when the console formatting does.
 */
public record DumpFile(String player, String uuid, long generated, List<Entry> entries) {

    /**
     * One line worth keeping.
     *
     * @param section the heading it appeared under, e.g. {@code Inventory}
     * @param kind    {@code field}, {@code text} or {@code note}
     * @param label   the field name, or null for a line that is all value
     */
    public record Entry(String section, String kind, String label, String value) {

        /** How a diff refers to this line. */
        public String describe() {
            return label == null || label.isBlank() ? value : label;
        }
    }

    /** The current format. Bumped if the shape ever changes. */
    public static final int VERSION = 1;

    /** Flattens a built report, keeping the headings as section names. */
    public static DumpFile of(String player, String uuid, Report report) {
        List<Entry> entries = new ArrayList<>();
        String section = "";
        for (Report.Line line : report.lines()) {
            switch (line.kind()) {
                case HEADER -> section = line.value();
                case FIELD -> entries.add(new Entry(section, "field", line.label(), line.value()));
                case TEXT -> entries.add(new Entry(section, "text", null, line.value()));
                case NOTE -> entries.add(new Entry(section, "note", null, line.value()));
                default -> {
                    // A title or a blank line is layout, not content.
                }
            }
        }
        return new DumpFile(player, uuid, System.currentTimeMillis(), entries);
    }

    /** Only the lines a diff compares: the notes are prose about them. */
    public List<Entry> comparable() {
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (!"note".equals(entry.kind())) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Written a field at a time rather than through a reflective mapper, so it
     * cannot break on a server whose Gson is a different build to ours.
     */
    public void write(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
                JsonWriter json = new JsonWriter(out)) {
            json.setIndent("  ");
            json.beginObject();
            json.name("version").value(VERSION);
            json.name("player").value(player);
            json.name("uuid").value(uuid);
            json.name("generated").value(generated);
            json.name("generatedAt").value(Fmt.stamp(generated));
            json.name("entries").beginArray();
            for (Entry entry : entries) {
                json.beginObject();
                json.name("section").value(entry.section());
                json.name("kind").value(entry.kind());
                if (entry.label() != null) {
                    json.name("label").value(entry.label());
                }
                json.name("value").value(entry.value());
                json.endObject();
            }
            json.endArray();
            json.endObject();
        }
    }

    /** Reads one back. Throws when the file is not a dump we can compare. */
    public static DumpFile read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                throw new IOException("not a dump file");
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement list = object.get("entries");
            if (list == null || !list.isJsonArray()) {
                throw new IOException("no entries in " + path.getFileName());
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : list.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                entries.add(new Entry(
                        string(entry, "section", ""),
                        string(entry, "kind", "text").toLowerCase(Locale.ROOT),
                        entry.has("label") ? string(entry, "label", null) : null,
                        string(entry, "value", "")));
            }
            return new DumpFile(
                    string(object, "player", "?"),
                    string(object, "uuid", ""),
                    object.has("generated") ? object.get("generated").getAsLong() : 0L,
                    entries);
        } catch (RuntimeException ex) {
            // Gson throws unchecked; a bad file is an IO problem to the caller.
            throw new IOException("could not read " + path.getFileName() + ": " + ex, ex);
        }
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
}
