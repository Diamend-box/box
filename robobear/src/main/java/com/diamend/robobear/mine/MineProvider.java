package com.diamend.robobear.mine;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A source of {@link MineRegion}s.
 *
 * <p>Providers are asked for a full list and never for a single lookup: the
 * result is snapshotted by {@link MineIndex}, so a provider is free to be slow,
 * to use reflection, or to read files. Nothing here runs on a block break.
 */
public interface MineProvider {

    /** Human-readable name of this source, for logs and {@code /rb mines}. */
    String name();

    /** Whether this provider can currently supply mines at all. */
    boolean available();

    /**
     * Every mine this source knows about. Implementations return an empty list
     * rather than throwing — a source that has broken should cost the server its
     * challenges, not its startup.
     */
    List<MineRegion> mines();

    /**
     * What each mine is made of, keyed by lower-cased mine id.
     *
     * <p>Optional: a source that doesn't know returns nothing and the configured
     * material list is used instead. A source that does know stops the challenge
     * asking for a block the mine doesn't contain.
     */
    default Map<String, Set<Material>> compositions() {
        return Map.of();
    }
}
