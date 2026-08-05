package com.diamend.boxtutorial.guide;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.arena.ArenaBlueprint;
import com.diamend.boxtutorial.arena.Instance;
import com.diamend.boxtutorial.data.Progress;
import com.diamend.boxtutorial.util.Messages;
import com.diamend.boxtutorial.util.Sounds;
import com.diamend.boxtutorial.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The tutorial itself: who is on which step, what finishes one, and what
 * happens when the last one is done.
 *
 * <p>Steps run in order by default. A checklist you can do in any order is a
 * list of chores; a tutorial is a sequence, and the point of the sequence is
 * that step four makes sense because you did step three. Only the current step
 * is armed, so mining sixteen blocks in the second minute doesn't silently tick
 * off a step the player hasn't been told about yet. Set {@code sequential:
 * false} to arm everything at once.
 */
public class TutorialService {

    private final BoxTutorialPlugin plugin;

    public TutorialService(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public Progress progress(Player player) {
        return plugin.store().get(player.getUniqueId());
    }

    /** True when this player should be nudged, tracked and shown a boss bar. */
    public boolean isActive(Progress progress) {
        return progress != null && progress.started() && !progress.finished()
                && !progress.stopped() && plugin.tutorial().stepCount() > 0;
    }

    /** The step a player is on: the first one they haven't completed. */
    public TutorialStep current(Progress progress) {
        for (TutorialStep step : plugin.tutorial().steps()) {
            if (!progress.isComplete(step.id())) {
                return step;
            }
        }
        return null;
    }

    /** The steps that events can currently complete. */
    public List<TutorialStep> armed(Progress progress) {
        if (sequential()) {
            TutorialStep current = current(progress);
            return current == null ? List.of() : List.of(current);
        }
        List<TutorialStep> open = new ArrayList<>();
        for (TutorialStep step : plugin.tutorial().steps()) {
            if (!progress.isComplete(step.id())) {
                open.add(step);
            }
        }
        return open;
    }

    public int completedCount(Progress progress) {
        int done = 0;
        for (TutorialStep step : plugin.tutorial().steps()) {
            if (progress.isComplete(step.id())) {
                done++;
            }
        }
        return done;
    }

    public int percent(Progress progress) {
        int total = plugin.tutorial().stepCount();
        return total == 0 ? 100 : (int) Math.round(completedCount(progress) * 100.0 / total);
    }

    public boolean sequential() {
        return plugin.getConfig().getBoolean("sequential", true);
    }

    // ------------------------------------------------------------------
    // Starting and stopping
    // ------------------------------------------------------------------

    /**
     * Starts (or restarts) the tutorial: claims an arena and puts them in it.
     *
     * @param fresh clear any progress first — a deliberate restart rather than
     *              picking up where they left off
     */
    public void start(Player player, boolean fresh) {
        if (plugin.tutorial().stepCount() == 0) {
            plugin.messages().send(player, "nothing-to-teach");
            return;
        }
        // Claimed before anything is written down, so a server with every arena
        // busy doesn't wipe somebody's progress on the way to refusing them.
        if (plugin.instances().claim(player) == null) {
            plugin.messages().send(player, "arena-full");
            return;
        }
        Progress progress = progress(player);
        if (fresh) {
            progress.reset();
        }
        progress.setStarted(true);
        progress.setStopped(false);
        progress.setFinished(false);
        progress.setName(player.getName());
        plugin.store().touch();

        if (plugin.getConfig().getBoolean("show-welcome-title", true)) {
            showTitle(player, plugin.messages().raw("welcome-title"),
                    plugin.messages().raw("welcome-subtitle"));
        }
        plugin.messages().send(player, "started");
        enter(player);
    }

    /**
     * Puts a player into their arena — new, or coming back to a half-finished
     * one — and tells them what they're on.
     *
     * @return false when there was no arena to give them
     */
    public boolean enter(Player player) {
        Instance instance = plugin.instances().claim(player);
        if (instance == null) {
            plugin.messages().send(player, "arena-full");
            return false;
        }
        player.teleport(instance.spawnPoint());
        plugin.guide().refresh(player);
        announceLater(player, plugin.getConfig().getLong("first-step-delay-ticks", 40L));
        return true;
    }

    /** True while this player is standing in the arena they were given. */
    public boolean isInArena(Player player) {
        Instance instance = plugin.instances().of(player);
        return instance != null && instance.contains(player.getLocation());
    }

    /**
     * Called when a player turns up outside the arena world mid-tutorial.
     *
     * <p>Their progress is kept — they're one {@code /tutorial} from being back
     * where they were — but the arena goes back in the pool. Reserving a room
     * for somebody who walked out of it is how a 32-instance server runs out at
     * eleven players.
     */
    public void leftArena(Player player) {
        boolean had = plugin.instances().of(player) != null;
        plugin.instances().release(player.getUniqueId());
        plugin.guide().hide(player);
        if (had && isActive(progress(player))) {
            plugin.messages().send(player, "arena-left");
        }
    }

    /** The player asked to be left alone; nothing is lost, it just goes quiet. */
    public void stop(Player player) {
        Progress progress = progress(player);
        progress.setStopped(true);
        plugin.store().touch();
        plugin.guide().hide(player);
        sendHome(player);
        plugin.messages().send(player, "stopped");
    }

    public void reset(UUID uuid) {
        plugin.store().get(uuid).reset();
        plugin.store().touch();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.guide().hide(online);
            sendHome(online);
        }
    }

