package com.diamend.robobear.mine;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The snapshot of every mine RoboBear can see, and the only thing the hot path
 * is allowed to ask.
 *
 * <p>Providers may be slow, reflective or file-backed; this reads them on enable,
 * on {@code /rb reload} and on a slow timer, and flattens the result into plain
 * boxes grouped by world. A block break then costs a map lookup and a handful of
 * int comparisons — no reflection, no plugin lookup, no allocation.
 */
public class MineIndex {

    private final RoboBearPlugin plugin;
    private final ManualMineProvider manual;
    private final MineResetLiteProvider mineResetLite;
    private final MineToggles toggles;
    private final MineMaterials materials;

    /** What each mine is made of, as reported by the source. */
    private Map<String, java.util.Set<org.bukkit.Material>> detected = Collections.emptyMap();

    /** Mines by lower-cased id, in the order the source listed them. */
    private Map<String, MineRegion> byId = Collections.emptyMap();

    /** The same regions grouped by world name, for the lookup path. */
    private Map<String, List<MineRegion>> byWorld = Collections.emptyMap();

    private String activeSource = "none";

    public MineIndex(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.manual = new ManualMineProvider(plugin);
        this.mineResetLite = new MineResetLiteProvider(plugin.getLogger());
        this.toggles = new MineToggles(plugin);
        this.materials = new MineMaterials(plugin);
    }

    public ManualMineProvider manualProvider() {
        return manual;
    }

    /** Which mines are in the objective pool, edited from {@code /rb mines edit}. */
    public MineToggles toggles() {
        return toggles;
    }

    /** Hand-set material lists, edited from {@code /rb quests}. */
    public MineMaterials materials() {
        return materials;
    }

    /** The MineResetLite reader, for {@code /rb mines debug}. */
    public MineResetLiteProvider mineResetLiteProvider() {
        return mineResetLite;
    }

    public String activeSource() {
        return activeSource;
    }

    /**
     * Re-reads the configured source. Safe to call at any time; the new snapshot
     * replaces the old one atomically, so a lookup running concurrently sees one
     * or the other and never a half-built map.
     */
    public void refresh() {
        MineProvider provider = chooseProvider();
        List<MineRegion> found;
        try {
            found = provider.mines();
        } catch (Throwable error) {
            plugin.getLogger().warning("Mine source '" + provider.name()
                    + "' failed; keeping the previous list. " + error);
            return;
        }

        Map<String, MineRegion> ids = new LinkedHashMap<>();
        Map<String, List<MineRegion>> worlds = new HashMap<>();
        for (MineRegion region : found) {
            if (region == null) {
                continue;
            }
            MineRegion previous = ids.put(region.id().toLowerCase(Locale.ROOT), region);
            if (previous != null) {
                plugin.getLogger().warning("Two mines share the id '" + region.id()
                        + "'; the later one wins.");
                List<MineRegion> stale = worlds.get(previous.world());
                if (stale != null) {
                    stale.remove(previous);
                }
            }
            worlds.computeIfAbsent(region.world(), key -> new ArrayList<>()).add(region);
        }

        this.byId = ids;
        this.byWorld = worlds;
        this.activeSource = provider.name();

        try {
            this.detected = provider.compositions();
        } catch (Throwable error) {
            // Knowing what a mine is made of is a bonus, never a requirement.
            this.detected = Collections.emptyMap();
        }
    }

    private MineProvider chooseProvider() {
        String configured = plugin.getConfig().getString("mines.source", "auto")
                .toLowerCase(Locale.ROOT).trim();
        switch (configured) {
            case "manual":
                return manual;
            case "mineresetlite":
                if (!mineResetLite.available()) {
                    plugin.getLogger().warning("mines.source is 'mineresetlite' but that plugin "
                            + "isn't installed or hasn't enabled. No mines are available.");
                }
                return mineResetLite;
            case "auto":
            default:
                return mineResetLite.available() ? mineResetLite : manual;
        }
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** Every known mine, in source order — including ones switched off. */
    public List<MineRegion> all() {
        return new ArrayList<>(byId.values());
    }

    /**
     * The mines objectives may be set in.
     *
     * <p>Deliberately not used by {@link #mineAt} or {@link #isInside}: switching
     * a mine off stops new objectives being rolled there, it does not sabotage a
     * run already under way in it.
     */
    public List<MineRegion> enabled() {
        List<MineRegion> playable = new ArrayList<>(byId.size());
        for (MineRegion region : byId.values()) {
            if (toggles.isEnabled(region.id())) {
                playable.add(region);
            }
        }
        return playable;
    }

    public int size() {
        return byId.size();
    }

    /** How many mines are actually in the objective pool. */
    public int enabledSize() {
        int count = 0;
        for (MineRegion region : byId.values()) {
            if (toggles.isEnabled(region.id())) {
                count++;
            }
        }
        return count;
    }

    /** A mine by id, case-insensitively, or null. */
    public MineRegion byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return byId(id) != null;
    }

