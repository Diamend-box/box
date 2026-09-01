package com.diamend.darksea.relic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The reliquary's rules, with no server attached: how big a captain's bag is,
 * what moves in and out of it, and whether the next slot is buyable. Every
 * method returns a new list rather than mutating — the caller writes the pair
 * back to {@link com.diamend.darksea.data.PlayerDataStore} in one go, so a
 * rejected move can never leave half a change on disk.
 *
 * <p>Ids are relic ids rather than {@link Relic} values because that is what
 * is persisted, and because an unknown id (a relic removed from a later build)
 * should sit harmlessly in a collection instead of crashing a menu.
 */
public final class ReliquaryMath {

    private ReliquaryMath() {
    }

    /** How many equip slots the captain has: the start, plus what they bought, capped. */
    public static int slots(int startSlots, int maxSlots, int bought) {
        return Math.min(Math.max(startSlots, maxSlots), Math.max(1, startSlots) + Math.max(0, bought));
    }

    /**
     * The equipped list as it should actually be read: duplicates dropped,
     * anything no longer in the collection dropped, and cut to the slot count.
     * Config can shrink a bag between sessions, so this runs on every read
     * rather than trusting what was stored.
     */
    public static List<String> effective(List<String> collection, List<String> equipped, int slots) {
        List<String> kept = new ArrayList<>();
        for (String id : new LinkedHashSet<>(equipped)) {
            if (kept.size() >= Math.max(0, slots)) {
                break;
            }
            if (collection.contains(id)) {
                kept.add(id);
            }
        }
        return List.copyOf(kept);
    }

    /** Files a relic away. Returns the collection unchanged if it is already in there. */
    public static List<String> deposit(List<String> collection, String relicId) {
        if (collection.contains(relicId)) {
            return List.copyOf(collection);
        }
        List<String> next = new ArrayList<>(collection);
        next.add(relicId);
        return List.copyOf(next);
    }

    /**
     * Puts a relic in a slot. Null when the move is not allowed — not owned,
     * already worn, or no free slot — so the caller can tell the player why
     * without duplicating the checks.
     */
    public static List<String> equip(List<String> collection, List<String> equipped,
                                     String relicId, int slots) {
        if (!collection.contains(relicId) || equipped.contains(relicId) || equipped.size() >= slots) {
            return null;
        }
        List<String> next = new ArrayList<>(equipped);
        next.add(relicId);
        return List.copyOf(next);
    }

    /** Takes a relic out of its slot. It stays in the collection. */
    public static List<String> unequip(List<String> equipped, String relicId) {
        List<String> next = new ArrayList<>(equipped);
        next.remove(relicId);
        return List.copyOf(next);
    }

    /** Whether there is another rung to buy: a price exists and the cap is not reached. */
    public static boolean canUpgrade(int slots, int maxSlots, boolean priced) {
        return priced && slots < maxSlots;
    }
}
