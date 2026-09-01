package com.diamend.spyglass.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** An NBT list: N tags, all of one type. */
public final class NbtList implements Iterable<NbtTag> {

    private static final NbtList EMPTY = new NbtList(NbtType.END, List.of());

    private final NbtType elementType;
    private final List<NbtTag> items;

    public NbtList(NbtType elementType, List<NbtTag> items) {
        this.elementType = elementType == null ? NbtType.END : elementType;
        this.items = items;
    }

    public static NbtList empty() {
        return EMPTY;
    }

    public NbtType elementType() {
        return elementType;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public NbtTag get(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public List<NbtTag> items() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public Iterator<NbtTag> iterator() {
        return items().iterator();
    }

    /** The elements that are compounds — the common case for lists of things. */
    public List<NbtCompound> compounds() {
        List<NbtCompound> out = new ArrayList<>(items.size());
        for (NbtTag tag : items) {
            NbtCompound compound = tag.asCompound();
            if (compound != null) {
                out.add(compound);
            }
        }
        return out;
    }

    /** The elements that are strings, in order. */
    public List<String> strings() {
        List<String> out = new ArrayList<>(items.size());
        for (NbtTag tag : items) {
            String text = tag.asString(null);
            if (text != null) {
                out.add(text);
            }
        }
        return out;
    }

    public double doubleAt(int index, double fallback) {
        NbtTag tag = get(index);
        return tag == null ? fallback : tag.asDouble(fallback);
    }

    public float floatAt(int index, float fallback) {
        NbtTag tag = get(index);
        return tag == null ? fallback : tag.asFloat(fallback);
    }

    public int intAt(int index, int fallback) {
        NbtTag tag = get(index);
        return tag == null ? fallback : tag.asInt(fallback);
    }
}
