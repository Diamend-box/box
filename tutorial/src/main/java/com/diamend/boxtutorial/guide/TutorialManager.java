package com.diamend.boxtutorial.guide;

import com.diamend.boxtutorial.util.Items;
import com.diamend.boxtutorial.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code tutorial.yml}: the ordered list of steps, and the glossary.
 *
 * <p>Steps keep the order they appear in the file — moving one up in the file
 * moves it up in the tutorial — so the file reads like the thing it describes.
 *
 * <p>Nothing here throws on a bad entry. A server owner editing this file is
 * usually doing it live, and a typo in step four is not a reason to stop
 * teaching steps one to three: the entry is skipped, a warning is recorded, and
 * {@code /tutorial reload} prints the list.
 */
public class TutorialManager {

    private final Plugin plugin;
    private final File file;

    private final List<TutorialStep> steps = new ArrayList<>();
    private final Map<String, TutorialStep> stepsById = new LinkedHashMap<>();
    private final List<Topic> topics = new ArrayList<>();
    private final Map<String, Topic> topicsById = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public TutorialManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tutorial.yml");
        load();
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public final void load() {
        steps.clear();
        stepsById.clear();
        topics.clear();
        topicsById.clear();
        warnings.clear();

        if (!file.exists()) {
            plugin.saveResource("tutorial.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        readSteps(config.getConfigurationSection("steps"));
        readTopics(config.getConfigurationSection("topics"));

        for (String warning : warnings) {
            plugin.getLogger().warning("tutorial.yml: " + warning);
        }
    }

    private void readSteps(ConfigurationSection section) {
        if (section == null) {
            warnings.add("no 'steps:' section — the tutorial has nothing to teach");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            String id = Text.lower(key).trim();
            if (entry == null) {
                warnings.add("step '" + key + "' is not a block of settings; skipped");
                continue;
            }
            if (stepsById.containsKey(id)) {
                warnings.add("step '" + id + "' is defined twice; the second was skipped");
                continue;
            }
            if (id.indexOf('.') >= 0) {
                // A dot is how YAML addresses a child key, so an id containing
                // one can never be read back out of its own section.
                warnings.add("step '" + id + "' has a dot in its name; rename it"
                        + " (dashes are fine) — skipped");
                continue;
            }

            StepTrigger trigger = StepTrigger.fromName(entry.getString("trigger"), StepTrigger.READ);
            String rawTrigger = entry.getString("trigger");
            if (rawTrigger != null && !rawTrigger.isBlank()
                    && StepTrigger.fromName(rawTrigger, null) == null) {
                warnings.add("step '" + id + "' has unknown trigger '" + rawTrigger
                        + "'; treated as READ");
            }

            String target = entry.getString("target", "");
            if (trigger == StepTrigger.REACH_LOCATION && TutorialStep.Place.parse(target) == null) {
                warnings.add("step '" + id + "' wants a place but its target isn't"
                        + " world;x;y;z;radius — nobody will be able to finish it");
            }

            TutorialStep step = new TutorialStep(
                    id,
                    icon(id, entry.getString("icon"), Material.PAPER),
                    entry.getString("name", Text.prettify(id)),
                    lines(entry, "description"),
                    trigger,
                    target,
                    entry.getInt("amount", 1),
                    entry.getString("hint", ""),
                    entry.getString("command", ""),
                    entry.getBoolean("optional", false),
                    entry.getStringList("rewards.commands"),
                    entry.getString("rewards.message", ""));
            steps.add(step);
            stepsById.put(id, step);
        }
    }

    private void readTopics(ConfigurationSection section) {
        if (section == null) {
            return; // the glossary is optional
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            String id = Text.lower(key).trim();
            if (entry == null || topicsById.containsKey(id)) {
                warnings.add("topic '" + key + "' is malformed or duplicated; skipped");
                continue;
            }
            List<String> lines = lines(entry, "lines");
            if (lines.isEmpty()) {
                lines = lines(entry, "text");
            }
            Topic topic = new Topic(id,
                    icon(id, entry.getString("icon"), Material.BOOK),
                    entry.getString("title", Text.prettify(id)),
                    lines);
            topics.add(topic);
            topicsById.put(id, topic);
        }
    }

    /** Resolves an icon, warning rather than silently drawing the wrong thing. */
    private Material icon(String id, String name, Material fallback) {
        Material material = Items.material(name, fallback);
        if (name != null && !name.isBlank() && material == fallback
                && !name.trim().equalsIgnoreCase(fallback.name())) {
            warnings.add("'" + id + "' asks for icon '" + name + "', which this server"
                    + " doesn't have; using " + fallback.name());
        }
        return material;
    }

    /** Reads a value that may be either a single line or a list of them. */
    private static List<String> lines(ConfigurationSection section, String path) {
        if (section.isString(path)) {
            return List.of(section.getString(path, ""));
        }
        return section.getStringList(path);
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public List<TutorialStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    public TutorialStep step(String id) {
        return id == null ? null : stepsById.get(Text.lower(id).trim());
    }

    public int stepCount() {
        return steps.size();
    }

    /** Position of a step in the running order, counting from 1; 0 if unknown. */
    public int number(TutorialStep step) {
        int index = steps.indexOf(step);
        return index < 0 ? 0 : index + 1;
    }

    public List<Topic> topics() {
        return Collections.unmodifiableList(topics);
    }

    /** Finds a topic by id, then by prefix, then by a word in its title. */
    public Topic topic(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String cleaned = Text.lower(query).trim();
        Topic exact = topicsById.get(cleaned);
        if (exact != null) {
            return exact;
        }
        for (Topic topic : topics) {
            if (topic.id().startsWith(cleaned)) {
                return topic;
            }
        }
        for (Topic topic : topics) {
            if (Text.plain(topic.title()).toLowerCase(Locale.ROOT).contains(cleaned)) {
                return topic;
            }
        }
        return null;
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public File file() {
        return file;
    }
}
