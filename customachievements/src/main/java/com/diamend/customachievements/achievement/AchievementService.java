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
import java.util.ArrayList;
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
        Achievement closest = null;
        double closestFraction = -1.0;
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId()) || !isAvailable(achievement, data)) {
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
                    double fraction = completionFraction(achievement, data);
                    if (fraction > closestFraction) {
                        closestFraction = fraction;
                        closest = achievement;
                    }
                }
            }
        }
        if (closest != null) {
            sendProgress(player, closest, data);
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
        handleGauge(player, type, requirement -> requirement.matchesTarget(targetKey) ? value : -1);
    }

    /**
     * Gauge update where each requirement gets its own current value (used by
     * "have X items", where the count depends on what that objective targets).
     * A negative value means the requirement doesn't apply.
     */
    public void handleGauge(Player player, TriggerType type,
                            java.util.function.ToIntFunction<Requirement> valueOf) {
        PlayerData data = playerData.get(player.getUniqueId());
        Achievement closest = null;
        double closestFraction = -1.0;
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId()) || !isAvailable(achievement, data)) {
                continue;
            }
            List<Requirement> requirements = achievement.getRequirements();
            boolean changed = false;
            for (int i = 0; i < requirements.size(); i++) {
                Requirement requirement = requirements.get(i);
                if (requirement.getTrigger() != type) {
                    continue;
                }
                int value = valueOf.applyAsInt(requirement);
                if (value < 0) {
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
                    double fraction = completionFraction(achievement, data);
                    if (fraction > closestFraction) {
                        closestFraction = fraction;
                        closest = achievement;
                    }
                }
            }
        }
        if (closest != null) {
            sendProgress(player, closest, data);
        }
    }

    // Throttle for action-bar progress messages, keyed by uuid#achievementId.
    private final java.util.Map<String, Long> lastProgress = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Drops a player's cached action-bar throttle timestamps. Called when a
     * player disconnects so the throttle map doesn't accumulate stale entries
     * for the lifetime of the server.
     */
    public void forgetPlayer(java.util.UUID uuid) {
        String prefix = uuid + "#";
        lastProgress.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * How close an in-progress achievement is to completion, as a fraction in
     * {@code [0, 1)}. Each requirement contributes its own capped progress ratio
     * and the results are averaged, so a single event that nudges several
     * achievements can pick the one nearest the finish line for the action bar.
     *
     * <p>Package-private and static so it can be unit-tested directly against
     * plain {@link Achievement}/{@link PlayerData} objects without a live server.
     */
    static double completionFraction(Achievement achievement, PlayerData data) {
        List<Requirement> requirements = achievement.getRequirements();
        if (requirements.isEmpty()) {
            return 1.0;
        }
        double total = 0.0;
        for (int i = 0; i < requirements.size(); i++) {
            int required = requirements.get(i).requiredAmount();
            if (required <= 0) {
                total += 1.0;
                continue;
            }
            int current = Math.min(data.getProgress(PlayerData.requirementKey(achievement.getId(), i)), required);
            total += (double) current / required;
        }
        return total / requirements.size();
    }

    /**
     * Shows an unobtrusive action-bar progress update (if enabled), throttled per
     * achievement. When one event advances several achievements at once, callers
     * pass the one closest to completion so the hint points at the nearest goal.
     */
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

    /**
     * Credits an objective with what the player had already done before the
     * achievement existed, read from Minecraft's lifetime statistics: 150 player
     * kills already on the board leave 50 to go on a "kill 200 players"
     * objective rather than 200.
     *
     * <p>Each objective is seeded at most once per player, recorded against the
     * objective's own shape, so this can run as often as it likes without ever
     * double-counting. It is deliberately <em>not</em> keyed on "has no progress
     * yet": a player who scored a single kill between creating the achievement
     * and the first backfill would otherwise be locked out of it permanently.
     *
     * <p>Repeats until nothing further changes, because unlocking one
     * achievement can open a gate on another and make it seedable in turn.
     */
    public void backfill(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        boolean fromStatistics = plugin.getConfig().getBoolean("backfill-from-statistics", true);
        int before;
        do {
            before = data.getCompleted().size();
            if (fromStatistics) {
                seed(player, false, null);
            }
            handleUnlockCount(player);
            awardCompleted(player, data);
        } while (data.getCompleted().size() != before);
    }

    /**
     * Hands over anything already finished but never awarded: an objective
     * seeded while the player was offline, or one whose required amount was
     * lowered below what they'd already done.
     */
    private void awardCompleted(Player player, PlayerData data) {
        for (Achievement achievement : achievements.all()) {
            if (!data.isCompleted(achievement.getId())
                    && isAvailable(achievement, data)
                    && isComplete(achievement, data)) {
                award(player, achievement, data);
            }
        }
    }

    /**
     * Whether the player has unlocked everything this achievement waits on.
     * A locked achievement doesn't advance and isn't seeded, so a tree can't be
     * finished out of order; {@code /ca grant} goes around the gate.
     */
    public static boolean isAvailable(Achievement achievement, PlayerData data) {
        for (String required : achievement.getRequires()) {
            if (required != null && !required.isBlank() && !data.isCompleted(required)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the seeding and reports what it did to every unfinished objective —
     * the statistic it read, and why an objective was or wasn't credited. Used
     * by {@code /ca backfill}, so an admin can see what the plugin actually
     * reads rather than guessing why a total didn't appear.
     *
     * @param redo re-seeds objectives already seeded once, which is the only way
     *             to retry after fixing whatever made the first attempt read zero
     */
    public List<String> seedWithReport(org.bukkit.OfflinePlayer player, boolean redo) {
        List<String> report = new ArrayList<>();
        PlayerData data = playerData.get(player.getUniqueId());
        boolean force = redo;
        int before;
        do {
            before = data.getCompleted().size();
            seed(player, force, report);
            force = false; // forcing is meant to happen once, not on every pass
            if (player instanceof Player online) {
                handleUnlockCount(online);
                awardCompleted(online, data);
            }
        } while (data.getCompleted().size() != before);
        return report;
    }

    private void seed(org.bukkit.OfflinePlayer player, boolean redo, List<String> report) {
        PlayerData data = playerData.get(player.getUniqueId());
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId())) {
                continue;
            }
            if (!isAvailable(achievement, data)) {
                note(report, achievement.getId() + " — locked until "
                        + String.join(", ", achievement.getRequires()) + " is unlocked");
                continue;
            }
            List<Requirement> requirements = achievement.getRequirements();
            boolean changed = false;
            for (int i = 0; i < requirements.size(); i++) {
                Requirement requirement = requirements.get(i);
                String key = PlayerData.requirementKey(achievement.getId(), i);
                // The schema rides along so that a version which can answer more
                // than the last one re-examines this objective once, instead of
                // being shut out by a marker set when the answer wasn't there.
                String signature = key + "@" + requirement.backfillSignature();
                String marker = signature + "@v" + StatisticBackfill.SCHEMA;
                String label = achievement.getId() + " #" + i + " "
                        + requirement.getTrigger().name() + " " + requirement.targetLabel();
                // Counted from the player's own unlocked achievements every time
                // one is awarded, so there is nothing here to seed — and no
                // marker to spend on it.
                if (requirement.getTrigger() == TriggerType.ACHIEVEMENT_UNLOCK) {
                    note(report, label + " — counted live from unlocked achievements");
                    continue;
                }
                // A reset is meant to stick. The marker alone can't say so —
                // it's schema-scoped, and a better reader reconsiders it — so
                // the wipe leaves its own schema-free record behind.
                if (data.isResetSeeded(signature)) {
                    if (!redo) {
                        note(report, label + " — seeded before a reset; add \"redo\" to seed it again");
                        continue;
                    }
                    data.clearResetSeeded(signature);
                }
                if (data.isBackfilled(marker) && !redo) {
                    note(report, label + " — already seeded once; add \"redo\" to force");
                    continue;
                }
                // Marked even when the statistics can't answer it, so an
                // objective is considered once and not re-examined every join.
                data.markBackfilled(marker);
                int total = StatisticBackfill.total(player, requirement);
                if (total < 0) {
                    note(report, label + " — no statistic exists for this objective");
                    continue;
                }
                if (total == 0) {
                    note(report, label + " — statistic reads 0, nothing to credit");
                    continue;
                }
                int seeded = Math.min(total, requirement.requiredAmount());
                if (seeded <= data.getProgress(key)) {
                    note(report, label + " — statistic " + total + ", but progress is already "
                            + data.getProgress(key));
                    continue;
                }
                data.setProgress(key, seeded);
                changed = true;
                note(report, label + " — statistic " + total + ", set to " + seeded + "/"
                        + requirement.requiredAmount());
            }
            if (changed && isComplete(achievement, data)) {
                if (player instanceof Player online) {
                    award(online, achievement, data);
                    note(report, achievement.getId() + " — completed and awarded");
                } else {
                    // Rewards, messages and broadcasts all need them present, so
                    // the unlock waits; awardCompleted() hands it over on join.
                    note(report, achievement.getId() + " — complete, awarded when they next join");
                }
            }
        }
    }

    private static void note(List<String> report, String line) {
        if (report != null) {
            report.add(line);
        }
    }

    /** Backfills every online player — used after achievements are added or reloaded. */
    public void backfillOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backfill(player);
        }
    }

    /**
     * Fires a custom trigger key for a player, advancing every {@code CUSTOM}
     * objective whose key matches. This is the integration point for anything
     * outside the plugin — Skript, other plugins, command blocks, datapacks —
     * so the key is free text the server owner invents, not a Minecraft value.
     */
    public void handleCustom(Player player, String key, int amount) {
        handle(player, TriggerType.CUSTOM, key, amount);
    }

    /**
     * Sets matching {@code CUSTOM} objectives to an absolute value instead of
     * adding to them, for scripts that already track their own running total.
     */
    public void setCustom(Player player, String key, int value) {
        handleGauge(player, TriggerType.CUSTOM, key, value);
    }

    // Players whose unlock count is being recomputed, so awarding a capstone
    // from inside the recount doesn't start a second one underneath it.
    private final java.util.Set<java.util.UUID> recounting =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Refreshes {@code ACHIEVEMENT_UNLOCK} objectives — "unlock 20 achievements"
     * — from the achievements this player has actually completed. Counted rather
     * than accumulated, so it can run as often as it likes: it never
     * double-counts, it follows a revoke back down, and a player who unlocked
     * things before the capstone existed is credited the moment they log in.
     *
     * <p>Awarding a capstone can complete another one, so the count is redone
     * until nothing new finishes. That terminates because every pass either
     * completes an achievement — and there are finitely many — or changes
     * nothing and ends the loop.
     */
    public void handleUnlockCount(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        if (!recounting.add(uuid)) {
            // Re-entered through award(); the pass already running will loop
            // again and see whatever just completed.
            return;
        }
        try {
            PlayerData data = playerData.get(uuid);
            int before;
            do {
                before = data.getCompleted().size();
                handleGauge(player, TriggerType.ACHIEVEMENT_UNLOCK,
                        requirement -> unlockedCount(data, requirement));
            } while (data.getCompleted().size() != before);
        } finally {
            recounting.remove(uuid);
        }
    }

    /**
     * How many achievements this player has unlocked that the requirement asks
     * about: all of them for a target of {@code ANY}, otherwise only those in
     * the category it names.
     */
    private int unlockedCount(PlayerData data, Requirement requirement) {
        int total = 0;
        for (Achievement achievement : achievements.all()) {
            if (data.isCompleted(achievement.getId())
                    && requirement.matchesTarget(achievement.getCategory())) {
                total++;
            }
        }
        return total;
    }

    /**
     * Advances PLAYER_DEATH requirements. A death matches on either the damage
     * cause ({@code FALL}, {@code LAVA}, ...) or what killed the player
     * ({@code CREEPER}, a mob family like {@code #HOSTILE}, ...), so "die to
     * lava" and "die to a creeper" are both expressible. Either may be null.
     */
    public void handleDeath(Player player, String cause, String killer) {
        handle(player, TriggerType.PLAYER_DEATH,
                requirement -> requirement.matchesTarget(cause)
                        || (killer != null && requirement.matchesTarget(killer)),
                false, 1);
    }

    /**
     * Refreshes ITEM_HAVE requirements from what the player is currently
     * carrying. Unlike ITEM_OBTAIN (which counts each item as it's received),
     * this reads the inventory, so it also sees items that arrive without an
     * event — {@code /give}, plugin grants, creative mode.
     */
    public void handleItemInventory(Player player) {
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        handleGauge(player, TriggerType.ITEM_HAVE, requirement -> countMatching(contents, requirement));
    }

    /** How many items in the given contents match a requirement's target. */
    private static int countMatching(org.bukkit.inventory.ItemStack[] contents, Requirement requirement) {
        int total = 0;
        for (org.bukkit.inventory.ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (requirement.matchesItem(item.getType().name(), itemName(item))) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static String itemName(org.bukkit.inventory.ItemStack item) {
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
        String environment = world.getEnvironment().name();
        // The namespaced key is optional metadata: some server implementations
        // (and the MockBukkit test server) don't expose it. Fall back to
        // name/environment matching when it's unavailable rather than letting
        // the whole join handler abort.
        String key;
        try {
            key = world.getKey().toString();
        } catch (RuntimeException ex) {
            key = null;
        }
        final String keyForMatch = key;
        handle(player, TriggerType.REACH_DIMENSION, requirement ->
                requirement.matchesTarget(name)
                        || (keyForMatch != null && requirement.matchesTarget(keyForMatch))
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
        Component description = descriptionInline(achievement);

        // Personal message.
        String unlocked = config.getString("messages.unlocked", "<green>You unlocked <name>!");
        player.sendMessage(prefix().append(render(unlocked, name, description)));

        // The description, one line per line. The unlock title only has room for
        // the name, so this is where a player actually sees what the achievement
        // was for — e.g. flavour text on line one and how it was earned on line
        // two — rather than just its name.
        if (config.getBoolean("show-description-on-unlock", true)) {
            for (String line : achievement.getDescription()) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                player.sendMessage(Text.parse("<gray>  " + line));
            }
        }

        // Rewards.
        if (achievement.getRewardXp() > 0) {
            player.giveExp(achievement.getRewardXp());
        }
        boolean storeOverflow = config.getBoolean("store-overflow-rewards", true);
        boolean overflowed = false;
        for (org.bukkit.inventory.ItemStack item : achievement.getRewardItems()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            for (org.bukkit.inventory.ItemStack leftover
                    : player.getInventory().addItem(item.clone()).values()) {
                if (storeOverflow) {
                    // Inventory full: keep the item in claimable storage rather
                    // than dropping it on the ground where it could be lost.
                    data.addPendingReward(leftover);
                    overflowed = true;
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
        if (overflowed) {
            player.sendMessage(prefix().append(Text.parse(
                    "<yellow>Your inventory was full — some rewards are waiting. "
                            + "Use <white>/ca claim<yellow> to collect them.")));
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

        // Native advancement toast (experimental; off by default).
        if (config.getBoolean("advancement-toast", false)) {
            ToastNotifier.show(plugin, player, achievement);
        }

        // Title. The subtitle is a template so it can show the description
        // (or both) instead of just the name — a title has only these two lines.
        if (config.getBoolean("show-title", true)) {
            Component titleText = Text.parse(config.getString("messages.title", "<gold>Achievement Unlocked"));
            Component subtitle = render(config.getString("messages.subtitle", "<name>"), name, description);
            player.showTitle(Title.title(titleText, subtitle,
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        }

        // Broadcast.
        if (achievement.isAnnounce() && config.getBoolean("announce-broadcasts", true)) {
            String broadcast = config.getString("messages.broadcast",
                    "<yellow><player></yellow> unlocked <white><name></white>!");
            Component message = prefix().append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(broadcast,
                            Placeholder.component("player", Component.text(player.getName())),
                            Placeholder.component("name", name),
                            Placeholder.component("description", description)));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(message);
            }
            Bukkit.getConsoleSender().sendMessage(message);
        }

        playerData.save(player.getUniqueId());

        // This unlock is itself progress toward a "unlock N achievements"
        // capstone, so recount before leaving.
        handleUnlockCount(player);
    }

    /** Fills a message template's {@code <name>} and {@code <description>} placeholders. */
    private Component render(String template, Component name, Component description) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(template,
                        Placeholder.component("name", name),
                        Placeholder.component("description", description));
    }

    /**
     * The achievement's description as a single line, for places that only have
     * room for one (the title's subtitle). Lines are joined with a separator so
     * "flavour text" and "how to earn it" can both be on screen at once.
     */
    private static Component descriptionInline(Achievement achievement) {
        Component joined = Component.empty();
        boolean first = true;
        for (String line : achievement.getDescription()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!first) {
                joined = joined.append(Text.parse("<dark_gray> • "));
            }
            joined = joined.append(Text.parse(line));
            first = false;
        }
        return joined;
    }

    private Component prefix() {
        return Text.parse(plugin.getConfig().getString("messages.prefix", ""));
    }
}