    /**
     * Sends a player back out to spawn and hands their arena back.
     *
     * <p>Nothing is taken off them on the way out: the axe they bought and the
     * ore they dug are theirs. The arena is a place they were, not a sandbox
     * that gets confiscated.
     */
    public void sendHome(Player player) {
        boolean inside = plugin.instances().isArenaWorld(player.getWorld());
        plugin.instances().release(player.getUniqueId());
        if (!inside) {
            return;
        }
        ArenaBlueprint blueprint = plugin.blueprint();
        if (blueprint.returnMode() == ArenaBlueprint.ReturnMode.COMMAND) {
            String command = blueprint.returnCommand();
            player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            // A command that didn't move them would strand them in the arena.
            if (plugin.instances().isArenaWorld(player.getWorld())) {
                player.teleport(fallbackHome());
            }
            return;
        }
        if (blueprint.returnMode() == ArenaBlueprint.ReturnMode.LOCATION) {
            World world = Bukkit.getWorld(blueprint.returnWorld());
            if (world != null) {
                player.teleport(new Location(world, blueprint.returnX(),
                        blueprint.returnY(), blueprint.returnZ()));
                return;
            }
            plugin.getLogger().warning("return.world '" + blueprint.returnWorld()
                    + "' isn't loaded; sending them to the main spawn instead.");
        }
        player.teleport(fallbackHome());
    }

