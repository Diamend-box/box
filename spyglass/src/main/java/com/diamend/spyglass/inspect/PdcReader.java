package com.diamend.spyglass.inspect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.diamend.spyglass.util.Safe;

/**
 * Reads a persistent-data value without being told its type.
 *
 * <p>The API only hands a value back if you ask with the right
 * {@link PersistentDataType}, and a container written by another plugin does not
 * come with a schema. So this tries each type in turn and reports the first that
 * answers — which is exactly what a person reading someone else's plugin data
 * wants to see.
 */
public final class PdcReader {

    private static final PersistentDataType<?, ?>[] TYPES = {
            PersistentDataType.STRING,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.DOUBLE,
            PersistentDataType.FLOAT,
            PersistentDataType.SHORT,
            PersistentDataType.BYTE,
            PersistentDataType.INTEGER_ARRAY,
            PersistentDataType.LONG_ARRAY,
            PersistentDataType.BYTE_ARRAY,
            PersistentDataType.TAG_CONTAINER,
    };

    private PdcReader() {
    }

    /** The value at that key, rendered with the type it turned out to be. */
    public static String read(PersistentDataContainer container, NamespacedKey key) {
        for (PersistentDataType<?, ?> type : TYPES) {
            String rendered = tryRead(container, key, type);
            if (rendered != null) {
                return rendered;
            }
        }
        return "(unreadable type)";
    }

    private static String tryRead(PersistentDataContainer container, NamespacedKey key,
                                  PersistentDataType<?, ?> type) {
        Object value = Safe.call(() -> (Object) container.get(key, type), null);
        if (value == null) {
            return null;
        }
        String label = type.getComplexType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (value instanceof int[] array) {
            return "int[" + array.length + "] " + Arrays.toString(array);
        }
        if (value instanceof long[] array) {
            return "long[" + array.length + "] " + Arrays.toString(array);
        }
        if (value instanceof byte[] array) {
            return "byte[" + array.length + "]";
        }
        if (value instanceof PersistentDataContainer nested) {
            return "container(" + nested.getKeys().size() + ") " + keyList(nested);
        }
        return value + " (" + label + ")";
    }

    private static String keyList(PersistentDataContainer container) {
        List<String> keys = new ArrayList<>();
        for (NamespacedKey key : container.getKeys()) {
            keys.add(key.toString());
        }
        return keys.toString();
    }
}
