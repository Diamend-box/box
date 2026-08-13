package com.diamend.spyglass.offline;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.diamend.spyglass.util.Fmt;

/**
 * Reads {@code stats/&lt;uuid&gt;.json}, the file the server writes every
 * statistic to.
 *
 * <p>Bukkit can answer statistics for an offline player, but it reloads this
 * file for each question — and a full report asks thousands. Reading the file
 * once is both faster and more honest: what comes back is exactly the set of
 * statistics that player has a number for, under vanilla's own names.
 *
 * <pre>
 * {"stats":{"minecraft:mined":{"minecraft:stone":482}},"DataVersion":4189}
 * </pre>
 */
public final class StatsFile {

    private final Map<String, Long> values;
    private final int dataVersion;

    private StatsFile(Map<String, Long> values, int dataVersion) {
        this.values = values;
        this.dataVersion = dataVersion;
    }

    public static StatsFile empty() {
        return new StatsFile(Map.of(), 0);
    }

    /** Parses the file; an unreadable or absent file reads as empty. */
    public static StatsFile read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static StatsFile parse(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || !root.isJsonObject()) {
            return empty();
        }
        JsonObject object = root.getAsJsonObject();
        Map<String, Long> values = new TreeMap<>();
        int version = 0;
        if (object.has("DataVersion") && object.get("DataVersion").isJsonPrimitive()) {
            version = object.get("DataVersion").getAsInt();
        }
        JsonElement stats = object.get("stats");
        if (stats != null && stats.isJsonObject()) {
            for (Map.Entry<String, JsonElement> category : stats.getAsJsonObject().entrySet()) {
                if (!category.getValue().isJsonObject()) {
                    continue;
                }
                String prefix = Fmt.shortKey(category.getKey());
                for (Map.Entry<String, JsonElement> entry : category.getValue().getAsJsonObject().entrySet()) {
                    try {
                        values.put(prefix + "." + Fmt.shortKey(entry.getKey()),
                                entry.getValue().getAsLong());
                    } catch (RuntimeException ignored) {
                        // A value that isn't a number is not worth failing over.
                    }
                }
            }
        }
        return new StatsFile(new LinkedHashMap<>(values), version);
    }

    /** Statistic name (e.g. {@code mined.stone}) to value, sorted by name. */
    public Map<String, Long> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** The Minecraft data version the file was written by; 0 when unknown. */
    public int dataVersion() {
        return dataVersion;
    }
}
