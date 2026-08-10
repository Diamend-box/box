package com.diamend.customachievements.achievement;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for mob groups — the "#HOSTILE" style targets that let a kill
 * objective cover a whole family of mobs instead of one entity type.
 *
 * <p>Several groups take their membership from Bukkit's own category interfaces
 * ({@code Enemy}, {@code Animals}, {@code Raider}, ...), so these assertions
 * double as a check that the real entity hierarchy still says what we expect.
 */
class EntityGroupTest {

    @Test
    void hostileCoversEveryKindOfEnemy() {
        assertTrue(EntityGroup.HOSTILE.matches("ZOMBIE"));
        assertTrue(EntityGroup.HOSTILE.matches("CREEPER"));
        assertTrue(EntityGroup.HOSTILE.matches("SLIME"), "slimes are hostile without being Monsters");
        assertTrue(EntityGroup.HOSTILE.matches("GHAST"), "flying hostiles count too");
        assertTrue(EntityGroup.HOSTILE.matches("ENDER_DRAGON"), "a boss is still hostile");
        assertFalse(EntityGroup.HOSTILE.matches("COW"));
        assertFalse(EntityGroup.HOSTILE.matches("VILLAGER"));
    }

    @Test
    void animalsCoverFarmAndWildCreatures() {
        assertTrue(EntityGroup.ANIMALS.matches("COW"));
        assertTrue(EntityGroup.ANIMALS.matches("SHEEP"));
        assertTrue(EntityGroup.ANIMALS.matches("WOLF"));
        assertTrue(EntityGroup.ANIMALS.matches("BEE"));
        assertFalse(EntityGroup.ANIMALS.matches("ZOMBIE"));
    }

    @Test
    void undeadIsTheSmiteList() {
        assertTrue(EntityGroup.UNDEAD.matches("ZOMBIE"));
        assertTrue(EntityGroup.UNDEAD.matches("WITHER_SKELETON"));
        assertTrue(EntityGroup.UNDEAD.matches("DROWNED"));
        assertTrue(EntityGroup.UNDEAD.matches("PHANTOM"));
        assertFalse(EntityGroup.UNDEAD.matches("CREEPER"), "creepers are hostile but not undead");
        assertFalse(EntityGroup.UNDEAD.matches("COW"));
    }

    @Test
    void arthropodsAreTheBaneOfArthropodsCrowd() {
        assertTrue(EntityGroup.ARTHROPODS.matches("SPIDER"));
        assertTrue(EntityGroup.ARTHROPODS.matches("CAVE_SPIDER"));
        assertTrue(EntityGroup.ARTHROPODS.matches("SILVERFISH"));
        assertTrue(EntityGroup.ARTHROPODS.matches("ENDERMITE"));
        assertTrue(EntityGroup.ARTHROPODS.matches("BEE"));
        assertFalse(EntityGroup.ARTHROPODS.matches("ZOMBIE"));
    }

    @Test
    void illagersCoverTheRaidRoster() {
        assertTrue(EntityGroup.ILLAGERS.matches("PILLAGER"));
        assertTrue(EntityGroup.ILLAGERS.matches("VINDICATOR"));
        assertTrue(EntityGroup.ILLAGERS.matches("EVOKER"));
        assertTrue(EntityGroup.ILLAGERS.matches("RAVAGER"));
        assertTrue(EntityGroup.ILLAGERS.matches("WITCH"));
        assertTrue(EntityGroup.ILLAGERS.matches("VEX"), "vexes ride along with evokers");
        assertFalse(EntityGroup.ILLAGERS.matches("VILLAGER"));
    }

    @Test
    void bossesCoverTheBigFourFights() {
        assertTrue(EntityGroup.BOSSES.matches("ENDER_DRAGON"));
        assertTrue(EntityGroup.BOSSES.matches("WITHER"));
        assertTrue(EntityGroup.BOSSES.matches("WARDEN"));
        assertTrue(EntityGroup.BOSSES.matches("ELDER_GUARDIAN"));
        assertFalse(EntityGroup.BOSSES.matches("ZOMBIE"));
    }

