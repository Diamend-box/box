package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for material groups — the "#LOGS" style targets that let an
 * objective cover a whole family of blocks ("mine 100 logs") instead of one
 * specific material. Membership is pure string logic, so these run without a
 * server; a couple of cases resolve against the real {@link Material} list to
 * catch rules that drift away from Minecraft's naming.
 */
class MaterialGroupTest {

    @Test
    void logsCoverEveryWoodType() {
        assertTrue(MaterialGroup.LOGS.matches("OAK_LOG"), "plain log");
        assertTrue(MaterialGroup.LOGS.matches("STRIPPED_BIRCH_LOG"), "stripped log");
        assertTrue(MaterialGroup.LOGS.matches("ACACIA_WOOD"), "wood block");
        assertTrue(MaterialGroup.LOGS.matches("CRIMSON_STEM"), "nether stem");
        assertTrue(MaterialGroup.LOGS.matches("STRIPPED_WARPED_HYPHAE"), "nether hyphae");
        assertTrue(MaterialGroup.LOGS.matches("BAMBOO_BLOCK"), "bamboo counts as a log");
    }

    @Test
    void logsExcludeCropAndMushroomStems() {
        // These all end in _STEM but are nothing like logs.
        assertFalse(MaterialGroup.LOGS.matches("MELON_STEM"));
        assertFalse(MaterialGroup.LOGS.matches("ATTACHED_PUMPKIN_STEM"));
        assertFalse(MaterialGroup.LOGS.matches("MUSHROOM_STEM"));
    }

    @Test
    void oresCoverDeepslateNetherAndAncientDebris() {
        assertTrue(MaterialGroup.ORES.matches("COAL_ORE"));
        assertTrue(MaterialGroup.ORES.matches("DEEPSLATE_DIAMOND_ORE"));
        assertTrue(MaterialGroup.ORES.matches("NETHER_GOLD_ORE"));
        assertTrue(MaterialGroup.ORES.matches("ANCIENT_DEBRIS"), "ancient debris is the nether's ore");
        assertFalse(MaterialGroup.ORES.matches("RAW_IRON_BLOCK"), "a storage block is not an ore");
        assertFalse(MaterialGroup.ORES.matches("IRON_BLOCK"));
    }

    @Test
    void legacyAliasesNeverMatch() {
        // Pre-flattening constants (LEGACY_LOG, LEGACY_WOOD) still exist in the
        // Material enum and would otherwise slip through the suffix rules.
        assertFalse(MaterialGroup.LOGS.matches("LEGACY_LOG"));
        assertFalse(MaterialGroup.LOGS.matches("LEGACY_WOOD"));
    }

    @Test
    void glassRulesDoNotSwallowGlassItems() {
        assertTrue(MaterialGroup.GLASS.matches("GLASS"));
        assertTrue(MaterialGroup.GLASS.matches("WHITE_STAINED_GLASS_PANE"));
        assertTrue(MaterialGroup.GLASS.matches("TINTED_GLASS"));
        assertFalse(MaterialGroup.GLASS.matches("GLASS_BOTTLE"), "a bottle is not a glass block");
    }

    @Test
    void byIdAcceptsTheSpellingsPeopleActuallyWrite() {
        assertEquals(MaterialGroup.LOGS, MaterialGroup.byId("#LOGS"));
        assertEquals(MaterialGroup.LOGS, MaterialGroup.byId("LOGS"));
        assertEquals(MaterialGroup.LOGS, MaterialGroup.byId("logs"));
        assertEquals(MaterialGroup.LOGS, MaterialGroup.byId("#minecraft:logs"));
        assertEquals(MaterialGroup.LOGS, MaterialGroup.byId("#LOG"), "singular is forgiven");
        assertNull(MaterialGroup.byId("#NOT_A_GROUP"));
    }

    @Test
    void membersResolveAgainstTheServersMaterialList() {
        assertTrue(MaterialGroup.LOGS.members().contains(Material.OAK_LOG));
        assertTrue(MaterialGroup.LOGS.members().size() > 10,
                "every wood type should contribute several log materials");
        assertTrue(MaterialGroup.ORES.members().contains(Material.ANCIENT_DEBRIS));
        assertFalse(MaterialGroup.ORES.members().contains(Material.IRON_BLOCK));
    }

    @Test
    void requirementMatchesTheWholeFamily() {
        Requirement requirement = new Requirement(TriggerType.BLOCK_BREAK, "#LOGS", 100);
        assertTrue(requirement.matchesTarget("OAK_LOG"));
        assertTrue(requirement.matchesTarget("SPRUCE_LOG"));
        assertFalse(requirement.matchesTarget("STONE"));
    }

    @Test
    void singleMaterialTargetsAreUnaffected() {
        Requirement requirement = new Requirement(TriggerType.BLOCK_BREAK, "OAK_LOG", 10);
        assertTrue(requirement.matchesTarget("OAK_LOG"));
        assertFalse(requirement.matchesTarget("SPRUCE_LOG"), "an exact target stays exact");
    }

    @Test
    void unknownGroupMatchesNothingRatherThanEverything() {
        Requirement requirement = new Requirement(TriggerType.BLOCK_BREAK, "#NOT_A_GROUP", 1);
        assertFalse(requirement.matchesTarget("OAK_LOG"));
        assertFalse(requirement.matchesTarget("STONE"));
    }

    @Test
    void itemObjectivesCanTargetAGroup() {
        Requirement requirement = new Requirement(TriggerType.ITEM_CRAFT, "#PLANKS", 64);
        assertTrue(requirement.matchesItem("OAK_PLANKS", null));
        assertTrue(requirement.matchesItem("WARPED_PLANKS", null));
        assertFalse(requirement.matchesItem("STICK", null));
    }

    @Test
    void matchingByCustomNameIgnoresGroups() {
        // "Match by: Custom Name" compares the item's display name, so a target
        // that looks like a group is just literal text there.
        Requirement requirement = new Requirement(TriggerType.ITEM_OBTAIN, "#LOGS", 1);
        requirement.setMatchByName(true);
        assertFalse(requirement.matchesItem("OAK_LOG", "Fancy Sword"));
        assertTrue(requirement.matchesItem("OAK_LOG", "#LOGS"));
    }

    @Test
    void describeReadsNaturally() {
        Requirement requirement = new Requirement(TriggerType.BLOCK_BREAK, "#LOGS", 100);
        assertEquals("Break Blocks: Any Logs x100", requirement.describe());
    }
}
