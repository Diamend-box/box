package com.diamend.boxcore.collection;

import com.diamend.boxcore.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code collections.yml}.
 *
 * <p>Builds a material → collections index up front so the hot path (every
 * block broken, every mob killed) is a map lookup rather than a scan.
 */
public class CollectionManager {

    private final Plugin plugin;
    private final Map<String, CollectionCategory> categories = new LinkedHashMap<>();
    private final Map<String, ItemCollection> collections = new LinkedHashMap<>();
    private final Map<Material, List<ItemCollection>> byMaterial = new EnumMap<>(Material.class);
    private final List<String> warnings = new ArrayList<>();

    public CollectionManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        collections.clear();
        byMaterial.clear();
        warnings.clear();

        File file = new File(plugin.getDataFolder(), "collections.yml");
        if (!file.exists()) {
            plugin.saveResource("collections.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection categorySection = config.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String rawId : categorySection.getKeys(false)) {
                ConfigurationSection section = categorySection.getConfigurationSection(rawId);
                if (section == null) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                CollectionCategory category = new CollectionCategory(id);
                category.setDisplay(section.getString("display", id));
                category.setIcon(Items.material(section.getString("icon"), Material.CHEST));
                category.setOrder(section.getInt("order", 0));
                categories.put(id, category);
            }
        }

        ConfigurationSection collectionSection = config.getConfigurationSection("collections");
        if (collectionSection == null) {
            plugin.getLogger().warning("collections.yml has no 'collections' section — nothing tracked.");
            return;
        }
        for (String rawId : collectionSection.getKeys(false)) {
            ConfigurationSection section = collectionSection.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            ItemCollection collection = read(rawId.toLowerCase(Locale.ROOT), section);
            // A collection with another source is fed by a module, so having no
            // materials is exactly right for it.
            if (collection.tracksItems() && collection.getMaterials().isEmpty()) {
                warn("collection '" + collection.getId() + "' tracks no valid items — skipped.");
                continue;
            }
            if (collection.getTiers().isEmpty()) {
                warn("collection '" + collection.getId() + "' has no tiers — it will count but never pay out.");
            }
            collections.put(collection.getId(), collection);
            if (collection.tracksItems()) {
                for (Material material : collection.getMaterials()) {
                    byMaterial.computeIfAbsent(material, key -> new ArrayList<>()).add(collection);
                }
            }
            if (!categories.containsKey(collection.getCategoryId())) {
                CollectionCategory implicit = new CollectionCategory(collection.getCategoryId());
                implicit.setDisplay(com.diamend.boxcore.util.Text.prettify(collection.getCategoryId()));
                implicit.setOrder(100);
                categories.put(implicit.getId(), implicit);
            }
        }

