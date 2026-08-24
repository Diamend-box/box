package com.diamend.boxcore.boost;

import java.util.Locale;

/**
 * What a boost multiplies.
 *
 * <p>The two are deliberately separate rather than one "everything" boost,
 * because they act on different things and neither implies the other.
 * {@link #DROPS} changes how much actually falls out of a block;
 * {@link #COLLECTIONS} changes how much that counts for. Collections are
 * credited from the block's natural drops rather than from the items that
 * spawn, so a drops boost does not quietly inflate collection progress and the
 * two never compound behind the owner's back.
 */
public enum BoostType {

    /** Multiplies how much a broken block actually drops. */
    DROPS("drops", "Drops"),

    /** Multiplies how much everything gathered counts toward collections. */
    COLLECTIONS("collections", "Collections");

    private final String id;
    private final String display;

    BoostType(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    /** Resolves a typed name, or null when it names no boost type. */
    public static BoostType parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (BoostType type : values()) {
            if (type.id.equals(wanted) || type.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return type;
            }
        }
        return null;
    }

    /** Whether a typed name means "every type at once". */
    public static boolean isAll(String name) {
        if (name == null) {
            return false;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return wanted.equals("all") || wanted.equals("*") || wanted.equals("everything");
    }
}
