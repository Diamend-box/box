package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A fully GUI-driven editor for a single achievement. Each field is a clickable
 * item; numeric fields use click/shift-click to adjust, the trigger cycles on
 * left/right click, and free-text fields open a short chat prompt.
 */
public class EditorMenu implements Menu {

    private static final int SIZE = 45;

    private static final int SLOT_ID = 10;
    private static final int SLOT_ICON = 12;
    private static final int SLOT_NAME = 14;
    private static final int SLOT_DESC = 16;
    private static final int SLOT_TRIGGER = 20;
    private static final int SLOT_TARGET = 22;
    private static final int SLOT_AMOUNT = 24;
    private static final int SLOT_ANNOUNCE = 29;
    private static final int SLOT_XP = 31;
    private static final int SLOT_COMMANDS = 33;
    private static final int SLOT_CANCEL = 38;
    private static final int SLOT_SAVE = 40;
    private static final int SLOT_DELETE = 42;

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

        ItemStack filler = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // ID
        inventory.setItem(SLOT_ID, Items.of(Material.NAME_TAG,
                Text.item("<aqua>Identifier"),
                lore("<white>" + draft.getId(),
                        "",
                        creating ? "<yellow>Click to change" : "<dark_gray>Locked while editing")));

        // Icon
        inventory.setItem(SLOT_ICON, Items.of(draft.getIcon(),
                Text.item("<aqua>Icon <dark_gray>(" + draft.getIcon().name() + ")"),
                lore("<gray>The item shown in the menu.",
                        "",
                        "<yellow>Click while holding an item to set it,",
                        "<yellow>or click empty-handed to type a name.")));

        // Name
        inventory.setItem(SLOT_NAME, Items.of(Material.WRITABLE_BOOK,
                Text.item("<aqua>Display Name"),
                lore("<white>" + draft.getDisplayName(),
                        "",
                        "<gray>Supports MiniMessage colours.",
                        "<yellow>Click to edit")));

        // Description
        List<String> descLines = new ArrayList<>();
        descLines.add("<gray>Current:");
        for (String line : draft.getDescription()) {
            descLines.add("<white>• " + line);
        }
        descLines.add("");
        descLines.add("<yellow>Click to edit (use | for new lines)");
        inventory.setItem(SLOT_DESC, Items.of(Material.BOOK,
                Text.item("<aqua>Description"), lore(descLines)));

        // Trigger
        inventory.setItem(SLOT_TRIGGER, Items.of(Material.COMPARATOR,
                Text.item("<aqua>Trigger: <white>" + draft.getTrigger().display()),
                lore("<gray>How this achievement is earned.",
                        "<dark_gray>" + draft.getTrigger().name(),
                        "",
                        "<yellow>Left-click: next   <yellow>Right-click: previous")));

        // Target
        boolean usesTarget = draft.getTrigger().usesTarget();
        inventory.setItem(SLOT_TARGET, Items.of(usesTarget ? Material.TARGET : Material.STRUCTURE_VOID,
                Text.item("<aqua>Target"),
                usesTarget
                        ? lore("<white>" + draft.getTarget(),
                        "",
                        "<gray>A block/item/entity name, or ANY.",
                        "<yellow>Click to edit")
                        : lore("<dark_gray>Not used by this trigger.")));

        // Amount
        boolean progress = draft.getTrigger().isProgress();
        inventory.setItem(SLOT_AMOUNT, Items.of(Material.REPEATER,
                Text.item("<aqua>Required Amount: <white>" + draft.getAmount()),
                progress
                        ? lore("<gray>How many times the trigger must fire.",
                        "",
                        "<yellow>Left +1 <dark_gray>|<yellow> Shift-Left +10",
                        "<yellow>Right -1 <dark_gray>|<yellow> Shift-Right -10")
                        : lore("<dark_gray>Not used by this trigger.")));

        // Announce
        inventory.setItem(SLOT_ANNOUNCE, Items.of(
                draft.isAnnounce() ? Material.LIME_DYE : Material.GRAY_DYE,
                Text.item("<aqua>Broadcast on Unlock: " + (draft.isAnnounce() ? "<green>ON" : "<red>OFF")),
                lore("<gray>Announce to the whole server.",
                        "",
                        "<yellow>Click to toggle")));

