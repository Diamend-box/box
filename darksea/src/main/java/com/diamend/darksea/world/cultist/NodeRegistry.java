package com.diamend.darksea.world.cultist;

import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where the caves' ore veins are and when each was last worked, in
 * {@code nodes.yml}. Modelled on
 * {@link com.diamend.darksea.island.IslandRegistry} — the same load, save and
 * clear shape, for the same reason: the file is the truth and the world is
 * rebuilt from it.
 *
 * <p>Positions are stored rather than re-derived. The scatter is deterministic
 * so they could be recomputed, but storing them means an admin can move or
 * delete a vein by hand and have that stick.
 */
public final class NodeRegistry {

    /**
     * One placed vein. {@code lastMined} is 0 until somebody takes a block out
     * of it, and the whole vein regrows on the type's cooldown measured from
     * that moment — so a half-mined vein comes back whole rather than dribbling
     * blocks back one at a time.
     */
    public static final class Node {
        private final String id;
        private final String typeId;
        private final Pos origin;
        private final List<Pos> blocks;
        private long lastMined;

        public Node(String id, String typeId, Pos origin, List<Pos> blocks, long lastMined) {
            this.id = id;
            this.typeId = typeId;
            this.origin = origin;
            this.blocks = List.copyOf(blocks);
            this.lastMined = lastMined;
        }

        public String id() {
            return id;
        }

        public String typeId() {
            return typeId;
        }

        public Pos origin() {
            return origin;
        }

        public List<Pos> blocks() {
            return blocks;
        }

        public long lastMined() {
            return lastMined;
        }

        public void setLastMined(long epochMillis) {
            this.lastMined = epochMillis;
        }

        /** Whether the cooldown has elapsed and this vein is due to grow back. */
        public boolean isDue(long now, long cooldownMillis) {
            return lastMined > 0 && now - lastMined >= cooldownMillis;
        }

        public boolean contains(Pos pos) {
            return blocks.contains(pos);
        }
    }

    private final File file;
    private final Logger log;
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public NodeRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        nodes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("nodes");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            List<Pos> blocks = new ArrayList<>();
            for (String raw : sec.getStringList("blocks")) {
                blocks.add(Pos.parse(raw));
            }
            if (blocks.isEmpty()) {
                log.warning("nodes.yml: '" + id + "' has no blocks — skipped");
                continue;
            }
            nodes.put(id, new Node(id,
                    sec.getString("type", "unknown"),
                    Pos.parse(sec.getString("origin", "0,0,0")),
                    blocks,
                    sec.getLong("last-mined", 0L)));
        }
        log.info("Loaded " + nodes.size() + " ore veins from nodes.yml");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("nodes");
        for (Node node : nodes.values()) {
            ConfigurationSection sec = root.createSection(node.id());
            sec.set("type", node.typeId());
            sec.set("origin", node.origin().serialize());
            sec.set("blocks", node.blocks().stream().map(Pos::serialize).toList());
            sec.set("last-mined", node.lastMined());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    public List<Node> all() {
        return List.copyOf(nodes.values());
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public void add(Node node) {
        nodes.put(node.id(), node);
    }

    public void clearAll() {
        nodes.clear();
        save();
    }

    /**
     * The vein owning a block, or null. A linear scan over a dozen veins of
     * forty blocks each — small enough that an index would be more code than
     * it saves, and this only runs on a block break inside the caves.
     */
    public Node nodeAt(Pos pos) {
        for (Node node : nodes.values()) {
            if (node.contains(pos)) {
                return node;
            }
        }
        return null;
    }
}
