package com.diamend.boxcore;

import com.diamend.boxcore.gui.RecipeEditMenu;
import com.diamend.boxcore.gui.RecipeEditorMenu;
import com.diamend.boxcore.ore.CompactRecipe;
import com.diamend.boxcore.ore.CompactRecipes;
import com.diamend.boxcore.ore.CompressorModule;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adding and editing compaction recipes without leaving the game.
 */
class RecipeEditorTest {

    private static final int FIRST_RECIPE = 10;
    private static final int ADD_SLOT = 49;

    private static final int PLUS_ONE = 23;
    private static final int MINUS_TEN = 20;
    private static final int MINUS_64 = 19;
    private static final int NAME_SLOT = 27;
    private static final int LOOK_SLOT = 29;
    private static final int GLOW_SLOT = 31;
    private static final int RESET_SLOT = 33;
    private static final int LORE_SLOT = 35;
    private static final int DELETE_SLOT = 40;

    private ServerMock server;
    private BoxCorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BoxCorePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CompressorModule module() {
        CompressorModule found = plugin.compressor();
        assertNotNull(found, "compressor module should be active");
        return found;
    }

    private String coalId() {
        CompactRecipe coal = module().recipes().forInput(Material.COAL);
        assertNotNull(coal, "coal should have carried over from the old config");
        return coal.id();
    }

    private void openEditor(PlayerMock player) {
        new RecipeEditorMenu(plugin, module(), 0).open(player);
    }

    private void openRecipe(PlayerMock player, String id) {
        new RecipeEditMenu(plugin, module(), id).open(player);
    }

    private boolean isOpen(PlayerMock player, Class<?> menu) {
        InventoryView view = player.getOpenInventory();
        if (view == null || view.getTopInventory() == null) {
            return false;
        }
        return menu.isInstance(view.getTopInventory().getHolder());
    }

