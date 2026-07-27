package com.diamend.boxcore.util;

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
     * Parses a string for use as item text. Item names and lore render italic
     * by default in Minecraft; this disables that unless explicitly asked for.
     */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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
     * Formats a modifier amount for display: {@code +2}, {@code -1.5},
     * or {@code +15%} when {@code percent} is set.
     */
    public static String amount(double value, boolean percent) {
        double shown = percent ? value * 100.0 : value;
        String number = Math.abs(shown - Math.rint(shown)) < 1.0e-6
                ? String.valueOf((long) Math.rint(shown))
                : trimZeros(String.format(Locale.ROOT, "%.2f", shown));
        return (shown >= 0 ? "+" : "") + number + (percent ? "%" : "");
    }

    /** Formats a proportion as an unsigned probability, e.g. {@code 12%}. */
    public static String percent(double fraction) {
        return round(fraction * 100.0) + "%";
    }

    /** Formats a duration in seconds, e.g. {@code 6s}. */
    public static String seconds(double value) {
        return round(value) + "s";
    }

    /** Renders a number without a trailing {@code .0}, to one decimal place. */
    private static String round(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0e-6
                ? String.valueOf((long) Math.rint(value))
                : trimZeros(String.format(Locale.ROOT, "%.1f", value));
    }

    private static String trimZeros(String value) {
        return value.contains(".")
                ? value.replaceAll("0+$", "").replaceAll("\\.$", "")
                : value;
    }

    /** Roman numeral for 1-40; falls back to the plain number outside that range. */
    public static String roman(int value) {
        if (value < 1 || value > 40) {
            return String.valueOf(value);
        }
        int[] numbers = { 40, 10, 9, 5, 4, 1 };
        String[] letters = { "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder out = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                out.append(letters[i]);
                remaining -= numbers[i];
            }
        }
        return out.toString();
    }

    /** A 20-segment progress bar, e.g. {@code ■■■■□□□□}. */
    public static String progressBar(double fraction, int width, String filled, String empty) {
        int clamped = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * width);
        return filled.repeat(clamped) + empty.repeat(Math.max(0, width - clamped));
    }
}
