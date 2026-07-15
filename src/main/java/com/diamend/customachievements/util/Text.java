package com.diamend.customachievements.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Small helpers around Adventure / MiniMessage so the rest of the plugin
 * doesn't have to repeat the "parse and disable italics" dance.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Parses a MiniMessage string into a Component. */
    public static Component parse(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    /**
     * Parses a MiniMessage string for use as item text. Item names and lore
     * render italic by default in Minecraft; this disables that unless the
     * string explicitly asks for italics.
     */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Strips all formatting to a plain string (used for logging/lookups). */
    public static String plain(String input) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(parse(input));
    }
}
