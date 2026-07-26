package com.diamend.darksea.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What each NPC has on the shelf. Pure data and pure arithmetic — no Bukkit —
 * so the whole price list can be asserted in unit tests.
 *
 * <p>Two rules shape the numbers. First, the refugee is the honest baseline
 * and everyone else is derived from him: the black market marks its stock up
 * by {@link #BLACK_MARKET_MARKUP} and pays {@link #BLACK_MARKET_SALVAGE} for
 * salvage, so a captain who walks the extra thirty blocks is rewarded for it.
 * Second, nothing that ends a run is for sale — the Undrowned Heart, relics
 * (woken or not) and boat upgrade tokens are earned in the water, never
 * bought, so the shops make a run cheaper to attempt but never skippable.
 */
public final class ShopStock {

    // Item ids, spelled out rather than imported: DarkSeaItems and Relic both
    // touch Bukkit, and this class stays compilable (and testable) without it.
    private static final String CHRONON = "chronon";
    private static final String TIDAL_DRAUGHT = "tidal_draught";
    private static final String SEA_SALVE = "sea_salve";
    private static final String DEEPSIGHT_TONIC = "deepsight_tonic";
    private static final String GILLWATER_PHILTER = "gillwater_philter";
    private static final String CHAINSHOT_ARROW = "chainshot_arrow";
    private static final String HULLPIERCER_ARROW = "hullpiercer_arrow";
    private static final String HARPOON_GUN = "harpoon_gun";
    private static final String DARK_SEA_BOAT = "dark_sea_boat";

    private static final String SAILORS_CUTLASS = "sailors_cutlass";
    private static final String NAXIAN_CLAW = "naxian_claw";
    private static final String ENHANCED_NAXIAN_CLAW = "enhanced_naxian_claw";
    private static final String ABOMINATION_BONE = "abomination_bone";
    private static final String MARIPHAGE_STINGER = "mariphage_stinger";
    private static final String NAXIAN_BOARDING_AXE = "naxian_boarding_axe";
    private static final String HARBORGUARD_PIKE = "harborguard_pike";
    private static final String ORDER_CEREMONIAL_BLADE = "order_ceremonial_blade";

    /** Relic ids the artificer will take off your hands (half their waking cost). */
    private static final String[] RELIC_IDS = {
            "relic_trade_coin", "relic_harbor_bell", "relic_mariphage_sample",
            "relic_monolith_splinter", "relic_naxome_heart", "relic_mariphage_vector",
    };
    private static final int[] RELIC_WAKE_COSTS = {50, 100, 150, 200, 200, 250};

    /** The black market's markup on everything it sells. */
    public static final double BLACK_MARKET_MARKUP = 1.6;
    /** What the black market pays for salvage, against the refugee's baseline. */
    public static final double BLACK_MARKET_SALVAGE = 1.5;

    /** How many lines of its rotating pool the black market shows at once. */
    public static final int BLACK_MARKET_SLOTS = 5;

    private ShopStock() {
    }

    /**
     * The board for an NPC. {@code rotation} is the sea's reset counter; only
     * the black market reads it, and it reads it as a seed so the same reset
     * always yields the same shelf (a restart mid-cycle doesn't reroll it).
     */
    public static List<ShopOffer> offers(NpcType type, long rotation) {
        return switch (type) {
            case REFUGEE_TRADER -> refugee();
            case ARTIFICER -> artificer();
            case BLACK_MARKET -> blackMarket(rotation);
            case BOAT_EXPERT -> boatExpert();
            case APOTHECARY -> apothecary();
        };
    }

    // ------------------------------------------------------------------
    // The honest shelf
    // ------------------------------------------------------------------

    /**
     * The refugee: cheap survival kit out, sea salvage in. His buy prices are
     * deliberately dull — he exists so a captain is never stuck ashore without
     * a hull, not to be anyone's supplier.
     */
    public static List<ShopOffer> refugee() {
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(ShopOffer.buy(DARK_SEA_BOAT, 1, 40));
        offers.add(ShopOffer.buy(GILLWATER_PHILTER, 1, 15));
        offers.add(ShopOffer.buy(DEEPSIGHT_TONIC, 1, 15));
        offers.add(ShopOffer.buy(SEA_SALVE, 1, 20));
        offers.add(ShopOffer.buy(CHAINSHOT_ARROW, 4, 25));
        offers.addAll(salvage(1.0));
        return List.copyOf(offers);
    }

    /**
     * Salvage prices: what the sea's redundant weapons are worth in Chronons.
     * Scaled by {@code factor} for the black market's better rate.
     */
    public static List<ShopOffer> salvage(double factor) {
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(ShopOffer.sell(NAXIAN_CLAW, 1, 8).scaled(factor));
        offers.add(ShopOffer.sell(SAILORS_CUTLASS, 1, 10).scaled(factor));
        offers.add(ShopOffer.sell(NAXIAN_BOARDING_AXE, 1, 14).scaled(factor));
        offers.add(ShopOffer.sell(HARBORGUARD_PIKE, 1, 14).scaled(factor));
        offers.add(ShopOffer.sell(ORDER_CEREMONIAL_BLADE, 1, 25).scaled(factor));
        offers.add(ShopOffer.sell(ENHANCED_NAXIAN_CLAW, 1, 30).scaled(factor));
        offers.add(ShopOffer.sell(ABOMINATION_BONE, 1, 30).scaled(factor));
        offers.add(ShopOffer.sell(MARIPHAGE_STINGER, 1, 45).scaled(factor));
        return List.copyOf(offers);
    }

    // ------------------------------------------------------------------
    // The specialists
    // ------------------------------------------------------------------

    /**
     * The artificer sells nothing: his board is the relic-waking button plus
     * a standing offer on relics you don't want, at half what waking one
     * costs. Selling a dormant relic can therefore never fund waking another.
     */
    public static List<ShopOffer> artificer() {
        List<ShopOffer> offers = new ArrayList<>();
        for (int i = 0; i < RELIC_IDS.length; i++) {
            offers.add(ShopOffer.sell(RELIC_IDS[i], 1, Math.max(1, RELIC_WAKE_COSTS[i] / 2)));
        }
        return List.copyOf(offers);
    }

    /**
     * The apothecary: every tonic, undercutting the refugee on the two he
     * also stocks, plus food that isn't a rumour.
     */
    public static List<ShopOffer> apothecary() {
        return List.of(
                ShopOffer.buy(TIDAL_DRAUGHT, 1, 25),
                ShopOffer.buy(SEA_SALVE, 1, 18),
                ShopOffer.buy(DEEPSIGHT_TONIC, 1, 12),
                ShopOffer.buy(GILLWATER_PHILTER, 1, 12),
                ShopOffer.buy(ShopOffer.VANILLA + "COOKED_COD", 8, 10),
                ShopOffer.buy(ShopOffer.VANILLA + "BREAD", 8, 10),
                ShopOffer.buy(ShopOffer.VANILLA + "GOLDEN_CARROT", 4, 30));
    }

    /**
     * The boat expert: hulls and the tools to fight from one. His real trade
     * is the clue ladder, which lives on the board as an action rather than
     * an offer (see {@link #clueCost(int)}).
     */
    public static List<ShopOffer> boatExpert() {
        return List.of(
                ShopOffer.buy(DARK_SEA_BOAT, 1, 35),
                ShopOffer.buy(HARPOON_GUN, 1, 120),
                ShopOffer.buy(CHAINSHOT_ARROW, 8, 40),
                ShopOffer.buy(TIDAL_DRAUGHT, 1, 28));
    }

    // ------------------------------------------------------------------
    // The black market
    // ------------------------------------------------------------------

    /**
     * The rotating shelf: {@link #BLACK_MARKET_SLOTS} lines drawn from the
     * pool without repeats, seeded on the reset counter, plus the standing
     * salvage offers (which never rotate — he always wants your loot).
     *
     * <p>Hullpiercer arrows are pinned into the pool rather than guaranteed:
     * they are the only reason to check his shelf, and a cycle where he
     * hasn't got any is what makes the cycles where he has matter.
     */
    public static List<ShopOffer> blackMarket(long rotation) {
        List<ShopOffer> pool = new ArrayList<>(List.of(
                ShopOffer.buy(HULLPIERCER_ARROW, 2, 90),
                ShopOffer.buy(HULLPIERCER_ARROW, 1, 50),
                ShopOffer.buy(CHAINSHOT_ARROW, 8, 45),
                ShopOffer.buy(HARPOON_GUN, 1, 150),
                ShopOffer.buy(TIDAL_DRAUGHT, 2, 60),
                ShopOffer.buy(SEA_SALVE, 2, 45),
                ShopOffer.buy(DEEPSIGHT_TONIC, 2, 30),
                ShopOffer.buy(GILLWATER_PHILTER, 2, 30),
                ShopOffer.buy(DARK_SEA_BOAT, 1, 55)));

        List<ShopOffer> picked = new ArrayList<>();
        Random rng = new Random(rotation * 0x9E3779B97F4A7C15L + 0x5EA1L);
        int want = Math.min(BLACK_MARKET_SLOTS, pool.size());
        while (picked.size() < want) {
            picked.add(pool.remove(rng.nextInt(pool.size())).scaled(BLACK_MARKET_MARKUP));
        }
        picked.addAll(salvage(BLACK_MARKET_SALVAGE));
        return List.copyOf(picked);
    }

    // ------------------------------------------------------------------
    // The clue ladder
    // ------------------------------------------------------------------

    /** How many rumours the old man has to sell before he runs dry. */
    public static final int MAX_CLUE_LEVEL = 4;

    /**
     * Price of the next rumour once you already hold {@code owned} of them.
     * Steep and steepening: the first is pocket change, the last — the live
     * bearing to this cycle's Rim — costs about a full deep run.
     */
    public static int clueCost(int owned) {
        return switch (Math.max(0, owned)) {
            case 0 -> 40;
            case 1 -> 120;
            case 2 -> 300;
            default -> 750;
        };
    }
}
