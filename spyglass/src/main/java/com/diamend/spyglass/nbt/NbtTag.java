package com.diamend.spyglass.nbt;

/**
 * One NBT value: its type, and the Java object behind it.
 *
 * <p>The accessors never throw and never return null for a primitive — asking a
 * string for its {@code asInt} gets you the fallback. Everything here reads a
 * file written by someone else, so "that tag wasn't the shape I expected" is a
 * normal Tuesday, not an error worth unwinding a report for.
 */
public record NbtTag(NbtType type, Object value) {

    public static NbtTag of(byte value) {
        return new NbtTag(NbtType.BYTE, value);
    }

    public static NbtTag of(short value) {
        return new NbtTag(NbtType.SHORT, value);
    }

    public static NbtTag of(int value) {
        return new NbtTag(NbtType.INT, value);
    }

    public static NbtTag of(long value) {
        return new NbtTag(NbtType.LONG, value);
    }

    public static NbtTag of(float value) {
        return new NbtTag(NbtType.FLOAT, value);
    }

    public static NbtTag of(double value) {
        return new NbtTag(NbtType.DOUBLE, value);
    }

    public static NbtTag of(String value) {
        return new NbtTag(NbtType.STRING, value == null ? "" : value);
    }

    public static NbtTag of(NbtCompound value) {
        return new NbtTag(NbtType.COMPOUND, value);
    }

    public static NbtTag of(NbtList value) {
        return new NbtTag(NbtType.LIST, value);
    }

    public boolean isNumber() {
        return value instanceof Number;
    }

    public Number asNumber() {
        return value instanceof Number number ? number : null;
    }

    public int asInt(int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public long asLong(long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public float asFloat(float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    public double asDouble(double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /** Minecraft has no boolean tag: it stores a byte, and 0 is false. */
    public boolean asBoolean(boolean fallback) {
        return value instanceof Number number ? number.intValue() != 0 : fallback;
    }

    public String asString(String fallback) {
        return value instanceof String text ? text : fallback;
    }

    public NbtCompound asCompound() {
        return value instanceof NbtCompound compound ? compound : null;
    }

    public NbtList asList() {
        return value instanceof NbtList list ? list : null;
    }

    public byte[] asByteArray() {
        return value instanceof byte[] array ? array : null;
    }

    public int[] asIntArray() {
        return value instanceof int[] array ? array : null;
    }

    public long[] asLongArray() {
        return value instanceof long[] array ? array : null;
    }
}
