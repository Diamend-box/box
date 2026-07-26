package com.diamend.darksea.world;

/**
 * Which of the plugin's worlds a thing is happening in, and what rules apply
 * there. Every listener that used to ask "is this the Dark Sea?" by comparing
 * a world name now asks the question it actually meant — "does this world have
 * exposure damage?", "does dying here cost you your run loot?" — which is what
 * keeps a second world from silently inheriting the first one's mechanics.
 *
 * <p>Bukkit-free, so the rule table is unit-testable without a server. The
 * mapping from a live world to one of these lives on
 * {@link com.diamend.darksea.DarkSeaPlugin}, which is the only thing that
 * knows the configured names.
 */
public enum ManagedWorld {

    /**
     * The endless hostile ocean. Everything the plugin was originally built
     * for: exposure by ring, sea-armor gating, boats and naval combat, and an
     * extraction loop where dying in the water costs you the run.
     */
    DARK_SEA(true, true, true, true, true),

    /**
     * The cultist caves past the origin island. Deliberately an ordinary
     * place: no exposure, no armor gate, no boats. You can still be attacked —
     * the ore veins are meant to be contested — but dying does not strip what
     * you mined, because a materials run should not be a second extraction
     * run on top of the sea.
     */
    CULTIST(false, false, false, true, false);

    private final boolean exposure;
    private final boolean runLoot;
    private final boolean naval;
    private final boolean pvp;
    private final boolean losesItemsOnDeath;

    ManagedWorld(boolean exposure, boolean runLoot, boolean naval,
                 boolean pvp, boolean losesItemsOnDeath) {
        this.exposure = exposure;
        this.runLoot = runLoot;
        this.naval = naval;
        this.pvp = pvp;
        this.losesItemsOnDeath = losesItemsOnDeath;
    }

    /** Whether the zone exposure task ticks here (and armor tiers gate travel). */
    public boolean hasExposure() {
        return exposure;
    }

    /** Whether loot gathered here is run-scoped and lost on death. */
    public boolean hasRunLoot() {
        return runLoot;
    }

    /** Whether boats, hull HP and naval weapons mean anything here. */
    public boolean hasNavalCombat() {
        return naval;
    }

    /** Whether players can damage each other here. */
    public boolean pvpEnabled() {
        return pvp;
    }

    /**
     * Whether death scatters your inventory. Distinct from {@link #hasRunLoot}:
     * run loot is the sea's extraction penalty on <em>this run's</em> finds,
     * while this is ordinary vanilla item loss.
     */
    public boolean losesItemsOnDeath() {
        return losesItemsOnDeath;
    }

    /** Whether islands, chests, vaults and the mob spawner operate here. */
    public boolean hasIslands() {
        return this == DARK_SEA;
    }

    /** Whether the ore veins operate here. */
    public boolean hasOreVeins() {
        return this == CULTIST;
    }

    /**
     * Whether the terrain is the plugin's to protect: no breaking or placing
     * except where a system explicitly allows it. True in both worlds, for
     * different reasons — islands are pasted scenery, and the caves are a
     * designed space with veins as the only mineable thing in them.
     */
    public boolean protectsTerrain() {
        return true;
    }
}
