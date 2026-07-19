package com.diamend.darksea.relic;

import com.diamend.darksea.item.DarkSeaItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Naxome relics — Loot 2.0's treasure band, now with a life cycle.
 * A relic drops DORMANT: a collectible, nothing more. Carried back to the
 * refugees at the calm center and paid for in Chronons, it wakes — and
 * grants its boost for as long as it rides in your inventory (capped at
 * {@code relics.max-active} at once, first slots win).
 *
 * The junk-band named items (Rotted Rigging, Vigil Candle...) are NOT
 * relics — flavor salvage stays flavor salvage.
 */
public enum Relic {

    TRADE_COIN("relic_trade_coin", 1, 50, Boost.SPEED, Material.GOLD_NUGGET,
            "<gold>Naxian Trade Coin</gold>",
            List.of("<gray>Struck for a market that drowned</gray>",
                    "<gray>with everyone in it.</gray>"),
            "Merchant's Stride — +10% speed"),

    HARBOR_BELL("relic_harbor_bell", 2, 100, Boost.BOAT, Material.NAUTILUS_SHELL,
            "<aqua>Harbor Bell of the Naxome</aqua>",
            List.of("<gray>It rang when ships came home.</gray>",
                    "<gray>It has been quiet a long time.</gray>"),
            "Homeward Wind — +15% boat speed"),

    MARIPHAGE_SAMPLE("relic_mariphage_sample", 3, 150, Boost.DAMAGE, Material.DRAGON_BREATH,
            "<dark_purple>Sealed Mariphage Sample</dark_purple>",
            List.of("<gray>Templar's stock, still stoppered.</gray>",
                    "<gray>The Order will want it back.</gray>"),
            "Plaguebearer's Edge — +1 damage"),

    MONOLITH_SPLINTER("relic_monolith_splinter", 4, 200, Boost.ARMOR, Material.ECHO_SHARD,
            "<dark_purple>Monolith Splinter</dark_purple>",
            List.of("<gray>It hums when held. Not a sound —</gray>",
                    "<gray>a word, almost.</gray>"),
            "Stone's Patience — +3 armor"),

    NAXOME_HEART("relic_naxome_heart", 4, 200, Boost.REGEN, Material.HEART_OF_THE_SEA,
            "<aqua>Heart of the Naxome</aqua>",
            List.of("<gray>Everything they were,</gray>",
                    "<gray>pressed into one cold stone.</gray>"),
            "Naxome's Mercy — slow regeneration"),

    /** Mariphage Core exclusive — never in a chest. */
    MARIPHAGE_VECTOR("relic_mariphage_vector", 4, 250, Boost.VECTOR, Material.FERMENTED_SPIDER_EYE,
            "<dark_purple>Vector of the Mariphage</dark_purple>",
            List.of("<gray>Cut from a Core. It still wants</gray>",
                    "<gray>to spread — let it, carefully.</gray>"),
            "Carrier — your strikes infect: Poison II, Slowness");

    /** What an awake relic does. Application lives in {@link RelicService}. */
    public enum Boost {
        SPEED, BOAT, DAMAGE, ARMOR, REGEN, VECTOR
    }

    /** Integer PDC tag: 1 once the refugees have woken the relic. */
    public static final NamespacedKey AWAKE_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:relic_awake"));

    /** Homeward Wind: the Harbor Bell's boat speed multiplier while active. */
    public static final double BOAT_BOOST_MULTIPLIER = 1.15;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DORMANT_LINE =
            "<dark_gray>Dormant. The refugees of the calm</dark_gray>";
    private static final String DORMANT_LINE_2 =
            "<dark_gray>center may know how to wake it.</dark_gray>";

    private final String id;
    private final int tier;
    private final int reviveCost;
    private final Boost boost;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final String boostLine;

    Relic(String id, int tier, int reviveCost, Boost boost, Material material,
          String displayName, List<String> lore, String boostLine) {
        this.id = id;
        this.tier = tier;
        this.reviveCost = reviveCost;
        this.boost = boost;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.boostLine = boostLine;
    }

    public String id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    /** Chronons the refugees ask to wake this relic. */
    public int reviveCost() {
        return reviveCost;
    }

    public Boost boost() {
        return boost;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public String boostLine() {
        return boostLine;
    }

    public static Relic byId(String id) {
        for (Relic relic : values()) {
            if (relic.id.equals(id)) {
                return relic;
            }
        }
        return null;
    }

    /** The relic an item is, or null for anything else. */
    public static Relic of(ItemStack item) {
        String id = DarkSeaItems.idOf(item);
        return id != null ? byId(id) : null;
    }

    public static boolean isAwake(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Integer awake = item.getItemMeta().getPersistentDataContainer()
                .get(AWAKE_KEY, PersistentDataType.INTEGER);
        return awake != null && awake == 1;
    }

    // ------------------------------------------------------------------
    // Creation and state change
    // ------------------------------------------------------------------

    /** A dormant relic, as chests and the Core drop them. */
    public ItemStack createDormant() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(displayName)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(noItalic(MM.deserialize(line)));
        }
        lines.add(noItalic(MM.deserialize(DORMANT_LINE)));
        lines.add(noItalic(MM.deserialize(DORMANT_LINE_2)));
        meta.lore(lines);
        meta.getPersistentDataContainer().set(DarkSeaItems.ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** Wakes a relic in place: PDC flag plus the boost line replacing the dormant hint. */
    public void wake(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(noItalic(MM.deserialize(line)));
        }
        lines.add(noItalic(MM.deserialize("<green>Awake — " + boostLine + "</green>")));
        lines.add(noItalic(MM.deserialize("<dark_gray>Works from your inventory.</dark_gray>")));
        meta.lore(lines);
        meta.getPersistentDataContainer().set(AWAKE_KEY, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
