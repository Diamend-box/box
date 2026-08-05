package com.diamend.boxtutorial.integration;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.guide.TutorialStep;
import com.diamend.boxtutorial.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PlaceholderAPI expansion, for scoreboards, tab lists and welcome holograms.
 *
 * <pre>
 *   %boxtutorial_step%              the current step's name, coloured
 *   %boxtutorial_step_plain%        the same with the colours stripped
 *   %boxtutorial_step_goal%         what that step is asking for
 *   %boxtutorial_step_number%       which step they're on, e.g. 3
 *   %boxtutorial_total%             how many steps there are
 *   %boxtutorial_completed%         how many they've done
 *   %boxtutorial_remaining%         how many are left
 *   %boxtutorial_percent%           0-100
 *   %boxtutorial_bar%               a ten-segment progress bar
 *   %boxtutorial_active%            true while the tutorial is running
 *   %boxtutorial_finished%          true once they've been through it
 *   %boxtutorial_done_&lt;id&gt;%   true when that step is done
 * </pre>
 */
public class TutorialPlaceholders extends PlaceholderExpansion {

    private final BoxTutorialPlugin plugin;

    public TutorialPlaceholders(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "boxtutorial";
    }

    @Override
    public String getAuthor() {
        return "diamend";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "";
        }
        Progress progress = plugin.store().get(player.getUniqueId());
        TutorialStep current = plugin.service().current(progress);
        String query = params.toLowerCase(Locale.ROOT);

        switch (query) {
            case "step" -> {
                return current == null ? "" : Text.legacy(current.name());
            }
            case "step_plain" -> {
                return current == null ? "" : Text.plain(current.name());
            }
            case "step_goal" -> {
                return current == null ? "" : current.objective();
            }
            case "step_number" -> {
                return String.valueOf(current == null
                        ? plugin.tutorial().stepCount() : plugin.tutorial().number(current));
            }
            case "total" -> {
                return String.valueOf(plugin.tutorial().stepCount());
            }
            case "completed" -> {
                return String.valueOf(plugin.service().completedCount(progress));
            }
            case "remaining" -> {
                return String.valueOf(plugin.tutorial().stepCount()
                        - plugin.service().completedCount(progress));
            }
            case "percent" -> {
                return String.valueOf(plugin.service().percent(progress));
            }
            case "bar" -> {
                return Text.legacy("<green>" + Text.progressBar(
                        plugin.service().percent(progress) / 100.0, 10, "■", "<dark_gray>■"));
            }
            case "active" -> {
                return String.valueOf(plugin.service().isActive(progress));
            }
            case "finished" -> {
                return String.valueOf(progress.finished());
            }
            default -> {
                // fall through to the prefixed lookups
            }
        }

        if (query.startsWith("done_")) {
            String id = query.substring(5);
            return plugin.tutorial().step(id) == null ? "" : String.valueOf(progress.isComplete(id));
        }
        return null; // unknown placeholder: PlaceholderAPI leaves it as-is
    }
}
