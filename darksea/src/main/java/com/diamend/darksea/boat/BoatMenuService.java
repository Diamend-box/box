package com.diamend.darksea.boat;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.naval.NavalMath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The boat wheel — the plugin's first chest GUI. A captain sneak-right-clicks
 * their own boat (or runs {@code /ds boat menu}) to open a small board that
 * lets them upgrade the hull, read its stats and live HP, patch it up at the
 * home dry-dock, and stow it as a {@link DarkSeaItems#DARK_SEA_BOAT} item.
 *
 * <p>Two rules keep it honest in PvP: stowing is blocked while the hull is
 * combat-tagged, so nobody pockets their boat out from under a ram to deny the
 * kill; and repair only works inside the home sanctuary, so a wounded captain
 * has to sail all the way back to safety to use it.
 */
public final class BoatMenuService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 27;
    // Main wheel
    private static final int UPGRADE_SLOT = 11;
    private static final int STATS_SLOT = 13;
    private static final int REPAIR_SLOT = 15;
    private static final int OUTFIT_SLOT = 20;
    private static final int PICKUP_SLOT = 24;
    // Outfit page
    private static final int OUTFIT_INFO_SLOT = 4;
    private static final int OUTFIT_SPEED_SLOT = 10;
    private static final int OUTFIT_TOUGH_SLOT = 12;
    private static final int OUTFIT_HP_SLOT = 14;
    private static final int OUTFIT_RAM_SLOT = 16;
    private static final int OUTFIT_BACK_SLOT = 18;
    private static final int OUTFIT_RESET_SLOT = 26;

    private final DarkSeaPlugin plugin;

    public BoatMenuService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    /** Sneak-right-click your own Dark Sea boat: open the wheel, don't board. */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Boat boat)) {
            return;  // fire once, on the main hand, for boats only
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()
                || !plugin.isDarkSea(boat.getWorld())) {
            return;  // a plain right-click still boards; only in the Dark Sea
        }
        UUID owner = BoatService.ownerOf(boat);
        if (owner == null) {
            BoatService.claim(boat, player);  // first touch claims an unowned hull
        } else if (!owner.equals(player.getUniqueId())) {
            return;  // someone else's boat — leave it to vanilla
        }
        event.setCancelled(true);
        open(player, boat);
    }

    /** Opens the wheel for a boat. Called by the listener and {@code /ds boat menu}. */
    public void open(Player player, Boat boat) {
        openPage(player, boat, BoatMenu.Page.MAIN);
    }

    private void openPage(Player player, Boat boat, BoatMenu.Page page) {
        BoatMenu holder = new BoatMenu(boat.getUniqueId(), page);
        String title = page == BoatMenu.Page.OUTFIT
                ? "<dark_aqua>Boat Outfit</dark_aqua>" : "<dark_aqua>Boat Wheel</dark_aqua>";
        Inventory inv = plugin.getServer().createInventory(holder, MENU_SIZE, MM.deserialize(title));
        holder.setInventory(inv);
        populate(inv, page, player, boat);
        player.openInventory(inv);
    }

    /**
     * The boat {@code /ds boat menu} should act on: the one you're riding, else
     * the nearest boat you own within reach. Null if there's nothing to open.
     */
    public Boat targetBoat(Player player) {
        if (player.getVehicle() instanceof Boat riding && BoatService.isOwner(riding, player)) {
            return riding;
        }
        Boat best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity nearby : player.getNearbyEntities(5, 3, 5)) {
            if (nearby instanceof Boat boat && BoatService.isOwner(boat, player)) {
                double dist = nearby.getLocation().distanceSquared(player.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = boat;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BoatMenu menu)) {
            return;
        }
        event.setCancelled(true);  // a display board: items never move
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != menu) {
            return;  // clicks in the player's own pack do nothing
        }
        Boat boat = boatById(menu.boatId());
        if (boat == null || !boat.isValid()) {
            player.closeInventory();
            plugin.messages().send(player, "boat-menu-gone");
            return;
        }
        if (menu.page() == BoatMenu.Page.OUTFIT) {
            handleOutfitClick(event, player, boat);
            return;
        }
        switch (event.getSlot()) {
            case UPGRADE_SLOT -> {
                plugin.boat().upgrade(player);
                populate(event.getInventory(), BoatMenu.Page.MAIN, player, boat);
            }
            case REPAIR_SLOT -> {
                repair(player, boat);
                if (boat.isValid()) {
                    populate(event.getInventory(), BoatMenu.Page.MAIN, player, boat);
                }
            }
            case OUTFIT_SLOT -> openPage(player, boat, BoatMenu.Page.OUTFIT);
            case PICKUP_SLOT -> pickup(player, boat);
            default -> {
            }
        }
    }

    private void handleOutfitClick(InventoryClickEvent event, Player player, Boat boat) {
        if (event.getSlot() == OUTFIT_BACK_SLOT) {
            openPage(player, boat, BoatMenu.Page.MAIN);
            return;
        }
        // No respeccing or buffing a hull out from under an active fight.
        if (plugin.naval().isCombatTagged(boat)) {
            plugin.messages().send(player, "boat-outfit-combat");
            return;
        }
        BoatService.StatId stat = switch (event.getSlot()) {
            case OUTFIT_SPEED_SLOT -> BoatService.StatId.SPEED;
            case OUTFIT_TOUGH_SLOT -> BoatService.StatId.TOUGHNESS;
            case OUTFIT_HP_SLOT -> BoatService.StatId.HP;
            case OUTFIT_RAM_SLOT -> BoatService.StatId.RAM_POWER;
            default -> null;
        };
        if (stat != null) {
            if (plugin.boat().allocate(player, stat)) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
            } else {
                plugin.messages().send(player, "boat-outfit-no-points");
            }
            populate(event.getInventory(), BoatMenu.Page.OUTFIT, player, boat);
            return;
        }
        if (event.getSlot() == OUTFIT_RESET_SLOT) {
            resetStats(player);
            populate(event.getInventory(), BoatMenu.Page.OUTFIT, player, boat);
        }
    }

    /** Respec: refund every committed point for a Chronon price scaled by how many. */
    private void resetStats(Player player) {
        int spent = plugin.boat().statPoints(player).total();
        if (spent <= 0) {
            plugin.messages().send(player, "boat-outfit-nothing");
            return;
        }
        int cost = plugin.boat().resetCost(player);
        if (!DarkSeaItems.removeChronons(player.getInventory(), cost)) {
            plugin.messages().send(player, "boat-outfit-need-chronons",
                    "cost", String.valueOf(cost),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
            return;
        }
        plugin.boat().resetPoints(player);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        plugin.messages().send(player, "boat-outfit-reset", "cost", String.valueOf(cost));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BoatMenu) {
            event.setCancelled(true);
        }
    }

    /**
     * A wrecked boat never places as a working one. Right-clicking it does
     * nothing at sea but tell you to dock; at the home island it rebuilds into
     * a seaworthy Dark Sea Boat for the full repair price.
     */
    @EventHandler
    public void onUseWreck(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null
                || !DarkSeaItems.BROKEN_DARK_SEA_BOAT.equals(DarkSeaItems.idOf(item))
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);  // a wreck can't be set down as a boat
        repairWreck(event.getPlayer(), item);
    }

    private void repairWreck(Player player, ItemStack wreck) {
        if (!plugin.naval().atHome(player.getLocation())) {
            plugin.messages().send(player, "boat-wreck-away");
            return;
        }
        double maxHp = plugin.naval().maxHpFor(player);
        int cost = NavalMath.repairCost(0, maxHp, plugin.settings().naval().repair().costPerHp());
        if (!DarkSeaItems.removeChronons(player.getInventory(), cost)) {
            plugin.messages().send(player, "boat-repair-need-chronons",
                    "cost", String.valueOf(cost),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
            return;
        }
        wreck.setAmount(wreck.getAmount() - 1);
        giveOrDrop(player, DarkSeaItems.create(DarkSeaItems.DARK_SEA_BOAT, 1));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.9f);
        plugin.messages().send(player, "boat-wreck-repaired", "cost", String.valueOf(cost));
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void repair(Player player, Boat boat) {
        double maxHp = plugin.naval().maxHp(boat);
        double hp = plugin.naval().hullHp(boat);
        if (hp >= maxHp) {
            plugin.messages().send(player, "boat-repair-sound");
            return;
        }
        if (!plugin.naval().atHomeDock(boat)) {
            plugin.messages().send(player, "boat-repair-away");
            return;
        }
        int cost = NavalMath.repairCost(hp, maxHp, plugin.settings().naval().repair().costPerHp());
        if (!DarkSeaItems.removeChronons(player.getInventory(), cost)) {
            plugin.messages().send(player, "boat-repair-need-chronons",
                    "cost", String.valueOf(cost),
                    "have", String.valueOf(DarkSeaItems.countChronons(player.getInventory())));
            return;
        }
        plugin.naval().repairHull(boat);
        boat.getWorld().playSound(boat.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.9f);
        plugin.messages().send(player, "boat-repaired", "cost", String.valueOf(cost));
    }

    private void pickup(Player player, Boat boat) {
        if (plugin.naval().isCombatTagged(boat)) {
            plugin.messages().send(player, "boat-pickup-combat");
            return;
        }
        Player rider = riderOf(boat);
        if (rider != null && !rider.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "boat-pickup-occupied");
            return;
        }
        Location loc = boat.getLocation();
        player.closeInventory();
        plugin.naval().forgetBoat(boat);
        boat.remove();
        giveOrDrop(player, DarkSeaItems.create(DarkSeaItems.DARK_SEA_BOAT, 1));
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.7f);
        plugin.messages().send(player, "boat-stowed");
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    private void populate(Inventory inv, BoatMenu.Page page, Player player, Boat boat) {
        ItemStack filler = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        if (page == BoatMenu.Page.OUTFIT) {
            populateOutfit(inv, player);
            return;
        }
        inv.setItem(UPGRADE_SLOT, upgradeButton(player));
        inv.setItem(STATS_SLOT, statsButton(player, boat));
        inv.setItem(REPAIR_SLOT, repairButton(player, boat));
        inv.setItem(OUTFIT_SLOT, outfitButton(player));
        inv.setItem(PICKUP_SLOT, pickupButton(boat));
    }

    private void populateOutfit(Inventory inv, Player player) {
        var pts = plugin.boat().statPoints(player);
        var cfg = plugin.settings().boat().statPoints();
        int free = plugin.boat().statPointsFree(player);
        inv.setItem(OUTFIT_INFO_SLOT, button(Material.NAUTILUS_SHELL,
                "<aqua>Outfit Your Boat</aqua>", List.of(
                        "<gray>Free points: <yellow>" + free + "</yellow>",
                        "<gray>Earned <white>1</white> per boat level, spent below.",
                        "<dark_gray>Click a stat to invest a point.</dark_gray>")));
        inv.setItem(OUTFIT_SPEED_SLOT, statPointButton(Material.FEATHER, "<aqua>Speed</aqua>",
                pts.speed(), "+" + fmt(cfg.speedPerPoint() * 100) + "% top speed / point", free));
        inv.setItem(OUTFIT_TOUGH_SLOT, statPointButton(Material.SHIELD, "<aqua>Toughness</aqua>",
                pts.toughness(), "+" + fmt(cfg.toughnessPerPoint()) + " damage divisor / point", free));
        inv.setItem(OUTFIT_HP_SLOT, statPointButton(Material.OAK_PLANKS, "<aqua>Hull HP</aqua>",
                pts.hp(), "+" + fmt(cfg.hpPerPoint()) + " hull HP / point", free));
        inv.setItem(OUTFIT_RAM_SLOT, statPointButton(Material.IRON_INGOT, "<aqua>Ram Power</aqua>",
                pts.ramPower(), "+" + fmt(cfg.ramPowerPerPoint()) + "x ram damage / point", free));
        inv.setItem(OUTFIT_BACK_SLOT, button(Material.ARROW, "<aqua>Back</aqua>",
                List.of("<dark_gray>Return to the boat wheel.</dark_gray>")));
        inv.setItem(OUTFIT_RESET_SLOT, resetButton(player));
    }

    private ItemStack upgradeButton(Player player) {
        int current = plugin.boat().levelOf(player);
        int next = current + 1;
        if (!plugin.settings().boat().levels().containsKey(next)) {
            return button(Material.GRAY_DYE, "<gray>Fully Upgraded</gray>",
                    List.of("<dark_gray>Your boat is at the highest level.</dark_gray>"));
        }
        String nextName = plugin.boat().stats(next).name();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: <aqua>" + plugin.boat().stats(current).name()
                + "</aqua> (level " + current + ")");
        lore.add("<gray>Next: <aqua>" + nextName + "</aqua> (level " + next + ")");
        int cost = plugin.boat().upgradeCost(player);
        int have = DarkSeaItems.countChronons(player.getInventory());
        lore.add("<gray>Cost: <aqua>" + cost + " Chronons</aqua> <dark_gray>(you have "
                + have + ")</dark_gray>");
        if (have >= cost) {
            lore.add("<green>Click to buy the " + nextName + ".</green>");
        } else {
            lore.add("<red>Not enough Chronons — " + (cost - have) + " short.</red>");
        }
        return button(Material.NAUTILUS_SHELL, "<aqua>Upgrade Boat</aqua>", lore);
    }

    private ItemStack statsButton(Player player, Boat boat) {
        int level = plugin.boat().levelOf(player);
        var stats = plugin.boat().stats(level);
        var pts = plugin.boat().statPoints(player);
        var cfg = plugin.settings().boat().statPoints();
        double hp = plugin.naval().hullHp(boat);
        double maxHp = plugin.naval().maxHp(boat);
        double speed = stats.speed() + pts.speed() * cfg.speedPerPoint();
        String state = plugin.naval().isCombatTagged(boat)
                ? "<red>In combat</red>"
                : (hp >= maxHp ? "<green>Hull sound</green>" : "<yellow>Repairing</yellow>");
        return button(Material.FILLED_MAP, "<aqua>Boat Stats</aqua>", List.of(
                "<gray>Class: <aqua>" + stats.name() + "</aqua> (level " + level + ")",
                "<gray>Hull: <white>" + fmt(hp) + " / " + fmt(maxHp) + "</white>",
                "<gray>Toughness: <white>" + fmt(plugin.boat().effectiveToughness(player)) + "x</white> <dark_gray>(damage divisor)</dark_gray>",
                "<gray>Ram Power: <white>" + fmt(plugin.naval().ramPower(boat)) + "x</white> <dark_gray>(damage you deal)</dark_gray>",
                "<gray>Shield: <white>" + stats.shield() + "</white>",
                "<gray>Speed: <white>" + fmt(speed) + "x</white>",
                "<gray>Status: " + state));
    }

    private ItemStack repairButton(Player player, Boat boat) {
        double hp = plugin.naval().hullHp(boat);
        double maxHp = plugin.naval().maxHp(boat);
        if (hp >= maxHp) {
            return button(Material.OAK_PLANKS, "<green>Hull Sound</green>",
                    List.of("<dark_gray>Nothing to repair.</dark_gray>"));
        }
        int cost = NavalMath.repairCost(hp, maxHp, plugin.settings().naval().repair().costPerHp());
        int have = DarkSeaItems.countChronons(player.getInventory());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Patch the hull back to full.");
        lore.add("<gray>Cost: <aqua>" + cost + " Chronons</aqua> <dark_gray>(you have "
                + have + ")</dark_gray>");
        if (!plugin.naval().atHomeDock(boat)) {
            lore.add("<red>Only at the home island — sail back to dock.</red>");
        } else if (have < cost) {
            lore.add("<red>Not enough Chronons.</red>");
        } else {
            lore.add("<green>Click to repair.</green>");
        }
        return button(Material.OAK_PLANKS, "<gold>Repair Hull</gold>", lore);
    }

    private ItemStack pickupButton(Boat boat) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Stow this boat as a carryable item.");
        if (plugin.naval().isCombatTagged(boat)) {
            lore.add("<red>Can't stow in combat — wait for the tag to fade.</red>");
        } else {
            lore.add("<green>Click to pick up.</green>");
        }
        return button(Material.DARK_OAK_BOAT, "<aqua>Pick Up Boat</aqua>", lore);
    }

    private ItemStack outfitButton(Player player) {
        int free = plugin.boat().statPointsFree(player);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Spend earned stat points on your boat.");
        if (free > 0) {
            lore.add("<green>" + free + " free point" + (free == 1 ? "" : "s") + " to spend.</green>");
        } else {
            lore.add("<dark_gray>No free points — upgrade to earn more.</dark_gray>");
        }
        return button(Material.NAUTILUS_SHELL, "<aqua>Outfit Boat</aqua>", lore);
    }

    private ItemStack statPointButton(Material material, String name, int invested,
                                      String perPoint, int free) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Invested: <white>" + invested + "</white> point" + (invested == 1 ? "" : "s"));
        lore.add("<gray>" + perPoint);
        if (free > 0) {
            lore.add("<green>Click to invest a point.</green>");
        } else {
            lore.add("<red>No free points.</red>");
        }
        return button(material, name, lore);
    }

    private ItemStack resetButton(Player player) {
        int spent = plugin.boat().statPoints(player).total();
        List<String> lore = new ArrayList<>();
        if (spent <= 0) {
            lore.add("<dark_gray>No points committed yet.</dark_gray>");
            return button(Material.GRAY_DYE, "<gray>Reset Stats</gray>", lore);
        }
        int cost = plugin.boat().resetCost(player);
        int have = DarkSeaItems.countChronons(player.getInventory());
        lore.add("<gray>Refund all <white>" + spent + "</white> committed points.");
        lore.add("<gray>Cost: <aqua>" + cost + " Chronons</aqua> <dark_gray>(you have " + have + ")</dark_gray>");
        lore.add(have >= cost ? "<green>Click to respec.</green>" : "<red>Not enough Chronons.</red>");
        return button(Material.GRINDSTONE, "<gold>Reset Stats</gold>", lore);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(name)));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(noItalic(MM.deserialize(line)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Boat boatById(UUID id) {
        return plugin.getServer().getEntity(id) instanceof Boat boat ? boat : null;
    }

    private static Player riderOf(Boat boat) {
        List<Entity> passengers = boat.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof Player rider ? rider : null;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
