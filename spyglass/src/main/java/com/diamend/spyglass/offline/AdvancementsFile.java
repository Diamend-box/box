package com.diamend.spyglass.offline;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads {@code advancements/&lt;uuid&gt;.json} — what a player has earned, and
 * how far into the ones they have not.
 *
 * <pre>
 * {"minecraft:story/root":{"criteria":{"crafting_table":"2026-08-13 04:35:12 +0000"},
 *                          "done":true},
 *  "DataVersion":4189}
 * </pre>
 */
public final class AdvancementsFile {

    /**
     * One advancement's state.
     *
     * @param key      e.g. {@code minecraft:story/mine_stone}
     * @param done     whether every criterion is met
     * @param criteria how many criteria have been met so far
     * @param earned   when the most recent criterion was met, as written; may be null
     */
    public record Entry(String key, boolean done, int criteria, String earned) {
    }

    private final List<Entry> entries;
    private final int dataVersion;

    private AdvancementsFile(List<Entry> entries, int dataVersion) {
        this.entries = entries;
        this.dataVersion = dataVersion;
    }

    public static AdvancementsFile empty() {
        return new AdvancementsFile(List.of(), 0);
    }

    public static AdvancementsFile read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static AdvancementsFile parse(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || !root.isJsonObject()) {
            return empty();
        }
        List<Entry> entries = new ArrayList<>();
        int version = 0;
        for (Map.Entry<String, JsonElement> field : root.getAsJsonObject().entrySet()) {
            if (field.getKey().equals("DataVersion")) {
                try {
                    version = field.getValue().getAsInt();
                } catch (RuntimeException ignored) {
                    // Leave it at zero.
                }
                continue;
            }
            if (!field.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = field.getValue().getAsJsonObject();
            boolean done = value.has("done") && value.get("done").getAsBoolean();
            int criteria = 0;
            String earned = null;
            if (value.has("criteria") && value.get("criteria").isJsonObject()) {
                JsonObject criteriaObject = value.getAsJsonObject("criteria");
                criteria = criteriaObject.size();
                for (Map.Entry<String, JsonElement> one : criteriaObject.entrySet()) {
                    try {
                        earned = one.getValue().getAsString();
                    } catch (RuntimeException ignored) {
                        // Not a timestamp; leave whatever we had.
                    }
                }
            }
            entries.add(new Entry(field.getKey(), done, criteria, earned));
        }
        return new AdvancementsFile(entries, version);
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int dataVersion() {
        return dataVersion;
    }

    /** Completed advancements, not counting the automatic recipe unlocks. */
    public long doneCount() {
        return entries.stream().filter(entry -> entry.done() && !isRecipe(entry.key())).count();
    }

    /** Started but unfinished, not counting recipes. */
    public long startedCount() {
        return entries.stream()
                .filter(entry -> !entry.done() && entry.criteria() > 0 && !isRecipe(entry.key()))
                .count();
    }

    public long recipeCount() {
        return entries.stream().filter(entry -> isRecipe(entry.key())).count();
    }

    public static boolean isRecipe(String key) {
        return key != null && key.contains(":recipes/");
    }
}
