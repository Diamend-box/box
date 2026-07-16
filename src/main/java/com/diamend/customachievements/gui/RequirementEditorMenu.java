package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.LocationTarget;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.util.Items;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Edits a single objective (trigger, target, amount) of an achievement. */
public class RequirementEditorMenu implements Menu {

    private static final int SIZE = 27;
    private static final int SLOT_TRIGGER = 10;
    private static final int SLOT_TARGET = 12;
    private static final int SLOT_AMOUNT = 14;
    private static final int SLOT_BYNAME = 15;
    private static final int SLOT_DELETE = 16;
    private static final int SLOT_BACK = 22;

    private final CustomAchievementsPlugin plugin;
    private final Player viewer;
    private final Achievement draft;
    private final int index;
    private final EditorMenu parent;

    private Inventory inventory;

    public RequirementEditorMenu(CustomAchievementsPlugin plugin, Player viewer,
                                 Achievement draft, int index, EditorMenu parent) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.draft = draft;
        this.index = index;
        this.parent = parent;
    }

    private Requirement requirement() {
        return draft.getRequirements().get(index);
    }

    @Override
    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    private void rebuild() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SIZE, Text.parse("<dark_green>Objective " + (index + 1)));
        }
        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        Requirement requirement = requirement();
        TriggerType trigger = requirement.getTrigger();

        inventory.setItem(SLOT_TRIGGER, Items.of(trigger.icon(),
                Text.item("<aqua>Trigger: <white>" + trigger.display()),
                lore("<gray>What the player must do.", "", "<yellow>Click to choose a trigger")));

        inventory.setItem(SLOT_TARGET, buildTargetItem(trigger, requirement));

        if (trigger.isProgress()) {
            inventory.setItem(SLOT_AMOUNT, Items.of(Material.REPEATER,
                    Text.item("<aqua>Required Amount: <white>" + requirement.getAmount()),
                    lore("<gray>How much is needed.", "", "<yellow>Click to type a number")));
        } else {
            inventory.setItem(SLOT_AMOUNT, Items.of(Material.GRAY_DYE,
                    Text.item("<aqua>Required Amount"), lore("<dark_gray>Not used by this trigger.")));
        }

        if (trigger.isItemTrigger()) {
            inventory.setItem(SLOT_BYNAME, Items.of(
                    requirement.isMatchByName() ? Material.NAME_TAG : Material.PAPER,
                    Text.item("<aqua>Match by: " + (requirement.isMatchByName()
                            ? "<green>Custom Name" : "<white>Material")),
                    lore("<gray>Match a custom item display name",
                            "<gray>(e.g. \"Compressed Iron Ingots\")",
                            "<gray>instead of the material type.", "",
                            "<yellow>Click to toggle")));
        }

        if (draft.getRequirements().size() > 1) {
            inventory.setItem(SLOT_DELETE, Items.of(Material.TNT,
                    Text.item("<dark_red>Delete Objective"), lore("<red>Shift-click to confirm.")));
        }
        inventory.setItem(SLOT_BACK, Items.of(Material.EMERALD,
                Text.item("<green>Done"), lore("<gray>Back to the achievement.")));
    }

    private org.bukkit.inventory.ItemStack buildTargetItem(TriggerType trigger, Requirement requirement) {
        if (!trigger.usesTarget()) {
            return Items.of(Material.STRUCTURE_VOID, Text.item("<aqua>Target"),
                    lore("<dark_gray>Not used by this trigger."));
        }
        return switch (trigger) {
            case REACH_LOCATION -> {
                LocationTarget location = requirement.getLocationTarget();
                yield Items.of(Material.COMPASS, Text.item("<aqua>Target Location"),
                        lore(location != null ? "<white>" + location.pretty() : "<red>Not set",
                                "",
                                "<yellow>Left-click: use your current location",
                                "<yellow>Right-click: type world x y z [radius]"));
            }
            case MYTHIC_MOB_KILL -> Items.of(Material.WITHER_SKELETON_SKULL, Text.item("<aqua>Target Mythic Mob"),
                    lore("<white>" + requirement.getTarget(),
                            "",
                            "<gray>MythicMobs internal name, or ANY.",
                            "<yellow>Click to type"));
            default -> Items.of(Material.TARGET, Text.item("<aqua>Target: <white>" + requirement.getTarget()),
                    lore("<gray>What to match.", "", "<yellow>Click to pick from a list"));
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        Requirement requirement = requirement();
        switch (slot) {
            case SLOT_TRIGGER -> new TriggerPickerMenu(plugin, viewer, chosen -> {
                if (chosen != requirement.getTrigger()) {
                    requirement.setTrigger(chosen);
                    requirement.setTarget("ANY");
                }
                open(viewer);
            }, () -> open(viewer)).open(viewer);
            case SLOT_TARGET -> handleTarget(click, requirement);
            case SLOT_BYNAME -> {
                if (requirement.getTrigger().isItemTrigger()) {
                    requirement.setMatchByName(!requirement.isMatchByName());
                    open(viewer);
                }
            }
            case SLOT_AMOUNT -> {
                if (requirement.getTrigger().isProgress()) {
                    promptNumber("Enter the required amount:", value -> requirement.setAmount(value));
                }
            }
            case SLOT_DELETE -> {
                if (draft.getRequirements().size() > 1 && click == ClickType.SHIFT_LEFT) {
                    draft.getRequirements().remove(index);
                    parent.open(viewer);
                }
            }
            case SLOT_BACK -> parent.open(viewer);
            default -> {
                // background
            }
        }
    }

    private void handleTarget(ClickType click, Requirement requirement) {
        TriggerType trigger = requirement.getTrigger();
        if (!trigger.usesTarget()) {
            return;
        }
        switch (trigger) {
            case REACH_LOCATION -> {
                if (click.isRightClick()) {
                    promptText("Enter a location (world x y z [radius]) OR a threshold like Y>319:", input -> {
                        LocationTarget parsed = LocationTarget.parse(input);
                        if (parsed == null) {
                            viewer.sendMessage(Text.parse("<red>Bad format. Use: world x y z [radius], or Y>319"));
                        } else {
                            requirement.setTarget(parsed.serialize());
                        }
                    });
                } else {
                    Location loc = viewer.getLocation();
                    requirement.setTarget(new LocationTarget(viewer.getWorld().getName(),
                            loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5, 5).serialize());
                    open(viewer);
                }
            }
            case MYTHIC_MOB_KILL -> promptText("Enter the MythicMobs internal name (or ANY):",
                    input -> requirement.setTarget(input.trim().isEmpty() ? "ANY" : input.trim()));
            case ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN -> {
                if (requirement.isMatchByName()) {
                    promptText("Enter the custom item name to match (or ANY):",
                            input -> requirement.setTarget(input.trim().isEmpty() ? "ANY" : input.trim()));
                } else {
                    openTargetPicker(trigger, requirement);
                }
            }
            default -> openTargetPicker(trigger, requirement);
        }
    }

    private void openTargetPicker(TriggerType trigger, Requirement requirement) {
        new TargetPickerMenu(plugin, viewer, "Select Target",
                TargetCatalog.forTrigger(trigger),
                value -> {
                    requirement.setTarget(validateTarget(trigger, value));
                    open(viewer);
                },
                () -> open(viewer)).open(viewer);
    }

    private String validateTarget(TriggerType trigger, String value) {
        if (value == null || value.equalsIgnoreCase("ANY")) {
            return "ANY";
        }
        if (trigger == TriggerType.ENTITY_KILL) {
            try {
                return EntityType.valueOf(value.toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException ex) {
                return value;
            }
        }
        return value;
    }

    private void promptNumber(String prompt, java.util.function.IntConsumer apply) {
        promptText(prompt, input -> {
            try {
                apply.accept(Math.max(1, Integer.parseInt(input.trim())));
            } catch (NumberFormatException ex) {
                viewer.sendMessage(Text.parse("<red>That's not a whole number."));
            }
        });
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

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
