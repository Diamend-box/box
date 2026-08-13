package com.diamend.spyglass.offline;

import java.io.File;
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

    public File playerData(UUID uuid) {
        return child("playerdata", uuid + ".dat");
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
