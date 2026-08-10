package com.diamend.robobear.challenge;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.data.PlayerData;
import com.diamend.robobear.gui.ObjectiveChoiceMenu;
import com.diamend.robobear.gui.UpgradeShopMenu;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The run state machine: entry, the round loop, the shop, and the ways a run ends.
 *
 * <p>Shaped after the real Robo Bear Challenge. A pass buys entry and a handful
 * of Cogs; each round offers a choice of objectives with the harder one paying
 * better; finishing pays Cogs and opens the shop; the clock only runs while an
 * objective is live. Surviving to a milestone round pays out for keeps, so a run
 * that ends at round nine still banked whatever round five was worth.
 *
 * <p>Everything is main-thread. The block-break path returns on a single map
 * lookup for a player with no run, and never touches disk.
 */
public class RoboService {

    private final RoboBearPlugin plugin;
    private final ObjectiveGenerator generator;
    private final Map<UUID, RoboRun> runs = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public RoboService(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.generator = new ObjectiveGenerator(plugin);
    }

    public RoboRun runOf(Player player) {
        return runs.get(player.getUniqueId());
    }

    public boolean isRunning(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    public int activeCount() {
        return runs.size();
    }

    // ------------------------------------------------------------------
    // Entry
    // ------------------------------------------------------------------

    /**
     * Starts a run, taking the entry pass if one is configured.
     *
     * @return true when a run began
     */
    public boolean start(Player player) {
        if (isRunning(player)) {
            plugin.messages().send(player, "already-running");
            return false;
        }
        if (plugin.mines().size() == 0
                && !plugin.getConfig().getBoolean("objectives.kill-mobs.enabled", true)) {
            plugin.messages().send(player, "nothing-to-do");
            return false;
        }
        long now = System.currentTimeMillis();
        PlayerData record = plugin.data().get(player);
        long cooldown = record.cooldownRemaining(
                plugin.getConfig().getLong("run.cooldown-seconds", 0L), now);
        if (cooldown > 0) {
            plugin.messages().send(player, "cooldown", "time", Text.duration(cooldown));
            return false;
        }

        // Taken last, once entry is otherwise certain, so a refused start never
        // eats the pass.
        if (!takeEntryItem(player)) {
            plugin.messages().send(player, "no-pass", "pass", entryItemLabel());
            return false;
        }

        RoboRun run = new RoboRun(player.getUniqueId(),
                now,
                plugin.getConfig().getInt("run.starting-cogs", 10),
                plugin.getConfig().getInt("run.free-rerolls", 1));
        runs.put(player.getUniqueId(), run);

        plugin.messages().send(player, "run-started", "cogs", run.cogs());
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f);
        offerRound(player, run);
        return true;
    }

    /** Rolls this round's offers and puts the choice in front of the player. */
    private void offerRound(Player player, RoboRun run) {
        List<Objective> offers = generator.offer(run.round());
        if (offers.isEmpty()) {
            plugin.messages().send(player, "nothing-to-do");
            endRun(player, run, false, null);
            return;
        }
        run.setOffers(offers);
        new ObjectiveChoiceMenu(plugin, run).open(player);
    }

    // ------------------------------------------------------------------
    // Choosing
    // ------------------------------------------------------------------

