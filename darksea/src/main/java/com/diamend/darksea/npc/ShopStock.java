package com.diamend.darksea.npc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What each NPC has on the shelf — an immutable snapshot of {@code shops.yml},
 * plus the two pieces of arithmetic the file can't express: how the black
 * market's shelf rotates, and how the clue ladder is priced.
 *
 * <p>Pure Java: no Bukkit anywhere in here. {@link ShopConfig} does the
 * parsing and hands the result over, which keeps the whole price list
 * assertable in unit tests and lets {@code /ds reload} swap the snapshot
 * under a running server without touching the boards' code.
 *
 * <p>Two rules the shipped file honours and the tests enforce. The refugee is
 * the honest baseline and the black market is derived from him — marked up on
 * what it sells, better on what it buys — so walking the extra thirty blocks
 * is always worth something. And nothing that ends a run is for sale: the
 * Undrowned Heart, relics, upgrade tokens and the Soulwake Compass are earned
 * in the water, so shops make a run cheaper to attempt but never skippable.
 */
public final class ShopStock {

    /** Prefix marking an offer whose id is a vanilla Material name. */
    public static final String VANILLA = ShopOffer.VANILLA;

    private final Map<String, List<ShopOffer>> fixed;
    private final List<ShopOffer> rotatingPool;
    private final List<ShopOffer> salvage;
    private final Set<String> takesSalvage;
    private final double markup;
    private final double salvageRate;
    private final int slots;
    private final List<Integer> clueCosts;

    public ShopStock(Map<String, List<ShopOffer>> fixed,
                     List<ShopOffer> rotatingPool,
                     List<ShopOffer> salvage,
                     Set<String> takesSalvage,
                     double markup,
                     double salvageRate,
                     int slots,
                     List<Integer> clueCosts) {
        Map<String, List<ShopOffer>> copy = new LinkedHashMap<>();
        fixed.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        this.fixed = Map.copyOf(copy);
        this.rotatingPool = List.copyOf(rotatingPool);
        this.salvage = List.copyOf(salvage);
        this.takesSalvage = Set.copyOf(takesSalvage);
        this.markup = markup;
        this.salvageRate = salvageRate;
        this.slots = slots;
        this.clueCosts = List.copyOf(clueCosts);
    }

    /** An empty snapshot — what a broken or missing shops.yml falls back to. */
    public static ShopStock empty() {
        return new ShopStock(Map.of(), List.of(), List.of(), Set.of(),
                1.0, 1.0, 0, List.of());
    }

    // ------------------------------------------------------------------
    // Boards
    // ------------------------------------------------------------------

    /**
     * The board for an NPC. {@code rotation} is the sea's reset counter; only
     * the black market reads it, and it reads it as a seed, so the same reset
     * always yields the same shelf — a restart mid-cycle doesn't reroll it.
     */
    public List<ShopOffer> offers(NpcType type, long rotation) {
        List<ShopOffer> board = new ArrayList<>();
        if (type.rotatesStock()) {
            board.addAll(rotate(rotation));
        }
        board.addAll(fixed.getOrDefault(type.id(), List.of()));
        if (takesSalvage.contains(type.id())) {
            board.addAll(salvageAt(type.rotatesStock() ? salvageRate : 1.0));
        }
        return List.copyOf(board);
    }

    /**
     * The rotating shelf: {@link #slots} lines drawn from the pool without
     * repeats, seeded on the reset counter.
     *
     * <p>Hullpiercer arrows sit in the pool rather than being guaranteed. They
     * are the only reason to check the black market's shelf at all, and a
     * cycle where he hasn't got any is what makes the cycles where he has
     * matter.
     */
    public List<ShopOffer> rotate(long rotation) {
        List<ShopOffer> pool = new ArrayList<>(rotatingPool);
        List<ShopOffer> picked = new ArrayList<>();
        Random rng = new Random(rotation * 0x9E3779B97F4A7C15L + 0x5EA1L);
        int want = Math.min(slots, pool.size());
        while (picked.size() < want) {
            picked.add(pool.remove(rng.nextInt(pool.size())).scaled(markup));
        }
        return List.copyOf(picked);
    }

    /** The salvage list at a given rate — 1.0 is the refugee's honest price. */
    public List<ShopOffer> salvageAt(double factor) {
        List<ShopOffer> scaled = new ArrayList<>(salvage.size());
        for (ShopOffer offer : salvage) {
            scaled.add(factor == 1.0 ? offer : offer.scaled(factor));
        }
        return List.copyOf(scaled);
    }

    // ------------------------------------------------------------------
    // The clue ladder
    // ------------------------------------------------------------------

    /** How many rumours the old man has to sell before he runs dry. */
    public int maxClueLevel() {
        return clueCosts.size();
    }

    /**
     * Price of the next rumour once you already hold {@code owned} of them.
     * Past the last rung the price plateaus rather than running off the end of
     * the list — the caller checks {@link #maxClueLevel()} first anyway.
     */
    public int clueCost(int owned) {
        if (clueCosts.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int index = Math.min(Math.max(0, owned), clueCosts.size() - 1);
        return clueCosts.get(index);
    }

    // ------------------------------------------------------------------
    // Accessors (config tests and /ds reload reporting)
    // ------------------------------------------------------------------

    public double markup() {
        return markup;
    }

    public double salvageRate() {
        return salvageRate;
    }

    public int slots() {
        return slots;
    }

    public List<ShopOffer> rotatingPool() {
        return rotatingPool;
    }

    public List<ShopOffer> salvage() {
        return salvage;
    }

    public Set<String> takesSalvage() {
        return new HashSet<>(takesSalvage);
    }

    /** The non-rotating lines for an NPC id, exactly as configured. */
    public List<ShopOffer> fixedFor(String npcId) {
        return fixed.getOrDefault(npcId, List.of());
    }

    /** Total configured lines — what the load message reports. */
    public int lineCount() {
        int total = rotatingPool.size() + salvage.size();
        for (List<ShopOffer> lines : fixed.values()) {
            total += lines.size();
        }
        return total;
    }
}
