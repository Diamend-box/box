package com.diamend.spyglass.nbt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads Minecraft's binary NBT — specifically {@code playerdata/&lt;uuid&gt;.dat},
 * which is a gzipped compound.
 *
 * <p>There is no server API for the data of a player who is offline, and the
 * file is the whole truth about them: inventory, ender chest, position, health,
 * abilities, effects, the lot. So this reads the file directly. It is a reader
 * only — Spyglass never writes a player's save.
 *
 * <p>The parse is defensive. The file comes from another process that may be
 * mid-write, from a different Minecraft version, or simply corrupt, so bad
 * lengths and runaway nesting fail as an {@link IOException} rather than as an
 * allocation the size of the heap.
 */
public final class NbtReader {

    /** Vanilla saves nest a handful deep; anything near this is a broken file. */
    private static final int MAX_DEPTH = 256;

    /** No legitimate player.dat array is anywhere near this long. */
    private static final int MAX_ELEMENTS = 16 * 1024 * 1024;

    /** Nor is any legitimate player.dat this big. */
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private NbtReader() {
    }

    /**
     * Reads a player data file, decompressing it if it is gzipped (it always is,
     * but a hand-made test file might not be).
     *
     * @throws IOException if the file is missing, too big, or isn't NBT
     */
    public static NbtCompound readFile(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_FILE_BYTES) {
            throw new IOException("player data file is implausibly large (" + size + " bytes)");
        }
        byte[] bytes = Files.readAllBytes(path);
        return read(bytes);
    }

    /** Reads a compound from raw bytes, gzipped or not. */
    public static NbtCompound read(byte[] bytes) throws IOException {
        if (bytes.length < 3) {
            throw new IOException("not NBT: only " + bytes.length + " bytes");
        }
        InputStream source = new ByteArrayInputStream(bytes);
        if (isGzip(bytes)) {
            source = new GZIPInputStream(source);
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(source))) {
            return readRoot(in);
        }
    }

    private static boolean isGzip(byte[] bytes) {
        return (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    /**
     * Reads the outermost tag, which the format requires to be a named compound.
     */
    public static NbtCompound readRoot(DataInput in) throws IOException {
        NbtType type = NbtType.byId(in.readUnsignedByte());
        if (type == NbtType.END) {
            return new NbtCompound();
        }
        if (type != NbtType.COMPOUND) {
            throw new IOException("not NBT: root tag is " + (type == null ? "unknown" : type.label()));
        }
        in.readUTF(); // the root's name, empty in every file the server writes
        NbtCompound root = readPayload(in, NbtType.COMPOUND, 0).asCompound();
        return root == null ? new NbtCompound() : root;
    }

    private static NbtTag readPayload(DataInput in, NbtType type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nested more than " + MAX_DEPTH + " deep");
        }
        return switch (type) {
            case END -> new NbtTag(NbtType.END, null);
            case BYTE -> NbtTag.of(in.readByte());
            case SHORT -> NbtTag.of(in.readShort());
            case INT -> NbtTag.of(in.readInt());
            case LONG -> NbtTag.of(in.readLong());
            case FLOAT -> NbtTag.of(in.readFloat());
            case DOUBLE -> NbtTag.of(in.readDouble());
            case STRING -> NbtTag.of(in.readUTF());
            case BYTE_ARRAY -> {
                byte[] array = new byte[length(in.readInt(), 1)];
                in.readFully(array);
                yield new NbtTag(NbtType.BYTE_ARRAY, array);
            }
            case INT_ARRAY -> {
                int[] array = new int[length(in.readInt(), 4)];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readInt();
                }
                yield new NbtTag(NbtType.INT_ARRAY, array);
            }
            case LONG_ARRAY -> {
                long[] array = new long[length(in.readInt(), 8)];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readLong();
                }
                yield new NbtTag(NbtType.LONG_ARRAY, array);
            }
            case LIST -> NbtTag.of(readList(in, depth));
            case COMPOUND -> NbtTag.of(readCompound(in, depth));
        };
    }

    private static NbtList readList(DataInput in, int depth) throws IOException {
        NbtType elementType = NbtType.byId(in.readUnsignedByte());
        int count = in.readInt();
        if (elementType == null) {
            throw new IOException("NBT list of an unknown tag type");
        }
        // An empty list is written with element type END; a non-empty one that
        // claims END is malformed.
        if (elementType == NbtType.END) {
            if (count > 0) {
                throw new IOException("NBT list of " + count + " end tags");
            }
            return NbtList.empty();
        }
        int size = length(count, 1);
        List<NbtTag> items = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            items.add(readPayload(in, elementType, depth + 1));
        }
        return new NbtList(elementType, items);
    }

    private static NbtCompound readCompound(DataInput in, int depth) throws IOException {
        NbtCompound compound = new NbtCompound();
        while (true) {
            int id = in.readUnsignedByte();
            NbtType type = NbtType.byId(id);
            if (type == null) {
                throw new IOException("NBT tag of an unknown type: " + id);
            }
            if (type == NbtType.END) {
                return compound;
            }
            String name = in.readUTF();
            compound.put(name, readPayload(in, type, depth + 1));
        }
    }

    /** Rejects the negative and absurd lengths a damaged file can claim. */
    private static int length(int declared, int bytesPerElement) throws IOException {
        if (declared < 0 || declared > MAX_ELEMENTS) {
            throw new IOException("NBT array claims " + declared + " elements");
        }
        long bytes = (long) declared * bytesPerElement;
        if (bytes > MAX_FILE_BYTES) {
            throw new IOException("NBT array claims " + bytes + " bytes");
        }
        return declared;
    }
}
