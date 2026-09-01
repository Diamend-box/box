package com.diamend.spyglass;

import java.util.ArrayList;
import java.util.List;

import com.diamend.spyglass.nbt.NbtCompound;
import com.diamend.spyglass.nbt.NbtList;
import com.diamend.spyglass.nbt.NbtTag;
import com.diamend.spyglass.nbt.NbtType;

/**
 * Builds a {@code playerdata/&lt;uuid&gt;.dat} the way a 1.21 server writes one,
 * so the offline half of the plugin can be tested against the shape it will
 * actually meet.
 */
public final class SamplePlayer {

    private SamplePlayer() {
    }

    /** A player with kit on, food eaten, effects running and an ender chest. */
    public static NbtCompound playerData() {
        NbtCompound root = new NbtCompound();
        root.put("DataVersion", NbtTag.of(4189));
        root.put("Health", NbtTag.of(17.5f));
        root.put("foodLevel", NbtTag.of(14));
        root.put("foodSaturationLevel", NbtTag.of(2.5f));
        root.put("foodExhaustionLevel", NbtTag.of(1.25f));
        root.put("XpLevel", NbtTag.of(31));
        root.put("XpP", NbtTag.of(0.5f));
        root.put("XpTotal", NbtTag.of(1024));
        root.put("Score", NbtTag.of(1024));
        root.put("playerGameType", NbtTag.of(0));
        root.put("previousPlayerGameType", NbtTag.of(1));
        root.put("Air", NbtTag.of((short) 300));
        root.put("Fire", NbtTag.of((short) -20));
        root.put("FallDistance", NbtTag.of(0.0f));
        root.put("OnGround", NbtTag.of((byte) 1));
        root.put("Invulnerable", NbtTag.of((byte) 0));
        root.put("PortalCooldown", NbtTag.of(0));
        root.put("SelectedItemSlot", NbtTag.of(0));
        root.put("seenCredits", NbtTag.of((byte) 1));
        root.put("Dimension", NbtTag.of("minecraft:overworld"));
        root.put("Pos", doubles(120.5D, 64.0D, -33.25D));
        root.put("Motion", doubles(0.0D, -0.0784D, 0.0D));
        root.put("Rotation", floats(90.0f, -12.5f));
        root.put("UUID", new NbtTag(NbtType.INT_ARRAY, new int[] { 1, 2, 3, 4 }));

        NbtCompound abilities = new NbtCompound();
        abilities.put("flying", NbtTag.of((byte) 0));
        abilities.put("mayfly", NbtTag.of((byte) 1));
        abilities.put("instabuild", NbtTag.of((byte) 0));
        abilities.put("invulnerable", NbtTag.of((byte) 0));
        abilities.put("mayBuild", NbtTag.of((byte) 1));
        abilities.put("flySpeed", NbtTag.of(0.05f));
        abilities.put("walkSpeed", NbtTag.of(0.1f));
        root.put("abilities", NbtTag.of(abilities));

        root.put("Inventory", NbtTag.of(new NbtList(NbtType.COMPOUND, List.of(
                NbtTag.of(sword(0)),
                NbtTag.of(simpleItem("minecraft:cooked_beef", 32, 1)),
                NbtTag.of(shulker(9)),
                NbtTag.of(bundle(10)),
                NbtTag.of(simpleItem("minecraft:netherite_helmet", 1, 103)),
                NbtTag.of(simpleItem("minecraft:shield", 1, -106))))));

        root.put("EnderItems", NbtTag.of(new NbtList(NbtType.COMPOUND, List.of(
                NbtTag.of(simpleItem("minecraft:diamond", 64, 0)),
                NbtTag.of(simpleItem("minecraft:elytra", 1, 1))))));

        NbtCompound speed = new NbtCompound();
        speed.put("id", NbtTag.of("minecraft:speed"));
        speed.put("amplifier", NbtTag.of((byte) 1));
        speed.put("duration", NbtTag.of(3600));
        speed.put("ambient", NbtTag.of((byte) 0));
        speed.put("show_particles", NbtTag.of((byte) 1));
        root.put("active_effects", NbtTag.of(new NbtList(NbtType.COMPOUND, List.of(NbtTag.of(speed)))));

        NbtCompound maxHealth = new NbtCompound();
        maxHealth.put("id", NbtTag.of("minecraft:max_health"));
        maxHealth.put("base", NbtTag.of(20.0D));
        NbtCompound luck = new NbtCompound();
        luck.put("id", NbtTag.of("minecraft:luck"));
        luck.put("base", NbtTag.of(3.0D));
        root.put("attributes", NbtTag.of(new NbtList(NbtType.COMPOUND,
                List.of(NbtTag.of(maxHealth), NbtTag.of(luck)))));

        NbtCompound respawn = new NbtCompound();
        respawn.put("pos", new NbtTag(NbtType.INT_ARRAY, new int[] { 10, 70, 20 }));
        respawn.put("dimension", NbtTag.of("minecraft:overworld"));
        respawn.put("forced", NbtTag.of((byte) 0));
        root.put("respawn", NbtTag.of(respawn));

        NbtCompound death = new NbtCompound();
        death.put("dimension", NbtTag.of("minecraft:the_nether"));
        death.put("pos", new NbtTag(NbtType.INT_ARRAY, new int[] { -40, 31, 8 }));
        root.put("LastDeathLocation", NbtTag.of(death));

        NbtCompound recipeBook = new NbtCompound();
        recipeBook.put("recipes", NbtTag.of(new NbtList(NbtType.STRING, List.of(
                NbtTag.of("minecraft:stone_pickaxe"),
                NbtTag.of("minecraft:torch")))));
        root.put("recipeBook", NbtTag.of(recipeBook));

        NbtCompound bukkitValues = new NbtCompound();
        bukkitValues.put("boxcore:points", NbtTag.of(42));
        bukkitValues.put("boxcore:home", NbtTag.of("spawn"));
        root.put("BukkitValues", NbtTag.of(bukkitValues));

        root.put("Tags", NbtTag.of(new NbtList(NbtType.STRING,
                List.of(NbtTag.of("vip"), NbtTag.of("quest.started")))));

        NbtCompound bukkit = new NbtCompound();
        bukkit.put("lastKnownName", NbtTag.of("Notch"));
        bukkit.put("firstPlayed", NbtTag.of(1_600_000_000_000L));
        bukkit.put("lastPlayed", NbtTag.of(1_700_000_000_000L));
        root.put("bukkit", NbtTag.of(bukkit));

        NbtCompound paper = new NbtCompound();
        paper.put("LastLogin", NbtTag.of(1_700_000_000_000L));
        paper.put("LastSeen", NbtTag.of(1_700_000_100_000L));
        root.put("Paper", NbtTag.of(paper));

        return root;
    }

