package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code /box} landing menu. Every active module contributes its own icon,
 * so a module added later shows up here without this class changing.
 */
public class HubMenu extends AbstractMenu {

    private final Map<Integer, HubEntry> slots = new HashMap<>();

    public HubMenu(BoxCorePlugin plugin) {
        super(plugin, 27, "<dark_gray>Box <gray>| <white>Hub");
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        List<HubEntry> entries = plugin.modules().hubEntries();
        for (HubEntry entry : entries) {
            int slot = freeSlot(entry.slot());
            if (slot < 0) {
                continue;
            }
            slots.put(slot, entry);
            set(slot, Items.text(entry.icon(), entry.name(), entry.loreFor(player), false));
        }
        set(22, profileIcon(player));
        closeButton(26);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    /** The module's preferred slot, or the next free one in the top two rows. */
    private int freeSlot(int preferred) {
        if (preferred >= 0 && preferred < 18 && !slots.containsKey(preferred)) {
            return preferred;
        }
        for (int slot = 10; slot < 17; slot++) {
            if (!slots.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack profileIcon(Player player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Skill points: <white>" + profile.getAvailablePoints()
                + " <dark_gray>(" + profile.getPointsSpent() + " spent)");
        lore.add("<gray>Nodes unlocked: <white>" + profile.getNodes().size());
        com.diamend.boxcore.collection.CollectionsModule collections =
                plugin.modules().get(com.diamend.boxcore.collection.CollectionsModule.class);
        if (collections != null) {
            lore.add("<gray>Items gathered: <white>"
                    + Text.number(collections.collections().itemsCollected(profile)));
        }
        if (plugin.getConfig().getBoolean("skills.allow-respec", true)) {
            lore.add("");
            lore.add("<dark_gray>/box respec to refund your points");
        }

        ItemStack head = Items.text(Material.PLAYER_HEAD,
                "<aqua>" + player.getName(), lore, false);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 26) {
            player.closeInventory();
            return;
        }
        HubEntry entry = slots.get(event.getRawSlot());
        if (entry == null) {
            return;
        }
        click(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> entry.open().accept(player));
    }

    /** Convenience used by the command and by "back" buttons. */
    public static void openFor(BoxCorePlugin plugin, Player player) {
        new HubMenu(plugin).open(player);
    }
}
