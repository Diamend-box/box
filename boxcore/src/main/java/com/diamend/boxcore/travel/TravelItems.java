package com.diamend.boxcore.travel;

import com.diamend.boxcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The consumable travel item: right-click to reach somewhere.
 *
 * <p>Two different things sell well, so both exist:
 *
 * <ul>
 *   <li>A <b>ticket</b> ({@link Mode#TRAVEL}) takes you there once. Spent on
 *       arrival, not on use, so a trip cut short by a sword doesn't cost you the
 *       ticket as well as the fight.</li>
 *   <li>A <b>map</b> ({@link Mode#UNLOCK}) puts the place on your travel list
 *       for good, exactly as if you had walked to it.</li>
 * </ul>
 *
 * <p>Like a boost item, what it does is written <em>on the item</em> rather than
 * looked up from config by id, so re-tuning config later can't quietly change or
 * void one that is already in somebody's ender chest. The destination is stored
 * by warp id, because a destination that has been moved or renamed is still the
 * same destination — deleting it is the only thing that voids a ticket, and
 * there is nothing sensible to do about that anyway.
 */
public final class TravelItems {

    /** What using one does. */
    public enum Mode {
        /** One trip, now. */
        TRAVEL,
        /** Adds the place to your travel list permanently. */
        UNLOCK;

        public static Mode parse(String raw) {
            Mode matched = match(raw);
            return matched == null ? TRAVEL : matched;
        }

        /**
         * Like {@link #parse(String)}, but says so when the word isn't a mode
         * at all. Command parsing needs the difference: {@code /box warp item
         * mines Notch} must read Notch as a player, not quietly hand out a
         * ticket because an unrecognised word fell through to the default.
         */
        public static Mode match(String raw) {
            if (raw == null) {
                return null;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "unlock", "discover", "map", "find" -> UNLOCK;
                case "travel", "ticket", "trip", "warp" -> TRAVEL;
                default -> null;
            };
        }

        public String display() {
            return this == UNLOCK ? "Map" : "Ticket";
        }
    }

    /** The warp id that means "every destination this player may see". */
    public static final String ANY = "any";

    /** How a travel item looks. Every field is optional in config. */
    public record Appearance(Material material,
                             String name,
                             List<String> lore,
                             int modelData,
                             boolean glow) {

        public static Appearance defaults() {
            return defaults(Mode.TRAVEL);
        }

        /** The plain look for a mode: a map for unlocking, paper for a trip. */
        public static Appearance defaults(Mode mode) {
            return new Appearance(mode == Mode.UNLOCK ? Material.FILLED_MAP : Material.PAPER,
                    null, null, 0, true);
        }

        public Material materialOr(Material fallback) {
            return material == null || material.isAir() ? fallback : material;
        }
    }

    /**
     * What one travel item grants.
     *
     * @param id     the config entry it was made from, for display
     * @param warpId the destination, or {@link #ANY} for every one they may see
     * @param mode   whether it travels once or unlocks for good
     */
    public record Payload(String id, String warpId, Mode mode) {

        public boolean isAny() {
            return ANY.equalsIgnoreCase(warpId);
        }
    }

    private final NamespacedKey idKey;
    private final NamespacedKey warpKey;
    private final NamespacedKey modeKey;

    public TravelItems(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "travel_id");
        this.warpKey = new NamespacedKey(plugin, "travel_warp");
        this.modeKey = new NamespacedKey(plugin, "travel_mode");
    }

    /**
     * Builds a stack of travel items.
     *
     * @param warp the destination as it exists now, for the name and lore only —
     *             null is fine, and the id is written either way
     */
    public ItemStack create(Payload payload, Appearance appearance, int amount, Warp warp) {
        if (payload == null || payload.warpId() == null || payload.warpId().isBlank()) {
            return null;
        }
        Appearance look = appearance == null ? Appearance.defaults() : appearance;
        ItemStack item = new ItemStack(look.materialOr(Material.PAPER), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, payload.id() == null ? "" : payload.id());
        data.set(warpKey, PersistentDataType.STRING, payload.warpId());
        data.set(modeKey, PersistentDataType.STRING, payload.mode().name());

        String where = destinationName(payload, warp);
        meta.displayName(Text.item(resolve(look.name() == null
                ? (payload.mode() == Mode.UNLOCK
                        ? "<aqua>Map: <white><warp>"
                        : "<aqua>Travel Ticket <gray>(<warp>)")
                : look.name(), payload, where)));
        meta.lore(lore(look, payload, where));
        if (look.modelData() > 0) {
            meta.setCustomModelData(look.modelData());
        }
        if (look.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** What to call the destination on the item. */
    private String destinationName(Payload payload, Warp warp) {
        if (payload.isAny()) {
            return "Everywhere";
        }
        return warp == null ? payload.warpId() : Text.plain(warp.display());
    }

    private List<Component> lore(Appearance look, Payload payload, String where) {
        List<String> lines = look.lore();
        if (lines == null || lines.isEmpty()) {
            lines = switch (payload.mode()) {
                case UNLOCK -> payload.isAny()
                        ? List.of("<gray>Adds every destination to your",
                                "<gray>travel list, for good.",
                                "",
                                "<dark_gray>Right-click to use.")
                        : List.of("<gray>Adds <white><warp></white> to your",
                                "<gray>travel list, for good.",
                                "",
                                "<dark_gray>Right-click to use.");
                case TRAVEL -> List.of("<gray>Takes you to <white><warp></white>, once.",
                        "<gray>Spent when you arrive.",
                        "",
                        "<dark_gray>Right-click to use.");
            };
        }
        List<Component> parsed = new ArrayList<>();
        for (String line : lines) {
            parsed.add(Text.item(resolve(line, payload, where)));
        }
        return parsed;
    }

    private String resolve(String text, Payload payload, String where) {
        return text.replace("<warp>", where)
                .replace("<mode>", payload.mode().display())
                .replace("<id>", payload.id() == null ? "" : payload.id());
    }

    /** Reads what an item grants, or null when it is not a travel item. */
    public Payload read(ItemStack item) {
        PersistentDataContainer data = data(item);
        if (data == null) {
            return null;
        }
        String warpId = data.get(warpKey, PersistentDataType.STRING);
        if (warpId == null || warpId.isBlank()) {
            return null;
        }
        String id = data.get(idKey, PersistentDataType.STRING);
        String mode = data.get(modeKey, PersistentDataType.STRING);
        return new Payload(id == null ? "" : id, warpId, Mode.parse(mode));
    }

    public boolean isTravelItem(ItemStack item) {
        return read(item) != null;
    }

    private PersistentDataContainer data(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}