        // Reward XP
        inventory.setItem(SLOT_XP, Items.of(Material.EXPERIENCE_BOTTLE,
                Text.item("<aqua>Reward XP: <white>" + draft.getRewardXp()),
                lore("<gray>Experience granted on unlock.",
                        "",
                        "<yellow>Left +10 <dark_gray>|<yellow> Shift-Left +100",
                        "<yellow>Right -10 <dark_gray>|<yellow> Shift-Right -100")));

        // Reward commands
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
        cmdLines.add("<yellow>Left-click: add   <yellow>Right-click: clear");
        inventory.setItem(SLOT_COMMANDS, Items.of(Material.COMMAND_BLOCK,
                Text.item("<aqua>Reward Commands"), lore(cmdLines)));

        // Actions
        inventory.setItem(SLOT_CANCEL, Items.of(Material.BARRIER,
                Text.item("<red><bold>Cancel"),
                lore("<gray>Discard changes and go back.")));
        inventory.setItem(SLOT_SAVE, Items.of(Material.EMERALD_BLOCK,
                Text.item("<green><bold>Save"),
                lore("<gray>Save this achievement.")));
        if (!creating) {
            inventory.setItem(SLOT_DELETE, Items.of(Material.TNT,
                    Text.item("<dark_red><bold>Delete"),
                    lore("<gray>Permanently remove this achievement.",
                            "",
                            "<red>Shift-click to confirm.")));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ClickType click = event.getClick();

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
            case SLOT_NAME -> promptText("Enter the display name (MiniMessage supported):",
                    input -> draft.setDisplayName(input));
            case SLOT_DESC -> promptText("Enter the description (use | to separate lines):", input -> {
                List<String> lines = new ArrayList<>();
                for (String part : input.split("\\|")) {
                    lines.add(part.trim());
                }
                draft.setDescription(lines);
            });
            case SLOT_TRIGGER -> {
                draft.setTrigger(click.isRightClick() ? draft.getTrigger().prev() : draft.getTrigger().next());
                rebuild();
            }
            case SLOT_TARGET -> {
                if (draft.getTrigger().usesTarget()) {
                    promptText("Enter a target name (or ANY). e.g. DIAMOND_ORE, ZOMBIE:",
                            this::applyTarget);
                }
            }
            case SLOT_AMOUNT -> {
                if (draft.getTrigger().isProgress()) {
                    draft.setAmount(draft.getAmount() + delta(click, 1, 10));
                    rebuild();
                }
            }
            case SLOT_ANNOUNCE -> {
                draft.setAnnounce(!draft.isAnnounce());
                rebuild();
            }
            case SLOT_XP -> {
                draft.setRewardXp(draft.getRewardXp() + delta(click, 10, 100));
                rebuild();
            }
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
            case SLOT_CANCEL -> new AchievementMenu(plugin, viewer, true).open(viewer);
            case SLOT_SAVE -> save();
            case SLOT_DELETE -> {
                if (!creating && click == ClickType.SHIFT_LEFT) {
                    plugin.getAchievementManager().remove(draft.getId());
                    viewer.sendMessage(Text.parse("<red>Deleted achievement <white>" + draft.getId() + "<red>."));
                    new AchievementMenu(plugin, viewer, true).open(viewer);
                }
            }
            default -> {
                // Clicking decorative background does nothing.
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

    private void applyTarget(String input) {
        String value = input.trim();
        if (value.equalsIgnoreCase("ANY")) {
            draft.setTarget("ANY");
            return;
        }
        if (draft.getTrigger() == TriggerType.ENTITY_KILL) {
            try {
                EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
                draft.setTarget(type.name());
            } catch (IllegalArgumentException ex) {
                viewer.sendMessage(Text.parse("<red>Unknown entity type: <white>" + input));
            }
        } else {
            Material material = Material.matchMaterial(value);
            if (material == null) {
                viewer.sendMessage(Text.parse("<red>Unknown material: <white>" + input));
            } else {
                draft.setTarget(material.name());
            }
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
        if (draft.getTrigger().usesTarget()
                && (draft.getTarget() == null || draft.getTarget().isBlank())) {
            draft.setTarget("ANY");
        }
        plugin.getAchievementManager().put(draft);
        viewer.sendMessage(Text.parse("<green>Saved achievement <white>" + draft.getId() + "<green>."));
        new AchievementMenu(plugin, viewer, true).open(viewer);
    }

    private int delta(ClickType click, int small, int big) {
        int magnitude = click.isShiftClick() ? big : small;
        return click.isRightClick() ? -magnitude : magnitude;
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
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line.isEmpty() ? Component.empty() : Text.item(line));
        }
        return out;
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
