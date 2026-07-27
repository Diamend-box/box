package com.diamend.boxcore.skill;

import com.diamend.boxcore.util.Registries;
import com.diamend.boxcore.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a skill node actually does. A node may carry any mix of attribute
 * modifiers, permanent potion effects, permissions and one-shot commands.
 *
 * <p>Attribute amounts and potion amplifiers scale with the node's level;
 * permissions and commands do not.
 */
public class NodeEffects {

    /** One attribute change: an amount per node level plus how it combines. */
    public record AttributeBonus(Attribute attribute, double amountPerLevel,
                                 AttributeModifier.Operation operation) {

        public double amountFor(int level) {
            return amountPerLevel * level;
        }
    }

    /** One permanent potion effect, with an optional per-level amplifier bump. */
    public record PotionBonus(PotionEffectType type, int baseAmplifier, int amplifierPerLevel) {

        public int amplifierFor(int level) {
            return Math.max(0, baseAmplifier + (Math.max(1, level) - 1) * amplifierPerLevel);
        }
    }

    private final List<AttributeBonus> attributes = new ArrayList<>();
    private final List<PotionBonus> potions = new ArrayList<>();
    private final List<String> permissions = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();

    public List<AttributeBonus> attributes() {
        return attributes;
    }

    public List<PotionBonus> potions() {
        return potions;
    }

    public List<String> permissions() {
        return permissions;
    }

    public List<String> commands() {
        return commands;
    }

    public boolean isEmpty() {
        return attributes.isEmpty() && potions.isEmpty() && permissions.isEmpty() && commands.isEmpty();
    }

    /**
     * Human-readable effect list for a node's lore, showing the totals the
     * player would have at {@code level}.
     */
    public List<String> describe(int level) {
        List<String> lines = new ArrayList<>();
        int shown = Math.max(1, level);
        for (AttributeBonus bonus : attributes) {
            boolean percent = Registries.isPercent(bonus.operation());
            lines.add("<gray>» <white>" + Text.amount(bonus.amountFor(shown), percent)
                    + " <gray>" + Registries.displayName(bonus.attribute()));
        }
        for (PotionBonus bonus : potions) {
            lines.add("<gray>» <white>" + Registries.displayName(bonus.type())
                    + " " + Text.roman(bonus.amplifierFor(shown) + 1));
        }
        for (String permission : permissions) {
            lines.add("<gray>» <white>Grants <gray>" + permission);
        }
        if (!commands.isEmpty()) {
            lines.add("<gray>» <white>Unlock reward");
        }
        return lines;
    }

    /** Effect totals keyed by attribute+operation, for aggregating a whole tree. */
    public Map<String, Double> totals(int level) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (AttributeBonus bonus : attributes) {
            String key = bonus.attribute().getKey() + "/" + bonus.operation().name();
            totals.merge(key, bonus.amountFor(level), Double::sum);
        }
        return totals;
    }
}
