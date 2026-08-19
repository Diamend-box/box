package com.diamend.anticheat.world;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * A thread-safe, read-only snapshot of the blocks immediately around each online
 * player.
 *
 * <p>Bukkit's world/block API is <em>not</em> safe to touch off the main
 * thread, but the movement checks (NoFall, Flight ground detection) run on
 * Netty/async threads and still need to know what's under and around a player.
 * Once per tick, on the main thread, we photograph a small box of blocks around
 * every player into an immutable-per-cycle map, then swap it in behind a
 * {@code volatile}. Async readers only ever see a complete, internally
 * consistent snapshot — at most one tick (50 ms) stale, which is fine for these
 * heuristics.
 *
 * <p>We store the {@link Material} per block (not just "solid?") so the checks
 * can also tell water/lava and climbables apart — essential for keeping Flight
 * from false-flagging swimming and ladder-climbing.
 */
public final class BlockCache {

    /** Blocks sampled horizontally either side of the player (in each of x/z). */
    private static final int H_RADIUS = 1;
    /** Blocks sampled below the player's feet. */
    private static final int DOWN = 2;
    /** Blocks sampled above the player's feet. */
    private static final int UP = 3;

    /** Player bounding-box half width, for footprint corner tests. */
    private static final double HALF_WIDTH = 0.3D;

    /** world UID -> (packed block pos -> material). Swapped wholesale each refresh. */
    private volatile Map<UUID, ConcurrentHashMap<Long, Material>> snapshot = new HashMap<>();

    /**
     * Rebuild the snapshot from the given players. MUST run on the main server
     * thread — it reads live blocks.
     */
    public void refresh(Collection<? extends Player> players) {
        Map<UUID, ConcurrentHashMap<Long, Material>> next = new HashMap<>();
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            ConcurrentHashMap<Long, Material> worldMap =
                    next.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());

            int baseX = loc.getBlockX();
            int baseY = loc.getBlockY();
            int baseZ = loc.getBlockZ();
            for (int dx = -H_RADIUS; dx <= H_RADIUS; dx++) {
                for (int dz = -H_RADIUS; dz <= H_RADIUS; dz++) {
                    for (int dy = -DOWN; dy <= UP; dy++) {
                        int x = baseX + dx;
                        int y = baseY + dy;
                        int z = baseZ + dz;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                            continue;
                        }
                        long key = pack(x, y, z);
                        if (worldMap.containsKey(key)) {
                            continue;
                        }
                        worldMap.put(key, world.getBlockAt(x, y, z).getType());
                    }
                }
            }
        }
        this.snapshot = next;
    }

    private Material material(UUID world, int bx, int by, int bz) {
        Map<UUID, ConcurrentHashMap<Long, Material>> snap = snapshot;
        ConcurrentHashMap<Long, Material> worldMap = snap.get(world);
        if (worldMap == null) {
            return null;
        }
        return worldMap.get(pack(bx, by, bz));
    }

    /** Solidity of a single block; {@code false} if it wasn't in the snapshot. */
    public boolean isSolid(UUID world, int bx, int by, int bz) {
        Material m = material(world, bx, by, bz);
        return m != null && m.isSolid();
    }

    /** Whether we actually have data for this block (vs. it being outside the sampled box). */
    public boolean isKnown(UUID world, int bx, int by, int bz) {
        return material(world, bx, by, bz) != null;
    }

    /**
     * Server-authoritative ground test: is there a solid block directly beneath
     * the player's bounding box at feet-height {@code y}?
     *
     * <p>Returns {@code false} when the relevant blocks weren't sampled, so
     * callers must treat "unknown" as "cannot prove airborne" and stay
     * conservative.
     */
    public boolean solidBelow(UUID world, double x, double y, double z) {
        int[] yLevels = { floor(y - 0.02D), floor(y) - 1 };
        double[] xs = { x - HALF_WIDTH, x + HALF_WIDTH };
        double[] zs = { z - HALF_WIDTH, z + HALF_WIDTH };
        for (int by : yLevels) {
            for (double px : xs) {
                for (double pz : zs) {
                    if (isSolid(world, floor(px), by, floor(pz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the footprint below the feet was sampled at all (so a false ground test is trustworthy). */
    public boolean groundKnown(UUID world, double x, double y, double z) {
        int by = floor(y) - 1;
        return isKnown(world, floor(x), by, floor(z));
    }

    /**
     * Whether the player's body occupies liquid or a climbable block — states
     * that legitimately produce non-gravity vertical motion, so Flight must not
     * count them. Checks the feet and the block at head height.
     */
    public boolean inLiquidOrClimbable(UUID world, double x, double y, double z) {
        int bx = floor(x);
        int bz = floor(z);
        return liquidOrClimbable(material(world, bx, floor(y), bz))
                || liquidOrClimbable(material(world, bx, floor(y) + 1, bz));
    }

    private static boolean liquidOrClimbable(Material m) {
        if (m == null) {
            return false;
        }
        return switch (m) {
            case WATER, LAVA, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS,
                 LADDER, VINE, SCAFFOLDING, WEEPING_VINES, WEEPING_VINES_PLANT,
                 TWISTING_VINES, TWISTING_VINES_PLANT, COBWEB, POWDER_SNOW, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    public void clear() {
        this.snapshot = new HashMap<>();
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Pack block coords into a long. x/z use 26 bits, y uses 12 bits. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
