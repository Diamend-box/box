package com.diamend.spyglass.nbt;

/**
 * The thirteen NBT tag types, in Mojang's numbering.
 *
 * <p>The ids are what actually appears in {@code playerdata/<uuid>.dat}; the
 * labels are what a human reading a report wants to see next to a value.
 */
public enum NbtType {

    END(0, "end"),
    BYTE(1, "byte"),
    SHORT(2, "short"),
    INT(3, "int"),
    LONG(4, "long"),
    FLOAT(5, "float"),
    DOUBLE(6, "double"),
    BYTE_ARRAY(7, "byte[]"),
    STRING(8, "string"),
    LIST(9, "list"),
    COMPOUND(10, "compound"),
    INT_ARRAY(11, "int[]"),
    LONG_ARRAY(12, "long[]");

    private static final NbtType[] BY_ID = new NbtType[13];

    static {
        for (NbtType type : values()) {
            BY_ID[type.id] = type;
        }
    }

    private final int id;
    private final String label;

    NbtType(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** True for the tags that carry a single number. */
    public boolean isNumeric() {
        return this == BYTE || this == SHORT || this == INT
                || this == LONG || this == FLOAT || this == DOUBLE;
    }

    /** The type with this id, or null when the file names a type that isn't one. */
    public static NbtType byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }
}
