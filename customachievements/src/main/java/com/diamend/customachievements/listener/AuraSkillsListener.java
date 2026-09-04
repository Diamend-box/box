package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.event.skill.SkillLevelUpEvent;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
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

    /**
     * Syncs a player's current level in every default skill so achievements for
     * levels they already have complete on join (not only on the next level-up).
     */
    public void syncLevels(Player player) {
        SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
        if (user == null) {
            return;
        }
        for (Skills skill : Skills.values()) {
            service.handleGauge(player, TriggerType.AURASKILLS_LEVEL, skill.name(), user.getSkillLevel(skill));
        }
    }
}
