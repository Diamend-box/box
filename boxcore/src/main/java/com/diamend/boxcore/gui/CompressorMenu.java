package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionCategory;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.ore.CompressedOre;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which ores this player can compress, which ones they cannot yet, and exactly
 * what is still standing between them and the next one.
 *
 * <p>The compressor is otherwise invisible: it starts folding ore the moment a
 * collection tier ticks over, with nothing anywhere saying that it will, or
 * that it did. Listing every configured ore against its gate turns it into
 * something a player can work towards rather than something that quietly
 * happens to them.
 *
 * <p>A locked ore's icon opens the collection that gates it, because "needs
 * Coal tier 2" is only useful next to what Coal tier 2 costs.
 */
public class CompressorMenu extends AbstractMenu {

    private static final int TOGGLE_SLOT = 4;
    private static final int INFO_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final int[] ORE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final CompressorModule compressor;

    /** Menu slot to the ore it is showing. */
    private final Map<Integer, Material> shown = new HashMap<>();

    public CompressorMenu(BoxCorePlugin plugin, CompressorModule compressor) {
        super(plugin, 54, "<dark_gray>Box <gray>| <aqua>Auto-compressor");
        this.compressor = compressor;
    }

    @Override
    protected void build(Player player) {
        shown.clear();
        set(TOGGLE_SLOT, toggleIcon(player));

        int index = 0;
        for (CompressorModule.OreUnlock unlock : compressor.unlocks().values()) {
            if (index >= ORE_SLOTS.length) {
                break; // more ores than the page holds; the rest still compress
            }
            int slot = ORE_SLOTS[index++];
            shown.put(slot, unlock.material());
            set(slot, oreIcon(player, unlock));
        }
        if (index == 0) {
            set(ORE_SLOTS[3], Items.text(Material.BARRIER, "<gray>Nothing compresses here",
                    List.of("<dark_gray>No ores are configured."), false));
        }

        set(INFO_SLOT, infoIcon());
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private ItemStack toggleIcon(Player player) {
        boolean on = compressor.isEnabledFor(player);
        List<String> lore = new ArrayList<>();
        lore.add(on
                ? "<gray>Ore folds up as you mine it."
                : "<gray>Ore is left exactly as it drops.");
        lore.add("");
        lore.add("<gray>Unlocked: <white>" + compressor.unlockedFor(player).size()
                + "</white>/" + compressor.unlocks().size() + " ores");
        lore.add("<gray>Ratio: <white>" + Text.number(compressor.ratio()) + " <gray>→ <white>1");
        if (compressor.inGrace(player)) {
            lore.add("<yellow>Paused — you just expanded some.");
        }
        lore.add("");
        lore.add(on ? "<yellow>Click to turn it off" : "<yellow>Click to turn it on");
        return Items.text(on ? Material.LIME_DYE : Material.GRAY_DYE,
                on ? "<green><bold>Auto-compress: ON" : "<red><bold>Auto-compress: OFF",
                lore, on);
    }

    private ItemStack oreIcon(Player player, CompressorModule.OreUnlock unlock) {
        Material ore = unlock.material();
        boolean unlocked = compressor.isUnlocked(player, ore);

        List<String> lore = new ArrayList<>();
        if (unlocked) {
            lore.add("<green>Unlocked");
            lore.add("<gray>" + Text.number(compressor.ratio()) + " <gray>→ <white>1 unit");
        } else {
            lore.addAll(gateLines(player, unlock));
        }
        lore.add("");
        lore.addAll(carryingLines(player, ore));

        return Items.text(ore, (unlocked ? "<aqua><bold>" : "<gray><bold>")
                + CompressedOre.displayName(ore), lore, unlocked);
    }

    /** What is still standing between this player and this ore. */
    private List<String> gateLines(Player player, CompressorModule.OreUnlock unlock) {
        List<String> lore = new ArrayList<>();
        lore.add("<red>Locked");

        ItemCollection collection = compressor.collectionFor(unlock.collectionId());
        if (collection == null) {
            // Configured against a collection that isn't loaded. Say so plainly
            // rather than render a progress bar over a number nobody can move.
            lore.add("<dark_gray>Needs the '" + unlock.collectionId() + "' collection,");
            lore.add("<dark_gray>which isn't loaded on this server.");
            return lore;
        }

        long amount = compressor.collected(player, unlock.collectionId());
        int reached = collection.tierFor(amount);
        // The display carries its own colour tags; strip them rather than nest
        // one set of tags inside another and hope MiniMessage agrees.
        lore.add("<gray>Needs <white>" + Text.plain(collection.getDisplay())
                + "</white> tier <white>" + unlock.tier());
        lore.add("<gray>You're on tier <white>" + reached + "</white>/"
                + collection.tierCount());

        long required = requiredAmount(collection, unlock.tier());
        if (required > 0) {
            double fraction = Math.max(0.0, Math.min(1.0, (double) amount / (double) required));
            lore.add("<gray>" + Text.progressBar(fraction, 20, "<aqua>■", "<dark_gray>■"));
            lore.add("<gray>" + collection.format(amount) + " <dark_gray>/ <gray>"
                    + collection.format(required));
            lore.add("<gray>Still to go: <white>"
                    + collection.format(Math.max(0, required - amount)));
        }
        lore.add("");
        lore.add("<yellow>Click to see the collection");
        return lore;
    }

    /**
     * The lifetime total that reaches a tier, or 0 when the collection has
     * fewer tiers than the gate names.
     */
    private long requiredAmount(ItemCollection collection, int tier) {
        List<com.diamend.boxcore.collection.CollectionTier> tiers = collection.getTiers();
        if (tier <= 0 || tier > tiers.size()) {
            return 0;
        }
        return tiers.get(tier - 1).amount();
    }

    private List<String> carryingLines(Player player, Material ore) {
        long raw = compressor.rawCount(player.getInventory(), ore);
        long units = compressor.compressedUnits(player, ore);
        if (raw <= 0 && units <= 0) {
            return List.of("<dark_gray>You aren't carrying any.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Carrying: <white>" + Text.number(raw) + "<gray> raw");
        if (units > 0) {
            lore.add("<gray>Compressed: <white>" + Text.number(units) + "<gray> unit"
                    + (units == 1 ? "" : "s") + " <dark_gray>(worth "
                    + Text.number(units * compressor.ratio()) + ")");
        }
        return lore;
    }

    private ItemStack infoIcon() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Every <white>" + Text.number(compressor.ratio())
                + "</white> raw ore folds into");
        lore.add("<gray>a single item, so you can stay");
        lore.add("<gray>out longer before your inventory");
        lore.add("<gray>sends you back.");
        lore.add("");
        lore.add("<gray>A compressed unit is worth exactly");
        lore.add("<gray>what it was folded from — nothing");
        lore.add("<gray>is gained or lost by compressing.");
        lore.add("");
        lore.add("<gray>Right-click a unit to expand one,");
        lore.add("<gray>or sneak and right-click for the stack.");
        if (compressor.graceSeconds() > 0) {
            lore.add("<dark_gray>Expanding pauses compression for "
                    + compressor.graceSeconds() + "s.");
        }
        return Items.text(Material.BOOK, "<yellow>How it works", lore, false);
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == BACK_SLOT) {
            click(player);
            openLater(player, new HubMenu(plugin));
            return;
        }
        if (raw == TOGGLE_SLOT) {
            boolean on = compressor.toggle(player);
            plugin.messages().send(player, on ? "compressor-on" : "compressor-off");
            click(player);
            redraw(player);
            return;
        }
        Material ore = shown.get(raw);
        if (ore != null && !compressor.isUnlocked(player, ore)) {
            openCollection(player, ore);
        }
    }

    /** Opens the collection gating an ore, when there is one to open. */
    private void openCollection(Player player, Material ore) {
        CompressorModule.OreUnlock unlock = compressor.unlock(ore);
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        if (unlock == null || collections == null) {
            return;
        }
        ItemCollection collection = collections.collections().get(unlock.collectionId());
        if (collection == null) {
            return;
        }
        CollectionCategory category = collections.collections().category(collection.getCategoryId());
        if (category == null) {
            return;
        }
        click(player);
        openLater(player, new CollectionDetailMenu(plugin, collections, category, collection, 0));
    }

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }
}
