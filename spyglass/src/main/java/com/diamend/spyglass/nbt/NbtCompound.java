package com.diamend.spyglass.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A named bag of tags — the shape of {@code playerdata/&lt;uuid&gt;.dat} and of
 * nearly everything inside it.
 *
 * <p>Insertion order is kept, so a raw dump comes out in the order the server
 * wrote it rather than shuffled into a hash order that makes diffing two players
 * pointless.
 */
public final class NbtCompound {

    private final Map<String, NbtTag> tags;

    public NbtCompound() {
        this(new LinkedHashMap<>());
    }

    public NbtCompound(Map<String, NbtTag> tags) {
        this.tags = tags;
    }

    public void put(String name, NbtTag tag) {
        if (name != null && tag != null) {
            tags.put(name, tag);
        }
    }

    public NbtTag get(String name) {
        return tags.get(name);
    }

    public boolean has(String name) {
        return tags.containsKey(name);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(tags.keySet());
    }

    public Map<String, NbtTag> tags() {
        return Collections.unmodifiableMap(tags);
    }

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    // ------------------------------------------------------------------
    // Typed reads. A missing tag and a tag of the wrong type both give the
    // fallback: callers are rendering a report, not validating a save.
    // ------------------------------------------------------------------

    public NbtCompound compound(String name) {
        NbtTag tag = tags.get(name);
        return tag == null ? null : tag.asCompound();
    }

    public NbtList list(String name) {
        NbtTag tag = tags.get(name);
        return tag == null ? null : tag.asList();
    }

    public String string(String name, String fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asString(fallback);
    }

    public int integer(String name, int fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asInt(fallback);
    }

    public long longValue(String name, long fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asLong(fallback);
    }

    public float floatValue(String name, float fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asFloat(fallback);
    }

    public double doubleValue(String name, double fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asDouble(fallback);
    }

    public boolean bool(String name, boolean fallback) {
        NbtTag tag = tags.get(name);
        return tag == null ? fallback : tag.asBoolean(fallback);
    }

    /**
     * The first of these names that is present, for tags Mojang has renamed —
     * {@code active_effects} was {@code ActiveEffects}, {@code attributes} was
     * {@code Attributes}, and a server that has not been through those updates
     * still has the old spelling on disk.
     */
    public NbtTag firstOf(String... names) {
        for (String name : names) {
            NbtTag tag = tags.get(name);
            if (tag != null) {
                return tag;
            }
        }
        return null;
    }

    public NbtTag asTag() {
        return NbtTag.of(this);
    }
}
