package com.diamend.darksea.armor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Order's own garb — Vironic cultist armor, dropped by the cultists who
 * wear it (never found in chests). Each rank's set carries the sea-armor
 * TIER of the ring that rank first appears in (Initiate 1 → Lord 4), so a
 * full set keeps the Mariphage out exactly like sea armor of that tier —
 * through the same {@link SeaArmor#TIER_KEY} tag and protection path.
 *
 * The trade: it's cloth. Leather's weak armor points against teeth and
 * claws, in exchange for the Order's blessing — a full set adds
 * {@code 2 x tier} melee damage while in the Dark Sea (see the combat
 * listener; math in {@link #setBonusDamage}).
 */
public final class VironicArmor {

    /** String PDC tag marking a piece as Vironic set gear. */
    public static final NamespacedKey SET_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:set"));
    public static final String SET_ID = "vironic";

    /** Rank per tier — locked to mobs.yml's cultist ladder by a unit test. */
    private static final Map<Integer, String> RANKS = Map.of(
            1, "Initiate", 2, "Acolyte", 3, "Templar", 4, "Lord");

    /** Slot garb names (Wyatt's naming; chest piece upgraded bodice → vestments). */
    private static final Map<SeaArmor.Piece, String> SLOT_NAMES = Map.of(
            SeaArmor.Piece.HELMET, "Hood",
            SeaArmor.Piece.CHESTPLATE, "Vestments",
            SeaArmor.Piece.LEGGINGS, "Robes",
            SeaArmor.Piece.BOOTS, "Boots");

    /** Robe dye deepens with rank: violet down into monolith-dark purple. */
    private static final Map<Integer, Color> DYES = Map.of(
            1, Color.fromRGB(122, 84, 161),
            2, Color.fromRGB(94, 58, 135),
            3, Color.fromRGB(64, 34, 104),
            4, Color.fromRGB(36, 14, 66));

    private static final Map<Integer, String> NAME_COLORS = Map.of(
            1, "#9b7bc4", 2, "#7e57c2", 3, "#5e35b1", 4, "#8e24aa");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private VironicArmor() {
    }

    public static String rankName(int tier) {
        return RANKS.get(tier);
    }

    /** One dyed, tagged piece of a rank's set, or null for an unknown tier. */
    public static ItemStack createPiece(int tier, SeaArmor.Piece piece) {
        String rank = RANKS.get(tier);
        if (rank == null) {
            return null;
        }
        Material material = Material.matchMaterial("LEATHER_" + piece.name());
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(
                "<color:" + NAME_COLORS.get(tier) + ">Vironic " + rank + " "
                        + SLOT_NAMES.get(piece) + "</color>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>Dark Sea protection tier <aqua>" + tier + "</aqua>")),
                noItalic(MM.deserialize("<gray>Full set: <light_purple>+" + (2 * tier)
                        + " damage</light_purple> <gray>in the Dark Sea.</gray>")),
                noItalic(MM.deserialize("<dark_gray>The plague knows its keepers.</dark_gray>"))));
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(DYES.get(tier));
        }
        meta.getPersistentDataContainer().set(SeaArmor.TIER_KEY, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(SET_KEY, PersistentDataType.STRING, SET_ID);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isVironic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return SET_ID.equals(item.getItemMeta().getPersistentDataContainer()
                .get(SET_KEY, PersistentDataType.STRING));
    }

    /**
     * The full-set melee bonus: {@code 2 x lowest tier} when all four slots
     * are Vironic, 0 otherwise. Mirrors {@link SeaArmor#effectiveTier}'s
     * lowest-piece rule — one borrowed boot breaks the blessing.
     */
    public static double setBonusDamage(ItemStack[] armorContents) {
        int minTier = Integer.MAX_VALUE;
        int pieces = 0;
        for (ItemStack item : armorContents) {
            if (!isVironic(item)) {
                return 0;
            }
            minTier = Math.min(minTier, SeaArmor.tierOf(item));
            pieces++;
        }
        if (pieces < 4 || minTier <= 0 || minTier == Integer.MAX_VALUE) {
            return 0;
        }
        return 2.0 * minTier;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
