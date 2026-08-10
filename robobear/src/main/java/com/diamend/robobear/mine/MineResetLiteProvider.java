package com.diamend.robobear.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
 * still found. Every lookup is tried against a list of plausible shapes — a
 * getter, then a field, then a corner pair, then a nested region object — and
 * whatever worked is cached per class. When nothing works it says so with the
 * real class and member names, both in the log and on demand through
 * {@code /rb mines debug}, so an admin gets something reportable instead of a
 * silent empty menu.
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

    private final Logger logger;

    /** Resolved accessors for whatever class the mines turned out to be. */
    private Accessors accessors;
    private Class<?> accessorsFor;
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
        // Either it exposes bounds we understand, or it is at least called a mine.
        return Accessors.resolve(type) != null
                || type.getSimpleName().toLowerCase(Locale.ROOT).contains("mine");
    }

    // ------------------------------------------------------------------
    // Reading one mine
    // ------------------------------------------------------------------

    private MineRegion read(Object mine) {
        Accessors resolved = accessorsFor(mine.getClass());
        if (resolved == null) {
            return null;
        }
        try {
            return resolved.toRegion(mine);
        } catch (Throwable error) {
            reportOnce("could not read a mine's bounds", mine.getClass(), error);
            return null;
        }
    }

    private Accessors accessorsFor(Class<?> type) {
        if (accessors != null && accessorsFor == type) {
            return accessors;
        }
        Accessors resolved = Accessors.resolve(type);
        if (resolved == null) {
            reportOnce("could not work out how to read mine bounds", type, null);
            return null;
        }
        accessors = resolved;
        accessorsFor = type;
        logger.info("[" + PLUGIN_NAME + "] reading mines via " + resolved.describe());
        return resolved;
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

        Accessors resolved = Accessors.resolve(mineType);
        if (resolved == null) {
            out.add("Could not work out how to read its bounds.");
            out.addAll(describeMembers("mine", mineType));
            out.add("Paste the two lists above into an issue and support can be added.");
            return out;
        }

        out.add("Bounds read via: " + resolved.describe());
        int shown = 0;
        for (Object mine : raw) {
            if (mine == null) {
                continue;
            }
            try {
                MineRegion region = resolved.toRegion(mine);
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
    // Resolved accessor set
    // ------------------------------------------------------------------

    /**
     * The accessors that worked for a particular mine class, resolved once.
     *
     * <p>Three bound shapes are supported: six separate coordinate accessors
     * ({@code getMinX()}…), a pair of corner objects ({@code getMin()} /
     * {@code getMax()}), or either of those living on a nested region object
     * that the mine holds.
     */
    private static final class Accessors {

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

        MineRegion toRegion(Object mine) throws Exception {
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

        String describe() {
            String shape = bounds.describe();
            return region == null ? shape : shape + " on a nested region object";
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
    }

    /** How one object exposes a bounding box. */
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
