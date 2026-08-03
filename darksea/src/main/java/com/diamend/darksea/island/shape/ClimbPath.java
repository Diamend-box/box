package com.diamend.darksea.island.shape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Carves a route a player can actually walk between two points of a shape.
 *
 * <p><b>Why this exists.</b> Several shapes hung their "way up" on geometry
 * that reads as a route in a cutaway render and is not one in the game: the
 * watchtower spiralled a single jutting stone every sixty degrees at radius
 * three — three blocks of horizontal gap per one of rise — and the spire's
 * parkour ledges stepped two blocks of height at a time, which no player can
 * jump at all. Both looked like staircases and neither was climbable, so the
 * chests above them were sealed on every seed the sea has ever generated.
 *
 * <p>The fix is to stop expressing a route as an arrangement and start
 * expressing it as a guarantee. A path walks orthogonally, rises at most one
 * block per step, and clears three cells over each tread — the literal
 * definition of walkable — so what comes out of here is climbable by
 * construction rather than by inspection.
 *
 * <p>Paths are <em>planned</em> before they are cut. A spiral wraps back over
 * itself as a tower tapers, and cutting each flight as it is decided lets a
 * later tread drop into an earlier flight's headroom and wall it off — which
 * is exactly how the spire kept sealing its own summit. Collecting the whole
 * staircase first, laying every tread, and only then clearing the air around
 * them (never through a tread) makes the order the segments were decided in
 * stop mattering.
 */
final class ClimbPath {

    /**
     * The discrete circle of radius three, in order around it. Every cell
     * touches the next, so walking it one cell per block of height is a
     * spiral staircase — the shape both the watchtower's inside wall and the
     * spire's bored shaft climb.
     */
    static final int[][] RING_3 = {
            {-3, -1}, {-2, -2}, {-1, -3}, {0, -3}, {1, -3}, {2, -2}, {3, -1}, {3, 0},
            {3, 1}, {2, 2}, {1, 3}, {0, 3}, {-1, 3}, {-2, 2}, {-3, 1}, {-3, 0},
    };

    /** How much air a tread needs over it: standing room, plus room to climb out. */
    private static final int HEADROOM = 3;

    private final List<Step> steps = new ArrayList<>();

    private record Step(int x, int y, int z, Function<Random, String> tread) {
    }

    /**
     * Adds a walk from one cell to another. {@code (x, y, z)} are the cells the
     * player's <em>feet</em> occupy: a tread goes one below each, and the cells
     * above are cleared when the plan is cut.
     *
     * <p>Horizontal distance is covered one axis at a time and the height is
     * spread evenly over those steps, so the rise never outruns the run — a
     * path asked to climb further than it travels arrives late rather than
     * leaving an unjumpable riser behind. (A climb with no run at all cannot be
     * a staircase, and is refused rather than silently built as a column.)
     */
    void connect(Function<Random, String> tread, int fromX, int fromY, int fromZ,
                 int toX, int toY, int toZ) {
        int run = Math.abs(toX - fromX) + Math.abs(toZ - fromZ);
        int rise = toY - fromY;
        if (run < Math.abs(rise)) {
            // Steeper than one block of rise per block of travel is not a
            // staircase, and building one anyway stacks tread on tread into a
            // solid plug — which is how the spire's spurs kept bricking up the
            // very tunnel mouth they were meant to open. Refuse it: a caller
            // with several candidate routes loses the impossible ones and
            // keeps the rest.
            return;
        }
        if (run == 0) {
            return;
        }
        int total = run;
        int x = fromX, y = fromY, z = fromZ;
        steps.add(new Step(x, y, z, tread));
        for (int i = 1; i <= total; i++) {
            if (x != toX) {
                x += Integer.signum(toX - x);
            } else if (z != toZ) {
                z += Integer.signum(toZ - z);
            }
            int wantY = fromY + (int) Math.round((double) rise * i / total);
            y += Integer.signum(wantY - y);
            steps.add(new Step(x, y, z, tread));
        }
    }

    /** Whether anything was planned — nothing to cut is not an error. */
    boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Lays every tread, then clears the air over all of them. */
    void cut(ShapeSketch s, Random rng) {
        Set<Rel> treads = new HashSet<>();
        for (Step step : steps) {
            Rel at = new Rel(step.x(), step.y() - 1, step.z());
            if (treads.add(at)) {
                s.put(at.x(), at.y(), at.z(), step.tread().apply(rng));
            }
        }
        for (Step step : steps) {
            for (int dy = 0; dy < HEADROOM; dy++) {
                Rel at = new Rel(step.x(), step.y() + dy, step.z());
                if (!treads.contains(at)) {
                    s.carve(at.x(), at.y(), at.z());
                }
            }
        }
    }
}
