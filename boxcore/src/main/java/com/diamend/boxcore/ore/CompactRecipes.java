package com.diamend.boxcore.ore;

import com.diamend.boxcore.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The compaction recipe book, stored in its own {@code compactor.yml}.
 *
 * <p>It lives apart from {@code config.yml} because the in-game editor rewrites
 * it. Saving a Bukkit configuration strips the comments around it, and
 * {@code config.yml} is mostly comments explaining itself — a file the server
 * owner reads. This one is a file the plugin writes, so losing formatting to a
 * save costs nothing.
 *
 * <p>A material may have at most one recipe. Two recipes for the same input
 * would leave auto-compaction guessing which one a slot meant, and guessing
 * with someone's inventory is not a thing worth being clever about.
 */
public class CompactRecipes {

    private final Plugin plugin;

    private final Map<String, CompactRecipe> byId = new LinkedHashMap<>();
    private final Map<Material, CompactRecipe> byInput = new EnumMap<>(Material.class);
    private final List<String> warnings = new ArrayList<>();

    public CompactRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "compactor.yml");
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Reads the recipe book, seeding it from the old {@code compressor.ores}
     * block the first time so an existing setup carries over untouched.
     */
    public void load() {
        byId.clear();
        byInput.clear();
        warnings.clear();

        File file = file();
        if (!file.exists()) {
            migrateFromConfig();
            save();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read compactor.yml", ex);
            return;
        }
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            CompactRecipe recipe = read(id, entry);
            if (recipe != null) {
                add(recipe);
            }
        }
    }

    private CompactRecipe read(String id, ConfigurationSection entry) {
        Material input = Material.matchMaterial(
                String.valueOf(entry.getString("input", "")).trim().toUpperCase(Locale.ROOT));
        if (input == null || input.isAir()) {
            warn("recipe '" + id + "' has an unknown input, skipping.");
            return null;
        }
        int amount = entry.getInt("amount", 64);
        if (amount < 2) {
            warn("recipe '" + id + "' compacts fewer than 2 items, skipping.");
            return null;
        }
        return new CompactRecipe(id, input, amount, readAppearance(id, entry, input));
    }

    /** Reads the optional {@code item:} block describing the compacted form. */
    private CompressedOre.Appearance readAppearance(String id, ConfigurationSection entry, Material input) {
        ConfigurationSection item = entry.getConfigurationSection("item");
        if (item == null) {
            return CompressedOre.Appearance.defaultFor(input);
        }
        Material skin = Items.material(item.getString("material"), input);
        if (skin.isBlock()) {
            // A placeable skin reopens the route the custom item exists to
            // close: place the block and the items leave the inventory without
            // anything counting them. Refuse the skin, not the recipe.
            warn("recipe '" + id + "' uses a placeable block (" + skin.name()
                    + ") as its item; falling back to " + input.name() + ".");
            skin = input;
        }
        List<String> lore = item.isList("lore") ? item.getStringList("lore") : null;
        return new CompressedOre.Appearance(
                skin,
                item.getString("name"),
                lore == null || lore.isEmpty() ? null : lore,
                Math.max(0, item.getInt("model-data", 0)),
                item.getBoolean("glow", true));
    }

    /**
     * Builds the first recipe book out of the old per-ore config.
     *
     * <p>Servers running the tier-gated compressor already have their ores,
     * ratios and skins written down. Reading them once means nobody has to
     * retype a working setup to move to the compactor.
     */
    private void migrateFromConfig() {
        int ratio = Math.max(2, plugin.getConfig().getInt("compressor.ratio", 64));
        ConfigurationSection ores = plugin.getConfig().getConfigurationSection("compressor.ores");
        if (ores == null) {
            return;
        }
        for (String key : ores.getKeys(false)) {
            Material input = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
            if (input == null || input.isAir()) {
                continue;
            }
            ConfigurationSection entry = ores.getConfigurationSection(key);
            String id = key.toLowerCase(Locale.ROOT);
            CompressedOre.Appearance look = entry == null
                    ? CompressedOre.Appearance.defaultFor(input)
                    : readAppearance(id, entry, input);
            add(new CompactRecipe(id, input, ratio, look));
        }
        plugin.getLogger().info("Seeded compactor.yml with " + byId.size()
                + " recipe(s) from the old compressor.ores config.");
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(List.of(
                "BoxCore — compaction recipes.",
                "",
                "Written by the in-game editor (/box compactor recipes). Hand edits",
                "work fine, but the editor rewrites this file, so anything you add",
                "around these values will not survive a save.",
                "",
                "  input       the material that folds up",
                "  amount      how many of it make one compacted unit",
                "  item        optional look for the unit: material, name, lore,",
                "              model-data, glow. The material cannot be a placeable",
                "              block — placing one would move items out of the",
                "              inventory without anything counting them."));
        for (CompactRecipe recipe : byId.values()) {
            String base = "recipes." + recipe.id() + ".";
            config.set(base + "input", recipe.input().name());
            config.set(base + "amount", recipe.amount());

            CompressedOre.Appearance look = recipe.appearance();
            config.set(base + "item.material", look.materialOr(recipe.input()).name());
            if (look.name() != null) {
                config.set(base + "item.name", look.name());
            }
            if (look.lore() != null && !look.lore().isEmpty()) {
                config.set(base + "item.lore", look.lore());
            }
            if (look.modelData() > 0) {
                config.set(base + "item.model-data", look.modelData());
            }
            if (look.glow()) {
                config.set(base + "item.glow", true);
            }
        }
        try {
            File folder = plugin.getDataFolder();
            if (folder.exists() || folder.mkdirs()) {
                config.save(file());
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write compactor.yml", ex);
        }
    }

    // ------------------------------------------------------------------
    // Reading and editing
    // ------------------------------------------------------------------

    public Collection<CompactRecipe> all() {
        return byId.values();
    }

    public CompactRecipe get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** The recipe that folds this material up, or null. */
    public CompactRecipe forInput(Material input) {
        return input == null ? null : byInput.get(input);
    }

    public int size() {
        return byId.size();
    }

    /**
     * Adds or replaces a recipe and writes the book out.
     *
     * @return false when another recipe already claims that input material
     */
    public boolean put(CompactRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        CompactRecipe clash = byInput.get(recipe.input());
        if (clash != null && !clash.id().equals(recipe.id())) {
            return false;
        }
        add(recipe);
        save();
        return true;
    }

    public boolean remove(String id) {
        CompactRecipe removed = byId.remove(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        byInput.remove(removed.input());
        save();
        return true;
    }

    /** Adds to both indexes without saving. */
    private void add(CompactRecipe recipe) {
        CompactRecipe previous = byId.put(recipe.id(), recipe);
        if (previous != null) {
            byInput.remove(previous.input());
        }
        byInput.put(recipe.input(), recipe);
    }

    /**
     * Recipes whose compacted form can be placed as a block. Always empty once
     * loaded, because a placeable skin is refused on read — asserted in a test
     * rather than only documented.
     */
    public List<CompactRecipe> placeable() {
        return byId.values().stream().filter(CompactRecipe::isPlaceable).toList();
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void warn(String message) {
        warnings.add(message);
        plugin.getLogger().warning("compactor.yml: " + message);
    }
}
