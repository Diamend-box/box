package com.diamend.boxtutorial.arena;

import com.diamend.boxtutorial.items.ItemRef;
import com.diamend.boxtutorial.items.ItemRegistry;
import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What one tutorial instance is made of, read from the {@code arena:} section.
 *
 * <p>Every instance is built from this same blueprint at a different origin, so
 * changing a number here changes every arena handed out from then on.
 */
public class ArenaBlueprint {

    /** Where a player goes when they're finished. */
    public enum ReturnMode { WORLD_SPAWN, LOCATION, COMMAND }

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final List<String> warnings = new ArrayList<>();

    private String worldName = "tutorial_arena";
    private int spacing = 512;
    private int maxInstances = 32;
    private int floorY = 64;
    private int radius = 12;
    private int wallHeight = 3;
    private Material floor = Material.STONE_BRICKS;
    private Material wall = Material.BARRIER;

    private MineSpec.Offset spawnOffset = new MineSpec.Offset(0, 1, 8);
    private MineSpec.Offset shopOffset = new MineSpec.Offset(0, 1, -8);
    private String shopName = "<gold>Trader";
    private String shopProfession = "toolsmith";
    private String shopVillagerType = "plains";

    private List<MineSpec> mines = List.of();
    private List<TradeSpec> trades = List.of();

    private ReturnMode returnMode = ReturnMode.WORLD_SPAWN;
    private String returnWorld = "";
    private double returnX;
    private double returnY = 64;
    private double returnZ;
    private String returnCommand = "";

    public ArenaBlueprint(Plugin plugin, ItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        load();
    }

    /** The item slots the trades draw from. */
    public ItemRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public final void load() {
        warnings.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arena");
        if (section == null) {
            warnings.add("no 'arena:' section — using the built-in layout");
            section = plugin.getConfig().createSection("arena");
        }

        worldName = section.getString("world", "tutorial_arena");
        spacing = Math.max(64, section.getInt("spacing", 512));
        maxInstances = Math.max(1, section.getInt("max-instances", 32));
        floorY = section.getInt("y", 64);
        radius = Math.max(4, section.getInt("radius", 12));
        wallHeight = Math.max(1, section.getInt("wall-height", 3));
        floor = material(section.getString("floor"), Material.STONE_BRICKS, "arena.floor");
        wall = material(section.getString("wall"), Material.BARRIER, "arena.wall");

        spawnOffset = offset(section, "spawn-offset", new MineSpec.Offset(0, 1, 8));

        ConfigurationSection shop = section.getConfigurationSection("shopkeeper");
        if (shop != null) {
            shopOffset = offset(shop, "offset", shopOffset);
            shopName = shop.getString("name", shopName);
            shopProfession = Text.lower(shop.getString("profession", shopProfession));
            shopVillagerType = Text.lower(shop.getString("type", shopVillagerType));
        }

        mines = readMines(section.getConfigurationSection("mines"));
        trades = readTrades(section.getConfigurationSection("trades"));
        readReturn(section.getConfigurationSection("return"));

        for (String warning : warnings) {
            plugin.getLogger().warning("arena config: " + warning);
        }
    }

