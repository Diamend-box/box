package com.diamend.robobear.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads mines out of MineResetLite at runtime, through reflection.
 *
 * <p><b>Why reflection.</b> MineResetLite isn't published to any Maven
 * repository this project can build against, and several forks of it are in
 * circulation with the same plugin name and slightly different internals.
 * Compiling against one of them would pin the server to that fork and break the
 * build for everyone else. This is the same soft-dependency approach
 * CustomAchievements uses for MythicMobs.
 *
 * <p><b>How it stays honest.</b> The plugin is located by name and then by a
 * loose match, so a fork that registers itself as {@code MineResetLitePlus} is
 * still found. Bounds are then read by whichever of several shapes the build
 * turns out to have — named getters, named fields, a corner pair, a nested
 * region object, or the map the mine serialises itself to — and whatever worked
 * is cached per class. When nothing works it says so with the real class and
 * member names, both in the log and on demand through {@code /rb mines debug},
 * so an admin gets something reportable instead of a silent empty menu.
 *
 * <p>Nothing here is on a hot path. {@link MineIndex} snapshots the result and
 * block breaks are matched against plain ints.
 */
public class MineResetLiteProvider implements MineProvider {

    private static final String PLUGIN_NAME = "MineResetLite";

    /** Loose match for forks: normalised plugin name must contain this. */
    private static final String PLUGIN_MARKER = "minereset";

    private static final String[] MINES_METHODS = {
            "getMines", "getAllMines", "mines", "getMineList", "getMineMap" };
    private static final String[] MINES_FIELDS = {
            "mines", "mineList", "allMines", "mineMap" };

    /** Objects that may hold the mine list one level down from the plugin. */
    private static final String[] MANAGER_MEMBERS = {
            "getMineManager", "mineManager", "getManager", "manager",
            "getMines", "mines", "getStorage", "storage" };

    private static final String[] NAME_MEMBERS = {
            "getName", "name", "getId", "id", "getMineName", "mineName" };
    private static final String[] WORLD_MEMBERS = {
            "getWorld", "world", "getWorldName", "worldName" };

    private static final String[][] MIN_MEMBERS = {
            { "getMinX", "minX", "getX1", "x1", "getLowerX", "lowerX" },
            { "getMinY", "minY", "getY1", "y1", "getLowerY", "lowerY" },
            { "getMinZ", "minZ", "getZ1", "z1", "getLowerZ", "lowerZ" } };
    private static final String[][] MAX_MEMBERS = {
            { "getMaxX", "maxX", "getX2", "x2", "getUpperX", "upperX" },
            { "getMaxY", "maxY", "getY2", "y2", "getUpperY", "upperY" },
            { "getMaxZ", "maxZ", "getZ2", "z2", "getUpperZ", "upperZ" } };

    private static final String[] CORNER_MIN_MEMBERS = {
            "getMin", "min", "getMinimum", "getFirst", "getPos1", "pos1",
            "getLoc1", "loc1", "getCorner1", "corner1", "getFirstPoint", "firstPoint", "p1" };
    private static final String[] CORNER_MAX_MEMBERS = {
            "getMax", "max", "getMaximum", "getSecond", "getPos2", "pos2",
            "getLoc2", "loc2", "getCorner2", "corner2", "getSecondPoint", "secondPoint", "p2" };

    /** A member that may hold the bounds in a separate object. */
    private static final String[] REGION_MEMBERS = {
            "getRegion", "region", "getBounds", "bounds", "getCuboid", "cuboid",
            "getArea", "area", "getSelection", "selection" };

    /** Methods that hand back the mine as a plain map, for the serialised path. */
    private static final String[] SERIALIZE_METHODS = { "serialize", "serialise", "toMap" };

    /** What the mine is made of — the block types it actually contains. */
    private static final String[] COMPOSITION_MEMBERS = {
            "getComposition", "composition", "getBlocks", "blocks",
            "getMaterials", "materials", "getPalette", "palette" };

    /** Ways a composition entry might name its block type. */
    private static final String[] MATERIAL_MEMBERS = {
            "getMaterial", "getType", "getBlockType", "material", "type" };

