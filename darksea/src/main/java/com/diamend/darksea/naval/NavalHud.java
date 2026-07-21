package com.diamend.darksea.naval;

/**
 * Renders the always-on boat action bar as a MiniMessage string. Pure
 * string-building so every state is unit-testable without a server.
 *
 * <p>The line is anchored on the hull pips — they are visible in every
 * state, even harpooned, because the one number a sailor must never lose
 * track of is how much boat is left under them. Around the pips:
 *
 * <pre>
 *   ⛵ Stormrunner ❘ ▮▮▮▮▮▮▯▯▯▯ 6 ❘ ⚠ hull wounded ❘ ⚡ 7s
 *   ⚓ HARPOONED — surge to cut! ❘ ▮▮▮▮▮▮▯▯▯▯ 6 ❘ ⚡ ready
 * </pre>
 *
 * <p>Glyphs are deliberately BMP-only (⛵ ⚓ ⚠ ⚡ ▮ ▯ ❘): the vanilla
 * client's Unifont fallback covers the Basic Multilingual Plane, so these
 * render on unmodified clients where true emoji would show as boxes.
 */
public final class NavalHud {

    /** The hull bar is always this many pips, whatever max HP is. */
    static final int SEGMENTS = 10;

    private static final String DIVIDER = " <dark_gray>❘</dark_gray> ";

    private NavalHud() {
    }

    /**
     * The full action-bar line for one rider.
     *
     * @param boatName         plain display name of the boat level
     * @param hp               current hull HP (already regen-adjusted)
     * @param maxHp            configured hull max HP
     * @param wounded          whether the wounded-hull slow is live
     * @param surgeSecondsLeft whole seconds until the surge is ready; 0 = ready
     * @param hooked           whether a harpoon line is on the boat
     */
    public static String render(String boatName, double hp, double maxHp,
                                boolean wounded, int surgeSecondsLeft, boolean hooked) {
        StringBuilder line = new StringBuilder();
        if (hooked) {
            line.append("<red><bold>⚓ HARPOONED</bold> — surge to cut!</red>");
        } else {
            line.append("<gray>⛵</gray> <aqua>").append(boatName).append("</aqua>");
        }
        line.append(DIVIDER).append(hullBar(hp, maxHp));
        if (wounded && !hooked) {
            line.append(DIVIDER).append("<red>⚠ hull wounded</red>");
        }
        line.append(DIVIDER).append(surge(surgeSecondsLeft));
        return line.toString();
    }

    /** The pip bar plus the numeric HP, colored by how much hull is left. */
    static String hullBar(double hp, double maxHp) {
        double fraction = maxHp > 0 ? Math.min(1.0, Math.max(0.0, hp / maxHp)) : 0.0;
        int filled = (int) Math.ceil(fraction * SEGMENTS);
        String color = hullColor(fraction);
        StringBuilder bar = new StringBuilder();
        if (filled > 0) {
            bar.append("<").append(color).append(">")
                    .append("▮".repeat(filled)).append("</").append(color).append(">");
        }
        if (filled < SEGMENTS) {
            bar.append("<dark_gray>").append("▯".repeat(SEGMENTS - filled)).append("</dark_gray>");
        }
        return bar + " <white>" + trim(hp) + "</white>";
    }

    /** Green while comfortable, yellow when pressed, red when nearly under. */
    static String hullColor(double fraction) {
        if (fraction > 0.7) {
            return "green";
        }
        return fraction > 0.35 ? "yellow" : "red";
    }

    /** The surge readout: bright when it's up, quiet countdown when not. */
    static String surge(int secondsLeft) {
        return secondsLeft <= 0
                ? "<yellow>⚡ ready</yellow>"
                : "<gray>⚡ " + secondsLeft + "s</gray>";
    }

    /** 7.0 prints as 7; 6.5 keeps its half so chip damage stays honest. */
    static String trim(double hp) {
        double clamped = Math.max(0, hp);
        return clamped == Math.floor(clamped)
                ? String.valueOf((long) clamped)
                : String.valueOf(Math.round(clamped * 10) / 10.0);
    }
}
