package com.diamend.darksea.npc;

import com.diamend.darksea.DarkSeaPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The outpost's people. Every NPC is an ordinary villager that has been
 * comprehensively talked out of being one: no AI, no gravity, no collision,
 * invulnerable, silent, and intercepted before vanilla can open a trading
 * screen. Right-clicking one opens {@link ShopMenuService}'s board instead.
 *
 * <p>They are deliberately not persistent entities. Nothing is written into
 * the world's entity data; the registry is the truth and every server start
 * re-spawns them from it. That means a corrupted or duplicated villager is
 * always one restart away from being fixed, and it is why placement can be a
 * runtime command rather than part of the home island's schematic.
 */
public final class NpcService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** PDC tag carrying the registry id, e.g. {@code artificer-1}. */
    public static final NamespacedKey NPC_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:npc"));

    private final DarkSeaPlugin plugin;
    private final NpcRegistry registry;

    public NpcService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new NpcRegistry(new File(plugin.getDataFolder(), "npcs.yml"),
                plugin.getLogger());
        this.registry.load();
    }

    public NpcRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /** Spawns everyone in the registry, clearing any leftovers first. */
    public int spawnAll() {
        int spawned = 0;
        for (NpcRegistry.Placement placement : registry.all()) {
            if (spawn(placement) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            plugin.getLogger().info("Spawned " + spawned + " outpost NPCs");
        }
        return spawned;
    }

    /**
     * Spawns one placement, first removing any villager already wearing its
     * tag — so a double {@code /ds npc create} or a reload never leaves two
     * artificers standing in the same doorway.
     */
    public Villager spawn(NpcRegistry.Placement placement) {
        World world = plugin.getServer().getWorld(placement.world());
        if (world == null) {
            plugin.getLogger().warning("NPC " + placement.id() + ": world '"
                    + placement.world() + "' is not loaded — not spawned");
            return null;
        }
        Location location = placement.toLocation(world);
        Chunk chunk = location.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        despawn(placement.id());

        Villager villager = world.spawn(location, Villager.class, entity -> {
            entity.getPersistentDataContainer()
                    .set(NPC_KEY, PersistentDataType.STRING, placement.id());
            harden(entity, placement.type());
        });
        return villager;
    }

    /** Everything that stops a villager behaving like a villager. */
    private void harden(Villager villager, NpcType type) {
        villager.setAI(false);
        villager.setGravity(false);          // an exact, permanent anchor
        villager.setCollidable(false);       // nobody shoves the artificer into the sea
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setPersistent(false);       // the registry is the truth, not the region file
        villager.setRemoveWhenFarAway(false);
        villager.customName(MM.deserialize(type.displayName()));
        villager.setCustomNameVisible(true);
        Villager.Profession profession = professionOf(type);
        if (profession != null) {
            villager.setProfession(profession);
        }
        villager.setVillagerLevel(5);        // no career changes, no restock chatter
    }

    private Villager.Profession professionOf(NpcType type) {
        NamespacedKey key = NamespacedKey.minecraft(type.profession());
        Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(key);
        if (profession == null) {
            plugin.getLogger().warning("Unknown villager profession '" + type.profession()
                    + "' for NPC type " + type.id());
        }
        return profession;
    }

    /** Removes the spawned entity for an id, if it is standing. */
    public void despawn(String id) {
        for (Entity entity : taggedEntities()) {
            if (id.equals(idOf(entity))) {
                entity.remove();
            }
        }
    }

    /** Removes every tagged villager — called on disable and before a respawn sweep. */
    public int despawnAll() {
        int removed = 0;
        for (Entity entity : taggedEntities()) {
            entity.remove();
            removed++;
        }
        return removed;
    }

    private List<Entity> taggedEntities() {
        List<Entity> found = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Villager.class)) {
                if (idOf(entity) != null) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    /** The registry id tagged on an entity, or null if it isn't one of ours. */
    public static String idOf(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return null;
        }
        return entity.getPersistentDataContainer().get(NPC_KEY, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    // Admin placement
    // ------------------------------------------------------------------

    /** {@code /ds npc create <type>}: registers and spawns where the admin stands. */
    public NpcRegistry.Placement create(Player admin, NpcType type) {
        NpcRegistry.Placement placement = registry.add(type, admin.getLocation());
        spawn(placement);
        return placement;
    }

    /** {@code /ds npc remove <id>}: unregisters and despawns. Null if unknown. */
    public NpcRegistry.Placement remove(String id) {
        NpcRegistry.Placement removed = registry.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            despawn(removed.id());
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Keeping them alive and out of the way
    // ------------------------------------------------------------------

    /** Right-click (main hand) opens the board; vanilla trading never happens. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = idOf(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;  // cancel both hands, but only act once
        }
        NpcRegistry.Placement placement = registry.get(id);
        if (placement == null) {
            plugin.getLogger().warning("Villager tagged '" + id
                    + "' has no registry entry — despawning");
            event.getRightClicked().remove();
            return;
        }
        plugin.shops().open(event.getPlayer(), placement);
    }

    /** Invulnerable already covers most of it; this covers the void and /kill. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (idOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Nothing targets them, so nothing drags a raid onto the outpost. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (idOf(event.getTarget()) != null) {
            event.setCancelled(true);
        }
    }

    /** No zombification, no witch conversion — the artificer stays the artificer. */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (idOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
