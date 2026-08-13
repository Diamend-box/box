package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.Upgrade;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff view of the workshop: which upgrades are on sale.
 *
 * <p>Prices stay in config.yml, where they sit next to the comments explaining
 * them and can be diffed. What belongs in game is the on/off decision, because
 * that is the one an admin makes while watching a run and wants to act on
 * before the next one starts.
 */
public class UpgradeEditorMenu extends AbstractMenu {

    private static final int SLOT_INFO = 4;
    private static final int UPGRADE_START = 10;
    private static final int SLOT_ENABLE_ALL = 18;
    private static final int SLOT_CLOSE = 22;
    private static final int SLOT_DISABLE_ALL = 26;

    private final List<Upgrade> shown = new ArrayList<>();

    public UpgradeEditorMenu(RoboBearPlugin plugin) {
        super(plugin, 27, "<dark_gray>Workshop Upgrades");
    }

    @Override
    protected void build(Player player) {
        shown.clear();
        int slot = UPGRADE_START;
        for (Upgrade upgrade : Upgrade.values()) {
            if (slot >= UPGRADE_START + 7) {
                break;
            }
            shown.add(upgrade);
            set(slot, icon(upgrade));
            slot++;
        }

        set(SLOT_INFO, info());
        set(SLOT_ENABLE_ALL, Items.text(Material.LIME_DYE, "<green>Sell everything", List.of(
                "<gray>Puts all <white>" + Upgrade.values().length + "<gray> back on sale.")));
        set(SLOT_DISABLE_ALL, Items.text(Material.GRAY_DYE, "<red>Close the workshop", List.of(
                "<gray>Takes every upgrade off sale. Cogs then",
                "<gray>do nothing, so consider dropping",
                "<gray><white>run.starting-cogs<gray> too.",
                "",
                "<yellow>Shift-click to confirm.")));
        closeButton(SLOT_CLOSE);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack info() {
        int open = plugin.service().upgrades().enabledCount();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>On sale: <white>" + open + "<gray> of <white>" + Upgrade.values().length);
        lore.add("");
        if (open == 0) {
            lore.add("<red>The workshop sells nothing, so Cogs");
            lore.add("<red>have nothing to be spent on.");
        } else {
            lore.add("<gray>Players see only what's on sale.");
        }
        lore.add("");
        lore.add("<dark_gray>Prices are in config.yml under");
        lore.add("<dark_gray>'upgrades', then /rb reload.");
        lore.add("<dark_gray>Levels already bought keep working");
        lore.add("<dark_gray>for the rest of that run.");
        return Items.text(Material.COMPASS, "<yellow>The workshop", lore);
    }

    private ItemStack icon(Upgrade upgrade) {
        boolean on = plugin.service().upgrades().isEnabled(upgrade);
        int cost = plugin.getConfig().getInt("upgrades." + upgrade.key() + ".cost", 8);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + upgrade.description());
        lore.add("");
        lore.add("<gray>Levels: <white>" + upgrade.maxLevel());
        lore.add("<gray>First level costs: <gold>" + cost + " cogs");
        lore.add("<dark_gray>config: upgrades." + upgrade.key() + ".cost");
        lore.add("");
        lore.add(on ? "<green>On sale" : "<red>Not sold");
        lore.add("");
        lore.add(on ? "<gray>Click to take it off sale" : "<gray>Click to put it on sale");
        return Items.text(upgrade.icon(),
                (on ? "<green>" : "<dark_gray>") + upgrade.displayName(), lore, on);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == SLOT_ENABLE_ALL) {
            click(player);
            int changed = plugin.service().upgrades().enableAll();
            player.sendMessage(Text.parse("<green>Every upgrade is on sale again "
                    + "<gray>(" + changed + " put back)."));
            refresh(player);
            return;
        }
        if (slot == SLOT_DISABLE_ALL) {
            // Guarded: this empties the screen players spend Cogs on, and the
            // button that undoes it is at the other end of the same row.
            if (!event.isShiftClick()) {
                deny(player);
                player.sendMessage(Text.parse("<yellow>Shift-click that to close the "
                        + "workshop entirely."));
                return;
            }
            click(player);
            int changed = plugin.service().upgrades().disableAll();
            player.sendMessage(Text.parse("<red>Took " + changed
                    + " upgrade(s) off sale. <gray>Cogs now buy nothing."));
            refresh(player);
            return;
        }

        int index = slot - UPGRADE_START;
        if (index < 0 || index >= shown.size()) {
            return;
        }
        Upgrade upgrade = shown.get(index);
        boolean on = plugin.service().upgrades().isEnabled(upgrade);
        plugin.service().upgrades().setEnabled(upgrade, !on);
        click(player);
        refresh(player);
    }
}
