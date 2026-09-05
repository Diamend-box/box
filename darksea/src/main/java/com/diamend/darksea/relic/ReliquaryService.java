package com.diamend.darksea.relic;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.item.DarkSeaItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Naxome Reliquary: the bag a captain keeps woken relics in, and the only
 * place a relic does anything. Right-clicking the item opens a board with the
 * bag's slots along the top and the whole collection below.
 *
 * <p>Two deliberate design calls. First, relics are filed by <em>id</em>
 * rather than stored as items — duplicates never stacked a boost anyway, so a
 * collection of ids is both the honest model and immune to the item-loss bugs
 * a real storage inventory invites. Second, extra slots are bought with cave
 * crystals and nothing else: that is what ties the caves to the surface, and
 * paying in Chronons would let a captain skip the caves entirely.
 */
public final class ReliquaryService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 54;
    private static final int SLOT_ROW = 0;          // slots 0-8: the bag's equip slots
    private static final int INFO_SLOT = 11;
    private static final int UPGRADE_SLOT = 13;
    private static final int DEPOSIT_SLOT = 15;
    private static final int COLLECTION_START = 18; // slots 18+: everything owned

    private final DarkSeaPlugin plugin;

    public ReliquaryService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // The bag itself
    // ------------------------------------------------------------------

    /** Whether the captain is carrying a reliquary — the gate on every relic boost. */
    public boolean hasReliquary(Player player) {
        return DarkSeaItems.hasItem(player.getInventory(), DarkSeaItems.RELIQUARY);
    }

    public int slotCount(Player player) {
        DarkSeaSettings.RelicSettings relics = plugin.settings().relics();
        return ReliquaryMath.slots(relics.bagStartSlots(), relics.bagMaxSlots(),
                plugin.data().relicSlotsBought(player.getUniqueId()));
    }

    /**
     * The relics actually granting boosts right now: what is in the bag's
     * slots, trimmed to the slots that exist. Empty without a reliquary in
     * hand, which is the whole point of the gate.
     */
    public List<Relic> activeRelics(Player player) {
        // Cheapest question first, because this runs for every online player
        // once a second. Reading the bag's slots is one map lookup; proving the
        // bag is actually in the pack means walking all forty-odd inventory
        // slots and pulling each stack's meta, which the server hands over as a
        // fresh copy every time. A captain with nothing filed away gets the
        // same empty answer either way, so the scan is now only paid for by
        // someone it could actually say yes to.
        List<String> equipped = plugin.data().equippedRelics(player.getUniqueId());
        if (equipped.isEmpty() || !hasReliquary(player)) {
            return List.of();
        }
        List<String> ids = ReliquaryMath.effective(
                plugin.data().relicCollection(player.getUniqueId()),
                equipped,
                slotCount(player));
        List<Relic> relics = new ArrayList<>(ids.size());
        for (String id : ids) {
            Relic relic = Relic.byId(id);
            if (relic != null) {
                relics.add(relic);
            }
        }
        return List.copyOf(relics);
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!DarkSeaItems.RELIQUARY.equals(DarkSeaItems.idOf(event.getItem()))) {
            return;
        }
        event.setCancelled(true);  // a bundle would otherwise open its vanilla UI
        open(event.getPlayer());
    }

    /** Opens the reliquary board. Also reachable from {@code /ds relic bag}. */
    public void open(Player player) {
        ReliquaryMenu holder = new ReliquaryMenu();
        Inventory inv = plugin.getServer().createInventory(holder, MENU_SIZE,
                MM.deserialize("<gradient:#c9a227:#fff3c4>Reliquary</gradient>"));
        holder.setInventory(inv);
        populate(inv, player);
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // The board
    // ------------------------------------------------------------------

    private void populate(Inventory inv, Player player) {
        inv.clear();
        DarkSeaSettings.RelicSettings settings = plugin.settings().relics();
        List<String> collection = plugin.data().relicCollection(player.getUniqueId());
        int slots = slotCount(player);
        List<String> equipped = ReliquaryMath.effective(collection,
                plugin.data().equippedRelics(player.getUniqueId()), slots);

        for (int i = 0; i < 9; i++) {
            if (i >= slots) {
                inv.setItem(SLOT_ROW + i, pane(Material.RED_STAINED_GLASS_PANE,
                        "<dark_gray>Locked slot</dark_gray>",
                        List.of("<dark_gray>Buy it below.</dark_gray>")));
            } else if (i < equipped.size()) {
                inv.setItem(SLOT_ROW + i, slotItem(Relic.byId(equipped.get(i))));
            } else {
                inv.setItem(SLOT_ROW + i, pane(Material.LIME_STAINED_GLASS_PANE,
                        "<green>Empty slot</green>",
                        List.of("<gray>Click a relic below to wear it.</gray>")));
            }
        }
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray></dark_gray>", List.of()));
        }
        inv.setItem(INFO_SLOT, infoTile());
        inv.setItem(UPGRADE_SLOT, upgradeTile(player, settings, slots));
        inv.setItem(DEPOSIT_SLOT, depositTile());

        int index = 0;
        for (String id : collection) {
            Relic relic = Relic.byId(id);
            if (relic == null || COLLECTION_START + index >= MENU_SIZE) {
                continue;
            }
            inv.setItem(COLLECTION_START + index++, collectionItem(relic, equipped.contains(id)));
        }
    }

    private ItemStack slotItem(Relic relic) {
        if (relic == null) {
            return pane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray></dark_gray>", List.of());
        }
        return tile(relic.material(), relic.displayName(),
                List.of("<green>" + relic.boostLine() + "</green>",
                        "<dark_gray>Click to take it off.</dark_gray>"));
    }

    private ItemStack collectionItem(Relic relic, boolean worn) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + relic.boostLine() + "</gray>");
        lore.add(worn ? "<green>Worn.</green>" : "<dark_gray>Click to wear it.</dark_gray>");
        lore.add("<dark_gray>Shift-click: take it back out.</dark_gray>");
        return tile(relic.material(), relic.displayName(), lore);
    }

    private ItemStack upgradeTile(Player player, DarkSeaSettings.RelicSettings settings, int slots) {
        int bought = plugin.data().relicSlotsBought(player.getUniqueId());
        DarkSeaSettings.SlotCost cost = settings.costForSlot(bought);
        if (!ReliquaryMath.canUpgrade(slots, settings.bagMaxSlots(), cost != null)) {
            return tile(Material.AMETHYST_BLOCK, "<gold>Reliquary</gold>",
                    List.of("<gray>Slots: <white>" + slots + "</white></gray>",
                            "<dark_gray>The bag is as deep as it goes.</dark_gray>"));
        }
        int have = DarkSeaItems.countItems(player.getInventory(), cost.itemId());
        String crystal = crystalName(cost.itemId());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Slots: <white>" + slots + "</white> of " + settings.bagMaxSlots() + "</gray>");
        lore.add("<gray>Next slot: <light_purple>" + cost.amount() + " " + crystal
                + "</light_purple> <dark_gray>(you have " + have + ")</dark_gray></gray>");
        lore.add(have >= cost.amount()
                ? "<green>Click to deepen the reliquary.</green>"
                : "<red>Not enough — " + (cost.amount() - have) + " short.</red>");
        lore.add("<dark_gray>Crystals come from the caves.</dark_gray>");
        return tile(Material.AMETHYST_CLUSTER, "<gold>Deepen the Reliquary</gold>", lore);
    }

    /**
     * The note that answers the first question anyone opening this asks: does
     * putting a relic in here cost me anything? It does not, and saying so on
     * a piece of paper is cheaper than a player never using the bag because
     * they assumed storage meant switched off.
     */
    private ItemStack infoTile() {
        return tile(Material.PAPER, "<white>How the Reliquary works</white>",
                List.of("<gray>Relics in the slots above give the</gray>",
                        "<gray>same bonus as carrying them did.</gray>",
                        "<dark_gray>They only work from in here now.</dark_gray>",
                        "<dark_gray>Click one below to put it in a slot.</dark_gray>"));
    }

    private ItemStack depositTile() {
        return tile(Material.CHEST, "<aqua>File your relics</aqua>",
                List.of("<gray>Puts every woken relic you are</gray>",
                        "<gray>carrying into the collection.</gray>",
                        "<dark_gray>Dormant relics must be woken first.</dark_gray>"));
    }

    /**
     * A price line reads better as "Emberglass" than "emberglass". Built from
     * the id rather than the item's display name on purpose: the name is
     * config-editable cosmetics, and a renamed crystal should still price the
     * slot under the id the config actually uses.
     */
    static String crystalName(String itemId) {
        String[] words = itemId.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? itemId : out.toString();
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReliquaryMenu menu)) {
            return;
        }
        event.setCancelled(true);  // a board, not a container: nothing is dragged in or out
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != menu) {
            return;
        }
        int slot = event.getSlot();
        if (slot == UPGRADE_SLOT) {
            buySlot(player);
        } else if (slot == DEPOSIT_SLOT) {
            depositCarried(player);
        } else if (slot < 9) {
            takeOff(player, slot);
        } else if (slot >= COLLECTION_START) {
            clickCollection(player, slot - COLLECTION_START, event.isShiftClick());
        } else {
            return;
        }
        populate(event.getInventory(), player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ReliquaryMenu) {
            event.setCancelled(true);
        }
    }

    private void takeOff(Player player, int index) {
        List<String> collection = plugin.data().relicCollection(player.getUniqueId());
        List<String> equipped = ReliquaryMath.effective(collection,
                plugin.data().equippedRelics(player.getUniqueId()), slotCount(player));
        if (index >= equipped.size()) {
            return;
        }
        plugin.data().setRelics(player.getUniqueId(), collection,
                ReliquaryMath.unequip(equipped, equipped.get(index)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.2f);
    }

    private void clickCollection(Player player, int index, boolean withdraw) {
        List<String> collection = plugin.data().relicCollection(player.getUniqueId());
        if (index < 0 || index >= collection.size()) {
            return;
        }
        String id = collection.get(index);
        int slots = slotCount(player);
        List<String> equipped = ReliquaryMath.effective(collection,
                plugin.data().equippedRelics(player.getUniqueId()), slots);
        if (withdraw) {
            withdraw(player, collection, equipped, id);
            return;
        }
        if (equipped.contains(id)) {
            plugin.data().setRelics(player.getUniqueId(), collection,
                    ReliquaryMath.unequip(equipped, id));
            return;
        }
        List<String> next = ReliquaryMath.equip(collection, equipped, id, slots);
        if (next == null) {
            plugin.messages().send(player, "reliquary-full", "{slots}", String.valueOf(slots));
            return;
        }
        plugin.data().setRelics(player.getUniqueId(), collection, next);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    /** Hands a filed relic back as an item, so relics stay tradeable. */
    private void withdraw(Player player, List<String> collection, List<String> equipped, String id) {
        Relic relic = Relic.byId(id);
        if (relic == null) {
            return;
        }
        ItemStack item = relic.createDormant();
        relic.wake(item);
        List<String> next = new ArrayList<>(collection);
        next.remove(id);
        plugin.data().setRelics(player.getUniqueId(), next,
                ReliquaryMath.unequip(equipped, id));
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        plugin.messages().send(player, "reliquary-withdrawn");
    }

    /** Files every woken relic in the captain's pack, consuming the items. */
    private void depositCarried(Player player) {
        List<String> collection = plugin.data().relicCollection(player.getUniqueId());
        List<String> updated = collection;
        int filed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            Relic relic = Relic.of(item);
            if (relic == null || !Relic.isAwake(item)) {
                continue;
            }
            updated = ReliquaryMath.deposit(updated, relic.id());
            player.getInventory().setItem(i, null);
            filed++;
        }
        if (filed == 0) {
            plugin.messages().send(player, "reliquary-nothing-to-file");
            return;
        }
        plugin.data().setRelics(player.getUniqueId(), updated,
                plugin.data().equippedRelics(player.getUniqueId()));
        plugin.messages().send(player, "reliquary-filed", "{count}", String.valueOf(filed));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.0f);
    }

    private void buySlot(Player player) {
        DarkSeaSettings.RelicSettings settings = plugin.settings().relics();
        int bought = plugin.data().relicSlotsBought(player.getUniqueId());
        int slots = slotCount(player);
        DarkSeaSettings.SlotCost cost = settings.costForSlot(bought);
        if (!ReliquaryMath.canUpgrade(slots, settings.bagMaxSlots(), cost != null)) {
            plugin.messages().send(player, "reliquary-maxed");
            return;
        }
        // Bill before granting: removeItems is all-or-nothing, so a short
        // captain is never charged a partial price for a slot they don't get.
        if (!DarkSeaItems.removeItems(player.getInventory(), cost.itemId(), cost.amount())) {
            plugin.messages().send(player, "reliquary-need-crystals",
                    "{cost}", String.valueOf(cost.amount()),
                    "{crystal}", crystalName(cost.itemId()),
                    "{have}", String.valueOf(
                            DarkSeaItems.countItems(player.getInventory(), cost.itemId())));
            return;
        }
        plugin.data().setRelicSlotsBought(player.getUniqueId(), bought + 1);
        plugin.messages().send(player, "reliquary-deepened", "{slots}", String.valueOf(slots + 1));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Tiles
    // ------------------------------------------------------------------

    private ItemStack tile(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(name)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(noItalic(MM.deserialize(line)));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material material, String name, List<String> lore) {
        return tile(material, name, lore);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
