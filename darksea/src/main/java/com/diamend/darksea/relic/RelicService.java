package com.diamend.darksea.relic;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.zone.Zone;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies awake relics riding in player inventories. Once a second every
 * online player's inventory is scanned front to back; the first
 * {@code relics.max-active} DISTINCT awake relics are the active set.
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
        int maxActive = plugin.settings().relics().maxActive();
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Relic> awake = new ArrayList<>();
            for (ItemStack item : player.getInventory().getContents()) {
                Relic relic = Relic.of(item);
                if (relic != null && Relic.isAwake(item)) {
                    awake.add(relic);
                }
            }
            Set<Relic.Boost> boosts = boostsOf(selectActive(awake, maxActive));
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
        }
    }

    /**
     * The active subset: inventory order, duplicates of a relic count once,
     * capped at {@code maxActive}. Pure — unit tested without a server.
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
        relic.wake(hand);
        plugin.messages().send(player, "relic-revived",
                "name", relic.displayName(), "cost", String.valueOf(cost));
    }

    /** The refugees live at the calm center: the sea's ring with required tier 0. */
    private boolean atTheRefugees(Player player) {
        if (!player.getWorld().getName().equals(plugin.settings().worldName())) {
            return false;
        }
        double dx = player.getLocation().getX() - plugin.settings().centerX();
        double dz = player.getLocation().getZ() - plugin.settings().centerZ();
        Zone zone = plugin.zoneManager().zoneAt(dx * dx + dz * dz);
        return zone.requiredTier() == 0;
    }
}
