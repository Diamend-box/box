package com.diamend.robobear.gui;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.ObjectiveType;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.mine.MineSurvey;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The quest editor: what kinds of job exist, and what each mine can be asked for.
 *
 * <p>Built because the generator once offered "30 gold in the quartz mine". It
 * picked a mine and a material independently, and nothing checked the mine
 * contained the material — an objective that cost whoever took it their run
 * through no fault of theirs.
 *
 * <p>The fix is automatic: materials now come from what MineResetLite says the
 * mine is made of. This screen is where you see what it worked out, and correct
 * it where it's wrong.
 */
public class QuestEditorMenu extends AbstractMenu {

    private static final int SLOT_INFO = 4;
    private static final int[] TYPE_SLOTS = { 10, 12, 14 };
    private static final int SLOT_HELP = 16;
    private static final int MINE_START = 27;
    private static final int PER_PAGE = 18;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    private int page;
    private int pages = 1;
    private List<MineRegion> shown = List.of();

    public QuestEditorMenu(RoboBearPlugin plugin) {
        super(plugin, 54, "<dark_gray>Quests");
    }

    @Override
    protected void build(Player player) {
        ObjectiveType[] types = ObjectiveType.values();
        for (int i = 0; i < TYPE_SLOTS.length && i < types.length; i++) {
            set(TYPE_SLOTS[i], typeIcon(types[i]));
        }
        set(SLOT_INFO, info());
        set(SLOT_HELP, help());

        List<MineRegion> all = plugin.mines().enabled();
        pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PER_PAGE;
        shown = new ArrayList<>(all.subList(from, Math.min(all.size(), from + PER_PAGE)));

        for (int i = 0; i < shown.size(); i++) {
            set(MINE_START + i, mineIcon(shown.get(i)));
        }
        if (all.isEmpty()) {
            set(MINE_START + 4, Items.text(Material.BARRIER, "<red>No mines in the pool", List.of(
                    "<gray>Material jobs need a mine to happen in.",
                    "<yellow>/rb mines edit")));
        }

        if (page > 0) {
            set(SLOT_PREV, Items.text(Material.ARROW, "<yellow>« Page " + page, List.of()));
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, Items.text(Material.ARROW, "<yellow>Page " + (page + 2) + " »", List.of()));
        }
        closeButton(SLOT_CLOSE);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack info() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Job types above, mines below.");
        lore.add("");
        lore.add("<gray>Mines in the pool: <white>" + plugin.mines().enabledSize());
        lore.add("<gray>Mines you've corrected: <white>" + plugin.mines().materials().size());
        lore.add("<gray>Page <white>" + (page + 1) + "<gray> of <white>" + pages);
        return Items.text(Material.WRITABLE_BOOK, "<yellow>Quests", lore);
    }

    private ItemStack help() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>A material job only ever asks for");
        lore.add("<gray>something the mine actually contains.");
        lore.add("");
        if (plugin.mines().hasDetectedMaterials()) {
            lore.add("<green>Your mines have been read, either from");
            lore.add("<green>the mine plugin or from the blocks");
            lore.add("<green>themselves, so this is automatic.");
        } else {
            lore.add("<yellow>Nothing could be read about any mine,");
            lore.add("<yellow>so the config list is used for all of");
            lore.add("<yellow>them. Correct the ones that matter");
            lore.add("<yellow>below, or check mines.sample-blocks.");
        }
        lore.add("");
        lore.add("<dark_gray>Narrowed by the list in config.yml");
        lore.add("<dark_gray>at objectives.mine-material.materials");
        return Items.text(Material.COMPASS, "<yellow>How materials are picked", lore);
    }

    private ItemStack typeIcon(ObjectiveType type) {
        boolean onInGame = plugin.service().objectives().allowedInGame(type);
        boolean onInConfig = plugin.service().objectives().allowedByConfig(type);
        boolean on = onInGame && onInConfig;

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + describe(type));
        lore.add("");
        if (!onInConfig) {
            lore.add("<red>Turned off in config.yml.");
            lore.add("<dark_gray>objectives." + key(type) + ".enabled");
            lore.add("<gray>Set it there, then /rb reload.");
        } else {
            lore.add(on ? "<green>Being offered" : "<red>Not offered");
            String blocked = on ? blocker(type) : null;
            if (blocked != null) {
                lore.add("<red>…except it can't be: " + blocked);
            }
            lore.add("");
            lore.add(on ? "<gray>Click to stop offering it" : "<gray>Click to start offering it");
        }
        return Items.text(icon(type), (on ? "<green>" : "<dark_gray>") + title(type), lore, on);
    }

    /**
     * Why a type that's switched on still can't be rolled, or null when it can.
     * Worth saying out loud: "on" and "actually offered" come apart easily here,
     * and a silently absent objective type is hard to explain from the outside.
     */
    private String blocker(ObjectiveType type) {
        if (type == ObjectiveType.KILL_MOBS) {
            return null;
        }
        if (plugin.mines().enabledSize() == 0) {
            return "no mines are in the pool.";
        }
        if (type == ObjectiveType.MINE_MATERIAL) {
            if (plugin.mines().configuredMaterials().isEmpty()) {
                return "config.yml's material list is empty.";
            }
            if (plugin.mines().minesWithMaterials().isEmpty()) {
                return "no mine contains anything on that list.";
            }
        }
        return null;
    }

    private ItemStack mineIcon(MineRegion mine) {
        List<org.bukkit.Material> effective = plugin.mines().materialsFor(mine.id());
        boolean corrected = plugin.mines().materials().isOverridden(mine.id());
        int detected = plugin.mines().detectedMaterials(mine.id()).size();

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + mine.world());
        lore.add("");
        if (effective.isEmpty()) {
            lore.add("<red>No material job can be set here.");
            lore.add("<gray>Nothing it contains is on the");
            lore.add("<gray>config's list of things to ask for.");
        } else {
            lore.add("<gray>Can be asked for:");
            int listed = 0;
            for (org.bukkit.Material material : effective) {
                if (listed++ >= 6) {
                    lore.add("<dark_gray> …and " + (effective.size() - 6) + " more");
                    break;
                }
                lore.add("<dark_gray> • <white>" + Items.prettyName(material));
            }
        }
        lore.add("");
        lore.add(corrected
                ? "<yellow>Set by hand"
                : detected > 0
                        ? "<dark_gray>Read from the mine (" + detected + " block types)"
                        : "<dark_gray>Falling back to the config list");

        // What the size clamp is working from. Without this, a job that came
        // out smaller than the round should have looks like a bug.
        MineSurvey survey = plugin.mines().surveyOf(mine.id());
        if (survey.foundAnything()) {
            lore.add("<dark_gray>Holds roughly <gray>"
                    + Text.number(survey.estimateFilled(mine.volume())) + "<dark_gray> blocks");
        }
        lore.add("");
        lore.add("<gray>Click to choose its materials");
        if (corrected) {
            lore.add("<gray>Shift-click to go back to automatic");
        }
        return Items.text(icon(effective),
                (effective.isEmpty() ? "<red>" : "<yellow>") + mine.id(), lore, corrected);
    }

    /**
     * A mine's icon is the first thing it can be asked for — but a few blocks
     * (water, fire) have no item form and would come out as an unplaceable
     * stack, so fall back to something that always renders.
     */
    private static Material icon(List<Material> effective) {
        for (Material material : effective) {
            if (material.isItem()) {
                return material;
            }
        }
        return effective.isEmpty() ? Material.BARRIER : Material.STONE;
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
        if (slot == SLOT_PREV && page > 0) {
            click(player);
            page--;
            refresh(player);
            return;
        }
        if (slot == SLOT_NEXT && page < pages - 1) {
            click(player);
            page++;
            refresh(player);
            return;
        }

        ObjectiveType[] types = ObjectiveType.values();
        for (int i = 0; i < TYPE_SLOTS.length && i < types.length; i++) {
            if (slot != TYPE_SLOTS[i]) {
                continue;
            }
            ObjectiveType type = types[i];
            boolean on = plugin.service().objectives().isEnabled(type);
            if (!plugin.service().objectives().setEnabled(type, !on)) {
                deny(player);
                player.sendMessage(Text.parse("<red>That type is off in config.yml. "
                        + "<gray>Set <white>objectives." + key(type)
                        + ".enabled: true<gray>, then /rb reload."));
                return;
            }
            click(player);
            refresh(player);
            return;
        }

        int index = slot - MINE_START;
        if (index < 0 || index >= shown.size()) {
            return;
        }
        MineRegion mine = shown.get(index);

        if (event.isShiftClick()) {
            if (plugin.mines().materials().isOverridden(mine.id())) {
                plugin.mines().materials().set(mine.id(), List.of());
                player.sendMessage(Text.parse("<green>" + mine.id()
                        + " is back to using what the mine is made of."));
                click(player);
                refresh(player);
            } else {
                deny(player);
            }
            return;
        }
        click(player);
        new MineMaterialsMenu(plugin, player, mine).open(player);
    }

    // ------------------------------------------------------------------

    private static String key(ObjectiveType type) {
        return com.diamend.robobear.challenge.ObjectiveToggles.key(type);
    }

    private static String title(ObjectiveType type) {
        return switch (type) {
            case MINE_BLOCKS -> "Break blocks";
            case MINE_MATERIAL -> "Break a material";
            case KILL_MOBS -> "Kill mobs";
        };
    }

    private static String describe(ObjectiveType type) {
        return switch (type) {
            case MINE_BLOCKS -> "\"Break N blocks in <mine>\" — anything counts.";
            case MINE_MATERIAL -> "\"Break N × <block> in <mine>\" — one type.";
            case KILL_MOBS -> "\"Kill N hostile mobs\" — anywhere.";
        };
    }

    private static Material icon(ObjectiveType type) {
        return switch (type) {
            case MINE_BLOCKS -> Material.STONE_PICKAXE;
            case MINE_MATERIAL -> Material.RAW_IRON;
            case KILL_MOBS -> Material.IRON_SWORD;
        };
    }
}
