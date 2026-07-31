package com.diamend.boxcore;

import com.diamend.boxcore.gui.CompactorMenu;
import com.diamend.boxcore.gui.RecipePickerMenu;
import com.diamend.boxcore.ore.CompactRecipe;
import com.diamend.boxcore.ore.CompactRecipes;
import com.diamend.boxcore.ore.CompactorTier;
import com.diamend.boxcore.ore.CompressorModule;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
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
 * The personal compactor as a thing a player owns: what it remembers, how it is
 * configured, and the ways it must refuse to be lost.
 */
class CompactorTest {

    /** Where CompactorMenu puts the first configurable slot. */
    private static final int FIRST_SLOT = 10;
    /** Where RecipePickerMenu puts the first recipe. */
    private static final int FIRST_RECIPE = 10;

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

    private ItemStack compactor(int tier, String... filters) {
        CompactorTier grade = module().tiers().get(tier);
        assertNotNull(grade, "tier " + tier + " should be configured");
        ItemStack item = module().compactors().create(grade);
        assertNotNull(item);
        return module().compactors().withFilters(item, List.of(filters));
    }

    /** The id of the first recipe on the books, whatever the config named it. */
    private String firstRecipeId() {
        CompactRecipe first = module().recipes().all().iterator().next();
        return first.id();
    }

    // ------------------------------------------------------------------
    // What the item remembers
    // ------------------------------------------------------------------

    @Test
    void aCompactorRemembersItsTierAndSlots() {
        ItemStack item = compactor(2);
        assertTrue(module().compactors().isCompactor(item));
        assertEquals(2, module().compactors().tierLevel(item));
        assertEquals(module().tiers().get(2).slots(), module().compactors().slots(item));
        assertFalse(module().compactors().isCompactor(new ItemStack(Material.HOPPER, 1)),
                "a plain hopper is not a compactor");
    }

    @Test
    void slottingIsWrittenOntoTheItem() {
        String id = firstRecipeId();
        ItemStack item = compactor(2, id);

        assertEquals(id, module().compactors().filters(item).get(0));
        assertEquals(1, module().compactors().active(item).size(),
                "one slot filled, two empty");
    }

    @Test
    void theSlotListIsAlwaysAsLongAsTheCompactor() {
        ItemStack item = compactor(2);
        assertEquals(module().tiers().get(2).slots(), module().compactors().filters(item).size(),
                "callers index this without checking, so it has to be full length");
        for (String id : module().compactors().filters(item)) {
            assertEquals("", id, "and empty where nothing is slotted");
        }
    }

    @Test
    void aRecipeThatNoLongerExistsIsQuietlyIgnored() {
        // A recipe can be deleted while compactors still name it. That must read
        // as an empty slot, not throw on the next sweep.
        ItemStack item = compactor(2, "not-a-recipe");
        assertEquals("not-a-recipe", module().compactors().filters(item).get(0));
        assertTrue(module().compactors().active(item).isEmpty(),
                "an unknown id folds nothing");
    }

    // ------------------------------------------------------------------
    // The recipe book
    // ------------------------------------------------------------------

    @Test
    void theBookIsSeededFromTheOldOreConfig() {
        assertTrue(module().recipes().size() > 0,
                "an existing setup should carry over rather than start empty");
        CompactRecipe coal = module().recipes().forInput(Material.COAL);
        assertNotNull(coal, "coal compressed before, so it compacts now");
        assertEquals(64, coal.amount(), "at the ratio it was configured with");
    }

    @Test
    void aRecipeSurvivesBeingWrittenAndReadBack() {
        assertTrue(module().recipes().put(
                new CompactRecipe("cobble", Material.COBBLESTONE, 160, null)));

        CompactRecipes reread = new CompactRecipes(plugin);
        reread.load();

        CompactRecipe found = reread.get("cobble");
        assertNotNull(found, "it should still be there after a reload");
        assertEquals(Material.COBBLESTONE, found.input());
        assertEquals(160, found.amount());
    }

    @Test
    void twoRecipesCannotClaimTheSameItem() {
        assertTrue(module().recipes().put(
                new CompactRecipe("cobble", Material.COBBLESTONE, 160, null)));
        assertFalse(module().recipes().put(
                        new CompactRecipe("rubble", Material.COBBLESTONE, 64, null)),
                "auto-compaction would have to guess which one a slot meant");
    }

    @Test
    void aDeletedRecipeIsGoneFromBothLookups() {
        assertTrue(module().recipes().put(
                new CompactRecipe("cobble", Material.COBBLESTONE, 160, null)));
        assertTrue(module().recipes().remove("cobble"));

        assertNull(module().recipes().get("cobble"));
        assertNull(module().recipes().forInput(Material.COBBLESTONE),
                "removing it must free the material for another recipe");
    }

    // ------------------------------------------------------------------
    // Opening it
    // ------------------------------------------------------------------

