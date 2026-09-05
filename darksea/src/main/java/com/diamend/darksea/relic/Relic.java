package com.diamend.darksea.relic;

import com.diamend.darksea.item.DarkSeaItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The relics — Loot 2.0's treasure band, with a life cycle. A relic drops
 * DORMANT: a collectible, nothing more. Carried back to the refugees at the
 * calm center and paid for in Chronons, it wakes — and grants its boost for
 * as long as it sits in a reliquary slot.
 *
 * <p>Six relics ship with the Dark Sea, and they are written here as
 * constants because loot.yml, shops.yml and the tests all name them: a
 * shipped relic disappearing under an admin's edit would break files that
 * cannot see the edit. Everything an admin makes in {@code /ds relic editor}
 * is a <em>custom</em> relic, lives in {@code relics-custom.yml}, and is
 * added to this registry at load — which is why this is a class with a
 * registry rather than the enum it used to be.
 *
 * <p>A custom relic is otherwise a first-class relic: it can be given, looted,
 * sold, woken, and filed in a reliquary exactly like a shipped one, because
 * every one of those paths asks {@link #byId} rather than switching on a
 * constant.
 *
 * <p>The junk-band named items (Rotted Rigging, Vigil Candle...) are NOT
 * relics — flavor salvage stays flavor salvage.
 */
public final class Relic {

    /**
     * What an awake relic does. Application lives in {@link RelicService}.
     *
     * <p>The first six are the shipped effects, each hard-wired to one number
     * in RelicService. {@link #EFFECT} is the open one: a custom relic picks a
     * potion effect and an amplifier in the editor, so an admin can build a
     * relic the plugin has never heard of without touching code.
     */
    public enum Boost {
        SPEED, BOAT, DAMAGE, ARMOR, REGEN, VECTOR, EFFECT
    }

    /** Integer PDC tag: 1 once the refugees have woken the relic. */
    public static final NamespacedKey AWAKE_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:relic_awake"));

    /** Homeward Wind: the Harbor Bell's boat speed multiplier while active. */
    public static final double BOAT_BOOST_MULTIPLIER = 1.15;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DORMANT_LINE =
            "<dark_gray>Dormant. The refugees of the calm</dark_gray>";
    private static final String DORMANT_LINE_2 =
            "<dark_gray>center may know how to wake it.</dark_gray>";

    // ------------------------------------------------------------------
    // The shipped six
    // ------------------------------------------------------------------

    public static final Relic TRADE_COIN = shipped("relic_trade_coin", 1, 50,
            Boost.SPEED, Material.GOLD_NUGGET,
            "<gold>Naxian Trade Coin</gold>",
            List.of("<gray>Struck for a market that drowned</gray>",
                    "<gray>with everyone in it.</gray>"),
            "Merchant's Stride — +10% speed");

    public static final Relic HARBOR_BELL = shipped("relic_harbor_bell", 2, 100,
            Boost.BOAT, Material.NAUTILUS_SHELL,
            "<aqua>Harbor Bell of the Naxome</aqua>",
            List.of("<gray>It rang when ships came home.</gray>",
                    "<gray>It has been quiet a long time.</gray>"),
            "Homeward Wind — +15% boat speed");

    public static final Relic MARIPHAGE_SAMPLE = shipped("relic_mariphage_sample", 3, 150,
            Boost.DAMAGE, Material.DRAGON_BREATH,
            "<dark_purple>Sealed Mariphage Sample</dark_purple>",
            List.of("<gray>Templar's stock, still stoppered.</gray>",
                    "<gray>The Order will want it back.</gray>"),
            "Plaguebearer's Edge — +1 damage");

    public static final Relic MONOLITH_SPLINTER = shipped("relic_monolith_splinter", 4, 200,
            Boost.ARMOR, Material.ECHO_SHARD,
            "<dark_purple>Monolith Splinter</dark_purple>",
            List.of("<gray>It hums when held. Not a sound —</gray>",
                    "<gray>a word, almost.</gray>"),
            "Stone's Patience — +3 armor");

    public static final Relic NAXOME_HEART = shipped("relic_naxome_heart", 4, 200,
            Boost.REGEN, Material.HEART_OF_THE_SEA,
            "<aqua>Heart of the Naxome</aqua>",
            List.of("<gray>Everything they were,</gray>",
                    "<gray>pressed into one cold stone.</gray>"),
            "Naxome's Mercy — slow regeneration");

    /** Mariphage Core exclusive — never in a chest. */
    public static final Relic MARIPHAGE_VECTOR = shipped("relic_mariphage_vector", 4, 250,
            Boost.VECTOR, Material.FERMENTED_SPIDER_EYE,
            "<dark_purple>Vector of the Mariphage</dark_purple>",
            List.of("<gray>Cut from a Core. It still wants</gray>",
                    "<gray>to spread — let it, carefully.</gray>"),
            "Carrier — your strikes infect: Poison II, Slowness");

    private static final List<Relic> BUILT_IN = List.of(
            TRADE_COIN, HARBOR_BELL, MARIPHAGE_SAMPLE,
            MONOLITH_SPLINTER, NAXOME_HEART, MARIPHAGE_VECTOR);

    /**
     * Built-ins first, then whatever relics-custom.yml last held. Rebuilt
     * whole on every {@link #setCustom}, and read without locking everywhere
     * else — a relic lookup happens once a second per player, and an
     * immutable map swapped in one write is cheaper than synchronising all of
     * them.
     */
    private static volatile List<Relic> customs = List.of();
    private static volatile Map<String, Relic> byId = index(List.of());

    // ------------------------------------------------------------------
    // Instance state
    // ------------------------------------------------------------------

    private final String id;
    private final int tier;
    private final int reviveCost;
    private final Boost boost;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final String boostLine;
    private final boolean custom;
    private final PotionEffectType effect;
    private final int effectAmplifier;

    private Relic(String id, int tier, int reviveCost, Boost boost, Material material,
                  String displayName, List<String> lore, String boostLine,
                  boolean custom, PotionEffectType effect, int effectAmplifier) {
        this.id = id;
        this.tier = tier;
        this.reviveCost = reviveCost;
        this.boost = boost;
        this.material = material;
        this.displayName = displayName;
        this.lore = List.copyOf(lore);
        this.boostLine = boostLine;
        this.custom = custom;
        this.effect = effect;
        this.effectAmplifier = effectAmplifier;
    }

    private static Relic shipped(String id, int tier, int reviveCost, Boost boost,
                                 Material material, String displayName,
                                 List<String> lore, String boostLine) {
        return new Relic(id, tier, reviveCost, boost, material, displayName, lore,
                boostLine, false, null, 0);
    }

    /**
     * A relic an admin made. Nothing here is trusted: the id is squared off to
     * something safe to put in a YAML key and a PDC tag, and every number is
     * clamped, because these values arrive from chat.
     */
    public static Relic custom(String id, int tier, int reviveCost, Boost boost,
                               Material material, String displayName,
                               List<String> lore, String boostLine,
                               PotionEffectType effect, int effectAmplifier) {
        return new Relic(sanitizeId(id), clamp(tier, 1, 5), Math.max(0, reviveCost),
                boost == null ? Boost.SPEED : boost,
                material == null || !material.isItem() ? Material.AMETHYST_SHARD : material,
                displayName == null || displayName.isBlank() ? "<white>Unnamed Relic</white>"
                        : displayName,
                lore == null ? List.of() : lore,
                boostLine == null || boostLine.isBlank() ? "an unnamed boon" : boostLine,
                true, effect, clamp(effectAmplifier, 0, 4));
    }

    /**
     * Trims an admin-typed id to the characters that are safe in a config key
     * and a namespaced tag. An id that sanitises away entirely gets a
     * placeholder rather than an empty string, so a bad id shows up in the
     * editor as something you can see and delete.
     */
    public static String sanitizeId(String raw) {
        if (raw == null) {
            return "relic";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                out.append(c);
            } else if ((c == ' ' || c == '-' || c == '.') && out.length() > 0) {
                out.append('_');
            }
        }
        String cleaned = out.toString();
        while (cleaned.endsWith("_")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "relic" : cleaned;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    // The registry
    // ------------------------------------------------------------------

    /**
     * Replaces the custom half of the registry. Called at startup and on every
     * reload and every editor edit, always with the whole list — a relic
     * removed from the file is a relic gone from the registry, which is the
     * only behaviour that makes deleting one in the editor mean anything.
     *
     * <p>A custom relic whose id collides with a shipped one, a registry item,
     * or an earlier custom is dropped. Ids are identity here: a collision does
     * not make two relics, it makes one relic that behaves like whichever the
     * lookup happened to find.
     */
    public static synchronized void setCustom(List<Relic> loaded) {
        List<Relic> kept = new ArrayList<>();
        for (Relic relic : loaded) {
            if (relic != null && !isIdTaken(relic.id, kept)) {
                kept.add(relic);
            }
        }
        customs = List.copyOf(kept);
        byId = index(customs);
    }

    /** Whether an id is already spoken for — by a shipped relic, a registry item, or {@code among}. */
    public static boolean isIdTaken(String id, List<Relic> among) {
        for (Relic relic : BUILT_IN) {
            if (relic.id.equals(id)) {
                return true;
            }
        }
        for (Relic relic : among) {
            if (relic.id.equals(id)) {
                return true;
            }
        }
        return DarkSeaItems.isRegistryId(id);
    }

    /** Whether an id is free for a new custom relic, against the live registry. */
    public static boolean isIdFree(String id) {
        return !isIdTaken(id, customs);
    }

    private static Map<String, Relic> index(List<Relic> custom) {
        Map<String, Relic> map = new LinkedHashMap<>();
        for (Relic relic : BUILT_IN) {
            map.put(relic.id, relic);
        }
        for (Relic relic : custom) {
            map.put(relic.id, relic);
        }
        return Map.copyOf(map);
    }

    /** Every relic in play: the shipped six, then the admin's own. */
    public static Relic[] values() {
        return all().toArray(new Relic[0]);
    }

    /** Every relic in play, as a list. */
    public static List<Relic> all() {
        List<Relic> all = new ArrayList<>(BUILT_IN);
        all.addAll(customs);
        return List.copyOf(all);
    }

    /** The six that ship with the plugin, which config files may safely name. */
    public static List<Relic> builtIns() {
        return BUILT_IN;
    }

    /** The admin's own, in file order. */
    public static List<Relic> customs() {
        return customs;
    }

    public static Relic byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /** The relic an item is, or null for anything else. */
    public static Relic of(ItemStack item) {
        String id = DarkSeaItems.idOf(item);
        return id != null ? byId(id) : null;
    }

    public static boolean isAwake(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Integer awake = item.getItemMeta().getPersistentDataContainer()
                .get(AWAKE_KEY, PersistentDataType.INTEGER);
        return awake != null && awake == 1;
    }

    // ------------------------------------------------------------------
    // Reading one
    // ------------------------------------------------------------------

    public String id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    /** Chronons the refugees ask to wake this relic. */
    public int reviveCost() {
        return reviveCost;
    }

    public Boost boost() {
        return boost;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public String boostLine() {
        return boostLine;
    }

    /** Whether this relic came out of relics-custom.yml rather than the code. */
    public boolean custom() {
        return custom;
    }

    /** The potion effect an {@link Boost#EFFECT} relic grants, or null. */
    public PotionEffectType effect() {
        return effect;
    }

    public int effectAmplifier() {
        return effectAmplifier;
    }

    // ------------------------------------------------------------------
    // Editing one — always a copy, never in place
    // ------------------------------------------------------------------

    private Relic with(int newTier, int newCost, Boost newBoost, Material newMaterial,
                       String newName, List<String> newLore, String newBoostLine,
                       PotionEffectType newEffect, int newAmplifier) {
        return custom(id, newTier, newCost, newBoost, newMaterial, newName, newLore,
                newBoostLine, newEffect, newAmplifier);
    }

    public Relic withTier(int newTier) {
        return with(newTier, reviveCost, boost, material, displayName, lore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withReviveCost(int newCost) {
        return with(tier, newCost, boost, material, displayName, lore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withBoost(Boost newBoost) {
        return with(tier, reviveCost, newBoost, material, displayName, lore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withMaterial(Material newMaterial) {
        return with(tier, reviveCost, boost, newMaterial, displayName, lore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withDisplayName(String newName) {
        return with(tier, reviveCost, boost, material, newName, lore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withLore(List<String> newLore) {
        return with(tier, reviveCost, boost, material, displayName, newLore, boostLine,
                effect, effectAmplifier);
    }

    public Relic withBoostLine(String newBoostLine) {
        return with(tier, reviveCost, boost, material, displayName, lore, newBoostLine,
                effect, effectAmplifier);
    }

    public Relic withEffect(PotionEffectType newEffect, int newAmplifier) {
        return with(tier, reviveCost, boost, material, displayName, lore, boostLine,
                newEffect, newAmplifier);
    }

    // ------------------------------------------------------------------
    // Creation and state change
    // ------------------------------------------------------------------

    /** A dormant relic, as chests and the Core drop them. */
    public ItemStack createDormant() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(displayName)));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(noItalic(MM.deserialize(line)));
        }
        lines.add(noItalic(MM.deserialize(DORMANT_LINE)));
        lines.add(noItalic(MM.deserialize(DORMANT_LINE_2)));
        meta.lore(lines);
        meta.getPersistentDataContainer().set(DarkSeaItems.ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** Wakes a relic in place: PDC flag plus the boost line replacing the dormant hint. */
    public void wake(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(noItalic(MM.deserialize(line)));
        }
        lines.add(noItalic(MM.deserialize("<green>Awake — " + boostLine + "</green>")));
        lines.add(noItalic(MM.deserialize("<dark_gray>Works from your reliquary.</dark_gray>")));
        meta.lore(lines);
        meta.getPersistentDataContainer().set(AWAKE_KEY, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /**
     * By id, not by reference. The registry hands out one instance per id so
     * reference equality would usually work, but an editor holds a relic
     * across a save that replaces that instance, and a stale reference
     * comparing unequal to its own saved copy is a bug that would only show up
     * as a board that will not redraw.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Relic relic && id.equals(relic.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
