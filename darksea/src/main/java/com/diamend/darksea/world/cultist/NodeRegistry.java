package com.diamend.darksea.world.cultist;

import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where the caves' crystal veins are and when each was first touched, in
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
     * One placed vein.
     *
     * <p>{@code firstMined} is 0 until somebody takes a block out of it, and the
     * whole vein regrows on the type's cooldown measured from <em>that</em>
     * moment — the first block, not the last. Two things follow, and both are
     * the point:
     *
     * <ul>
     *   <li>Half-mining a vein stops being a punishment. The clock does not
     *       restart with every block, so a vein you chip at comes back on
     *       schedule instead of being held open indefinitely by your own
     *       mining.
     *   <li>Regrow times spread themselves out. Players touch different veins at
     *       different moments, so the cycle desynchronises with no explicit
     *       stagger logic — which is what keeps the caves continuously
     *       grindable rather than a supply drop that is cleared and then dead
     *       for half an hour.
     * </ul>
     *
     * <p>{@code seed} is kept so the geode rind can be recomputed on load rather
     * than stored. The shell is several times the size of the core and never
     * changes, so writing it to disk would triple the file for nothing.
     */
    public static final class Node {
        private final String id;
        private final String typeId;
        private final Pos origin;
        private final List<Pos> blocks;
        private final long seed;
        private long firstMined;

        public Node(String id, String typeId, Pos origin, List<Pos> blocks,
                    long seed, long firstMined) {
            this.id = id;
            this.typeId = typeId;
            this.origin = origin;
            this.blocks = List.copyOf(blocks);
            this.seed = seed;
            this.firstMined = firstMined;
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

        public long seed() {
            return seed;
        }

        public long firstMined() {
            return firstMined;
        }

        /**
         * Starts the regrow clock if it is not already running. Idempotent on
         * purpose — every subsequent block out of the same vein calls this and
         * must not push the deadline back.
         *
         * @return true if this call actually started the clock
         */
        public boolean touch(long epochMillis) {
            if (firstMined > 0) {
                return false;
            }
            firstMined = epochMillis;
            return true;
        }

        /** Clears the clock, so the next block mined starts a fresh cycle. */
        public void resetClock() {
            this.firstMined = 0L;
        }

        /** Whether the cooldown has elapsed and this vein is due to grow back. */
        public boolean isDue(long now, long cooldownMillis) {
            return firstMined > 0 && now - firstMined >= cooldownMillis;
        }

        public boolean contains(Pos pos) {
            return blocks.contains(pos);
        }
    }

    private final File file;
    private final Logger log;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<Pos, Node> index = new HashMap<>();

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
                    sec.getLong("seed", 0L),
                    sec.getLong("first-mined", 0L)));
        }
        reindex();
        log.info("Loaded " + nodes.size() + " crystal veins from nodes.yml");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("nodes");
        for (Node node : nodes.values()) {
            ConfigurationSection sec = root.createSection(node.id());
            sec.set("type", node.typeId());
            sec.set("origin", node.origin().serialize());
            sec.set("blocks", node.blocks().stream().map(Pos::serialize).toList());
            sec.set("seed", node.seed());
            sec.set("first-mined", node.firstMined());
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
        for (Pos pos : node.blocks()) {
            index.put(pos, node);
        }
    }

    public void clearAll() {
        nodes.clear();
        index.clear();
        save();
    }

    private void reindex() {
        index.clear();
        for (Node node : nodes.values()) {
            for (Pos pos : node.blocks()) {
                index.put(pos, node);
            }
        }
    }

    /**
     * The vein owning a block, or null.
     *
     * <p>Indexed rather than scanned. This used to walk every vein's block list
     * and that was fine at a dozen veins of forty blocks; at thirteen veins of a
     * hundred and fifty it is ~1,500 {@link Pos} comparisons, and it now runs on
     * every swing at a crystal rather than once per break.
     */
    public Node nodeAt(Pos pos) {
        return index.get(pos);
    }
}
