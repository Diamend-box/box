package com.diamend.anticheat.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.diamend.anticheat.AntiCheatPlugin;
import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.check.CheckType;
import com.diamend.anticheat.config.AntiCheatConfig;
import com.diamend.anticheat.config.CheckConfig;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.violation.PunishmentMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The point-and-click control panel — the "ease of use" half of the plugin.
 *
 * <p>Everything a server owner needs day to day is here: flip the whole plugin
 * between alert-only and enforce, toggle any individual check or protection on
 * or off, browse online players and read (or reset) their live violation
 * levels — all without touching config.yml. Toggles persist to disk and hot
 * reload immediately.
 *
 * <p>This class only builds inventories and reacts to clicks; the icons carry
 * their meaning in colour (green = on, red = off) so the state is readable at a
 * glance.
 */
public final class AntiCheatGui {

    // Fixed slot layout for the main screen.
    private static final int SLOT_MODE = 4;
    private static final int SLOT_PLAYERS = 45;
    private static final int SLOT_RELOAD = 49;
    private static final int SLOT_VERBOSE = 53;
    private static final int SLOT_HEALTH = 30;
    private static final int SLOT_ESP = 32;

    private static final Map<Integer, CheckType> CHECK_SLOTS = new LinkedHashMap<>();

    static {
        CHECK_SLOTS.put(10, CheckType.REACH);
        CHECK_SLOTS.put(11, CheckType.AUTOCLICKER);
        CHECK_SLOTS.put(12, CheckType.AIM);
        CHECK_SLOTS.put(13, CheckType.KILLAURA);
        CHECK_SLOTS.put(19, CheckType.FLIGHT);
        CHECK_SLOTS.put(20, CheckType.SPEED);
        CHECK_SLOTS.put(21, CheckType.NOFALL);
        CHECK_SLOTS.put(22, CheckType.TIMER);
        CHECK_SLOTS.put(23, CheckType.VEHICLE);
        CHECK_SLOTS.put(28, CheckType.SCAFFOLD);
    }

    private final AntiCheatPlugin plugin;
    private final AlertManager alerts;

    public AntiCheatGui(AntiCheatPlugin plugin, AlertManager alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
    }

    // ==================== Building ====================

