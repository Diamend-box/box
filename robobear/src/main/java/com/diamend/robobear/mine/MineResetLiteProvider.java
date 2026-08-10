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
 * <p><b>How it stays honest.</b> Every lookup is tried against a list of
 * plausible shapes — a getter, then a field, then a corner-pair accessor — and
 * whatever worked is cached per class. If nothing works, the provider logs the
 * class's actual method names <em>once</em> and returns no mines, so an admin
 * gets an actionable report instead of a silent empty menu.
 *
 * <p>Nothing here is on a hot path. {@link MineIndex} snapshots the result and
 * block breaks are matched against plain ints.
 */
public class MineResetLiteProvider implements MineProvider {

    private static final String PLUGIN_NAME = "MineResetLite";

    private static final String[] MINES_METHODS = { "getMines", "getAllMines", "mines" };
    private static final String[] MINES_FIELDS = { "mines", "mineList", "allMines" };

    private final Logger logger;

    /** Resolved accessors for whatever class the mines turned out to be. */
    private Accessors accessors;
    private Class<?> accessorsFor;
    private boolean reportedFailure;

    public MineResetLiteProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean available() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public List<MineRegion> mines() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            return List.of();
        }
        Collection<?> raw;
        try {
            raw = findMineCollection(plugin);
        } catch (Throwable error) {
            reportOnce("could not read the mine list from " + PLUGIN_NAME, plugin.getClass(), error);
            return List.of();
        }
        if (raw == null) {
            reportOnce("found no mine list on " + PLUGIN_NAME, plugin.getClass(), null);
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
    // Finding the collection of mines
    // ------------------------------------------------------------------

    private Collection<?> findMineCollection(Plugin plugin) throws Exception {
        for (String name : MINES_METHODS) {
            Method method = findMethod(plugin.getClass(), name);
            if (method != null) {
                Object value = method.invoke(plugin);
                Collection<?> collection = asCollection(value);
                if (collection != null) {
                    return collection;
                }
            }
        }
        for (String name : MINES_FIELDS) {
            Field field = findField(plugin.getClass(), name);
            if (field != null) {
                Collection<?> collection = asCollection(field.get(plugin));
                if (collection != null) {
                    return collection;
                }
            }
        }
        // Last resort: any field on the plugin whose contents look like mines.
        for (Class<?> type = plugin.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Collection.class.isAssignableFrom(field.getType())
                        && !Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                Collection<?> collection = asCollection(field.get(plugin));
                if (collection != null && !collection.isEmpty()
                        && looksLikeMine(collection.iterator().next())) {
                    return collection;
                }
            }
        }
        return null;
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
        return findMethod(type, "getName") != null
                && (findMethod(type, "getMinX") != null || findField(type, "minX") != null);
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
            Set<String> methods = new LinkedHashSet<>();
            for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
                for (Method method : t.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0) {
                        methods.add(method.getName());
                    }
                }
            }
            logger.warning("No-argument methods it offers: " + String.join(", ", methods));
        }
        logger.warning("Set 'mines.source: manual' in RoboBear's config.yml and define the regions "
                + "with /rb pos1, /rb pos2 and /rb mine set <id> to carry on without it.");
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
     * <p>Two bound shapes are supported: six separate coordinate accessors
     * ({@code getMinX()}…), or a pair of corner objects ({@code getMin()} /
     * {@code getMax()}) returning something Location-like.
     */
    private static final class Accessors {

        private final Member name;
        private final Member world;
        private final Member[] mins;   // x, y, z — null when cornerMin is used
        private final Member[] maxes;
        private final Member cornerMin;
        private final Member cornerMax;

        private Accessors(Member name, Member world, Member[] mins, Member[] maxes,
                          Member cornerMin, Member cornerMax) {
            this.name = name;
            this.world = world;
            this.mins = mins;
            this.maxes = maxes;
            this.cornerMin = cornerMin;
            this.cornerMax = cornerMax;
        }

        static Accessors resolve(Class<?> type) {
            Member name = Member.find(type, "getName", "name", "getId", "id");
            if (name == null) {
                return null;
            }
            Member world = Member.find(type, "getWorld", "world", "getWorldName", "worldName");
            if (world == null) {
                return null;
            }

            Member[] mins = {
                    Member.find(type, "getMinX", "minX", "getX1", "x1"),
                    Member.find(type, "getMinY", "minY", "getY1", "y1"),
                    Member.find(type, "getMinZ", "minZ", "getZ1", "z1") };
            Member[] maxes = {
                    Member.find(type, "getMaxX", "maxX", "getX2", "x2"),
                    Member.find(type, "getMaxY", "maxY", "getY2", "y2"),
                    Member.find(type, "getMaxZ", "maxZ", "getZ2", "z2") };
            if (allPresent(mins) && allPresent(maxes)) {
                return new Accessors(name, world, mins, maxes, null, null);
            }

            Member cornerMin = Member.find(type, "getMin", "min", "getMinimum", "getFirst");
            Member cornerMax = Member.find(type, "getMax", "max", "getMaximum", "getSecond");
            if (cornerMin != null && cornerMax != null) {
                return new Accessors(name, world, null, null, cornerMin, cornerMax);
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

        MineRegion toRegion(Object mine) throws Exception {
            Object rawName = name.get(mine);
            String worldName = worldName(world.get(mine));
            if (rawName == null || worldName == null) {
                return null;
            }
            String id = String.valueOf(rawName);
            if (id.isBlank()) {
                return null;
            }
            int[] low;
            int[] high;
            if (mins != null) {
                low = new int[] { toInt(mins[0].get(mine)), toInt(mins[1].get(mine)), toInt(mins[2].get(mine)) };
                high = new int[] { toInt(maxes[0].get(mine)), toInt(maxes[1].get(mine)), toInt(maxes[2].get(mine)) };
            } else {
                low = corner(cornerMin.get(mine));
                high = corner(cornerMax.get(mine));
                if (low == null || high == null) {
                    return null;
                }
            }
            return MineRegion.between(id, id, worldName,
                    low[0], low[1], low[2], high[0], high[1], high[2]);
        }

        String describe() {
            return mins != null ? "coordinate getters" : "corner pair";
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
