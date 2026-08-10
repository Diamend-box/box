package com.diamend.robobear.integration;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.challenge.Objective;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.data.PlayerData;
import com.diamend.robobear.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI expansion, for scoreboards, holograms and tab lists.
 *
 * <pre>
 *   %robobear_running%            true while the player is in a run
 *   %robobear_state%              choosing, running or shopping
 *   %robobear_round%              the round they're on
 *   %robobear_cogs%               Cogs in hand this run
 *   %robobear_objective%          what this round is asking for
 *   %robobear_progress%           counted so far this round
 *   %robobear_target%             what this round wants
 *   %robobear_percent%            of that, the percent done
 *   %robobear_time%               time left on the round, e.g. 2:30
 *   %robobear_elapsed%            how long the run has been going
 *   %robobear_payouts%            milestone payouts taken this run
 *   %robobear_runs%               runs they've finished, all time
 *   %robobear_best_round%         deepest round they've ever cleared
 *   %robobear_total_rounds%       rounds cleared, all time
 *   %robobear_milestones%         milestone payouts taken, all time
 *   %robobear_deepest_payout%     the name of the deepest one
 *   %robobear_cooldown%           time until they can run again
 *   %robobear_ready%              true when they can start a run
 *   %robobear_mines%              mines RoboBear can see
 *   %robobear_tiers%              milestone tiers set up
 *   %robobear_active%             runs in progress right now
 * </pre>
 *
 * <p>Time placeholders read {@code 0s} rather than an empty string when nothing
 * applies, so a scoreboard line never collapses to a stray label.
 */
public class RoboBearPlaceholders extends PlaceholderExpansion {

    private final RoboBearPlugin plugin;

    public RoboBearPlaceholders(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "robobear";
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
        // The expansion outlives a PlaceholderAPI reload; RoboBear unregisters
        // it itself on disable.
        return true;
    }

    /** Unregisters without letting an API change take the whole disable down. */
    public void unregisterSafely() {
        try {
            unregister();
        } catch (Throwable ignored) {
            // Nothing here is worth failing a shutdown over.
        }
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        // Server-wide values need no player.
        switch (key) {
            case "mines":
                return String.valueOf(plugin.mines().size());
            case "tiers":
                return String.valueOf(plugin.milestones().size());
            case "active":
                return String.valueOf(plugin.service().activeCount());
            default:
                break;
        }

        PlayerData data = plugin.data().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldown = data.cooldownRemaining(
                plugin.getConfig().getLong("run.cooldown-seconds", 0L), now);

        switch (key) {
            case "runs":
                return String.valueOf(data.runs());
            case "best_round":
                return String.valueOf(data.bestRound());
            case "total_rounds":
                return String.valueOf(data.totalRounds());
            case "milestones":
                return String.valueOf(data.totalMilestones());
            case "deepest_payout": {
                MilestoneRewards.Tier tier = plugin.milestones().at(data.deepestMilestone());
                return tier == null ? "" : Text.legacy(tier.name());
            }
            case "cooldown":
                return Text.duration(cooldown);
            case "ready":
                return String.valueOf(cooldown == 0 && !isRunning(player));
            default:
                break;
        }

        // The rest describe a live run, so they need the player online.
        Player online = player.isOnline() ? player.getPlayer() : null;
        RoboRun run = online == null ? null : plugin.service().runOf(online);
        if (run == null) {
            return switch (key) {
                case "running" -> "false";
                case "round", "cogs", "progress", "target", "percent", "payouts" -> "0";
                case "time", "elapsed" -> "0s";
                case "state", "objective" -> "";
                default -> null;
            };
        }

        Objective objective = run.objective();
        return switch (key) {
            case "running" -> "true";
            case "state" -> run.state().name().toLowerCase(Locale.ROOT);
            case "round" -> String.valueOf(run.round());
            case "cogs" -> String.valueOf(run.cogs());
            case "payouts" -> String.valueOf(run.milestonesEarned().size());
            case "objective" -> objective == null ? "" : objective.describe(plugin.mines());
            case "progress" -> String.valueOf(run.progress());
            case "target" -> String.valueOf(objective == null ? 0 : objective.amount());
            case "percent" -> String.valueOf((int) Math.round(run.fraction() * 100.0));
            case "time" -> Text.clock(run.secondsLeft(now));
            case "elapsed" -> Text.duration(run.elapsedSeconds(now));
            default -> null;
        };
    }

    private boolean isRunning(OfflinePlayer player) {
        Player online = player.isOnline() ? player.getPlayer() : null;
        return online != null && plugin.service().isRunning(online);
    }
}
