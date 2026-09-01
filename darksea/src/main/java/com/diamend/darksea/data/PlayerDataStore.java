package com.diamend.darksea.data;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Per-player persistent state (playerdata/&lt;uuid&gt;.yml). Values are cached
 * after first read and written through immediately on change — the files are
 * tiny and changes are rare (boat upgrades).
 */
public final class PlayerDataStore {

    /**
     * A captain's spent stat points, one field per allocatable boat stat. Points
     * are earned 1 per boat level and spent from the boat wheel's Outfit page;
     * {@link #total()} is how many are committed (the rest are still free).
     */
    public record StatPoints(int speed, int toughness, int hp, int ramPower) {
        public static final StatPoints ZERO = new StatPoints(0, 0, 0, 0);

        public int total() {
            return speed + toughness + hp + ramPower;
        }
    }

    private final File folder;
    private final Logger log;
    private final Map<UUID, Integer> boatLevels = new ConcurrentHashMap<>();
    private final Map<UUID, StatPoints> statPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> undrowned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> undrownedLastSave = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clueLevels = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> collections = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> equipped = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> slotsBought = new ConcurrentHashMap<>();

    public PlayerDataStore(File folder, Logger log) {
        this.folder = folder;
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warning("Could not create " + folder);
        }
    }

    public int boatLevel(UUID player) {
        return boatLevels.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            if (!file.exists()) {
                return 0;
            }
            return YamlConfiguration.loadConfiguration(file).getInt("boat-level", 0);
        });
    }

    public void setBoatLevel(UUID player, int level) {
        boatLevels.put(player, level);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("boat-level", level);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    public StatPoints statPoints(UUID player) {
        return statPoints.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            if (!file.exists()) {
                return StatPoints.ZERO;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            return new StatPoints(
                    yaml.getInt("stats.speed", 0),
                    yaml.getInt("stats.toughness", 0),
                    yaml.getInt("stats.hp", 0),
                    yaml.getInt("stats.ram-power", 0));
        });
    }

    public void setStatPoints(UUID player, StatPoints points) {
        statPoints.put(player, points);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("stats.speed", points.speed());
        yaml.set("stats.toughness", points.toughness());
        yaml.set("stats.hp", points.hp());
        yaml.set("stats.ram-power", points.ramPower());
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    /** Whether the captain has attuned the Undrowned Heart (a permanent, once-only choice). */
    public boolean isUndrowned(UUID player) {
        return undrowned.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists() && YamlConfiguration.loadConfiguration(file)
                    .getBoolean("undrowned.attuned", false);
        });
    }

    public void setUndrowned(UUID player, boolean attuned) {
        undrowned.put(player, attuned);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("undrowned.attuned", attuned);
        save(yaml, file);
    }

    /** Epoch millis of the last time the Heart refused a killing blow (0 if never). */
    public long undrownedLastSave(UUID player) {
        return undrownedLastSave.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists()
                    ? YamlConfiguration.loadConfiguration(file).getLong("undrowned.last-save", 0L)
                    : 0L;
        });
    }

    public void setUndrownedLastSave(UUID player, long epochMillis) {
        undrownedLastSave.put(player, epochMillis);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("undrowned.last-save", epochMillis);
        save(yaml, file);
    }

    /**
     * How many rumours about the Heart this captain has bought from the old
     * boat expert. Ratchets up only — the ladder is a purchase history, not a
     * consumable, so a death never costs you a clue you already paid for.
     */
    public int clueLevel(UUID player) {
        return clueLevels.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists()
                    ? YamlConfiguration.loadConfiguration(file).getInt("heart-clues", 0)
                    : 0;
        });
    }

    public void setClueLevel(UUID player, int level) {
        clueLevels.put(player, level);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("heart-clues", level);
        save(yaml, file);
    }

    // ------------------------------------------------------------------
    // The reliquary
    // ------------------------------------------------------------------

    /**
     * Every relic this captain has filed away, by relic id. A collection, not
     * a stack: a relic is in it or it isn't, which matches the boost rules —
     * a second copy of a relic was never worth anything.
     */
    public List<String> relicCollection(UUID player) {
        return collections.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists()
                    ? List.copyOf(YamlConfiguration.loadConfiguration(file).getStringList("relics.collection"))
                    : List.of();
        });
    }

    /** The relics in the bag's slots — the ones actually granting boosts. */
    public List<String> equippedRelics(UUID player) {
        return equipped.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists()
                    ? List.copyOf(YamlConfiguration.loadConfiguration(file).getStringList("relics.equipped"))
                    : List.of();
        });
    }

    /** Writes both lists together: equipping is always a move between them. */
    public void setRelics(UUID player, List<String> collection, List<String> equippedRelics) {
        collections.put(player, List.copyOf(collection));
        equipped.put(player, List.copyOf(equippedRelics));
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("relics.collection", new ArrayList<>(collection));
        yaml.set("relics.equipped", new ArrayList<>(equippedRelics));
        save(yaml, file);
    }

    /**
     * How many extra reliquary slots this captain has bought. Added to the
     * configured starting slots, so lowering the ladder in config never
     * strands relics a player already paid for.
     */
    public int relicSlotsBought(UUID player) {
        return slotsBought.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            return file.exists()
                    ? YamlConfiguration.loadConfiguration(file).getInt("relics.slots-bought", 0)
                    : 0;
        });
    }

    public void setRelicSlotsBought(UUID player, int bought) {
        slotsBought.put(player, bought);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("relics.slots-bought", bought);
        save(yaml, file);
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    private File fileFor(UUID player) {
        return new File(folder, player + ".yml");
    }
}
