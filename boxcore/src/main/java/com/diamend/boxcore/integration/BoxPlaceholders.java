package com.diamend.boxcore.integration;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PlaceholderAPI expansion, for scoreboards, holograms and tab lists.
 *
 * <pre>
 *   %boxcore_points%                      points available
 *   %boxcore_points_spent%                points spent
 *   %boxcore_points_earned%               points earned overall
 *   %boxcore_nodes%                       nodes unlocked
 *   %boxcore_node_&lt;tree.node&gt;%      owned level of one node
 *   %boxcore_tree_&lt;tree&gt;%           points spent in one tree
 *   %boxcore_collected%                   items gathered overall
 *   %boxcore_collection_&lt;id&gt;%       amount gathered
 *   %boxcore_tier_&lt;id&gt;%             collection tier reached
 *   %boxcore_progress_&lt;id&gt;%         percent toward the next tier
 * </pre>
 */
public class BoxPlaceholders extends PlaceholderExpansion {

    private final BoxCorePlugin plugin;

    public BoxPlaceholders(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "boxcore";
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
        PlayerProfile profile = plugin.profiles().loadDetached(player.getUniqueId());
        String query = params.toLowerCase(Locale.ROOT);

        switch (query) {
            case "points" -> {
                return String.valueOf(profile.getAvailablePoints());
            }
            case "points_spent" -> {
                return String.valueOf(profile.getPointsSpent());
            }
            case "points_earned" -> {
                return String.valueOf(profile.getPointsEarned());
            }
            case "nodes" -> {
                return String.valueOf(profile.getNodes().size());
            }
            case "collected" -> {
                return Text.number(profile.getTotalCollected());
            }
            default -> {
                // fall through to the prefixed lookups
            }
        }

        SkillsModule skills = plugin.modules().get(SkillsModule.class);
        if (query.startsWith("node_") && skills != null) {
            SkillNode node = skills.trees().getNode(query.substring(5));
            return node == null ? "" : String.valueOf(profile.getNodeLevel(node.key()));
        }
        if (query.startsWith("tree_") && skills != null) {
            SkillTree tree = skills.trees().getTree(query.substring(5));
            return tree == null ? "" : String.valueOf(skills.service().pointsSpentIn(profile, tree));
        }

        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        if (collections == null) {
            return "";
        }
        if (query.startsWith("collection_")) {
            ItemCollection collection = collections.collections().get(query.substring(11));
            return collection == null ? "" : Text.number(profile.getCollected(collection.getId()));
        }
        if (query.startsWith("tier_")) {
            ItemCollection collection = collections.collections().get(query.substring(5));
            return collection == null ? ""
                    : String.valueOf(collection.tierFor(profile.getCollected(collection.getId())));
        }
        if (query.startsWith("progress_")) {
            ItemCollection collection = collections.collections().get(query.substring(9));
            if (collection == null) {
                return "";
            }
            double progress = collection.progress(profile.getCollected(collection.getId()));
            return String.valueOf(Math.round(progress * 100.0));
        }
        return "";
    }
}
