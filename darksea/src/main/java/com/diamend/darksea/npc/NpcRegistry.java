package com.diamend.darksea.npc;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where the outpost's people stand, in {@code npcs.yml}. Placement is a
 * runtime admin command rather than anything baked into a schematic, which is
 * what lets the whole NPC layer exist before the home island is built: paste
 * the outpost whenever it's ready, walk to each doorway, run
 * {@code /ds npc create <type>}.
 *
 * <p>The black market's shelf is not seeded from here. It used to be, off a
 * counter bumped by every sea reset; it now runs on wall time via
 * {@link MarketClock}, which needs nothing persisted at all. Placements do
 * live here rather than with the islands, because they must survive a full
 * reset — that wipes the island registry outright.
 */
public final class NpcRegistry {

    /** A placed NPC: its stable id, what it is, and exactly where it stands. */
    public record Placement(String id, NpcType type, String world,
                            double x, double y, double z, float yaw) {

        public Location toLocation(org.bukkit.World bukkitWorld) {
            return new Location(bukkitWorld, x, y, z, yaw, 0.0f);
        }
    }

    private final File file;
    private final Logger log;
    private final Map<String, Placement> placements = new LinkedHashMap<>();

    public NpcRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        placements.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            NpcType type = NpcType.byId(sec.getString("type", ""));
            if (type == null) {
                log.warning("npcs.yml: '" + id + "' has an unknown type — skipped");
                continue;
            }
            placements.put(id, new Placement(id, type,
                    sec.getString("world", ""),
                    sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                    (float) sec.getDouble("yaw")));
        }
        log.info("Loaded " + placements.size() + " NPC placements");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("npcs");
        for (Placement placement : placements.values()) {
            ConfigurationSection sec = root.createSection(placement.id());
            sec.set("type", placement.type().id());
            sec.set("world", placement.world());
            sec.set("x", placement.x());
            sec.set("y", placement.y());
            sec.set("z", placement.z());
            sec.set("yaw", placement.yaw());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    public List<Placement> all() {
        return List.copyOf(placements.values());
    }

    /**
     * The placements standing in one chunk.
     *
     * <p>Used on chunk load to put back NPCs the unload took with them, so it
     * runs for every chunk the server streams in and stays a handful of
     * comparisons against a five-entry registry.
     */
    public List<Placement> inChunk(String worldName, int chunkX, int chunkZ) {
        List<Placement> found = new ArrayList<>();
        for (Placement placement : placements.values()) {
            if (placement.world().equals(worldName)
                    && chunkOf(placement.x()) == chunkX
                    && chunkOf(placement.z()) == chunkZ) {
                found.add(placement);
            }
        }
        return found;
    }

    /**
     * A world coordinate's chunk coordinate, dividing the way Minecraft does.
     *
     * <p>Floor, not truncation: {@code (int) -0.5} is 0 and would put anything
     * in the negative half of chunk -1 into chunk 0 instead.
     */
    public static int chunkOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), 16);
    }

    public Placement get(String id) {
        return placements.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Records a placement, replacing any NPC already registered under the
     * generated id. Ids are {@code <type>-<n>} so a second artificer is
     * {@code artificer-2} and the first stays {@code artificer-1} — stable
     * across restarts, which is what the spawned entity is tagged with.
     */
    public Placement add(NpcType type, Location location) {
        String id = nextId(type);
        Placement placement = new Placement(id, type, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw());
        placements.put(id, placement);
        save();
        return placement;
    }

    /** Removes a placement by id; returns what was removed, or null. */
    public Placement remove(String id) {
        Placement removed = placements.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            save();
        }
        return removed;
    }

    private String nextId(NpcType type) {
        for (int n = 1; ; n++) {
            String candidate = type.id() + "-" + n;
            if (!placements.containsKey(candidate)) {
                return candidate;
            }
        }
    }

}
