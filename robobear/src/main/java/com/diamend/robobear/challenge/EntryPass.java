package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The item that buys a run, and the only thing allowed to say whether one is
 * genuine.
 *
 * <p><b>Why a tag and not a name.</b> Matching a pass by its display name is
 * matching something any player can produce: a name tag, an anvil and a couple
 * of levels mints an unlimited supply, and on a PvP server that is the entry
 * gate gone. A pass is therefore stamped with a persistent data tag when it is
 * issued, and entry checks the tag. Anvils, enchanting and shulker storage all
 * preserve persistent data, so a legitimate pass survives being renamed or
 * moved about; nothing a player can craft ever acquires the tag.
 *
 * <p>Name matching is kept for {@code require-tag: false}, which exists for
 * servers that were already handing passes out before this and want their
 * existing stock to keep working.
 */
public class EntryPass {

    /** Value stored under the tag. Only its presence is ever read. */
    private static final byte STAMP = 1;

    /** Sanity bound on one {@code /rb pass give}: a full inventory of passes. */
    public static final int MAX_GIVE = 2304;

    private final RoboBearPlugin plugin;
    private final NamespacedKey key;

    public EntryPass(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "entry-pass");
    }

    /** Whether only RoboBear-issued passes count. */
    public boolean requireTag() {
        return plugin.getConfig().getBoolean("run.entry-item.require-tag", true);
    }

    /** How many items one entry costs. */
    public int cost() {
        return Math.max(1, plugin.getConfig().getInt("run.entry-item.amount", 1));
    }

    private Material material() {
        return Items.material(plugin.getConfig().getString("run.entry-item.item", ""), null);
    }

    // ------------------------------------------------------------------
    // Issuing
    // ------------------------------------------------------------------

    /**
     * One entry's worth of pass, stamped and ready to hand over, or null when
     * entry is free.
     */
    public ItemStack prototype() {
        return issue(cost());
    }

    /** A stack of {@code count} pass items, or null when entry is free. */
    public ItemStack issue(int count) {
        Material material = material();
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // A material with no meta can't be tagged, so it can't be forged
            // either — it just isn't distinguishable. Hand it back plain.
            return item;
        }

        String name = plugin.getConfig().getString("run.entry-item.name", "");
        if (name != null && !name.isBlank()) {
            meta.displayName(Text.item(name));
        }
        List<String> lore = plugin.getConfig().getStringList("run.entry-item.lore");
        if (!lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> parsed = new ArrayList<>();
            for (String line : lore) {
                parsed.add(Text.item(line));
            }
            meta.lore(parsed);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, STAMP);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Hands passes to a player, dropping at their feet whatever won't fit.
     *
     * @param count how many pass items to give, split across stacks as needed
     * @return the number actually put in the inventory, the rest being on the floor
     */
    public int give(Player target, int count) {
        Material material = material();
        if (material == null) {
            return 0;
        }
        int total = Math.max(1, Math.min(MAX_GIVE, count));
        int max = Math.max(1, material.getMaxStackSize());

        List<ItemStack> stacks = new ArrayList<>();
        for (int remaining = total; remaining > 0; ) {
            int chunk = Math.min(remaining, max);
            stacks.add(issue(chunk));
            remaining -= chunk;
        }

        Map<Integer, ItemStack> leftover =
                target.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        int dropped = 0;
        for (ItemStack stack : leftover.values()) {
            dropped += stack.getAmount();
            target.getWorld().dropItemNaturally(target.getLocation(), stack);
        }
        return total - dropped;
    }

    // ------------------------------------------------------------------
    // Checking
    // ------------------------------------------------------------------

    /** Whether this stack is a pass RoboBear will accept. */
    public boolean isPass(ItemStack item) {
        Material material = material();
        return material != null && matches(item, material);
    }

    /**
     * Whether this stack is the right item wearing the right name but was never
     * issued — an anvil job. Only meaningful while {@link #requireTag()} holds,
     * and used solely to explain a refusal rather than to accuse anyone.
     */
    public boolean looksForged(ItemStack item) {
        if (!requireTag() || item == null) {
            return false;
        }
        Material material = material();
        if (material == null || item.getType() != material || isTagged(item)) {
            return false;
        }
        return nameMatches(item);
    }

    private boolean matches(ItemStack item, Material material) {
        if (item == null || item.getType() != material) {
            return false;
        }
        // The tag is the whole answer when it's required: a pass that was
        // renamed after being issued is still a pass, and a perfect copy of the
        // name is still not one.
        return requireTag() ? isTagged(item) : nameMatches(item);
    }

    private boolean isTagged(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean nameMatches(ItemStack item) {
        String wanted = plugin.getConfig().getString("run.entry-item.name", "");
        if (wanted == null || wanted.isBlank()) {
            return true; // any item of the type will do
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return Text.plain(Text.serialize(meta.displayName()))
                .equalsIgnoreCase(Text.plain(wanted));
    }

    // ------------------------------------------------------------------
    // Spending
    // ------------------------------------------------------------------

    /**
     * Takes one entry's worth of pass from a player.
     *
     * <p>Called only once entry is otherwise certain, so a refused start never
     * eats the pass — the same rule BoxCore applies to its respec token.
     *
     * @return false when they don't have it, in which case nothing was touched
     */
    public boolean take(Player player) {
        Material material = material();
        if (material == null) {
            return true; // entry is free
        }
        int needed = cost();

        // Read and write through getItem/setItem rather than mutating the array
        // getContents() hands back — those stacks are copies on some server
        // implementations, and a pass that silently isn't consumed is a free run.
        PlayerInventory inventory = player.getInventory();
        int size = inventory.getSize();

        int found = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (matches(item, material)) {
                found += item.getAmount();
            }
        }
        if (found < needed) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("run.entry-item.consume", true)) {
            return true;
        }

        int remaining = needed;
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!matches(item, material)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (item.getAmount() - take <= 0) {
                inventory.setItem(slot, null);
            } else {
                ItemStack reduced = item.clone();
                reduced.setAmount(item.getAmount() - take);
                inventory.setItem(slot, reduced);
            }
        }
        return true;
    }

    /** Whether the player is carrying anything that looks like a forged pass. */
    public boolean carriesForgery(Player player) {
        if (!requireTag()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (looksForged(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /** How the cost reads in a menu or a message. */
    public String label() {
        ItemStack prototype = prototype();
        if (prototype == null) {
            return "nothing";
        }
        return prototype.getAmount() + "× " + Items.describe(prototype);
    }
}
