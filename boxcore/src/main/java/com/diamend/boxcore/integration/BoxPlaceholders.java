package com.diamend.boxcore.integration;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.skill.SkillNode;
import com.diamend.boxcore.skill.SkillTree;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.skill.perk.Perk;
import com.diamend.boxcore.travel.TravelModule;
import com.diamend.boxcore.travel.Warp;
import com.diamend.boxcore.util.Durations;
import com.diamend.boxcore.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
 *   %boxcore_perk_&lt;perk&gt;%           total of one perk (or true/false)
 *   %boxcore_collected%                   items gathered overall
 *   %boxcore_collection_&lt;id&gt;%       amount gathered
 *   %boxcore_tier_&lt;id&gt;%             collection tier reached
 *   %boxcore_progress_&lt;id&gt;%         percent toward the next tier
 *   %boxcore_boost_&lt;type&gt;%          multiplier in effect, e.g. 2.5
 *   %boxcore_boost_&lt;type&gt;_time%     time until it changes, e.g. 12m 30s
 *   %boxcore_boost_global_&lt;type&gt;%   the server-wide part alone
 *   %boxcore_boost_active%                true when anything is boosting
 *   %boxcore_compressor_enabled%          true/false, their own pause toggle
 *   %boxcore_compressor_has%              true when they carry a compactor
 *   %boxcore_compressor_slots%            compactor slots they carry
 *   %boxcore_compressor_used%             of those, how many are filled
 *   %boxcore_compressor_recipes%          recipes this server compacts
 *   %boxcore_travel_found%                destinations they've found
 *   %boxcore_travel_total%                destinations they can see
 *   %boxcore_travel_percent%              of those, the percent found
 *   %boxcore_travel_destinations%         destinations set up at all
 *   %boxcore_travel_found_&lt;id&gt;%     true when they've found that one
 *   %boxcore_travel_warmup%               seconds a trip takes
 *   %boxcore_travel_travelling%           true while a trip is running
 *   %boxcore_travel_combat%               true while combat blocks travel
 *   %boxcore_travel_combat_time%          time until it doesn't, e.g. 12s
 * </pre>
 *
 * <p>The boost placeholders read 1 rather than an empty string when nothing is
 * running, so a scoreboard line like {@code x%boxcore_boost_drops%} always says
 * something true instead of collapsing to {@code x}.
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
                CollectionsModule module = plugin.modules().get(CollectionsModule.class);
                return Text.number(module == null
                        ? profile.getTotalCollected() : module.collections().itemsCollected(profile));
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
        if (query.startsWith("perk_") && skills != null) {
            Perk perk = Perk.byName(query.substring(5));
            if (perk == null) {
                return "";
            }
            double value = skills.perks().of(player.getUniqueId()).value(perk);
            return perk.isFlag() ? String.valueOf(value > 0) : Text.amount(value, false);
        }

        if (query.startsWith("boost_")) {
            return boost(profile, query.substring(6));
        }
        if (query.startsWith("compressor_")) {
            return compressor(player, profile, query.substring(11));
        }
        if (query.startsWith("travel_")) {
            return travel(player, profile, query.substring(7));
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

    /** Everything after {@code boost_}: {@code drops}, {@code global_drops_time}, {@code active}. */
    private String boost(PlayerProfile profile, String query) {
        BoostsModule boosts = plugin.boosts();
        if (boosts == null) {
            return "";
        }
        if (query.equals("active")) {
            for (BoostType type : BoostType.values()) {
                if (boosts.multiplierFor(profile, type) > 1.0) {
                    return "true";
                }
            }
            return "false";
        }

        String rest = query;
        boolean globalOnly = rest.startsWith("global_");
        if (globalOnly) {
            rest = rest.substring(7);
        }
        boolean wantsTime = rest.endsWith("_time");
        if (wantsTime) {
            rest = rest.substring(0, rest.length() - 5);
        }

        BoostType type = BoostType.parse(rest);
        if (type == null) {
            return "";
        }
        PlayerProfile whose = globalOnly ? null : profile;
        return wantsTime
                ? Durations.format(boosts.remainingFor(whose, type))
                : Text.decimal(boosts.multiplierFor(whose, type));
    }

    /**
     * Everything after {@code compressor_}.
     *
     * <p>What a compactor holds is a property of an item in an inventory, so
     * the slot counts can only be answered for someone who is online. They read
     * empty rather than zero for an offline player — zero would be a claim
     * about their compactor, and we don't have it to look at.
     */
    private String compressor(OfflinePlayer player, PlayerProfile profile, String query) {
        CompressorModule module = plugin.compressor();
        if (module == null) {
            return "";
        }
        if (query.equals("enabled")) {
            return String.valueOf(profile.isCompressorEnabled());
        }
        if (query.equals("recipes")) {
            return String.valueOf(module.recipes().size());
        }
        Player online = player.getPlayer();
        if (online == null) {
            return "";
        }
        return switch (query) {
            case "has" -> String.valueOf(module.hasCompactor(online));
            case "slots" -> String.valueOf(slotTotal(module, online, false));
            case "used" -> String.valueOf(slotTotal(module, online, true));
            default -> "";
        };
    }

    /**
     * Everything after {@code travel_}.
     *
     * <p>What someone has found is on their profile, so the counts answer for an
     * offline player too. What they are <em>allowed</em> to see isn't — permission
     * checks need a player who is here — so an offline lookup counts against
     * every destination rather than pretending it knows which ones were theirs.
     */
    private String travel(OfflinePlayer player, PlayerProfile profile, String query) {
        TravelModule module = plugin.travel();
        if (module == null) {
            return "";
        }
        if (query.startsWith("found_")) {
            Warp warp = module.warps().get(query.substring(6));
            return warp == null ? "" : String.valueOf(profile.hasDiscovered(warp.id()));
        }

        Player online = player.getPlayer();
        int total = online == null ? module.warps().size() : module.visibleTo(online).size();
        int found = discovered(module, profile, online);
        return switch (query) {
            case "found" -> String.valueOf(found);
            case "total" -> String.valueOf(total);
            case "destinations" -> String.valueOf(module.warps().size());
            case "percent" -> String.valueOf(total == 0 ? 0 : Math.round(found * 100.0 / total));
            case "warmup" -> String.valueOf(module.travel().warmupSeconds());
            case "travelling" -> String.valueOf(online != null && module.travel().isTravelling(online));
            case "combat" -> String.valueOf(online != null && module.combat().isTagged(online));
            case "combat_time" -> online == null
                    ? "" : Durations.format(module.combat().remainingMillis(online));
            default -> "";
        };
    }

    /** How many destinations they've found, counting only ones that still exist. */
    private int discovered(TravelModule module, PlayerProfile profile, Player online) {
        if (online != null) {
            return module.discoveredCount(online);
        }
        int found = 0;
        for (Warp warp : module.warps().all()) {
            if (profile.hasDiscovered(warp.id())) {
                found++;
            }
        }
        return found;
    }

    /** Slots across every compactor the player carries, filled ones or all. */
    private int slotTotal(CompressorModule module, Player player, boolean filledOnly) {
        int total = 0;
        for (int index : module.compactorSlots(player)) {
            ItemStack compactor = player.getInventory().getItem(index);
            total += filledOnly
                    ? module.compactors().active(compactor).size()
                    : module.compactors().slots(compactor);
        }
        return total;
    }
}
