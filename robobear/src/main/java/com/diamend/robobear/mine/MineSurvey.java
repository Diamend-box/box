package com.diamend.robobear.mine;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

/**
 * What a walk over a mine's blocks found.
 *
 * <p>RoboBear asks a mine two questions it cannot answer from a bounding box:
 * <i>what are you made of</i>, and <i>how much of it is there</i>. Asking the
 * source plugin is the polite route and the one tried first, but it only works
 * when that plugin exposes its composition — and when it doesn't, the fallback
 * used to be "assume the mine contains everything on the server-wide list",
 * which is how a quartz mine came to be asked for iron ore.
 *
 * <p>So the world itself is the authority. A survey walks a stride across the
 * region, counts what it lands on, and scales those counts up to the whole
 * volume. It is an estimate and reads like one — a mine half mined-out surveys
 * as half a mine — but it is an estimate drawn from the actual blocks, which is
 * strictly better than a guess drawn from a config file.
 *
 * @param sampled how many positions were actually read
 * @param filled  how many of those were not air
 * @param hits    how many times each material was landed on
 */
public record MineSurvey(long sampled, long filled, Map<Material, Integer> hits) {

    /** The answer when nothing could be read at all. */
    public static final MineSurvey NOTHING = new MineSurvey(0, 0, Map.of());

    public MineSurvey {
        hits = Map.copyOf(hits);
    }

    /** Whether this survey read anything. An all-air mine is not empty here. */
    public boolean isEmpty() {
        return sampled <= 0;
    }

    /** Whether anything solid was found — the precondition for any estimate. */
    public boolean foundAnything() {
        return filled > 0;
    }

    public Set<Material> materials() {
        return hits.keySet();
    }

    /**
     * Roughly how many of this material the mine holds, scaled from the sample.
     *
     * <p>Zero means "the survey never landed on one", which for a rare ore in a
     * big mine is not the same as "there are none" — callers treat a zero as
     * <i>unknown</i> rather than as a hard bound.
     */
    public long estimate(Material material, long volume) {
        if (sampled <= 0 || material == null) {
            return 0;
        }
        Integer found = hits.get(material);
        if (found == null) {
            return 0;
        }
        return Math.max(0, Math.round((double) volume * found / sampled));
    }

    /** Roughly how many breakable blocks the mine holds. */
    public long estimateFilled(long volume) {
        if (sampled <= 0) {
            return 0;
        }
        return Math.max(0, Math.round((double) volume * filled / sampled));
    }
}
