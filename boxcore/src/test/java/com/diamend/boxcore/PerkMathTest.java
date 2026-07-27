package com.diamend.boxcore;

import com.diamend.boxcore.skill.NodeEffects;
import com.diamend.boxcore.skill.perk.Perk;
import com.diamend.boxcore.util.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perk naming, scaling, stacking and lore rendering — pure, so no server.
 */
class PerkMathTest {

    @Test
    void perkNamesTolerateEveryReasonableSpelling() {
        assertSame(Perk.AUTO_SMELT, Perk.byName("auto_smelt"));
        assertSame(Perk.AUTO_SMELT, Perk.byName("AUTO-SMELT"));
        assertSame(Perk.AUTO_SMELT, Perk.byName("  Auto.Smelt  "));
        assertSame(Perk.AUTO_SMELT, Perk.byName("autosmelt"), "alias");
        assertSame(Perk.LIFESTEAL, Perk.byName("life_steal"), "alias");
        assertNull(Perk.byName("teleportation"), "an unknown perk is reported, not guessed");
        assertNull(Perk.byName(null));
        assertNull(Perk.byName(""));
    }

    @Test
    void thePerksThatDontFitABoxPvpServerAreGone() {
        // Powder snow, spoiled food, anvils, durability, crafting and enchanting
        // are survival-server concerns; none of them decide a box fight.
        for (String retired : new String[] { "no_freeze", "safe_stomach", "anvil_discount",
                "tool_saver", "bonus_craft", "enchant_discount", "self_repair" }) {
            assertNull(Perk.byName(retired), retired + " should no longer resolve");
        }
    }

    @Test
    void everyPerkHasADistinctIdAndADescription() {
        for (Perk perk : Perk.values()) {
            assertSame(perk, Perk.byName(perk.id()), perk + " resolves from its own id");
            String lore = perk.describe(perk.defaultAmount());
            assertFalse(lore.isBlank(), perk + " has lore");
            assertFalse(lore.contains("{}"), perk + " substituted its amount: " + lore);
        }
    }

    @Test
    void amountsScaleWithTheNodeLevelUnlessTheyreToldNotTo() {
        NodeEffects.PerkBonus scaling = new NodeEffects.PerkBonus(Perk.LIFESTEAL, 0.04, true);
        assertEquals(0.04, scaling.amountFor(1), 1.0e-9);
        assertEquals(0.12, scaling.amountFor(3), 1.0e-9);
        // Level 0 means "what would one level give me?" — for the GUI preview.
        assertEquals(0.04, scaling.amountFor(0), 1.0e-9);

        NodeEffects.PerkBonus fixed = new NodeEffects.PerkBonus(Perk.SECOND_CHANCE, 15, false);
        assertEquals(15.0, fixed.amountFor(1), 1.0e-9);
        assertEquals(15.0, fixed.amountFor(4), 1.0e-9, "a cooldown must not grow with levels");
    }

    @Test
    void stackingMatchesWhatEachPerkMeans() {
        assertEquals(0.18, Perk.Stacking.SUM.combine(0.08, 0.10), 1.0e-9);
        assertEquals(1.0, Perk.Stacking.MAX.combine(1.0, 1.0), 1.0e-9);
        // Two sources of Second Chance should mean the shorter wait, not the sum.
        assertEquals(10.0, Perk.Stacking.MIN.combine(15.0, 10.0), 1.0e-9);

        assertSame(Perk.Stacking.SUM, Perk.LIFESTEAL.stacking());
        assertSame(Perk.Stacking.MIN, Perk.SECOND_CHANCE.stacking());
        assertSame(Perk.Stacking.MAX, Perk.AUTO_SMELT.stacking());
    }

    @Test
    void loreReadsTheWayAPlayerExpects() {
        assertEquals("Heal +12% of the melee damage you deal", Perk.LIFESTEAL.describe(0.12));
        assertEquals("25% chance to double a mob's drops", Perk.MOB_LOOT.describe(0.25));
        assertEquals("Speed II for 6s after a kill", Perk.ADRENALINE.describe(6));
        assertEquals("Survive one killing blow every 15 min", Perk.SECOND_CHANCE.describe(15));
        assertEquals("+15% damage to other players", Perk.PLAYER_DAMAGE.describe(0.15));
        assertEquals("Your melee hits poison the target for 3s", Perk.VENOM_STRIKE.describe(3));
        // A flag perk ignores its amount entirely.
        assertEquals(Perk.REPLANT.describe(1), Perk.REPLANT.describe(99));
        assertTrue(Perk.REPLANT.isFlag());
        assertFalse(Perk.LIFESTEAL.isFlag());
    }

    @Test
    void numberFormattingDropsPointlessDecimals() {
        assertEquals("12%", Text.percent(0.12));
        assertEquals("7.5%", Text.percent(0.075));
        assertEquals("6s", Text.seconds(6));
        assertEquals("2.5s", Text.seconds(2.5));
    }
}
