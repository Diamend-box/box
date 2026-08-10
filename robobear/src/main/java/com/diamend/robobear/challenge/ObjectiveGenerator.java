package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Items;
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
            Objective objective = roll(round, difficulty);
            if (objective != null) {
                offers.add(objective);
            }
        }
        return offers;
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

    private List<ObjectiveType> allowedTypes() {
        List<ObjectiveType> allowed = new ArrayList<>();
        boolean haveMines = plugin.mines().size() > 0;

        if (haveMines && plugin.getConfig().getBoolean("objectives.mine-blocks.enabled", true)) {
            allowed.add(ObjectiveType.MINE_BLOCKS);
        }
        if (haveMines && plugin.getConfig().getBoolean("objectives.mine-material.enabled", true)
                && !materialPool().isEmpty()) {
            allowed.add(ObjectiveType.MINE_MATERIAL);
        }
        if (plugin.getConfig().getBoolean("objectives.kill-mobs.enabled", true)) {
            allowed.add(ObjectiveType.KILL_MOBS);
        }
        return allowed;
    }

    private Objective rollBlocks(int round, double difficulty) {
        MineRegion mine = randomMine();
        if (mine == null) {
            return null;
        }
        int amount = scaled("objectives.mine-blocks", round, difficulty, 120, 1.28);
        return new Objective(ObjectiveType.MINE_BLOCKS, mine.id(), null, amount,
                cogsFor(round, difficulty));
    }

    private Objective rollMaterial(int round, double difficulty) {
        MineRegion mine = randomMine();
        List<Material> pool = materialPool();
        if (mine == null || pool.isEmpty()) {
            return null;
        }
        Material material = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int amount = scaled("objectives.mine-material", round, difficulty, 40, 1.25);
        return new Objective(ObjectiveType.MINE_MATERIAL, mine.id(), material, amount,
                cogsFor(round, difficulty));
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

    private int cogsFor(int round, double difficulty) {
        int base = plugin.getConfig().getInt("run.cogs-per-round", 6);
        double perRound = plugin.getConfig().getDouble("run.cogs-round-bonus", 0.5);
        return (int) Math.max(1, Math.round((base + (perRound * (round - 1))) * difficulty));
    }

    private MineRegion randomMine() {
        List<MineRegion> mines = plugin.mines().all();
        if (mines.isEmpty()) {
            return null;
        }
        return mines.get(ThreadLocalRandom.current().nextInt(mines.size()));
    }

    /** Materials a {@code MINE_MATERIAL} objective may ask for. */
    private List<Material> materialPool() {
        List<Material> pool = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("objectives.mine-material.materials")) {
            Material material = Items.material(name, null);
            if (material != null) {
                pool.add(material);
            }
        }
        return pool;
    }
}
