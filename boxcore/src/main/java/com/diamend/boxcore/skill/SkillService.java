package com.diamend.boxcore.skill;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The rules of the skill system: what a node costs, whether a player may take
 * it, and what happens to their points when they do (or when they respec).
 *
 * <p>The decision logic in {@link #check} is deliberately free of Bukkit types
 * so it can be unit-tested directly.
 */
public class SkillService {

    /** Why a node can't be taken right now. */
    public enum Denial {
        ALLOWED,
        MAX_LEVEL,
        MISSING_REQUIREMENT,
        NEEDS_POINTS_IN_TREE,
        NO_PERMISSION,
        CANNOT_AFFORD
    }

    /**
     * The outcome of asking "can this player take this node?".
     *
     * @param reason why not (or {@link Denial#ALLOWED})
     * @param detail human-readable explanation for the GUI/lore
     * @param cost   what the next level would cost
     */
    public record UnlockCheck(Denial reason, String detail, int cost) {

        public boolean allowed() {
            return reason == Denial.ALLOWED;
        }
    }

    private final Plugin plugin;
    private final SkillTreeManager trees;
    private final ProfileManager profiles;
    private final EffectApplier effects;
    private final Messages messages;

    public SkillService(Plugin plugin, SkillTreeManager trees, ProfileManager profiles,
                        EffectApplier effects, Messages messages) {
        this.plugin = plugin;
        this.trees = trees;
        this.profiles = profiles;
        this.effects = effects;
        this.messages = messages;
    }

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    /** Points this player has sunk into one tree. */
    public int pointsSpentIn(PlayerProfile profile, SkillTree tree) {
        int spent = 0;
        for (SkillNode node : tree.nodes()) {
            spent += node.totalCostAt(profile.getNodeLevel(node.key()));
        }
        return spent;
    }

    /**
     * Decides whether {@code profile} may take the next level of {@code node}.
     *
     * @param hasPermission tests a permission string; pass {@code p -> true}
     *                      when permissions aren't relevant (e.g. in tests)
     */
    public UnlockCheck check(PlayerProfile profile, SkillNode node, Predicate<String> hasPermission) {
        int level = profile.getNodeLevel(node.key());
        int cost = node.costForLevel(level);

        if (level >= node.getMaxLevel()) {
            return new UnlockCheck(Denial.MAX_LEVEL, "Already fully unlocked", cost);
        }
        if (!node.getPermission().isEmpty() && !hasPermission.test(node.getPermission())) {
            return new UnlockCheck(Denial.NO_PERMISSION, "You can't unlock this", cost);
        }
        for (SkillNode.Requirement requirement : node.getRequirements()) {
            SkillNode required = trees.getNode(node.getTreeId() + "." + requirement.nodeId());
            if (required == null) {
                continue; // dropped during load; don't hard-lock the node behind a ghost
            }
            if (profile.getNodeLevel(required.key()) < requirement.level()) {
                String name = required.getDisplay()
                        + (requirement.level() > 1 ? " " + Text.roman(requirement.level()) : "");
                return new UnlockCheck(Denial.MISSING_REQUIREMENT, "Requires " + name, cost);
            }
        }
        SkillTree tree = trees.getTree(node.getTreeId());
        if (tree != null && node.getRequiredPointsInTree() > 0) {
            int spent = pointsSpentIn(profile, tree);
            if (spent < node.getRequiredPointsInTree()) {
                return new UnlockCheck(Denial.NEEDS_POINTS_IN_TREE,
                        "Requires " + node.getRequiredPointsInTree() + " points spent in this tree "
                                + "(you have spent " + spent + ")", cost);
            }
        }
        if (profile.getAvailablePoints() < cost) {
            return new UnlockCheck(Denial.CANNOT_AFFORD,
                    "Costs " + cost + " point" + Messages.plural(cost), cost);
        }
        return new UnlockCheck(Denial.ALLOWED, "Click to unlock", cost);
    }

    /**
     * Recomputes a profile's spent-point total from the nodes it actually owns.
     *
     * <p>This is what makes {@code trees.yml} safe to edit on a live server:
     * nodes that were deleted are dropped and their points handed back, levels
     * above a lowered {@code max-level} are trimmed, and a changed cost is
     * re-charged at the new price. Returns true when anything changed.
     */
    public boolean reconcile(PlayerProfile profile) {
        int spent = 0;
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : Map.copyOf(profile.getNodes()).entrySet()) {
            SkillNode node = trees.getNode(entry.getKey());
            if (node == null) {
                profile.setNodeLevel(entry.getKey(), 0);
                changed = true;
                continue;
            }
            int level = Math.min(entry.getValue(), node.getMaxLevel());
            if (level != entry.getValue()) {
                profile.setNodeLevel(node.key(), level);
                changed = true;
            }
            spent += node.totalCostAt(level);
        }
        if (spent != profile.getPointsSpent()) {
            profile.setPointsSpent(spent);
            changed = true;
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Attempts to take the next level of a node for a player, with feedback. */
    public UnlockCheck unlock(Player player, SkillNode node) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        UnlockCheck check = check(profile, node, player::hasPermission);
        if (!check.allowed()) {
            switch (check.reason()) {
                case MAX_LEVEL -> messages.send(player, "node-maxed");
                case CANNOT_AFFORD -> messages.send(player, "node-cant-afford",
                        "cost", check.cost(), "plural", Messages.plural(check.cost()),
                        "have", profile.getAvailablePoints());
                default -> messages.send(player, "node-locked", "reason", check.detail());
            }
            playSound(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return check;
        }

        int newLevel = profile.getNodeLevel(node.key()) + 1;
        profile.spend(check.cost());
        profile.setNodeLevel(node.key(), newLevel);

        for (String command : node.getEffects().commands()) {
            runCommand(player, command);
        }
        effects.apply(player);

        String name = node.getDisplay() + (node.getMaxLevel() > 1 ? " " + Text.roman(newLevel) : "");
        messages.send(player, "node-unlocked", "node", name);
        if (plugin.getConfig().getBoolean("skills.unlock-feedback", true)) {
            player.sendActionBar(Text.parse("<green>" + Text.plain(name) + "<gray> — <white>"
                    + profile.getAvailablePoints() + "<gray> point"
                    + Messages.plural(profile.getAvailablePoints()) + " left"));
            // A boost countdown ticking every second would wipe this out before
            // it could be read, so ask it to wait its turn.
            if (plugin instanceof BoxCorePlugin box && box.boosts() != null) {
                box.boosts().notifier().hold(player, 3000L);
            }
        }
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        return check;
    }

    /** Force-sets a node level, ignoring cost and prerequisites (admin path). */
    public void setLevel(Player player, SkillNode node, int level) {
        setLevel(profiles.get(player.getUniqueId()), node, level);
        effects.apply(player);
    }

    /** Same as {@link #setLevel} but for an offline profile. */
    public void setLevel(PlayerProfile profile, SkillNode node, int level) {
        profile.setNodeLevel(node.key(), Math.min(Math.max(0, level), node.getMaxLevel()));
        reconcile(profile);
    }

    /** Why a respec did or didn't happen. */
    public enum RespecOutcome {
        DONE,
        DISABLED,
        NOTHING_TO_REFUND,
        MISSING_ITEM
    }

    /**
     * The outcome of a respec attempt.
     *
     * @param outcome  what happened
     * @param refunded points handed back (0 unless {@link RespecOutcome#DONE})
     * @param held     how many of the cost item the player had, for the message
     */
    public record RespecResult(RespecOutcome outcome, int refunded, int held) {

        public boolean succeeded() {
            return outcome == RespecOutcome.DONE;
        }
    }

    /** What a respec currently costs. Re-read each time so /box reload applies. */
    public RespecCost respecCost() {
        return RespecCost.from(plugin.getConfig().getConfigurationSection("skills.respec-item"));
    }

    /**
     * Refunds every node the player owns, consuming the configured respec item.
     *
     * <p>The item is taken only once the respec is certain to go through, so a
     * failed respec can never eat the token.
     */
    public RespecResult respec(Player player) {
        if (!plugin.getConfig().getBoolean("skills.allow-respec", true)) {
            return new RespecResult(RespecOutcome.DISABLED, 0, 0);
        }
        PlayerProfile profile = profiles.get(player.getUniqueId());
        int refunded = profile.getPointsSpent();
        if (refunded <= 0) {
            return new RespecResult(RespecOutcome.NOTHING_TO_REFUND, 0, 0);
        }
        RespecCost cost = respecCost();
        if (!cost.canAfford(player)) {
            return new RespecResult(RespecOutcome.MISSING_ITEM, 0, cost.heldBy(player));
        }
        if (!cost.take(player)) {
            // Lost a race with something else emptying the inventory.
            return new RespecResult(RespecOutcome.MISSING_ITEM, 0, cost.heldBy(player));
        }

        profile.clearNodes();
        profile.setPointsSpent(0);

        for (String command : plugin.getConfig().getStringList("skills.respec-commands")) {
            runCommand(player, command);
        }
        effects.apply(player);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
        return new RespecResult(RespecOutcome.DONE, refunded, 0);
    }

    private void runCommand(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
            // Sound constants move between versions; never let feedback break an unlock.
        }
    }

    public SkillTreeManager trees() {
        return trees;
    }

    public EffectApplier effects() {
        return effects;
    }
}
