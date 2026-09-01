package com.diamend.spyglass.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

import com.diamend.spyglass.watch.WatchCategory;

/**
 * An immutable snapshot of config.yml, re-read on {@code /spy reload}.
 *
 * <p>Every value has a sane default, so a config that is missing, empty or
 * half-edited still produces a working plugin.
 *
 * @param pageSize              lines per page of console output
 * @param maskIp                hide IP addresses even from the console
 * @param saveBeforeNbt         ask the server to write an online player out
 *                              before reading their save file
 * @param logUsage              log who inspected whom
 * @param dumpFolder            where dumps go, under the plugin folder
 * @param dumpKeep              how many dumps to keep; 0 keeps every one
 * @param findMaxSaves          how many save files one disk-wide search may read
 * @param findSeconds           how long that search may take before it gives up
 * @param defaultCategories     what {@code /spy watch} follows when not told
 * @param movementSampleSeconds how often a moving player is reported
 * @param maxLinesPerSecond     per-watch budget, so a fight cannot outrun the console
 * @param watchLog              also write watch lines to a per-player log file
 * @param autoWatch             lower-cased names the console follows across restarts
 */
public record SpyglassConfig(
        int pageSize,
        boolean maskIp,
        boolean saveBeforeNbt,
        boolean logUsage,
        String dumpFolder,
        int dumpKeep,
        int findMaxSaves,
        int findSeconds,
        Set<WatchCategory> defaultCategories,
        int movementSampleSeconds,
        int maxLinesPerSecond,
        boolean watchLog,
        List<String> autoWatch) {

    public static SpyglassConfig load(FileConfiguration config) {
        int pageSize = clamp(config.getInt("page-size", 30), 5, 500);
        boolean maskIp = config.getBoolean("mask-ip", false);
        boolean saveBeforeNbt = config.getBoolean("save-before-nbt", true);
        boolean logUsage = config.getBoolean("log-usage", false);

        String dumpFolder = config.getString("dumps.folder", "dumps");
        if (dumpFolder == null || dumpFolder.isBlank() || dumpFolder.contains("..")) {
            dumpFolder = "dumps";
        }
        int dumpKeep = Math.max(0, config.getInt("dumps.keep", 50));

        // Reading every save on a long-running server is minutes of disk, so the
        // search is bounded twice: by how many files and by how long.
        int findMaxSaves = clamp(config.getInt("find.max-saves", 500), 1, 100_000);
        int findSeconds = clamp(config.getInt("find.time-budget", 10), 1, 600);

        List<String> categoryNames = config.getStringList("watch.default-categories");
        Set<WatchCategory> categories = WatchCategory.parse(categoryNames);
        if (categories.isEmpty()) {
            categories = EnumSet.complementOf(EnumSet.of(WatchCategory.MOVEMENT));
        }

        int sample = clamp(config.getInt("watch.movement-sample-seconds", 3), 1, 3600);
        int maxLines = clamp(config.getInt("watch.max-lines-per-second", 20), 0, 1000);
        boolean watchLog = config.getBoolean("watch.log", false);

        List<String> auto = new ArrayList<>();
        for (String name : config.getStringList("watch.auto")) {
            if (name != null && !name.isBlank()) {
                auto.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }

        return new SpyglassConfig(pageSize, maskIp, saveBeforeNbt, logUsage, dumpFolder, dumpKeep,
                findMaxSaves, findSeconds, Set.copyOf(categories), sample, maxLines, watchLog,
                List.copyOf(auto));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isAutoWatched(String name) {
        return name != null && autoWatch.contains(name.toLowerCase(Locale.ROOT));
    }
}
