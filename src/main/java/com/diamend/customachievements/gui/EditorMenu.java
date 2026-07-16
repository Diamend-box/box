package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The achievement editor: presentation/reward fields plus a list of objectives.
 * Each objective opens a {@link RequirementEditorMenu}; triggers and targets are
 * chosen from GUI pickers.
 */
public class EditorMenu implements Menu {

    private static final int SIZE = 54;

    private static final int SLOT_ID = 10;
    private static final int SLOT_ICON = 12;
    private static final int SLOT_NAME = 14;
    private static final int SLOT_DESC = 16;
    private static final int SLOT_ANNOUNCE = 19;
    private static final int SLOT_HIDDEN = 20;
    private static final int SLOT_CATEGORY = 21;
    private static final int SLOT_XP = 22;
    private static final int SLOT_ITEMS = 23;
    private static final int SLOT_COMMANDS = 24;
    private static final int SLOT_ADD = 45;
    private static final int SLOT_CLONE = 46;
    private static final int SLOT_SAVE = 48;
    private static final int SLOT_CANCEL = 50;
    private static final int SLOT_DELETE = 52;

    private static final int[] OBJECTIVE_SLOTS = {
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
    };

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final Achievement draft;
    private final boolean creating;

    private Inventory inventory;

    public EditorMenu(CustomAchievementsPlugin plugin, Player viewer, Achievement draft, boolean creating) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.draft = draft;
        this.creating = creating;
    }

    @Override
    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    private void rebuild() {
        if (inventory == null) {
            Component title = Text.parse(creating
                    ? "<dark_green><bold>Create Achievement"
                    : "<dark_green><bold>Edit: <white>" + draft.getId());
            inventory = Bukkit.createInventory(this, SIZE, title);
        }
        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }

        inventory.setItem(SLOT_ID, Items.of(Material.NAME_TAG, Text.item("<aqua>Identifier"),
                lore("<white>" + draft.getId(), "",
                        creating ? "<yellow>Click to change" : "<dark_gray>Locked while editing")));

        inventory.setItem(SLOT_ICON, Items.of(draft.getIcon(),
                Text.item("<aqua>Icon <dark_gray>(" + draft.getIcon().name() + ")"),
                lore("<gray>Item shown in the menu.", "",
                        "<yellow>Click holding an item to set it,",
                        "<yellow>or click empty-handed to type a name.")));

        inventory.setItem(SLOT_NAME, Items.of(Material.WRITABLE_BOOK, Text.item("<aqua>Display Name"),
                lore("<white>" + draft.getDisplayName(), "",
                        "<gray>Supports &-codes and MiniMessage.",
                        "<yellow>Click to edit")));

        List<String> descLines = new ArrayList<>();
        descLines.add("<gray>Current:");
        if (draft.getDescription().isEmpty()) {
            descLines.add("<dark_gray>(empty)");
        }
        for (String line : draft.getDescription()) {
            descLines.add("<white>• " + line);
        }
        descLines.add("");
        descLines.add("<yellow>Left: rewrite (| for new lines)");
        descLines.add("<yellow>Right: add a line  <yellow>Shift-right: remove last");
        inventory.setItem(SLOT_DESC, Items.of(Material.BOOK, Text.item("<aqua>Description"), lore(descLines)));

        inventory.setItem(SLOT_ANNOUNCE, Items.of(draft.isAnnounce() ? Material.LIME_DYE : Material.GRAY_DYE,
                Text.item("<aqua>Broadcast on Unlock: " + (draft.isAnnounce() ? "<green>ON" : "<red>OFF")),
                lore("<gray>Announce to the whole server.", "", "<yellow>Click to toggle")));

        inventory.setItem(SLOT_HIDDEN, Items.of(draft.isHidden() ? Material.BLACK_DYE : Material.GLOWSTONE_DUST,
                Text.item("<aqua>Secret: " + (draft.isHidden() ? "<green>ON" : "<red>OFF")),
                lore("<gray>Hidden achievements show as <white>???",
                        "<gray>until a player unlocks them.", "", "<yellow>Click to toggle")));

        inventory.setItem(SLOT_CATEGORY, Items.of(Material.CHEST,
                Text.item("<aqua>Category"),
                lore(draft.getCategory().isBlank() ? "<dark_gray>(none)" : "<white>" + draft.getCategory(), "",
                        "<gray>Groups achievements into tabs.",
                        "<yellow>Click to set  <yellow>Right-click to clear")));

        inventory.setItem(SLOT_XP, Items.of(Material.EXPERIENCE_BOTTLE,
                Text.item("<aqua>Reward XP: <white>" + draft.getRewardXp()),
                lore("<gray>Experience granted on unlock.", "", "<yellow>Click to type a number")));

        inventory.setItem(SLOT_ITEMS, Items.of(Material.CHEST_MINECART,
                Text.item("<aqua>Reward Items: <white>" + draft.getRewardItems().size()),
                lore("<gray>Items given to the player on unlock.", "", "<yellow>Click to edit")));

        List<String> cmdLines = new ArrayList<>();
        if (draft.getRewardCommands().isEmpty()) {
            cmdLines.add("<dark_gray>None");
        } else {
            for (String command : draft.getRewardCommands()) {
                cmdLines.add("<white>• /" + command);
            }
        }
        cmdLines.add("");
        cmdLines.add("<gray>Use %player% for the player's name.");
        cmdLines.add("<yellow>Left: add   <yellow>Right: clear");
        inventory.setItem(SLOT_COMMANDS, Items.of(Material.COMMAND_BLOCK, Text.item("<aqua>Reward Commands"),
                lore(cmdLines)));

        // Objectives.
        List<Requirement> requirements = draft.getRequirements();
        for (int i = 0; i < OBJECTIVE_SLOTS.length; i++) {
            int slot = OBJECTIVE_SLOTS[i];
            if (i < requirements.size()) {
                Requirement requirement = requirements.get(i);
                inventory.setItem(slot, Items.of(requirement.getTrigger().icon(),
                        Text.item("<yellow>Objective " + (i + 1)),
                        lore("<gray>" + requirement.describe(), "",
                                "<yellow>Click to edit",
                                requirements.size() > 1 ? "<red>Shift-right to remove" : "")));
            } else {
                inventory.setItem(slot, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        if (requirements.size() < OBJECTIVE_SLOTS.length) {
            inventory.setItem(SLOT_ADD, Items.of(Material.LIME_CONCRETE,
                    Text.item("<green><bold>+ Add Objective"),
                    lore("<gray>Add another requirement.",
                            "<gray>All objectives must be completed.")));
        }

        inventory.setItem(SLOT_SAVE, Items.of(Material.EMERALD_BLOCK, Text.item("<green><bold>Save"),
                lore("<gray>Save this achievement.")));
        inventory.setItem(SLOT_CANCEL, Items.of(Material.BARRIER, Text.item("<red><bold>Cancel"),
                lore("<gray>Discard changes and go back.")));
        if (!creating) {
            inventory.setItem(SLOT_CLONE, Items.of(Material.BOOK, Text.item("<aqua><bold>Duplicate"),
                    lore("<gray>Create a copy of this achievement", "<gray>as a new draft to edit.")));
            inventory.setItem(SLOT_DELETE, Items.of(Material.TNT, Text.item("<dark_red><bold>Delete"),
                    lore("<gray>Permanently remove this achievement.", "", "<red>Shift-click to confirm.")));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        // Objective tiles.
        for (int i = 0; i < OBJECTIVE_SLOTS.length; i++) {
            if (OBJECTIVE_SLOTS[i] == slot) {
                if (i < draft.getRequirements().size()) {
                    if (click == ClickType.SHIFT_RIGHT && draft.getRequirements().size() > 1) {
                        draft.getRequirements().remove(i);
                        rebuild();
                    } else {
                        new RequirementEditorMenu(plugin, viewer, draft, i, this).open(viewer);
                    }
                }
                return;
            }
        }

        switch (slot) {
            case SLOT_ID -> {
                if (creating) {
                    promptText("Enter a unique identifier (letters, numbers, underscores):", input -> {
                        String id = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                        if (id.isBlank()) {
                            viewer.sendMessage(Text.parse("<red>That identifier is not valid."));
                        } else if (plugin.getAchievementManager().exists(id)) {
                            viewer.sendMessage(Text.parse("<red>An achievement with that id already exists."));
                        } else {
                            draft.setId(id);
                        }
                    });
                }
            }
            case SLOT_ICON -> handleIcon(event);
            case SLOT_NAME -> promptText("Enter the display name (&-codes or MiniMessage):",
                    input -> draft.setDisplayName(input));
            case SLOT_DESC -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    List<String> description = draft.getDescription();
                    if (!description.isEmpty()) {
                        description.remove(description.size() - 1);
                    }
                    rebuild();
                } else if (click.isRightClick()) {
                    promptText("Enter a line to add to the description:",
                            input -> draft.getDescription().add(input.trim()));
                } else {
                    promptText("Enter the description (use | to separate lines):", input -> {
                        List<String> lines = new ArrayList<>();
                        for (String part : input.split("\\|")) {
                            lines.add(part.trim());
                        }
                        draft.setDescription(lines);
                    });
                }
            }
            case SLOT_ANNOUNCE -> {
                draft.setAnnounce(!draft.isAnnounce());
                rebuild();
            }
            case SLOT_HIDDEN -> {
                draft.setHidden(!draft.isHidden());
                rebuild();
            }
            case SLOT_CATEGORY -> {
                if (click.isRightClick()) {
                    draft.setCategory("");
                    rebuild();
                } else {
                    promptText("Enter a category name (groups achievements into tabs):",
                            input -> draft.setCategory(input.trim()));
                }
            }
            case SLOT_ITEMS -> new RewardItemsMenu(plugin, viewer, draft, this).open(viewer);
            case SLOT_XP -> promptText("Enter the reward XP amount:", input -> {
                try {
                    draft.setRewardXp(Math.max(0, Integer.parseInt(input.trim())));
                } catch (NumberFormatException ex) {
                    viewer.sendMessage(Text.parse("<red>That's not a whole number."));
                }
            });
            case SLOT_COMMANDS -> {
                if (click.isRightClick()) {
                    draft.getRewardCommands().clear();
                    rebuild();
                } else {
                    promptText("Enter a command to run on unlock (no leading slash):", input -> {
                        String command = input.startsWith("/") ? input.substring(1) : input;
                        if (!command.isBlank()) {
                            draft.getRewardCommands().add(command);
                        }
                    });
                }
            }
            case SLOT_ADD -> {
                if (draft.getRequirements().size() < OBJECTIVE_SLOTS.length) {
                    draft.getRequirements().add(new Requirement());
                    new RequirementEditorMenu(plugin, viewer, draft,
                            draft.getRequirements().size() - 1, this).open(viewer);
                }
            }
            case SLOT_CLONE -> {
                if (!creating) {
                    Achievement copy = draft.copy();
                    String base = draft.getId() + "_copy";
                    String id = base;
                    int n = 1;
                    while (plugin.getAchievementManager().exists(id)) {
                        id = base + n++;
                    }
                    copy.setId(id);
                    viewer.sendMessage(Text.parse("<green>Editing a copy — set an id and Save to keep it."));
                    new EditorMenu(plugin, viewer, copy, true).open(viewer);
                }
            }
            case SLOT_SAVE -> save();
            case SLOT_CANCEL -> new AchievementMenu(plugin, viewer, true).open(viewer);
            case SLOT_DELETE -> {
                if (!creating && click == ClickType.SHIFT_LEFT) {
                    plugin.getAchievementManager().remove(draft.getId());
                    viewer.sendMessage(Text.parse("<red>Deleted achievement <white>" + draft.getId() + "<red>."));
                    new AchievementMenu(plugin, viewer, true).open(viewer);
                }
            }
            default -> {
                // background
            }
        }
    }

    private void handleIcon(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            draft.setIcon(cursor.getType());
            rebuild();
        } else {
            promptText("Enter a material name for the icon (e.g. DIAMOND):", input -> {
                Material material = Material.matchMaterial(input.trim());
                if (material == null || !material.isItem()) {
                    viewer.sendMessage(Text.parse("<red>Unknown item material: <white>" + input));
                } else {
                    draft.setIcon(material);
                }
            });
        }
    }

    private void save() {
        if (draft.getId() == null || draft.getId().isBlank()) {
            viewer.sendMessage(Text.parse("<red>You must set an identifier first."));
            return;
        }
        if (creating && plugin.getAchievementManager().exists(draft.getId())) {
            viewer.sendMessage(Text.parse("<red>An achievement with that id already exists."));
            return;
        }
        if (draft.getDisplayName() == null || draft.getDisplayName().isBlank()) {
            viewer.sendMessage(Text.parse("<red>You must set a display name."));
            return;
        }
        for (Requirement requirement : draft.getRequirements()) {
            if (requirement.getTrigger().usesTarget()
                    && (requirement.getTarget() == null || requirement.getTarget().isBlank())) {
                requirement.setTarget("ANY");
            }
            if (requirement.getTrigger() == com.diamend.customachievements.achievement.TriggerType.REACH_LOCATION
                    && requirement.getLocationTarget() == null) {
                viewer.sendMessage(Text.parse("<red>An objective's location is not set (open it and pick one)."));
                return;
            }
        }
        plugin.getAchievementManager().put(draft);
        viewer.sendMessage(Text.parse("<green>Saved achievement <white>" + draft.getId() + "<green>."));
        new AchievementMenu(plugin, viewer, true).open(viewer);
    }

    private void promptText(String prompt, java.util.function.Consumer<String> apply) {
        viewer.closeInventory();
        viewer.sendMessage(Text.parse("<gold>» <yellow>" + prompt));
        viewer.sendMessage(Text.parse("<gray>Type in chat, or type <white>cancel<gray> to go back."));
        plugin.getChatInput().await(viewer.getUniqueId(), input -> {
            if (input != null) {
                apply.accept(input);
            }
            open(viewer);
        });
    }

    private List<Component> lore(String... lines) {
        return lore(List.of(lines));
    }

    private List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line.isEmpty() ? Component.empty() : Text.item(line));
        }
        return out;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
