package com.diamend.spyglass.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.diamend.spyglass.util.Fmt;

/**
 * Writes a whole report to a file.
 *
 * <p>A console scrolls, and a full report on an active player is thousands of
 * lines. {@code /spy dump} puts the lot somewhere you can open in an editor,
 * keep, and compare against the next one.
 */
public final class DumpWriter {

    private final File folder;
    private final int keep;

    public DumpWriter(File folder, int keep) {
        this.folder = folder;
        this.keep = keep;
    }

    /**
     * Writes the lines to {@code <name>-<timestamp>.txt} and prunes old dumps.
     *
     * @return the file written
     */
    public File write(String playerName, List<String> lines) throws IOException {
        Files.createDirectories(folder.toPath());
        Path path = free(safeName(playerName) + "-" + Fmt.fileStamp(System.currentTimeMillis()));
        Files.write(path, lines, StandardCharsets.UTF_8);
        prune();
        return path.toFile();
    }

    /**
     * The first name not already taken. Two dumps in the same second would
     * otherwise land on the same file and the first would be lost.
     */
    private Path free(String base) {
        Path path = folder.toPath().resolve(base + ".txt");
        for (int suffix = 2; Files.exists(path) && suffix < 1000; suffix++) {
            path = folder.toPath().resolve(base + "-" + suffix + ".txt");
        }
        return path;
    }

    /** Deletes the oldest dumps beyond the keep count. 0 keeps everything. */
    private void prune() {
        if (keep <= 0) {
            return;
        }
        File[] existing = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (existing == null || existing.length <= keep) {
            return;
        }
        List<File> files = new ArrayList<>(Arrays.asList(existing));
        // Name breaks the tie: dumps written in the same millisecond still
        // prune oldest-first, because the name carries the time too.
        files.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < files.size() - keep; i++) {
            // A dump we cannot delete is not worth failing the command over.
            files.get(i).delete();
        }
    }

    /** Player names are already tame, but a UUID target or a fork's name might not be. */
    static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "player";
        }
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        String cleaned = out.toString();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
