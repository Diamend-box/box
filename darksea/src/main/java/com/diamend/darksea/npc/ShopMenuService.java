package com.diamend.darksea.npc;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.relic.Relic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws and runs the shop boards. Every NPC gets the same double chest: the
 * top rows are what the NPC will sell you, the bottom rows are what they will
 * buy, and any special buttons (the artificer's relic anvil, the boat expert's
 * rumours) sit on the last row.
 *
 * <p>Boards are redrawn after every successful click rather than edited in
 * place, so a purchase that changes what a player can afford is always
 * reflected immediately, and a stale board can never be double-spent.
 */
public final class ShopMenuService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 54;
    private static final int SELL_ROW_START = 27;
    private static final int ACTION_ROW_START = 45;

    private final DarkSeaPlugin plugin;

    public ShopMenuService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void open(Player player, NpcRegistry.Placement placement) {
        ShopMenu menu = new ShopMenu(placement.id(), placement.type());
        Inventory inv = plugin.getServer().createInventory(menu, MENU_SIZE,
                MM.deserialize(placement.type().menuTitle()));
        menu.setInventory(inv);
        populate(menu, player);
        player.openInventory(inv);
    }

    private void populate(ShopMenu menu, Player player) {
        Inventory inv = menu.getInventory();
        inv.clear();
        NpcType type = menu.type();
        long rotation = plugin.npcs().registry().rotation();

        int buySlot = 0;
        int sellSlot = SELL_ROW_START;
        for (ShopOffer offer : plugin.shopStock().offers(type, rotation)) {
            if (offer.kind() == ShopOffer.Kind.BUY) {
                if (buySlot >= SELL_ROW_START) {
                    continue;  // shelf full; the stock tables never come close
                }
                inv.setItem(buySlot, render(offer, player));
                menu.put(buySlot, offer);
                buySlot++;
            } else {
                if (sellSlot >= ACTION_ROW_START) {
                    continue;
                }
                inv.setItem(sellSlot, render(offer, player));
                menu.put(sellSlot, offer);
                sellSlot++;
            }
        }

        int actionSlot = ACTION_ROW_START;
        if (type.wakesRelics()) {
            inv.setItem(actionSlot, wakeButton(player));
            menu.put(actionSlot, ShopMenu.Action.WAKE_RELIC);
            actionSlot++;
        }
        if (type.sellsHeartClues()) {
            inv.setItem(actionSlot, clueButton(player));
            menu.put(actionSlot, ShopMenu.Action.BUY_CLUE);
        }

        // The purse, always in the same corner.
        inv.setItem(MENU_SIZE - 1, purse(player));
    }

    /** An offer's tile: the real item, with the price written under it. */
    private ItemStack render(ShopOffer offer, Player player) {
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
        if (offer.kind() == ShopOffer.Kind.BUY) {
            lore.add(line(plugin.messages().component("shop-lore-buy",
                    "price", String.valueOf(offer.price()),
                    "amount", String.valueOf(offer.amount()))));
        } else {
            lore.add(line(plugin.messages().component("shop-lore-sell",
                    "price", String.valueOf(offer.price()),
                    "amount", String.valueOf(offer.amount()))));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack wakeButton(Player player) {
        ItemStack stack = new ItemStack(Material.ANVIL);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(plugin.messages().component("shop-wake-title")));
        Relic held = Relic.of(player.getInventory().getItemInMainHand());
        List<Component> lore = new ArrayList<>();
        if (held == null) {
            lore.add(line(plugin.messages().component("shop-wake-empty")));
        } else {
            lore.add(line(plugin.messages().component("shop-wake-holding",
                    "name", held.displayName(), "cost", String.valueOf(held.reviveCost()))));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack clueButton(Player player) {
        ItemStack stack = new ItemStack(Material.MAP);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(plugin.messages().component("shop-clue-title")));
        int owned = plugin.data().clueLevel(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        if (owned >= plugin.shopStock().maxClueLevel()) {
            lore.add(line(plugin.messages().component("shop-clue-exhausted")));
        } else {
            lore.add(line(plugin.messages().component("shop-clue-next",
                    "n", String.valueOf(owned + 1),
                    "cost", String.valueOf(plugin.shopStock().clueCost(owned)))));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack purse(Player player) {
        ItemStack stack = DarkSeaItems.create(DarkSeaItems.CHRONON, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(line(plugin.messages().component("shop-purse",
                "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory()))))));
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component line(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** Builds the stack an offer trades in — plugin item, relic, or vanilla. */
    private ItemStack itemFor(ShopOffer offer) {
        if (offer.isVanilla()) {
            Material material = Material.matchMaterial(offer.vanillaMaterial());
            return material == null ? null : new ItemStack(material, offer.amount());
        }
        ItemStack stack = DarkSeaItems.create(offer.itemId(), offer.amount());
        if (stack != null && stack.getAmount() != offer.amount() && stack.getMaxStackSize() > 1) {
            stack.setAmount(offer.amount());
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopMenu menu)) {
            return;
        }
        event.setCancelled(true);  // a board, not a container
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != menu) {
            return;
        }
        ShopOffer offer = menu.offerAt(event.getSlot());
        if (offer != null) {
            if (offer.kind() == ShopOffer.Kind.BUY) {
                buy(player, offer);
            } else {
                sell(player, offer);
            }
            populate(menu, player);
            return;
        }
        ShopMenu.Action action = menu.actionAt(event.getSlot());
        if (action == ShopMenu.Action.WAKE_RELIC) {
            plugin.relics().wakeAtArtificer(player);
            populate(menu, player);
        } else if (action == ShopMenu.Action.BUY_CLUE) {
            buyClue(player);
            populate(menu, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopMenu) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Trading
    // ------------------------------------------------------------------

    private void buy(Player player, ShopOffer offer) {
        ItemStack goods = itemFor(offer);
        if (goods == null) {
            plugin.messages().send(player, "shop-unavailable");
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            plugin.messages().send(player, "shop-no-room");
            return;
        }
        if (!DarkSeaItems.removeChronons(player.getInventory(), offer.price())) {
            plugin.messages().send(player, "shop-need-chronons",
                    "cost", String.valueOf(offer.price()),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
            return;
        }
        giveOrDrop(player, goods);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        plugin.messages().send(player, "shop-bought",
                "amount", String.valueOf(offer.amount()),
                "item", nameOf(goods),
                "cost", String.valueOf(offer.price()));
    }

    private void sell(Player player, ShopOffer offer) {
        if (!takeGoods(player, offer)) {
            plugin.messages().send(player, "shop-nothing-to-sell");
            return;
        }
        giveOrDrop(player, DarkSeaItems.create(DarkSeaItems.CHRONON, offer.price()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
        plugin.messages().send(player, "shop-sold",
                "amount", String.valueOf(offer.amount()),
                "price", String.valueOf(offer.price()));
    }

    /**
     * Removes {@code amount} matching items, all-or-nothing. Relics only count
     * while dormant: a woken relic represents Chronons already spent, and the
     * artificer is not in the business of refunding them.
     */
    private boolean takeGoods(Player player, ShopOffer offer) {
        int needed = offer.amount();
        List<Integer> slots = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        int found = 0;
        for (int i = 0; i < contents.length && found < needed; i++) {
            if (matches(contents[i], offer)) {
                slots.add(i);
                found += contents[i].getAmount();
            }
        }
        if (found < needed) {
            return false;
        }
        int remaining = needed;
        for (int slot : slots) {
            ItemStack stack = contents[slot];
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            remaining -= take;
            if (remaining <= 0) {
                break;
            }
        }
        return true;
    }

    private boolean matches(ItemStack stack, ShopOffer offer) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (offer.isVanilla()) {
            Material material = Material.matchMaterial(offer.vanillaMaterial());
            return material != null && stack.getType() == material;
        }
        Relic relic = Relic.of(stack);
        if (relic != null) {
            return relic.id().equals(offer.itemId()) && !Relic.isAwake(stack);
        }
        return offer.itemId().equals(DarkSeaItems.idOf(stack));
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack spill : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), spill);
        }
    }

    private String nameOf(ItemStack stack) {
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ------------------------------------------------------------------
    // The old man's rumours
    // ------------------------------------------------------------------

    /**
     * Buys the next rung of the clue ladder. The first three are flavour with
     * real information buried in them; the fourth is live — the bearing and
     * range from where the buyer stands to the nearest standing Core nest,
     * which is where the Heart's trail actually starts.
     */
    private void buyClue(Player player) {
        int owned = plugin.data().clueLevel(player.getUniqueId());
        int maxLevel = plugin.shopStock().maxClueLevel();
        if (owned >= maxLevel) {
            plugin.messages().send(player, "shop-clue-exhausted");
            return;
        }
        int cost = plugin.shopStock().clueCost(owned);
        if (!DarkSeaItems.removeChronons(player.getInventory(), cost)) {
            plugin.messages().send(player, "shop-need-chronons",
                    "cost", String.valueOf(cost),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
            return;
        }
        int level = owned + 1;
        plugin.data().setClueLevel(player.getUniqueId(), level);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 0.8f);
        plugin.messages().send(player, "shop-clue-" + level);
        if (level >= maxLevel) {
            tellBearing(player);
        }
    }

    /** The live rung: heading and range to the nearest Core nest, or a shrug. */
    private void tellBearing(Player player) {
        IslandInstance nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (IslandInstance island : plugin.registry().byTier(5)) {
            double distSq = island.origin().distanceSquared2D(
                    player.getLocation().getX(), player.getLocation().getZ());
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = island;
            }
        }
        if (nearest == null) {
            plugin.messages().send(player, "shop-clue-no-bearing");
            return;
        }
        double dx = nearest.origin().x() - player.getLocation().getX();
        double dz = nearest.origin().z() - player.getLocation().getZ();
        plugin.messages().send(player, "shop-clue-bearing",
                "heading", compass(dx, dz),
                "blocks", String.valueOf((int) Math.sqrt(bestSq)));
    }

    /** Eight-point heading for a world-space delta (+X east, +Z south). */
    public static String compass(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(((degrees % 360) + 360) % 360 / 45.0) % 8;
        return switch (index) {
            case 0 -> "north";
            case 1 -> "north-east";
            case 2 -> "east";
            case 3 -> "south-east";
            case 4 -> "south";
            case 5 -> "south-west";
            case 6 -> "west";
            default -> "north-west";
        };
    }
}
