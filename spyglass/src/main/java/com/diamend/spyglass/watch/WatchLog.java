package com.diamend.spyglass.watch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

/**
 * Keeps a watch after the console has scrolled away.
 *
 * <p>A watch left running overnight is only useful if you can read it in the
 * morning, and a terminal buffer is not where that lives. With {@code watch.log}
 * on, every line the watchers were sent is also appended to
 * {@code plugins/Spyglass/logs/&lt;player&gt;.log}.
 *
 * <p>Watch events arrive on whatever thread caused them, several times a second
 * during a fight, so nothing is written where it happens: lines go on a queue and
 * a background task drains it once a second, one open-and-append per player per
 * pass. That keeps the disk off the server tick and turns a burst of two hundred
 * lines into a single write.
 */
public final class WatchLog {

    /** Rotate rather than let one file grow until the disk notices. */
    private static final long MAX_BYTES = 16L * 1024L * 1024L;

    /** A queue this deep means the disk is not keeping up; drop the rest. */
    private static final int MAX_QUEUED = 20_000;

    private static final long FLUSH_TICKS = 20L;

    private final Plugin plugin;
    private final File folder;
    private final Queue<Line> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    private volatile BukkitTask task;

    public WatchLog(Plugin plugin, File folder) {
        this.plugin = plugin;
        this.folder = folder;
    }

    private record Line(String player, String text) {
    }

    /**
     * Queues one line. Cheap enough to call from an event handler: it appends to
     * a queue and returns.
     */
    public void append(String player, long when, String what, String detail) {
        if (player == null || player.isBlank()) {
            return;
        }
        if (queued.get() >= MAX_QUEUED) {
            dropped.incrementAndGet();
            return;
        }
        queued.incrementAndGet();
        pending.add(new Line(player, Fmt.stamp(when) + "  " + what + "  "
                + (detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' '))));
        start();
    }

    /** Writes everything queued and stops the background task. */
    public void close() {
        BukkitTask running = task;
        task = null;
        if (running != null) {
            Safe.run(running::cancel);
        }
        flush();
    }

    /** Starts the drain task the first time there is anything to drain. */
    private void start() {
        if (task != null || !plugin.isEnabled()) {
            return;
        }
        synchronized (this) {
            if (task != null) {
                return;
            }
            task = Safe.call(() -> plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flush, FLUSH_TICKS, FLUSH_TICKS), null);
        }
        if (task == null) {
            // No scheduler to be had (a disabling plugin, a test harness): write
            // it here rather than lose it.
            flush();
        }
    }

    /** Drains the queue into one append per player. */
    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<String, List<String>> batches = new LinkedHashMap<>();
        Line line;
        while ((line = pending.poll()) != null) {
            queued.decrementAndGet();
            batches.computeIfAbsent(line.player(), key -> new ArrayList<>()).add(line.text());
        }
        int lost = dropped.getAndSet(0);
        for (Map.Entry<String, List<String>> entry : batches.entrySet()) {
            List<String> lines = entry.getValue();
            if (lost > 0) {
                lines.add(Fmt.stamp(System.currentTimeMillis()) + "  spyglass  "
                        + lost + " line(s) dropped: the log queue filled up");
                lost = 0;
            }
            write(entry.getKey(), lines);
        }
    }

    private void write(String player, List<String> lines) {
        try {
            Files.createDirectories(folder.toPath());
            Path path = folder.toPath().resolve(safeName(player) + ".log");
            rotate(path);
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ex) {
            // A watch is a convenience; a log we cannot write is not worth
            // taking the watch down over. Say so once per failed batch.
            plugin.getLogger().warning("Spyglass could not write the watch log for "
                    + player + ": " + ex);
        }
    }

    private void rotate(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) < MAX_BYTES) {
            return;
        }
        String name = path.getFileName().toString();
        String base = name.endsWith(".log") ? name.substring(0, name.length() - 4) : name;
        Files.move(path, path.resolveSibling(
                base + "-" + Fmt.fileStamp(System.currentTimeMillis()) + ".log"));
    }

    /** The same taming a dump filename gets: this ends up as a path. */
    static String safeName(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        String cleaned = out.toString();
        if (cleaned.isBlank()) {
            return "player";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
