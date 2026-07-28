package com.diamend.boxcore.collection;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which blocks a player placed, so place-and-break can't farm a
 * collection.
 *
 * <p>Flags live in the <em>chunk's</em> persistent data container, which means
 * they are written into the region file and survive a restart — the thing an
 * in-memory set could never do. They also unload with their chunk, so memory is
 * bounded by what's loaded rather than by how long the server has been up.
 *
 * <p>Each entry packs a position and a hash of the material into one int:
 *
 * <pre>
 *   bits 22-30  y + 64   (0-511)
 *   bits 18-21  z &amp; 15
 *   bits 14-17  x &amp; 15
 *   bits 0-13   material hash
 * </pre>
 *
 * <p>Position occupies the high bits, so the natural ordering of the ints is
 * also the ordering of the positions and a sorted array can be binary-searched.
 *
 * <p>Carrying the material matters for servers whose mines regenerate. If a
 * player places cobblestone where ore later respawns, the flag is still sitting
 * on that position — without the hash, the regenerated ore would be silently
 * refused. Comparing the hash means a block that no longer matches what was
 * placed counts normally, and the stale flag is dropped on the spot.
 */
public final class PlacedBlocks {

    /** Lowest y any vanilla world uses; keeps the packed y non-negative. */
    private static final int Y_OFFSET = 64;
    private static final int Y_RANGE = 512;
    private static final int HASH_BITS = 14;
    private static final int HASH_MASK = (1 << HASH_BITS) - 1;

    /**
     * Per-chunk ceiling, so one player can't grow a region file without bound.
     * Entries are removed as blocks break, so this is a count of tracked blocks
     * currently standing in the chunk, not a cumulative total.
     */
    private static final int MAX_PER_CHUNK = 8192;

    private static final int[] EMPTY = new int[0];

    private final Plugin plugin;
    private final NamespacedKey key;

    /** Used only if the platform's chunks reject persistent data. */
    private final Map<Long, int[]> fallback = new HashMap<>();
    private boolean persistent = true;

    private long lastFullWarning;

