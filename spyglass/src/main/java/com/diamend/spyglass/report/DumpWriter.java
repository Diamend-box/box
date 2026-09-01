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
import java.util.Locale;

import com.diamend.spyglass.util.Fmt;

/**
 * Writes a whole report to a file — twice.
 *
 * <p>A console scrolls, and a full report on an active player is thousands of
 * lines. {@code /spy dump} puts the lot somewhere you can open in an editor,
 * keep, and compare against the next one.
 *
 * <p>Every dump lands as a pair: {@code <name>-<stamp>.txt} for reading and
 * {@code <name>-<stamp>.json} for {@code /spy diff} and for anything else that
 * wants the numbers without having to parse a column layout. The two are
 * created and pruned together, so a {@code .json} always has its page beside it.
 */
public final class DumpWriter {

    private final File folder;
    private final int keep;

    public DumpWriter(File folder, int keep) {
        this.folder = folder;
        this.keep = keep;
    }

    public File folder() {
        return folder;
    }

    /**
     * Writes the pair and prunes old dumps.
     *
     * @param uuid  the player's id, so a diff can tell it is comparing one
     *              person against themselves
     * @return the {@code .txt} file, the one worth telling somebody about
     */
    public File write(String playerName, String uuid, Report report) throws IOException {
        Files.createDirectories(folder.toPath());
        String base = free(safeName(playerName) + "-" + Fmt.fileStamp(System.currentTimeMillis()));
        Path text = folder.toPath().resolve(base + ".txt");
        Files.write(text, report.plain(), StandardCharsets.UTF_8);
        DumpFile.of(playerName, uuid, report).write(folder.toPath().resolve(base + ".json"));
        prune();
        return text.toFile();
    }

    /** The dumps on disk, newest first; {@code player} null for all of them. */
    public List<File> list(String player) {
        String prefix = player == null ? null : safeName(player).toLowerCase(Locale.ROOT) + "-";
        File[] found = folder.listFiles((dir, name) -> name.endsWith(".json")
                && (prefix == null || name.toLowerCase(Locale.ROOT).startsWith(prefix)));
        if (found == null) {
            return List.of();
        }
        List<File> files = new ArrayList<>(Arrays.asList(found));
        files.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName).reversed());
        return files;
    }

    /** The most recent dump for a player, or null when they have none. */
    public File latest(String player) {
        List<File> files = list(player);
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * One named dump from the folder.
     *
     * <p>Only a bare filename is accepted, and only from inside the dumps
     * folder: the name comes off a command line, and {@code ../../server.jar} is
     * not a dump.
     */
    public File resolve(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.contains("..")) {
            return null;
        }
        String wanted = name.endsWith(".json") || name.endsWith(".txt")
                ? name.substring(0, name.lastIndexOf('.')) : name;
        File file = new File(folder, wanted + ".json");
        return file.isFile() ? file : null;
    }

    /**
     * The first name not already taken. Two dumps in the same millisecond would
     * otherwise land on the same file and the first would be lost.
     */
    private String free(String base) {
        String candidate = base;
        for (int suffix = 2; taken(candidate) && suffix < 1000; suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private boolean taken(String base) {
        return Files.exists(folder.toPath().resolve(base + ".txt"))
                || Files.exists(folder.toPath().resolve(base + ".json"));
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
            File text = files.get(i);
            // A dump we cannot delete is not worth failing the command over.
            text.delete();
            sidecar(text).delete();
        }
    }

    /** The {@code .json} that belongs with a {@code .txt}. */
    static File sidecar(File text) {
        String name = text.getName();
        int dot = name.lastIndexOf('.');
        return new File(text.getParentFile(), (dot < 0 ? name : name.substring(0, dot)) + ".json");
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
