package com.diamend.boxtutorial.items;

import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The items the tutorial hands out, and the ability to replace any of them with
 * yours.
 *
 * <p>Every reward is a named slot — {@code sword}, {@code charm} — with a plain
 * definition in config so the plugin works out of the box. Point a slot at your
 * own item with {@code /tutorial items} (hold it, click the slot) and from then
 * on that is what the trade hands over.
 *
 * <p>Bindings are stored as the item's own bytes rather than as a material and
 * a name. A custom item from another plugin carries data components nothing
 * here understands — model data, plugin tags, attributes — and the only honest
 * way to give back exactly what was handed in is to keep the bytes.
 */
public class ItemRegistry {

    private final Plugin plugin;
    private final File file;

    /** Stamped on everything the tutorial hands out, so it knows its own. */
    private final NamespacedKey stamp;

    /** Slot ids in the order they're declared, for a stable menu. */
    private final List<String> ids = new ArrayList<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Map<String, ItemStack> defaults = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> stats = new LinkedHashMap<>();
    private final Map<String, ItemStack> bound = new LinkedHashMap<>();

    private final List<String> warnings = new ArrayList<>();

    public ItemRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "items.yml");
        this.stamp = new NamespacedKey(plugin, "item_id");
        load();
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public final void load() {
        ids.clear();
        labels.clear();
        defaults.clear();
        stats.clear();
        warnings.clear();
        readDefinitions(plugin.getConfig().getConfigurationSection("items"));
        readBindings();
        for (String warning : warnings) {
            plugin.getLogger().warning("items config: " + warning);
        }
    }

    private void readDefinitions(ConfigurationSection section) {
        if (section == null) {
            warnings.add("no 'items:' section — the tutorial has nothing to give out");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            String id = Text.lower(key).trim();
            if (entry == null) {
                warnings.add("item '" + key + "' is not a block of settings; skipped");
                continue;
            }
            Material material = Items.material(entry.getString("material"), null);
            if (material == null) {
                warnings.add("item '" + id + "' has no usable material; skipped");
                continue;
            }
            ItemStack item = Items.text(material,
                    entry.getString("name", Text.prettify(id)),
                    entry.getStringList("lore"),
                    entry.getBoolean("glow", false));
            enchant(id, item, entry.getConfigurationSection("enchants"));

            ids.add(id);
            labels.put(id, entry.getString("label", Text.prettify(id)));
            defaults.put(id, item);

            Map<String, Double> values = new LinkedHashMap<>();
            ConfigurationSection statSection = entry.getConfigurationSection("stats");
            if (statSection != null) {
                for (String stat : statSection.getKeys(false)) {
                    values.put(Text.lower(stat), statSection.getDouble(stat));
                }
            }
            if (!values.isEmpty()) {
                stats.put(id, values);
            }
        }
    }

    /**
     * Applies a slot's {@code enchants:} block — {@code efficiency: 2}.
     *
     * <p>Only ever touches the built-in default. An item somebody bound arrives
     * with whatever enchantments it already had, and adding to those would be
     * this plugin quietly editing a staff member's item.
     */
    private void enchant(String id, ItemStack item, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            Enchantment enchantment = enchantment(name);
            if (enchantment == null) {
                warnings.add("item '" + id + "' asks for an enchantment called '" + name
                        + "', which this server doesn't have; ignored");
                continue;
            }
            meta.addEnchant(enchantment, Math.max(1, section.getInt(name, 1)), true);
        }
        item.setItemMeta(meta);
    }

    /**
     * Finds an enchantment by name, with or without a namespace.
     *
     * <p>Looked up in the registry rather than named as a constant, for the same
     * reason the attributes are: the constants have moved around, and a server
     * on a slightly different version should lose one enchantment rather than
     * the whole plugin.
     */
    private Enchantment enchantment(String name) {
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        try {
            NamespacedKey enchantKey = NamespacedKey.fromString(
                    cleaned.indexOf(':') >= 0 ? cleaned : "minecraft:" + cleaned);
            return enchantKey == null ? null : Registry.ENCHANTMENT.get(enchantKey);
        } catch (Throwable ex) {
            return null;
        }
    }

    private void readBindings() {
        bound.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read items.yml", ex);
            return;
        }
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Object raw = section.get(id);
            try {
                ItemStack item = null;
                if (raw instanceof ItemStack stack) {
                    item = stack;                       // written by the YAML fallback
                } else if (raw instanceof String encoded && !encoded.isBlank()) {
                    item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                }
                if (item != null && !item.getType().isAir()) {
                    bound.put(Text.lower(id), item);
                }
            } catch (Throwable ex) {
                warnings.add("the saved item for '" + id + "' could not be read back"
                        + " (a version change?); the default is being used");
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, ItemStack> entry : bound.entrySet()) {
            try {
                // The item's own bytes keep everything — model data, plugin
                // tags, components this plugin has never heard of.
                config.set("items." + entry.getKey(),
                        Base64.getEncoder().encodeToString(entry.getValue().serializeAsBytes()));
            } catch (Throwable ex) {
                // Some server implementations don't offer that; the ordinary
                // YAML form still carries name, lore, enchants and meta.
                config.set("items." + entry.getKey(), entry.getValue());
            }
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create the BoxTutorial data folder.");
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write items.yml", ex);
        }
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public List<String> ids() {
        return Collections.unmodifiableList(ids);
    }

    public boolean isKnown(String id) {
        return id != null && (defaults.containsKey(Text.lower(id)) || bound.containsKey(Text.lower(id)));
    }

    public String label(String id) {
        return labels.getOrDefault(Text.lower(id), Text.prettify(id));
    }

    /** True when a staff member has pointed this slot at their own item. */
    public boolean isBound(String id) {
        return bound.containsKey(Text.lower(id));
    }

    /**
     * The item this slot currently means: the bound one if there is one, the
     * configured default otherwise, with any configured stats applied.
     *
     * @return a copy, safe to hand out, or null when the slot isn't defined
     */
    public ItemStack get(String id) {
        String key = Text.lower(id);
        ItemStack base = base(key);
        if (base == null) {
            return null;
        }
        ItemStack copy = base.clone();
        applyStats(key, copy);
        mark(key, copy);
        return copy;
    }

    /** The slot's item as configured or bound, without the plugin's mark. */
    private ItemStack base(String key) {
        ItemStack bound = this.bound.get(key);
        return bound != null ? bound : defaults.get(key);
    }

    /** The same item in a given quantity. */
    public ItemStack get(String id, int amount) {
        ItemStack item = get(id);
        if (item != null) {
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        }
        return item;
    }

    /**
     * True when that stack is this slot's item — ignoring how many.
     *
     * <p>Anything the tutorial handed out carries a mark naming its slot, and
     * that is the answer when it's there: it survives a name change, tells two
     * slots bound to the same material apart, and can't be forged by renaming
     * an anvil. Items that came from somewhere else are compared on what a
     * player can actually see — the type, the name and the lore — because
     * that's the only claim worth making about an item this plugin didn't
     * create.
     */
    public boolean matches(String id, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String key = Text.lower(id);
        String marked = markOf(stack);
        if (!marked.isEmpty()) {
            return marked.equals(key);
        }
        ItemStack canonical = base(key);
        return canonical != null && looksLike(canonical, stack);
    }

    /** Writes the slot id onto the item. */
    private void mark(String id, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        try {
            meta.getPersistentDataContainer().set(stamp, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {
            // Without the mark it still matches on how it looks.
        }
    }

    /** The slot this item was handed out as, or "" if it wasn't. */
    private String markOf(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return "";
            }
            String value = meta.getPersistentDataContainer()
                    .get(stamp, PersistentDataType.STRING);
            return value == null ? "" : Text.lower(value);
        } catch (Throwable ex) {
            return "";
        }
    }

    /** Same type, same name, same lore — as much as a player can tell. */
    private static boolean looksLike(ItemStack canonical, ItemStack other) {
        if (canonical.getType() != other.getType()) {
            return false;
        }
        return name(canonical).equals(name(other)) && lore(canonical).equals(lore(other));
    }

    private static String name(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
    }

    private static List<String> lore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lines = meta == null ? null : meta.lore();
        if (lines == null) {
            return List.of();
        }
        List<String> plain = new ArrayList<>();
        for (Component line : lines) {
            plain.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return plain;
    }

    /** Points a slot at a copy of this item. Nothing is taken from anybody. */
    public void bind(String id, ItemStack item) {
        if (id == null || item == null || item.getType().isAir()) {
            return;
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        // Binding one of our own items back (they can take a copy from the
        // menu) must not store the mark, or the slot ends up defined in terms
        // of the slot it used to be.
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            try {
                meta.getPersistentDataContainer().remove(stamp);
                copy.setItemMeta(meta);
            } catch (Throwable ignored) {
                // Nothing to strip.
            }
        }
        bound.put(Text.lower(id), copy);
        save();
    }

    /** Back to the configured default. */
    public void clear(String id) {
        if (bound.remove(Text.lower(id)) != null) {
            save();
        }
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    // ------------------------------------------------------------------
    // Stats
    // ------------------------------------------------------------------

    /** The stats configured for a slot, e.g. {@code max_health: 4}. */
    public Map<String, Double> statsFor(String id) {
        return Collections.unmodifiableMap(stats.getOrDefault(Text.lower(id), Map.of()));
    }

    /**
     * Writes the configured stats onto the item as off-hand attribute
     * modifiers.
     *
     * <p>Vanilla does the rest: hold it in your off hand and the numbers apply,
     * put it away and they don't, and the tooltip says so without the plugin
     * having to explain anything. No ticking task, and nothing to leak if the
     * server stops while somebody is holding one.
     */
    private void applyStats(String id, ItemStack item) {
        Map<String, Double> values = stats.get(id);
        if (values == null || values.isEmpty()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        // Drop any modifier this plugin added before, so re-binding an item the
        // plugin itself gave out can't stack the stats up twice.
        Multimap<Attribute, AttributeModifier> kept = LinkedHashMultimap.create();
        Multimap<Attribute, AttributeModifier> existing = meta.getAttributeModifiers();
        if (existing != null) {
            for (Map.Entry<Attribute, AttributeModifier> entry : existing.entries()) {
                if (!isOurs(entry.getValue())) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
        }
        meta.setAttributeModifiers(kept);

        for (Map.Entry<String, Double> stat : values.entrySet()) {
            Attribute attribute = attribute(stat.getKey());
            if (attribute == null) {
                continue;
            }
            meta.addAttributeModifier(attribute, new AttributeModifier(
                    key(id, stat.getKey()), stat.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.OFFHAND));
        }
        item.setItemMeta(meta);
    }

    /** Our own modifiers carry the plugin's namespace; everything else is the item's. */
    private boolean isOurs(AttributeModifier modifier) {
        try {
            NamespacedKey modifierKey = modifier.getKey();
            return modifierKey != null
                    && modifierKey.getNamespace().equalsIgnoreCase(plugin.getName());
        } catch (Throwable ex) {
            return false;
        }
    }

    private NamespacedKey key(String id, String stat) {
        return new NamespacedKey(plugin, "tutorial_" + clean(id) + "_" + clean(stat));
    }

    private static String clean(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    /**
     * Finds an attribute by name.
     *
     * <p>Looked up in the registry rather than named as a constant: the
     * attribute constants were renamed in 1.21.2 ({@code generic.max_health}
     * became {@code max_health}), so both spellings are tried and a server on
     * either one works.
     */
    private Attribute attribute(String name) {
        String cleaned = clean(name);
        Attribute found = lookup(cleaned);
        if (found == null && !cleaned.startsWith("generic.")) {
            found = lookup("generic." + cleaned);
        }
        if (found == null && cleaned.startsWith("generic.")) {
            found = lookup(cleaned.substring("generic.".length()));
        }
        if (found == null) {
            warnings.add("no attribute called '" + name + "' on this server; that stat is ignored");
        }
        return found;
    }

    private Attribute lookup(String name) {
        try {
            NamespacedKey attributeKey = NamespacedKey.fromString("minecraft:" + name);
            return attributeKey == null ? null : Registry.ATTRIBUTE.get(attributeKey);
        } catch (Throwable ex) {
            return null;
        }
    }
}
