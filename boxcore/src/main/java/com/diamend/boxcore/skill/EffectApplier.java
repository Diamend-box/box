package com.diamend.boxcore.skill;

import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the nodes a player owns into live effects: attribute modifiers,
 * permanent potion effects and permissions.
 *
 * <p>Application is idempotent — every pass recomputes the player's totals from
 * scratch, strips every modifier BoxCore has ever added, and re-applies exactly
 * one modifier per attribute-and-operation. That matters because Bukkit
 * attribute modifiers are saved in the player's own data: without the strip,
 * a rejoin or a reload would stack a second copy of every bonus.
 */
public class EffectApplier {

    private final Plugin plugin;
    private final SkillTreeManager trees;
    private final ProfileManager profiles;

    /** Permission grants are held open per player and rebuilt on every pass. */
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    /** What we last gave each player, so we know what to take away. */
    private final Map<UUID, Set<PotionEffectType>> appliedPotions = new HashMap<>();

    public EffectApplier(Plugin plugin, SkillTreeManager trees, ProfileManager profiles) {
        this.plugin = plugin;
        this.trees = trees;
        this.profiles = profiles;
    }

    private String namespace() {
        return plugin.getName().toLowerCase(Locale.ROOT);
    }

    /** Recomputes and applies everything the player's unlocked nodes grant. */
    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerProfile profile = profiles.get(player.getUniqueId());

        Map<Attribute, Map<AttributeModifier.Operation, Double>> attributeTotals = new LinkedHashMap<>();
        Map<PotionEffectType, Integer> potionTotals = new LinkedHashMap<>();
        Set<String> permissions = new HashSet<>();

        for (SkillNode node : ownedNodes(profile)) {
            int level = level(profile, node);
            for (NodeEffects.AttributeBonus bonus : node.getEffects().attributes()) {
                attributeTotals
                        .computeIfAbsent(bonus.attribute(), key -> new LinkedHashMap<>())
                        .merge(bonus.operation(), bonus.amountFor(level), Double::sum);
            }
            for (NodeEffects.PotionBonus bonus : node.getEffects().potions()) {
                potionTotals.merge(bonus.type(), bonus.amplifierFor(level), Math::max);
            }
            permissions.addAll(node.getEffects().permissions());
        }

        applyAttributes(player, attributeTotals);
        applyPotions(player, potionTotals);
        applyPermissions(player, permissions);
    }

    /**
     * Re-applies only the potion effects.
     *
     * <p>This is what the periodic refresh uses. Attribute modifiers stay
     * applied until something removes them, so re-sending them every cycle
     * would be pure packet churn — potions are the ones another plugin (or a
     * bucket of milk) can quietly strip.
     */
    public void refreshPotions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerProfile profile = profiles.get(player.getUniqueId());
        Map<PotionEffectType, Integer> potionTotals = new LinkedHashMap<>();
        for (SkillNode node : ownedNodes(profile)) {
            for (NodeEffects.PotionBonus bonus : node.getEffects().potions()) {
                potionTotals.merge(bonus.type(), bonus.amplifierFor(level(profile, node)), Math::max);
            }
        }
        applyPotions(player, potionTotals);
    }

    /** The nodes a profile owns that still exist in the loaded trees. */
    private List<SkillNode> ownedNodes(PlayerProfile profile) {
        List<SkillNode> owned = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : profile.getNodes().entrySet()) {
            SkillNode node = trees.getNode(entry.getKey());
            // A node deleted from trees.yml simply stops granting anything;
            // SkillService.reconcile hands its points back on the next pass.
            if (node != null && entry.getValue() > 0) {
                owned.add(node);
            }
        }
        return owned;
    }

    private int level(PlayerProfile profile, SkillNode node) {
        return Math.min(profile.getNodeLevel(node.key()), node.getMaxLevel());
    }

    private void applyAttributes(Player player,
                                 Map<Attribute, Map<AttributeModifier.Operation, Double>> totals) {
        stripModifiers(player);
        for (Map.Entry<Attribute, Map<AttributeModifier.Operation, Double>> entry : totals.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance == null) {
                continue; // attribute doesn't apply to players on this server version
            }
            for (Map.Entry<AttributeModifier.Operation, Double> part : entry.getValue().entrySet()) {
                if (part.getValue() == 0.0) {
                    continue;
                }
                NamespacedKey key = new NamespacedKey(plugin,
                        "skills_" + part.getKey().name().toLowerCase(Locale.ROOT));
                instance.addModifier(new AttributeModifier(key, part.getValue(), part.getKey()));
            }
        }
        clampHealth(player);
    }

    /** Removes every modifier this plugin has added, on every attribute. */
    private void stripModifiers(Player player) {
        String namespace = namespace();
        for (Attribute attribute : attributeRegistry()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (namespace.equals(modifier.getKey().getNamespace())) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    private Iterable<Attribute> attributeRegistry() {
        try {
            return Registry.ATTRIBUTE;
        } catch (Throwable ex) {
            return List.of();
        }
    }

    /** Keeps current health inside a max-health that may have just shrunk. */
    private void clampHealth(Player player) {
        Attribute maxHealthAttribute = com.diamend.boxcore.util.Registries.attribute("max_health");
        if (maxHealthAttribute == null) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(maxHealthAttribute);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(Math.max(1.0, maxHealth.getValue()));
        }
    }

    private void applyPotions(Player player, Map<PotionEffectType, Integer> totals) {
        UUID uuid = player.getUniqueId();
        Set<PotionEffectType> previous = appliedPotions.getOrDefault(uuid, Set.of());
        for (PotionEffectType type : previous) {
            if (!totals.containsKey(type)) {
                player.removePotionEffect(type);
            }
        }
        for (Map.Entry<PotionEffectType, Integer> entry : totals.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current != null
                    && current.getAmplifier() == entry.getValue()
                    && current.getDuration() == PotionEffect.INFINITE_DURATION) {
                continue; // already exactly right — don't re-send it
            }
            player.addPotionEffect(new PotionEffect(entry.getKey(), PotionEffect.INFINITE_DURATION,
                    entry.getValue(), true, false, true));
        }
        appliedPotions.put(uuid, new HashSet<>(totals.keySet()));
    }

    private void applyPermissions(Player player, Set<String> permissions) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment existing = attachments.remove(uuid);
        if (existing != null) {
            try {
                player.removeAttachment(existing);
            } catch (IllegalArgumentException ignored) {
                // Attachment already gone (player relogged) — nothing to undo.
            }
        }
        if (permissions.isEmpty()) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank()) {
                attachment.setPermission(permission.trim(), true);
            }
        }
        attachments.put(uuid, attachment);
        player.recalculatePermissions();
    }

    /**
     * Removes everything this plugin granted a player. Called on quit and on
     * plugin disable so nothing is left behind in their saved attributes.
     */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        stripModifiers(player);
        for (PotionEffectType type : appliedPotions.getOrDefault(uuid, Set.of())) {
            player.removePotionEffect(type);
        }
        appliedPotions.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // Already detached.
            }
        }
    }
}
