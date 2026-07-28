package com.diamend.darksea.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * The plugin's own warnings and errors, kept in memory so they can be read
 * back in game.
 *
 * <p>Every config loader in this plugin warns-and-skips rather than throwing:
 * a bad vein, an unknown item id, a malformed shop line gets logged and
 * dropped. That is the right behaviour, but it means the difference between
 * "shops.yml loaded" and "shops.yml loaded, minus four lines you meant to
 * have" only exists in the console — which is exactly what you do not have
 * while standing in the world testing it.
 *
 * <p>So this attaches to the plugin logger and keeps the last {@link #CAPACITY}
 * records at WARNING or above. Bounded on purpose: a listener that warns every
 * tick must not turn into a memory leak.
 *
 * <p>JDK logging only — no Bukkit — so the buffer semantics are provable
 * without a server.
 */
public final class LogTail extends Handler {

    /** How many records to keep. Enough to see a config load's worth. */
    public static final int CAPACITY = 40;

    /** One captured record, already flattened to text. */
    public record Entry(Level level, String message) {

        /** SEVERE and above; the ones worth colouring red in game. */
        public boolean severe() {
            return level.intValue() >= Level.SEVERE.intValue();
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    /** Total seen, including the ones evicted — the count is the honest one. */
    private int warnings;
    private int severes;

    public LogTail() {
        this(CAPACITY);
    }

    public LogTail(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getLevel().intValue() < Level.WARNING.intValue()) {
            return;
        }
        String message = record.getMessage();
        if (message == null) {
            message = record.getThrown() == null ? "(no message)"
                    : String.valueOf(record.getThrown());
        }
        Entry entry = new Entry(record.getLevel(), message);
        synchronized (this) {
            if (entry.severe()) {
                severes++;
            } else {
                warnings++;
            }
            entries.addLast(entry);
            while (entries.size() > capacity) {
                entries.removeFirst();
            }
        }
    }

    /** The retained records, oldest first. Never longer than the capacity. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** The most recent {@code limit} records, oldest first. */
    public synchronized List<Entry> tail(int limit) {
        List<Entry> all = new ArrayList<>(entries);
        if (limit >= all.size()) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - Math.max(0, limit), all.size()));
    }

    /** Records seen at exactly WARNING, including any since evicted. */
    public synchronized int warningCount() {
        return warnings;
    }

    /** Records seen at SEVERE or above, including any since evicted. */
    public synchronized int severeCount() {
        return severes;
    }

    public synchronized int totalCount() {
        return warnings + severes;
    }

    /** True when the plugin has logged nothing above INFO. */
    public synchronized boolean clean() {
        return totalCount() == 0;
    }

    /** Drops the buffer and the counters — {@code /ds reload} starts fresh. */
    public synchronized void reset() {
        entries.clear();
        warnings = 0;
        severes = 0;
    }

    @Override
    public void flush() {
        // Nothing buffered downstream; records are stored on publish.
    }

    @Override
    public void close() {
        reset();
    }
}
