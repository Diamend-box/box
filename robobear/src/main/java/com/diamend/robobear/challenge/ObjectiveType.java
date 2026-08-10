package com.diamend.robobear.challenge;

/**
 * The kinds of goal a round can set.
 *
 * <p>Bee Swarm's rounds ask for pollen and for dead Mechsquitos. The mining
 * equivalents are the two things a boxpvp player is already doing: breaking
 * blocks in a cube, and killing whatever is in the way.
 */
public enum ObjectiveType {

    /** Break any block inside one named mine. */
    MINE_BLOCKS,

    /** Break one specific material, inside one named mine. */
    MINE_MATERIAL,

    /** Kill hostile mobs, anywhere. */
    KILL_MOBS
}
