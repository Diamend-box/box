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
 */
public final class SpyglassConfig {

    private final int pageSize;
    private final boolean maskIp;
    private final boolean saveBeforeNbt;
    private final boolean logUsage;
    private final String dumpFolder;
    private final int dumpKeep;
    private final Set<WatchCategory> defaultCategories;
    private final int movementSampleSeconds;
    private final int maxLinesPerSecond;
    private final List<String> autoWatch;

    private SpyglassConfig(int pageSize, boolean maskIp, boolean saveBeforeNbt, boolean logUsage,
                           String dumpFolder, int dumpKeep, Set<WatchCategory> defaultCategories,
                           int movementSampleSeconds, int maxLinesPerSecond, List<String> autoWatch) {
        this.pageSize = pageSize;
        this.maskIp = maskIp;
        this.saveBeforeNbt = saveBeforeNbt;
        this.logUsage = logUsage;
        this.dumpFolder = dumpFolder;
        this.dumpKeep = dumpKeep;
        this.defaultCategories = defaultCategories;
        this.movementSampleSeconds = movementSampleSeconds;
        this.maxLinesPerSecond = maxLinesPerSecond;
        this.autoWatch = autoWatch;
    }

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

        List<String> categoryNames = config.getStringList("watch.default-categories");
        Set<WatchCategory> categories = WatchCategory.parse(categoryNames);
        if (categories.isEmpty()) {
            categories = EnumSet.complementOf(EnumSet.of(WatchCategory.MOVEMENT));
        }

        int sample = clamp(config.getInt("watch.movement-sample-seconds", 3), 1, 3600);
        int maxLines = clamp(config.getInt("watch.max-lines-per-second", 20), 0, 1000);

        List<String> auto = new ArrayList<>();
        for (String name : config.getStringList("watch.auto")) {
            if (name != null && !name.isBlank()) {
                auto.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }

        return new SpyglassConfig(pageSize, maskIp, saveBeforeNbt, logUsage, dumpFolder, dumpKeep,
                categories, sample, maxLines, List.copyOf(auto));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public int pageSize() {
        return pageSize;
    }

    public boolean maskIp() {
        return maskIp;
    }

    public boolean saveBeforeNbt() {
        return saveBeforeNbt;
    }

    public boolean logUsage() {
        return logUsage;
    }

    public String dumpFolder() {
        return dumpFolder;
    }

    public int dumpKeep() {
        return dumpKeep;
    }

    public Set<WatchCategory> defaultCategories() {
        return Set.copyOf(defaultCategories);
    }

    public int movementSampleSeconds() {
        return movementSampleSeconds;
    }

    public int maxLinesPerSecond() {
        return maxLinesPerSecond;
    }

    /** Lower-cased names the console follows automatically. */
    public List<String> autoWatch() {
        return autoWatch;
    }

    public boolean isAutoWatched(String name) {
        return name != null && autoWatch.contains(name.toLowerCase(Locale.ROOT));
    }
}