    /**
     * The mine a block sits in, or null. Overlapping mines resolve to whichever
     * the source listed first, which is the only stable answer available.
     */
    public MineRegion mineAt(Block block) {
        if (block == null) {
            return null;
        }
        List<MineRegion> candidates = byWorld.get(block.getWorld().getName());
        if (candidates == null) {
            return null;
        }
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String world = block.getWorld().getName();
        for (int i = 0; i < candidates.size(); i++) {
            MineRegion region = candidates.get(i);
            if (region.contains(world, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // What a mine is made of
    // ------------------------------------------------------------------

    /** What the source says this mine contains, or empty when it didn't say. */
    public java.util.Set<org.bukkit.Material> detectedMaterials(String id) {
        return detected.getOrDefault(id == null ? "" : id.toLowerCase(Locale.ROOT), java.util.Set.of());
    }

    /** Whether the source told us anything about any mine's contents. */
    public boolean hasDetectedMaterials() {
        return !detected.isEmpty();
    }

    /**
     * The materials a {@code MINE_MATERIAL} objective may ask for in this mine.
     *
     * <p>Three sources, in order of how much someone meant them:
     *
     * <ol>
     *   <li>A hand-set list for this mine, which wins outright.</li>
     *   <li>What the mine is actually made of, narrowed to the config list of
     *       materials worth asking for. This is the normal path, and it is what
     *       stops the challenge asking for gold in a quartz mine.</li>
     *   <li>The config list alone, when the source can't say what the mine
     *       contains — the old behaviour, and still the best guess available.</li>
     * </ol>
     *
     * <p>An empty result is a real answer: this mine has nothing worth asking
     * for, so no material objective is offered in it.
     */
    public List<org.bukkit.Material> materialsFor(String id) {
        List<org.bukkit.Material> chosen = materials.override(id);
        return chosen.isEmpty() ? automaticMaterials(id) : chosen;
    }

    /**
     * What this mine would be asked for if nobody had set it by hand — steps two
     * and three above. The editor needs this to tell "someone chose exactly this"
     * apart from "someone opened the screen and closed it".
     */
    public List<org.bukkit.Material> automaticMaterials(String id) {
        List<org.bukkit.Material> allowed = configuredMaterials();
        if (allowed.isEmpty()) {
            // config.yml documents an empty list as the off switch for this type,
            // and it stays that way — otherwise emptying it would silently start
            // sending people after whatever filler a mine happens to contain.
            return List.of();
        }
        java.util.Set<org.bukkit.Material> present = detectedMaterials(id);
        if (present.isEmpty()) {
            return allowed;
        }

        List<org.bukkit.Material> both = new ArrayList<>();
        for (org.bukkit.Material material : present) {
            if (allowed.contains(material)) {
                both.add(material);
            }
        }
        return both;
    }

    /** The server-wide list of materials worth setting an objective on. */
    public List<org.bukkit.Material> configuredMaterials() {
        List<org.bukkit.Material> pool = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("objectives.mine-material.materials")) {
            org.bukkit.Material material = com.diamend.robobear.util.Items.material(name, null);
            if (material != null && !pool.contains(material)) {
                pool.add(material);
            }
        }
        return pool;
    }

    /** Every enabled mine that a material objective could actually be set in. */
    public List<MineRegion> minesWithMaterials() {
        List<MineRegion> usable = new ArrayList<>();
        for (MineRegion region : enabled()) {
            if (!materialsFor(region.id()).isEmpty()) {
                usable.add(region);
            }
        }
        return usable;
    }

    /** Whether a block sits inside one specific mine — the run's hot question. */
    public boolean isInside(String mineId, Block block) {
        MineRegion region = byId(mineId);
        return region != null && region.contains(block);
    }
}
