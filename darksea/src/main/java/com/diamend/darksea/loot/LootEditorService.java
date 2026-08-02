package com.diamend.darksea.loot;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.relic.Relic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The in-game loot editor: {@code /ds loot}. Pick a tier and a table, then
 * click anything in your own inventory to put it in that table's pool.
 *
 * <p>Two rules make it safe to hand an admin. Nothing here ever moves a real
 * item — adding a line <em>copies</em> what you clicked, so a mis-click cannot
 * eat your gear. And nothing here can touch a shipped entry: loot.yml's lines
 * are drawn for context, greyed and unclickable, because their weights are the
 * only scale against which a new weight means anything. Everything you add
 * lands in {@code loot-custom.yml}, which this editor owns outright.
 *
 * <p>Every line shows its share of the merged table as a percentage, which is
 * the number that actually decides how often something appears. A raw weight
 * of 5 says nothing on its own; "5.2% per roll, 4 rolls per chest" says
 * everything.
 */
public final class LootEditorService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BOARD_SIZE = 54;
    private static final int PICKER_SIZE = 54;

    /** Where a LIST board stops showing entries and starts showing controls. */
    private static final int LIST_CONTROL_ROW = 45;

    /** The weight a freshly added entry starts at — rare, so a mis-add can't flood a tier. */
    public static final int DEFAULT_WEIGHT = 5;

    private final DarkSeaPlugin plugin;

    public LootEditorService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    public void openPicker(Player admin) {
        LootEditor editor = new LootEditor(LootEditor.Page.PICKER, null);
        show(admin, editor, PICKER_SIZE, "loot-editor-title-picker", "");
    }

    public void openList(Player admin, CustomLoot.Key key) {
        LootEditor editor = new LootEditor(LootEditor.Page.LIST, key);
        show(admin, editor, BOARD_SIZE, "loot-editor-title-list",
                "tier " + key.tier() + (key.vault() ? " vault" : ""));
    }

    private void show(Player admin, LootEditor editor, int size, String titleKey, String label) {
        Inventory inv = plugin.getServer().createInventory(editor, size,
                plugin.messages().component(titleKey, "table", label));
        editor.setInventory(inv);
        populate(editor);
        admin.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void populate(LootEditor editor) {
        Inventory inv = editor.getInventory();
        inv.clear();
        editor.clear();
        if (editor.page() == LootEditor.Page.PICKER) {
            drawPicker(editor, inv);
        } else {
            drawList(editor, inv);
        }
    }

    /**
     * One row per tier: the base table on the left, the vault table beside it.
     * Tiers with no shipped table are skipped — an overlay entry for one would
     * be dropped at merge, so offering to add to it would be a lie.
     */
    private void drawPicker(LootEditor editor, Inventory inv) {
        LootTables shipped = plugin.shippedLoot();
        CustomLoot custom = plugin.customLoot();
        int row = 0;
        for (int tier : sortedTiers(shipped)) {
            int slot = row * 9 + 2;
            inv.setItem(slot, tile(iconFor(tier),
                    "<white>Tier " + tier + " — every chest</white>",
                    countLines(shipped.base().get(tier),
                            custom.entriesFor(new CustomLoot.Key(tier, false)))));
            editor.putTable(slot, new CustomLoot.Key(tier, false));

            LootTable vault = shipped.vault().get(tier);
            if (vault != null) {
                int vaultSlot = slot + 3;
                inv.setItem(vaultSlot, tile(Material.CHEST,
                        "<gold>Tier " + tier + " — vaults only</gold>",
                        countLines(vault,
                                custom.entriesFor(new CustomLoot.Key(tier, true)))));
                editor.putTable(vaultSlot, new CustomLoot.Key(tier, true));
            }
            row++;
            if (row >= 5) {
                break;  // five rows of tiles, one row left for the help book
            }
        }
        inv.setItem(53, tile(Material.WRITABLE_BOOK, "<yellow>How this works</yellow>",
                List.of(plugin.messages().component("loot-editor-help-file"),
                        plugin.messages().component("loot-editor-help-pick"))));
    }

    private List<Component> countLines(LootTable table, List<LootEntry> added) {
        if (table == null) {
            return List.of();
        }
        return List.of(plugin.messages().component("loot-editor-table-counts",
                        "shipped", String.valueOf(table.entries().size()),
                        "added", String.valueOf(added.size())),
                plugin.messages().component("loot-editor-table-rolls",
                        "rolls", String.valueOf(table.rolls()),
                        "cooldown", String.valueOf(table.refillCooldownMinutes())));
    }

    private void drawList(LootEditor editor, Inventory inv) {
        CustomLoot.Key key = editor.key();
        LootTable shipped = plugin.shippedLoot().forChest(key.tier(), key.vault());
        List<LootEntry> added = plugin.customLoot().entriesFor(key);

        // The denominator every percentage is against: what a chest will
        // actually roll once the overlay is merged in.
        int total = 0;
        if (shipped != null) {
            for (LootEntry entry : shipped.entries()) {
                total += entry.weight();
            }
        }
        for (LootEntry entry : added) {
            total += entry.weight();
        }

        int slot = 0;
        if (shipped != null) {
            for (LootEntry entry : shipped.entries()) {
                if (slot >= LIST_CONTROL_ROW) {
                    break;
                }
                inv.setItem(slot++, render(entry, total, false));
            }
        }
        for (int i = 0; i < added.size() && slot < LIST_CONTROL_ROW; i++) {
            inv.setItem(slot, render(added.get(i), total, true));
            editor.putAdded(slot, i);
            slot++;
        }

        inv.setItem(LIST_CONTROL_ROW, tile(Material.ARROW, "<gray>Back</gray>", List.of()));
        editor.putButton(LIST_CONTROL_ROW, LootEditor.Button.BACK);
        inv.setItem(53, tile(Material.WRITABLE_BOOK, "<yellow>How to edit</yellow>",
                List.of(plugin.messages().component("loot-editor-help-add"),
                        plugin.messages().component("loot-editor-help-weight"),
                        plugin.messages().component("loot-editor-help-amount"),
                        plugin.messages().component("loot-editor-help-delete"),
                        plugin.messages().component("loot-editor-help-shipped"))));
    }

    /**
     * An entry as a tile: the item it actually rolls, with its weight, its
     * share of the table and where it is written underneath.
     */
    private ItemStack render(LootEntry entry, int total, boolean editable) {
        ItemStack stack = preview(entry);
        if (stack == null) {
            stack = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(noItalic(plugin.messages().component("loot-editor-line-weight",
                "weight", String.valueOf(entry.weight()),
                "share", share(entry.weight(), total))));
        lore.add(noItalic(plugin.messages().component("loot-editor-line-amount",
                "min", String.valueOf(Math.max(1, entry.min())),
                "max", String.valueOf(Math.max(1, entry.max())))));
        lore.add(noItalic(plugin.messages().component(
                editable ? "loot-editor-line-added" : "loot-editor-line-shipped",
                "source", sourceOf(entry))));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A percentage with one decimal — 0.4% and 0.9% are different answers. */
    private static String share(int weight, int total) {
        if (total <= 0) {
            return "0";
        }
        return String.valueOf(Math.round(weight * 1000.0 / total) / 10.0);
    }

    /**
     * A sample roll, used purely as the tile's icon. Seeded fixed so a board
     * does not reshuffle its own armour pieces every redraw — a tile that
     * changes when you click a different one reads as a bug.
     */
    private ItemStack preview(LootEntry entry) {
        try {
            ItemStack stack = entry.roll(new Random(7), plugin.settings().armor());
            if (stack != null) {
                stack.setAmount(1);
            }
            return stack;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** How the entry is written — worth showing, since the four forms behave differently. */
    private String sourceOf(LootEntry entry) {
        return switch (entry.type()) {
            case ITEM -> entry.material().name().toLowerCase(Locale.ROOT);
            case ARMOR -> "sea armour, tier " + entry.armorTier();
            case TOKEN -> "boat token, level " + entry.tokenLevel();
            case CUSTOM -> entry.customId();
            case SNAPSHOT -> "item snapshot";
        };
    }

    /** Tier icons that read at a glance rather than needing the label read. */
    private static Material iconFor(int tier) {
        return switch (tier) {
            case 1 -> Material.OAK_PLANKS;
            case 2 -> Material.COPPER_INGOT;
            case 3 -> Material.IRON_INGOT;
            case 4 -> Material.DIAMOND;
            default -> Material.ECHO_SHARD;
        };
    }

    private List<Integer> sortedTiers(LootTables shipped) {
        List<Integer> tiers = new ArrayList<>(shipped.base().keySet());
        tiers.sort(null);
        return tiers;
    }

    private ItemStack tile(Material material, String miniMessageName, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(miniMessageName)));
        List<Component> clean = new ArrayList<>(lore.size());
        for (Component component : lore) {
            clean.add(noItalic(component));
        }
        meta.lore(clean);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootEditor editor)) {
            return;
        }
        event.setCancelled(true);  // the editor never moves a real item
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        if (!admin.hasPermission("darksea.admin")) {
            admin.closeInventory();
            plugin.messages().send(admin, "no-permission");
            return;
        }

        // A click down in your own pack means "put this in the pool".
        if (event.getClickedInventory() != null
                && event.getClickedInventory() != editor.getInventory()) {
            if (editor.page() == LootEditor.Page.LIST) {
                addFromInventory(admin, editor, event.getCurrentItem());
            }
            return;
        }

        if (editor.page() == LootEditor.Page.PICKER) {
            CustomLoot.Key key = editor.tableAt(event.getSlot());
            if (key != null) {
                openList(admin, key);
            }
            return;
        }
        handleListClick(admin, editor, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LootEditor) {
            event.setCancelled(true);
        }
    }

    private void handleListClick(Player admin, LootEditor editor, InventoryClickEvent event) {
        if (editor.buttonAt(event.getSlot()) == LootEditor.Button.BACK) {
            openPicker(admin);
            return;
        }
        Integer index = editor.addedAt(event.getSlot());
        if (index == null) {
            return;  // a shipped line or a filler; loot.yml is not editable here
        }
        List<LootEntry> entries = new ArrayList<>(plugin.customLoot().entriesFor(editor.key()));
        if (index >= entries.size()) {
            populate(editor);  // the board went stale; redraw rather than guess
            return;
        }
        LootEntry entry = entries.get(index);

        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            entries.remove(index);
            persist(editor, entries);
            admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.3f);
            plugin.messages().send(admin, "loot-editor-removed", "item", sourceOf(entry));
            populate(editor);
            return;
        }
        if (event.getClick() == ClickType.MIDDLE) {
            // Cycles the stack size rather than growing without bound: a
            // right-click to go back down is already spoken for by weight.
            int max = entry.max() >= 8 ? 1 : entry.max() + 1;
            entries.set(index, entry.withAmount(Math.min(entry.min(), max), max));
            persist(editor, entries);
            populate(editor);
            return;
        }
        int step = event.isShiftClick() ? 10 : 1;
        int delta = event.isRightClick() ? -step : step;
        entries.set(index, entry.withWeight(entry.weight() + delta));
        persist(editor, entries);
        admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f,
                delta > 0 ? 1.6f : 0.8f);
        populate(editor);
    }

    // ------------------------------------------------------------------
    // Adding an entry off your own hotbar
    // ------------------------------------------------------------------

    /**
     * Copies a clicked stack into the pool. The entry form is chosen to keep
     * loot-custom.yml as readable as possible: a registry item or relic writes
     * a CUSTOM entry naming its id, a plain vanilla item writes an ITEM entry
     * naming its material, and only a stack carrying real item data falls back
     * to a base64 SNAPSHOT.
     */
    private void addFromInventory(Player admin, LootEditor editor, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        List<LootEntry> entries = new ArrayList<>(plugin.customLoot().entriesFor(editor.key()));
        if (entries.size() >= LIST_CONTROL_ROW) {
            plugin.messages().send(admin, "loot-editor-list-full");
            return;
        }
        LootEntry entry = entryFor(clicked);
        if (entry == null) {
            plugin.messages().send(admin, "loot-editor-cannot-encode");
            return;
        }
        entries.add(entry);
        persist(editor, entries);
        admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        plugin.messages().send(admin, "loot-editor-added",
                "item", sourceOf(entry),
                "weight", String.valueOf(DEFAULT_WEIGHT));
        populate(editor);
    }

    /** The most readable entry that reproduces this stack, or null if none can. */
    private LootEntry entryFor(ItemStack stack) {
        int amount = Math.max(1, stack.getAmount());
        Relic relic = Relic.of(stack);
        String registryId = relic != null ? relic.id() : DarkSeaItems.idOf(stack);
        if (registryId != null) {
            return new LootEntry(LootEntry.Type.CUSTOM, null, amount, amount, null,
                    List.of(), 0, 0, registryId, DEFAULT_WEIGHT);
        }
        if (isPlain(stack)) {
            return new LootEntry(LootEntry.Type.ITEM, stack.getType(), amount, amount, null,
                    List.of(), 0, 0, null, DEFAULT_WEIGHT);
        }
        try {
            ItemStack one = stack.clone();
            one.setAmount(1);
            String data = Base64.getEncoder().encodeToString(one.serializeAsBytes());
            return new LootEntry(LootEntry.Type.SNAPSHOT, null, amount, amount, null,
                    List.of(), 0, 0, data, DEFAULT_WEIGHT);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not snapshot " + stack.getType()
                    + " for the loot pool: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Whether a stack is an ordinary item of its type. Asked by comparing
     * serialised forms, because that is exactly the question: if snapshotting
     * this stack yields the same bytes as snapshotting a bare one, the
     * snapshot carries nothing and the readable material name is better.
     */
    private static boolean isPlain(ItemStack stack) {
        try {
            ItemStack one = stack.clone();
            one.setAmount(1);
            return java.util.Arrays.equals(one.serializeAsBytes(),
                    new ItemStack(stack.getType()).serializeAsBytes());
        } catch (RuntimeException ex) {
            return !stack.hasItemMeta();
        }
    }

    /** Swaps the live overlay and writes loot-custom.yml, immediately, every time. */
    private void persist(LootEditor editor, List<LootEntry> entries) {
        plugin.saveCustomLoot(plugin.customLoot().with(editor.key(), entries));
    }
}