    /** The main world's spawn — the one place that always exists. */
    private Location fallbackHome() {
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.instances().isArenaWorld(world)) {
                return world.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** Empties the arena on shutdown or reload, so nobody is left in a void. */
    public void evacuate() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.instances().isArenaWorld(player.getWorld())) {
                sendHome(player);
            }
        }
        plugin.instances().releaseAll();
    }

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    /**
     * Notes that something happened, and completes a step if that finished it.
     *
     * <p>Every listener funnels through here, so the rules about which steps are
     * armed and what counts live in exactly one place.
     */
    public void record(Player player, StepTrigger trigger, String target, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Progress progress = progress(player);
        if (!isActive(progress)) {
            return;
        }
        for (TutorialStep step : armed(progress)) {
            if (step.trigger() != trigger || !step.matchesTarget(target)) {
                continue;
            }
            int total = progress.addCount(step.id(), amount);
            plugin.store().touch();
            if (total >= step.amount()) {
                completeStep(player, step, true);
            } else {
                plugin.guide().refresh(player);
            }
            if (sequential()) {
                return;
            }
        }
    }

    /** Completes a step for an online player, with the feedback and the rewards. */
    public boolean completeStep(Player player, TutorialStep step, boolean rewards) {
        if (player == null || step == null) {
            return false;
        }
        Progress progress = progress(player);
        if (!progress.complete(step.id())) {
            return false;
        }
        plugin.store().touch();

        plugin.messages().send(player, "step-complete",
                "step", step.name(),
                "number", plugin.tutorial().number(step),
                "total", plugin.tutorial().stepCount());
        if (rewards) {
            plugin.messages().sendPlain(player, step.rewardMessage());
            for (String command : step.rewardCommands()) {
                runConsole(player, command);
            }
        }
        Sounds.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);

        if (current(progress) == null) {
            finish(player);
        } else {
            plugin.guide().refresh(player);
            announceLater(player, plugin.getConfig().getLong("next-step-delay-ticks", 30L));
        }
        return true;
    }

    /** Marks a step done for someone who may be offline. No rewards, no noise. */
    public boolean completeOffline(UUID uuid, TutorialStep step) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return completeStep(online, step, false);
        }
        Progress progress = plugin.store().get(uuid);
        if (!progress.complete(step.id())) {
            return false;
        }
        plugin.store().touch();
        return true;
    }

    /** Whether this player is allowed to move past the step they're stuck on. */
    public boolean canSkip(TutorialStep step) {
        return step != null
                && (step.optional() || plugin.getConfig().getBoolean("allow-step-skip", true));
    }

    /**
     * Steps past a step the player can't or won't do.
     *
     * <p>It's here because a tutorial that traps someone on step four — the one
     * wired to a command this server doesn't have — is worse than one they can
     * walk past.
     *
     * <p>The step is closed out so it stops being armed, but it is also recorded
     * as <em>skipped</em>, and the menu draws that differently: a checklist that
     * ticks off something the player never did is lying to them.
     */
    public boolean skipStep(Player player, TutorialStep step) {
        if (player == null || step == null || !canSkip(step)) {
            return false;
        }
        Progress progress = progress(player);
        if (progress.isComplete(step.id())) {
            return false;
        }
        progress.complete(step.id());
        progress.markSkipped(step.id());
        plugin.store().touch();
        plugin.messages().send(player, "step-skipped", "step", step.name());

        if (current(progress) == null) {
            finish(player);
        } else {
            plugin.guide().refresh(player);
            announceLater(player, plugin.getConfig().getLong("next-step-delay-ticks", 30L));
        }
        return true;
    }

    private void finish(Player player) {
        Progress progress = progress(player);
        progress.setFinished(true);
        plugin.store().touch();
        plugin.guide().hide(player);

        plugin.messages().send(player, "finished");
        if (plugin.getConfig().getBoolean("completion.title", true)) {
            showTitle(player, plugin.messages().raw("finished-title"),
                    plugin.messages().raw("finished-subtitle"));
        }
        Sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);

        for (String command : plugin.getConfig().getStringList("completion.commands")) {
            runConsole(player, command);
        }
        if (plugin.getConfig().getBoolean("completion.broadcast", false)) {
            String line = plugin.messages().raw("finished-broadcast", "player", player.getName());
            if (!line.isBlank()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    plugin.messages().sendLiteral(online, line);
                }
            }
        }

        // A beat before the teleport, so the last thing they read isn't
        // interrupted by the loading screen. What they made comes with them.
        UUID uuid = player.getUniqueId();
        long delay = Math.max(1L, plugin.getConfig().getLong("return-delay-ticks", 60L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                sendHome(online);
                plugin.messages().send(online, "sent-home");
            } else {
                plugin.instances().release(uuid);
            }
        }, delay);
    }

    // ------------------------------------------------------------------
    // Telling the player about it
    // ------------------------------------------------------------------

    /** Sends the briefing for whichever step the player is on. */
    public void announceCurrent(Player player) {
        Progress progress = progress(player);
        if (!isActive(progress)) {
            return;
        }
        TutorialStep step = current(progress);
        if (step != null) {
            announce(player, step);
        }
    }

    public void announceLater(Player player, long ticks) {
        if (ticks <= 0) {
            announceCurrent(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                announceCurrent(online);
            }
        }, ticks);
    }

    /** The full "here's what to do next" block for one step. */
    public void announce(Player player, TutorialStep step) {
        if (player == null || step == null) {
            return;
        }
        Messages messages = plugin.messages();
        boolean spacer = plugin.getConfig().getBoolean("chat-spacers", true);
        if (spacer) {
            player.sendMessage(Component.empty());
        }
        messages.send(player, "step-header",
                "number", plugin.tutorial().number(step),
                "total", plugin.tutorial().stepCount(),
                "step", step.name());
        for (String line : step.description()) {
            messages.sendPlain(player, "  " + line);
        }
        if (!step.trigger().isPassive()) {
            messages.send(player, "step-goal", "goal", step.objective());
        }
        messages.sendPlain(player, step.hint().isBlank() ? "" : "  " + step.hint());
        sendCommandLine(player, step.command());
        if (spacer) {
            player.sendMessage(Component.empty());
        }
        Sounds.play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    /** A click-to-run line that takes them into the arena. */
    public void sendInvite(Player player) {
        String template = plugin.messages().raw("invite-button");
        Component line = Text.parse(template.isBlank()
                ? "  <yellow>» <white><u>Click here to start the tutorial</u>" : template);
        player.sendMessage(line
                .clickEvent(ClickEvent.runCommand("/tutorial"))
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>Runs <white>/tutorial"))));
    }

    /** A click-to-run line for the step's suggested command, when it has one. */
    public void sendCommandLine(Player player, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }
        String command = rawCommand.startsWith("/") ? rawCommand : "/" + rawCommand;
        String template = plugin.messages().raw("step-command", "command", command);
        Component line = Text.parse(template.isBlank()
                ? "  <yellow>» <white>" + command + " <gray>(click to run)" : template);
        player.sendMessage(line
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>Click to run <white>" + command))));
    }

    /** Prints one glossary topic. Works for the console as well as a player. */
    public void explain(CommandSender target, Topic topic) {
        if (target == null || topic == null) {
            return;
        }
        Messages messages = plugin.messages();
        boolean spacer = plugin.getConfig().getBoolean("chat-spacers", true)
                && target instanceof Player;
        if (spacer) {
            target.sendMessage(Component.empty());
        }
        messages.send(target, "topic-header", "title", topic.title());
        for (String line : topic.lines()) {
            messages.sendPlain(target, "  " + line);
        }
        if (spacer) {
            target.sendMessage(Component.empty());
        }
    }

    private void showTitle(Player player, String main, String sub) {
        if (main.isBlank() && sub.isBlank()) {
            return;
        }
        player.showTitle(Title.title(Text.parse(main), Text.parse(sub),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3),
                        Duration.ofMillis(700))));
    }

    /** Runs a reward command from the console, with the usual placeholders. */
    private void runConsole(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());
        if (parsed.startsWith("/")) {
            parsed = parsed.substring(1);
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Reward command failed (" + parsed + "): " + ex.getMessage());
        }
    }
}
