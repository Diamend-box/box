package com.diamend.spyglass.watch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import com.diamend.spyglass.config.SpyglassConfig;
import com.diamend.spyglass.inspect.ItemFormatter;
import com.diamend.spyglass.util.Fmt;
import com.diamend.spyglass.util.Safe;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;

/**
 * Turns what a watched player does into lines in somebody's console.
 *
 * <p>Everything is {@code MONITOR} priority and reads only: a watch never
 * changes what happens, it only reports it. Each handler leaves immediately when
 * nobody is watching that player, which is the normal case.
 */
public final class WatchListener implements Listener {

    private final WatchManager manager;
    private final Supplier<SpyglassConfig> config;
    private final Server server;

    public WatchListener(WatchManager manager, Supplier<SpyglassConfig> config, Server server) {
        this.manager = manager;
        this.config = config;
        this.server = server;
    }

    // ------------------------------------------------------------------
    // Chat and commands
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CHAT, "chat",
                Fmt.plain(event.message()) + (event.isCancelled() ? "  (cancelled)" : ""));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.COMMAND, "command",
                event.getMessage() + (event.isCancelled() ? "  (cancelled)" : ""));
    }

    // ------------------------------------------------------------------
    // Coming and going
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        startAutoWatch(player);
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CONNECTION, "joined", at(player.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.isWatched(player)) {
            manager.emit(player, WatchCategory.CONNECTION, "left", at(player.getLocation()));
        }
        // A watcher who logs out stops watching; the console never does.
        manager.forgetWatcher(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CONNECTION, "kicked",
                Safe.text(() -> Fmt.plain(event.reason())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CONNECTION, "teleport",
                at(event.getFrom()) + " -> " + at(event.getTo())
                        + "  (" + event.getCause() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CONNECTION, "world",
                event.getFrom().getName() + " -> " + Safe.text(() -> player.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.CONNECTION, "respawn", at(event.getRespawnLocation()));
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emitPosition(player, at(to));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.MOVEMENT, "sneak",
                event.isSneaking() ? "on" : "off");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.MOVEMENT, "sprint",
                event.isSprinting() ? "on" : "off");
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event instanceof CraftItemEvent) {
            return; // reported by onCraft, with the recipe result
        }
        if (!(event.getWhoClicked() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "click",
                Safe.text(() -> event.getInventory().getType()) + " slot " + event.getSlot()
                        + " " + event.getAction()
                        + "  " + ItemFormatter.line(event.getCurrentItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "craft",
                ItemFormatter.line(Safe.call(() -> event.getRecipe().getResult(), null)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "opened",
                Safe.text(() -> event.getInventory().getType().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "closed",
                Safe.text(() -> event.getInventory().getType().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "dropped",
                ItemFormatter.line(Safe.call(() -> event.getItemDrop().getItemStack(), null)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "picked up",
                ItemFormatter.line(Safe.call(() -> event.getItem().getItemStack(), null)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "swap hands",
                "main " + ItemFormatter.line(event.getMainHandItem())
                        + " / off " + ItemFormatter.line(event.getOffHandItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.INVENTORY, "consumed",
                ItemFormatter.line(event.getItem()));
    }

    // ------------------------------------------------------------------
    // The world
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.BLOCKS, "block break",
                Safe.text(() -> event.getBlock().getType().getKey().getKey())
                        + " at " + at(event.getBlock().getLocation())
                        + (event.isCancelled() ? "  (cancelled)" : ""));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.BLOCKS, "block place",
                Safe.text(() -> event.getBlock().getType().getKey().getKey())
                        + " at " + at(event.getBlock().getLocation())
                        + (event.isCancelled() ? "  (cancelled)" : ""));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        Safe.run(() -> {
            for (Component line : event.lines()) {
                lines.add(Fmt.plain(line));
            }
        });
        manager.emit(player, WatchCategory.BLOCKS, "sign",
                at(event.getBlock().getLocation()) + "  " + String.join(" | ", lines));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.BLOCKS, "bucket empty",
                Safe.text(() -> event.getBucket().getKey().getKey())
                        + " at " + at(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.BLOCKS, "bucket fill",
                Safe.text(() -> event.getBucket().getKey().getKey())
                        + " at " + at(event.getBlock().getLocation()));
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager instanceof Player attacker && manager.isWatched(attacker)) {
            manager.emit(attacker, WatchCategory.COMBAT, "hit",
                    describe(victim) + " for " + Fmt.num(event.getFinalDamage()));
        }
        if (victim instanceof Player hurt && manager.isWatched(hurt)) {
            manager.emit(hurt, WatchCategory.COMBAT, "hurt by",
                    describe(damager) + " for " + Fmt.num(event.getFinalDamage())
                            + ", " + Fmt.num(hurt.getHealth()) + " health left");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return; // already reported, with the attacker
        }
        if (!(event.getEntity() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.COMBAT, "hurt",
                event.getCause() + " for " + Fmt.num(event.getFinalDamage())
                        + ", " + Fmt.num(player.getHealth()) + " health left");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.COMBAT, "died",
                Safe.text(() -> Fmt.plain(event.deathMessage())) + " at " + at(player.getLocation())
                        + ", kept " + event.getDrops().size() + " drop(s)");
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.STATE, "game mode",
                Safe.text(player::getGameMode) + " -> " + event.getNewGameMode());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.STATE, "level",
                event.getOldLevel() + " -> " + event.getNewLevel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.STATE, "flight", event.isFlying() ? "on" : "off");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.STATE, "effect",
                event.getAction() + " " + Safe.text(() -> event.getModifiedType().getKey().getKey())
                        + " (" + event.getCause() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (!manager.isWatched(player)) {
            return;
        }
        manager.emit(player, WatchCategory.STATE, "advancement",
                Safe.text(() -> event.getAdvancement().getKey().toString()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Starts the console's watch on a player named in {@code watch.auto}. */
    private void startAutoWatch(Player player) {
        SpyglassConfig settings = config.get();
        if (!settings.isAutoWatched(player.getName())) {
            return;
        }
        Safe.run(() -> manager.add(server.getConsoleSender(), player.getUniqueId(),
                player.getName(), settings.defaultCategories()));
    }

    private static String at(Location location) {
        if (location == null) {
            return "?";
        }
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private static String describe(Entity entity) {
        if (entity == null) {
            return "nothing";
        }
        if (entity instanceof Player player) {
            return "player " + player.getName();
        }
        return Safe.text(() -> entity.getType().toString());
    }
}
