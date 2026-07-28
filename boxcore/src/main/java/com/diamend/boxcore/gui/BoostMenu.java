package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.boost.Boost;
import com.diamend.boxcore.boost.BoostItems;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What's boosting you right now, and what you're carrying that could boost you
 * more.
 *
 * <p>The countdowns rebuild once a second while the menu is open, so a boost
 * running out is something you watch happen rather than something you discover
 * by reopening the menu. The task stops itself as soon as the viewer is looking
 * at anything else.
 */
public class BoostMenu extends AbstractMenu {

    private static final int SERVER_SLOT = 4;
    private static final int DROPS_SLOT = 11;
    private static final int COLLECTIONS_SLOT = 15;
    private static final int LABEL_SLOT = 18;
    private static final int BACK_SLOT = 27;
    private static final int CLOSE_SLOT = 35;
    private static final int[] ITEM_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private final BoostsModule boosts;

    /** Menu slot to the inventory slot it is showing, and to what it grants. */
    private final Map<Integer, Integer> sources = new HashMap<>();
    private final Map<Integer, BoostItems.Payload> offered = new HashMap<>();

    private BukkitTask refresh;

    public BoostMenu(BoxCorePlugin plugin, BoostsModule boosts) {
        super(plugin, 36, "<dark_gray>Box <gray>| <light_purple>Boosts");
        this.boosts = boosts;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        startRefresh(player);
    }

    @Override
    protected void build(Player player) {
        sources.clear();
        offered.clear();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());

        set(SERVER_SLOT, serverIcon());
        set(DROPS_SLOT, statusIcon(profile, BoostType.DROPS, Material.DIAMOND_ORE));
        set(COLLECTIONS_SLOT, statusIcon(profile, BoostType.COLLECTIONS, Material.CHEST));
        set(LABEL_SLOT, Items.text(Material.BOOK, "<yellow>Your boost items",
                List.of("<gray>Boost items you're carrying.",
                        "<gray>Click one to start it.",
                        "",
                        "<dark_gray>Right-clicking one in hand works too."), false));
        buildItemRow(player);

        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    private ItemStack statusIcon(PlayerProfile profile, BoostType type, Material icon) {
        double total = boosts.multiplierFor(profile, type);
        double server = boosts.globalMultiplier(type);
        double own = boosts.personalMultiplier(profile, type);

        List<String> lore = new ArrayList<>();
        if (total <= 1.0) {
            lore.add("<dark_gray>Nothing running.");
        } else {
            lore.add("<gray>Right now: <white>" + Text.decimal(total) + "x");
            if (server > 1.0) {
                lore.add("<gray>  Server-wide: <white>" + Text.decimal(server) + "x");
            }
            if (own > 1.0) {
                lore.add("<gray>  Yours: <white>" + Text.decimal(own) + "x");
            }
            lore.add("<gray>Next change in: <white>"
                    + Durations.format(boosts.remainingFor(profile, type)));
            if (server > 1.0 && own > 1.0 && server * own > boosts.maxMultiplier()) {
                // Say so rather than let someone wonder why 2x and 8x made 8x.
                lore.add("<red>Capped at the server limit of <white>"
                        + Text.decimal(boosts.maxMultiplier()) + "x<red>.");
            }
        }
        lore.add("");
        lore.addAll(explain(type));
        return Items.text(icon, "<light_purple><bold>" + type.display(), lore, total > 1.0);
    }

    private List<String> explain(BoostType type) {
        if (type == BoostType.DROPS) {
            List<String> lines = new ArrayList<>();
            lines.add("<gray>Multiplies what a broken");
            lines.add("<gray>block actually drops.");
            if (boosts.oresOnly()) {
                lines.add("<dark_gray>Whitelisted ore only.");
            }
            return lines;
        }
        return List.of("<gray>Multiplies how much what",
                "<gray>you gather counts towards",
                "<gray>your collections.");
    }

    private ItemStack serverIcon() {
        List<Boost> running = boosts.globalBoosts();
        List<String> lore = new ArrayList<>();
        if (running.isEmpty()) {
            lore.add("<dark_gray>No server-wide boost is running.");
        } else {
            long now = System.currentTimeMillis();
            for (Boost boost : running) {
                lore.add("<gray>" + boost.type().display() + ": <white>"
                        + Text.decimal(boost.multiplier()) + "x <dark_gray>("
                        + Durations.format(boost.remainingMillis(now)) + " left)");
            }
        }
        lore.add("");
        lore.add("<dark_gray>Boosts multiply together, up to "
                + Text.decimal(boosts.maxMultiplier()) + "x.");
        return Items.text(Material.BEACON, "<aqua><bold>Server-wide", lore, !running.isEmpty());
    }

    // ------------------------------------------------------------------
    // The items row
    // ------------------------------------------------------------------

    private void buildItemRow(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int shown = 0;
        for (int index = 0; index < contents.length && shown < ITEM_SLOTS.length; index++) {
            BoostItems.Payload payload = boosts.items().read(contents[index]);
            if (payload == null) {
                continue;
            }
            int slot = ITEM_SLOTS[shown++];
            sources.put(slot, index);
            offered.put(slot, payload);
            set(slot, withUseHint(contents[index]));
        }
        if (shown == 0) {
            set(ITEM_SLOTS[3], Items.text(Material.GLASS_BOTTLE, "<gray>No boost items",
                    List.of("<dark_gray>You aren't carrying any."), false));
        }
    }

    /**
     * The carried item as it will appear in the menu — its own name and lore,
     * kept so a player recognises it, with the click hint added.
     */
    private ItemStack withUseHint(ItemStack source) {
        ItemStack icon = source.clone();
        icon.setAmount(Math.max(1, Math.min(64, source.getAmount())));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Text.item("<yellow>Click to start it"));
            meta.lore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
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
            stopRefresh();
            player.closeInventory();
            return;
        }
        if (raw == BACK_SLOT) {
            stopRefresh();
            click(player);
            openLater(player, new HubMenu(plugin));
            return;
        }
        Integer index = sources.get(raw);
        if (index != null) {
            use(player, raw, index);
        }
    }

    /**
     * Spends one boost item out of the player's inventory.
     *
     * <p>The inventory can be rearranged while this menu is open, so what the
     * slot showed is checked against what is in it now. A mismatch redraws
     * instead of spending whatever happens to be sitting there.
     */
    private void use(Player player, int slot, int index) {
        ItemStack held = player.getInventory().getItem(index);
        BoostItems.Payload payload = boosts.items().read(held);
        if (payload == null || !payload.equals(offered.get(slot))) {
            redraw(player);
            return;
        }
        boosts.activate(player, payload);

        ItemStack left = held.clone();
        left.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(index, left.getAmount() <= 0 ? null : left);

        plugin.messages().send(player, "boost-activated",
                "type", payload.typeNames(),
                "multiplier", Text.decimal(payload.multiplier()),
                "duration", Durations.format(payload.durationMillis()));
        click(player);
        redraw(player);
    }

    // ------------------------------------------------------------------
    // Live countdowns
    // ------------------------------------------------------------------

    private void redraw(Player player) {
        getInventory().clear();
        build(player);
    }

    private void startRefresh(Player player) {
        stopRefresh();
        refresh = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isWatching(player)) {
                stopRefresh();
                return;
            }
            redraw(player);
        }, 20L, 20L);
    }

    private boolean isWatching(Player player) {
        try {
            return player.getOpenInventory().getTopInventory().getHolder() == this;
        } catch (Throwable ex) {
            return false; // can't tell; stop rather than redraw something else
        }
    }

    private void stopRefresh() {
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
    }
}
