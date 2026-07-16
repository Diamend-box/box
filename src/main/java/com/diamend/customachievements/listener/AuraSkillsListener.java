package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import dev.aurelium.auraskills.api.event.skill.SkillLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Optional AuraSkills integration. Advances {@link TriggerType#AURASKILLS_LEVEL}
 * objectives when a player levels up a skill. This class references AuraSkills
 * API types, so it is only instantiated when AuraSkills is installed (see
 * {@code CustomAchievementsPlugin#registerListeners}); the plugin loads fine
 * without it.
 */
public class AuraSkillsListener implements Listener {

    private final AchievementService service;

    public AuraSkillsListener(AchievementService service) {
        this.service = service;
    }

    @EventHandler
    public void onSkillLevelUp(SkillLevelUpEvent event) {
        String skill = event.getSkill() != null ? event.getSkill().name() : "ANY";
        service.handleGauge(event.getPlayer(), TriggerType.AURASKILLS_LEVEL, skill, event.getLevel());
    }
}
