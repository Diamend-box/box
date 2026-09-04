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

    /** What each mine is made of, from the source and from the world itself. */
    private Map<String, java.util.Set<org.bukkit.Material>> detected = Collections.emptyMap();

    /** The last usable block survey of each mine, for composition and capacity. */
    private Map<String, MineSurvey> surveys = Collections.emptyMap();

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

        Map<String, java.util.Set<org.bukkit.Material>> reported;
        try {
            reported = provider.compositions();
        } catch (Throwable error) {
            // Knowing what a mine is made of is a bonus, never a requirement.
            reported = Map.of();
        }
        surveyAll(ids, reported);
    }

    /**
     * Works out what each mine contains, from the source and from the blocks.
     *
     * <p>The two are unioned rather than ranked. A source that reports its
     * composition is telling us what the mine holds when full, which a survey of
     * a half-mined region would understate; a survey sees what is really there,
     * which a source that hides its internals can't tell us at all. Between them
     * the answer is right far more often than either alone.
     */
    private void surveyAll(Map<String, MineRegion> ids,
                           Map<String, java.util.Set<org.bukkit.Material>> reported) {
        int budget = plugin.getConfig().getInt("mines.sample-blocks", 2048);
        Map<String, MineSurvey> found = new HashMap<>();
        Map<String, java.util.Set<org.bukkit.Material>> combined = new HashMap<>();

        for (Map.Entry<String, MineRegion> entry : ids.entrySet()) {
            String key = entry.getKey();
            java.util.Set<org.bukkit.Material> union = new java.util.LinkedHashSet<>(
                    reported.getOrDefault(key, java.util.Set.of()));

            MineSurvey survey = MineSurvey.NOTHING;
            if (budget > 0) {
                try {
                    survey = survey(entry.getValue(), budget);
                } catch (Throwable error) {
                    survey = MineSurvey.NOTHING;
                }
            }
            if (survey.isEmpty()) {
                // Nothing readable this time — an unloaded world, or a mine
                // nobody is near. Keeping the last answer is better than
                // forgetting what we already knew about it.
                survey = surveys.getOrDefault(key, MineSurvey.NOTHING);
            }
            if (!survey.isEmpty()) {
                found.put(key, survey);
                union.addAll(survey.materials());
            }
            if (!union.isEmpty()) {
                combined.put(key, union);
            }
        }

        this.surveys = found;
        this.detected = combined;
    }

    /**
     * Reads a stride of blocks across a mine.
     *
     * <p>Bounded by construction. The stride is chosen so a mine of any size
     * costs about {@code budget} block reads, and chunks that aren't already
     * loaded are skipped rather than pulled in — a survey is worth a fraction of
     * a millisecond every few minutes, never a chunk load.
     */
    private MineSurvey survey(MineRegion region, int budget) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(region.world());
        if (world == null) {
            return MineSurvey.NOTHING;
        }

        long volume = region.volume();
        int step = 1;
        if (volume > budget) {
            step = Math.max(1, (int) Math.ceil(Math.cbrt((double) volume / budget)));
        }

        Map<org.bukkit.Material, Integer> hits = new HashMap<>();
        long sampled = 0;
        long filled = 0;

        for (int x = region.minX(); x <= region.maxX(); x += step) {
            for (int z = region.minZ(); z <= region.maxZ(); z += step) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = region.minY(); y <= region.maxY(); y += step) {
                    org.bukkit.Material type = world.getBlockAt(x, y, z).getType();
                    sampled++;
                    if (type.isAir()) {
                        continue;
                    }
                    filled++;
                    hits.merge(type, 1, Integer::sum);
                }
            }
        }
        return sampled == 0 ? MineSurvey.NOTHING : new MineSurvey(sampled, filled, hits);
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

    /** Whether anything is known about any mine's contents. */
    public boolean hasDetectedMaterials() {
        return !detected.isEmpty();
    }

    /** Whether anything is known about <i>this</i> mine's contents. */
    public boolean hasDetectionFor(String id) {
        return detected.containsKey(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    /** The last block survey of a mine, or {@link MineSurvey#NOTHING}. */
    public MineSurvey surveyOf(String id) {
        return surveys.getOrDefault(id == null ? "" : id.toLowerCase(Locale.ROOT),
                MineSurvey.NOTHING);
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
     *   <li>The config list alone — but only on a server where nothing at all
     *       can be read, neither source nor blocks. A mine whose contents are
     *       unknown on a server where others are known is left out instead.</li>
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
        if (!hasDetectionFor(id)) {
            // Nothing is known about this mine. Handing back the whole config
            // list here is exactly what asked for iron ore in the quartz mine,
            // so it is only done when nothing is known about *any* mine — a
            // server with no detection at all, where the list is the only
            // information there is. Once one mine can be read, a mine that
            // can't is left out rather than guessed at.
            return hasDetectedMaterials() ? List.of() : allowed;
        }

        java.util.Set<org.bukkit.Material> present = detectedMaterials(id);
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