    /** A named, enchanted, part-worn sword in the modern component layout. */
    private static NbtCompound sword(int slot) {
        NbtCompound item = new NbtCompound();
        item.put("id", NbtTag.of("minecraft:diamond_sword"));
        item.put("count", NbtTag.of(1));
        item.put("Slot", NbtTag.of((byte) slot));

        NbtCompound enchantments = new NbtCompound();
        enchantments.put("minecraft:sharpness", NbtTag.of(5));
        enchantments.put("minecraft:unbreaking", NbtTag.of(3));

        NbtCompound components = new NbtCompound();
        components.put("minecraft:damage", NbtTag.of(120));
        components.put("minecraft:custom_name", NbtTag.of("{\"text\":\"Excalibur\"}"));
        components.put("minecraft:enchantments", NbtTag.of(enchantments));
        item.put("components", NbtTag.of(components));
        return item;
    }

    /** A shulker box with two stacks inside it. */
    private static NbtCompound shulker(int slot) {
        NbtCompound item = new NbtCompound();
        item.put("id", NbtTag.of("minecraft:shulker_box"));
        item.put("count", NbtTag.of(1));
        item.put("Slot", NbtTag.of((byte) slot));

        List<NbtTag> inside = new ArrayList<>();
        inside.add(NbtTag.of(containerEntry(0, "minecraft:redstone", 64)));
        inside.add(NbtTag.of(containerEntry(1, "minecraft:tnt", 16)));

        NbtCompound components = new NbtCompound();
        components.put("minecraft:container", NbtTag.of(new NbtList(NbtType.COMPOUND, inside)));
        item.put("components", NbtTag.of(components));
        return item;
    }

