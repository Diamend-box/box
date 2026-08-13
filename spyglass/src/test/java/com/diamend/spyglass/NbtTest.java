package com.diamend.spyglass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtList;
import com.diamend.spyglass.nbt.NbtPath;
import com.diamend.spyglass.nbt.NbtPrinter;
import com.diamend.spyglass.nbt.NbtReader;
import com.diamend.spyglass.nbt.NbtTag;
import com.diamend.spyglass.nbt.NbtType;
import com.diamend.spyglass.nbt.NbtWriter;

/**
 * The NBT reader against a file shaped like the one a real server writes: every
 * tag type, gzipped and not, plus the paths and printing built on top of it.
 */
class NbtTest {

    @Test
    void everyTagTypeSurvivesTheRoundTrip() throws IOException {
        NbtCompound root = new NbtCompound();
        root.put("byte", NbtTag.of((byte) -12));
        root.put("short", NbtTag.of((short) 4096));
        root.put("int", NbtTag.of(1_234_567));
        root.put("long", NbtTag.of(1_700_000_000_000L));
        root.put("float", NbtTag.of(0.05f));
        root.put("double", NbtTag.of(64.25D));
        root.put("string", NbtTag.of("minecraft:diamond_sword"));
        root.put("bytes", new NbtTag(NbtType.BYTE_ARRAY, new byte[] { 1, 2, 3 }));
        root.put("ints", new NbtTag(NbtType.INT_ARRAY, new int[] { 4, 5, 6 }));
        root.put("longs", new NbtTag(NbtType.LONG_ARRAY, new long[] { 7L, 8L }));
        NbtCompound nested = new NbtCompound();
        nested.put("flying", NbtTag.of((byte) 1));
        root.put("abilities", NbtTag.of(nested));
        root.put("list", NbtTag.of(new NbtList(NbtType.STRING,
                List.of(NbtTag.of("a"), NbtTag.of("b")))));
        root.put("empty", NbtTag.of(NbtList.empty()));

        NbtCompound back = NbtReader.read(NbtWriter.toGzippedBytes(root));

        assertEquals(-12, back.integer("byte", 0));
        assertEquals(4096, back.integer("short", 0));
        assertEquals(1_234_567, back.integer("int", 0));
        assertEquals(1_700_000_000_000L, back.longValue("long", 0L));
        assertEquals(0.05f, back.floatValue("float", 0f), 1e-7);
        assertEquals(64.25D, back.doubleValue("double", 0D), 1e-9);
        assertEquals("minecraft:diamond_sword", back.string("string", ""));
        assertEquals(3, back.get("bytes").asByteArray().length);
        assertEquals(5, back.get("ints").asIntArray()[1]);
        assertEquals(8L, back.get("longs").asLongArray()[1]);
        assertTrue(back.compound("abilities").bool("flying", false));
        assertEquals(List.of("a", "b"), back.list("list").strings());
        assertTrue(back.list("empty").isEmpty());
        // Insertion order is what makes two dumps comparable.
        assertEquals("byte", back.keys().iterator().next());
    }

    @Test
    void readsAnUncompressedFileToo() throws IOException {
        NbtCompound root = new NbtCompound();
        root.put("Health", NbtTag.of(20.0f));

        NbtCompound back = NbtReader.read(NbtWriter.toRawBytes(root));

        assertEquals(20.0f, back.floatValue("Health", 0f), 1e-7);
    }

    @Test
    void readsARealShapedSaveFromDisk(@TempDir Path dir) throws IOException {
        Path file = NbtWriter.writeFile(dir.resolve("playerdata/abc.dat"), SamplePlayer.playerData());

        NbtCompound root = NbtReader.readFile(file);

        assertEquals(4189, root.integer("DataVersion", 0));
        assertEquals(17.5f, root.floatValue("Health", 0f), 1e-7);
        assertEquals(5, root.list("Inventory").size());
        assertEquals("minecraft:overworld", root.string("Dimension", ""));
        assertEquals(120.5D, root.list("Pos").doubleAt(0, 0), 1e-9);
    }

    @Test
    void rubbishIsRejectedRatherThanGuessedAt() {
        assertThrows(IOException.class, () -> NbtReader.read(new byte[] { 9, 9, 9, 9, 9 }));
        assertThrows(IOException.class, () -> NbtReader.read(new byte[] { 1 }));
    }

    @Test
    void aListClaimingBillionsOfElementsIsRefused() {
        // Type 10 (compound), empty root name, then one LIST tag whose length is
        // 2 billion: a file like this would otherwise be an OutOfMemoryError.
        byte[] bad = {
                10, 0, 0,                       // compound, name ""
                9, 0, 1, 'x',                   // list named "x"
                10,                             // of compounds
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        };
        assertThrows(IOException.class, () -> NbtReader.read(bad));
    }

    @Test
    void pathsWalkCompoundsAndLists() {
        NbtCompound root = SamplePlayer.playerData();

        assertEquals(0.05f, NbtPath.resolve(root, "abilities.flySpeed").asFloat(0f), 1e-7);
        assertEquals("minecraft:diamond_sword",
                NbtPath.resolve(root, "Inventory.0.id").asString(""));
        assertEquals("minecraft:diamond_sword",
                NbtPath.resolve(root, "Inventory[0]/id").asString(""));
        assertEquals(5, NbtPath.resolve(root, "Inventory.0.components.minecraft:enchantments"
                + ".minecraft:sharpness").asInt(0));
        // Nobody remembers the capitalisation, so it does not matter.
        assertNotNull(NbtPath.resolve(root, "inventory"));
        assertNull(NbtPath.resolve(root, "Inventory.99"));
        assertNull(NbtPath.resolve(root, "nothing.here"));
        assertEquals(root.size(), NbtPath.resolve(root, "").asCompound().size());
    }

    @Test
    void printingIsIndentedAndTyped() {
        NbtCompound root = SamplePlayer.playerData();
        NbtPrinter printer = new NbtPrinter(6, 8);

        List<String> lines = printer.print("abilities", root.get("abilities"));

        assertEquals("abilities: compound(7)", lines.get(0));
        assertTrue(lines.contains("  flySpeed: 0.05f"), () -> String.join("\n", lines));
        assertTrue(lines.contains("  mayfly: 1b"), () -> String.join("\n", lines));
    }

    @Test
    void printingStopsGoingDeeperThanItIsTold() {
        NbtCompound root = SamplePlayer.playerData();

        List<String> shallow = new NbtPrinter(1, 4).print("", root.asTag());

        assertFalse(String.join("\n", shallow).contains("flySpeed"),
                "depth 1 should not reach into abilities");
        assertTrue(String.join("\n", shallow).contains("deeper than the limit"),
                () -> String.join("\n", shallow));
    }

    @Test
    void longListsAreTrimmedWithACount() {
        NbtCompound root = new NbtCompound();
        List<NbtTag> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(NbtTag.of("item" + i));
        }
        root.put("many", NbtTag.of(new NbtList(NbtType.STRING, many)));

        List<String> lines = new NbtPrinter(4, 5).print("many", root.get("many"));

        assertEquals("many: list(40 x string)", lines.get(0));
        assertTrue(lines.get(lines.size() - 1).contains("35 more"), lines.toString());
    }
}
