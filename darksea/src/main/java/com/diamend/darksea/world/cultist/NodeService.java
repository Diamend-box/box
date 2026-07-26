package com.diamend.darksea.world.cultist;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.util.Pos;
import com.diamend.darksea.world.ManagedWorld;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The ore veins: placing them, mining them, growing them back, and making sure
 * they are the only thing in the caves anyone can touch.
 *
 * <p>Three rules, all of them deliberate.
 *
 * <p><b>The whole vein comes back at once</b>, on a cooldown measured from the
 * last block taken out of it. A half-mined vein therefore returns whole rather
 * than dribbling blocks back individually, which makes the decision at a vein
 * "do I have time to clear this before someone turns up" instead of "do I chip
 * two blocks off it every minute".
 *
 * <p><b>Nothing else in the caves is breakable or placeable.</b> The dimension
 * is a designed space, not a canvas. That also stops the obvious ways around a
 * vein: tunnelling up to one from underneath, or walling one off so nobody
 * else can work it.
 *
 * <p><b>Mining never yields the block.</b> A vein block is scenery that hands
 * out an item; the player gets the configured drop and the block becomes
 * ordinary rock until it regrows.
 */
public final class NodeService extends BukkitRunnable implements Listener {

    /** How often the regrow pass runs. Veins take tens of minutes; this is ample. */
    public static final long REGROW_PERIOD_TICKS = 20L * 30;

    private final DarkSeaPlugin plugin;
    private final NodeRegistry registry;

    public NodeService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new NodeRegistry(new File(plugin.getDataFolder(), "nodes.yml"),
                plugin.getLogger());
        this.registry.load();
    }

    public NodeRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    /**
     * Scatters and builds every vein, if none are registered yet. Called after
     * the caves world is created; on later starts the registry already has
     * them and this does nothing.
     */
    public int placeAllIfEmpty(World caves) {
        if (caves == null || !registry.isEmpty()) {
            return 0;
        }
        return regenerate(caves);
    }

    /** Wipes the veins and scatters a fresh set — {@code /ds caves reveins}. */
    public int regenerate(World caves) {
        if (caves == null) {
            return 0;
        }
        OreTables tables = plugin.oreTables();
        var settings = plugin.settings().cultist();
        CultistCarve carve = new CultistCarve(caves.getSeed(), settings.floorY(),
                settings.roofY(), settings.halfExtent(), settings.chamberRadius());

        registry.clearAll();
        List<VeinScatter.Placement> placements =
                VeinScatter.scatter(carve, tables, caves.getSeed());
        int built = 0;
        for (int i = 0; i < placements.size(); i++) {
            VeinScatter.Placement placement = placements.get(i);
            OreType type = tables.byId(placement.typeId());
            if (type == null) {
                continue;
            }
            List<Pos> blocks = veinBlocks(carve, placement, type);
            if (blocks.isEmpty()) {
                continue;
            }
            NodeRegistry.Node node = new NodeRegistry.Node(
                    type.id() + "-" + (i + 1), type.id(),
                    new Pos(placement.x(), placement.y(), placement.z()), blocks, 0L);
            registry.add(node);
            paint(caves, node, type);
            built++;
        }
        registry.save();
        plugin.getLogger().info("Placed " + built + " ore veins in the caves");
        return built;
    }

    /**
     * The blob's world positions, keeping only offsets that land in solid rock.
     * A vein trimmed to the rock reads as ore embedded in the cave wall; one
     * that kept its air offsets would leave blocks floating in the open.
     */
    private List<Pos> veinBlocks(CultistCarve carve, VeinScatter.Placement placement,
                                 OreType type) {
        List<Pos> blocks = new ArrayList<>();
        for (OreVein.Offset offset : OreVein.grow(placement.seed(),
                type.sizeFor(placement.seed()))) {
            int x = placement.x() + offset.dx();
            int y = placement.y() + offset.dy();
            int z = placement.z() + offset.dz();
            if (!carve.inBounds(x, z) || y <= carve.floorY() || y >= carve.roofY()) {
                continue;
            }
            if (carve.isOpen(x, y, z)) {
                continue;   // would hang in mid-air
            }
            blocks.add(new Pos(x, y, z));
        }
        return blocks;
    }

    /** Writes a vein's blocks into the world. */
    private void paint(World caves, NodeRegistry.Node node, OreType type) {
        Material material = Material.matchMaterial(type.blockId());
        if (material == null) {
            plugin.getLogger().warning("ores.yml '" + type.id() + "': unknown block '"
                    + type.blockId() + "' — vein left as rock");
            return;
        }
        for (Pos pos : node.blocks()) {
            caves.getBlockAt(pos.x(), pos.y(), pos.z()).setType(material, false);
        }
    }

    // ------------------------------------------------------------------
    // Mining
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (plugin.managedWorld(block.getWorld()) != ManagedWorld.CULTIST) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode().name().equals("CREATIVE")
                && player.hasPermission("darksea.admin")) {
            return;   // an admin in creative can still edit the place
        }

        event.setCancelled(true);   // nothing in the caves drops its own block

        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        NodeRegistry.Node node = registry.nodeAt(pos);
        if (node == null) {
            plugin.messages().send(player, "caves-unbreakable");
            return;
        }
        OreType type = plugin.oreTables().byId(node.typeId());
        if (type == null) {
            return;
        }
        if (block.getType() == Material.STONE || block.getType().isAir()) {
            return;   // already mined out, waiting to regrow
        }

        block.setType(Material.STONE, false);
        node.setLastMined(System.currentTimeMillis());
        registry.save();

        ItemStack drop = DarkSeaItems.create(type.dropId(), type.dropAmount());
        if (drop == null) {
            plugin.getLogger().warning("ores.yml '" + type.id() + "': unknown drop '"
                    + type.dropId() + "'");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
        for (ItemStack spill : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), spill);
        }
    }

    /** The caves are a designed space: no building in them either. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.managedWorld(event.getBlock().getWorld()) != ManagedWorld.CULTIST) {
            return;
        }
        if (player.getGameMode().name().equals("CREATIVE")
                && player.hasPermission("darksea.admin")) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "caves-no-building");
    }

    // ------------------------------------------------------------------
    // Regrowing
    // ------------------------------------------------------------------

    @Override
    public void run() {
        World caves = plugin.worldService().caves();
        if (caves == null || registry.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (NodeRegistry.Node node : registry.all()) {
            OreType type = plugin.oreTables().byId(node.typeId());
            if (type == null || !node.isDue(now, type.cooldownMillis())) {
                continue;
            }
            Material material = Material.matchMaterial(type.blockId());
            if (material == null) {
                continue;
            }
            // The whole vein at once, including blocks nobody took — cheap, and
            // it repairs any that went missing some other way.
            for (Pos pos : node.blocks()) {
                Block block = caves.getBlockAt(pos.x(), pos.y(), pos.z());
                if (block.getType() != material) {
                    block.setType(material, false);
                }
            }
            node.setLastMined(0L);
            changed = true;
        }
        if (changed) {
            registry.save();
        }
    }
}
