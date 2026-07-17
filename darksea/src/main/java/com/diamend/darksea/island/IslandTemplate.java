package com.diamend.darksea.island;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A schematic on disk plus its metadata. An optional YAML sidecar next to
 * the schematic (same basename, .yml) overrides defaults:
 *
 * <pre>
 * weight: 3        # selection weight within its tier pool (default 1)
 * paste-y: 60      # clipboard-origin Y for this template (default generation.paste-y)
 * </pre>
 */
public record IslandTemplate(String name, int tier, File file, int weight, Integer pasteY) {

    /** Scans schematics/tier1..tierN for *.schem / *.schematic templates. */
    public static Map<Integer, List<IslandTemplate>> loadAll(File schematicsRoot, int maxTier, Logger log) {
        Map<Integer, List<IslandTemplate>> byTier = new HashMap<>();
        for (int tier = 1; tier <= maxTier; tier++) {
            List<IslandTemplate> templates = loadDirectory(new File(schematicsRoot, "tier" + tier), tier, log);
            if (!templates.isEmpty()) {
                byTier.put(tier, templates);
            }
        }
        return byTier;
    }

    public static List<IslandTemplate> loadDirectory(File dir, int tier, Logger log) {
        List<IslandTemplate> templates = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".schem") || n.endsWith(".schematic"));
        if (files == null) {
            return templates;
        }
        for (File file : files) {
            String base = file.getName().replaceFirst("\\.(schem|schematic)$", "");
            int weight = 1;
            Integer pasteY = null;
            File sidecar = new File(dir, base + ".yml");
            if (sidecar.exists()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sidecar);
                weight = Math.max(1, yaml.getInt("weight", 1));
                if (yaml.contains("paste-y")) {
                    pasteY = yaml.getInt("paste-y");
                }
            }
            templates.add(new IslandTemplate(base, tier, file, weight, pasteY));
        }
        templates.sort((a, b) -> a.name().compareTo(b.name()));
        if (!templates.isEmpty()) {
            log.info("Loaded " + templates.size() + " island template(s) for tier " + tier);
        }
        return templates;
    }
}
