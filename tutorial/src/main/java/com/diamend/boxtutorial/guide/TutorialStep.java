package com.diamend.boxtutorial.guide;

import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One thing the tutorial asks a new player to do (or to read).
 *
 * <p>Steps are immutable and come straight from {@code tutorial.yml}. What a
 * step <em>knows</em> lives here; what happens when it completes lives in
 * {@link TutorialService}.
 */
public class TutorialStep {

    /** A place a {@link StepTrigger#REACH_LOCATION} step points at. */
    public record Place(String world, double x, double y, double z, double radius) {

        /** Parses {@code world;x;y;z;radius}; radius may be omitted. */
        public static Place parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split(";");
            if (parts.length < 4) {
                return null;
            }
            try {
                return new Place(parts[0].trim(),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim()),
                        parts.length > 4 ? Math.max(1.0, Double.parseDouble(parts[4].trim())) : 8.0);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private final String id;
    private final Material icon;
    private final String name;
    private final List<String> description;
    private final StepTrigger trigger;
    private final String target;
    private final int amount;
    private final String hint;
    private final String command;
    private final boolean optional;
    private final List<String> rewardCommands;
    private final String rewardMessage;

    private final Place place;

    public TutorialStep(String id, Material icon, String name, List<String> description,
                        StepTrigger trigger, String target, int amount, String hint,
                        String command, boolean optional, List<String> rewardCommands,
                        String rewardMessage) {
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.description = List.copyOf(description);
        this.trigger = trigger;
        this.target = target == null ? "" : target.trim();
        this.amount = Math.max(1, amount);
        this.hint = hint == null ? "" : hint;
        this.command = command == null ? "" : command.trim();
        this.optional = optional;
        this.rewardCommands = List.copyOf(rewardCommands);
        this.rewardMessage = rewardMessage == null ? "" : rewardMessage;
        this.place = trigger == StepTrigger.REACH_LOCATION ? Place.parse(this.target) : null;
    }

    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    public String name() {
        return name;
    }

    public List<String> description() {
        return description;
    }

    public StepTrigger trigger() {
        return trigger;
    }

    public String target() {
        return target;
    }

    public int amount() {
        return amount;
    }

    public String hint() {
        return hint;
    }

    /** A command worth offering the player, click-to-run. May be empty. */
    public String command() {
        return command;
    }

    /** Optional steps can always be skipped, even when skipping is off. */
    public boolean optional() {
        return optional;
    }

    public List<String> rewardCommands() {
        return rewardCommands;
    }

    public String rewardMessage() {
        return rewardMessage;
    }

    public Place place() {
        return place;
    }

    /**
     * Whether something that just happened counts toward this step.
     *
     * <p>A blank target (or {@code ANY}) counts everything; otherwise the target
     * is a comma-separated list of names, matched case-insensitively. That makes
     * {@code target: "STONE, DEEPSLATE, COBBLESTONE"} work without needing a
     * list in YAML or a tag system nobody asked for.
     */
    public boolean matchesTarget(String actual) {
        if (!trigger.usesTarget() || target.isBlank() || target.equalsIgnoreCase("ANY")) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        String cleaned = strip(actual.trim());
        for (String raw : target.split(",")) {
            String option = strip(raw.trim());
            if (option.isEmpty()) {
                continue;
            }
            if (option.equalsIgnoreCase(cleaned)) {
                return true;
            }
            // A command target matches what was typed after it too, so
            // `target: sell` is finished by "/sell all" as well as "/sell".
            if (trigger == StepTrigger.RUN_COMMAND
                    && cleaned.regionMatches(true, 0, option + " ", 0, option.length() + 1)) {
                return true;
            }
        }
        return false;
    }

    /** Leading slashes are noise: {@code /sell} and {@code sell} are one target. */
    private static String strip(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /** True when this step's progress is worth showing as {@code 3/16}. */
    public boolean counts() {
        return amount > 1;
    }

    /** A one-line "what you have to do", generated from the trigger and target. */
    public String objective() {
        String things = targetWords();
        return switch (trigger) {
            case READ -> "Read this step, then click it";
            case MANUAL -> "A staff member marks this one done";
            case RUN_COMMAND -> "Run /" + (target.isBlank() ? "the command" : firstTarget());
            case BREAK_BLOCK -> "Break " + amount + " " + things;
            case PLACE_BLOCK -> "Place " + amount + " " + things;
            case PICK_UP_ITEM -> "Pick up " + amount + " " + things;
            case CRAFT_ITEM -> "Craft " + amount + " " + things;
            case KILL_MOB -> "Kill " + amount + " " + things;
            case KILL_PLAYER -> "Win " + amount + " fight" + (amount == 1 ? "" : "s");
            case BUY_ITEM -> "Buy " + (amount > 1 ? amount + " " : "") + things
                    + " from the trader";
            case HAVE_ITEM -> "Be carrying " + amount + " " + things;
            case ENTER_WORLD -> "Go to " + (target.isBlank() ? "another world" : firstTarget());
            case REACH_LOCATION -> place == null ? "Go to the marked place"
                    : "Go to " + (int) place.x() + ", " + (int) place.y() + ", " + (int) place.z();
            case PLAYTIME_MINUTES -> "Play for " + amount + " minute" + (amount == 1 ? "" : "s");
        };
    }

    /** How the target reads in {@link #objective()}: {@code Iron Ore}, {@code blocks}. */
    private String targetWords() {
        if (target.isBlank() || target.equalsIgnoreCase("ANY")) {
            return switch (trigger) {
                case BREAK_BLOCK, PLACE_BLOCK -> "blocks";
                case KILL_MOB -> "mobs";
                default -> "items";
            };
        }
        String[] options = target.split(",");
        String first = Text.prettify(options[0].trim().toLowerCase(Locale.ROOT));
        return options.length == 1 ? first : first + " (or " + (options.length - 1) + " more)";
    }

    private String firstTarget() {
        return target.split(",")[0].trim();
    }
}