    @Test
    void carryingOneOpensIt() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, compactor(2));

        module().openFor(player);

        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof CompactorMenu);
    }

    @Test
    void carryingNoneSaysSoInsteadOfOpeningNothing() {
        PlayerMock player = server.addPlayer();
        while (player.nextMessage() != null) {
            // drain
        }

        module().openFor(player);

        assertNotNull(player.nextMessage(), "they are told why nothing opened");
        assertFalse(player.getOpenInventory().getTopInventory().getHolder()
                instanceof CompactorMenu);
    }

    @Test
    void clickingASlotOpensThePicker() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, compactor(2));
        module().openFor(player);

        player.simulateInventoryClick(FIRST_SLOT);
        server.getScheduler().performTicks(3);

        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof RecipePickerMenu);
    }

    @Test
    void pickingARecipeWritesItIntoTheSlot() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, compactor(2));
        module().openFor(player);

        player.simulateInventoryClick(FIRST_SLOT);
        server.getScheduler().performTicks(3);
        player.simulateInventoryClick(FIRST_RECIPE);
        server.getScheduler().performTicks(3);

        ItemStack held = player.getInventory().getItem(0);
        assertEquals(firstRecipeId(), module().compactors().filters(held).get(0),
                "what was picked is what the compactor now folds");
    }

    @Test
    void shiftClickingASlotEmptiesIt() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, compactor(2, firstRecipeId()));
        module().openFor(player);

        player.simulateInventoryClick(player.getOpenInventory(),
                ClickType.SHIFT_LEFT, FIRST_SLOT);

        ItemStack held = player.getInventory().getItem(0);
        assertEquals("", module().compactors().filters(held).get(0));
        assertTrue(module().compactors().active(held).isEmpty());
    }

    @Test
    void theMenuWillNotWriteOntoSomethingThatIsNoLongerACompactor() {
        // The inventory can be rearranged with the menu open. Writing the new
        // assignments onto whatever is in the remembered slot would be a good
        // way to turn someone's pickaxe into a compactor.
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, compactor(2));
        module().openFor(player);

        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_PICKAXE, 1));
        CompactorMenu.assign(plugin, module(), player, 0, 0, firstRecipeId());

        ItemStack now = player.getInventory().getItem(0);
        assertEquals(Material.DIAMOND_PICKAXE, now.getType(), "the pickaxe is untouched");
        assertFalse(module().compactors().isCompactor(now));
    }

    // ------------------------------------------------------------------
    // Not losing it
    // ------------------------------------------------------------------

    /**
     * The raw slot the player's own inventory view shows a material at.
     *
     * <p>Found rather than assumed: the raw-slot layout of the crafting view is
     * a mapping this test has no business hard-coding.
     */
    private int rawSlotOf(PlayerMock player, Material material) {
        for (int raw = 0; raw < player.getOpenInventory().countSlots(); raw++) {
            ItemStack item = player.getOpenInventory().getItem(raw);
            if (item != null && item.getType() == material) {
                return raw;
            }
        }
        return -1;
    }

    @Test
    void aCompactorCannotBeDroppedOutOfTheInventory() {
        PlayerMock player = server.addPlayer();
        ItemStack item = compactor(1);
        player.getInventory().setItem(9, item);
        int raw = rawSlotOf(player, item.getType());
        assertTrue(raw >= 0, "the compactor should be visible in the player's own view");

        InventoryClickEvent event = player.simulateInventoryClick(
                player.getOpenInventory(), ClickType.DROP, raw);

        assertTrue(event.isCancelled(), "Q over a compactor does nothing");
        assertTrue(module().compactors().isCompactor(player.getInventory().getItem(9)),
                "and it is still there");
    }

    @Test
    void anOrdinaryItemCanStillBeDropped() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(9, new ItemStack(Material.COAL, 8));
        int raw = rawSlotOf(player, Material.COAL);
        assertTrue(raw >= 0);

        InventoryClickEvent event = player.simulateInventoryClick(
                player.getOpenInventory(), ClickType.DROP, raw);

        assertFalse(event.isCancelled(), "only the compactor is protected");
    }

    @Test
    void whatIsPulledOutOfADeathDropComesBack() {
        PlayerMock player = server.addPlayer();
        ItemStack kept = compactor(3);

        module().keepThroughDeath(player.getUniqueId(), List.of(kept));
        List<ItemStack> back = module().reclaimAfterDeath(player.getUniqueId());

        assertEquals(1, back.size());
        assertTrue(module().compactors().isCompactor(back.get(0)));
        assertTrue(module().reclaimAfterDeath(player.getUniqueId()).isEmpty(),
                "handed back once, not on every respawn afterwards");
    }

    // ------------------------------------------------------------------
    // The admin command
    // ------------------------------------------------------------------

    @Test
    void theGiveCommandHandsOutACompactor() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.getInventory().clear();

        assertTrue(player.performCommand("box compactor give 2"));

        assertTrue(module().hasCompactor(player), "they are carrying one now");
        ItemStack given = module().firstCompactor(player);
        assertEquals(module().tiers().get(2).slots(), module().compactors().slots(given));
    }

    @Test
    void theGiveCommandNeedsPermission() {
        PlayerMock player = server.addPlayer();
        player.getInventory().clear();

        assertTrue(player.performCommand("box compactor give 2"));

        assertFalse(module().hasCompactor(player), "no admin permission, no compactor");
    }

    @Test
    void anUnknownTierIsRefused() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.getInventory().clear();

        assertTrue(player.performCommand("box compactor give 99"));

        assertFalse(module().hasCompactor(player));
    }
}