    @Test
    void aquaticCoversSwimmersAndTheirEnemies() {
        assertTrue(EntityGroup.AQUATIC.matches("SQUID"));
        assertTrue(EntityGroup.AQUATIC.matches("DOLPHIN"));
        assertTrue(EntityGroup.AQUATIC.matches("COD"));
        assertTrue(EntityGroup.AQUATIC.matches("GUARDIAN"));
        assertTrue(EntityGroup.AQUATIC.matches("DROWNED"));
        assertFalse(EntityGroup.AQUATIC.matches("COW"));
    }

    @Test
    void netherAndEndListTheirNatives() {
        assertTrue(EntityGroup.NETHER.matches("BLAZE"));
        assertTrue(EntityGroup.NETHER.matches("PIGLIN_BRUTE"));
        assertTrue(EntityGroup.NETHER.matches("STRIDER"));
        assertFalse(EntityGroup.NETHER.matches("COW"));

        assertTrue(EntityGroup.END.matches("ENDERMAN"));
        assertTrue(EntityGroup.END.matches("SHULKER"));
        assertTrue(EntityGroup.END.matches("ENDER_DRAGON"));
        assertFalse(EntityGroup.END.matches("BLAZE"));
    }

    @Test
    void byIdAcceptsTheSpellingsPeopleActuallyWrite() {
        assertEquals(EntityGroup.HOSTILE, EntityGroup.byId("#HOSTILE"));
        assertEquals(EntityGroup.HOSTILE, EntityGroup.byId("hostile"));
        assertEquals(EntityGroup.UNDEAD, EntityGroup.byId("#minecraft:undead"));
        assertEquals(EntityGroup.BOSSES, EntityGroup.byId("#BOSS"), "irregular plural is forgiven");
        assertNull(EntityGroup.byId("#NOT_A_GROUP"));
    }

    @Test
    void membersResolveAgainstTheServersEntityList() {
        assertTrue(EntityGroup.HOSTILE.members().contains(EntityType.ZOMBIE));
        assertTrue(EntityGroup.HOSTILE.members().size() > 10, "there are plenty of hostile mobs");
        assertTrue(EntityGroup.ANIMALS.members().contains(EntityType.COW));
        assertFalse(EntityGroup.ANIMALS.members().contains(EntityType.ZOMBIE));
    }

    @Test
    void groupsOnlyResolveForTheirOwnTriggers() {
        // A mob family means nothing to a block trigger, and vice versa.
        assertEquals(EntityGroup.HOSTILE, TargetGroups.resolve(TriggerType.ENTITY_KILL, "#HOSTILE"));
        assertNull(TargetGroups.resolve(TriggerType.BLOCK_BREAK, "#HOSTILE"));
        assertEquals(MaterialGroup.LOGS, TargetGroups.resolve(TriggerType.BLOCK_BREAK, "#LOGS"));
        assertNull(TargetGroups.resolve(TriggerType.ENTITY_KILL, "#LOGS"));
        assertNull(TargetGroups.resolve(TriggerType.MYTHIC_MOB_KILL, "#HOSTILE"),
                "MythicMobs objectives match internal names, not entity families");
    }

    @Test
    void requirementMatchesTheWholeFamily() {
        Requirement requirement = new Requirement(TriggerType.ENTITY_KILL, "#UNDEAD", 100);
        assertTrue(requirement.matchesTarget("ZOMBIE"));
        assertTrue(requirement.matchesTarget("WITHER_SKELETON"));
        assertFalse(requirement.matchesTarget("COW"));
        assertEquals("Kill Entities: Any Undead x100", requirement.describe());
    }

    @Test
    void singleEntityTargetsAreUnaffected() {
        Requirement requirement = new Requirement(TriggerType.ENTITY_KILL, "ZOMBIE", 10);
        assertTrue(requirement.matchesTarget("ZOMBIE"));
        assertFalse(requirement.matchesTarget("SKELETON"), "an exact target stays exact");
    }

    @Test
    void changingTheTriggerReResolvesTheGroup() {
        // The editor lets you switch trigger; the cached group must not go stale.
        Requirement requirement = new Requirement(TriggerType.BLOCK_BREAK, "#LOGS", 10);
        assertTrue(requirement.matchesTarget("OAK_LOG"));

        requirement.setTrigger(TriggerType.ENTITY_KILL);
        assertFalse(requirement.matchesTarget("OAK_LOG"),
                "a material family should stop matching once the trigger is a kill");
    }
}
