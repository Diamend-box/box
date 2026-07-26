package com.diamend.darksea.npc;

/**
 * One line on a shop board. {@code BUY} takes Chronons off the player and
 * hands over {@code amount} of {@code itemId}; {@code SELL} does the reverse.
 * Prices are always the whole-lot price, not per-item, so a stack of four
 * arrows for 25 is {@code new ShopOffer(id, 4, 25, BUY)}.
 *
 * <p>An item id comes in three forms:
 * <ul>
 *   <li>a bare {@link com.diamend.darksea.item.DarkSeaItems} or relic id —
 *       {@code sea_salve}, {@code relic_harbor_bell};</li>
 *   <li>{@code vanilla:MATERIAL} for a plain Minecraft item;</li>
 *   <li>{@code custom:<base64>} for a literal item snapshot, which is what the
 *       in-game editor writes when you add an item off your own hotbar. That
 *       captures the whole stack — name, lore, enchants, model data, another
 *       plugin's NBT — so anything you can hold, you can sell.</li>
 * </ul>
 *
 * <p>Nothing here touches Bukkit: the custom form is carried as an opaque
 * string and only decoded at the boards, which keeps the whole price list
 * unit-testable without a server.
 */
public record ShopOffer(String itemId, int amount, int price, Kind kind) {

    public enum Kind { BUY, SELL }

    /** Prefix marking an offer whose id is a vanilla Material name. */
    public static final String VANILLA = "vanilla:";

    /** Prefix marking an offer carrying a base64 snapshot of a literal stack. */
    public static final String CUSTOM = "custom:";

    /** The largest lot size a single line may trade. */
    public static final int MAX_AMOUNT = 64;

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

    public boolean isCustom() {
        return itemId.startsWith(CUSTOM);
    }

    /** The base64 stack snapshot for a custom offer, else null. */
    public String customData() {
        return isCustom() ? itemId.substring(CUSTOM.length()) : null;
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

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    /**
     * The same offer at a new price. Clamped at 1 rather than refused: the
     * editor's right-click walks a price down and should stop at the floor
     * instead of throwing in a player's face.
     */
    public ShopOffer withPrice(int newPrice) {
        return new ShopOffer(itemId, amount, Math.max(1, newPrice), kind);
    }

    /**
     * The same offer at a new lot size, wrapping around at
     * {@link #MAX_AMOUNT} — the editor cycles amount with a single click, so
     * running off the top should come back to 1 rather than stick.
     */
    public ShopOffer withAmount(int newAmount) {
        int wrapped = newAmount;
        if (wrapped > MAX_AMOUNT) {
            wrapped = 1;
        } else if (wrapped < 1) {
            wrapped = MAX_AMOUNT;
        }
        return new ShopOffer(itemId, wrapped, price, kind);
    }

    /** The same line moved between the buy and sell shelves. */
    public ShopOffer withKind(Kind newKind) {
        return new ShopOffer(itemId, amount, price, newKind);
    }
}
