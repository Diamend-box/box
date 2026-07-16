package com.diamend.customachievements.achievement;

import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.data.PlayerDataManager;
import com.diamend.customachievements.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Central place where achievement progress is applied and completions are
 * awarded. Both the event listeners and the admin command funnel through here.
 */
public class AchievementService {

    private final Plugin plugin;
    private final AchievementManager achievements;
    private final PlayerDataManager playerData;

    public AchievementService(Plugin plugin, AchievementManager achievements, PlayerDataManager playerData) {
        this.plugin = plugin;
        this.achievements = achievements;
        this.playerData = playerData;
    }

    /**
     * Applies an event of a given trigger type to a player, advancing every
     * matching, not-yet-completed achievement and awarding any that finish.
     */
    public void handle(Player player, TriggerType type, String targetKey, int amount) {
        handle(player, type, requirement -> requirement.matchesTarget(targetKey), false, amount);
    }

    /**
     * Advances every matching requirement across all not-yet-completed
     * achievements, then awards any achievement whose requirements are now all
     * complete. Requirements are matched by trigger type and the given matcher.
     *
     * @param threshold when true the matched requirement is marked done (its
     *                  progress set to the required amount) rather than having
     *                  {@code amount} added — used by location/dimension/level
     *                  triggers that describe a state, not a running count.
     */
    public void handle(Player player, TriggerType type, Predicate<Requirement> matcher,
                       boolean threshold, int amount) {
        if (!threshold && amount <= 0) {
            return;
        }
        PlayerData data = playerData.get(player.getUniqueId());
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId())) {
                continue;
            }
            List<Requirement> requirements = achievement.getRequirements();
            boolean changed = false;
            for (int i = 0; i < requirements.size(); i++) {
                Requirement requirement = requirements.get(i);
                if (requirement.getTrigger() != type || !matcher.test(requirement)) {
                    continue;
                }
                String key = PlayerData.requirementKey(achievement.getId(), i);
                if (threshold) {
                    data.setProgress(key, requirement.requiredAmount());
                } else {
                    data.addProgress(key, amount);
                }
                changed = true;
            }
            if (changed) {
                if (isComplete(achievement, data)) {
                    award(player, achievement, data);
                } else {
                    sendProgress(player, achievement, data);
                }
            }
        }
    }

    /**
     * Updates gauge-style requirements (playtime, AuraSkills level) to reflect a
     * current value rather than accumulating. Progress is set to the value
     * (capped at the requirement's amount); the achievement is awarded once all
     * requirements are satisfied.
     */
    public void handleGauge(Player player, TriggerType type, String targetKey, int value) {
        if (value < 0) {
            return;
        }
        PlayerData data = playerData.get(player.getUniqueId());
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId())) {
                continue;
            }
            List<Requirement> requirements = achievement.getRequirements();
            boolean changed = false;
            for (int i = 0; i < requirements.size(); i++) {
                Requirement requirement = requirements.get(i);
                if (requirement.getTrigger() != type || !requirement.matchesTarget(targetKey)) {
                    continue;
                }
                int capped = Math.min(value, requirement.requiredAmount());
                String key = PlayerData.requirementKey(achievement.getId(), i);
                if (data.getProgress(key) != capped) {
                    data.setProgress(key, capped);
                    changed = true;
                }
            }
            if (changed) {
                if (isComplete(achievement, data)) {
                    award(player, achievement, data);
                } else {
                    sendProgress(player, achievement, data);
                }
            }
        }
    }

    // Throttle for action-bar progress messages, keyed by uuid#achievementId.
    private final java.util.Map<String, Long> lastProgress = new java.util.concurrent.ConcurrentHashMap<>();

    /** Shows an unobtrusive action-bar progress update (if enabled), throttled per achievement. */
    private void sendProgress(Player player, Achievement achievement, PlayerData data) {
        if (!plugin.getConfig().getBoolean("progress-feedback", true)) {
            return;
        }
        String key = player.getUniqueId() + "#" + achievement.getId();
        long now = System.currentTimeMillis();
        Long last = lastProgress.get(key);
        if (last != null && now - last < 750L) {
            return;
        }
        lastProgress.put(key, now);

        Component name = Text.parse(achievement.getDisplayName());
        List<Requirement> requirements = achievement.getRequirements();
        Component detail;
        if (requirements.size() == 1) {
            Requirement requirement = requirements.get(0);
            int required = requirement.requiredAmount();
            int current = Math.min(data.getProgress(PlayerData.requirementKey(achievement.getId(), 0)), required);
            detail = Text.parse("<gray> — <yellow>" + current + "<gray>/<yellow>" + required);
        } else {
            int done = 0;
            for (int i = 0; i < requirements.size(); i++) {
                if (data.getProgress(PlayerData.requirementKey(achievement.getId(), i))
                        >= requirements.get(i).requiredAmount()) {
                    done++;
                }
            }
            detail = Text.parse("<gray> — <yellow>" + done + "<gray>/<yellow>" + requirements.size() + " objectives");
        }
        player.sendActionBar(name.append(detail));
    }

    /** True when every requirement of the achievement has reached its required amount. */
    private boolean isComplete(Achievement achievement, PlayerData data) {
        List<Requirement> requirements = achievement.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            String key = PlayerData.requirementKey(achievement.getId(), i);
            if (data.getProgress(key) < requirements.get(i).requiredAmount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Advances item-based requirements (craft / consume / obtain), matching by
     * material or, when the objective opts in, by the item's custom name.
     */
    public void handleItem(Player player, TriggerType type, org.bukkit.inventory.ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return;
        }
        String material = item.getType().name();
        String name = itemName(item);
        handle(player, type, requirement -> requirement.matchesItem(material, name), false, amount);
    }

    private String itemName(org.bukkit.inventory.ItemStack item) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        Component displayName = meta.displayName();
        return displayName == null ? null
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(displayName);
    }

    /** Advances REACH_LOCATION requirements whose radius contains the given location. */
    public void handleLocation(Player player, Location location) {
        if (location == null) {
            return;
        }
        handle(player, TriggerType.REACH_LOCATION, requirement -> {
            LocationTarget target = requirement.getLocationTarget();
            return target != null && target.contains(location);
        }, true, 1);
    }

    /**
     * Advances REACH_DIMENSION requirements for the world the player is now in.
     * The target may be the world's name (e.g. {@code world_nether}), its
     * namespaced key (e.g. {@code minecraft:the_nether} or a custom
     * {@code mypack:skylands}), or its environment
     * ({@code NORMAL}/{@code NETHER}/{@code THE_END}/{@code CUSTOM}).
     */
    public void handleDimension(Player player, World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        String key = world.getKey().toString();
        String environment = world.getEnvironment().name();
        handle(player, TriggerType.REACH_DIMENSION, requirement ->
                requirement.matchesTarget(name)
                        || requirement.matchesTarget(key)
                        || requirement.matchesTarget(environment), false, 1);
    }

    /** Directly grants an achievement (manual / command / API). Returns false if already owned. */
    public boolean grant(Player player, Achievement achievement) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data.isCompleted(achievement.getId())) {
            return false;
        }
        award(player, achievement, data);
        return true;
    }

    private void award(Player player, Achievement achievement, PlayerData data) {
        data.setCompleted(achievement.getId());

        FileConfiguration config = plugin.getConfig();
        Component name = Text.parse(achievement.getDisplayName());

        // Personal message.
        String unlocked = config.getString("messages.unlocked", "<green>You unlocked <name>!");
        player.sendMessage(prefix().append(withName(unlocked, name)));

        // Rewards.
        if (achievement.getRewardXp() > 0) {
            player.giveExp(achievement.getRewardXp());
        }
        for (org.bukkit.inventory.ItemStack item : achievement.getRewardItems()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            for (org.bukkit.inventory.ItemStack leftover
                    : player.getInventory().addItem(item.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        for (String command : achievement.getRewardCommands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        // Sound.
        if (config.getBoolean("play-sound", true)) {
            try {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundCategory.MASTER, 1.0f, 1.0f);
            } catch (Throwable ignored) {
                // Fall back to the namespaced key if the enum constant is unavailable.
                player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f);
            }
        }

        // Title.
        if (config.getBoolean("show-title", true)) {
            Component titleText = Text.parse(config.getString("messages.title", "<gold>Achievement Unlocked"));
            player.showTitle(Title.title(titleText, name,
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        }

        // Broadcast.
        if (achievement.isAnnounce() && config.getBoolean("announce-broadcasts", true)) {
            String broadcast = config.getString("messages.broadcast",
                    "<yellow><player></yellow> unlocked <white><name></white>!");
            Component message = prefix().append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(broadcast,
                            Placeholder.component("player", Component.text(player.getName())),
                            Placeholder.component("name", name)));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
            Bukkit.getConsoleSender().sendMessage(message);
        }

        playerData.save(player.getUniqueId());
    }

    private Component withName(String template, Component name) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(template, Placeholder.component("name", name));
    }

    private Component prefix() {
        return Text.parse(plugin.getConfig().getString("messages.prefix", ""));
    }
}
