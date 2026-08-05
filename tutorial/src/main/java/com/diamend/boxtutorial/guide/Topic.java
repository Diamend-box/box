package com.diamend.boxtutorial.guide;

import org.bukkit.Material;

import java.util.List;

/**
 * A glossary entry: one boxpvp idea explained in a few lines.
 *
 * <p>The steps teach a player what to do on <em>this</em> server. Topics teach
 * them what the words mean — "combat tag", "safezone", "your box" — which is
 * the part a first-timer is actually missing, and the part no step order can
 * cover because they'll want it at some unpredictable moment.
 */
public record Topic(String id, Material icon, String title, List<String> lines) {

    public Topic {
        lines = List.copyOf(lines);
    }
}
