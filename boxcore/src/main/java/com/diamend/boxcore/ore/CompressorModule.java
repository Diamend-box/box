package com.diamend.boxcore.ore;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The personal compactor: a carried item that folds up whichever recipes the
 * player has slotted into it, so they can stay out longer before inventory
 * space forces them back.
 *
 * <p>This is a <em>capacity</em> tool and nothing more. A compacted unit is
 * worth exactly what it was folded from, so anything counting items sees no
 * change — the compactor buys time in the box, never value.
 *
 * <p>What compacts is decided by the item in the player's inventory, not by
 * anything on their profile. Owning a compactor and slotting a recipe into it
 * is the whole rule; carry nothing and nothing folds. Slot count comes from the
 * compactor's tier, which is how a progression system will grant capacity
 * later.
 *
 * <p>Compaction runs on a throttled sweep of players who have gained items
 * rather than inside {@code BlockBreakEvent}. Regenerating cubes make that
 * event fire constantly, and scanning an inventory on every block break is the
 * kind of per-break work the server cannot afford.
 */
public class CompressorModule implements BoxModule {

    private final BoxCorePlugin plugin;

    private final CompactRecipes recipes;
    private final CompactorTiers tiers;
    private final CompactorItem compactors;

    /** Players who have gained items since the last sweep. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    /** Players who recently expanded a unit, and when the grace period ends. */
    private final Map<UUID, Long> expandGrace = new ConcurrentHashMap<>();
    /** Compactors pulled out of a death drop, waiting for the respawn. */
    private final Map<UUID, List<ItemStack>> heldThroughDeath = new ConcurrentHashMap<>();

    private int graceSeconds = 30;
    private int sweepTicks = 20;

    private BukkitTask task;

