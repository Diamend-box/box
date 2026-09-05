package com.diamend.darksea.relic;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Reads and writes {@code relics-custom.yml}, the file {@code /ds relic
 * editor} owns.
 *
 * <p>A separate file for the same reason loot-custom.yml is separate: the six
 * shipped relics are code, named by loot.yml and shops.yml, and an editor that
 * could rewrite them would let one click break files it cannot see. So the
 * shipped six are untouchable and everything an admin makes lands here.
 *
 * <p>Nothing in this file is trusted. A bad material, a bad boost, a bad
 * potion effect or a bad number costs that <em>field</em> its value and logs a
 * line; only a relic with no usable id at all is dropped. A hand-edited file
 * that is half wrong should still load the half that is right, because the
 * alternative is a server that starts with no relics and no explanation.
 */
public final class CustomRelicConfig {

    private static final List<String> HEADER = List.of(
            "DarkSea custom relics.",
            "",
            "Written by /ds relic editor. The six shipped relics are NOT in here —",
            "they live in code because loot.yml and shops.yml name them by id, and an",
            "edit that renamed one would silently break those files. Everything here",
            "is yours.",
            "",
            "A relic listed here is a real relic: it can be given (/ds give <id>), put",
            "in a loot table by id, sold in shops.yml, woken by the refugees for its",
            "revive-cost, and filed in a reliquary. The key IS the id, and changing a",
            "key orphans every copy already in the world — rename the display name",
            "instead, which is free.",
            "",
            "boost is what the relic actually does while it sits in a reliquary slot:",
            "  SPEED  +10% movement speed",
            "  BOAT   +15% boat speed",
            "  DAMAGE +1 attack damage",
            "  ARMOR  +3 armor",
            "  REGEN  slow regeneration",
            "  VECTOR your strikes infect: Poison II, Slowness",
            "  EFFECT the potion effect named in 'effect', at 'effect-amplifier'",
            "The first six are fixed numbers in code. EFFECT is the open one — use it",
            "for anything the others do not cover.",
            "",
            "boost-line is the one-line description shown on the woken item and in the",
            "reliquary. It is text, not a promise: write what the boost actually does.",
            "",
            "Hand edits survive /ds reload. They do NOT survive the next edit made in",
            "game, which rewrites this file from the loaded state.");

    private CustomRelicConfig() {
    }

    /** Loads every custom relic, or an empty list if the file is absent. */
    public static List<Relic> load(File file, Logger log) {
        if (!file.isFile()) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection relics = yaml.getConfigurationSection("relics");
        if (relics == null) {
            return List.of();
        }
        List<Relic> loaded = new ArrayList<>();
        for (String key : relics.getKeys(false)) {
            ConfigurationSection sec = relics.getConfigurationSection(key);
            if (sec == null) {
                log.warning("relics-custom.yml: '" + key + "' is not a relic block — skipped");
                continue;
            }
            try {
                loaded.add(read(key, sec, log));
            } catch (RuntimeException ex) {
                log.warning("relics-custom.yml: '" + key + "' could not be read ("
                        + ex.getMessage() + ") — skipped");
            }
        }
        return List.copyOf(loaded);
    }

    private static Relic read(String key, ConfigurationSection sec, Logger log) {
        String id = Relic.sanitizeId(key);
        Material material = material(sec.getString("material"), id, log);
        Relic.Boost boost = boost(sec.getString("boost"), id, log);
        PotionEffectType effect = effect(sec.getString("effect"), id, log);
        if (boost == Relic.Boost.EFFECT && effect == null) {
            // An EFFECT relic with no effect does nothing at all, which reads
            // in game as a relic that is broken rather than one that is unset.
            log.warning("relics-custom.yml: '" + id + "' is an EFFECT relic with no"
                    + " usable 'effect' — it will grant nothing until one is set");
        }
        return Relic.custom(id,
                sec.getInt("tier", 3),
                sec.getInt("revive-cost", 100),
                boost,
                material,
                sec.getString("name", "<white>" + id + "</white>"),
                sec.getStringList("lore"),
                sec.getString("boost-line", "an unnamed boon"),
                effect,
                sec.getInt("effect-amplifier", 0));
    }

    private static Material material(String raw, String id, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material found = Material.matchMaterial(raw.trim());
        if (found == null || !found.isItem()) {
            log.warning("relics-custom.yml: '" + id + "' material '" + raw
                    + "' is not an item — using the default");
            return null;
        }
        return found;
    }

    private static Relic.Boost boost(String raw, String id, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Relic.Boost.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("relics-custom.yml: '" + id + "' boost '" + raw
                    + "' is not one of " + List.of(Relic.Boost.values()) + " — using the default");
            return null;
        }
    }

    /** A potion effect by its vanilla name ({@code strength}, {@code minecraft:strength}), or null. */
    public static PotionEffectType effect(String raw, String id, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        PotionEffectType found = effectByName(raw);
        if (found == null && log != null) {
            log.warning("relics-custom.yml: '" + id + "' effect '" + raw
                    + "' is not a potion effect — ignored");
        }
        return found;
    }

    /** A potion effect by name, tolerating case, spaces and a missing namespace. */
    public static PotionEffectType effectByName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (cleaned.startsWith("minecraft:")) {
            cleaned = cleaned.substring("minecraft:".length());
        }
        NamespacedKey key = NamespacedKey.fromString("minecraft:" + cleaned);
        return key == null ? null : Registry.EFFECT.get(key);
    }

    /** The name to write for an effect, and to show in the editor. */
    public static String effectName(PotionEffectType effect) {
        return effect == null ? "none" : effect.getKey().getKey();
    }

    /** Writes every custom relic out whole. Called after each edit, so nothing is lost to a crash. */
    public static void save(List<Relic> relics, File file, Logger log) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Relic relic : relics) {
            String path = "relics." + relic.id() + ".";
            yaml.set(path + "material", relic.material().name());
            yaml.set(path + "name", relic.displayName());
            yaml.set(path + "lore", new ArrayList<>(relic.lore()));
            yaml.set(path + "tier", relic.tier());
            yaml.set(path + "revive-cost", relic.reviveCost());
            yaml.set(path + "boost", relic.boost().name());
            yaml.set(path + "boost-line", relic.boostLine());
            if (relic.boost() == Relic.Boost.EFFECT) {
                yaml.set(path + "effect", effectName(relic.effect()));
                yaml.set(path + "effect-amplifier", relic.effectAmplifier());
            }
        }
        yaml.options().setHeader(HEADER);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }
}
