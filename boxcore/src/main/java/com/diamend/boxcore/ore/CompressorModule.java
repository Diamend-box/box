package com.diamend.boxcore.ore;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folds full stacks of raw ore into single items, so a player can stay out
 * longer before inventory space forces them back.
 *
 * <p>This is a <em>capacity</em> tool and nothing more. Compressed units are
 * worth exactly what they were folded from ({@link OreValues}), so anything
 * counting ore sees no change — the compressor buys time in the box, never
 * safety.
 *
 * <p>Unlocks are per ore type, gated on that ore's own collection tier, so the
 * ores a player has actually worked are the ores they can carry more of.
 *
 * <p>Compression runs on a throttled sweep of players who have gained items
 * rather than inside {@code BlockBreakEvent}. Regenerating cubes make that
 * event fire constantly, and scanning an inventory on every block break is the
 * kind of per-break work the server cannot afford.
 */
public class CompressorModule implements BoxModule {

    /** One unlockable ore: what gates it, and how its compressed form looks. */
    public record OreUnlock(Material material,
                            String collectionId,
                            int tier,
                            CompressedOre.Appearance appearance) {
    }

    private static final int VANILLA_STACK = 64;

    private final BoxCorePlugin plugin;
    private final Map<Material, OreUnlock> unlocks = new LinkedHashMap<>();

    /** Players who have gained items since the last sweep. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    /** Players who recently expanded a unit, and when the grace period ends. */
    private final Map<UUID, Long> expandGrace = new ConcurrentHashMap<>();

    private int ratio = VANILLA_STACK;
    private int graceSeconds = 30;
    private int sweepTicks = 20;

    private BukkitTask task;

