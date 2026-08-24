package com.diamend.boxcore.gui;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Switching BoxCore's features on and off without opening a file.
 *
 * <p>The toggle is written to config as it is made, so a module switched off in
 * game stays off across a restart. Anything else would be a trap: the feature
 * would come back on its own hours later with nobody remembering why.
 */
public class ModuleMenu extends AbstractMenu {

    private static final int[] ROW = {10, 11, 12, 13, 14, 15, 16};
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 18;
    private static final int CLOSE_SLOT = 26;

    private final Map<Integer, String> slots = new HashMap<>();

    public ModuleMenu(BoxCorePlugin plugin) {
        super(plugin, 27, "<dark_gray>Box <gray>| <red>Modules");
    }

    @Override
    protected void build(Player player) {
        slots.clear();
        List<BoxModule> modules = plugin.modules().registered();
        int running = 0;

        for (int index = 0; index < modules.size() && index < ROW.length; index++) {
            BoxModule module = modules.get(index);
            boolean active = plugin.modules().isActive(module.id());
            if (active) {
                running++;
            }
            int slot = ROW[index];
            slots.put(slot, module.id());

            List<String> lore = new ArrayList<>();
            lore.add(active ? "<green>Running" : "<red>Switched off");
            lore.add("<dark_gray>modules." + module.id() + ".enabled");
            if (active) {
                for (String line : module.statusLines()) {
                    lore.add("<gray>" + line);
                }
            } else {
                lore.add("<dark_gray>Its commands, menus, items and");
                lore.add("<dark_gray>placeholders all go quiet.");
            }
            lore.add("");
            lore.add(active ? "<yellow>Click to switch off" : "<yellow>Click to switch on");

            set(slot, Items.text(iconFor(module, active), (active ? "<white>" : "<gray>")
                    + module.displayName(), lore, active));
        }

        List<String> header = new ArrayList<>();
        header.add("<gray>Running: <white>" + running + "<gray>/<white>" + modules.size());
        header.add("");
        header.add("<gray>Changes are written to config.yml");
        header.add("<gray>and take effect straight away.");
        if (modules.size() > ROW.length) {
            header.add("<red>Showing " + ROW.length + " of " + modules.size() + ".");
        }
        set(HEADER_SLOT, Items.text(Material.COMPARATOR, "<red><bold>Modules", header, true));
        backButton(BACK_SLOT, "Hub");
        closeButton(CLOSE_SLOT);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * The module's own hub icon where it has one, so a row of modules reads the
     * same way as the hub it controls. A switched-off module shows as a grey
     * pane instead, which is what "not there" looks like everywhere else in the
     * plugin.
     */
    private Material iconFor(BoxModule module, boolean active) {
        if (!active) {
            return Material.GRAY_DYE;
        }
        HubEntry entry = module.hubEntry();
        return entry == null ? Material.LIME_DYE : entry.icon();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (raw == BACK_SLOT) {
            click(player);
            openLater(player, new HubMenu(plugin));
            return;
        }
        String id = slots.get(raw);
        if (id == null) {
            return;
        }
        // Checked here as well as at the command that opens this. A menu that
        // can switch features off for the whole server is not somewhere to rely
        // on nobody having found another way in.
        if (!player.hasPermission("boxcore.admin")) {
            plugin.messages().send(player, "no-permission");
            player.closeInventory();
            return;
        }
        click(player);
        boolean wasActive = plugin.modules().isActive(id);
        boolean nowActive = plugin.modules().setEnabled(id, !wasActive);

        if (!wasActive && !nowActive) {
            // It refused to come up. The console has the stack trace; the
            // person standing at the menu needs to know it did not work.
            plugin.messages().sendLiteral(player,
                    "<red>" + id + " failed to start. Check the console.");
        }
        plugin.getLogger().info(player.getName() + " switched module '" + id + "' "
                + (nowActive ? "on" : "off") + ".");
        openLater(player, new ModuleMenu(plugin));
    }
}
