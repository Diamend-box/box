package com.diamend.boxcore.ore;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one place the question "how much ore is this?" is answered.
 *
 * <p>Everything that needs an ore count goes through here rather than counting
 * items inline. The value itself is trivial — every whitelisted raw item is
 * worth one — but the *counting* is not: compression, custom items and the
 * whitelist check all live behind this, and separate call sites reimplementing
 * that will drift apart.
 *
 * <p>Amounts are reported in <em>ore-equivalents</em>, not item counts, so a
 * compressed unit reads as the stack it was folded from. That is what lets
 * compression stay invisible to anything counting ore: a player who compresses
 * their inventory is holding exactly as much as they were a moment before.
 *
 * <p>The whitelist is raw forms only. Smelted output carries no ore-equivalent,
 * and no entry may be a placeable block — {@code ANCIENT_DEBRIS} is the trap
 * there, since placing a block would move ore out of the counted world
 * entirely. {@link #placeableEntries()} exists so a test can hold that line.
 */
public final class OreValues {

    /** Raw forms only: no ingots, and nothing that can be placed as a block. */
    private static final List<Material> DEFAULT_WHITELIST = List.of(
            Material.RAW_IRON,
            Material.RAW_GOLD,
            Material.RAW_COPPER,
            Material.DIAMOND,
            Material.EMERALD,
            Material.COAL,
            Material.LAPIS_LAZULI,
            Material.REDSTONE,
            Material.QUARTZ,
            Material.NETHERITE_SCRAP);

    private final CompressedOre compressed;
    private final Set<Material> whitelist = new LinkedHashSet<>();

    public OreValues(Plugin plugin) {
        this.compressed = new CompressedOre(plugin);
        load(plugin.getConfig());
    }

    /** Re-reads the whitelist; called on {@code /box reload}. */
    public void load(FileConfiguration config) {
        whitelist.clear();
        List<String> configured = config == null
                ? List.of()
                : config.getStringList("ore.whitelist");
        if (configured.isEmpty()) {
            whitelist.addAll(DEFAULT_WHITELIST);
            return;
        }
        for (String name : configured) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) {
                whitelist.add(material);
            }
        }
        if (whitelist.isEmpty()) {
            whitelist.addAll(DEFAULT_WHITELIST);
        }
    }

    public boolean isOre(Material material) {
        return material != null && whitelist.contains(material);
    }

    /**
     * Resolves a typed ore name to a whitelisted material, or null.
     *
     * <p>Accepts the material name ({@code RAW_IRON}) and the short display
     * name the player actually sees ({@code iron}), because the two differ for
     * every raw form and staff type the one on the item.
     */
    public Material matchOre(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        Material exact = Material.matchMaterial(trimmed.toUpperCase(Locale.ROOT));
        if (isOre(exact)) {
            return exact;
        }
        String wanted = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Material material : whitelist) {
            String bare = material.name().startsWith("RAW_")
                    ? material.name().substring(4)
                    : material.name();
            if (bare.equals(wanted) || bare.startsWith(wanted + "_")) {
                return material;
            }
        }
        return null;
    }

    public Set<Material> whitelist() {
        return Collections.unmodifiableSet(whitelist);
    }

    /**
     * Which whitelisted ore a stack counts as, or null when it counts as none.
     *
     * <p>The compressed tag is read before the item's material, never the
     * reverse. A compressed unit's material is only its skin — a server owner
     * can make compressed coal look like anything — so the material answers
     * "how does this render", not "what is this".
     */
    public Material oreKey(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        if (compressed.isCompressed(item)) {
            Material source = compressed.sourceOre(item);
            return isOre(source) ? source : null;
        }
        return isOre(item.getType()) ? item.getType() : null;
    }

    /** What one item of this stack is worth, in ore-equivalents. */
    public long unitValue(ItemStack item) {
        if (oreKey(item) == null) {
            return 0;
        }
        int ratio = compressed.ratio(item);
        return ratio > 0 ? ratio : 1;
    }

    /** What a whole stack is worth, in ore-equivalents. */
    public long equivalents(ItemStack item) {
        long unit = unitValue(item);
        return unit == 0 ? 0 : unit * item.getAmount();
    }

    /**
     * Everything the player is carrying, in ore-equivalents per material.
     *
     * <p>Covers the main inventory, hotbar and offhand. Armour slots are
     * excluded because no whitelisted item is wearable.
     */
    public Map<Material, Long> carried(Player player) {
        Map<Material, Long> totals = new EnumMap<>(Material.class);
        if (player == null) {
            return totals;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            accumulate(totals, item);
        }
        accumulate(totals, inventory.getItemInOffHand());
        return totals;
    }

    /** Ore-equivalents of one material the player is carrying. */
    public long carried(Player player, Material material) {
        return carried(player).getOrDefault(material, 0L);
    }

    private void accumulate(Map<Material, Long> totals, ItemStack item) {
        Material key = oreKey(item);
        if (key == null) {
            return;
        }
        long value = equivalents(item);
        if (value > 0) {
            totals.merge(key, value, Long::sum);
        }
    }

    /**
     * Whitelist entries that can be placed as a block.
     *
     * <p>Always empty in a correct configuration. Placing a block removes the
     * item from the inventory, which would park ore somewhere nothing counts
     * it, so this is asserted in a test rather than only documented.
     */
    public List<Material> placeableEntries() {
        return whitelist.stream().filter(Material::isBlock).toList();
    }

    public CompressedOre compressed() {
        return compressed;
    }
}
