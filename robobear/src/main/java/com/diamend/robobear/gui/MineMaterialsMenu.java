package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one mine may be asked for: drop the blocks in, close, done.
 *
 * <p>Prefilled with the answer already in use, so opening it shows what the
 * generator would pick from rather than an empty box to guess at. Closing it
 * unchanged changes nothing — the list is only stored as a hand-set override
 * when it actually differs from what would have been worked out automatically,
 * so looking at a mine never quietly pins it.
 *
 * <p>Empty means automatic, not "nothing": clear the box and the mine goes back
 * to using its own composition.
 */
public class MineMaterialsMenu implements Menu {

    private static final int CONTENT_SIZE = 45;
    private static final int SIZE = 54;
    private static final int SLOT_HELP = 45;
    private static final int SLOT_SOURCE = 47;
    private static final int SLOT_DONE = 49;
    private static final int SLOT_CLEAR = 51;
    private static final int SLOT_BACK = 53;

    private final RoboBearPlugin plugin;
    private final Player viewer;
    private final MineRegion mine;

    private Inventory inventory;
    private boolean clearing;

    public MineMaterialsMenu(RoboBearPlugin plugin, Player viewer, MineRegion mine) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.mine = mine;
    }

    @Override
    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_gray>Materials — " + mine.id()));
            int slot = 0;
            for (Material material : plugin.mines().materialsFor(mine.id())) {
                if (slot >= CONTENT_SIZE) {
                    break;
                }
                if (material.isItem()) {
                    inventory.setItem(slot++, new ItemStack(material));
                }
            }
        }
        for (int slot = CONTENT_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        inventory.setItem(SLOT_HELP, Items.text(Material.BOOK, "<yellow>How this works", List.of(
                "<gray>Put the blocks this mine may be",
                "<gray>asked for into the space above.",
                "<gray>Saved when you close.",
                "",
                "<gray>Leave it empty to go back to using",
                "<gray>what the mine is made of.",
                "",
                "<dark_gray>Only blocks count — an item that",
                "<dark_gray>can't be broken is ignored.")));

        inventory.setItem(SLOT_SOURCE, source());

        inventory.setItem(SLOT_DONE, Items.text(Material.EMERALD, "<green><bold>Done", List.of(
                "<gray>Saves and goes back.")));

        inventory.setItem(SLOT_CLEAR, Items.text(Material.WATER_BUCKET, "<yellow>Back to automatic", List.of(
                "<gray>Empties the box and uses what",
                "<gray>the mine is made of again.")));

        inventory.setItem(SLOT_BACK, Items.text(Material.ARROW, "<yellow>« Quests", List.of(
                "<gray>Saves and goes back.")));
        player.openInventory(inventory);
    }

    private ItemStack source() {
        Set<Material> detected = plugin.mines().detectedMaterials(mine.id());
        List<Material> allowed = plugin.mines().configuredMaterials();
        boolean overridden = plugin.mines().materials().isOverridden(mine.id());

        List<String> lore = new ArrayList<>();
        lore.add("<gray>World: <white>" + mine.world());
        lore.add("");
        if (detected.isEmpty()) {
            lore.add("<yellow>Your mine source didn't say what");
            lore.add("<yellow>this mine contains, so the list");
            lore.add("<yellow>above starts from config.yml.");
        } else {
            lore.add("<gray>The mine contains <white>" + detected.size() + "<gray> block types.");
            lore.add("<gray>Of those, <white>" + countAllowed(detected, allowed)
                    + "<gray> are on the config's");
            lore.add("<gray>list of things worth asking for.");
        }
        lore.add("");
        lore.add(overridden ? "<yellow>Currently set by hand" : "<green>Currently automatic");
        return Items.text(Material.COMPASS, "<yellow>" + mine.id(), lore, overridden);
    }

    private static int countAllowed(Set<Material> detected, List<Material> allowed) {
        if (allowed.isEmpty()) {
            return detected.size();
        }
        int count = 0;
        for (Material material : detected) {
            if (allowed.contains(material)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean cancelClick(InventoryClickEvent event) {
        if (event == null) {
            return false; // permit drags into the content area
        }
        int raw = event.getRawSlot();
        return raw >= CONTENT_SIZE && raw < SIZE;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == SLOT_DONE || slot == SLOT_BACK) {
            viewer.closeInventory(); // onClose saves and returns
            return;
        }
        if (slot == SLOT_CLEAR) {
            for (int i = 0; i < CONTENT_SIZE; i++) {
                inventory.setItem(i, null);
            }
            clearing = true;
            viewer.closeInventory();
        }
    }

    @Override
    public void onClose() {
        Set<Material> chosen = new LinkedHashSet<>();
        int ignored = 0;
        for (int slot = 0; slot < CONTENT_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Material material = item.getType();
            if (material.isBlock()) {
                chosen.add(material);
            } else {
                ignored++;
            }
        }

        // Matching the automatic answer is the same as having no opinion. Storing
        // it anyway would freeze the mine's list against future composition edits
        // for anyone who merely opened this screen to look.
        boolean automatic = chosen.isEmpty()
                || (!clearing && chosen.equals(new LinkedHashSet<>(plugin.mines().automaticMaterials(mine.id()))));

        plugin.mines().materials().set(mine.id(), automatic ? List.of() : chosen);
        clearing = false;

        if (!viewer.isOnline()) {
            return;
        }
        if (ignored > 0) {
            viewer.sendMessage(Text.parse("<yellow>Ignored " + ignored
                    + " item" + (ignored == 1 ? "" : "s") + " that can't be mined."));
        }
        viewer.sendMessage(automatic
                ? Text.parse("<green>" + mine.id() + "<gray> uses what the mine is made of.")
                : Text.parse("<green>" + mine.id() + "<gray> can be asked for <white>"
                        + chosen.size() + "<gray> material" + (chosen.size() == 1 ? "" : "s") + "."));

        Bukkit.getScheduler().runTask(plugin, () -> new QuestEditorMenu(plugin).open(viewer));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