        for (String warning : warnings) {
            plugin.getLogger().warning("collections.yml: " + warning);
        }
        plugin.getLogger().info("Loaded " + collections.size() + " collection(s) in "
                + categories.size() + " category/categories.");
    }

    private void warn(String message) {
        warnings.add(message);
    }

    private ItemCollection read(String id, ConfigurationSection section) {
        ItemCollection collection = new ItemCollection(id);
        collection.setDisplay(section.getString("display", id));
        collection.setCategoryId(section.getString("category", "general").toLowerCase(Locale.ROOT));
        collection.setSource(section.getString("source", ItemCollection.SOURCE_ITEMS)
                .toLowerCase(Locale.ROOT));
        collection.setUnit(section.getString("unit", ""));

        for (String entry : section.getStringList("items")) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String value = entry.trim();
            if (value.startsWith("#")) {
                List<Material> tagged = resolveTag(value.substring(1));
                if (tagged.isEmpty()) {
                    warn("collection '" + id + "' references unknown tag '" + value + "'.");
                }
                collection.getMaterials().addAll(tagged);
                continue;
            }
            Material material = Items.material(value, null);
            if (material == null) {
                warn("collection '" + id + "' references unknown item '" + value + "'.");
                continue;
            }
            collection.getMaterials().add(material);
        }
        if (section.isString("icon")) {
            collection.setIcon(Items.material(section.getString("icon"), null));
        }

        int defaultPoints = section.getInt("points-per-tier", 1);
        for (Object raw : section.getList("tiers", List.of())) {
            CollectionTier tier = readTier(raw, defaultPoints, id);
            if (tier != null) {
                collection.getTiers().add(tier);
            }
        }
        collection.getTiers().sort(Comparator.comparingLong(CollectionTier::amount));
        return collection;
    }

    private CollectionTier readTier(Object raw, int defaultPoints, String collectionId) {
        if (raw instanceof Number number) {
            return new CollectionTier(number.longValue(), defaultPoints);
        }
        if (raw instanceof Map<?, ?> map) {
            long amount = longOf(map.get("amount"), -1);
            if (amount < 0) {
                warn("collection '" + collectionId + "' has a tier without an 'amount' — skipped.");
                return null;
            }
            int points = (int) longOf(map.get("points"), defaultPoints);
            int xp = (int) longOf(map.get("xp"), 0);
            Object message = map.get("message");
            List<String> commands = new ArrayList<>();
            if (map.get("commands") instanceof List<?> list) {
                for (Object command : list) {
                    if (command != null) {
                        commands.add(String.valueOf(command));
                    }
                }
            }
            return new CollectionTier(amount, points, xp,
                    message == null ? "" : String.valueOf(message), commands);
        }
        if (raw != null) {
            try {
                return new CollectionTier(Long.parseLong(String.valueOf(raw).trim()), defaultPoints);
            } catch (NumberFormatException ignored) {
                warn("collection '" + collectionId + "' has an unreadable tier '" + raw + "'.");
            }
        }
        return null;
    }

    private static long longOf(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Resolves an item/block tag reference, e.g. {@code logs},
     * {@code minecraft:logs} or {@code blocks/base_stone_overworld}.
     */
    private List<Material> resolveTag(String reference) {
        String registry = Tag.REGISTRY_ITEMS;
        String name = reference.trim().toLowerCase(Locale.ROOT);
        int slash = name.indexOf('/');
        if (slash > 0) {
            String prefix = name.substring(0, slash);
            if (prefix.equals("blocks") || prefix.equals("block")) {
                registry = Tag.REGISTRY_BLOCKS;
            }
            name = name.substring(slash + 1);
        }
        NamespacedKey key = name.contains(":") ? NamespacedKey.fromString(name) : NamespacedKey.minecraft(name);
        if (key == null) {
            return List.of();
        }
        try {
            Tag<Material> tag = Bukkit.getTag(registry, key, Material.class);
            if (tag == null && registry.equals(Tag.REGISTRY_ITEMS)) {
                tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
            }
            return tag == null ? List.of() : new ArrayList<>(tag.getValues());
        } catch (Throwable ex) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------

    public ItemCollection get(String id) {
        return id == null ? null : collections.get(id.toLowerCase(Locale.ROOT));
    }

    /** Collections fed by a material, or an empty list. */
    public List<ItemCollection> forMaterial(Material material) {
        return byMaterial.getOrDefault(material, List.of());
    }

    public boolean tracks(Material material) {
        return byMaterial.containsKey(material);
    }

    /**
     * How much of everything a player has actually gathered.
     *
     * <p>Not simply the profile's total: collections fed by a module rather
     * than by items — hours played, say — would otherwise be added to a figure
     * labelled "items gathered".
     */
    public long itemsCollected(com.diamend.boxcore.data.PlayerProfile profile) {
        long total = 0;
        for (Map.Entry<String, Long> entry : profile.getCollections().entrySet()) {
            ItemCollection collection = collections.get(entry.getKey());
            if (collection == null || collection.tracksItems()) {
                total += entry.getValue();
            }
        }
        return total;
    }

    /** The first collection fed by a named source, or null when none is configured. */
    public ItemCollection bySource(String source) {
        for (ItemCollection collection : collections.values()) {
            if (collection.getSource().equalsIgnoreCase(source)) {
                return collection;
            }
        }
        return null;
    }

    public CollectionCategory category(String id) {
        return categories.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    /** Categories in display order. */
    public List<CollectionCategory> categories() {
        List<CollectionCategory> list = new ArrayList<>(categories.values());
        list.sort(Comparator.comparingInt(CollectionCategory::getOrder)
                .thenComparing(CollectionCategory::getId));
        return list;
    }

    /** Collections belonging to a category, in config order. */
    public List<ItemCollection> inCategory(String categoryId) {
        List<ItemCollection> list = new ArrayList<>();
        for (ItemCollection collection : collections.values()) {
            if (collection.getCategoryId().equalsIgnoreCase(categoryId)) {
                list.add(collection);
            }
        }
        return list;
    }

    public List<ItemCollection> all() {
        return new ArrayList<>(collections.values());
    }

    public int count() {
        return collections.size();
    }

    public List<String> warnings() {
        return warnings;
    }
}
