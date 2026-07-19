package com.diamend.darksea.item;

import com.diamend.darksea.combat.NaxCombatListener;
import com.diamend.darksea.item.DarkSeaItems.WeaponSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The balance promise, held in CI: the Dark Sea is post-End content, so its
 * weapons must be worth sailing for — better than a bare netherite sword's
 * 12.8 DPS at the top end — but never touch enchanted netherite (Sharpness V
 * ~17.6 DPS). Wyatt's per-family calls (fast claws, slow heavy bone, chest
 * gear a notch under mob drops) are all asserted here, so a future tuning
 * pass can't quietly break the design.
 */
class WeaponBalanceTest {

    private static final double BARE_NETHERITE_DPS = 8.0 * 1.6;      // 12.8
    private static final double SHARP_V_NETHERITE_DPS = 11.0 * 1.6;  // 17.6

    private static final List<String> MOB_DROPS = List.of(
            DarkSeaItems.SAILORS_CUTLASS, DarkSeaItems.NAXIAN_CLAW,
            DarkSeaItems.ENHANCED_NAXIAN_CLAW, DarkSeaItems.ABOMINATION_BONE);
    private static final List<String> CHEST_FINDS = List.of(
            DarkSeaItems.NAXIAN_BOARDING_AXE, DarkSeaItems.HARBORGUARD_PIKE,
            DarkSeaItems.ORDER_CEREMONIAL_BLADE);

    private static WeaponSpec spec(String id) {
        return DarkSeaItems.WEAPONS.get(id);
    }

    @Test
    void everyWeaponSitsBetweenUsefulAndOverpowered() {
        for (WeaponSpec spec : DarkSeaItems.WEAPONS.values()) {
            if (spec.trueDamage()) {
                continue; // the Stinger plays a different game entirely
            }
            assertTrue(spec.dps() >= 8.5,
                    spec.id() + " DPS " + spec.dps() + " — not worth picking up");
            assertTrue(spec.dps() < SHARP_V_NETHERITE_DPS - 2.0,
                    spec.id() + " DPS " + spec.dps() + " — crowds enchanted netherite");
        }
    }

    @Test
    void clawsAreFastAndTheirLineageEscalates() {
        WeaponSpec cutlass = spec(DarkSeaItems.SAILORS_CUTLASS);
        WeaponSpec claw = spec(DarkSeaItems.NAXIAN_CLAW);
        WeaponSpec enhanced = spec(DarkSeaItems.ENHANCED_NAXIAN_CLAW);

        // "mid damage but much faster attack speed"
        assertTrue(claw.speed() > cutlass.speed() * 1.3, "claw isn't meaningfully faster");
        assertTrue(claw.damage() < cutlass.damage(), "claw shouldn't out-hit the cutlass");
        // "Enhanced ... with greater damage"
        assertTrue(enhanced.damage() > claw.damage());
        assertTrue(enhanced.dps() > claw.dps());
    }

    @Test
    void abominationBoneIsSlowHeavyAndDpsMatchedToTheEnhancedClaw() {
        WeaponSpec bone = spec(DarkSeaItems.ABOMINATION_BONE);
        WeaponSpec enhanced = spec(DarkSeaItems.ENHANCED_NAXIAN_CLAW);

        assertTrue(bone.damage() > 2 * enhanced.damage(), "bone should hit like a truck");
        assertTrue(bone.speed() < 1.3, "bone should swing slowly");
        // "about equal ... in terms of dps"
        assertEquals(enhanced.dps(), bone.dps(), 0.5);
    }

    @Test
    void chestWeaponsAverageWorseThanMobDrops() {
        double chest = CHEST_FINDS.stream().mapToDouble(id -> spec(id).dps()).average().orElseThrow();
        double mob = MOB_DROPS.stream().mapToDouble(id -> spec(id).dps()).average().orElseThrow();
        assertTrue(chest < mob - 1.0,
                "chest finds (" + chest + " DPS avg) should trail mob drops (" + mob + ")");
        // ...but the ceremonial blade is the promised "usable and good" find.
        assertTrue(spec(DarkSeaItems.ORDER_CEREMONIAL_BLADE).dps() > 11.0);
    }

    @Test
    void theStingerIsTwoHeartsOfTrueDamage() {
        WeaponSpec stinger = spec(DarkSeaItems.MARIPHAGE_STINGER);
        assertTrue(stinger.trueDamage());
        assertEquals(4.0, stinger.damage());
        assertEquals(NaxCombatListener.STINGER_TRUE_DAMAGE, stinger.damage(),
                "listener and spec disagree on the Stinger's hit");
        // "attack speed is a bit slower than a sword" (sword = 1.6)
        assertTrue(stinger.speed() < 1.6 && stinger.speed() >= 1.2);
        // Its raw DPS must stay modest — armor piercing is the whole point.
        assertTrue(stinger.dps() < BARE_NETHERITE_DPS / 2.0 + 1.0);
    }
}
