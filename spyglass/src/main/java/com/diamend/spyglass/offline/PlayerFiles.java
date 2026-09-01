package com.diamend.spyglass.offline;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.World;

import com.diamend.spyglass.util.Safe;

/**
 * Finds the three files the server keeps about a player.
 *
 * <p>They all live under the main world's folder, whatever that world is called:
 * {@code playerdata/<uuid>.dat} (everything about the body — inventory, health,
 * position), {@code stats/<uuid>.json} and {@code advancements/<uuid>.json}.
 * Dimensions get their own folders, but player data does not: it is all in the
 * first world.
 */
public final class PlayerFiles {

    private final Server server;
    private final File fixedFolder;

    public PlayerFiles(Server server) {
        this.server = server;
        this.fixedFolder = null;
    }

    /** Reads from a folder given outright, rather than from the running server. */
    public PlayerFiles(File worldFolder) {
        this.server = null;
        this.fixedFolder = worldFolder;
    }

    /** The main world's folder, or null on a server with no worlds loaded. */
    public File worldFolder() {
        if (fixedFolder != null) {
            return fixedFolder;
        }
        return Safe.call(() -> {
            List<World> worlds = server.getWorlds();
            return worlds.isEmpty() ? null : worlds.get(0).getWorldFolder();
        }, null);
    }

    /**
     * The server folder the world sits in — where {@code usercache.json} lives.
     * Null when there is no world to work back from.
     */
    public File serverFolder() {
        File world = worldFolder();
        return world == null ? null : world.getParentFile();
    }

    public File playerData(UUID uuid) {
        return child("playerdata", uuid + ".dat");
    }

    /**
     * Every save on disk, most recently written first.
     *
     * <p>That order is the useful one for a bounded scan: whoever was here
     * yesterday is a better guess than whoever was here in 2019, and a search
     * that has to stop early should stop having read the recent half.
     */
    public List<File> allPlayerData() {
        File world = worldFolder();
        File folder = world == null ? null : new File(world, "playerdata");
        File[] found = folder == null
                ? null
                : folder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (found == null) {
            return List.of();
        }
        List<File> files = new ArrayList<>(Arrays.asList(found));
        files.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName).reversed());
        return files;
    }

    /** The player id a save file is named after, or null when it isn't one. */
    public static UUID uuidOf(File playerData) {
        String name = playerData == null ? "" : playerData.getName();
        if (!name.endsWith(".dat")) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(0, name.length() - 4));
        } catch (IllegalArgumentException ex) {
            // playerdata also holds .dat_old backups and the odd stray file.
            return null;
        }
    }

    public File stats(UUID uuid) {
        return child("stats", uuid + ".json");
    }

    public File advancements(UUID uuid) {
        return child("advancements", uuid + ".json");
    }

    /** True when this server has ever written a save for that player. */
    public boolean hasPlayerData(UUID uuid) {
        File file = playerData(uuid);
        return file != null && file.isFile();
    }

    private File child(String folder, String name) {
        File world = worldFolder();
        return world == null ? null : new File(new File(world, folder), name);
    }
}
