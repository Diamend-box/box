package com.diamend.boxcore.boost;

import com.diamend.boxcore.util.Durations;
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
 * The consumable boost item: right-click to start the boost it describes.
 *
 * <p>What the item does is written <em>on the item</em>, not looked up from
 * config by id. An item already in someone's ender chest was bought or won on
 * the terms printed on it, and re-tuning the config afterwards must not quietly
 * change or void it. The id is carried too, but only for display and for
 * knowing where it came from.
 */
public final class BoostItems {

    /** How a boost item looks. Every field is optional in config. */
    public record Appearance(Material material,
                             String name,
                             List<String> lore,
                             int modelData,
                             boolean glow) {

        public static Appearance defaults() {
            return new Appearance(Material.EXPERIENCE_BOTTLE, null, null, 0, true);
        }

        public Material materialOr(Material fallback) {
            return material == null || material.isAir() ? fallback : material;
        }
    }

    /**
     * What one boost item grants.
     *
     * @param global whether using it boosts the whole server rather than the
     *               player who used it
     */
    public record Payload(String id,
                          List<BoostType> types,
                          double multiplier,
                          long durationMillis,
                          boolean global) {

        /** A personal boost item — the ordinary kind. */
        public Payload(String id, List<BoostType> types, double multiplier, long durationMillis) {
            this(id, types, multiplier, durationMillis, false);
        }

        /** {@code "Ore drops & Collections"}, or the single type's name. */
        public String typeNames() {
            List<String> names = new ArrayList<>();
            for (BoostType type : types) {
                names.add(type.display());
            }
            return String.join(" & ", names);
        }
    }

    private final NamespacedKey typesKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey idKey;
    private final NamespacedKey globalKey;

    public BoostItems(Plugin plugin) {
        this.typesKey = new NamespacedKey(plugin, "boost_types");
        this.multiplierKey = new NamespacedKey(plugin, "boost_multiplier");
        this.durationKey = new NamespacedKey(plugin, "boost_duration");
        this.idKey = new NamespacedKey(plugin, "boost_id");
        this.globalKey = new NamespacedKey(plugin, "boost_global");
    }

    /** Builds a stack of boost items. */
    public ItemStack create(Payload payload, Appearance appearance, int amount) {
        if (payload == null || payload.types().isEmpty()) {
            return null;
        }
        Appearance look = appearance == null ? Appearance.defaults() : appearance;
        ItemStack item = new ItemStack(look.materialOr(Material.EXPERIENCE_BOTTLE),
                Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (BoostType type : payload.types()) {
            ids.add(type.id());
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(typesKey, PersistentDataType.STRING, String.join(",", ids));
        data.set(multiplierKey, PersistentDataType.DOUBLE, payload.multiplier());
        data.set(durationKey, PersistentDataType.LONG, payload.durationMillis());
        data.set(idKey, PersistentDataType.STRING, payload.id() == null ? "" : payload.id());
        data.set(globalKey, PersistentDataType.BYTE, (byte) (payload.global() ? 1 : 0));

        meta.displayName(Text.item(resolve(
                look.name() == null
                        ? (payload.global() ? "<gold>Server " : "<light_purple>")
                                + Text.decimal(payload.multiplier()) + "x "
                                + payload.typeNames() + " Boost"
                        : look.name(),
                payload)));
        meta.lore(lore(look, payload));
        if (look.modelData() > 0) {
            meta.setCustomModelData(look.modelData());
        }
        if (look.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> lore(Appearance look, Payload payload) {
        List<String> lines = look.lore();
        if (lines == null || lines.isEmpty()) {
            // Spending a server-wide boost is not undoable and not private.
            // The item says so before it is used, not after.
            lines = payload.global()
                    ? List.of(
                            "<gray>Multiplies <white><type></white> by <white><multiplier>x</white>",
                            "<gray>for <white><duration></white>, <gold>for everyone online<gray>.",
                            "",
                            "<dark_gray>Right-click to start it for the server.")
                    : List.of(
                            "<gray>Multiplies <white><type></white> by <white><multiplier>x</white>",
                            "<gray>for <white><duration></white>.",
                            "",
                            "<dark_gray>Right-click to use.");
        }
        List<Component> parsed = new ArrayList<>();
        for (String line : lines) {
            parsed.add(Text.item(resolve(line, payload)));
        }
        return parsed;
    }

    private String resolve(String text, Payload payload) {
        return text.replace("<multiplier>", Text.decimal(payload.multiplier()))
                .replace("<duration>", Durations.format(payload.durationMillis()))
                .replace("<type>", payload.typeNames())
                .replace("<id>", payload.id() == null ? "" : payload.id());
    }

    /** Reads what an item grants, or null when it is not a boost item. */
    public Payload read(ItemStack item) {
        PersistentDataContainer data = data(item);
        if (data == null) {
            return null;
        }
        String stored = data.get(typesKey, PersistentDataType.STRING);
        Double multiplier = data.get(multiplierKey, PersistentDataType.DOUBLE);
        Long duration = data.get(durationKey, PersistentDataType.LONG);
        if (stored == null || stored.isBlank() || multiplier == null || duration == null) {
            return null;
        }
        List<BoostType> types = new ArrayList<>();
        for (String part : stored.split(",")) {
            BoostType type = BoostType.parse(part.trim().toLowerCase(Locale.ROOT));
            if (type != null && !types.contains(type)) {
                types.add(type);
            }
        }
        if (types.isEmpty() || multiplier <= 1.0 || duration <= 0) {
            return null;
        }
        String id = data.get(idKey, PersistentDataType.STRING);
        // Items written before global boosts existed carry no flag, and every
        // one of them was personal.
        Byte global = data.get(globalKey, PersistentDataType.BYTE);
        return new Payload(id == null ? "" : id, types, multiplier, duration,
                global != null && global != 0);
    }

    public boolean isBoostItem(ItemStack item) {
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
