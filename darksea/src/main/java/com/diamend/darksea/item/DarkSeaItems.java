package com.diamend.darksea.item;

import com.diamend.darksea.relic.Relic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The registry of every custom DarkSea item that is not sea armor or a boat
 * token: weapons, the Chronon currency, and consumables. Like {@link
 * com.diamend.darksea.armor.SeaArmor}, identity lives in the
 * PersistentDataContainer ({@link #ID_KEY}) — names are cosmetic and nothing
 * can be counterfeited with an anvil.
 *
 * Weapons carry flat attribute modifiers and deliberately no enchantments:
 * the Dark Sea is post-End content, so its gear sits between bare netherite
 * and enchanted netherite — a sidegrade with personality, never a straight
 * power jump. The numbers live in {@link #WEAPONS} where a unit test can
 * hold the balance to that promise.
 */
public final class DarkSeaItems {

    /** String PDC tag: which registry item this is (e.g. "naxian_claw"). */
    public static final NamespacedKey ID_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:item_id"));

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ------------------------------------------------------------------
    // Weapons
    // ------------------------------------------------------------------

    /**
     * One weapon line: {@code damage} and {@code speed} are the DISPLAYED
     * values (damage includes the fist's base 1.0; speed is attacks per
     * second against a base of 4.0). {@code trueDamage} marks the Mariphage
     * Stinger, whose listener replaces the whole damage calculation.
     */
    public record WeaponSpec(String id, Material material, double damage, double speed,
                             boolean trueDamage, String name, List<String> lore) {

        /** Sustained damage per second — the balance yardstick. */
        public double dps() {
            return damage * speed;
        }
    }

    public static final String SAILORS_CUTLASS = "sailors_cutlass";
    public static final String NAXIAN_CLAW = "naxian_claw";
    public static final String ENHANCED_NAXIAN_CLAW = "enhanced_naxian_claw";
    public static final String ABOMINATION_BONE = "abomination_bone";
    public static final String MARIPHAGE_STINGER = "mariphage_stinger";
    public static final String NAXIAN_BOARDING_AXE = "naxian_boarding_axe";
    public static final String HARBORGUARD_PIKE = "harborguard_pike";
    public static final String ORDER_CEREMONIAL_BLADE = "order_ceremonial_blade";

    /** Mob-drop weapons (the good ones) and chest weapons (worse on average). */
    public static final Map<String, WeaponSpec> WEAPONS = buildWeapons();

    private static Map<String, WeaponSpec> buildWeapons() {
        Map<String, WeaponSpec> map = new LinkedHashMap<>();
        // Mob drops. Reference: netherite sword 8.0 @ 1.6 = 12.8 DPS bare,
        // 17.6 with Sharpness V. Everything here lands between those posts.
        add(map, new WeaponSpec(SAILORS_CUTLASS, Material.IRON_SWORD, 6.0, 2.0, false,
                "<color:#c8d6dd>Sailor's Cutlass</color>",
                List.of("<gray>Swung long after the hand</gray>",
                        "<gray>forgot what it was defending.</gray>")));
        add(map, new WeaponSpec(NAXIAN_CLAW, Material.PRISMARINE_SHARD, 4.5, 2.8, false,
                "<color:#58c8b7>Naxian Claw</color>",
                List.of("<gray>The change starts at the fingers.</gray>")));
        add(map, new WeaponSpec(ENHANCED_NAXIAN_CLAW, Material.PRISMARINE_CRYSTALS, 6.0, 2.4, false,
                "<color:#2ee6c8>Enhanced Naxian Claw</color>",
                List.of("<gray>The change finished. What's left</gray>",
                        "<gray>cuts bone-deep and doesn't tire.</gray>")));
        add(map, new WeaponSpec(ABOMINATION_BONE, Material.BONE, 13.0, 1.1, false,
                "<color:#e0d6c0>Abomination Bone</color>",
                List.of("<gray>Too heavy to be a club.</gray>",
                        "<gray>It was a rib.</gray>")));
        // 2 hearts of TRUE damage — the combat listener strips armor and
        // resistance from the hit, so the displayed 4.0 is what always lands.
        add(map, new WeaponSpec(MARIPHAGE_STINGER, Material.BREEZE_ROD, 4.0, 1.4, true,
                "<dark_purple>Mariphage Stinger</dark_purple>",
                List.of("<gray>The plague's needle. Armor is</gray>",
                        "<gray>a suggestion it declines.</gray>",
                        "<dark_gray>2 hearts, straight through defense.</dark_gray>")));
        // Chest finds: honest tools of the drowned, worse than what the
        // cursed carry now — with one ceremonial exception worth keeping.
        add(map, new WeaponSpec(NAXIAN_BOARDING_AXE, Material.IRON_AXE, 8.0, 1.15, false,
                "<color:#b0bec5>Naxian Boarding Axe</color>",
                List.of("<gray>Harbor-watch issue. The harbor</gray>",
                        "<gray>is at the bottom of the Deep.</gray>")));
        add(map, new WeaponSpec(HARBORGUARD_PIKE, Material.TRIDENT, 8.0, 1.2, false,
                "<color:#7fb2c4>Harborguard Pike</color>",
                List.of("<gray>Throw it and you may not</gray>",
                        "<gray>get it back. They didn't.</gray>")));
        add(map, new WeaponSpec(ORDER_CEREMONIAL_BLADE, Material.GOLDEN_SWORD, 6.5, 1.8, false,
                "<color:#c9a227>Ceremonial Blade of the Order</color>",
                List.of("<gray>Meant to open throats over</gray>",
                        "<gray>an altar, not in a fight.</gray>",
                        "<gray>It manages both.</gray>")));
        return Map.copyOf(map);
    }

    private static void add(Map<String, WeaponSpec> map, WeaponSpec spec) {
        map.put(spec.id(), spec);
    }

    // ------------------------------------------------------------------
    // Currency and consumables
    // ------------------------------------------------------------------

    public static final String CHRONON = "chronon";
    public static final String TIDAL_DRAUGHT = "tidal_draught";
    public static final String SEA_SALVE = "sea_salve";
    public static final String DEEPSIGHT_TONIC = "deepsight_tonic";
    public static final String GILLWATER_PHILTER = "gillwater_philter";
    /** The apex relic hidden past the Devouring Rim — consumed once for a permanent, cooldowned death-save. */
    public static final String UNDROWNED_HEART = "undrowned_heart";

    /** Tidal Draught: boat speed bonus multiplier and duration. */
    public static final double DRAUGHT_MULTIPLIER = 1.25;
    public static final int DRAUGHT_SECONDS = 90;

    // ------------------------------------------------------------------
    // Naval weapons (behavior in com.diamend.darksea.naval)
    // ------------------------------------------------------------------

    public static final String CHAINSHOT_ARROW = "chainshot_arrow";
    public static final String HULLPIERCER_ARROW = "hullpiercer_arrow";
    public static final String HARPOON_GUN = "harpoon_gun";

    /** The stowable Dark Sea Boat — what picking a boat up hands back. */
    public static final String DARK_SEA_BOAT = "dark_sea_boat";

    /** A sunk boat's salvage: unusable until repaired at the home dry-dock. */
    public static final String BROKEN_DARK_SEA_BOAT = "broken_dark_sea_boat";

    /** Every id this registry can create, relics included. */
    public static Set<String> allIds() {
        Set<String> ids = new TreeSet<>(WEAPONS.keySet());
        ids.add(CHRONON);
        ids.add(TIDAL_DRAUGHT);
        ids.add(SEA_SALVE);
        ids.add(DEEPSIGHT_TONIC);
        ids.add(GILLWATER_PHILTER);
        ids.add(UNDROWNED_HEART);
        ids.add(CHAINSHOT_ARROW);
        ids.add(HULLPIERCER_ARROW);
        ids.add(HARPOON_GUN);
        ids.add(DARK_SEA_BOAT);
        ids.add(BROKEN_DARK_SEA_BOAT);
        for (Relic relic : Relic.values()) {
            ids.add(relic.id());
        }
        return ids;
    }

    private DarkSeaItems() {
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    /** Builds any registry item by id ({@code amount} for stackables), or null for unknown ids. */
    public static ItemStack create(String id, int amount) {
        Relic relic = Relic.byId(id);
        if (relic != null) {
            return relic.createDormant();
        }
        WeaponSpec weapon = WEAPONS.get(id);
        if (weapon != null) {
            return createWeapon(weapon);
        }
        return switch (id) {
            case CHRONON -> createChronon(amount);
            case TIDAL_DRAUGHT -> createDrinkable(id, Material.POTION, Color.fromRGB(38, 198, 218),
                    "<aqua>Tidal Draught</aqua>",
                    List.of("<gray>Drink at the tiller: the sea</gray>",
                            "<gray>pushes with you for a while.</gray>",
                            "<dark_gray>+25% boat speed, " + DRAUGHT_SECONDS + "s.</dark_gray>"),
                    List.of());
            case SEA_SALVE -> createDrinkable(id, Material.HONEY_BOTTLE, null,
                    "<color:#ffd54f>Naxian Sea-Salve</color>",
                    List.of("<gray>Kelp, honey and something the</gray>",
                            "<gray>recipe only calls 'patience'.</gray>",
                            "<dark_gray>Mends flesh, steels it briefly.</dark_gray>"),
                    List.of());
            case DEEPSIGHT_TONIC -> createDrinkable(id, Material.POTION, Color.fromRGB(126, 87, 194),
                    "<color:#b39ddb>Deepsight Tonic</color>",
                    List.of("<gray>The Naxome dove deep. This is</gray>",
                            "<gray>how they saw the bottom.</gray>"),
                    List.of(new PotionEffect(PotionEffectType.NIGHT_VISION, 8 * 60 * 20, 0)));
            case GILLWATER_PHILTER -> createDrinkable(id, Material.POTION, Color.fromRGB(0, 137, 123),
                    "<color:#4db6ac>Gillwater Philter</color>",
                    List.of("<gray>Tastes like drowning politely.</gray>"),
                    List.of(new PotionEffect(PotionEffectType.WATER_BREATHING, 8 * 60 * 20, 0)));
            case CHAINSHOT_ARROW -> createNavalArrow(id, amount, Color.fromRGB(84, 92, 100),
                    "<color:#90a4ae>Chainshot Arrow</color>",
                    List.of("<gray>Doesn't sink ships. Stops them.</gray>",
                            "<dark_gray>Cripples a hull's speed on hit.</dark_gray>"));
            case HULLPIERCER_ARROW -> createNavalArrow(id, amount, Color.fromRGB(158, 46, 46),
                    "<color:#e57373>Hullpiercer Arrow</color>",
                    List.of("<gray>Forged to open ships,</gray>",
                            "<gray>not sailors.</gray>",
                            "<dark_gray>Heavy hull damage, cuts through plating.</dark_gray>"));
            case UNDROWNED_HEART -> createUndrownedHeart();
            case HARPOON_GUN -> createHarpoonGun();
            case DARK_SEA_BOAT -> createDarkSeaBoat();
            case BROKEN_DARK_SEA_BOAT -> createBrokenDarkSeaBoat();
            default -> null;
        };
    }

    private static ItemStack createNavalArrow(String id, int amount, Color color,
                                              String name, List<String> loreLines) {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(name)));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(noItalic(MM.deserialize(line)));
        }
        meta.lore(lore);
        if (meta instanceof PotionMeta potion) {
            potion.setColor(color);
            potion.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createHarpoonGun() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize("<color:#4dd0e1>Harpoon Gun</color>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>The whalers' answer to a</gray>")),
                noItalic(MM.deserialize("<gray>ship that won't stop running.</gray>")),
                noItalic(MM.deserialize("<dark_gray>Hooks a boat and reels it in.</dark_gray>")),
                noItalic(MM.deserialize("<dark_gray>Surging snaps the line.</dark_gray>"))));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, HARPOON_GUN);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDarkSeaBoat() {
        ItemStack item = new ItemStack(Material.DARK_OAK_BOAT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize("<color:#2a7d8c>Dark Sea Boat</color>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>A vessel for the Deep. Set it</gray>")),
                noItalic(MM.deserialize("<gray>down on open water to sail.</gray>")),
                noItalic(MM.deserialize("<dark_gray>Sneak-right-click your boat to open</dark_gray>")),
                noItalic(MM.deserialize("<dark_gray>its wheel: upgrade, repair, stow.</dark_gray>"))));
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, DARK_SEA_BOAT);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBrokenDarkSeaBoat() {
        ItemStack item = new ItemStack(Material.DARK_OAK_BOAT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize("<color:#8a3a3a>Wrecked Dark Sea Boat</color>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>Splintered salvage from a hull</gray>")),
                noItalic(MM.deserialize("<gray>the Deep took under. It won't sail.</gray>")),
                noItalic(MM.deserialize("<red>Right-click at the home island to repair.</red>"))));
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, BROKEN_DARK_SEA_BOAT);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createWeapon(WeaponSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(spec.name())));
        List<Component> lore = new ArrayList<>();
        for (String line : spec.lore()) {
            lore.add(noItalic(MM.deserialize(line)));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        // Displayed damage = base 1.0 + modifier; displayed speed = base 4.0 + modifier.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                Objects.requireNonNull(NamespacedKey.fromString("darksea:weapon_damage")),
                spec.damage() - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                Objects.requireNonNull(NamespacedKey.fromString("darksea:weapon_speed")),
                spec.speed() - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, spec.id());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The Undrowned Heart: not drunk but attuned. Right-clicked once (see
     * {@link com.diamend.darksea.relic.UndrownedHeartService}) it is consumed
     * for good and the captain can no longer truly drown — the sea spits them
     * back once every couple of minutes.
     */
    private static ItemStack createUndrownedHeart() {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize("<gradient:#12d6df:#f0f9ff>The Undrowned Heart</gradient>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>Pulled from beyond the Devouring Rim,</gray>")),
                noItalic(MM.deserialize("<gray>where the sea forgot how to keep its dead.</gray>")),
                noItalic(MM.deserialize("<dark_gray>Hold it and the drowning never quite takes.</dark_gray>")),
                noItalic(MM.deserialize("<dark_aqua>Right-click to attune. Consumed forever.</dark_aqua>"))));
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, UNDROWNED_HEART);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createChronon(int amount) {
        ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize("<aqua>Chronon</aqua>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>The Naxome minted time itself.</gray>")),
                noItalic(MM.deserialize("<dark_gray>The refugees at the calm center</dark_gray>")),
                noItalic(MM.deserialize("<dark_gray>still honor it.</dark_gray>"))));
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, CHRONON);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDrinkable(String id, Material material, Color color,
                                             String name, List<String> loreLines,
                                             List<PotionEffect> effects) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(name)));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(noItalic(MM.deserialize(line)));
        }
        meta.lore(lore);
        if (meta instanceof PotionMeta potion) {
            if (color != null) {
                potion.setColor(color);
            }
            for (PotionEffect effect : effects) {
                potion.addCustomEffect(effect, true);
            }
            potion.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** The registry id of an item, or null for vanilla/untagged stacks. */
    public static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(ID_KEY, PersistentDataType.STRING);
    }

    public static boolean isChronon(ItemStack item) {
        return CHRONON.equals(idOf(item));
    }

    /** Total Chronons across an inventory (PDC-tagged stacks only). */
    public static int countChronons(Inventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (isChronon(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Removes {@code amount} Chronons from the inventory. Returns false and
     * removes nothing if the inventory holds fewer than that.
     */
    public static boolean removeChronons(Inventory inventory, int amount) {
        if (amount <= 0 || countChronons(inventory) < amount) {
            return amount <= 0;
        }
        int remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isChronon(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take >= item.getAmount()) {
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        return true;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
