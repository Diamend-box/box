package com.diamend.darksea.mob;

/**
 * Readable names for the things trying to kill you.
 *
 * Every spawn carries its mobs.yml type as an identity tag, but nothing was
 * ever shown to the player — so a server without MythicMobs (or with the
 * pack in the wrong folder) presents its whole roster as anonymous husks,
 * which is precisely what the second live test looked like. Mob ids are
 * written in the pack's camel case, so the display name falls straight out
 * of the id: {@code CrazedSailor} reads as "Crazed Sailor" whether it
 * spawned as a Mythic mob or as its vanilla fallback.
 *
 * Bukkit-free, so the whole rule is unit-testable.
 */
public final class MobNames {

    private MobNames() {
    }

    /**
     * Splits a camel-case mob id into words. Ids that are already spaced,
     * underscored or lower case are returned tidied rather than mangled, so
     * a hand-written mobs.yml entry never ends up worse off than it started.
     */
    public static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String cleaned = id.replace('_', ' ').replace('-', ' ');

        // Break camel case: a capital that follows a lower-case letter or a
        // digit starts a new word. A capital after a capital does not, so an
        // acronym in the middle of an id survives intact.
        StringBuilder spaced = new StringBuilder(cleaned.length() + 4);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (i > 0 && Character.isUpperCase(c)
                    && !Character.isUpperCase(cleaned.charAt(i - 1))
                    && !Character.isWhitespace(cleaned.charAt(i - 1))) {
                spaced.append(' ');
            }
            spaced.append(c);
        }

        StringBuilder out = new StringBuilder(spaced.length());
        for (String word : spaced.toString().trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            // A SHOUTED word (a vanilla EntityType name, usually) becomes
            // Title Case; anything else keeps the casing it was written with.
            String body = word.equals(word.toUpperCase()) && word.length() > 1
                    ? word.substring(1).toLowerCase()
                    : word.substring(1);
            out.append(Character.toUpperCase(word.charAt(0))).append(body);
        }
        return out.toString();
    }
}
