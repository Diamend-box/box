package com.diamend.spyglass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diamend.spyglass.inspect.Query;
import com.diamend.spyglass.nbt.NbtWriter;
import com.diamend.spyglass.offline.OfflineInspector;
import com.diamend.spyglass.offline.OfflineSnapshot;
import com.diamend.spyglass.offline.PlayerFiles;
import com.diamend.spyglass.report.Report;
import com.diamend.spyglass.report.Section;

/**
 * Reading a player who is not logged in.
 *
 * <p>This is the half that has no server API behind it, so it is tested against
 * a save file laid out the way a 1.21 server lays one out — right down to the
 * component-style items and the {@code respawn} compound.
 */
class OfflineInspectorTest {

    private static final UUID UUID_NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    Path worldFolder;

    private PlayerFiles files;
    private OfflineInspector inspector;

    @BeforeEach
    void setUp() throws IOException {
        NbtWriter.writeFile(worldFolder.resolve("playerdata").resolve(UUID_NOTCH + ".dat"),
                SamplePlayer.playerData());
        write(worldFolder.resolve("stats").resolve(UUID_NOTCH + ".json"), SamplePlayer.statsJson());
        write(worldFolder.resolve("advancements").resolve(UUID_NOTCH + ".json"),
                SamplePlayer.advancementsJson());
        files = new PlayerFiles(worldFolder.toFile());
        inspector = new OfflineInspector();
    }

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private String render(Section section) {
        return render(section, null);
    }

    private String render(Section section, String argument) {
        OfflineSnapshot snapshot = OfflineSnapshot.load(files, null, UUID_NOTCH, "Notch");
        assertTrue(snapshot.hasData(), () -> "no save loaded: " + snapshot.error());
        Report report = inspector.section(snapshot, section, new Query(argument, true));
        return String.join("\n", report.plain());
    }

    @Test
    void vitalsComeOutOfTheSaveFile() {
        String text = render(Section.VITALS);

        assertTrue(text.contains("17.5/20"), text);
        assertTrue(text.contains("14/20"), text);
        assertTrue(text.contains("SURVIVAL"), text);
        assertTrue(text.contains("may fly"), text);
        assertTrue(text.contains("0.05"), text);
    }

    @Test
    void positionIncludesRespawnAndLastDeath() {
        String text = render(Section.POSITION);

        assertTrue(text.contains("minecraft:overworld 120.50 64.00 -33.25"), text);
        assertTrue(text.contains("90 yaw"), text);
        assertTrue(text.contains("10 70 20"), text);
        assertTrue(text.contains("minecraft:the_nether -40 31 8"), text);
    }

    @Test
    void inventoryReadsModernComponentItems() {
        String text = render(Section.INVENTORY);

        assertTrue(text.contains("diamond_sword"), text);
        assertTrue(text.contains("damage 120"), text);
        assertTrue(text.contains("Excalibur"), text);
        assertTrue(text.contains("sharpness 5"), text);
        assertTrue(text.contains("cooked_beef x32"), text);
        // What is inside a shulker box is part of what someone is carrying.
        assertTrue(text.contains("holds:2"), text);
        assertTrue(text.contains("hotbar"), text);
    }

    @Test
    void inventoryCanBeFiltered() {
        String text = render(Section.INVENTORY, "beef");

        assertTrue(text.contains("cooked_beef"), text);
        assertFalse(text.contains("diamond_sword"), text);
    }

    @Test
    void aFilterLooksInsideAContainer() {
        // The tnt has no slot of its own — it is in the shulker box in slot 9.
        String text = render(Section.INVENTORY, "tnt");

        assertTrue(text.contains("shulker_box"), text);
        assertTrue(text.contains("> tnt x16"), text);
        assertFalse(text.contains("cooked_beef"), text);
    }