    // Keys as they appear in a serialised mine, already normalised for lookup.
    private static final String[][] MAP_MIN_KEYS = {
            { "minx", "x1", "lowerx", "startx" },
            { "miny", "y1", "lowery", "starty" },
            { "minz", "z1", "lowerz", "startz" } };
    private static final String[][] MAP_MAX_KEYS = {
            { "maxx", "x2", "upperx", "endx" },
            { "maxy", "y2", "uppery", "endy" },
            { "maxz", "z2", "upperz", "endz" } };
    private static final String[] MAP_NAME_KEYS = { "name", "id", "minename" };
    private static final String[] MAP_WORLD_KEYS = { "world", "worldname" };

    private final Logger logger;

    /** The reader that worked for whatever class the mines turned out to be. */
    private MineReader reader;
    private Class<?> readerFor;
    private boolean reportedFailure;
    private boolean reportedLooseMatch;

    public MineResetLiteProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean available() {
        Plugin plugin = locate();
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public List<MineRegion> mines() {
        Plugin plugin = locate();
        if (plugin == null || !plugin.isEnabled()) {
            return List.of();
        }
        Collection<?> raw;
        try {
            raw = findMineCollection(plugin);
        } catch (Throwable error) {
            reportOnce("could not read the mine list from " + plugin.getName(),
                    plugin.getClass(), error);
            return List.of();
        }
        if (raw == null) {
            reportOnce("found no mine list on " + plugin.getName(), plugin.getClass(), null);
            return List.of();
        }

        List<MineRegion> result = new ArrayList<>();
        for (Object mine : raw) {
            if (mine == null) {
                continue;
            }
            MineRegion region = read(mine);
            if (region != null) {
                result.add(region);
            }
        }
        return result;
    }

    /**
     * What each mine is made of, by mine id.
     *
     * <p>This is what stops the challenge asking for gold in a quartz mine.
     * MineResetLite already knows a mine's composition — it has to, to refill it
     * — so the honest source for "what can you actually get here" is the mine
     * itself rather than a list someone typed once.
     *
     * <p>A build that doesn't expose it simply returns nothing for that mine, and
     * the configured material list is used as before.
     */
    @Override
    public Map<String, Set<Material>> compositions() {
        Plugin plugin = locate();
        if (plugin == null || !plugin.isEnabled()) {
            return Map.of();
        }
        Collection<?> raw;
        try {
            raw = findMineCollection(plugin);
        } catch (Throwable error) {
            return Map.of();
        }
        if (raw == null) {
            return Map.of();
        }

        Map<String, Set<Material>> out = new LinkedHashMap<>();
        for (Object mine : raw) {
            if (mine == null) {
                continue;
            }
            MineRegion region = read(mine);
            if (region == null) {
                continue;
            }
            Set<Material> materials = compositionOf(mine);
            if (!materials.isEmpty()) {
                out.put(region.id().toLowerCase(Locale.ROOT), materials);
            }
        }
        return out;
    }

    /** The block types one mine contains, by member first and serialised map second. */
    Set<Material> compositionOf(Object mine) {
        Set<Material> found = new LinkedHashSet<>();

        Member member = Member.find(mine.getClass(), COMPOSITION_MEMBERS);
        if (member != null) {
            try {
                collectMaterials(member.get(mine), found);
            } catch (Throwable ignored) {
                // fall through to the serialised map
            }
        }
        if (!found.isEmpty()) {
            return found;
        }

        Method serializer = findSerializer(mine.getClass());
        if (serializer == null) {
            return found;
        }
        Keyed keyed = Keyed.of(invokeQuietly(serializer, mine));
        if (keyed == null) {
            return found;
        }
        Object key = keyed.findKey("composition", "blocks", "materials", "palette");
        if (key != null) {
            collectMaterials(keyed.value(key), found);
        }
        return found;
    }

    private static void collectMaterials(Object value, Set<Material> into) {
        if (value instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                addMaterial(key, into);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                addMaterial(entry, into);
            }
        } else if (value instanceof Object[] array) {
            for (Object entry : array) {
                addMaterial(entry, into);
            }
        }
    }

    private static void addMaterial(Object token, Set<Material> into) {
        Material material = materialFrom(token);
        if (material != null && material.isBlock() && !material.isAir()) {
            into.add(material);
        }
    }