    public CompressorModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "compressor";
    }

    @Override
    public String displayName() {
        return "Auto-compressor";
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(new CompressorListener(plugin, this), plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, sweepTicks, sweepTicks);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
        expandGrace.clear();
    }

    @Override
    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        ratio = Math.max(2, plugin.getConfig().getInt("compressor.ratio", VANILLA_STACK));
        graceSeconds = Math.max(0, plugin.getConfig().getInt("compressor.expand-grace-seconds", 30));
        sweepTicks = Math.max(5, plugin.getConfig().getInt("compressor.sweep-ticks", 20));

        unlocks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("compressor.ores");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("compressor.ores: unknown material '" + key + "', skipping.");
                continue;
            }
            if (!plugin.ores().isOre(material)) {
                plugin.getLogger().warning("compressor.ores: '" + key
                        + "' is not on the ore whitelist, skipping.");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            String collectionId = entry == null ? key.toLowerCase(Locale.ROOT)
                    : entry.getString("collection", key.toLowerCase(Locale.ROOT));
            int tier = entry == null ? 0 : entry.getInt("tier", 0);
            unlocks.put(material, new OreUnlock(material, collectionId, Math.max(0, tier),
                    readAppearance(entry, material)));
        }
    }

    /**
     * Reads the optional {@code item:} block describing how an ore's compressed
     * form should look. Anything left out falls back to the built-in look.
     */
    private CompressedOre.Appearance readAppearance(ConfigurationSection entry, Material ore) {
        ConfigurationSection item = entry == null ? null : entry.getConfigurationSection("item");
        if (item == null) {
            return CompressedOre.Appearance.defaultFor(ore);
        }
        Material skin = Items.material(item.getString("material"), ore);
        if (skin.isBlock()) {
            // A placeable skin reopens the route the custom item exists to
            // close: place the block and the ore leaves the inventory without
            // anything counting it. Refuse the skin rather than the ore.
            plugin.getLogger().warning("compressor.ores." + ore.name()
                    + ".item.material is a placeable block (" + skin.name()
                    + "); falling back to " + ore.name() + ".");
            skin = ore;
        }
        List<String> lore = item.isList("lore") ? item.getStringList("lore") : null;
        return new CompressedOre.Appearance(
                skin,
                item.getString("name"),
                lore == null || lore.isEmpty() ? null : lore,
                Math.max(0, item.getInt("model-data", 0)),
                item.getBoolean("glow", false));
    }

    /** How this ore's compressed form should look, defaulting when unconfigured. */
    public CompressedOre.Appearance appearanceFor(Material ore) {
        OreUnlock unlock = unlocks.get(ore);
        return unlock == null || unlock.appearance() == null
                ? CompressedOre.Appearance.defaultFor(ore)
                : unlock.appearance();
    }

    /**
     * Configured compressed skins that can be placed as a block. Always empty
     * in a working configuration — asserted in a test, because a placeable skin
     * silently reopens the laundering route the custom item exists to close.
     */
    public List<Material> placeableSkins() {
        List<Material> placeable = new ArrayList<>();
        for (OreUnlock unlock : unlocks.values()) {
            Material skin = unlock.appearance().materialOr(unlock.material());
            if (skin.isBlock()) {
                placeable.add(skin);
            }
        }
        return placeable;
    }

    // ------------------------------------------------------------------
    // Unlocks
    // ------------------------------------------------------------------

    public int ratio() {
        return ratio;
    }

    public Map<Material, OreUnlock> unlocks() {
        return java.util.Collections.unmodifiableMap(unlocks);
    }

    /** Whether this player has earned the compressor for a given ore. */
    public boolean isUnlocked(Player player, Material material) {
        OreUnlock unlock = unlocks.get(material);
        if (unlock == null) {
            return false;
        }
        if (unlock.tier() <= 0) {
            return true;
        }
        return collectionTier(player, unlock.collectionId()) >= unlock.tier();
    }

    /** The tier this player has reached in a collection, or 0. */
    public int collectionTier(Player player, String collectionId) {
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        if (collections == null) {
            return 0;
        }
        ItemCollection collection = collections.collections().get(collectionId);
        if (collection == null) {
            return 0;
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        return collection.tierFor(profile.getCollected(collectionId));
    }

    public List<Material> unlockedFor(Player player) {
        List<Material> result = new ArrayList<>();
        for (Material material : unlocks.keySet()) {
            if (isUnlocked(player, material)) {
                result.add(material);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Compression
    // ------------------------------------------------------------------

    /** Flags a player for the next sweep. Called from the hot event path. */
    public void markPending(Player player) {
        if (player != null) {
            pending.add(player.getUniqueId());
        }
    }

    private void sweep() {
        if (pending.isEmpty()) {
            return;
        }
        List<UUID> batch = new ArrayList<>(pending);
        pending.clear();
        for (UUID uuid : batch) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                compress(player);
            }
        }
    }

    /**
     * Folds every full {@code ratio} of raw ore in the player's inventory into
     * compressed units. Returns how many units were created.
     *
     * <p>Never touches an already-compressed stack, and never runs during the
     * grace window after an expand — otherwise expanding ore to use in an anvil
     * or an enchanting table would be undone before the player could reach one.
     */
    public int compress(Player player) {
        if (player == null || !isEnabledFor(player) || inGrace(player)) {
            return 0;
        }
        int created = 0;
        for (Material material : unlocks.keySet()) {
            if (!isUnlocked(player, material)) {
                continue;
            }
            created += compressOne(player, material);
        }
        return created;
    }

    private int compressOne(Player player, Material material) {
        PlayerInventory inventory = player.getInventory();
        long raw = rawCount(inventory, material);
        int units = (int) (raw / ratio);
        if (units <= 0) {
            return 0;
        }
        ItemStack compressed = plugin.ores().compressed()
                .create(material, appearanceFor(material), ratio, units);
        if (compressed == null) {
            return 0;
        }
        // Take the raw ore first: removing units*ratio items always frees at
        // least as many slots as the compressed stack needs, so the give-back
        // below cannot overflow into a drop.
        removeRaw(inventory, material, (long) units * ratio);
        give(player, compressed);
        return units;
    }

    /**
     * Mints compressed units of an ore at the configured ratio and appearance,
     * without taking any raw ore for them. Used by the admin give command to
     * put a real unit in hand for testing a skin or a drop rule.
     *
     * @return the stack, or null when the ore isn't one this server compresses
     */
    public ItemStack createUnit(Material ore, int units) {
        if (ore == null || units <= 0 || !plugin.ores().isOre(ore)) {
            return null;
        }
        return plugin.ores().compressed().create(ore, appearanceFor(ore), ratio, units);
    }

    /**
     * Puts items into the inventory without depending on stack-merge rules.
     *
     * <p>A compressed unit and a raw item are the same material and differ only
     * in metadata, so anything that merges on material alone would fold raw ore
     * into a compressed stack and multiply it by the ratio. Rather than trust
     * every inventory path to compare metadata the way we expect, this only
     * ever tops up a stack of genuinely the same kind — both raw, or both
     * compressed at the same ratio — and otherwise takes an empty slot.
     *
     * <p>Slots are written one at a time rather than through
     * {@code setStorageContents}, which is not implemented for player
     * inventories everywhere and would clobber the whole inventory to change
     * one slot even where it is.
     */
    public void give(Player player, ItemStack stack) {
        CompressedOre compressed = plugin.ores().compressed();
        PlayerInventory inventory = player.getInventory();
        int slots = inventory.getStorageContents().length;
        int max = stack.getType().getMaxStackSize();
        int remaining = stack.getAmount();

        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != stack.getType()) {
                continue;
            }
            if (!compressed.sameKind(item, stack)) {
                continue;
            }
            int room = max - item.getAmount();
            if (room <= 0) {
                continue;
            }
            int add = Math.min(room, remaining);
            item.setAmount(item.getAmount() + add);
            inventory.setItem(slot, item);
            remaining -= add;
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                continue;
            }
            int add = Math.min(max, remaining);
            ItemStack copy = stack.clone();
            copy.setAmount(add);
            inventory.setItem(slot, copy);
            remaining -= add;
        }

        if (remaining > 0) {
            ItemStack spill = stack.clone();
            spill.setAmount(remaining);
            player.getWorld().dropItemNaturally(player.getLocation(), spill);
        }
    }

    /** Counts raw (uncompressed) items of a material in the inventory. */
    public long rawCount(PlayerInventory inventory, Material material) {
        long total = 0;
        CompressedOre compressed = plugin.ores().compressed();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material && !compressed.isCompressed(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeRaw(PlayerInventory inventory, Material material, long amount) {
        CompressedOre compressed = plugin.ores().compressed();
        int slots = inventory.getStorageContents().length;
        long remaining = amount;
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material || compressed.isCompressed(item)) {
                continue;
            }
            int take = (int) Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take >= item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inventory.setItem(slot, item);
            }
        }
    }

    /**
     * Expands compressed units held in the main hand back into raw ore.
     *
     * @param units how many to expand; capped by the stack and by free space
     * @return how many units were actually expanded
     */
    public int expand(Player player, int units) {
        if (player == null || units <= 0) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        CompressedOre compressed = plugin.ores().compressed();
        int itemRatio = compressed.ratio(held);
        if (itemRatio <= 0) {
            return 0;
        }
        // Read everything off the held stack before touching it. Emptying an
        // ItemStack turns it into AIR, so a material read afterwards would come
        // back as nothing and the raw ore would never be handed over.
        //
        // The ore is the tagged source, not the item's material: a compressed
        // unit may be skinned as anything, and expanding it must hand back the
        // ore it is worth rather than the thing it looks like.
        Material material = compressed.sourceOre(held);
        if (material == null) {
            return 0;
        }
        int heldUnits = held.getAmount();
        int maxStack = material.getMaxStackSize();

        int available = Math.min(units, heldUnits);
        int room = freeSpaceFor(inventory, material);
        // Expanding the whole held stack frees the slot it was sitting in, and
        // on a full inventory that slot is the only room left. Counting it only
        // when every unit is consumed keeps the estimate from ever promising
        // space that will not exist.
        int roomIfHandEmpties = room + maxStack;
        int affordable = available == heldUnits && roomIfHandEmpties / itemRatio >= available
                ? available
                : Math.min(available, room / itemRatio);
        if (affordable <= 0) {
            return 0;
        }

        int remaining = heldUnits - affordable;
        inventory.setItemInMainHand(remaining <= 0
                ? null
                : compressed.create(material, appearanceFor(material), itemRatio, remaining));

        // Hand the ore back a stack at a time. An ItemStack larger than the
        // material's stack size is not something every inventory path handles.
        int raw = affordable * itemRatio;
        while (raw > 0) {
            int chunk = Math.min(maxStack, raw);
            give(player, new ItemStack(material, chunk));
            raw -= chunk;
        }
        startGrace(player);
        return affordable;
    }

    /**
     * Room for raw items of a material, counting empty slots and the headroom
     * on partial stacks. Compressed stacks are skipped: they are the same
     * material but different metadata, so raw ore will never merge into them.
     */
    private int freeSpaceFor(PlayerInventory inventory, Material material) {
        CompressedOre compressed = plugin.ores().compressed();
        int max = material.getMaxStackSize();
        int space = 0;
        ItemStack[] contents = inventory.getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                space += max;
            } else if (item.getType() == material && !compressed.isCompressed(item)) {
                space += Math.max(0, max - item.getAmount());
            }
        }
        return space;
    }

    // ------------------------------------------------------------------
    // Per-player state
    // ------------------------------------------------------------------

    public boolean isEnabledFor(Player player) {
        return plugin.profiles().get(player.getUniqueId()).isCompressorEnabled();
    }

    /** Flips the player's toggle and returns the new state. */
    public boolean toggle(Player player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        boolean now = !profile.isCompressorEnabled();
        profile.setCompressorEnabled(now);
        if (now) {
            markPending(player);
        }
        return now;
    }

    public void setEnabled(Player player, boolean enabled) {
        plugin.profiles().get(player.getUniqueId()).setCompressorEnabled(enabled);
        if (enabled) {
            markPending(player);
        }
    }

    private void startGrace(Player player) {
        if (graceSeconds > 0) {
            expandGrace.put(player.getUniqueId(), System.currentTimeMillis() + graceSeconds * 1000L);
        }
    }

    /** True while a recent expand is still protected from re-compression. */
    public boolean inGrace(Player player) {
        Long until = expandGrace.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            expandGrace.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public int graceSeconds() {
        return graceSeconds;
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
        expandGrace.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(15, Material.RAW_IRON,
                "<aqua><bold>Auto-compressor",
                List.of("<gray>Folds full stacks of ore", "<gray>into single items."),
                player -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("");
                    int unlocked = unlockedFor(player).size();
                    lines.add("<gray>Unlocked: <white>" + unlocked + "</white>/" + unlocks.size() + " ores");
                    lines.add("<gray>Ratio: <white>" + Text.number(ratio) + " <gray>→ <white>1");
                    lines.add(isEnabledFor(player)
                            ? "<green>Enabled <dark_gray>(/box compress)"
                            : "<red>Disabled <dark_gray>(/box compress)");
                    return lines;
                },
                player -> plugin.messages().send(player, "compressor-status",
                        "state", isEnabledFor(player) ? "enabled" : "disabled",
                        "unlocked", unlockedFor(player).size(),
                        "total", unlocks.size()));
    }

    @Override
    public List<String> statusLines() {
        return List.of(unlocks.size() + " compressible ore(s), " + ratio + ":1");
    }
}
