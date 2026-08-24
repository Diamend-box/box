package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.mine.MineSurvey;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls the objectives a round offers.
 *
 * <p>Two things make the offer a decision rather than a formality. The amounts
 * grow with the round number, so round nine is genuinely harder than round two;
 * and within a round the offers are deliberately spread across difficulties, with
 * Cogs scaled to match. Taking the greedy one buys upgrades faster and is more
 * likely to end the run — which is exactly the trade Bee Swarm's version makes.
 */
public class ObjectiveGenerator {

    private final RoboBearPlugin plugin;

    /**
     * Difficulty spread applied across the offers in a round. The first offer is
     * always the gentle one, so a player under pressure has somewhere to retreat.
     */
    private static final double[] SPREAD = { 0.75, 1.15, 1.5, 1.9 };

    /** How hard to try for an offer that isn't a repeat of one already made. */
    private static final int DISTINCT_TRIES = 12;

    /** How hard to try for a mine that can actually supply the job. */
    private static final int MINE_TRIES = 8;

    public ObjectiveGenerator(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the offers for a round.
     *
     * @return between one and {@code run.objectives-offered} objectives; empty
     *         only when the server has nothing to build one from
     */
    public List<Objective> offer(int round) {
        int wanted = Math.max(1, plugin.getConfig().getInt("run.objectives-offered", 2));
        List<Objective> offers = new ArrayList<>();

        for (int i = 0; i < wanted; i++) {
            double difficulty = SPREAD[Math.min(i, SPREAD.length - 1)];
            for (int attempt = 0; attempt < DISTINCT_TRIES; attempt++) {
                Objective candidate = roll(round, difficulty);
                if (candidate == null) {
                    break; // nothing can be rolled at all; more tries won't help
                }
                if (!duplicates(offers, candidate)) {
                    offers.add(candidate);
                    break;
                }
            }
        }
        return offers;
    }

    /**
     * Whether an offer asks for the same thing as one already on the table.
     *
     * <p>A round is meant to be a decision. "Break 30 iron ore in quartz" beside
     * "break 55 iron ore in quartz" is not one — it's the same job twice with
     * the second priced higher, which is how the choice screen came to show a
     * safe and a greedy option that were the same quest.
     *
     * <p>The amount is deliberately not part of the comparison: differing only
     * in quantity is exactly the case being rejected.
     */
    private static boolean duplicates(List<Objective> offers, Objective candidate) {
        for (Objective existing : offers) {
            if (existing.type() == candidate.type()
                    && java.util.Objects.equals(existing.mineId(), candidate.mineId())
                    && existing.material() == candidate.material()) {
                return true;
            }
        }
        return false;
    }

    private Objective roll(int round, double difficulty) {
        List<ObjectiveType> allowed = allowedTypes();
        if (allowed.isEmpty()) {
            return null;
        }
        ObjectiveType type = allowed.get(ThreadLocalRandom.current().nextInt(allowed.size()));
        return switch (type) {
            case KILL_MOBS -> rollKill(round, difficulty);
            case MINE_MATERIAL -> rollMaterial(round, difficulty);
            case MINE_BLOCKS -> rollBlocks(round, difficulty);
        };
    }

    /**
     * The types a round could actually roll right now.
     *
     * <p>Public because "is there any job at all?" is the honest precondition for
     * letting someone spend an entry pass, and this is the only place that knows.
     */
    public List<ObjectiveType> allowedTypes() {
        List<ObjectiveType> allowed = new ArrayList<>();
        ObjectiveToggles types = plugin.service().objectives();
        boolean haveMines = plugin.mines().enabledSize() > 0;

        if (haveMines && types.isEnabled(ObjectiveType.MINE_BLOCKS)) {
            allowed.add(ObjectiveType.MINE_BLOCKS);
        }
        // Not just "is the type on" — there has to be a mine where a material
        // objective is actually completable, or the offer is a trap.
        if (haveMines && types.isEnabled(ObjectiveType.MINE_MATERIAL)
                && !plugin.mines().minesWithMaterials().isEmpty()) {
            allowed.add(ObjectiveType.MINE_MATERIAL);
        }
        if (types.isEnabled(ObjectiveType.KILL_MOBS)) {
            allowed.add(ObjectiveType.KILL_MOBS);
        }
        return allowed;
    }

    private Objective rollBlocks(int round, double difficulty) {
        int wanted = scaled("objectives.mine-blocks", round, difficulty, 120, 1.28);

        for (int attempt = 0; attempt < MINE_TRIES; attempt++) {
            MineRegion mine = randomMine();
            if (mine == null) {
                return null;
            }
            int amount = affordable(mine, null, wanted);
            if (amount > 0) {
                return new Objective(ObjectiveType.MINE_BLOCKS, mine.id(), null, amount,
                        cogsFor(round, difficulty));
            }
        }
        return null;
    }

    /**
     * "Break N × material in mine".
     *
     * <p>The mine is chosen first and the material second, <b>from that mine's
     * own list</b>. Choosing them independently is how this used to ask for gold
     * in a quartz mine: an objective nobody could complete, which quietly cost
     * whoever took it their run.
     */
    private Objective rollMaterial(int round, double difficulty) {
        List<MineRegion> candidates = plugin.mines().minesWithMaterials();
        if (candidates.isEmpty()) {
            return null;
        }
        int wanted = scaled("objectives.mine-material", round, difficulty, 40, 1.25);

        for (int attempt = 0; attempt < MINE_TRIES; attempt++) {
            MineRegion mine = candidates.get(
                    ThreadLocalRandom.current().nextInt(candidates.size()));

            List<Material> pool = plugin.mines().materialsFor(mine.id());
            if (pool.isEmpty()) {
                continue;
            }
            Material material = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            int amount = affordable(mine, material, wanted);
            if (amount > 0) {
                return new Objective(ObjectiveType.MINE_MATERIAL, mine.id(), material, amount,
                        cogsFor(round, difficulty));
            }
        }
        return null;
    }

    /**
     * Trims a rolled amount down to what the mine can actually give up.
     *
     * <p>The curve knows the round number and nothing else, so it will happily
     * ask for 250 blocks from a mine that holds two stacks and refills every
     * five minutes. That is not a hard round, it is a round nobody can win, and
     * on a ladder an unwinnable round ends the climb.
     *
     * <p>The bound is the mine's estimated stock, multiplied by how many times
     * it refills during a round and by the share of it a single job may claim.
     * A player racing a clock will never strip a mine bare, so asking for all of
     * it is asking for a coin flip.
     *
     * @return the amount to ask for, or 0 when this mine can't support the job
     */
    private int affordable(MineRegion mine, Material material, int wanted) {
        MineSurvey survey = plugin.mines().surveyOf(mine.id());
        if (!survey.foundAnything()) {
            // Nothing has been read from this mine, so there is no honest bound
            // to apply. Refusing here would take mining objectives away from
            // every server whose mines sit in unloaded chunks.
            return wanted;
        }
        long stock = material == null
                ? survey.estimateFilled(mine.volume())
                : survey.estimate(material, mine.volume());
        if (stock <= 0) {
            // The stride never landed on this material. In a big mine that is
            // as likely to mean "rare" as "absent", so it isn't treated as a
            // bound — the material got here by being detected in the first place.
            return wanted;
        }

        return trim(stock, wanted,
                plugin.getConfig().getDouble("objectives.limits.mine-resets-per-round", 1.0),
                plugin.getConfig().getDouble("objectives.limits.mine-fraction", 0.6),
                plugin.getConfig().getInt("objectives.limits.minimum-amount", 10));
    }

    /**
     * The arithmetic behind {@link #affordable}, with nothing to look up.
     *
     * @param stock   estimated blocks of the target in the mine when full
     * @param wanted  what the difficulty curve asked for
     * @return the amount to ask for, or 0 when the mine is too thin to bother
     */
    static int trim(long stock, int wanted, double refills, double share, int minimum) {
        long ceiling = Math.round(stock * Math.max(0.1, refills) * Math.max(0.05, share));
        int floor = Math.max(1, minimum);
        if (ceiling < floor) {
            return 0; // too thin to build a job out of; try somewhere else
        }
        return wanted <= ceiling ? wanted : roundDown(ceiling);
    }

    private Objective rollKill(int round, double difficulty) {
        int amount = scaled("objectives.kill-mobs", round, difficulty, 10, 1.22);
        return new Objective(ObjectiveType.KILL_MOBS, null, null, amount,
                cogsFor(round, difficulty));
    }

    /** {@code base × growth^(round-1) × difficulty}, rounded to something readable. */
    private int scaled(String path, int round, double difficulty, int defaultBase, double defaultGrowth) {
        int base = plugin.getConfig().getInt(path + ".base-amount", defaultBase);
        double growth = plugin.getConfig().getDouble(path + ".growth", defaultGrowth);
        double raw = base * Math.pow(growth, Math.max(0, round - 1)) * difficulty;
        return round(raw);
    }

    /**
     * Rounds to a number that reads like a target rather than a calculation:
     * to 5 under a hundred, to 10 under a thousand, to 50 above it.
     */
    private static int round(double value) {
        int step = value < 100 ? 5 : value < 1000 ? 10 : 50;
        return Math.max(step, (int) (Math.round(value / step) * step));
    }

    /** As {@link #round}, but never upwards — a ceiling has to stay a ceiling. */
    static int roundDown(long value) {
        if (value <= 0) {
            return 0;
        }
        int step = value < 100 ? 5 : value < 1000 ? 10 : 50;
        long snapped = value / step * step;
        // Below one step there is nothing to round to, and rounding up to the
        // step would put the answer back above the ceiling it came from.
        return snapped <= 0 ? (int) value : (int) snapped;
    }

    private int cogsFor(int round, double difficulty) {
        int base = plugin.getConfig().getInt("run.cogs-per-round", 6);
        double perRound = plugin.getConfig().getDouble("run.cogs-round-bonus", 0.5);
        return (int) Math.max(1, Math.round((base + (perRound * (round - 1))) * difficulty));
    }

    /**
     * A mine to send someone to — only ever one that is switched on, so a
     * rank-gated mine a player can't enter never becomes their objective.
     */
    private MineRegion randomMine() {
        List<MineRegion> mines = plugin.mines().enabled();
        if (mines.isEmpty()) {
            return null;
        }
        return mines.get(ThreadLocalRandom.current().nextInt(mines.size()));
    }

}
