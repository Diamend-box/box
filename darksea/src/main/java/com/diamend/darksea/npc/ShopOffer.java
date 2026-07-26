package com.diamend.darksea.npc;

/**
 * One line on a shop board. {@code BUY} takes Chronons off the player and
 * hands over {@code amount} of {@code itemId}; {@code SELL} does the reverse.
 * Prices are always the whole-lot price, not per-item, so a stack of four
 * arrows for 25 is {@code new ShopOffer(id, 4, 25, BUY)}.
 *
 * <p>Item ids are {@link com.diamend.darksea.item.DarkSeaItems} ids, with one
 * exception: an id prefixed {@code vanilla:} names a vanilla Material instead
 * (the apothecary's real food). Nothing here touches Bukkit, so the whole
 * price list is unit-testable.
 */
public record ShopOffer(String itemId, int amount, int price, Kind kind) {

    public enum Kind { BUY, SELL }

    /** Prefix marking an offer whose id is a vanilla Material name. */
    public static final String VANILLA = "vanilla:";

    public ShopOffer {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("offer needs an item id");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("offer amount must be positive: " + amount);
        }
        if (price < 1) {
            throw new IllegalArgumentException("offer price must be positive: " + price);
        }
    }

    public static ShopOffer buy(String itemId, int amount, int price) {
        return new ShopOffer(itemId, amount, price, Kind.BUY);
    }

    public static ShopOffer sell(String itemId, int amount, int price) {
        return new ShopOffer(itemId, amount, price, Kind.SELL);
    }

    public boolean isVanilla() {
        return itemId.startsWith(VANILLA);
    }

    /** The Material name for a vanilla offer, else null. */
    public String vanillaMaterial() {
        return isVanilla() ? itemId.substring(VANILLA.length()) : null;
    }

    /**
     * The same offer with its price scaled and rounded up — how the black
     * market's markup and its better salvage rates are derived from the
     * refugee's honest list rather than written out twice.
     */
    public ShopOffer scaled(double factor) {
        int scaled = (int) Math.ceil(price * factor);
        return new ShopOffer(itemId, amount, Math.max(1, scaled), kind);
    }
}
