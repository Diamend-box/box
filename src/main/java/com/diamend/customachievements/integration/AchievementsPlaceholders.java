package com.diamend.customachievements.integration;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import com.diamend.customachievements.achievement.Requirement;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PlaceholderAPI expansion, registered only when PlaceholderAPI is installed.
 *
 * <p>Lets holograms, scoreboards, signs and chat show live per-player
 * achievement info. All placeholders use the {@code customachievements_} prefix:
 *
 * <ul>
 *   <li>{@code %customachievements_completed%} — completed count</li>
 *   <li>{@code %customachievements_total%} — number of achievements</li>
 *   <li>{@code %customachievements_remaining%} — not-yet-unlocked count</li>
 *   <li>{@code %customachievements_percent%} — overall completion (0-100)</li>
 *   <li>{@code %customachievements_status_<id>%} — Unlocked / Locked / ??? (secret)</li>
 *   <li>{@code %customachievements_progress_<id>%} — e.g. {@code 7/10} or {@code Complete}</li>
 *   <li>{@code %customachievements_percent_<id>%} — that achievement's progress (0-100)</li>
 *   <li>{@code %customachievements_name_<id>%} — the display name (coloured; ??? when secret)</li>
 * </ul>
 *
 * <p>Because achievement ids may contain underscores, the sub-key is taken as
 * everything up to the first underscore and the id is the remainder.
 */
public class AchievementsPlaceholders extends PlaceholderExpansion {

    private final CustomAchievementsPlugin plugin;

    public AchievementsPlaceholders(CustomAchievementsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "customachievements";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Stay registered across {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT);

        // Player-independent placeholder.
        if (key.equals("total")) {
            return String.valueOf(plugin.getAchievementManager().count());
        }
        if (player == null) {
            return null;
        }
        PlayerData data = plugin.getPlayerDataManager().loadDetached(player.getUniqueId());

        // Global per-player stats.
        int total = plugin.getAchievementManager().count();
        switch (key) {
            case "completed" -> {
                return String.valueOf(completedCount(data));
            }
            case "remaining" -> {
                return String.valueOf(Math.max(0, total - completedCount(data)));
            }
            case "percent" -> {
                return String.valueOf(total == 0 ? 0 : completedCount(data) * 100 / total);
            }
            default -> {
                // fall through to per-achievement handling
            }
        }

        int split = key.indexOf('_');
        if (split <= 0 || split == key.length() - 1) {
            return null;
        }
        String sub = key.substring(0, split);
        String id = key.substring(split + 1);
        Achievement achievement = plugin.getAchievementManager().get(id);
        if (achievement == null) {
            return null;
        }
        boolean done = data.isCompleted(id);
        boolean secret = achievement.isHidden() && !done;

        return switch (sub) {
            case "status" -> done ? "Unlocked" : (secret ? "???" : "Locked");
            case "name" -> secret ? "§7???" : Text.legacy(achievement.getDisplayName());
            case "progress" -> {
                if (done) {
                    yield "Complete";
                }
                int[] pr = progress(achievement, data, id);
                yield pr[0] + "/" + pr[1];
            }
            case "percent" -> {
                if (done) {
                    yield "100";
                }
                int[] pr = progress(achievement, data, id);
                yield String.valueOf(pr[1] == 0 ? 0 : pr[0] * 100 / pr[1]);
            }
            default -> null;
        };
    }

    /** Number of achievements this player has completed (counts only existing ones). */
    private int completedCount(PlayerData data) {
        int count = 0;
        for (Achievement achievement : plugin.getAchievementManager().all()) {
            if (data.isCompleted(achievement.getId())) {
                count++;
            }
        }
        return count;
    }

    /** Returns {done, needed} progress units summed across an achievement's objectives. */
    private int[] progress(Achievement achievement, PlayerData data, String id) {
        int done = 0;
        int needed = 0;
        java.util.List<Requirement> requirements = achievement.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            int required = requirements.get(i).requiredAmount();
            int have = Math.min(required, data.getProgress(PlayerData.requirementKey(id, i)));
            done += have;
            needed += required;
        }
        return new int[] {done, needed};
    }
}
