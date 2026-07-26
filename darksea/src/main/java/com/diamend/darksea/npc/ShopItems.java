package com.diamend.darksea.npc;

import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.relic.Relic;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Turns shop lines into real stacks and back. The three id forms a
 * {@link ShopOffer} can carry all resolve here, so the player-facing boards
 * and the admin editor agree on exactly what a line means.
 *
 * <p>The interesting form is {@code custom:} — a base64 snapshot of a whole
 * ItemStack, written when an admin adds a line by clicking something in their
 * own inventory. It carries name, lore, enchants, model data and any other
 * plugin's NBT, which is what makes "sell my custom gear" work without this
 * plugin knowing anything about that gear.
 */
public final class ShopItems {

    private ShopItems() {
    }

    /**
     * The stack a line trades in, at the line's lot size, or null if the id
     * names nothing this server can build.
     */
    public static ItemStack resolve(ShopOffer offer, Logger log) {
        if (offer.isCustom()) {
            return decode(offer, log);
        }
        if (offer.isVanilla()) {
            Material material = Material.matchMaterial(offer.vanillaMaterial());
            return material == null ? null : new ItemStack(material, offer.amount());
        }
        ItemStack stack = DarkSeaItems.create(offer.itemId(), offer.amount());
        if (stack != null && stack.getAmount() != offer.amount()
                && stack.getMaxStackSize() > 1) {
            stack.setAmount(offer.amount());
        }
        return stack;
    }

    /** Rebuilds a custom line's stack from its snapshot, or null if unreadable. */
    public static ItemStack decode(ShopOffer offer, Logger log) {
        String data = offer.customData();
        if (data == null) {
            return null;
        }
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
            stack.setAmount(offer.amount());
            return stack;
        } catch (RuntimeException ex) {
            log.warning("shops.yml has an unreadable custom item: " + ex.getMessage());
            return null;
        }
    }

    /**
     * The id that best describes a stack, chosen to keep shops.yml readable: a
     * registry item or relic writes its own id, a plain vanilla item writes
     * {@code vanilla:MATERIAL}, and only a stack carrying real item data falls
     * back to a snapshot. Null if it can't be encoded at all.
     */
    public static String encode(ItemStack stack, Logger log) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        Relic relic = Relic.of(stack);
        if (relic != null) {
            return relic.id();
        }
        String registryId = DarkSeaItems.idOf(stack);
        if (registryId != null) {
            return registryId;
        }
        if (isPlain(stack)) {
            return ShopOffer.VANILLA + stack.getType().name();
        }
        try {
            return ShopOffer.CUSTOM
                    + Base64.getEncoder().encodeToString(stack.serializeAsBytes());
        } catch (RuntimeException ex) {
            log.warning("Could not snapshot " + stack.getType()
                    + " for a shop line: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Whether a stack is an ordinary item of its type, carrying nothing a
     * fresh one wouldn't — the question {@link #encode} has to answer to
     * decide between a readable id and a base64 snapshot.
     *
     * <p>Asked by comparing the serialised forms, because that is literally
     * the thing being decided: if snapshotting this stack would produce the
     * same bytes as snapshotting a bare one, the snapshot carries no
     * information and the readable id is strictly better. The two obvious
     * shortcuts both lie, in opposite directions — {@code hasItemMeta()} is
     * true for stacks whose meta is entirely default, and
     * {@code isSimilar()} is only as thorough as the server's item factory.
     * Amount is normalised away first: it belongs to the shop line, not the
     * item.
     */
    public static boolean isPlain(ItemStack stack) {
        try {
            ItemStack one = stack.clone();
            one.setAmount(1);
            return Arrays.equals(one.serializeAsBytes(),
                    new ItemStack(stack.getType()).serializeAsBytes());
        } catch (RuntimeException ex) {
            return !stack.hasItemMeta();  // can't serialise it; assume it's special
        }
    }

    /**
     * Whether a player's stack satisfies a sell line. Custom lines compare by
     * {@link ItemStack#isSimilar} against the snapshot — everything but the
     * amount — so a player can sell a partial stack of custom gear, and gear
     * that merely looks similar won't pass.
     */
    public static boolean matches(ItemStack stack, ShopOffer offer, Logger log) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (offer.isCustom()) {
            ItemStack wanted = decode(offer, log);
            return wanted != null && wanted.isSimilar(stack);
        }
        if (offer.isVanilla()) {
            Material material = Material.matchMaterial(offer.vanillaMaterial());
            // A plain-material line takes plain items only: an admin who put
            // COOKED_COD on the shelf did not mean "and anything renamed".
            return material != null && stack.getType() == material && isPlain(stack);
        }
        Relic relic = Relic.of(stack);
        if (relic != null) {
            // Waking a relic costs Chronons; the artificer won't refund them.
            return relic.id().equals(offer.itemId()) && !Relic.isAwake(stack);
        }
        return offer.itemId().equals(DarkSeaItems.idOf(stack));
    }
}