    /**
     * Turns one composition entry into a Material.
     *
     * <p>Entries are rarely Materials. MineResetLite wraps them in its own block
     * type, and once serialised they are strings in whatever shape that type's
     * {@code toString} produces — {@code GOLD_ORE}, {@code minecraft:stone}, or
     * a legacy {@code WOOL/3} with the data value still attached.
     */
    private static Material materialFrom(Object token) {
        if (token == null) {
            return null;
        }
        if (token instanceof Material material) {
            return material;
        }
        for (String name : MATERIAL_MEMBERS) {
            Method method = findMethod(token.getClass(), name);
            if (method != null && Material.class.isAssignableFrom(method.getReturnType())
                    && invokeQuietly(method, token) instanceof Material material) {
                return material;
            }
        }
        String text = String.valueOf(token).trim();
        int cut = text.indexOf('/');
        if (cut > 0) {
            text = text.substring(0, cut);
        }
        return Material.matchMaterial(text);
    }

    // ------------------------------------------------------------------
    // Locating the plugin
    // ------------------------------------------------------------------

    /**
     * Finds MineResetLite, by exact name first and then by a loose match.
     *
     * <p>The loose pass matters more than it looks: {@code getPlugin(String)} is
     * an exact map lookup, so a fork that calls itself {@code MineresetLite} or
     * {@code MineResetLitePlus} is invisible to the exact name alone — and the
     * symptom is an empty mine list with no reflection error to explain it.
     */
    private Plugin locate() {
        Plugin exact = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (exact != null) {
            return exact;
        }
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (normalise(candidate.getName()).contains(PLUGIN_MARKER)) {
                if (!reportedLooseMatch) {
                    reportedLooseMatch = true;
                    logger.info("Using '" + candidate.getName() + "' as the MineResetLite source "
                            + "(the name isn't an exact match, but it looks like a fork).");
                }
                return candidate;
            }
        }
        return null;
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /** Key normalisation keeps digits, because {@code x1} and {@code x2} are keys. */
    private static String normaliseKey(Object key) {
        return key == null ? "" : String.valueOf(key).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------
    // Finding the collection of mines
    // ------------------------------------------------------------------

    private Collection<?> findMineCollection(Plugin plugin) throws Exception {
        Collection<?> direct = collectionFrom(plugin, plugin.getClass());
        if (direct != null) {
            return direct;
        }
        // A fork may keep the list on a manager rather than on the plugin.
        for (String name : MANAGER_MEMBERS) {
            Member member = Member.find(plugin.getClass(), name);
            if (member == null) {
                continue;
            }
            Object holder;
            try {
                holder = member.get(plugin);
            } catch (Throwable ignored) {
                continue;
            }
            if (holder == null || holder instanceof Collection<?> || holder instanceof Map<?, ?>) {
                continue; // already covered by the direct pass
            }
            Collection<?> nested = collectionFrom(holder, holder.getClass());
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /** Looks for a mine collection on one object: named getters, named fields, then any field. */
    private Collection<?> collectionFrom(Object target, Class<?> type) throws Exception {
        for (String name : MINES_METHODS) {
            Method method = findMethod(type, name);
            if (method != null) {
                Collection<?> collection = asCollection(invokeQuietly(method, target));
                if (collection != null) {
                    return collection;
                }
            }
        }
        for (String name : MINES_FIELDS) {
            Field field = findField(type, name);
            if (field != null) {
                Collection<?> collection = asCollection(field.get(target));
                if (collection != null) {
                    return collection;
                }
            }
        }
        // Last resort: any field whose contents look like mines.
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            for (Field field : t.getDeclaredFields()) {
                if (!Collection.class.isAssignableFrom(field.getType())
                        && !Map.class.isAssignableFrom(field.getType())
                        && !field.getType().isArray()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                Collection<?> collection = asCollection(field.get(target));
                if (collection != null && !collection.isEmpty()
                        && looksLikeMine(collection.iterator().next())) {
                    return collection;
                }
            }
        }
        return null;
    }

    private static Object invokeQuietly(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values();
        }
        if (value instanceof Object[] array) {
            // Arrays.asList, not List.of — a source array may legitimately hold
            // nulls, and List.of would throw on them instead of skipping them.
            return Arrays.asList(array);
        }
        return null;
    }

    private static boolean looksLikeMine(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        if (Member.find(type, NAME_MEMBERS) == null) {
            return false;
        }
        // Cheapest test first: it is at least called a mine. Otherwise pay for a
        // full resolve, which is what catches a class renamed past recognition.
        return type.getSimpleName().toLowerCase(Locale.ROOT).contains("mine")
                || resolveReader(candidate) != null;
    }

    // ------------------------------------------------------------------
    // Reading one mine
    // ------------------------------------------------------------------

    private MineRegion read(Object mine) {
        MineReader resolved = readerFor(mine);
        if (resolved == null) {
            return null;
        }
        try {
            return resolved.read(mine);
        } catch (Throwable error) {
            reportOnce("could not read a mine's bounds", mine.getClass(), error);
            return null;
        }
    }

    private MineReader readerFor(Object mine) {
        Class<?> type = mine.getClass();
        if (reader != null && readerFor == type) {
            return reader;
        }
        MineReader resolved = resolveReader(mine);
        if (resolved == null) {
            reportOnce("could not work out how to read mine bounds", type, null);
            return null;
        }
        reader = resolved;
        readerFor = type;
        logger.info("[" + PLUGIN_NAME + "] reading mines via " + resolved.describe());
        return resolved;
    }

    /**
     * Picks the way this mine object can be read: named members first, then the
     * map it serialises itself to.
     *
     * <p>Package-private so the tests can exercise it against mine objects
     * shaped like real builds without needing a server.
     *
     * @return the reader, or null if nothing here understands the object
     */
    static MineReader resolveReader(Object sample) {
        if (sample == null) {
            return null;
        }
        Class<?> type = sample.getClass();
        Accessors direct = Accessors.resolve(type);
        if (direct != null) {
            return direct;
        }
        return SerialisedReader.resolve(type, sample);
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    /**
     * A full account of what the integration can and can't see, built on demand
     * for {@code /rb mines debug}.
     *
     * <p>The startup warning fires once and can be scrolled away or rotated out
     * before anyone looks; this can be asked for at any time, and its output is
     * exactly what's needed to add support for a fork.
     */
    public List<String> diagnose() {
        List<String> out = new ArrayList<>();
        Plugin plugin = locate();

        if (plugin == null) {
            out.add("No loaded plugin matches '" + PLUGIN_NAME + "'.");
            List<String> loaded = new ArrayList<>();
            for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
                loaded.add(candidate.getName());
            }
            out.add("Plugins loaded: " + (loaded.isEmpty() ? "(none)" : String.join(", ", loaded)));
            out.add("If your mine plugin is in that list under another name, tell us its name.");
            return out;
        }

        out.add("Plugin: " + plugin.getName() + " v" + plugin.getDescription().getVersion()
                + (plugin.isEnabled() ? " (enabled)" : " (NOT enabled)"));
        out.add("Main class: " + plugin.getClass().getName());

        Collection<?> raw;
        try {
            raw = findMineCollection(plugin);
        } catch (Throwable error) {
            out.add("Reading the mine list threw: " + error);
            out.addAll(describeMembers("plugin", plugin.getClass()));
            return out;
        }
        if (raw == null) {
            out.add("No mine collection could be found on it.");
            out.addAll(describeMembers("plugin", plugin.getClass()));
            return out;
        }

        out.add("Mine collection found: " + raw.size() + " entr" + (raw.size() == 1 ? "y" : "ies"));
        if (raw.isEmpty()) {
            out.add("The list is empty — MineResetLite itself has no mines defined,");
            out.add("or it hadn't loaded them yet. Check /mrl list, then /rb reload.");
            return out;
        }

        Object first = raw.iterator().next();
        Class<?> mineType = first == null ? null : first.getClass();
        if (mineType == null) {
            out.add("The first entry was null, which is not something we can read.");
            return out;
        }
        out.add("Mine class: " + mineType.getName());

        MineReader resolved = resolveReader(first);
        if (resolved == null) {
            out.add("Could not work out how to read its bounds.");
            // On an obfuscated build the member names are noise but the
            // serialised keys still mean something, so lead with those.
            out.addAll(describeSerialisation(first));
            out.addAll(describeMembers("mine", mineType));
            out.add("Paste the lists above into an issue and support can be added.");
            return out;
        }

        out.add("Bounds read via: " + resolved.describe());
        int shown = 0;
        for (Object mine : raw) {
            if (mine == null) {
                continue;
            }
            try {
                MineRegion region = resolved.read(mine);
                out.add(region == null
                        ? " • (an entry produced no usable region)"
                        : " • " + region.id() + " " + region.boundsDescription());
            } catch (Throwable error) {
                out.add(" • an entry failed to read: " + error);
            }
            if (++shown >= 10) {
                out.add(" • …and " + (raw.size() - shown) + " more");
                break;
            }
        }
        return out;
    }

    /** The no-arg methods and fields of a class, for a report. */
    private static List<String> describeMembers(String label, Class<?> type) {
        List<String> out = new ArrayList<>();
        Set<String> methods = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            for (Method method : t.getDeclaredMethods()) {
                if (method.getParameterCount() == 0) {
                    methods.add(method.getName() + "():" + method.getReturnType().getSimpleName());
                }
            }
            for (Field field : t.getDeclaredFields()) {
                fields.add(field.getName() + ":" + field.getType().getSimpleName());
            }
        }
        out.add(label + " no-arg methods: " + (methods.isEmpty() ? "(none)" : String.join(", ", methods)));
        out.add(label + " fields: " + (fields.isEmpty() ? "(none)" : String.join(", ", fields)));
        return out;
    }

    /**
     * What the mine says about itself when asked to serialise, keys and value
     * types. This is the half of the report that survives obfuscation: a build
     * has to keep writing the same keys or it could not read its own saved
     * mines back, however thoroughly its fields were renamed.
     */
    private static List<String> describeSerialisation(Object mine) {
        Method serializer = findSerializer(mine.getClass());
        if (serializer == null) {
            return List.of("mine serialize(): none found, so the keys can't be read either.");
        }
        Object value;
        try {
            value = serializer.invoke(mine);
        } catch (Throwable error) {
            return List.of("mine " + serializer.getName() + "() threw: " + error);
        }
        if (!(value instanceof Map<?, ?> map)) {
            return List.of("mine " + serializer.getName() + "() returned no map.");
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object entryValue = entry.getValue();
            keys.add(entry.getKey() + ":"
                    + (entryValue == null ? "null" : entryValue.getClass().getSimpleName()));
        }
        return List.of("mine " + serializer.getName() + "() keys: "
                + (keys.isEmpty() ? "(empty map)" : String.join(", ", keys)));
    }

    private static Method findSerializer(Class<?> type) {
        for (String candidate : SERIALIZE_METHODS) {
            Method method = findMethod(type, candidate);
            if (method != null && Map.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    /**
     * Logs an integration failure once, with enough detail to be reported.
     * Repeating it every refresh would bury the server log.
     */
    private void reportOnce(String what, Class<?> type, Throwable error) {
        if (reportedFailure) {
            return;
        }
        reportedFailure = true;
        logger.warning("RoboBear " + what + ".");
        logger.warning("This build of " + PLUGIN_NAME + " isn't shaped the way the integration expects.");
        if (type != null) {
            logger.warning("Class was: " + type.getName());
            for (String line : describeMembers("Its", type)) {
                logger.warning(line);
            }
        }
        logger.warning("Run '/rb mines debug' for the full report, which is what's needed to add "
                + "support for this build.");
        logger.warning("To carry on meanwhile, set 'mines.source: manual' in RoboBear's config.yml "
                + "and define the regions with /rb pos1, /rb pos2 and /rb mine set <id>.");
        if (error != null) {
            logger.log(Level.FINE, "Reflection failure detail", error);
        }
    }

    // ------------------------------------------------------------------
    // Readers
    // ------------------------------------------------------------------

    /** One resolved way of turning a source plugin's mine object into a region. */
    interface MineReader {

        /** @return the region, or null when this particular mine can't be read */
        MineRegion read(Object mine) throws Exception;

        /** How the bounds are being read, for the log and {@code /rb mines debug}. */
        String describe();
    }

    /**
     * Reads a mine through its own members.
     *
     * <p>Three bound shapes are supported: six separate coordinate accessors
     * ({@code getMinX()}…), a pair of corner objects ({@code getMin()} /
     * {@code getMax()}), or either of those living on a nested region object
     * that the mine holds.
     */
    private static final class Accessors implements MineReader {

        private final Member name;
        private final Member world;
        private final Bounds bounds;

        /** Non-null when the bounds live on a separate object the mine holds. */
        private final Member region;

        /** Whether {@link #world} reads from the region rather than the mine. */
        private final boolean worldOnRegion;

        private Accessors(Member name, Member world, Bounds bounds, Member region,
                          boolean worldOnRegion) {
            this.name = name;
            this.world = world;
            this.bounds = bounds;
            this.region = region;
            this.worldOnRegion = worldOnRegion;
        }

        static Accessors resolve(Class<?> type) {
            Member name = Member.find(type, NAME_MEMBERS);
            if (name == null) {
                return null;
            }
            Member world = Member.find(type, WORLD_MEMBERS);

            Bounds direct = Bounds.resolve(type);
            if (direct != null && world != null) {
                return new Accessors(name, world, direct, null, false);
            }

            // The bounds — and sometimes the world with them — may sit on a
            // region object the mine holds rather than on the mine itself.
            for (String candidate : REGION_MEMBERS) {
                Member holder = Member.find(type, candidate);
                if (holder == null) {
                    continue;
                }
                Class<?> inner = holder.type();
                if (inner == null || inner == Object.class || inner.isPrimitive()) {
                    continue;
                }
                Bounds nested = Bounds.resolve(inner);
                if (nested == null) {
                    continue;
                }
                if (world != null) {
                    return new Accessors(name, world, nested, holder, false);
                }
                Member innerWorld = Member.find(inner, WORLD_MEMBERS);
                if (innerWorld != null) {
                    return new Accessors(name, innerWorld, nested, holder, true);
                }
            }
            return null;
        }

        @Override
        public MineRegion read(Object mine) throws Exception {
            Object rawName = name.get(mine);
            if (rawName == null) {
                return null;
            }
            String id = String.valueOf(rawName);
            if (id.isBlank()) {
                return null;
            }

            Object boundsTarget = region == null ? mine : region.get(mine);
            if (boundsTarget == null) {
                return null;
            }
            String worldName = worldName(worldOnRegion ? world.get(boundsTarget) : world.get(mine));
            if (worldName == null) {
                return null;
            }

            int[][] box = bounds.read(boundsTarget);
            if (box == null) {
                return null;
            }
            return MineRegion.between(id, id, worldName,
                    box[0][0], box[0][1], box[0][2], box[1][0], box[1][1], box[1][2]);
        }

        @Override
        public String describe() {
            String shape = bounds.describe();
            return region == null ? shape : shape + " on a nested region object";
        }
    }

    /**
     * Reads a mine out of the map it serialises itself to.
     *
     * <p><b>This is the path that survives obfuscation.</b> A build whose fields
     * and methods have been renamed to single letters — {@code c:int, t:int,
     * v:int} where {@code minX, minY, minZ} used to be — still has to write its
     * mines to YAML and load them back afterwards, and that means
     * {@code serialize()} still uses the original string keys. Obfuscators
     * rename symbols, not string literals; renaming these would break every
     * saved mine file on the server. So when nothing on the class is called
     * anything any more, the map it produces is still labelled.
     *
     * <p>The keys are resolved once from a real mine and checked to actually
     * hold numbers, so a map that happens to have a {@code serialize()} but no
     * usable bounds is rejected here rather than silently producing nothing.
     */
    private static final class SerialisedReader implements MineReader {

        private final Method serializer;

        /** Preferred over the map when the class still exposes them. */
        private final Member name;
        private final Member world;

        // The keys exactly as the mine wrote them, so lookups need no rework.
        private final Object[] minKeys;
        private final Object[] maxKeys;
        private final Object nameKey;
        private final Object worldKey;

        private SerialisedReader(Method serializer, Member name, Member world,
                                 Object[] minKeys, Object[] maxKeys,
                                 Object nameKey, Object worldKey) {
            this.serializer = serializer;
            this.name = name;
            this.world = world;
            this.minKeys = minKeys;
            this.maxKeys = maxKeys;
            this.nameKey = nameKey;
            this.worldKey = worldKey;
        }

        static SerialisedReader resolve(Class<?> type, Object sample) {
            if (sample == null) {
                return null;
            }
            Method serializer = findSerializer(type);
            if (serializer == null) {
                return null;
            }
            Keyed keyed = Keyed.of(invokeQuietly(serializer, sample));
            if (keyed == null) {
                return null;
            }

            Object[] minKeys = new Object[3];
            Object[] maxKeys = new Object[3];
            for (int axis = 0; axis < 3; axis++) {
                minKeys[axis] = keyed.numberKey(MAP_MIN_KEYS[axis]);
                maxKeys[axis] = keyed.numberKey(MAP_MAX_KEYS[axis]);
                if (minKeys[axis] == null || maxKeys[axis] == null) {
                    return null;
                }
            }

            Member name = Member.find(type, NAME_MEMBERS);
            Object nameKey = name != null ? null : keyed.textKey(MAP_NAME_KEYS);
            if (name == null && nameKey == null) {
                return null;
            }

            Member world = Member.find(type, WORLD_MEMBERS);
            Object worldKey = world != null ? null : keyed.textKey(MAP_WORLD_KEYS);
            if (world == null && worldKey == null) {
                return null;
            }

            return new SerialisedReader(serializer, name, world, minKeys, maxKeys, nameKey, worldKey);
        }

        @Override
        public MineRegion read(Object mine) throws Exception {
            Keyed keyed = Keyed.of(serializer.invoke(mine));
            if (keyed == null) {
                return null;
            }
            String id = name != null ? text(name.get(mine)) : text(keyed.value(nameKey));
            if (id == null || id.isBlank()) {
                return null;
            }
            String worldName = world != null
                    ? worldName(world.get(mine))
                    : text(keyed.value(worldKey));
            if (worldName == null || worldName.isBlank()) {
                return null;
            }

            int[] low = new int[3];
            int[] high = new int[3];
            for (int axis = 0; axis < 3; axis++) {
                if (!(keyed.value(minKeys[axis]) instanceof Number min)
                        || !(keyed.value(maxKeys[axis]) instanceof Number max)) {
                    return null;
                }
                low[axis] = (int) Math.floor(min.doubleValue());
                high[axis] = (int) Math.floor(max.doubleValue());
            }
            return MineRegion.between(id, id, worldName,
                    low[0], low[1], low[2], high[0], high[1], high[2]);
        }

        @Override
        public String describe() {
            return "the serialised map (" + minKeys[0] + "/" + minKeys[1] + "/" + minKeys[2]
                    + " → " + maxKeys[0] + "/" + maxKeys[1] + "/" + maxKeys[2] + ")";
        }

        private static String text(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }

    /**
     * A serialised map indexed by normalised key, so a build writing
     * {@code min-x} or {@code MinX} is found just the same. The original key is
     * what's kept, so reads go straight back to the map with no rework.
     */
    private record Keyed(Map<?, ?> raw, Map<String, Object> keys) {

        static Keyed of(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            Map<String, Object> keys = new LinkedHashMap<>();
            for (Object key : map.keySet()) {
                if (key != null) {
                    keys.putIfAbsent(normaliseKey(key), key);
                }
            }
            return new Keyed(map, keys);
        }

        Object value(Object key) {
            return key == null ? null : raw.get(key);
        }

        /** The first of these normalised names present, whatever its value type. */
        Object findKey(String... candidates) {
            for (String candidate : candidates) {
                Object key = keys.get(candidate);
                if (key != null) {
                    return key;
                }
            }
            return null;
        }

        /** The first of these keys whose value is a number. */
        Object numberKey(String[] candidates) {
            for (String candidate : candidates) {
                Object key = keys.get(candidate);
                if (key != null && raw.get(key) instanceof Number) {
                    return key;
                }
            }
            return null;
        }

        /** The first of these keys whose value is text worth having. */
        Object textKey(String[] candidates) {
            for (String candidate : candidates) {
                Object key = keys.get(candidate);
                if (key != null && raw.get(key) instanceof String text && !text.isBlank()) {
                    return key;
                }
            }
            return null;
        }
    }

    /** How one object exposes a bounding box through its own members. */
    private static final class Bounds {

        private final Member[] mins;   // x, y, z — null when the corner pair is used
        private final Member[] maxes;
        private final Member cornerMin;
        private final Member cornerMax;

        private Bounds(Member[] mins, Member[] maxes, Member cornerMin, Member cornerMax) {
            this.mins = mins;
            this.maxes = maxes;
            this.cornerMin = cornerMin;
            this.cornerMax = cornerMax;
        }

        static Bounds resolve(Class<?> type) {
            Member[] mins = {
                    Member.find(type, MIN_MEMBERS[0]),
                    Member.find(type, MIN_MEMBERS[1]),
                    Member.find(type, MIN_MEMBERS[2]) };
            Member[] maxes = {
                    Member.find(type, MAX_MEMBERS[0]),
                    Member.find(type, MAX_MEMBERS[1]),
                    Member.find(type, MAX_MEMBERS[2]) };
            if (allPresent(mins) && allPresent(maxes)) {
                return new Bounds(mins, maxes, null, null);
            }

            Member cornerMin = Member.find(type, CORNER_MIN_MEMBERS);
            Member cornerMax = Member.find(type, CORNER_MAX_MEMBERS);
            if (cornerMin != null && cornerMax != null) {
                return new Bounds(null, null, cornerMin, cornerMax);
            }
            return null;
        }

        private static boolean allPresent(Member[] members) {
            for (Member member : members) {
                if (member == null) {
                    return false;
                }
            }
            return true;
        }

        /** @return {@code {{minX,minY,minZ},{maxX,maxY,maxZ}}}, or null. */
        int[][] read(Object target) throws Exception {
            if (mins != null) {
                return new int[][] {
                        { toInt(mins[0].get(target)), toInt(mins[1].get(target)),
                                toInt(mins[2].get(target)) },
                        { toInt(maxes[0].get(target)), toInt(maxes[1].get(target)),
                                toInt(maxes[2].get(target)) } };
            }
            int[] low = corner(cornerMin.get(target));
            int[] high = corner(cornerMax.get(target));
            return low == null || high == null ? null : new int[][] { low, high };
        }

        String describe() {
            return mins != null ? "coordinate getters" : "corner pair";
        }

        /** Reads a Location, Vector or anything else offering block coordinates. */
        private static int[] corner(Object value) throws Exception {
            if (value == null) {
                return null;
            }
            if (value instanceof Location location) {
                return new int[] { location.getBlockX(), location.getBlockY(), location.getBlockZ() };
            }
            Member x = Member.find(value.getClass(), "getBlockX", "getX", "x");
            Member y = Member.find(value.getClass(), "getBlockY", "getY", "y");
            Member z = Member.find(value.getClass(), "getBlockZ", "getZ", "z");
            if (x == null || y == null || z == null) {
                return null;
            }
            return new int[] { toInt(x.get(value)), toInt(y.get(value)), toInt(z.get(value)) };
        }

        private static int toInt(Object value) {
            if (value instanceof Number number) {
                return (int) Math.floor(number.doubleValue());
            }
            return Integer.parseInt(String.valueOf(value).trim());
        }
    }

    private static String worldName(Object value) {
        if (value instanceof World world) {
            return world.getName();
        }
        if (value instanceof String text) {
            return text;
        }
        return value == null ? null : String.valueOf(value);
    }

    /** A resolved getter or field, whichever the class turned out to have. */
    private record Member(Method method, Field field) {

        static Member find(Class<?> type, String... names) {
            for (String name : names) {
                Method method = findMethod(type, name);
                if (method != null) {
                    return new Member(method, null);
                }
            }
            for (String name : names) {
                Field field = findField(type, name);
                if (field != null) {
                    return new Member(null, field);
                }
            }
            return null;
        }

        /** The declared type this member yields, used to look one level deeper. */
        Class<?> type() {
            return method != null ? method.getReturnType() : field.getType();
        }

        Object get(Object target) throws Exception {
            return method != null ? method.invoke(target) : field.get(target);
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            Method method;
            try {
                method = t.getDeclaredMethod(name);
            } catch (NoSuchMethodException | RuntimeException ignored) {
                continue; // try the superclass
            }
            if (method.getReturnType() == void.class) {
                continue; // a setter-shaped match is no use to a reader
            }
            // A public method on an exported class needs no unlocking, so a
            // refused setAccessible is not a reason to discard it.
            try {
                method.setAccessible(true);
            } catch (Throwable ignored) {
                // use it as-is
            }
            return method;
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            Field field;
            try {
                field = t.getDeclaredField(name);
            } catch (NoSuchFieldException | RuntimeException ignored) {
                continue; // try the superclass
            }
            try {
                field.setAccessible(true);
            } catch (Throwable ignored) {
                // use it as-is; a public field still reads
            }
            return field;
        }
        return null;
    }
}
