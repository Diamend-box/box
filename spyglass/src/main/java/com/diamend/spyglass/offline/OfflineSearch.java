package com.diamend.spyglass.offline;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtList;
import com.diamend.spyglass.nbt.NbtReader;

/**
 * Looks for an item in every save on the disk.
 *
 * <p>{@code /spy find} without this only sees people who happen to be connected,
 * which makes "nobody has a beacon" a claim about the last five minutes rather
 * than about the server. Reading the saves answers it properly — at the cost of
 * touching every file in {@code playerdata}, which on a long-running server is
 * thousands of them.
 *
 * <p>So the scan is bounded three ways. It reads most-recently-played first, so
 * stopping early still covers the players anyone cares about. It stops at a file
 * count and at a time budget, and says which. And what it read is kept as
 * rendered text, keyed by the file's timestamp, so asking a second question
 * about the same saves costs no disk at all — a save only changes when its
 * player logs out.
 */
public final class OfflineSearch {

    /**
     * One stack as it will be printed, with whatever it contains. Strings only:
     * this is what the cache holds, and holding parsed NBT for a thousand
     * players would not be a cache so much as a second copy of the world.
     */
    public record Stack(String where, int slot, String line, List<Stack> inside) {
    }

    /** Somewhere the wanted text turned up. */
    public record Hit(UUID uuid, String name, String where, int slot, String line) {
    }

    /**
     * How a scan ended.
     *
     * @param hits     what was found, in the order the saves were read
     * @param scanned  how many saves were actually read
     * @param total    how many there were
     * @param failed   saves that could not be read at all
     * @param stopped  null when the scan finished, otherwise why it did not
     */
    public record Result(List<Hit> hits, int scanned, int total, int failed, String stopped) {

        public boolean complete() {
            return stopped == null;
        }
    }

    /** Rendered inventories, keyed by save file path. */
    private final Map<String, Cached> cache = new LinkedHashMap<>();

    private final PlayerFiles files;
    private final NameCache names;
    private final int maxCached;

    public OfflineSearch(PlayerFiles files, NameCache names, int maxCached) {
        this.files = files;
        this.names = names;
        this.maxCached = Math.max(16, maxCached);
    }

    /** Forgets everything read so far. */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * Reads saves until it runs out of files, budget or patience.
     *
     * <p>Call this off the main thread: it is disk work, and a lot of it.
     *
     * @param wanted    lower-case text to look for
     * @param maxSaves  how many files to read at most
     * @param maxMillis how long to spend at most
     * @param maxHits   stop once this many stacks have matched
     */
    public Result search(String wanted, int maxSaves, long maxMillis, int maxHits) {
        // We are already off the main thread, so this is the moment to let the
        // name cache catch up — the results are named from it.
        names.refresh();
        List<File> saves = files.allPlayerData();
        List<Hit> hits = new ArrayList<>();
        long deadline = System.currentTimeMillis() + maxMillis;
        int scanned = 0;
        int failed = 0;
        String stopped = null;

        for (File save : saves) {
            if (scanned >= maxSaves) {
                stopped = "read " + scanned + " of " + saves.size()
                        + " saves (find.max-saves)";
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                stopped = "ran out of time after " + scanned + " of " + saves.size()
                        + " saves (find.time-budget)";
                break;
            }
            UUID uuid = PlayerFiles.uuidOf(save);
            if (uuid == null) {
                continue;
            }
            List<Stack> stacks = read(save);
            if (stacks == null) {
                failed++;
                continue;
            }
            scanned++;
            String name = names.name(uuid);
            for (Stack stack : stacks) {
                String trail = trail(stack, wanted, 0);
                if (trail == null) {
                    continue;
                }
                hits.add(new Hit(uuid, name == null ? uuid.toString() : name,
                        stack.where(), stack.slot(), stack.line() + trail));
                if (hits.size() >= maxHits) {
                    stopped = "stopped at " + maxHits + " matches";
                    return new Result(hits, scanned, saves.size(), failed, stopped);
                }
            }
        }
        return new Result(hits, scanned, saves.size(), failed, stopped);
    }

    /** The rendered inventory of one save, from cache when it has not changed. */
    private List<Stack> read(File save) {
        String key = save.getPath();
        long modified = save.lastModified();
        long length = save.length();
        List<Stack> cached = cached(key, modified, length);
        if (cached != null) {
            return cached;
        }
        // Deliberately outside the lock: this is the slow part, and a second
        // search should not queue behind it.
        List<Stack> stacks = new ArrayList<>();
        try {
            NbtCompound root = NbtReader.readFile(save.toPath());
            collect(stacks, root.list("Inventory"), "inventory");
            collect(stacks, root.list("EnderItems"), "enderchest");
        } catch (IOException | RuntimeException ex) {
            // A save being written as we read it is normal, not an error worth
            // stopping a whole scan for. Remember nothing and move on.
            forget(key);
            return null;
        }
        List<Stack> finished = List.copyOf(stacks);
        store(key, modified, length, finished);
        return finished;
    }

    private synchronized List<Stack> cached(String key, long modified, long length) {
        Cached found = cache.get(key);
        return found != null && found.stamp == modified && found.size == length ? found.stacks : null;
    }

    private synchronized void store(String key, long modified, long length, List<Stack> stacks) {
        if (cache.size() >= maxCached && !cache.containsKey(key)) {
            // Insertion-ordered, so the first key is the one read longest ago.
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new Cached(modified, length, stacks));
    }

    private synchronized void forget(String key) {
        cache.remove(key);
    }

    private static void collect(List<Stack> out, NbtList items, String where) {
        if (items == null) {
            return;
        }
        for (NbtCompound item : items.compounds()) {
            if (!NbtItems.isEmpty(item)) {
                out.add(render(item, where, NbtItems.slot(item), 0));
            }
        }
    }

    private static Stack render(NbtCompound item, String where, int slot, int depth) {
        List<Stack> inside = new ArrayList<>();
        if (depth < 4) {
            for (NbtCompound nested : NbtItems.contents(item)) {
                if (!NbtItems.isEmpty(nested)) {
                    inside.add(render(nested, where, slot, depth + 1));
                }
            }
        }
        return new Stack(where, slot, NbtItems.line(item), List.copyOf(inside));
    }

    /** The same trail the live search prints, over the rendered tree. */
    private static String trail(Stack stack, String wanted, int depth) {
        if (stack.line().toLowerCase(Locale.ROOT).contains(wanted)) {
            return "";
        }
        if (depth >= 4) {
            return null;
        }
        for (Stack nested : stack.inside()) {
            String deeper = trail(nested, wanted, depth + 1);
            if (deeper != null) {
                return " > " + nested.line() + deeper;
            }
        }
        return null;
    }

    private record Cached(long stamp, long size, List<Stack> stacks) {
    }
}
