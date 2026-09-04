package com.diamend.customachievements.achievement;

import com.diamend.customachievements.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Best-effort native "advancement toast" pop-up on unlock, using a hidden,
 * runtime-registered advancement that is granted (showing the toast) then
 * revoked.
 *
 * <p>Runtime advancement registration is not supported on every server
 * implementation; if it fails, toasts silently disable themselves so unlocks
 * (message/title/sound) are never affected. Off by default — see
 * {@code advancement-toast} in config.yml.
 */
public final class ToastNotifier {

    private static boolean unsupported = false;
    private static final Map<String, Advancement> CACHE = new HashMap<>();

    private ToastNotifier() {
    }

    public static void show(Plugin plugin, Player player, Achievement achievement) {
        if (unsupported) {
            return;
        }
        try {
            Advancement advancement = CACHE.computeIfAbsent(achievement.getId(), id -> {
                NamespacedKey key = new NamespacedKey(plugin, "toast_" + id);
                Advancement existing = Bukkit.getAdvancement(key);
                return existing != null ? existing : Bukkit.getUnsafe().loadAdvancement(key, json(achievement));
            });
            if (advancement == null) {
                return;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                progress.awardCriteria(criterion);
            }
            // Revoke shortly after so it can toast again next time and never
            // clutters the player's real advancement list.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                AdvancementProgress after = player.getAdvancementProgress(advancement);
                for (String criterion : after.getAwardedCriteria()) {
                    after.revokeCriteria(criterion);
                }
            }, 20L);
        } catch (Throwable ex) {
            unsupported = true;
            plugin.getLogger().warning("Advancement toasts are not supported on this server; disabling them. ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    private static String json(Achievement achievement) {
        String icon = achievement.getIcon().getKey().toString();
        String title = jsonString(Text.plain(achievement.getDisplayName()));
        return "{\"criteria\":{\"impossible\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{\"icon\":{\"id\":\"" + icon + "\"},"
                + "\"title\":" + title + ",\"description\":\"\","
                + "\"frame\":\"task\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true}}";
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
