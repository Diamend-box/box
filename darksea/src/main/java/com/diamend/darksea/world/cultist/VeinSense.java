package com.diamend.darksea.world.cultist;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.util.Pos;
import com.diamend.darksea.world.ManagedWorld;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Which way the nearest geode is, and how far.
 *
 * <p>Thirteen veins are scattered through a 512-block cave system with a
 * hundred-block ceiling, and nothing in it is breakable — so a player who
 * cannot see a vein cannot tunnel toward one either, and the dimension turns
 * into a search. The first live test found one geode in several minutes of
 * looking, <em>in spectator mode with flight</em>. That is not a difficulty
 * setting, it is the content being unreachable.
 *
 * <p>So the caves tell you where to go. The readout gives a bearing relative to
 * where the player is facing, a distance, and whether the vein is standing or
 * still growing back — enough to walk toward one and to decide it is not worth
 * the walk. What it deliberately does not give is a route: the caves still have
 * to be navigated, and knowing a vein is 90 blocks that way is not the same as
 * knowing which passage gets you there.
 *
 * <p>It never fights the extraction bar for the action bar. A player mid-channel
 * has already found their vein.
 */
public final class VeinSense extends BukkitRunnable {

    /** How often the readout refreshes. Action-bar text fades after ~3 seconds. */
    public static final long PERIOD_TICKS = 20L;

    /**
     * Eight-point compass, clockwise from straight ahead. Relative to the
     * player's facing rather than to north, because "the vein is behind you"
     * is a thing you can act on without first working out which way north is.
     */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final DarkSeaPlugin plugin;
    private final NodeService nodes;
    private final ExtractionChannel extraction;

    public VeinSense(DarkSeaPlugin plugin, NodeService nodes, ExtractionChannel extraction) {
        this.plugin = plugin;
        this.nodes = nodes;
        this.extraction = extraction;
    }

    @Override
    public void run() {
        if (nodes.registry().isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.managedWorld(player.getWorld()) != ManagedWorld.CULTIST) {
                continue;
            }
            if (extraction.isChannelling(player)) {
                continue;   // the progress bar owns the action bar
            }
            show(player);
        }
    }

    private void show(Player player) {
        Location at = player.getLocation();
        // Nearest UNTOUCHED vein first, and only fall back to one that is
        // growing back when every vein in the caves is on its clock.
        //
        // Ranking on distance alone sent players to the geode they had just
        // emptied — which is the nearest one precisely because they are still
        // standing next to it. "Most of the time the indicator leads you to a
        // geode you already mined" was the verdict after the fourth live test,
        // and that is worse than no indicator: it walks you back to a wall of
        // basalt you have already looked at.
        NodeRegistry.Node nearest = nearest(at, false);
        boolean growing = nearest == null;
        if (growing) {
            nearest = nearest(at, true);
        }
        if (nearest == null) {
            return;
        }
        Pos origin = nearest.origin();
        double dx = origin.x() - at.getX();
        double dy = origin.y() - at.getY();
        double dz = origin.z() - at.getZ();
        plugin.messages().actionBar(player,
                growing ? "caves-vein-sense-growing" : "caves-vein-sense",
                "arrow", arrow(at.getYaw(), dx, dz),
                "crystal", nearest.typeId(),
                "distance",
                String.valueOf((int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))));
    }

    /**
     * The closest vein, either among those nobody has touched since the last
     * regrow or among those still growing back.
     *
     * <p>Standing or growing is read off the vein's clock rather than by
     * counting blocks: a running clock means somebody has taken crystal out of
     * it. Reading the world instead would mean touching hundreds of blocks per
     * player per second to answer a question about one line of text.
     */
    private NodeRegistry.Node nearest(Location at, boolean wantGrowing) {
        NodeRegistry.Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NodeRegistry.Node node : nodes.registry().all()) {
            if ((node.firstMined() > 0) != wantGrowing) {
                continue;
            }
            Pos origin = node.origin();
            double dx = origin.x() - at.getX();
            double dy = origin.y() - at.getY();
            double dz = origin.z() - at.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < bestDistance) {
                bestDistance = distanceSquared;
                best = node;
            }
        }
        return best;
    }

    /**
     * The compass arrow for a world offset, relative to where a player faces.
     *
     * <p>Minecraft yaw is 0 along +Z and increases clockwise seen from above,
     * so {@code atan2(-dx, dz)} expresses a world offset in the same frame and
     * the difference of the two is a bearing off the player's nose. Pure, so
     * the frame conversion — the part that is easy to get subtly reversed — is
     * checked without a server.
     */
    static String arrow(double yawDegrees, double dx, double dz) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((target - yawDegrees) % 360.0 + 360.0) % 360.0;
        return ARROWS[(int) Math.round(relative / 45.0) % 8];
    }
}
