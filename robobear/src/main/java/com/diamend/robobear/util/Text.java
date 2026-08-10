package com.diamend.robobear.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers around Adventure / MiniMessage. Text may use MiniMessage tags
 * (e.g. {@code <green>}, {@code <bold>}) and/or classic Minecraft colour codes
 * ({@code &a}, {@code &l}, {@code &#ff0000}); legacy codes are translated to
 * MiniMessage before parsing so both styles work together.
 *
 * <p>Deliberately the same contract as BoxCore's and CustomAchievements' copies:
 * a server owner who has learned one plugin's text rules has learned all three.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final Pattern HEX = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private Text() {
    }

    /** Converts classic {@code &}/{@code §} colour codes into MiniMessage tags. */
    static String translateLegacy(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }
        String result = HEX.matcher(input).replaceAll("<#$1>");
        Matcher matcher = CODE.matcher(result);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = CODES.getOrDefault(code, "");
            matcher.appendReplacement(out, tag.isEmpty() ? "" : "<" + tag + ">");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Parses a MiniMessage (and/or legacy colour code) string into a Component. */
    public static Component parse(String input) {
        return MM.deserialize(translateLegacy(input == null ? "" : input));
    }

    /**
     * Parses a string for use as item text. Item names and lore render italic by
     * default in Minecraft; this disables that unless explicitly asked for.
     */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Renders a component back to a MiniMessage string. */
    public static String serialize(Component component) {
        return component == null ? "" : MM.serialize(component);
    }

    /** Strips all formatting to a plain string. */
    public static String plain(String input) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(parse(input));
    }

    /**
     * Renders to a legacy section-sign ({@code §}) coloured string, for external
     * consumers (PlaceholderAPI, holograms) that expect classic colour codes.
     */
    public static String legacy(String input) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(parse(input));
    }

    /** Lower-cases using the root locale (stable case folding for config keys). */
    public static String lower(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    /** Turns {@code some_material_name} into {@code Some Material Name}. */
    public static String prettify(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String[] words = input.toLowerCase(Locale.ROOT).replace('.', ' ').replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }

    /** Thousands-separated number, e.g. {@code 1,024}. */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * A running clock, always {@code m:ss} — what a countdown wants. Negative
     * input reads {@code 0:00} rather than going backwards.
     */
    public static String clock(long seconds) {
        long safe = Math.max(0, seconds);
        return (safe / 60) + ":" + String.format(Locale.ROOT, "%02d", safe % 60);
    }

    /**
     * A duration in prose, e.g. {@code 2m 30s}, {@code 45s}, {@code 1h 5m}. Used
     * where a number is being read rather than watched.
     */
    public static String duration(long seconds) {
        long safe = Math.max(0, seconds);
        if (safe < 60) {
            return safe + "s";
        }
        long hours = safe / 3600;
        long minutes = (safe % 3600) / 60;
        long remainder = safe % 60;
        if (hours > 0) {
            return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
        }
        return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
    }

    /** A progress bar of the given width, e.g. {@code ■■■■□□□□}. */
    public static String progressBar(double fraction, int width, String filled, String empty) {
        int clamped = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * width);
        return filled.repeat(clamped) + empty.repeat(Math.max(0, width - clamped));
    }
}
