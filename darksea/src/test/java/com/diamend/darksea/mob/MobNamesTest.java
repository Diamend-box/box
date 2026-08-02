package com.diamend.darksea.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mob nametags. Without them a server whose MythicMobs pack did not load
 * presents its entire roster as identical anonymous husks, which is exactly
 * what the second live test looked like.
 */
class MobNamesTest {

    @Test
    void theShippedRosterReadsAsEnglish() {
        assertEquals("Crazed Sailor", MobNames.pretty("CrazedSailor"));
        assertEquals("Mutated Naxian", MobNames.pretty("MutatedNaxian"));
        assertEquals("Transmuted Naxian", MobNames.pretty("TransmutedNaxian"));
        assertEquals("Naxian Abomination", MobNames.pretty("NaxianAbomination"));
        assertEquals("Vironic Initiate", MobNames.pretty("VironicInitiate"));
        assertEquals("Vironic Acolyte", MobNames.pretty("VironicAcolyte"));
        assertEquals("Vironic Templar", MobNames.pretty("VironicTemplar"));
        assertEquals("Vironic Lord", MobNames.pretty("VironicLord"));
        assertEquals("Mariphage Core", MobNames.pretty("MariphageCore"));
        assertEquals("Mariphage Vessel", MobNames.pretty("MariphageVessel"));
    }

    @Test
    void aHandWrittenEntryIsTidiedRatherThanMangled() {
        assertEquals("Husk", MobNames.pretty("HUSK"));
        assertEquals("Wither Skeleton", MobNames.pretty("WITHER_SKELETON"));
        assertEquals("Drowned Captain", MobNames.pretty("drowned-captain"));
        assertEquals("Already Spaced", MobNames.pretty("Already Spaced"));
        assertEquals("Sea Wolf", MobNames.pretty("  sea   wolf  "));
    }

    @Test
    void nothingInMeansNothingOut() {
        assertEquals("", MobNames.pretty(null));
        assertEquals("", MobNames.pretty(""));
        assertEquals("", MobNames.pretty("   "));
    }
}