    public void openMain(Player viewer) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("AntiCheat Control Panel", NamedTextColor.DARK_RED));
        holder.setInventory(inv);

        AntiCheatConfig cfg = plugin.config();
        boolean enforce = cfg.mode() == PunishmentMode.ENFORCE;

        inv.setItem(SLOT_MODE, item(
                enforce ? Material.REDSTONE_BLOCK : Material.REDSTONE_TORCH,
                Component.text("Mode: " + (enforce ? "ENFORCE" : "ALERT-ONLY"),
                        enforce ? NamedTextColor.RED : NamedTextColor.YELLOW),
                List.of(
                        line(enforce
                                ? "Punishments ARE carried out."
                                : "Only alerts staff — never punishes."),
                        line("Click to switch.")
                )));

        fillPanes(inv);

        for (Map.Entry<Integer, CheckType> entry : CHECK_SLOTS.entrySet()) {
            CheckType type = entry.getValue();
            CheckConfig cc = cfg.check(type);
            boolean on = cc != null && cc.enabled();
            inv.setItem(entry.getKey(), checkItem(type, on));
        }

        inv.setItem(SLOT_HEALTH, toggleItem(Material.GOLDEN_APPLE, "Anti Health-Indicators",
                cfg.healthSpoofEnabled(),
                "Masks other players' HP to full", "in the packets ESP reads."));
        inv.setItem(SLOT_ESP, toggleItem(Material.ENDER_EYE, "Anti-ESP (entity culling)",
                cfg.cullingEnabled(),
                "Hides players out of line of sight", "so ESP has nothing to draw."));

        inv.setItem(SLOT_PLAYERS, item(Material.PLAYER_HEAD,
                Component.text("Players", NamedTextColor.AQUA),
                List.of(line("View & reset violation levels."))));
        inv.setItem(SLOT_RELOAD, item(Material.BOOK,
                Component.text("Reload config", NamedTextColor.GREEN),
                List.of(line("Re-read config.yml from disk."))));
        boolean verbose = alerts.isVerbose(viewer.getUniqueId());
        inv.setItem(SLOT_VERBOSE, item(verbose ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Verbose alerts: " + (verbose ? "ON" : "OFF"),
                        verbose ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(line("Show every single flag to you."), line("Click to toggle."))));

        viewer.openInventory(inv);
    }

    public void openPlayers(Player viewer) {
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.PLAYERS, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("AntiCheat · Players", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        long now = System.currentTimeMillis();
        int slot = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            PlayerData data = plugin.players().get(online.getUniqueId());
            double total = 0.0D;
            if (data != null) {
                for (CheckType type : CheckType.values()) {
                    total += data.violations().level(type, now);
                }
            }
            inv.setItem(slot++, head(online, total));
        }
        inv.setItem(49, item(Material.ARROW, Component.text("Back", NamedTextColor.GRAY),
                List.of(line("Return to the control panel."))));
        viewer.openInventory(inv);
    }

    public void openPlayerDetail(Player viewer, UUID targetId) {
        Player target = plugin.getServer().getPlayer(targetId);
        String name = target != null ? target.getName()
                : Bukkit.getOfflinePlayer(targetId).getName();
        GuiHolder holder = new GuiHolder(GuiHolder.Screen.PLAYER_DETAIL, targetId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("AntiCheat · " + (name == null ? "player" : name),
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);
        fillPanes(inv);

        PlayerData data = plugin.players().get(targetId);
        long now = System.currentTimeMillis();
        int i = 0;
        int[] slots = { 10, 11, 12, 13, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32 };
        for (CheckType type : CheckType.values()) {
            if (i >= slots.length) {
                break;
            }
            double vl = data == null ? 0.0D : data.violations().level(type, now);
            boolean hot = vl > 0;
            inv.setItem(slots[i++], item(hot ? Material.RED_DYE : Material.LIME_DYE,
                    Component.text(type.displayName(), hot ? NamedTextColor.RED : NamedTextColor.GREEN),
                    List.of(line("Violation level: " + String.format(Locale.ROOT, "%.1f", vl)),
                            line("Category: " + type.category()))));
        }

        inv.setItem(45, item(Material.ARROW, Component.text("Back", NamedTextColor.GRAY),
                List.of(line("Return to the player list."))));
        inv.setItem(49, item(Material.BARRIER, Component.text("Reset violations", NamedTextColor.RED),
                List.of(line("Clear every violation level"), line("for this player."))));
        viewer.openInventory(inv);
    }

    // ==================== Click handling ====================

    /** Handle a click in one of our screens. Returns nothing; refreshes as needed. */
    public void handleClick(Player viewer, GuiHolder holder, int slot) {
        switch (holder.screen()) {
            case MAIN -> handleMainClick(viewer, slot);
            case PLAYERS -> handlePlayersClick(viewer, slot);
            case PLAYER_DETAIL -> handleDetailClick(viewer, holder, slot);
        }
    }

    private void handleMainClick(Player viewer, int slot) {
        if (!viewer.hasPermission("anticheat.admin")) {
            // Non-admins can still browse, but only admins may change settings.
            if (slot == SLOT_PLAYERS) {
                openPlayers(viewer);
            } else if (slot == SLOT_VERBOSE) {
                alerts.toggleVerbose(viewer.getUniqueId());
                openMain(viewer);
            }
            return;
        }
        if (slot == SLOT_MODE) {
            AntiCheatConfig cfg = plugin.config();
            plugin.setMode(cfg.mode() == PunishmentMode.ENFORCE
                    ? PunishmentMode.ALERT_ONLY : PunishmentMode.ENFORCE);
            openMain(viewer);
        } else if (slot == SLOT_RELOAD) {
            plugin.reloadAntiCheat();
            openMain(viewer);
        } else if (slot == SLOT_VERBOSE) {
            alerts.toggleVerbose(viewer.getUniqueId());
            openMain(viewer);
        } else if (slot == SLOT_PLAYERS) {
            openPlayers(viewer);
        } else if (slot == SLOT_HEALTH) {
            AntiCheatConfig cfg = plugin.config();
            plugin.setConfigBoolean("protections.health-indicators.enabled",
                    !cfg.healthSpoofEnabled());
            openMain(viewer);
        } else if (slot == SLOT_ESP) {
            AntiCheatConfig cfg = plugin.config();
            plugin.setConfigBoolean("protections.anti-esp.enabled", !cfg.cullingEnabled());
            openMain(viewer);
        } else if (CHECK_SLOTS.containsKey(slot)) {
            CheckType type = CHECK_SLOTS.get(slot);
            CheckConfig cc = plugin.config().check(type);
            boolean on = cc != null && cc.enabled();
            plugin.setConfigBoolean("checks." + type.configKey() + ".enabled", !on);
            openMain(viewer);
        }
    }

    private void handlePlayersClick(Player viewer, int slot) {
        if (slot == 49) {
            openMain(viewer);
            return;
        }
        Inventory inv = viewer.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta instanceof SkullMeta skull && skull.getOwningPlayer() != null) {
            openPlayerDetail(viewer, skull.getOwningPlayer().getUniqueId());
        }
    }

    private void handleDetailClick(Player viewer, GuiHolder holder, int slot) {
        if (slot == 45) {
            openPlayers(viewer);
        } else if (slot == 49 && holder.target() != null) {
            if (!viewer.hasPermission("anticheat.command")) {
                return;
            }
            PlayerData data = plugin.players().get(holder.target());
            if (data != null) {
                data.violations().reset();
            }
            openPlayerDetail(viewer, holder.target());
        }
    }

    // ==================== Item helpers ====================

    private ItemStack checkItem(CheckType type, boolean on) {
        Material mat = switch (type) {
            case REACH -> Material.IRON_SWORD;
            case AUTOCLICKER -> Material.CLOCK;
            case AIM -> Material.BOW;
            case KILLAURA -> Material.DIAMOND_SWORD;
            case FLIGHT -> Material.FEATHER;
            case SPEED -> Material.SUGAR;
            case NOFALL -> Material.SLIME_BLOCK;
            case TIMER -> Material.REPEATER;
            case VEHICLE -> Material.OAK_BOAT;
            case SCAFFOLD -> Material.SCAFFOLDING;
        };
        return item(mat,
                Component.text(type.displayName() + ": " + (on ? "ON" : "OFF"),
                        on ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(line("Category: " + type.category()), line("Click to toggle.")));
    }

    private ItemStack toggleItem(Material mat, String label, boolean on,
                                 String desc1, String desc2) {
        return item(mat,
                Component.text(label + ": " + (on ? "ON" : "OFF"),
                        on ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(line(desc1), line(desc2), line("Click to toggle.")));
    }

    private ItemStack head(Player target, double totalVl) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
        }
        boolean hot = totalVl > 0;
        meta.displayName(Component.text(target.getName(),
                hot ? NamedTextColor.RED : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Total VL: " + String.format(Locale.ROOT, "%.1f", totalVl)));
        lore.add(line("Click to inspect."));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    /** Fill empty decorative slots with grey panes so the layout reads cleanly. */
    private void fillPanes(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        // Only fill the border-ish empties later populated slots overwrite anyway.
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }
}
