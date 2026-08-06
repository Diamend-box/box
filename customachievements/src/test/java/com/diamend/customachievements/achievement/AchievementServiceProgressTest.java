package com.diamend.customachievements.achievement;

import com.diamend.customachievements.data.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the action-bar target selection. These deliberately avoid
 * MockBukkit: {@link AchievementService#completionFraction} depends only on plain
 * {@link Achievement}/{@link PlayerData} objects, so it can be exercised without a
 * live server. This is the ranking the action bar uses to show the achievement
 * <em>closest</em> to completion (highest fraction) rather than the furthest.
 */
class AchievementServiceProgressTest {

    @Test
    void closerAchievementRanksAboveFurther() {
        // A progress-style trigger is required for the amount to count toward the
        // fraction; threshold triggers are always 0-or-1 (see requiredAmount()).
        Achievement near = new Achievement("near_goal");
        near.setTrigger(TriggerType.BLOCK_BREAK);
        near.setAmount(2);   // single requirement, needs 2
        Achievement far = new Achievement("far_goal");
        far.setTrigger(TriggerType.BLOCK_BREAK);
        far.setAmount(100);  // single requirement, needs 100

        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setProgress(PlayerData.requirementKey("near_goal", 0), 1); // 1/2  = 0.50
        data.setProgress(PlayerData.requirementKey("far_goal", 0), 1);  // 1/100 = 0.01

        assertTrue(AchievementService.completionFraction(near, data)
                        > AchievementService.completionFraction(far, data),
                "the achievement with more relative progress should rank as closer");
    }

    @Test
    void singleRequirementFractionIsCappedProgressRatio() {
        Achievement achievement = new Achievement("half_way");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setAmount(4);
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setProgress(PlayerData.requirementKey("half_way", 0), 2); // 2/4

        assertEquals(0.5, AchievementService.completionFraction(achievement, data), 1e-9);
    }

    @Test
    void overshootIsCappedAtOnePerRequirement() {
        Achievement achievement = new Achievement("capped");
        achievement.setTrigger(TriggerType.BLOCK_BREAK);
        achievement.setAmount(3);
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setProgress(PlayerData.requirementKey("capped", 0), 999); // capped to 3/3

        assertEquals(1.0, AchievementService.completionFraction(achievement, data), 1e-9);
    }

    @Test
    void multiRequirementAveragesEachObjective() {
        Achievement achievement = new Achievement("multi");
        achievement.getRequirements().clear();
        achievement.getRequirements().add(new Requirement(TriggerType.BLOCK_BREAK, "STONE", 4));
        achievement.getRequirements().add(new Requirement(TriggerType.ENTITY_KILL, "ZOMBIE", 2));

        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setProgress(PlayerData.requirementKey("multi", 0), 4); // 4/4 = 1.0
        data.setProgress(PlayerData.requirementKey("multi", 1), 1); // 1/2 = 0.5

        // Averaged across the two objectives: (1.0 + 0.5) / 2 = 0.75.
        assertEquals(0.75, AchievementService.completionFraction(achievement, data), 1e-9);
    }
}
