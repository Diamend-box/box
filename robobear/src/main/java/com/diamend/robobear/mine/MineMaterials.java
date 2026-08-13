package com.diamend.robobear.mine;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Hand-set material lists for individual mines.
 *
 * <p>The automatic answer — what MineResetLite says the mine is made of — is
 * right almost always, and this is the escape hatch for when it isn't: a mine
 * whose composition includes filler nobody should be sent after, a build that
 * doesn't expose its composition at all, or a manual region RoboBear has no way
 * to inspect.
 *
 * <p>An empty list means "no opinion", and the detected composition is used.
 * That keeps this file small: only the mines someone has actually corrected
 * appear in it.
 */
public class MineMaterials {

    private final JavaPlugin plugin;
    private final File file;

    /** Lower-cased mine id to the materials someone chose for it. */
    private final Map<String, List<Material>> overrides = new LinkedHashMap<>();

    public MineMaterials(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mine-materials.yml");
        load();
    }

    /** What was chosen for this mine, or an empty list when nobody has said. */
    public List<Material> override(String id) {
        List<Material> chosen = overrides.get(key(id));
        return chosen == null ? List.of() : List.copyOf(chosen);
    }

    public boolean isOverridden(String id) {
        return !override(id).isEmpty();
    }

    /** Sets a mine's materials. An empty collection clears back to automatic. */
    public void set(String id, Collection<Material> materials) {
        String key = key(id);
        if (materials == null || materials.isEmpty()) {
            overrides.remove(key);
        } else {
            List<Material> clean = new ArrayList<>();
            for (Material material : materials) {
                if (material != null && material.isBlock() && !material.isAir()
                        && !clean.contains(material)) {
                    clean.add(material);
                }
            }
            if (clean.isEmpty()) {
                overrides.remove(key);
            } else {
                overrides.put(key, clean);
            }
        }
        save();
    }

    public int size() {
        return overrides.size();
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public final void load() {
        overrides.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getKeys(false)) {
            List<Material> materials = new ArrayList<>();
            for (String name : config.getStringList(id)) {
                Material material = Material.matchMaterial(name == null ? "" : name.trim());
                if (material != null && material.isBlock() && !material.isAir()) {
                    materials.add(material);
                } else if (name != null && !name.isBlank()) {
                    plugin.getLogger().warning("mine-materials.yml: '" + name
                            + "' under '" + id + "' isn't a block material; ignored.");
                }
            }
            if (!materials.isEmpty()) {
                overrides.put(key(id), materials);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<Material>> entry : overrides.entrySet()) {
            List<String> names = new ArrayList<>(entry.getValue().size());
            for (Material material : entry.getValue()) {
                names.add(material.name());
            }
            config.set(entry.getKey(), names);
        }
        config.options().setHeader(List.of(
                "Materials the challenge may ask for in a particular mine.",
                "",
                "A mine listed here uses exactly these. A mine that isn't listed",
                "uses whatever MineResetLite says it is made of, narrowed to the",
                "list in config.yml under objectives.mine-material.materials.",
                "",
                "Edit this in game with /rb quests rather than by hand."));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent
                        + " to save mine-materials.yml.");
                return;
            }
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mine-materials.yml", error);
        }
    }
}
