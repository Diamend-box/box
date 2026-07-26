package com.diamend.darksea.npc;

import com.diamend.darksea.DarkSeaPlugin;
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
import java.util.List;

/**
 * The in-game shop editor: {@code /ds shop}. Every price, lot size and line in
 * {@code shops.yml} is editable here, and — the point of the thing — a new
 * line is added by clicking an item in your own inventory, which captures the
 * whole stack. Name, lore, enchants, custom model data, another plugin's NBT:
 * if you can hold it, you can sell it.
 *
 * <p>Nothing in this editor ever moves an item. Adding a line <em>copies</em>
 * what you clicked; your stack stays exactly where it was. That is deliberate
 * — an editor that shuffles real inventory is one dropped click away from
 * eating somebody's gear, and an admin tool should not have that failure mode
 * at all.
 *
 * <p>Every change writes {@code shops.yml} immediately and swaps the live
 * snapshot, so a crash between edits can't lose work and open shop boards pick
 * the change up the next time they are drawn.
 */
public final class ShopEditorService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BOARD_SIZE = 54;
    private static final int SMALL_SIZE = 27;

    /** Where a LIST board stops showing lines and starts showing controls. */
    private static final int LIST_CONTROL_ROW = 45;

    /** The price a freshly added line starts at, before you click it up or down. */
    public static final int DEFAULT_PRICE = 10;

    private final DarkSeaPlugin plugin;

    public ShopEditorService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    public void openPicker(Player admin) {
        ShopEditor editor = new ShopEditor(ShopEditor.Page.PICKER, null, null);
        show(admin, editor, SMALL_SIZE, "editor-title-picker");
    }

    public void openList(Player admin, NpcType npc, ShopEditor.ListKind kind) {
        ShopEditor editor = new ShopEditor(ShopEditor.Page.LIST, kind, npc);
        show(admin, editor, BOARD_SIZE, "editor-title-list");
    }

    public void openSettings(Player admin) {
        ShopEditor editor = new ShopEditor(ShopEditor.Page.SETTINGS, null, null);
        show(admin, editor, SMALL_SIZE, "editor-title-settings");
    }

    private void show(Player admin, ShopEditor editor, int size, String titleKey) {
        Inventory inv = plugin.getServer().createInventory(editor, size,
                plugin.messages().component(titleKey,
                        "list", editor.listKind() == null ? "" : label(editor)));
        editor.setInventory(inv);
        populate(editor);
        admin.openInventory(inv);
    }

    private String label(ShopEditor editor) {
        return switch (editor.listKind()) {
            case NPC_BUY -> editor.npc().id() + " / sells";
            case NPC_SELL -> editor.npc().id() + " / buys";
            case POOL -> "black market rotation";
            case SALVAGE -> "salvage prices";
        };
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void populate(ShopEditor editor) {
        Inventory inv = editor.getInventory();
        inv.clear();
        editor.clear();
        switch (editor.page()) {
            case PICKER -> drawPicker(editor, inv);
            case LIST -> drawList(editor, inv);
            case SETTINGS -> drawSettings(editor, inv);
        }
    }

    private void drawPicker(ShopEditor editor, Inventory inv) {
        int slot = 10;
        for (NpcType type : NpcType.values()) {
            ItemStack icon = button(iconFor(type), type.displayName(), List.of(
                    plugin.messages().component("editor-pick-npc-hint"),
                    plugin.messages().component("editor-pick-npc-counts",
                            "sells", String.valueOf(plugin.shopStock()
                                    .fixedFor(type.id(), ShopOffer.Kind.BUY).size()),
                            "buys", String.valueOf(plugin.shopStock()
                                    .fixedFor(type.id(), ShopOffer.Kind.SELL).size()))));
            inv.setItem(slot, icon);
            editor.putListEntry(slot, type, ShopEditor.ListKind.NPC_BUY);
            slot++;
        }
        inv.setItem(16, button(Material.ENDER_EYE, "<dark_purple>Black market rotation</dark_purple>",
                List.of(plugin.messages().component("editor-pick-pool",
                        "count", String.valueOf(plugin.shopStock().rotatingPool().size()),
                        "slots", String.valueOf(plugin.shopStock().slots())))));
        editor.putListEntry(16, null, ShopEditor.ListKind.POOL);

        inv.setItem(17, button(Material.IRON_SWORD, "<gold>Salvage prices</gold>",
                List.of(plugin.messages().component("editor-pick-salvage",
                        "count", String.valueOf(plugin.shopStock().salvage().size())))));
        editor.putListEntry(17, null, ShopEditor.ListKind.SALVAGE);

        inv.setItem(22, button(Material.COMPARATOR, "<aqua>Rates & clue ladder</aqua>",
                List.of(plugin.messages().component("editor-pick-settings"))));
        editor.putButton(22, ShopEditor.Button.OPEN_SETTINGS);
    }

    private void drawList(ShopEditor editor, Inventory inv) {
        List<ShopOffer> lines = currentList(editor);
        for (int i = 0; i < lines.size() && i < LIST_CONTROL_ROW; i++) {
            inv.setItem(i, renderLine(lines.get(i)));
            editor.putLine(i, i);
        }

        inv.setItem(LIST_CONTROL_ROW, button(Material.ARROW,
                "<gray>Back</gray>", List.of()));
        editor.putButton(LIST_CONTROL_ROW, ShopEditor.Button.BACK);

        if (editor.listKind().isNpcList()) {
            boolean takes = plugin.shopStock().takesSalvage().contains(editor.npcId());
            inv.setItem(47, button(takes ? Material.LIME_DYE : Material.GRAY_DYE,
                    "<white>Standing salvage offer: " + (takes ? "on" : "off") + "</white>",
                    List.of(plugin.messages().component("editor-salvage-toggle-hint"))));
            editor.putButton(47, ShopEditor.Button.TOGGLE_SALVAGE);
        }

        inv.setItem(53, button(Material.WRITABLE_BOOK,
                "<yellow>How to edit</yellow>", helpLines()));
    }

    private void drawSettings(ShopEditor editor, Inventory inv) {
        ShopStock stock = plugin.shopStock();

        inv.setItem(0, button(Material.GOLD_INGOT,
                "<white>Markup: " + trim(stock.markup()) + "x</white>",
                List.of(plugin.messages().component("editor-markup-hint"))));
        editor.putButton(0, ShopEditor.Button.MARKUP_UP);
        inv.setItem(9, button(Material.REDSTONE, "<red>Markup down</red>", List.of()));
        editor.putButton(9, ShopEditor.Button.MARKUP_DOWN);

        inv.setItem(1, button(Material.EMERALD,
                "<white>Salvage rate: " + trim(stock.salvageRate()) + "x</white>",
                List.of(plugin.messages().component("editor-salvage-rate-hint"))));
        editor.putButton(1, ShopEditor.Button.SALVAGE_RATE_UP);
        inv.setItem(10, button(Material.REDSTONE, "<red>Salvage rate down</red>", List.of()));
        editor.putButton(10, ShopEditor.Button.SALVAGE_RATE_DOWN);

        inv.setItem(2, button(Material.ENDER_EYE,
                "<white>Rotation slots: " + stock.slots() + "</white>",
                List.of(plugin.messages().component("editor-slots-hint",
                        "pool", String.valueOf(stock.rotatingPool().size())))));
        editor.putButton(2, ShopEditor.Button.SLOTS_UP);
        inv.setItem(11, button(Material.REDSTONE, "<red>Slots down</red>", List.of()));
        editor.putButton(11, ShopEditor.Button.SLOTS_DOWN);

        List<Integer> costs = stock.clueCosts();
        for (int i = 0; i < costs.size() && i < 7; i++) {
            int slot = 18 + i;
            inv.setItem(slot, button(Material.MAP,
                    "<color:#7fb2c4>Rumour " + (i + 1) + ": " + costs.get(i) + "</color>",
                    List.of(plugin.messages().component("editor-clue-hint"))));
            editor.putClueRung(slot, i);
        }
        inv.setItem(25, button(Material.WRITABLE_BOOK, "<green>Add a rumour</green>",
                List.of(plugin.messages().component("editor-clue-add-hint"))));
        editor.putButton(25, ShopEditor.Button.ADD_CLUE_RUNG);
        inv.setItem(26, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        editor.putButton(26, ShopEditor.Button.BACK);
    }

    private List<Component> helpLines() {
        return List.of(
                plugin.messages().component("editor-help-add"),
                plugin.messages().component("editor-help-price"),
                plugin.messages().component("editor-help-amount"),
                plugin.messages().component("editor-help-delete"));
    }

    /** A shop line as an editor tile: the real item, with its numbers under it. */
    private ItemStack renderLine(ShopOffer offer) {
        ItemStack stack = itemFor(offer);
        if (stack == null) {
            stack = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(noItalic(plugin.messages().component("editor-line-numbers",
                "amount", String.valueOf(offer.amount()),
                "price", String.valueOf(offer.price()))));
        lore.add(noItalic(plugin.messages().component("editor-line-source",
                "source", sourceOf(offer))));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** How a line will be written to shops.yml — worth showing, since it varies. */
    private String sourceOf(ShopOffer offer) {
        if (offer.isCustom()) {
            return "custom snapshot";
        }
        if (offer.isVanilla()) {
            return offer.vanillaMaterial().toLowerCase(java.util.Locale.ROOT);
        }
        return offer.itemId();
    }

    private ItemStack button(Material material, String miniMessageName, List<Component> lore) {
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

    private static Material iconFor(NpcType type) {
        return switch (type) {
            case REFUGEE_TRADER -> Material.EMERALD;
            case ARTIFICER -> Material.ANVIL;
            case BLACK_MARKET -> Material.ENDER_CHEST;
            case BOAT_EXPERT -> Material.OAK_BOAT;
            case APOTHECARY -> Material.BREWING_STAND;
        };
    }

    private static String trim(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopEditor editor)) {
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

        // A click down in your own pack means "put this on the shelf".
        if (event.getClickedInventory() != null
                && event.getClickedInventory() != editor.getInventory()) {
            if (editor.page() == ShopEditor.Page.LIST) {
                addFromInventory(admin, editor, event.getCurrentItem());
            }
            return;
        }

        switch (editor.page()) {
            case PICKER -> handlePickerClick(admin, editor, event);
            case LIST -> handleListClick(admin, editor, event);
            case SETTINGS -> handleSettingsClick(admin, editor, event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopEditor) {
            event.setCancelled(true);
        }
    }

    private void handlePickerClick(Player admin, ShopEditor editor, InventoryClickEvent event) {
        if (editor.buttonAt(event.getSlot()) == ShopEditor.Button.OPEN_SETTINGS) {
            openSettings(admin);
            return;
        }
        ShopEditor.ListKind kind = editor.listAt(event.getSlot());
        if (kind == null) {
            return;
        }
        NpcType type = editor.npcAt(event.getSlot());
        if (type != null) {
            // Left opens what they sell, right opens what they buy.
            kind = event.isRightClick()
                    ? ShopEditor.ListKind.NPC_SELL : ShopEditor.ListKind.NPC_BUY;
        }
        openList(admin, type, kind);
    }

    private void handleListClick(Player admin, ShopEditor editor, InventoryClickEvent event) {
        ShopEditor.Button button = editor.buttonAt(event.getSlot());
        if (button == ShopEditor.Button.BACK) {
            openPicker(admin);
            return;
        }
        if (button == ShopEditor.Button.TOGGLE_SALVAGE) {
            boolean takes = plugin.shopStock().takesSalvage().contains(editor.npcId());
            persist(plugin.shopStock().withSalvageTaker(editor.npcId(), !takes));
            populate(editor);
            return;
        }
        Integer index = editor.lineAt(event.getSlot());
        if (index == null) {
            return;
        }
        List<ShopOffer> lines = new ArrayList<>(currentList(editor));
        if (index >= lines.size()) {
            populate(editor);  // the board went stale; redraw rather than guess
            return;
        }
        ShopOffer offer = lines.get(index);

        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            lines.remove(index);
            persist(applyList(editor, lines));
            admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.3f);
            plugin.messages().send(admin, "editor-removed", "item", sourceOf(offer));
            populate(editor);
            return;
        }
        if (event.getClick() == ClickType.MIDDLE) {
            lines.set(index, offer.withAmount(offer.amount() + 1));
            persist(applyList(editor, lines));
            populate(editor);
            return;
        }
        int step = event.isShiftClick() ? 10 : 1;
        int delta = event.isRightClick() ? -step : step;
        lines.set(index, offer.withPrice(offer.price() + delta));
        persist(applyList(editor, lines));
        admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f,
                delta > 0 ? 1.6f : 0.8f);
        populate(editor);
    }

    private void handleSettingsClick(Player admin, ShopEditor editor, InventoryClickEvent event) {
        ShopStock stock = plugin.shopStock();
        ShopEditor.Button button = editor.buttonAt(event.getSlot());
        if (button == ShopEditor.Button.BACK) {
            openPicker(admin);
            return;
        }
        Integer rung = editor.clueRungAt(event.getSlot());
        if (rung != null) {
            List<Integer> costs = new ArrayList<>(stock.clueCosts());
            if (event.getClick() == ClickType.DROP) {
                costs.remove((int) rung);
            } else {
                int step = event.isShiftClick() ? 100 : 10;
                int updated = costs.get(rung) + (event.isRightClick() ? -step : step);
                costs.set(rung, Math.max(1, updated));
            }
            persist(stock.withClueCosts(costs));
            populate(editor);
            return;
        }
        if (button == null) {
            return;
        }
        ShopStock updated = switch (button) {
            case MARKUP_UP -> stock.withMarketSettings(
                    stock.markup() + 0.1, stock.salvageRate(), stock.slots());
            case MARKUP_DOWN -> stock.withMarketSettings(
                    stock.markup() - 0.1, stock.salvageRate(), stock.slots());
            case SALVAGE_RATE_UP -> stock.withMarketSettings(
                    stock.markup(), stock.salvageRate() + 0.1, stock.slots());
            case SALVAGE_RATE_DOWN -> stock.withMarketSettings(
                    stock.markup(), stock.salvageRate() - 0.1, stock.slots());
            case SLOTS_UP -> stock.withMarketSettings(
                    stock.markup(), stock.salvageRate(), stock.slots() + 1);
            case SLOTS_DOWN -> stock.withMarketSettings(
                    stock.markup(), stock.salvageRate(), stock.slots() - 1);
            case ADD_CLUE_RUNG -> {
                List<Integer> costs = new ArrayList<>(stock.clueCosts());
                costs.add(costs.isEmpty() ? 50 : costs.get(costs.size() - 1) * 2);
                yield stock.withClueCosts(costs);
            }
            default -> null;
        };
        if (updated != null) {
            persist(updated);
            populate(editor);
        }
    }

    // ------------------------------------------------------------------
    // Adding a line off your own hotbar
    // ------------------------------------------------------------------

    /**
     * Copies a clicked stack onto the shelf. The id is chosen to keep
     * shops.yml as readable as possible: a registry item writes its own id, a
     * plain vanilla item writes {@code vanilla:MATERIAL}, and only something
     * with real item data falls back to a base64 snapshot.
     */
    private void addFromInventory(Player admin, ShopEditor editor, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String id = ShopItems.encode(clicked, plugin.getLogger());
        if (id == null) {
            plugin.messages().send(admin, "editor-cannot-encode");
            return;
        }
        List<ShopOffer> lines = new ArrayList<>(currentList(editor));
        if (lines.size() >= LIST_CONTROL_ROW) {
            plugin.messages().send(admin, "editor-list-full");
            return;
        }
        lines.add(new ShopOffer(id, Math.max(1, clicked.getAmount()), DEFAULT_PRICE,
                editor.listKind().offerKind()));
        persist(applyList(editor, lines));
        admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        plugin.messages().send(admin, "editor-added",
                "item", id.startsWith(ShopOffer.CUSTOM) ? "custom snapshot" : id,
                "price", String.valueOf(DEFAULT_PRICE));
        populate(editor);
    }

    private ItemStack itemFor(ShopOffer offer) {
        return ShopItems.resolve(offer, plugin.getLogger());
    }

    // ------------------------------------------------------------------
    // Reading and writing the edited list
    // ------------------------------------------------------------------

    private List<ShopOffer> currentList(ShopEditor editor) {
        ShopStock stock = plugin.shopStock();
        return switch (editor.listKind()) {
            case NPC_BUY -> stock.fixedFor(editor.npcId(), ShopOffer.Kind.BUY);
            case NPC_SELL -> stock.fixedFor(editor.npcId(), ShopOffer.Kind.SELL);
            case POOL -> stock.rotatingPool();
            case SALVAGE -> stock.salvage();
        };
    }

    /**
     * Folds an edited list back into a full snapshot. An NPC's two lists live
     * in one file list, so editing one has to carry the other through
     * untouched rather than overwrite it.
     */
    private ShopStock applyList(ShopEditor editor, List<ShopOffer> lines) {
        ShopStock stock = plugin.shopStock();
        return switch (editor.listKind()) {
            case NPC_BUY -> {
                List<ShopOffer> merged = new ArrayList<>(lines);
                merged.addAll(stock.fixedFor(editor.npcId(), ShopOffer.Kind.SELL));
                yield stock.withFixed(editor.npcId(), merged);
            }
            case NPC_SELL -> {
                List<ShopOffer> merged =
                        new ArrayList<>(stock.fixedFor(editor.npcId(), ShopOffer.Kind.BUY));
                merged.addAll(lines);
                yield stock.withFixed(editor.npcId(), merged);
            }
            case POOL -> stock.withPool(lines);
            case SALVAGE -> stock.withSalvage(lines);
        };
    }

    /** Swaps the live snapshot and writes shops.yml, immediately, every time. */
    private void persist(ShopStock updated) {
        plugin.saveShopStock(updated);
    }
}
