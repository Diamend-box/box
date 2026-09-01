package com.diamend.spyglass.inspect;

import java.util.Locale;

/**
 * The extra word after a section name.
 *
 * <p>It means different things to different sections — a substring filter for
 * {@code stats}, a slot number for {@code item}, a path for {@code nbt} — so it
 * is carried as written and read by whoever needs it.
 *
 * @param argument   what the sender typed after the section, or null
 * @param sensitive  whether this sender may see IP addresses
 */
public record Query(String argument, boolean sensitive) {

    public static Query none() {
        return new Query(null, false);
    }

    public boolean hasArgument() {
        return argument != null && !argument.isBlank();
    }

    /** True when there is no filter, or the text matches it. */
    public boolean matches(String text) {
        if (!hasArgument()) {
            return true;
        }
        return text != null && text.toLowerCase(Locale.ROOT).contains(argument.toLowerCase(Locale.ROOT));
    }

    /**
     * The filter as lower-case text ready to search for, or null when there is
     * no filter and everything matches.
     */
    public String needle() {
        return hasArgument() ? argument.trim().toLowerCase(Locale.ROOT) : null;
    }

    /** The argument as a slot number, or null when it isn't one. */
    public Integer slot() {
        if (!hasArgument()) {
            return null;
        }
        try {
            return Integer.parseInt(argument.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
