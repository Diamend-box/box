package com.diamend.darksea.relic;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.zone.Zone;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the relics a player is wearing in their reliquary. Once a second
 * every online player's bag is read and its slotted relics become the active
 * set; see {@link ReliquaryService}. Without a reliquary in the pack, nothing
 * is active — carrying a relic loose does nothing at all.
 * Stat boosts are transient attribute modifiers (never persisted — a relog
 * simply re-applies on the next pass), regeneration is an ambient effect
 * re-applied like the exposure task's, and BOAT/VECTOR are answered from
 * the cached active set by {@link com.diamend.darksea.boat.BoatService}
 * and the combat listener.
 *
 * Reviving happens through {@code /ds relic revive}: only in the calm
 * center's ring (where the refugees are), holding the dormant relic, for
 * its Chronon cost.
 */
public final class RelicService extends BukkitRunnable implements Listener {

    private static final NamespacedKey SPEED_KEY = key("relic_speed");
    private static final NamespacedKey DAMAGE_KEY = key("relic_damage");
    private static final NamespacedKey ARMOR_KEY = key("relic_armor");

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("darksea:" + value));
    }

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Set<Relic.Boost>> active = new ConcurrentHashMap<>();

    public RelicService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether the player currently benefits from the given boost. */
    public boolean isActive(Player player, Relic.Boost boost) {
        Set<Relic.Boost> boosts = active.get(player.getUniqueId());
        return boosts != null && boosts.contains(boost);
    }

    // ------------------------------------------------------------------
    // The once-a-second pass
    // ------------------------------------------------------------------

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // The bag is the whole rule now: what is in its slots is what
            // works, and no reliquary means no boosts at all.
            List<Relic> worn = plugin.reliquary().activeRelics(player);
            Set<Relic.Boost> boosts = boostsOf(worn);
            active.put(player.getUniqueId(), boosts);
            applyAttribute(player, Attribute.MOVEMENT_SPEED, SPEED_KEY, 0.10,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, boosts.contains(Relic.Boost.SPEED));
            applyAttribute(player, Attribute.ATTACK_DAMAGE, DAMAGE_KEY, 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, boosts.contains(Relic.Boost.DAMAGE));
            applyAttribute(player, Attribute.ARMOR, ARMOR_KEY, 3.0,
                    AttributeModifier.Operation.ADD_NUMBER, boosts.contains(Relic.Boost.ARMOR));
            if (boosts.contains(Relic.Boost.REGEN)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        60, 0, true, false, false));
            }
            applyCustomEffects(player, worn);
        }
    }

    /**
     * The EFFECT relics an admin built in {@code /ds relic editor}. Unlike the
     * shipped six these carry their own effect and level, so they cannot be
     * collapsed into the boost set — two EFFECT relics are two different
     * relics, and the set would remember only that one of them was worn.
     *
     * <p>Applied ambiently at twice the pass interval, the way REGEN is: the
     * effect lapses on its own about a second after the relic leaves the bag,
     * which is the whole of the bookkeeping.
     */
    private void applyCustomEffects(Player player, List<Relic> worn) {
        for (Relic relic : worn) {
            if (relic.boost() == Relic.Boost.EFFECT && relic.effect() != null) {
                player.addPotionEffect(new PotionEffect(relic.effect(), 40,
                        relic.effectAmplifier(), true, false, false));
            }
        }
    }

    /**
     * The active subset: bag order, duplicates of a relic count once, capped
     * at the slot count. Pure — unit tested without a server.
     */
    public static List<Relic> selectActive(List<Relic> awakeInOrder, int maxActive) {
        Set<Relic> picked = new LinkedHashSet<>();
        for (Relic relic : awakeInOrder) {
            if (picked.size() >= Math.max(0, maxActive)) {
                break;
            }
            picked.add(relic);
        }
        return List.copyOf(picked);
    }

    private static Set<Relic.Boost> boostsOf(List<Relic> relics) {
        Set<Relic.Boost> boosts = EnumSet.noneOf(Relic.Boost.class);
        for (Relic relic : relics) {
            boosts.add(relic.boost());
        }
        return boosts;
    }

    private void applyAttribute(Player player, Attribute attribute, NamespacedKey modifierKey,
                                double amount, AttributeModifier.Operation operation, boolean wanted) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = null;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getKey().equals(modifierKey)) {
                existing = modifier;
                break;
            }
        }
        if (wanted && existing == null) {
            instance.addTransientModifier(new AttributeModifier(modifierKey, amount, operation));
        } else if (!wanted && existing != null) {
            instance.removeModifier(existing);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Revival
    // ------------------------------------------------------------------

    /**
     * The artificer's anvil. Same price and same rules as {@code /ds relic
     * revive}, minus the geography check: standing at his board already proves
     * you walked to him, and he is the only one in the outpost who does this.
     */
    public void wakeAtArtificer(Player player) {
        revive(player, false);
    }

    /** /ds relic revive — all failure paths message the player and change nothing. */
    public void revive(Player player) {
        revive(player, true);
    }

    private void revive(Player player, boolean requireHome) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Relic relic = Relic.of(hand);
        if (relic == null) {
            plugin.messages().send(player, "relic-not-relic");
            return;
        }
        if (Relic.isAwake(hand)) {
            plugin.messages().send(player, "relic-already-awake");
            return;
        }
        if (requireHome && !atTheRefugees(player)) {
            plugin.messages().send(player, "relic-only-home");
            return;
        }
        int cost = relic.reviveCost();
        int have = DarkSeaItems.countChronons(player.getInventory());
        if (have < cost) {
            plugin.messages().send(player, "relic-need-chronons",
                    "cost", String.valueOf(cost), "have", String.valueOf(have));
            return;
        }
        DarkSeaItems.removeChronons(player.getInventory(), cost);
        wakeOne(player, hand, relic);
        celebrate(player, relic);
        plugin.messages().send(player, "relic-revived",
                "name", relic.displayName(), "cost", String.valueOf(cost));
    }

    /**
     * Wakes exactly ONE relic out of the held stack.
     *
     * The awake flag lives in item meta, which a stack shares — so waking a
     * stack of three woke all three for a single payment. Splitting one off
     * first keeps the price per relic honest: the rest of the stack stays
     * dormant in hand, and the woken one goes to the inventory (or the floor
     * if there is no room, which beats deleting it).
     */
    /**
     * The moment itself.
     *
     * <p>Waking used to be a line of chat and a changed tooltip, for the
     * largest one-off Chronon spend in the game. It read as a transaction that
     * had gone through rather than as something happening, and "it doesn't feel
     * special" was the verdict after the third live test.
     *
     * <p>So it takes a beat: the anvil strike lands first, then the relic
     * answers half a second later, and the title says which boon the player
     * just bought while the second sound is still ringing. The pause is the
     * whole trick — one simultaneous noise is an event, two spaced ones are a
     * cause and an effect.
     */
    private void celebrate(Player player, Relic relic) {
        Location at = player.getLocation();
        player.playSound(at, Sound.BLOCK_ANVIL_USE, 1.0f, 0.7f);
        player.getWorld().spawnParticle(Particle.ENCHANT, at.clone().add(0, 1.2, 0),
                80, 0.4, 0.7, 0.4, 1.2);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location then = player.getLocation();
            player.playSound(then, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
            player.playSound(then, Sound.ITEM_TOTEM_USE, 0.35f, 1.6f);
            player.getWorld().spawnParticle(Particle.END_ROD, then.clone().add(0, 1.0, 0),
                    40, 0.3, 0.6, 0.3, 0.05);
            player.showTitle(Title.title(
                    plugin.messages().component("relic-woken-title", "name", relic.displayName()),
                    plugin.messages().component("relic-woken-subtitle", "boost", relic.boostLine()),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000),
                            Duration.ofMillis(700))));
        }, 10L);
    }

    private void wakeOne(Player player, ItemStack hand, Relic relic) {
        if (hand.getAmount() <= 1) {
            relic.wake(hand);
            return;
        }
        ItemStack single = hand.clone();
        single.setAmount(1);
        relic.wake(single);
        hand.setAmount(hand.getAmount() - 1);
        for (ItemStack leftover : player.getInventory().addItem(single).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** The refugees live at the calm center: the sea's ring with required tier 0. */
    private boolean atTheRefugees(Player player) {
        if (!plugin.isDarkSea(player.getWorld())) {
            return false;
        }
        double dx = player.getLocation().getX() - plugin.settings().centerX();
        double dz = player.getLocation().getZ() - plugin.settings().centerZ();
        Zone zone = plugin.zoneManager().zoneAt(dx * dx + dz * dz);
        return zone.requiredTier() == 0;
    }
}
