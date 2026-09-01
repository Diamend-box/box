package com.diamend.robobear.mine;

import com.diamend.robobear.mine.MineResetLiteProvider.MineReader;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reflection layer against mine objects shaped like the builds people
 * actually run.
 *
 * <p>The obfuscated case is the one that matters: MineResetLite 4.21.2 ships
 * with its fields renamed to single letters, so there is no {@code minX} left
 * to find and the older shapes all come up empty. These fixtures are modelled
 * on the member dump from a real 4.21.2 server rather than invented.
 */
class MineReaderTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("mines");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an unobfuscated build is read straight off its getters")
    void plainBuildUsesGetters() throws Exception {
        MineReader reader = MineResetLiteProvider.resolveReader(new PlainMine(world));

        assertNotNull(reader, "named coordinate getters are the easy case");
        assertEquals("coordinate getters", reader.describe());
        assertBox(reader.read(new PlainMine(world)));
    }

    @Test
    @DisplayName("an obfuscated build is read from the keys it serialises with")
    void obfuscatedBuildUsesTheSerialisedMap() throws Exception {
        Object mine = new ObfuscatedMine(world);

        MineReader reader = MineResetLiteProvider.resolveReader(mine);

        assertNotNull(reader, "renamed fields must not be the end of the road");
        assertTrue(reader.describe().contains("serialised map"),
                "it should say where the bounds came from: " + reader.describe());
        assertTrue(reader.describe().contains("minX"),
                "the resolved keys belong in the description: " + reader.describe());
        assertBox(reader.read(mine));
    }

    @Test
    @DisplayName("the map carries the name and world too when nothing else is left")
    void fullyRenamedBuildFallsBackToTheMapEntirely() throws Exception {
        Object mine = new FullyRenamedMine();

        MineReader reader = MineResetLiteProvider.resolveReader(mine);

        assertNotNull(reader, "with getName and getWorld gone, the map is all there is");
        assertBox(reader.read(mine));
    }

    @Test
    @DisplayName("a serialisable object that isn't box-shaped is refused")
    void somethingElseEntirelyIsRejected() {
        assertNull(MineResetLiteProvider.resolveReader(new NotAMine()),
                "a serialize() with no coordinates must fail resolution, not read as an empty mine");
    }

    // ------------------------------------------------------------------
    // What a mine is made of
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a composition getter is read straight off the mine")
    void compositionComesOffTheGetter() {
        Set<org.bukkit.Material> found = provider().compositionOf(new StockedMine(world));

        assertTrue(found.contains(org.bukkit.Material.STONE));
        assertTrue(found.contains(org.bukkit.Material.GOLD_ORE));
        assertFalse(found.contains(org.bukkit.Material.DIAMOND_ORE),
                "only what the mine actually contains");
    }

    @Test
    @DisplayName("an obfuscated mine's composition still comes out of serialize()")
    void compositionSurvivesObfuscation() {
        Set<org.bukkit.Material> found = provider().compositionOf(new ObfuscatedMine(world));

        assertEquals(Set.of(org.bukkit.Material.STONE), found,
                "the serialised map is the one shape obfuscation can't rename");
    }

    @Test
    @DisplayName("composition entries are read however the build spells them")
    void compositionEntriesAreParsedLoosely() {
        Set<org.bukkit.Material> found = provider().compositionOf(new AwkwardlySpeltMine());

        assertTrue(found.contains(org.bukkit.Material.GOLD_ORE), "plain name");
        assertTrue(found.contains(org.bukkit.Material.STONE), "namespaced id");
        assertTrue(found.contains(org.bukkit.Material.DIAMOND_ORE), "wrapped in a block type");
        assertFalse(found.contains(org.bukkit.Material.AIR), "air is not something to mine");
        assertEquals(3, found.size(), "and nothing invented from the junk entry");
    }

    @Test
    @DisplayName("a build that says nothing about its blocks reports nothing")
    void silentBuildReportsNoComposition() {
        assertTrue(provider().compositionOf(new PlainMine(world)).isEmpty(),
                "an empty answer must stay empty rather than becoming a wrong one");
    }

    private MineResetLiteProvider provider() {
        return new MineResetLiteProvider(java.util.logging.Logger.getLogger("robobear-test"));
    }

    private void assertBox(MineRegion region) {
        assertNotNull(region, "the mine should have produced a region");
        assertEquals("quarry", region.id());
        assertEquals("mines", region.world());
        assertEquals(10, region.minX());
        assertEquals(64, region.minY());
        assertEquals(20, region.minZ());
        assertEquals(30, region.maxX());
        assertEquals(80, region.maxY());
        assertEquals(40, region.maxZ());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** A build whose symbols were never renamed. */
    public static final class PlainMine {

        private final World world;

        PlainMine(World world) {
            this.world = world;
        }

        public String getName() {
            return "quarry";
        }

        public World getWorld() {
            return world;
        }

        public int getMinX() {
            return 10;
        }

        public int getMinY() {
            return 64;
        }

        public int getMinZ() {
            return 20;
        }

        public int getMaxX() {
            return 30;
        }

        public int getMaxY() {
            return 80;
        }

        public int getMaxZ() {
            return 40;
        }
    }

    /**
     * MineResetLite 4.21.2 as it actually arrives. The field letters and the
     * void no-arg methods are copied from a real server's member dump; getName
     * and getWorld survive obfuscation because the plugin's own API calls them,
     * and serialize() keeps the original keys because saved mine files have to
     * keep loading.
     */
    public static final class ObfuscatedMine {

        private final World l;
        private final int f = 10;
        private final int J = 64;
        private final int o = 20;
        private final int c = 30;
        private final int t = 80;
        private final int v = 40;

        ObfuscatedMine(World l) {
            this.l = l;
        }

        public String getName() {
            return "quarry";
        }

        public World getWorld() {
            return l;
        }

        public void B() {
            // an obfuscated internal, and exactly the kind of thing a reader
            // must not mistake for an accessor
        }

        public String[] y() {
            return new String[0];
        }

        public Map<String, Object> serialize() {
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("world", l.getName());
            me.put("minX", f);
            me.put("minY", J);
            me.put("minZ", o);
            me.put("maxX", c);
            me.put("maxY", t);
            me.put("maxZ", v);
            me.put("name", "quarry");
            me.put("composition", Map.of("STONE", 1.0));
            return me;
        }
    }

    /** Renamed past even getName and getWorld, and writing punctuated keys. */
    public static final class FullyRenamedMine {

        public Map<String, Object> serialize() {
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("id", "quarry");
            me.put("world", "mines");
            me.put("min-x", 10);
            me.put("min-y", 64);
            me.put("min-z", 20);
            me.put("max-x", 30);
            me.put("max-y", 80);
            me.put("max-z", 40);
            return me;
        }
    }

    /** A build that exposes its composition the easy way. */
    public static final class StockedMine {

        private final World world;

        StockedMine(World world) {
            this.world = world;
        }

        public String getName() {
            return "quarry";
        }

        public World getWorld() {
            return world;
        }

        public Map<String, Double> getComposition() {
            Map<String, Double> mix = new LinkedHashMap<>();
            mix.put("STONE", 0.8);
            mix.put("GOLD_ORE", 0.2);
            return mix;
        }
    }

    /** Every spelling of a composition entry seen in the wild, plus junk. */
    public static final class AwkwardlySpeltMine {

        public java.util.List<Object> getBlocks() {
            return java.util.List.of(
                    "GOLD_ORE",
                    "minecraft:stone",
                    new BlockType(org.bukkit.Material.DIAMOND_ORE),
                    "AIR",
                    "NOT_A_REAL_BLOCK");
        }
    }

    /** MineResetLite wraps composition entries in its own type. */
    public record BlockType(org.bukkit.Material material) {

        public org.bukkit.Material getMaterial() {
            return material;
        }
    }

    /** Serialisable, named, and nothing to do with a region. */
    public static final class NotAMine {

        public String getName() {
            return "shop";
        }

        public Map<String, Object> serialize() {
            return Map.of("name", "shop", "owner", "steve");
        }
    }
}