    public PlacedBlocks(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "placed_blocks");
    }

    /** False once a chunk has refused persistent data and the memory fallback took over. */
    public boolean isPersistent() {
        return persistent;
    }

    // ------------------------------------------------------------------
    // Marking
    // ------------------------------------------------------------------

    /** Records that a player placed this block. */
    public void mark(Block block) {
        if (block == null) {
            return;
        }
        mark(block.getChunk(), block.getX(), block.getY(), block.getZ(), block.getType());
    }

    private void mark(Chunk chunk, int x, int y, int z, Material material) {
        int position = position(x, y, z);
        if (position < 0) {
            return; // outside the packable y range; treated as never placed
        }
        int value = (position << HASH_BITS) | hash(material);
        int[] entries = read(chunk);
        int index = indexOf(entries, position);
        if (index >= 0) {
            if (entries[index] == value) {
                return;
            }
            entries[index] = value; // same spot, different block
            write(chunk, entries);
            return;
        }
        if (entries.length >= MAX_PER_CHUNK) {
            warnFull(chunk);
            return;
        }
        write(chunk, insert(entries, -index - 1, value));
    }

    /**
     * Consumes the flag for a block that is being destroyed.
     *
     * @return true when this exact block was placed by a player and so must not
     *         count towards a collection. Any flag found is removed either way.
     */
    public boolean consume(Block block) {
        if (block == null) {
            return false;
        }
        Chunk chunk = block.getChunk();
        int position = position(block.getX(), block.getY(), block.getZ());
        if (position < 0) {
            return false;
        }
        int[] entries = read(chunk);
        int index = indexOf(entries, position);
        if (index < 0) {
            return false;
        }
        boolean sameBlock = (entries[index] & HASH_MASK) == hash(block.getType());
        write(chunk, removeAt(entries, index));
        return sameBlock;
    }

    /** Drops the flag at a position without asking what used to be there. */
    public void clear(Block block) {
        if (block == null) {
            return;
        }
        Chunk chunk = block.getChunk();
        int position = position(block.getX(), block.getY(), block.getZ());
        if (position < 0) {
            return;
        }
        int[] entries = read(chunk);
        int index = indexOf(entries, position);
        if (index >= 0) {
            write(chunk, removeAt(entries, index));
        }
    }

    /**
     * Moves the flags on a run of blocks a piston is about to shift.
     *
     * <p>Without this, pushing a placed block one space over would leave it
     * unflagged at its new home and farmable.
     */
    public void shift(List<Block> blocks, int dx, int dy, int dz) {
        if (blocks == null || blocks.isEmpty() || (dx == 0 && dy == 0 && dz == 0)) {
            return;
        }
        // Read every flag before moving any of them: a run of blocks overlaps
        // itself, so clearing as we go would erase a flag we still need.
        List<int[]> moved = new ArrayList<>();
        for (Block block : blocks) {
            Chunk chunk = block.getChunk();
            int position = position(block.getX(), block.getY(), block.getZ());
            if (position < 0) {
                continue;
            }
            int[] entries = read(chunk);
            int index = indexOf(entries, position);
            if (index < 0) {
                continue;
            }
            moved.add(new int[]{block.getX() + dx, block.getY() + dy, block.getZ() + dz,
                    entries[index] & HASH_MASK});
            write(chunk, removeAt(entries, index));
        }
        World world = blocks.get(0).getWorld();
        for (int[] entry : moved) {
            int position = position(entry[0], entry[1], entry[2]);
            if (position < 0) {
                continue;
            }
            Chunk chunk = world.getChunkAt(entry[0] >> 4, entry[2] >> 4);
            int value = (position << HASH_BITS) | entry[3];
            int[] entries = read(chunk);
            int index = indexOf(entries, position);
            if (index >= 0) {
                entries[index] = value;
                write(chunk, entries);
            } else if (entries.length < MAX_PER_CHUNK) {
                write(chunk, insert(entries, -index - 1, value));
            }
        }
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    /**
     * Wipes every flag in a square of chunks — the escape hatch for a mine that
     * was regenerated by something which never fired a place event.
     *
     * @return how many flags were dropped.
     */
    public int clearChunks(World world, int chunkX, int chunkZ, int radius) {
        if (world == null) {
            return 0;
        }
        int cleared = 0;
        int limit = Math.max(0, radius);
        for (int x = chunkX - limit; x <= chunkX + limit; x++) {
            for (int z = chunkZ - limit; z <= chunkZ + limit; z++) {
                if (!isGenerated(world, x, z)) {
                    continue; // nothing has ever been placed in terrain that doesn't exist
                }
                Chunk chunk = world.getChunkAt(x, z);
                int held = read(chunk).length;
                if (held > 0) {
                    write(chunk, EMPTY);
                    cleared += held;
                }
            }
        }
        return cleared;
    }

    /** Avoids generating fresh terrain just to look for flags that can't be there. */
    private static boolean isGenerated(World world, int x, int z) {
        try {
            return world.isChunkLoaded(x, z) || world.isChunkGenerated(x, z);
        } catch (Throwable ex) {
            return true; // can't tell; fall through and read the chunk
        }
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private int[] read(Chunk chunk) {
        if (persistent) {
            try {
                PersistentDataContainer container = chunk.getPersistentDataContainer();
                int[] stored = container.get(key, PersistentDataType.INTEGER_ARRAY);
                return stored == null ? EMPTY : stored;
            } catch (Throwable ex) {
                degrade(ex);
            }
        }
        return fallback.getOrDefault(chunkKey(chunk), EMPTY);
    }

    private void write(Chunk chunk, int[] entries) {
        if (persistent) {
            try {
                PersistentDataContainer container = chunk.getPersistentDataContainer();
                if (entries.length == 0) {
                    container.remove(key);
                } else {
                    container.set(key, PersistentDataType.INTEGER_ARRAY, entries);
                }
                return;
            } catch (Throwable ex) {
                degrade(ex);
            }
        }
        if (entries.length == 0) {
            fallback.remove(chunkKey(chunk));
        } else {
            fallback.put(chunkKey(chunk), entries);
        }
    }

    /**
     * Falls back to memory once, loudly. Placed-block tracking still works for
     * the current uptime; it just stops surviving a restart.
     */
    private void degrade(Throwable ex) {
        if (persistent) {
            persistent = false;
            plugin.getLogger().warning("This server's chunks don't support persistent data ("
                    + ex.getClass().getSimpleName() + "), so placed-block tracking is being kept"
                    + " in memory and will be forgotten on restart.");
        }
    }

    private void warnFull(Chunk chunk) {
        long now = System.currentTimeMillis();
        if (now - lastFullWarning < 60_000L) {
            return;
        }
        lastFullWarning = now;
        plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in "
                + chunk.getWorld().getName() + " is holding " + MAX_PER_CHUNK
                + " player-placed blocks; further placements there won't be tracked.");
    }

    private static long chunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) ^ (chunk.getZ() & 0xffffffffL);
    }

    // ------------------------------------------------------------------
    // Packing
    // ------------------------------------------------------------------

    /** Packs a position, or -1 when its y can't be represented. */
    public static int position(int x, int y, int z) {
        int shifted = y + Y_OFFSET;
        if (shifted < 0 || shifted >= Y_RANGE) {
            return -1;
        }
        return (shifted << 8) | ((z & 15) << 4) | (x & 15);
    }

    public static int hash(Material material) {
        return material == null ? 0 : Math.floorMod(material.name().hashCode(), HASH_MASK + 1);
    }

    /**
     * Binary search by position, ignoring the material hash in the low bits.
     * Returns the index, or {@code -(insertionPoint) - 1} when absent — the
     * same convention as {@link Arrays#binarySearch(int[], int)}.
     */
    public static int indexOf(int[] entries, int position) {
        int low = 0;
        int high = entries.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int found = entries[mid] >>> HASH_BITS;
            if (found < position) {
                low = mid + 1;
            } else if (found > position) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static int[] insert(int[] entries, int index, int value) {
        int[] grown = new int[entries.length + 1];
        System.arraycopy(entries, 0, grown, 0, index);
        grown[index] = value;
        System.arraycopy(entries, index, grown, index + 1, entries.length - index);
        return grown;
    }

    public static int[] removeAt(int[] entries, int index) {
        if (entries.length == 1) {
            return EMPTY;
        }
        int[] shrunk = new int[entries.length - 1];
        System.arraycopy(entries, 0, shrunk, 0, index);
        System.arraycopy(entries, index + 1, shrunk, index, entries.length - index - 1);
        return shrunk;
    }
}