    private List<MineSpec> readMines(ConfigurationSection section) {
        if (section == null) {
            warnings.add("no mines are configured — there is nothing to gather");
            return List.of();
        }
        List<MineSpec> found = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                warnings.add("mine '" + key + "' is not a block of settings; skipped");
                continue;
            }
            Map<Material, Integer> blocks = new LinkedHashMap<>();
            ConfigurationSection composition = entry.getConfigurationSection("blocks");
            if (composition != null) {
                for (String name : composition.getKeys(false)) {
                    Material material = Items.material(name, null);
                    if (material == null) {
                        warnings.add("mine '" + key + "' lists '" + name
                                + "', which this server doesn't have; skipped");
                        continue;
                    }
                    blocks.put(material, Math.max(1, composition.getInt(name, 1)));
                }
            }
            if (blocks.isEmpty()) {
                warnings.add("mine '" + key + "' has no blocks; skipped");
                continue;
            }
            MineSpec mine = new MineSpec(Text.lower(key),
                    entry.getString("name", Text.prettify(key)),
                    offset(entry, "min", new MineSpec.Offset(-8, 1, -4)),
                    offset(entry, "max", new MineSpec.Offset(-3, 4, 1)),
                    entry.getInt("refill-at", 40),
                    blocks,
                    readDrops(key, entry.getConfigurationSection("drops"), blocks.keySet()));
            found.add(mine);
        }
        return found;
    }

    /**
     * Reads a mine's {@code drops:} table — {@code DARK_OAK_LOG: "OAK_LOG 4"}.
     *
     * @param mined the blocks this mine actually contains, so an override for
     *              something the mine can't produce is reported rather than
     *              sitting there quietly doing nothing
     */
    private Map<Material, ItemStack> readDrops(String mineId, ConfigurationSection section,
                                               Set<Material> mined) {
        if (section == null) {
            return Map.of();
        }
        Map<Material, ItemStack> found = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            Material broken = Items.material(name, null);
            if (broken == null) {
                warnings.add("mine '" + mineId + "' has a drop for '" + name
                        + "', which this server doesn't have; skipped");
                continue;
            }
            ItemStack drop = Items.parse(section.getString(name, ""));
            if (drop == null) {
                warnings.add("mine '" + mineId + "' says " + broken.name() + " drops '"
                        + section.getString(name, "") + "', which isn't an item; skipped");
                continue;
            }
            if (!mined.contains(broken)) {
                warnings.add("mine '" + mineId + "' has a drop for " + broken.name()
                        + ", which isn't one of its blocks");
            }
            found.put(broken, drop);
        }
        return found;
    }

    private List<TradeSpec> readTrades(ConfigurationSection section) {
        if (section == null) {
            warnings.add("no trades are configured — the trader will have nothing to offer");
            return List.of();
        }
        List<TradeSpec> found = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                warnings.add("trade '" + key + "' is not a block of settings; skipped");
                continue;
            }
            ItemRef result = ItemRef.parse(entry.getString("result", ""));
            if (result == null || !known(key, result)) {
                warnings.add("trade '" + key + "' has no usable 'result'; skipped");
                continue;
            }
            List<ItemRef> cost = new ArrayList<>();
            for (String line : entry.getStringList("cost")) {
                ItemRef item = ItemRef.parse(line);
                if (item == null || !known(key, item)) {
                    warnings.add("trade '" + key + "' asks for '" + line + "', which isn't an item");
                    continue;
                }
                cost.add(item);
            }
            if (cost.isEmpty()) {
                warnings.add("trade '" + key + "' costs nothing; skipped");
                continue;
            }
            if (cost.size() > 2) {
                warnings.add("trade '" + key + "' has more than the two ingredients a"
                        + " vanilla trade can show; the rest were dropped");
            }
            found.add(new TradeSpec(Text.lower(key), cost, result));
        }
        return found;
    }

    /** Named slots have to exist, or the trade quietly sells nothing. */
    private boolean known(String tradeId, ItemRef ref) {
        if (!ref.isCustom() || (registry != null && registry.isKnown(ref.itemId()))) {
            return true;
        }
        warnings.add("trade '" + tradeId + "' uses item '" + ref.itemId()
                + "', which isn't defined under items:");
        return false;
    }

    private void readReturn(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        String mode = section.getString("mode", "WORLD_SPAWN").trim().toUpperCase(Locale.ROOT);
        try {
            returnMode = ReturnMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            warnings.add("return.mode '" + mode + "' is not one of WORLD_SPAWN, LOCATION,"
                    + " COMMAND; using WORLD_SPAWN");
            returnMode = ReturnMode.WORLD_SPAWN;
        }
        returnWorld = section.getString("world", "");
        returnX = section.getDouble("x", 0.5);
        returnY = section.getDouble("y", 64);
        returnZ = section.getDouble("z", 0.5);
        returnCommand = section.getString("command", "");
        if (returnMode == ReturnMode.LOCATION && returnWorld.isBlank()) {
            warnings.add("return.mode is LOCATION but no world is named; using the main spawn");
            returnMode = ReturnMode.WORLD_SPAWN;
        }
        if (returnMode == ReturnMode.COMMAND && returnCommand.isBlank()) {
            warnings.add("return.mode is COMMAND but no command is set; using the main spawn");
            returnMode = ReturnMode.WORLD_SPAWN;
        }
    }

    private MineSpec.Offset offset(ConfigurationSection section, String path,
                                   MineSpec.Offset fallback) {
        List<Integer> values = section.getIntegerList(path);
        if (values.size() != 3) {
            if (section.contains(path)) {
                warnings.add("'" + section.getName() + "." + path
                        + "' should be three numbers like [0, 1, 8]; using the default");
            }
            return fallback;
        }
        return new MineSpec.Offset(values.get(0), values.get(1), values.get(2));
    }

    private Material material(String name, Material fallback, String path) {
        Material material = Items.material(name, fallback);
        if (name != null && !name.isBlank() && material == fallback
                && !name.trim().equalsIgnoreCase(fallback.name())) {
            warnings.add("'" + path + "' is '" + name + "', which this server doesn't have;"
                    + " using " + fallback.name());
        }
        return material;
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public String worldName() {
        return worldName;
    }

    public int spacing() {
        return spacing;
    }

    public int maxInstances() {
        return maxInstances;
    }

    public int floorY() {
        return floorY;
    }

    public int radius() {
        return radius;
    }

    public int wallHeight() {
        return wallHeight;
    }

    public Material floor() {
        return floor;
    }

    public Material wall() {
        return wall;
    }

    public MineSpec.Offset spawnOffset() {
        return spawnOffset;
    }

    public MineSpec.Offset shopOffset() {
        return shopOffset;
    }

    public String shopName() {
        return shopName;
    }

    public String shopProfession() {
        return shopProfession;
    }

    public String shopVillagerType() {
        return shopVillagerType;
    }

    public List<MineSpec> mines() {
        return Collections.unmodifiableList(mines);
    }

    public MineSpec mine(String id) {
        for (MineSpec mine : mines) {
            if (mine.id().equalsIgnoreCase(id)) {
                return mine;
            }
        }
        return null;
    }

    public List<TradeSpec> trades() {
        return Collections.unmodifiableList(trades);
    }

    public ReturnMode returnMode() {
        return returnMode;
    }

    public String returnWorld() {
        return returnWorld;
    }

    public double returnX() {
        return returnX;
    }

    public double returnY() {
        return returnY;
    }

    public double returnZ() {
        return returnZ;
    }

    public String returnCommand() {
        return returnCommand;
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }
}