    @Test
    void aFilterFollowsContainersInsideContainers() {
        String text = render(Section.INVENTORY, "nether_star");

        assertTrue(text.contains("bundle"), text);
        assertTrue(text.contains("> shulker_box"), text);
        assertTrue(text.contains("> nether_star"), text);
    }

    @Test
    void armourComesFromTheVanillaSlotNumbers() {
        String text = render(Section.ARMOR);

        assertTrue(text.contains("netherite_helmet"), text);
        assertTrue(text.contains("shield"), text);
        assertTrue(text.contains("Excalibur"), "the held slot is the main hand: " + text);
    }

    @Test
    void enderChestIsReadToo() {
        String text = render(Section.ENDERCHEST);

        assertTrue(text.contains("diamond x64"), text);
        assertTrue(text.contains("elytra"), text);
    }

    @Test
    void effectsAndAttributesAreListed() {
        assertTrue(render(Section.EFFECTS).contains("speed"), "effects");
        assertTrue(render(Section.EFFECTS).contains("level 2"), "amplifier is zero-based on disk");

        String attributes = render(Section.ATTRIBUTES);
        assertTrue(attributes.contains("max_health"), attributes);
        assertTrue(attributes.contains("luck"), attributes);
    }

    @Test
    void statisticsComeFromTheStatsFile() {
        String text = render(Section.STATS);

        assertTrue(text.contains("mined.stone"), text);
        assertTrue(text.contains("48,210"), text);
        assertTrue(text.contains("killed.zombie"), text);
        // Playtime is ticks on disk and hours to a human.
        assertTrue(text.contains("custom.play_time"), text);
        assertTrue(text.contains("1728000 ticks"), text);
    }

    @Test
    void statisticsCanBeFiltered() {
        String text = render(Section.STATS, "mined");

        assertTrue(text.contains("mined.stone"), text);
        assertFalse(text.contains("killed.zombie"), text);
    }

    @Test
    void advancementsCountRecipesSeparately() {
        String text = render(Section.ADVANCEMENTS);

        assertTrue(text.contains("minecraft:story/root"), text);
        assertTrue(text.contains("recipe unlocks"), text);
        assertTrue(text.contains("1 (not listed)"), text);
        assertFalse(text.contains("minecraft:recipes/misc/torch"), text);
    }

    @Test
    void persistentDataAndTagsAreReadable() {
        String text = render(Section.DATA);

        assertTrue(text.contains("boxcore:points = 42"), text);
        assertTrue(text.contains("boxcore:home = \"spawn\""), text);
        assertTrue(text.contains("vip, quest.started"), text);
    }

    @Test
    void recipesAreCountedAndSearchable() {
        assertTrue(render(Section.RECIPES).contains("2 recipe(s)"));
        assertTrue(render(Section.RECIPES, "torch").contains("minecraft:torch"));
    }

    @Test
    void oneItemCanBeOpenedUp() {
        String text = render(Section.ITEM, "0");

        assertTrue(text.contains("minecraft:diamond_sword"), text);
        assertTrue(text.contains("Excalibur"), text);
        assertTrue(text.contains("sharpness 5"), text);
        assertTrue(text.contains("Raw tag:"), text);
    }

    @Test
    void theWholeReportHoldsTogether() {
        String text = render(Section.ALL);

        assertTrue(text.contains("Identity (offline)"), text);
        assertTrue(text.contains("Inventory (offline)"), text);
        assertTrue(text.contains("Statistics (offline)"), text);
        assertTrue(text.contains("Permissions"), text);
        assertTrue(text.contains("only exist while a player is connected"), text);
    }

    @Test
    void aMissingSaveIsReportedRatherThanThrown() {
        OfflineSnapshot missing = OfflineSnapshot.load(files, null,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Ghost");

        assertFalse(missing.hasData());
        String text = String.join("\n",
                inspector.section(missing, Section.INVENTORY, Query.none()).plain());
        assertTrue(text.contains("No save data"), text);
        assertTrue(text.contains("does not exist"), text);
    }
}