    /**
     * A bundle holding a shulker box holding one nether star — two levels of
     * nesting, and a different component at each, which is what a search has to
     * cope with before anyone believes it when it says "not found".
     */
    private static NbtCompound bundle(int slot) {
        NbtCompound star = new NbtCompound();
        star.put("id", NbtTag.of("minecraft:nether_star"));
        star.put("count", NbtTag.of(1));

        NbtCompound inner = new NbtCompound();
        inner.put("id", NbtTag.of("minecraft:shulker_box"));
        inner.put("count", NbtTag.of(1));
        NbtCompound innerComponents = new NbtCompound();
        NbtCompound entry = new NbtCompound();
        entry.put("slot", NbtTag.of(0));
        entry.put("item", NbtTag.of(star));
        innerComponents.put("minecraft:container",
                NbtTag.of(new NbtList(NbtType.COMPOUND, List.of(NbtTag.of(entry)))));
        inner.put("components", NbtTag.of(innerComponents));

        NbtCompound item = new NbtCompound();
        item.put("id", NbtTag.of("minecraft:bundle"));
        item.put("count", NbtTag.of(1));
        item.put("Slot", NbtTag.of((byte) slot));
        NbtCompound components = new NbtCompound();
        components.put("minecraft:bundle_contents",
                NbtTag.of(new NbtList(NbtType.COMPOUND, List.of(NbtTag.of(inner)))));
        item.put("components", NbtTag.of(components));
        return item;
    }

    private static NbtCompound containerEntry(int slot, String id, int count) {
        NbtCompound entry = new NbtCompound();
        entry.put("slot", NbtTag.of(slot));
        NbtCompound item = new NbtCompound();
        item.put("id", NbtTag.of(id));
        item.put("count", NbtTag.of(count));
        entry.put("item", NbtTag.of(item));
        return entry;
    }

    public static NbtCompound simpleItem(String id, int count, int slot) {
        NbtCompound item = new NbtCompound();
        item.put("id", NbtTag.of(id));
        item.put("count", NbtTag.of(count));
        item.put("Slot", NbtTag.of((byte) slot));
        return item;
    }

    private static NbtTag doubles(double... values) {
        List<NbtTag> tags = new ArrayList<>();
        for (double value : values) {
            tags.add(NbtTag.of(value));
        }
        return NbtTag.of(new NbtList(NbtType.DOUBLE, tags));
    }

    private static NbtTag floats(float... values) {
        List<NbtTag> tags = new ArrayList<>();
        for (float value : values) {
            tags.add(NbtTag.of(value));
        }
        return NbtTag.of(new NbtList(NbtType.FLOAT, tags));
    }

    /** The stats file a server would have written for the same player. */
    public static String statsJson() {
        return "{\"stats\":{"
                + "\"minecraft:custom\":{\"minecraft:play_time\":1728000,\"minecraft:jump\":4210},"
                + "\"minecraft:mined\":{\"minecraft:stone\":48210,\"minecraft:diamond_ore\":37},"
                + "\"minecraft:killed\":{\"minecraft:zombie\":812}"
                + "},\"DataVersion\":4189}";
    }

    /** The advancements file to match. */
    public static String advancementsJson() {
        return "{"
                + "\"minecraft:story/root\":{\"criteria\":{\"crafting_table\":"
                + "\"2026-01-01 10:00:00 +0000\"},\"done\":true},"
                + "\"minecraft:story/mine_diamond\":{\"criteria\":{},\"done\":false},"
                + "\"minecraft:recipes/misc/torch\":{\"criteria\":{\"has_coal\":"
                + "\"2026-01-01 10:05:00 +0000\"},\"done\":true},"
                + "\"DataVersion\":4189}";
    }
}
