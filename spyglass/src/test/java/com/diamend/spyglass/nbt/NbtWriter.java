package com.diamend.spyglass.nbt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Writes NBT — test-only, so the tests can hand the reader a file the server
 * would have written and check it comes back out the same.
 *
 * <p>Spyglass itself never writes a player's data. This lives in test sources
 * for that reason.
 */
public final class NbtWriter {

    private NbtWriter() {
    }

    /** A gzipped, named-root file exactly as the server writes {@code <uuid>.dat}. */
    public static byte[] toGzippedBytes(NbtCompound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(NbtType.COMPOUND.id());
            out.writeUTF("");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    /** The same, uncompressed, to prove the reader copes with both. */
    public static byte[] toRawBytes(NbtCompound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(NbtType.COMPOUND.id());
            out.writeUTF("");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    public static Path writeFile(Path path, NbtCompound root) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, toGzippedBytes(root));
        return path;
    }

    private static void writeCompound(DataOutputStream out, NbtCompound compound) throws IOException {
        for (String key : compound.keys()) {
            NbtTag tag = compound.get(key);
            out.writeByte(tag.type().id());
            out.writeUTF(key);
            writePayload(out, tag);
        }
        out.writeByte(NbtType.END.id());
    }

    private static void writePayload(DataOutputStream out, NbtTag tag) throws IOException {
        switch (tag.type()) {
            case BYTE -> out.writeByte(tag.asInt(0));
            case SHORT -> out.writeShort(tag.asInt(0));
            case INT -> out.writeInt(tag.asInt(0));
            case LONG -> out.writeLong(tag.asLong(0L));
            case FLOAT -> out.writeFloat(tag.asFloat(0f));
            case DOUBLE -> out.writeDouble(tag.asDouble(0d));
            case STRING -> out.writeUTF(tag.asString(""));
            case BYTE_ARRAY -> {
                byte[] array = tag.asByteArray();
                out.writeInt(array.length);
                out.write(array);
            }
            case INT_ARRAY -> {
                int[] array = tag.asIntArray();
                out.writeInt(array.length);
                for (int value : array) {
                    out.writeInt(value);
                }
            }
            case LONG_ARRAY -> {
                long[] array = tag.asLongArray();
                out.writeInt(array.length);
                for (long value : array) {
                    out.writeLong(value);
                }
            }
            case LIST -> {
                NbtList list = tag.asList();
                out.writeByte(list.isEmpty() ? NbtType.END.id() : list.elementType().id());
                out.writeInt(list.size());
                for (NbtTag item : list) {
                    writePayload(out, item);
                }
            }
            case COMPOUND -> writeCompound(out, tag.asCompound());
            case END -> {
                // nothing to write
            }
        }
    }
}
