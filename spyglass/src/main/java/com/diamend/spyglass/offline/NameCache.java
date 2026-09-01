package com.diamend.spyglass.offline;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Who the UUIDs on disk belong to.
 *
 * <p>{@code playerdata} is named by UUID, so a search across every save can
 * report "found in 069a79f4-…" and nothing more useful than that. The server
 * already keeps the answer in {@code usercache.json} beside the world folder,
 * which is a plain list of {@code {name, uuid}} — read it and the results have
 * names on them, and tab-completion can offer people who are not online.
 *
 * <p>Deliberately not Bukkit's {@code getOfflinePlayer(String)}: that will
 * invent a profile for a name nobody has ever used, and may block on a web
 * request to Mojang while it does. The cache file is the truth about who has
 * actually played here.
 *
 * <p>Re-read when the file's timestamp or size changes, which the server does
 * whenever somebody joins.
 */
public final class NameCache {

    /** A cache this large is a server with bigger problems than tab completion. */
    private static final int MAX_ENTRIES = 20_000;

    private final PlayerFiles files;

    private long stamp;
    private long size;
    private Map<UUID, String> byUuid = Map.of();
    private Map<String, UUID> byName = Map.of();
    private List<String> names = List.of();

    public NameCache(PlayerFiles files) {
        this.files = files;
    }

    /**
     * The name for a player id, or null when the server has never cached one.
     *
     * <p>Answers from what was last read and never touches the disk, so it is
     * safe to ask during tab completion. {@link #refresh()} is what reads.
     */
    public synchronized String name(UUID uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    /** The player id behind a name, ignoring case; null when it is not cached. */
    public synchronized UUID uuid(String name) {
        return name == null ? null : byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Every cached name, in the order the file lists them (most recent first). */
    public synchronized List<String> names() {
        return names;
    }

    /** Drops what we have, so the next {@link #refresh()} re-reads the file. */
    public synchronized void clear() {
        stamp = 0;
        size = 0;
        byUuid = Map.of();
        byName = Map.of();
        names = List.of();
    }

    /**
     * Re-reads {@code usercache.json} when it has changed. This is the disk
     * half, so call it off the main thread.
     */
    public synchronized void refresh() {
        File file = cacheFile();
        if (file == null || !file.isFile()) {
            return;
        }
        long modified = file.lastModified();
        long length = file.length();
        if (modified == stamp && length == size) {
            return;
        }
        stamp = modified;
        size = length;
        try {
            read(file);
        } catch (IOException | RuntimeException ex) {
            // A half-written cache is the server's business, not ours: keep
            // whatever we had and try again next time it changes.
            byUuid = Map.of();
            byName = Map.of();
            names = List.of();
        }
    }

    private void read(File file) throws IOException {
        Map<UUID, String> uuids = new LinkedHashMap<>();
        Map<String, UUID> lookup = new LinkedHashMap<>();
        List<String> found = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                throw new IOException("usercache.json is not a list");
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject() || uuids.size() >= MAX_ENTRIES) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String name = string(entry, "name");
                UUID uuid = uuid(string(entry, "uuid"));
                if (name == null || name.isBlank() || uuid == null) {
                    continue;
                }
                if (uuids.put(uuid, name) == null) {
                    found.add(name);
                }
                // A name can be reused after a rename; the file lists the most
                // recent entry first, so the first one to claim it wins.
                lookup.putIfAbsent(name.toLowerCase(Locale.ROOT), uuid);
            }
        }
        byUuid = Map.copyOf(uuids);
        byName = Map.copyOf(lookup);
        names = List.copyOf(found);
    }

    private File cacheFile() {
        File server = files.serverFolder();
        return server == null ? null : new File(server, "usercache.json");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static UUID uuid(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
