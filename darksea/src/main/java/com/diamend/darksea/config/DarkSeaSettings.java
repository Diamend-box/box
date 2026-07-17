package com.diamend.darksea.config;

import com.diamend.darksea.zone.Zone;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Immutable snapshot of every configurable value. Reload swaps the whole
 * snapshot atomically; services always read through the plugin's current
 * snapshot, so a reload takes effect on the next tick without restarts
 * (except world shape and task intervals, which are read once at startup).
 */
public record DarkSeaSettings(
        String worldName,
        int seaLevel,
        int seabedBaseY,
        int seabedVariation,
        int centerX,
        int centerZ,
        ExposureSettings exposure,
        List<Zone> zones,
        ArmorSettings armor,
        GenerationSettings generation,
        MobSpawnSettings mobSpawning,
        BoatSettings boat,
        Map<String, String> messages) {

    public record ExposureSettings(int checkIntervalTicks, int effectDurationTicks, int graceOnLoginSeconds) {
    }

    public record ArmorStyle(String displayName, String materialPrefix) {
    }

    public record ArmorSettings(boolean unbreakable, Map<Integer, ArmorStyle> tiers) {
    }

    public record GenerationSettings(int pasteY, double minIslandGap, double ringBorderMargin,
                                     double outerRadius, Map<Integer, Integer> islandsPerRing,
                                     Material chestMarker, Material mobMarker) {
    }

    public record MobSpawnSettings(int scanIntervalTicks, double activationRadius, int perIslandCap,
                                   int globalCap, int abandonCooldownMinutes) {
    }

    public record BoatLevel(String name, double speed, int shield) {
    }

    public record BoatSettings(double speedCapBase, Map<Integer, BoatLevel> levels) {
    }

    /**
     * Parses a configuration. Throws {@link IllegalStateException} with a
     * human-readable reason for unusable configs (no zones, no armor tiers);
     * recoverable oddities (an unknown effect type, a bad material) are
     * logged and skipped.
     */
    public static DarkSeaSettings load(FileConfiguration cfg, Logger log) {
        String worldName = cfg.getString("world.name", "dark_sea");
        int seaLevel = cfg.getInt("world.sea-level", 62);
        int seabedBaseY = cfg.getInt("world.seabed.base-y", 45);
        int seabedVariation = cfg.getInt("world.seabed.variation", 5);
        int centerX = cfg.getInt("center.x", 0);
        int centerZ = cfg.getInt("center.z", 0);

        ExposureSettings exposure = new ExposureSettings(
                Math.max(1, cfg.getInt("exposure.check-interval-ticks", 20)),
                Math.max(20, cfg.getInt("exposure.effect-duration-ticks", 60)),
                Math.max(0, cfg.getInt("exposure.grace-on-login-seconds", 10)));

        List<Zone> zones = loadZones(cfg, log);
        if (zones.isEmpty()) {
            throw new IllegalStateException("config.yml defines no zones — the Dark Sea needs at least one");
        }

        ArmorSettings armor = loadArmor(cfg, log);
        if (armor.tiers().isEmpty()) {
            throw new IllegalStateException("config.yml defines no armor tiers under armor.tiers");
        }

        GenerationSettings generation = loadGeneration(cfg, log);

        MobSpawnSettings mobSpawning = new MobSpawnSettings(
                Math.max(20, cfg.getInt("mob-spawning.scan-interval-ticks", 100)),
                Math.max(8, cfg.getDouble("mob-spawning.activation-radius", 64)),
                Math.max(1, cfg.getInt("mob-spawning.per-island-cap", 6)),
                Math.max(1, cfg.getInt("mob-spawning.global-cap", 120)),
                Math.max(1, cfg.getInt("mob-spawning.abandon-cooldown-minutes", 5)));

        BoatSettings boat = loadBoat(cfg, log);

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection msgSec = cfg.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                String value = msgSec.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        }

        return new DarkSeaSettings(worldName, seaLevel, seabedBaseY, seabedVariation, centerX, centerZ,
                exposure, zones, armor, generation, mobSpawning, boat, Map.copyOf(messages));
    }

    private static List<Zone> loadZones(FileConfiguration cfg, Logger log) {
        List<Zone> zones = new ArrayList<>();
        for (Map<?, ?> zm : cfg.getMapList("zones")) {
            Object idRaw = zm.get("id");
            if (idRaw == null) {
                log.warning("Skipping a zone with no id in config.yml");
                continue;
            }
            String id = String.valueOf(idRaw);
            String name = zm.get("name") != null ? String.valueOf(zm.get("name")) : id;
            double maxRadius = toDouble(zm.get("max-radius"), -1);
            int requiredTier = toInt(zm.get("required-tier"), 0);

            List<Zone.ZoneEffect> effects = new ArrayList<>();
            if (zm.get("effects") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> em)) {
                        continue;
                    }
                    String typeName = String.valueOf(em.get("type"));
                    PotionEffectType type = resolveEffect(typeName);
                    if (type == null) {
                        log.warning("Zone '" + id + "': unknown potion effect '" + typeName + "' — skipped");
                        continue;
                    }
                    effects.add(new Zone.ZoneEffect(type, Math.max(0, toInt(em.get("amplifier"), 0))));
                }
            }
            zones.add(new Zone(id, name, maxRadius, requiredTier, List.copyOf(effects)));
        }
        return zones;
    }

    private static PotionEffectType resolveEffect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT));
            return Registry.EFFECT.get(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ArmorSettings loadArmor(FileConfiguration cfg, Logger log) {
        boolean unbreakable = cfg.getBoolean("armor.unbreakable", true);
        Map<Integer, ArmorStyle> tiers = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("armor.tiers");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int tier;
                try {
                    tier = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    log.warning("armor.tiers." + key + " is not a number — skipped");
                    continue;
                }
                String name = sec.getString(key + ".name", "Tier " + tier);
                String prefix = sec.getString(key + ".material", "IRON");
                // Fail fast on a prefix that doesn't form real armor materials.
                if (Material.matchMaterial(prefix + "_HELMET") == null) {
                    log.warning("armor.tiers." + key + ".material '" + prefix
                            + "' is not an armor material prefix — skipped");
                    continue;
                }
                tiers.put(tier, new ArmorStyle(name, prefix.toUpperCase(Locale.ROOT)));
            }
        }
        return new ArmorSettings(unbreakable, Map.copyOf(tiers));
    }

    private static GenerationSettings loadGeneration(FileConfiguration cfg, Logger log) {
        Map<Integer, Integer> perRing = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("generation.islands-per-ring");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    perRing.put(Integer.parseInt(key), Math.max(0, sec.getInt(key)));
                } catch (NumberFormatException ex) {
                    log.warning("generation.islands-per-ring." + key + " is not a number — skipped");
                }
            }
        }
        Material chestMarker = matchMaterial(cfg.getString("markers.chest"), Material.LODESTONE, "markers.chest", log);
        Material mobMarker = matchMaterial(cfg.getString("markers.mob-spawn"), Material.GOLD_BLOCK, "markers.mob-spawn", log);
        return new GenerationSettings(
                cfg.getInt("generation.paste-y", 58),
                Math.max(0, cfg.getDouble("generation.min-island-gap", 400)),
                Math.max(0, cfg.getDouble("generation.ring-border-margin", 200)),
                Math.max(500, cfg.getDouble("generation.outer-radius", 8000)),
                Map.copyOf(perRing), chestMarker, mobMarker);
    }

    private static BoatSettings loadBoat(FileConfiguration cfg, Logger log) {
        Map<Integer, BoatLevel> levels = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("boat.levels");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    log.warning("boat.levels." + key + " is not a number — skipped");
                    continue;
                }
                levels.put(level, new BoatLevel(
                        sec.getString(key + ".name", "Level " + level),
                        Math.max(1.0, sec.getDouble(key + ".speed", 1.0)),
                        Math.max(0, sec.getInt(key + ".shield", 0))));
            }
        }
        if (!levels.containsKey(0)) {
            levels.put(0, new BoatLevel("Rowboat", 1.0, 0));
        }
        return new BoatSettings(Math.max(0.1, cfg.getDouble("boat.speed-cap-base", 0.45)), Map.copyOf(levels));
    }

    private static Material matchMaterial(String name, Material fallback, String path, Logger log) {
        if (name == null) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            log.warning(path + " '" + name + "' is not a material — using " + fallback);
            return fallback;
        }
        return mat;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
