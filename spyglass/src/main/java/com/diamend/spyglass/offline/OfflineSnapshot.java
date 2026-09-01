package com.diamend.spyglass.offline;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtReader;

/**
 * Everything Spyglass can know about a player who is not logged in: their save
 * file, parsed, plus the two JSON files beside it, read only if asked for.
 *
 * <p>Loading touches the disk, so this is built off the main thread and then
 * rendered.
 */
public final class OfflineSnapshot {

    private final PlayerFiles files;
    private final OfflinePlayer player;
    private final UUID uuid;
    private final String name;
    private final File dataFile;
    private final NbtCompound data;
    private final String error;

    private StatsFile stats;
    private AdvancementsFile advancements;

    private OfflineSnapshot(PlayerFiles files, OfflinePlayer player, UUID uuid, String name,
                            File dataFile, NbtCompound data, String error) {
        this.files = files;
        this.player = player;
        this.uuid = uuid;
        this.name = name;
        this.dataFile = dataFile;
        this.data = data;
        this.error = error;
    }

    /** Reads the save file. Never throws: a failure becomes {@link #error()}. */
    public static OfflineSnapshot load(PlayerFiles files, OfflinePlayer player, UUID uuid, String name) {
        File file = files.playerData(uuid);
        if (file == null) {
            return new OfflineSnapshot(files, player, uuid, name, null, null,
                    "the server has no world folder to read player data from");
        }
        if (!file.isFile()) {
            return new OfflineSnapshot(files, player, uuid, name, file, null,
                    "no save file — " + file.getName() + " does not exist");
        }
        try {
            NbtCompound data = NbtReader.readFile(file.toPath());
            return new OfflineSnapshot(files, player, uuid, name, file, data, null);
        } catch (IOException | RuntimeException ex) {
            return new OfflineSnapshot(files, player, uuid, name, file, null,
                    "could not read " + file.getName() + ": " + ex.getMessage());
        }
    }

    public OfflinePlayer player() {
        return player;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** The parsed save, or null when there was nothing to parse. */
    public NbtCompound data() {
        return data;
    }

    public boolean hasData() {
        return data != null;
    }

    /** Why there is no data, when there is none. */
    public String error() {
        return error;
    }

    public File dataFile() {
        return dataFile;
    }

    /** When the save was last written, or 0. */
    public long savedAt() {
        return dataFile == null ? 0L : dataFile.lastModified();
    }

    /** The statistics file, read on first use. */
    public StatsFile stats() {
        if (stats == null) {
            File file = files.stats(uuid);
            try {
                stats = StatsFile.read(file == null ? null : file.toPath());
            } catch (IOException | RuntimeException ex) {
                stats = StatsFile.empty();
            }
        }
        return stats;
    }

    /** The advancements file, read on first use. */
    public AdvancementsFile advancements() {
        if (advancements == null) {
            File file = files.advancements(uuid);
            try {
                advancements = AdvancementsFile.read(file == null ? null : file.toPath());
            } catch (IOException | RuntimeException ex) {
                advancements = AdvancementsFile.empty();
            }
        }
        return advancements;
    }
}