    /** A named, described item of a material, as an owner would make in an anvil. */
    private ItemStack styled(Material material, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        meta.displayName(Component.text(name));
        meta.lore(List.of(Component.text(loreLine)));
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    @Test
    void theEditorListsWhatIsOnTheBooks() {
        PlayerMock player = server.addPlayer();
        openEditor(player);
        Inventory menu = player.getOpenInventory().getTopInventory();

        CompactRecipe first = module().recipes().all().iterator().next();
        assertEquals(first.input(), menu.getItem(FIRST_RECIPE).getType(),
                "the first recipe is shown as the item it folds");
    }

    @Test
    void addingUsesWhateverIsInYourHand() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 1));
        openEditor(player);

        player.simulateInventoryClick(ADD_SLOT);
        server.getScheduler().performTicks(3);

        CompactRecipe added = module().recipes().forInput(Material.COBBLESTONE);
        assertNotNull(added, "cobblestone compacts now");
        assertEquals(64, added.amount(), "starting at a stack, adjustable from here");
        assertTrue(isOpen(player, RecipeEditMenu.class),
                "and it opens the new recipe rather than leaving you to find it");
    }

    @Test
    void addingWithAnEmptyHandIsRefused() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(null);
        int before = module().recipes().size();
        openEditor(player);

        player.simulateInventoryClick(ADD_SLOT);
        server.getScheduler().performTicks(3);

        assertEquals(before, module().recipes().size(), "nothing was added");
    }

    @Test
    void addingSomethingAlreadyOnTheBooksIsRefused() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.COAL, 1));
        int before = module().recipes().size();
        openEditor(player);

        player.simulateInventoryClick(ADD_SLOT);
        server.getScheduler().performTicks(3);

        assertEquals(before, module().recipes().size(),
                "one item, one recipe — otherwise a slot naming it would be guessing");
    }

    // ------------------------------------------------------------------
    // Editing one
    // ------------------------------------------------------------------

    @Test
    void theStepButtonsChangeHowMuchMakesAUnit() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(PLUS_ONE);
        assertEquals(65, module().recipes().get(id).amount());

        player.simulateInventoryClick(MINUS_TEN);
        assertEquals(55, module().recipes().get(id).amount());
    }

    @Test
    void theAmountCannotFallBelowTwo() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        for (int click = 0; click < 4; click++) {
            player.simulateInventoryClick(MINUS_64);
        }

        assertEquals(2, module().recipes().get(id).amount(),
                "one-into-one would not be a recipe");
    }

    @Test
    void aChangeIsOnDiskImmediately() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(PLUS_ONE);

        CompactRecipes reread = new CompactRecipes(plugin);
        reread.load();
        assertEquals(65, reread.get(id).amount(),
                "a crash between edits should cost nothing");
    }

    @Test
    void theLookIsCopiedOffTheItemInYourHand() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        player.getInventory().setItemInMainHand(
                styled(Material.PAPER, "Coal Briquette", "Pressed flat."));
        openRecipe(player, id);

        player.simulateInventoryClick(LOOK_SLOT);

        CompactRecipe now = module().recipes().get(id);
        assertEquals(Material.PAPER, now.appearance().material(), "the skin came across");
        assertNotNull(now.appearance().name());
        assertTrue(now.appearance().name().contains("Coal Briquette"),
                "and so did the name: " + now.appearance().name());
        assertNotNull(now.appearance().lore());
        assertTrue(now.appearance().lore().get(0).contains("Pressed flat."),
                "and the lore");
    }

    @Test
    void aPlaceableBlockIsRefusedAsASkin() {
        // Placing a block moves items out of the inventory without anything
        // counting them, which is the route the custom item exists to close.
        PlayerMock player = server.addPlayer();
        String id = coalId();
        Material before = module().recipes().get(id).appearance().materialOr(Material.COAL);
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 1));
        openRecipe(player, id);

        player.simulateInventoryClick(LOOK_SLOT);

        assertEquals(before, module().recipes().get(id).appearance().materialOr(Material.COAL),
                "the skin is unchanged");
    }

    @Test
    void theGlowToggleFlips() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        boolean before = module().recipes().get(id).appearance().glow();
        openRecipe(player, id);

        player.simulateInventoryClick(GLOW_SLOT);

        assertEquals(!before, module().recipes().get(id).appearance().glow());
    }

    @Test
    void resettingPutsTheLookBack() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        player.getInventory().setItemInMainHand(
                styled(Material.PAPER, "Coal Briquette", "Pressed flat."));
        openRecipe(player, id);
        player.simulateInventoryClick(LOOK_SLOT);
        assertEquals(Material.PAPER, module().recipes().get(id).appearance().material());

        player.simulateInventoryClick(RESET_SLOT);

        CompactRecipe now = module().recipes().get(id);
        assertEquals(Material.COAL, now.appearance().materialOr(Material.COAL));
        assertNull(now.appearance().name(), "back to the built-in name");
    }

    // ------------------------------------------------------------------
    // Typing the words
    // ------------------------------------------------------------------

    /** Answers the question the menu just asked, and lets the answer land. */
    private void answer(PlayerMock player, String text) {
        assertTrue(plugin.prompts().deliver(player, text), "something should be asking");
        server.getScheduler().performOneTick();
    }

    @Test
    void theNameCanBeTyped() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(NAME_SLOT);
        answer(player, "<gold>Coal Brick");

        assertEquals("<gold>Coal Brick", module().recipes().get(id).appearance().name());
        assertTrue(isOpen(player, RecipeEditMenu.class),
                "and it puts you back where you were");
    }

    @Test
    void aTypedNameCanCarryTheRatio() {
        // The reason typing exists at all: an anvil writes a fixed string, and
        // "64 coal" stops being true the first time somebody edits the amount.
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(NAME_SLOT);
        answer(player, "<ratio> coal");

        assertTrue(module().recipes().get(id).appearance().name().contains("<ratio>"),
                "the token is stored, not resolved at the time it was typed");
    }

    @Test
    void shiftClickingTheNamePutsTheBuiltInOneBack() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);
        player.simulateInventoryClick(NAME_SLOT);
        answer(player, "Coal Brick");

        openRecipe(player, id);
        player.simulateInventoryClick(player.getOpenInventory(), ClickType.SHIFT_LEFT, NAME_SLOT);

        assertNull(module().recipes().get(id).appearance().name(), "back to the built-in name");
    }

    @Test
    void cancellingLeavesTheNameAlone() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(NAME_SLOT);
        answer(player, "cancel");

        assertNull(module().recipes().get(id).appearance().name(), "nothing was written");
    }

    @Test
    void loreLinesAreAddedOneAtATime() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(LORE_SLOT);
        answer(player, "Pressed flat.");
        player.simulateInventoryClick(LORE_SLOT);
        answer(player, "Worth <ratio>.");

        List<String> lines = module().recipes().get(id).appearance().lore();
        assertNotNull(lines);
        assertEquals(List.of("Pressed flat.", "Worth <ratio>."), lines);
    }

    @Test
    void rightClickingDropsTheLastLine() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);
        player.simulateInventoryClick(LORE_SLOT);
        answer(player, "Pressed flat.");
        player.simulateInventoryClick(LORE_SLOT);
        answer(player, "A mistake.");

        openRecipe(player, id);
        player.simulateInventoryClick(player.getOpenInventory(), ClickType.RIGHT, LORE_SLOT);

        assertEquals(List.of("Pressed flat."), module().recipes().get(id).appearance().lore());
    }

    @Test
    void shiftClickingTheLoreClearsIt() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);
        player.simulateInventoryClick(LORE_SLOT);
        answer(player, "Pressed flat.");

        openRecipe(player, id);
        player.simulateInventoryClick(player.getOpenInventory(), ClickType.SHIFT_LEFT, LORE_SLOT);

        assertNull(module().recipes().get(id).appearance().lore(),
                "back to the built-in lines");
    }

    // ------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------

    @Test
    void deletingTakesAShiftClick() {
        PlayerMock player = server.addPlayer();
        String id = coalId();
        openRecipe(player, id);

        player.simulateInventoryClick(DELETE_SLOT);
        assertNotNull(module().recipes().get(id), "a plain click only asks");

        player.simulateInventoryClick(player.getOpenInventory(),
                ClickType.SHIFT_LEFT, DELETE_SLOT);
        assertNull(module().recipes().get(id), "a shift-click does it");
    }

    // ------------------------------------------------------------------
    // Reaching it
    // ------------------------------------------------------------------

    @Test
    void theCommandOpensTheEditor() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(player.performCommand("box compactor recipes"));

        assertTrue(isOpen(player, RecipeEditorMenu.class));
    }

    @Test
    void theEditorNeedsAdmin() {
        PlayerMock player = server.addPlayer();

        assertTrue(player.performCommand("box compactor recipes"));

        assertFalse(isOpen(player, RecipeEditorMenu.class), "no admin permission, no editor");
    }
}
