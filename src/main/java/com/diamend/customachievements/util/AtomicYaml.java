package com.diamend.customachievements.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Writes a {@link YamlConfiguration} to disk atomically. The data is first
 * written to a sibling temporary file and then moved into place with an atomic
 * rename, so a crash, kill, or full disk part-way through a write can't leave a
 * half-written (corrupt) file where a complete one used to be — the previous
 * file survives untouched until the replacement is fully on disk.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    /**
     * Saves {@code config} to {@code file} via a temp-file-and-rename so the
     * destination is only ever replaced by a fully written copy.
     */
    public static void save(YamlConfiguration config, File file) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create directory " + dir);
        }
        // Temp file lives in the same directory so the rename stays on one
        // filesystem (a cross-device move can't be atomic).
        File temp = File.createTempFile(file.getName() + "-", ".tmp", dir);
        try {
            config.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                // Rare filesystems can't do an atomic rename; fall back to a
                // plain replace, still safer than writing in place.
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }
}
