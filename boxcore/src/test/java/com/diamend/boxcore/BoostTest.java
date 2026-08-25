package com.diamend.boxcore;

import com.diamend.boxcore.boost.Boost;
import com.diamend.boxcore.boost.BoostItems;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.boost.DropGuard;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Durations;
import org.bukkit.Material;
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
 * Boosts: how multipliers combine, that they end when they say they will, and
 * that the two types stay independent of each other.
 */
class BoostTest {

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

    private BoostsModule boosts() {
        BoostsModule module = plugin.boosts();
        assertNotNull(module, "boosts module should be active");
        return module;
    }

    // ------------------------------------------------------------------
    // Combining
    // ------------------------------------------------------------------

    @Test
    void noBoostsMeansNoChange() {
        PlayerMock player = server.addPlayer();
        assertEquals(1.0, boosts().multiplier(player, BoostType.DROPS));
        assertEquals(1.0, boosts().multiplier(player, BoostType.COLLECTIONS));
        assertEquals(40, boosts().boosted(player, BoostType.DROPS, 40));
    }

    @Test
    void globalAndPersonalMultiplyTogether() {
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 2.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 60_000, "test");

        assertEquals(4.0, boosts().multiplier(player, BoostType.DROPS),
                "2x global and 2x personal is 4x");
    }

    @Test
    void aPersonalBoostIsPersonal() {
        PlayerMock lucky = server.addPlayer();
        PlayerMock other = server.addPlayer();
        boosts().addPlayer(lucky, BoostType.DROPS, 3.0, 60_000, "test");

        assertEquals(3.0, boosts().multiplier(lucky, BoostType.DROPS));
        assertEquals(1.0, boosts().multiplier(other, BoostType.DROPS),
                "nobody else is boosted");
    }

    @Test
    void theTypesDoNotBleedIntoEachOther() {
        // The whole reason they are separate types: a drops boost gives more
        // ore without inflating collection progress.
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 2.0, 60_000, "test");

        assertEquals(2.0, boosts().multiplier(player, BoostType.DROPS));
        assertEquals(1.0, boosts().multiplier(player, BoostType.COLLECTIONS),
                "boosting drops must not boost collections");
    }

    @Test
    void theCapIsAHardCeiling() {
        // One of each still multiplies, so the cap still has work to do.
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 5.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 5.0, 60_000, "test");

        assertEquals(boosts().maxMultiplier(), boosts().multiplier(player, BoostType.DROPS),
                "25x worth of boosts still lands on the cap");
    }

    @Test
    void aSecondGlobalReplacesTheFirstRatherThanStacking() {
        // Two people spending an item within the hour used to make 4x without
        // anyone deciding that should happen.
        PlayerMock player = server.addPlayer();
        for (int i = 0; i < 5; i++) {
            boosts().addGlobal(BoostType.DROPS, 3.0, 60_000, "test");
        }
        assertEquals(3.0, boosts().multiplier(player, BoostType.DROPS),
                "five 3x boosts are still one 3x boost");
        assertEquals(1, boosts().globalBoosts().size(), "and only one is running");
    }

    @Test
    void aSecondPersonalBoostReplacesTheFirst() {
        PlayerMock player = server.addPlayer();
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 3.0, 60_000, "test");

        assertEquals(3.0, boosts().multiplier(player, BoostType.DROPS),
                "the newer one wins outright");
    }

    @Test
    void replacingOneTypeLeavesTheOtherAlone() {
        // Collections and drops are separate boosts, so starting a drops boost
        // must not quietly end a collections one someone is part way through.
        PlayerMock player = server.addPlayer();
        boosts().addPlayer(player, BoostType.COLLECTIONS, 2.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 2.0, 60_000, "test");
        boosts().addPlayer(player, BoostType.DROPS, 4.0, 60_000, "test");

        assertEquals(4.0, boosts().multiplier(player, BoostType.DROPS));
        assertEquals(2.0, boosts().multiplier(player, BoostType.COLLECTIONS),
                "the collections boost was never in the argument");
    }

    // ------------------------------------------------------------------
    // Expiry
    // ------------------------------------------------------------------

    @Test
    void anExpiredBoostDoesNothing() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        profile.addBoost(new Boost(BoostType.DROPS, 5.0,
                System.currentTimeMillis() - 1, "test"));

        assertEquals(1.0, boosts().multiplier(player, BoostType.DROPS),
                "a boost that ran out is over, not merely hidden");
    }

    @Test
    void expiryIsWallClockSoItSurvivesARelog() {
        // The reason expiry is a timestamp rather than a countdown: nothing has
        // to be running for the clock to keep running.
        long inTenMinutes = System.currentTimeMillis() + 600_000;
        Boost boost = new Boost(BoostType.COLLECTIONS, 2.0, inTenMinutes, "test");

        assertTrue(boost.isActive(System.currentTimeMillis()));
        assertFalse(boost.isActive(inTenMinutes + 1));
        assertEquals(0, boost.remainingMillis(inTenMinutes + 1));
    }

    @Test
    void pruningDropsOnlyTheFinishedOnes() {
        PlayerMock player = server.addPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        profile.addBoost(new Boost(BoostType.DROPS, 2.0, now - 1, "done"));
        profile.addBoost(new Boost(BoostType.DROPS, 2.0, now + 600_000, "running"));

        assertTrue(profile.pruneBoosts(now));
        assertEquals(1, profile.getBoosts().size());
        assertEquals("running", profile.getBoosts().get(0).source());
        assertFalse(profile.pruneBoosts(now), "nothing left to prune");
    }

    @Test
    void clearingEndsThemEarly() {
        PlayerMock player = server.addPlayer();
        boosts().addGlobal(BoostType.DROPS, 2.0, 600_000, "test");
        boosts().addPlayer(player, BoostType.COLLECTIONS, 2.0, 600_000, "test");

        assertEquals(1, boosts().clearGlobal());
        assertEquals(1, boosts().clearPlayer(player.getUniqueId()));
        assertEquals(1.0, boosts().multiplier(player, BoostType.DROPS));
        assertEquals(1.0, boosts().multiplier(player, BoostType.COLLECTIONS));
    }

    // ------------------------------------------------------------------
    // Scaling
    // ------------------------------------------------------------------

    @Test
    void wholeMultipliersAreExact() {
        assertEquals(80, Boost.scale(40, 2.0, 0.99));
        assertEquals(120, Boost.scale(40, 3.0, 0.0));
        assertEquals(40, Boost.scale(40, 1.0, 0.99), "1x is a no-op");
        assertEquals(0, Boost.scale(0, 4.0, 0.0));
    }

    @Test
    void aFractionalMultiplierPaysTheRemainderAsAChance() {
        // 1.5x of a single diamond has to be 1 or 2 — flooring would make every
        // fractional boost do nothing at all for single-item drops.
        assertEquals(2, Boost.scale(1, 1.5, 0.4), "the roll lands inside the half");
        assertEquals(1, Boost.scale(1, 1.5, 0.6), "and outside it");
        assertEquals(4, Boost.scale(3, 1.5, 0.9), "3 x 1.5 is exactly 4.5");
        assertEquals(5, Boost.scale(3, 1.5, 0.1));
    }

    // ------------------------------------------------------------------
    // Boost items
    // ------------------------------------------------------------------

    @Test
    void aBoostItemCarriesWhatItGrants() {
        BoostItems items = boosts().items();
        BoostItems.Payload payload = new BoostItems.Payload(
                "test-2x", List.of(BoostType.DROPS, BoostType.COLLECTIONS), 2.0, 1_800_000);
        ItemStack item = items.create(payload, BoostItems.Appearance.defaults(), 1);
        assertNotNull(item);

        BoostItems.Payload read = items.read(item);
        assertNotNull(read, "the item must describe itself");
        assertEquals(2.0, read.multiplier());
        assertEquals(1_800_000, read.durationMillis());
        assertEquals(List.of(BoostType.DROPS, BoostType.COLLECTIONS), read.types());
        assertEquals("test-2x", read.id());
    }

    @Test
    void anOrdinaryItemIsNotABoost() {
        assertNull(boosts().items().read(new ItemStack(Material.EXPERIENCE_BOTTLE, 1)));
        assertNull(boosts().items().read(null));
        assertFalse(boosts().items().isBoostItem(new ItemStack(Material.DIAMOND, 1)));
    }

    @Test
    void theConfiguredItemsExistAndActivate() {
        ItemStack item = boosts().createItem("drops-2x", 1);
        assertNotNull(item, "config ships a drops-2x boost item");
        BoostItems.Payload payload = boosts().items().read(item);
        assertNotNull(payload);

        PlayerMock player = server.addPlayer();
        boosts().activate(player, payload);
        assertEquals(payload.multiplier(), boosts().multiplier(player, BoostType.DROPS));
        assertNull(boosts().createItem("no-such-item", 1));
    }

    @Test
    void anItemCanOverrideDurationAndMultiplier() {
        // A staff member handing out a one-off 5x-for-a-day version of the
        // configured 2x-for-30-minutes item, without needing a second entry
        // in config to support it.
        ItemStack item = boosts().createItem("drops-2x", 1, 86_400_000L, 5.0);
        BoostItems.Payload payload = boosts().items().read(item);

        assertEquals(5.0, payload.multiplier(), "the override wins over config");
        assertEquals(86_400_000L, payload.durationMillis());
        assertEquals(BoostType.DROPS, payload.types().get(0), "everything else is unchanged");
    }

    @Test
    void anOverrideOfZeroKeepsTheConfiguredFigure() {
        ItemStack configured = boosts().createItem("drops-2x", 1);
        ItemStack overridden = boosts().createItem("drops-2x", 1, 0L, 0.0);

        BoostItems.Payload before = boosts().items().read(configured);
        BoostItems.Payload after = boosts().items().read(overridden);
        assertEquals(before.multiplier(), after.multiplier());
        assertEquals(before.durationMillis(), after.durationMillis());
    }

    @Test
    void theNameOnAnOverriddenItemMatchesWhatItActuallyDoes() {
        // The bug this is guarding: config used to spell "(30m)" into the item
        // name as literal text, which stayed on screen unchanged even when the
        // duration underneath it was overridden — so the two disagreed.
        ItemStack item = boosts().createItem("drops-2x", 1, 3_600_000L, 3.0);
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        assertTrue(name.contains("3x"), "the name shows the actual multiplier: " + name);
        assertTrue(name.contains(Durations.format(3_600_000L)),
                "the name shows the actual duration: " + name);
        assertFalse(name.contains("30m") || name.contains("2x"),
                "no trace of the configured figures is left behind: " + name);
    }

    // ------------------------------------------------------------------
    // What a boost is allowed to multiply
    // ------------------------------------------------------------------

    @Test
    void aShulkerBoxIsNeverMultiplied() {
        // The exploit: a shulker box drops as one item carrying everything
        // inside it, so doubling the item duplicates twenty-seven stacks.
        // Refused even by a guard that has been told to allow unstackables,
        // because this rule is not the unstackable rule.
        DropGuard permissive = new DropGuard(List.of(), true);
        assertFalse(permissive.allows(new ItemStack(Material.SHULKER_BOX)));
        assertFalse(permissive.allows(new ItemStack(Material.RED_SHULKER_BOX)));
        assertTrue(DropGuard.carriesContents(new ItemStack(Material.CYAN_SHULKER_BOX)));
    }

    @Test
    void plainDropsAreStillMultiplied() {
        DropGuard guard = DropGuard.defaults();
        assertTrue(guard.allows(new ItemStack(Material.DIAMOND)));
        assertTrue(guard.allows(new ItemStack(Material.COBBLESTONE, 12)));
        assertFalse(guard.allows(new ItemStack(Material.AIR)));
        assertFalse(guard.allows(null));
    }

    @Test
    void unstackableItemsAreLeftAloneUnlessAskedFor() {
        assertFalse(DropGuard.defaults().allows(new ItemStack(Material.DIAMOND_PICKAXE)),
                "an item with no quantity to multiply is not doubled by default");
        assertTrue(new DropGuard(List.of(), true).allows(new ItemStack(Material.DIAMOND_PICKAXE)),
                "unless the server says its custom drops need it");
    }

    @Test
    void theNeverMultiplyListIsHonoured() {
        DropGuard guard = new DropGuard(List.of(Material.DIAMOND), false);
        assertFalse(guard.allows(new ItemStack(Material.DIAMOND)));
        assertTrue(guard.allows(new ItemStack(Material.EMERALD)));
    }

    @Test
    void theModuleShipsWithTheSafeDefaults() {
        DropGuard guard = boosts().guard();
        assertFalse(guard.allows(new ItemStack(Material.SHULKER_BOX)),
                "shipped config must not leave the dupe open");
        assertTrue(guard.allows(new ItemStack(Material.DIAMOND)));
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    void durationsParseTheWayPeopleWriteThem() {
        assertEquals(90_000, Durations.parse("90s"));
        assertEquals(1_800_000, Durations.parse("30m"));
        assertEquals(9_000_000, Durations.parse("2h30m"));
        assertEquals(86_400_000, Durations.parse("1d"));
        assertEquals(1_800_000, Durations.parse("30"), "a bare number is minutes");
        assertEquals(-1, Durations.parse("soon"));
        assertEquals(-1, Durations.parse("30x"));
        assertEquals(-1, Durations.parse(""));
    }

    @Test
    void durationsFormatToTwoUnits() {
        assertEquals("30m", Durations.format(1_800_000));
        assertEquals("2h 30m", Durations.format(9_000_000));
        assertEquals("8s", Durations.format(8_000));
        assertEquals("1d 2h", Durations.format(93_600_000));
        assertEquals("0s", Durations.format(0));
    }

    @Test
    void typeNamesResolve() {
        assertEquals(BoostType.DROPS, BoostType.parse("drops"));
        assertEquals(BoostType.COLLECTIONS, BoostType.parse("COLLECTIONS"));
        assertNull(BoostType.parse("xp"));
        assertTrue(BoostType.isAll("all"));
        assertEquals(2, BoostsModule.typesFor("all").size());
        assertEquals(1, BoostsModule.typesFor("drops").size());
        assertEquals(0, BoostsModule.typesFor("nonsense").size());
    }
}
