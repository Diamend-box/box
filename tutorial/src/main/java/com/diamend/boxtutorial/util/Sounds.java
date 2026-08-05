package com.diamend.boxtutorial.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Playing a sound at a player, without ever letting feedback break the thing it
 * was giving feedback about.
 *
 * <p>Sound constants are renamed and retired between Minecraft versions, and a
 * missing one throws where it is looked up rather than where it is declared.
 * That is a poor reason for a tutorial step to fail to complete, so every call
 * here swallows the failure and carries on silently.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
            // Feedback only.
        }
    }
}