    /** Locks in one of the offered objectives and starts the clock. */
    public void choose(Player player, int index) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || run.state() != RoboRun.State.CHOOSING) {
            return;
        }
        List<Objective> offers = run.offers();
        if (index < 0 || index >= offers.size()) {
            return;
        }
        Objective chosen = offers.get(index);
        if (!chosen.isValid(plugin.mines())) {
            plugin.messages().send(player, "mine-missing");
            return;
        }

        int seconds = plugin.getConfig().getInt("run.round-seconds", 300);
        long now = System.currentTimeMillis();
        run.begin(chosen, seconds, now);

        applyUpgradeEffects(player, run);
        plugin.messages().send(player, "round-begin",
                "round", run.round(),
                "objective", chosen.describe(plugin.mines()),
                "time", Text.duration(run.secondsLeft(now)),
                "cogs", chosen.cogs());
        player.closeInventory();
        updateDisplay(player, run, now);
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f);
    }

    /**
     * Puts the menu the run is currently waiting on back in front of the player.
     *
     * <p>Closing a menu should never strand a run: a player who hits escape on
     * the choice screen can get back to it, and the clock hasn't started.
     */
    public void reopen(Player player) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        switch (run.state()) {
            case CHOOSING -> new ObjectiveChoiceMenu(plugin, run).open(player);
            case SHOPPING -> new UpgradeShopMenu(plugin, run).open(player);
            case RUNNING -> {
                player.closeInventory();
                updateDisplay(player, run, System.currentTimeMillis());
            }
        }
    }

    /** Re-rolls the offered objectives, if the player has a reroll left. */
    public void reroll(Player player) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || run.state() != RoboRun.State.CHOOSING) {
            return;
        }
        if (!run.useReroll()) {
            plugin.messages().send(player, "no-rerolls");
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f);
            return;
        }
        List<Objective> offers = generator.offer(run.round());
        if (!offers.isEmpty()) {
            run.setOffers(offers);
        }
        play(player, Sound.UI_BUTTON_CLICK, 1.2f);
        new ObjectiveChoiceMenu(plugin, run).open(player);
    }

    // ------------------------------------------------------------------
    // Counting
    // ------------------------------------------------------------------

    /** Called for every block broken. Returns instantly for players with no run. */
    public void onBlockBreak(Player player, Block block) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || run.state() != RoboRun.State.RUNNING) {
            return;
        }
        Objective objective = run.objective();
        if (objective == null || objective.isKill()) {
            return;
        }
        if (!objective.acceptsBlock(plugin.mines(), block)) {
            return;
        }
        if (plugin.getConfig().getBoolean("run.ignore-self-placed", true)
                && run.wasPlacedThisRun(block)) {
            return;
        }
        credit(player, run);
    }

    /** Called when a player kills a hostile mob. */
    public void onMobKill(Player player) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || run.state() != RoboRun.State.RUNNING) {
            return;
        }
        Objective objective = run.objective();
        if (objective == null || !objective.isKill()) {
            return;
        }
        credit(player, run);
    }

    private void credit(Player player, RoboRun run) {
        long now = System.currentTimeMillis();
        if (run.isExpired(now)) {
            // The tick task ends it within the second; don't credit work done
            // after the clock ran out.
            return;
        }
        if (run.count()) {
            completeRound(player, run, now);
        } else {
            updateDisplay(player, run, now);
        }
    }

    /** Called when a player places a block, to keep it out of their own count. */
    public void onBlockPlace(Player player, Block block) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || !plugin.getConfig().getBoolean("run.ignore-self-placed", true)) {
            return;
        }
        if (plugin.mines().mineAt(block) == null) {
            return;
        }
        run.notePlaced(block, plugin.getConfig().getInt("run.max-tracked-placements", 20000));
    }

    // ------------------------------------------------------------------
    // Finishing a round
    // ------------------------------------------------------------------

    private void completeRound(Player player, RoboRun run, long now) {
        Objective objective = run.objective();
        int cogs = objective.cogs() + salvageBonus(run);
        run.addCogs(cogs);

        int completed = run.round();
        run.finishRound();
        clearDisplay(player);

        plugin.messages().send(player, "round-done",
                "round", completed,
                "cogs", cogs,
                "total", run.cogs());
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f);

        PlayerData data = plugin.data().get(player);
        data.recordRound(completed, now);

        MilestoneRewards.Tier tier = plugin.milestones().at(completed);
        if (tier != null) {
            awardMilestone(player, run, tier);
        }

        int max = plugin.getConfig().getInt("run.max-rounds", 0);
        if (max > 0 && completed >= max) {
            plugin.messages().send(player, "ladder-cleared", "round", completed);
            endRun(player, run, true, null);
            return;
        }
        new UpgradeShopMenu(plugin, run).open(player);
    }

    private int salvageBonus(RoboRun run) {
        int level = run.levelOf(Upgrade.SALVAGE);
        return level * plugin.getConfig().getInt("upgrades.salvage.cogs-per-level", 3);
    }

    private void awardMilestone(Player player, RoboRun run, MilestoneRewards.Tier tier) {
        run.milestonesEarned().add(tier.round());
        plugin.data().get(player).recordMilestone(tier.round());

        plugin.messages().send(player, "milestone", "name", tier.name(), "round", tier.round());
        play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f);

        deliver(player, tier.items());
        runCommands(player, tier.commands(), tier.round());

        if (plugin.getConfig().getBoolean("rewards.broadcast", false)) {
            Bukkit.getServer().sendMessage(plugin.messages().prefixed("broadcast",
                    "player", player.getName(),
                    "name", tier.name(),
                    "round", tier.round()));
        }
    }

    /** Moves on to the next round from the shop. */
    public void nextRound(Player player) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null || run.state() != RoboRun.State.SHOPPING) {
            return;
        }
        run.nextRound();
        offerRound(player, run);
    }

    // ------------------------------------------------------------------
    // The shop
    // ------------------------------------------------------------------

    /** The cost of the next level of an upgrade for this run. */
    public int costOf(RoboRun run, Upgrade upgrade) {
        int base = plugin.getConfig().getInt("upgrades." + upgrade.key() + ".cost", 8);
        double growth = plugin.getConfig().getDouble("upgrades.cost-growth", 1.6);
        return upgrade.costFor(run.levelOf(upgrade) + 1, base, growth);
    }

    /** Buys one level of an upgrade. Returns true when it went through. */
    public boolean buy(Player player, Upgrade upgrade) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null) {
            return false;
        }
        if (run.levelOf(upgrade) >= upgrade.maxLevel()) {
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f);
            return false;
        }
        int cost = costOf(run, upgrade);
        if (!run.spendCogs(cost)) {
            plugin.messages().send(player, "not-enough-cogs", "cost", cost, "cogs", run.cogs());
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f);
            return false;
        }
        run.addLevel(upgrade);

        if (upgrade == Upgrade.OVERCLOCK) {
            run.addBonusSeconds(plugin.getConfig().getInt("upgrades.overclock.seconds-per-level", 60));
        }
        applyUpgradeEffects(player, run);

        plugin.messages().send(player, "bought",
                "upgrade", upgrade.displayName(),
                "level", run.levelOf(upgrade),
                "cogs", run.cogs());
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.8f);
        return true;
    }

    /**
     * Re-applies every buff the run has bought.
     *
     * <p>Duration covers the round plus a margin, and is refreshed at the start of
     * each round, so a buff lasts exactly as long as the run does and expires on
     * its own if the plugin never gets to clean up.
     */
    private void applyUpgradeEffects(Player player, RoboRun run) {
        int seconds = plugin.getConfig().getInt("run.round-seconds", 300)
                + run.bonusSeconds() + 30;
        int ticks = Math.max(20, seconds * 20);

        for (Map.Entry<Upgrade, Integer> entry : run.upgrades().entrySet()) {
            PotionEffect effect = entry.getKey().effectFor(entry.getValue(), ticks);
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    private void clearUpgradeEffects(Player player, RoboRun run) {
        for (Upgrade upgrade : run.upgrades().keySet()) {
            PotionEffect effect = upgrade.effectFor(1, 1);
            if (effect != null) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** Ends a run for a reason the player should hear about. */
    public void fail(Player player, String messageKey, Object... replacements) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        endRun(player, run, false, messageKey, replacements);
    }

    /** Voluntarily stops, keeping everything already banked. */
    public void retire(Player player) {
        RoboRun run = runs.get(player.getUniqueId());
        if (run == null) {
            plugin.messages().send(player, "not-running");
            return;
        }
        endRun(player, run, true, "retired", "round", run.round());
    }

    private void endRun(Player player, RoboRun run, boolean graceful,
                        String messageKey, Object... replacements) {
        runs.remove(run.player());
        clearDisplay(player);
        clearUpgradeEffects(player, run);

        long now = System.currentTimeMillis();
        PlayerData data = plugin.data().get(player);
        // The round they died on doesn't count as reached; the last finished one does.
        data.recordRunEnd(run.round() - 1, run.elapsedSeconds(now), now);

        if (messageKey != null) {
            plugin.messages().send(player, messageKey, replacements);
        }
        plugin.messages().send(player, "run-summary",
                "rounds", Math.max(0, run.round() - 1),
                "milestones", run.milestonesEarned().size(),
                "time", Text.duration(run.elapsedSeconds(now)));

        play(player, graceful ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_VILLAGER_NO, 0.9f);
    }

    /** Drops a run silently, for a player who is leaving anyway. */
    public void abandon(UUID uuid) {
        RoboRun run = runs.remove(uuid);
        BossBar bar = bars.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (bar != null) {
                player.hideBossBar(bar);
            }
            if (run != null) {
                clearUpgradeEffects(player, run);
            }
        }
    }

    /** Ends every run, for shutdown and reloads. */
    public void abandonAll() {
        for (UUID uuid : Map.copyOf(runs).keySet()) {
            abandon(uuid);
        }
        for (Map.Entry<UUID, BossBar> entry : Map.copyOf(bars).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
        runs.clear();
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    /** Runs once a second: expires overdue rounds and refreshes the display. */
    public void tick() {
        if (runs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int warnAt = plugin.getConfig().getInt("display.warn-seconds", 10);

        for (UUID uuid : Map.copyOf(runs).keySet()) {
            RoboRun run = runs.get(uuid);
            if (run == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                abandon(uuid);
                continue;
            }
            if (run.state() != RoboRun.State.RUNNING) {
                continue; // the clock is stopped between rounds
            }
            if (run.isExpired(now)) {
                endRun(player, run, false, "failed-time", "round", run.round());
                continue;
            }
            long left = run.secondsLeft(now);
            if (!run.warned() && warnAt > 0 && left <= warnAt) {
                run.markWarned();
                play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f);
            }
            updateDisplay(player, run, now);
        }
    }

    // ------------------------------------------------------------------
    // Entry item
    // ------------------------------------------------------------------

    /** The configured pass, or null when entry is free. */
    public ItemStack entryItemPrototype() {
        Material material = Items.material(
                plugin.getConfig().getString("run.entry-item.item", ""), null);
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material,
                Math.max(1, plugin.getConfig().getInt("run.entry-item.amount", 1)));
        String name = plugin.getConfig().getString("run.entry-item.name", "");
        if (name != null && !name.isBlank()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.item(name));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public String entryItemLabel() {
        ItemStack prototype = entryItemPrototype();
        if (prototype == null) {
            return "nothing";
        }
        return prototype.getAmount() + "× " + Items.describe(prototype);
    }

    /**
     * Takes the entry pass from the player's inventory.
     *
     * <p>Taken only once entry is otherwise certain, so a refused start never
     * eats the pass — the same rule BoxCore applies to its respec token.
     */
    private boolean takeEntryItem(Player player) {
        ItemStack prototype = entryItemPrototype();
        if (prototype == null) {
            return true; // entry is free
        }
        int needed = prototype.getAmount();
        String wantedName = plugin.getConfig().getString("run.entry-item.name", "");
        boolean matchName = wantedName != null && !wantedName.isBlank();
        String wantedPlain = matchName ? Text.plain(wantedName) : "";

        // Read and write through getItem/setItem rather than mutating the array
        // getContents() hands back — those stacks are copies on some server
        // implementations, and a pass that silently isn't consumed is a free run.
        PlayerInventory inventory = player.getInventory();
        int size = inventory.getSize();

        int found = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (matches(item, prototype.getType(), matchName, wantedPlain)) {
                found += item.getAmount();
            }
        }
        if (found < needed) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("run.entry-item.consume", true)) {
            return true;
        }

        int remaining = needed;
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!matches(item, prototype.getType(), matchName, wantedPlain)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (item.getAmount() - take <= 0) {
                inventory.setItem(slot, null);
            } else {
                ItemStack reduced = item.clone();
                reduced.setAmount(item.getAmount() - take);
                inventory.setItem(slot, reduced);
            }
        }
        return true;
    }

    private boolean matches(ItemStack item, Material type, boolean matchName, String wantedPlain) {
        if (item == null || item.getType() != type) {
            return false;
        }
        if (!matchName) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return Text.plain(Text.serialize(meta.displayName())).equalsIgnoreCase(wantedPlain);
    }

    // ------------------------------------------------------------------
    // Reward delivery
    // ------------------------------------------------------------------

    /**
     * Hands over a milestone's items.
     *
     * <p>Default delivery is the floor, not the inventory. The risk spec pays a
     * PvP kill the same way (§11): value that lands in the open, unbanked, is
     * value somebody else can still take off you.
     */
    private void deliver(Player player, List<ItemStack> items) {
        boolean toInventory = "inventory".equalsIgnoreCase(
                plugin.getConfig().getString("rewards.delivery", "ground"));

        for (ItemStack reward : items) {
            ItemStack copy = reward.clone();
            if (toInventory) {
                for (ItemStack overflow : player.getInventory().addItem(copy).values()) {
                    dropAt(player, overflow);
                }
            } else {
                dropAt(player, copy);
            }
        }
    }

    private void runCommands(Player player, List<String> commands, int round) {
        for (String command : commands) {
            String filled = command
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%round%", String.valueOf(round));
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled);
            } catch (Throwable error) {
                plugin.getLogger().warning("Milestone command failed at round " + round
                        + ": " + filled + " (" + error + ")");
            }
        }
    }

    private void dropAt(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        Location where = player.getLocation();
        World world = where.getWorld();
        if (world == null) {
            player.getInventory().addItem(item);
            return;
        }
        world.dropItemNaturally(where, item);
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private String displayMode() {
        return plugin.getConfig().getString("display.mode", "actionbar")
                .toLowerCase(Locale.ROOT).trim();
    }

    private void updateDisplay(Player player, RoboRun run, long now) {
        String mode = displayMode();
        Objective objective = run.objective();
        if (mode.equals("none") || objective == null) {
            return;
        }
        long left = run.secondsLeft(now);
        boolean urgent = left <= plugin.getConfig().getInt("display.warn-seconds", 10);
        String colour = urgent ? "<red>" : "<yellow>";

        String line = "<gray>R" + run.round() + " "
                + "<white>" + Text.number(run.progress())
                + "<gray>/<white>" + Text.number(objective.amount())
                + " <gray>" + objective.describeShort(plugin.mines())
                + "  " + colour + Text.clock(left)
                + "  <dark_gray>| <gold>" + run.cogs() + " cogs";

        if (mode.equals("bossbar")) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), key -> {
                BossBar created = BossBar.bossBar(Component.empty(), 1.0f,
                        BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                player.showBossBar(created);
                return created;
            });
            bar.name(Text.parse(line));
            bar.progress((float) Math.max(0.0, Math.min(1.0, run.fraction())));
            bar.color(urgent ? BossBar.Color.RED : BossBar.Color.YELLOW);
        } else {
            player.sendActionBar(Text.parse(line));
        }
    }

    private void clearDisplay(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        if (displayMode().equals("actionbar")) {
            player.sendActionBar(Component.empty());
        }
    }

    private void play(Player player, Sound sound, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, 0.7f, pitch);
        } catch (Throwable ignored) {
            // A server without that sound key is not a reason to break a run.
        }
    }
}