    public CompressorModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
        this.recipes = new CompactRecipes(plugin);
        this.tiers = new CompactorTiers(plugin);
        this.compactors = new CompactorItem(plugin, tiers, recipes);
    }

    @Override
    public String id() {
        return "compressor";
    }

    @Override
    public String displayName() {
        return "Personal compactor";
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
        heldThroughDeath.clear();
    }

    @Override
    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        graceSeconds = Math.max(0, plugin.getConfig().getInt("compressor.expand-grace-seconds", 30));
        sweepTicks = Math.max(5, plugin.getConfig().getInt("compressor.sweep-ticks", 20));
        tiers.load();
        recipes.load();
    }

    // ------------------------------------------------------------------
    // The pieces
    // ------------------------------------------------------------------

    public CompactRecipes recipes() {
        return recipes;
    }

    public CompactorTiers tiers() {
        return tiers;
    }

    public CompactorItem compactors() {
        return compactors;
    }

    public int graceSeconds() {
        return graceSeconds;
    }

    // ------------------------------------------------------------------
    // Finding a player's compactors
    // ------------------------------------------------------------------

    /** Inventory indexes of every compactor the player is carrying. */
    public List<Integer> compactorSlots(Player player) {
        List<Integer> found = new ArrayList<>();
        if (player == null) {
            return found;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length; index++) {
            if (compactors.isCompactor(contents[index])) {
                found.add(index);
            }
        }
        return found;
    }

    /** The first compactor the player is carrying, or null. */
    public ItemStack firstCompactor(Player player) {
        List<Integer> slots = compactorSlots(player);
        return slots.isEmpty() ? null : player.getInventory().getItem(slots.get(0));
    }

    public boolean hasCompactor(Player player) {
        return !compactorSlots(player).isEmpty();
    }

    // ------------------------------------------------------------------
    // Compaction
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
     * Folds up everything the player's compactors are set to. Returns how many
     * units were created.
     *
     * <p>Two compactors slotted with the same recipe fold it once between them,
     * not twice — the recipe is the unit of work, not the compactor.
     *
     * <p>Never runs during the grace window after an expand, or the ore someone
     * expanded to use in an anvil would be folded back up before they reached
     * one.
     */
    public int compress(Player player) {
        if (player == null || !isEnabledFor(player) || inGrace(player)) {
            return 0;
        }
        int created = 0;
        Set<String> done = new HashSet<>();
        for (int index : compactorSlots(player)) {
            ItemStack compactor = player.getInventory().getItem(index);
            for (CompactRecipe recipe : compactors.active(compactor)) {
                if (done.add(recipe.id())) {
                    created += compactOne(player, recipe);
                }
            }
        }
        return created;
    }

    private int compactOne(Player player, CompactRecipe recipe) {
        PlayerInventory inventory = player.getInventory();
        long raw = rawCount(inventory, recipe.input());
        int units = (int) (raw / recipe.amount());
        if (units <= 0) {
            return 0;
        }
        ItemStack compacted = plugin.ores().compressed()
                .create(recipe.input(), recipe.appearance(), recipe.amount(), units);
        if (compacted == null) {
            return 0;
        }
        // Take the raw items first: removing units*amount items always frees at
        // least as many slots as the compacted stack needs, so the give-back
        // below cannot overflow into a drop.
        removeRaw(inventory, recipe.input(), (long) units * recipe.amount());
        give(player, compacted);
        return units;
    }

    /**
     * Mints compacted units without taking anything for them. Used by the admin
     * give command to put a real unit in hand for testing a skin or a drop rule.
     *
     * @return the stack, or null when nothing compacts that material
     */
    public ItemStack createUnit(Material input, int units) {
        CompactRecipe recipe = recipes.forInput(input);
        if (recipe == null || units <= 0) {
            return null;
        }
        return plugin.ores().compressed()
                .create(recipe.input(), recipe.appearance(), recipe.amount(), units);
    }

    /**
     * Whether an item must never be folded, merged into, or counted as raw
     * stock. A compactor skinned as something that also has a recipe — a hopper
     * on a server that compacts hoppers — would otherwise be fed to itself.
     */
    private boolean isTool(ItemStack item) {
        return compactors.isCompactor(item);
    }

    /**
     * Puts items into the inventory without depending on stack-merge rules.
     *
     * <p>A compacted unit and a raw item are the same material and differ only
     * in metadata, so anything that merges on material alone would fold raw
     * items into a compacted stack and multiply them by the recipe amount.
     * Rather than trust every inventory path to compare metadata the way we
     * expect, this only ever tops up a stack of genuinely the same kind — both
     * raw, or both compacted at the same amount for the same input — and
     * otherwise takes an empty slot.
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
            if (item == null || item.getType() != stack.getType() || isTool(item)) {
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

    /** Counts raw (uncompacted) items of a material in the inventory. */
    public long rawCount(PlayerInventory inventory, Material material) {
        long total = 0;
        CompressedOre compressed = plugin.ores().compressed();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material
                    && !compressed.isCompressed(item) && !isTool(item)) {
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
            if (item == null || item.getType() != material
                    || compressed.isCompressed(item) || isTool(item)) {
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
     * Expands what is in the player's hand and tells them what happened.
     *
     * <p>Silence is the one outcome this must never produce. A right click that
     * does nothing and says nothing is indistinguishable from a broken item,
     * and that is exactly how expansion read on its first playtest.
     *
     * @param all expand the whole held stack rather than a single unit
     * @return how many units were expanded
     */
    public int expandInHand(Player player, boolean all) {
        if (player == null) {
            return 0;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CompressedOre compressed = plugin.ores().compressed();
        boolean wasCompressed = compressed.isCompressed(held);
        // Read the ore off the stack before expanding it. Emptying the hand
        // turns this stack into AIR, so a message naming the ore has to be
        // built from what it was, not from what the slot holds afterwards.
        Material ore = wasCompressed ? compressed.sourceOre(held) : null;

        int expanded = expand(player, all ? held.getAmount() : 1);
        if (expanded > 0) {
            plugin.messages().send(player, "compressor-expanded",
                    "amount", expanded,
                    "plural", expanded == 1 ? "" : "s",
                    "ore", ore == null ? "items" : CompressedOre.displayName(ore),
                    "seconds", graceSeconds);
            return expanded;
        }
        // Say which of the two reasons it was: "no room" and "that isn't a
        // compacted unit" call for opposite fixes.
        plugin.messages().send(player,
                wasCompressed ? "compressor-no-room" : "compressor-not-compacted");
        return 0;
    }

    /**
     * Expands compacted units held in the main hand back into raw items.
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
        // back as nothing and the raw items would never be handed over.
        //
        // The source is the tagged material, not the item's own: a compacted
        // unit may be skinned as anything, and expanding it must hand back what
        // it is worth rather than what it looks like.
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

        // Hand the items back a stack at a time. An ItemStack larger than the
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

    /** How a material's compacted form should look, per its recipe. */
    public CompressedOre.Appearance appearanceFor(Material material) {
        CompactRecipe recipe = recipes.forInput(material);
        return recipe == null ? CompressedOre.Appearance.defaultFor(material) : recipe.appearance();
    }

    /**
     * Room for raw items of a material, counting empty slots and the headroom
     * on partial stacks. Compacted stacks are skipped: they are the same
     * material but different metadata, so raw items will never merge into them.
     */
    private int freeSpaceFor(PlayerInventory inventory, Material material) {
        CompressedOre compressed = plugin.ores().compressed();
        int max = material.getMaxStackSize();
        int space = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                space += max;
            } else if (item.getType() == material
                    && !compressed.isCompressed(item) && !isTool(item)) {
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

    /** True while a recent expand is still protected from re-compaction. */
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

    public void forget(UUID uuid) {
        pending.remove(uuid);
        expandGrace.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Surviving death
    // ------------------------------------------------------------------

    /** Stashes compactors pulled out of a death drop until the respawn. */
    public void keepThroughDeath(UUID uuid, List<ItemStack> kept) {
        if (!kept.isEmpty()) {
            heldThroughDeath.put(uuid, kept);
        }
    }

    /** Hands back whatever was stashed, and forgets it. */
    public List<ItemStack> reclaimAfterDeath(UUID uuid) {
        List<ItemStack> kept = heldThroughDeath.remove(uuid);
        return kept == null ? List.of() : kept;
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(15, Material.HOPPER,
                "<aqua><bold>Personal compactor",
                List.of("<gray>Folds what you slot into it", "<gray>as you gather it."),
                player -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("");
                    ItemStack held = firstCompactor(player);
                    if (held == null) {
                        lines.add("<red>You don't have one.");
                    } else {
                        int slots = compactors.slots(held);
                        lines.add("<gray>Slots in use: <white>" + compactors.active(held).size()
                                + "</white>/" + slots);
                        lines.add(isEnabledFor(player)
                                ? "<green>Enabled <dark_gray>(/box compress)"
                                : "<red>Disabled <dark_gray>(/box compress)");
                    }
                    lines.add("<gray>Recipes on this server: <white>" + recipes.size());
                    lines.add("");
                    lines.add("<yellow>Click to open");
                    return lines;
                },
                this::openFor);
    }

    /** Opens the carried compactor, or says why it can't be opened. */
    public void openFor(Player player) {
        ItemStack held = firstCompactor(player);
        if (held == null) {
            plugin.messages().send(player, "compactor-none");
            return;
        }
        new com.diamend.boxcore.gui.CompactorMenu(plugin, this,
                compactorSlots(player).get(0)).open(player);
    }

    @Override
    public List<String> statusLines() {
        return List.of(recipes.size() + " recipe(s), " + tiers.all().size() + " compactor tier(s)");
    }
}
