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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crystal veins: placing them, growing them back, and making sure they are
 * the only thing in the caves anyone can touch. The act of extracting a block
 * lives in {@link ExtractionChannel}, which calls back into
 * {@link #extract} here when a channel completes.
 *
 * <p>Four rules, all of them deliberate.
 *
 * <p><b>The whole vein comes back at once</b>, on a cooldown measured from the
 * <em>first</em> block taken out of it. A half-mined vein returns whole and on
 * schedule rather than dribbling blocks back individually or having its
 * deadline pushed back by every further swing — so the decision at a vein is
 * "do I have time to clear this before someone turns up", and chipping at one
 * costs you nothing.
 *
 * <p><b>A vein is a geode.</b> The harvestable core is wrapped in a rind of
 * calcite and crystal buds, placed once and never touched again. That rind is
 * what makes a vein a landmark you can see across a cavern, and it means a
 * spent vein still reads as a geode waiting to refill rather than as a hole.
 *
 * <p><b>Nothing else in the caves is breakable or placeable.</b> The dimension
 * is a designed space, not a canvas. That also stops the obvious ways around a
 * vein: tunnelling up to one from underneath, or walling one off so nobody
 * else can work it.
 *
 * <p><b>Mining never yields the block.</b> A crystal block is scenery that hands
 * out an item; the player gets the configured drop and the block becomes the
 * type's matrix rock until it regrows.
 */
public final class NodeService extends BukkitRunnable implements Listener {

    /** How often the regrow pass runs. Veins take tens of minutes; this is ample. */
    public static final long REGROW_PERIOD_TICKS = 20L * 30;

    /** How long between two "you can't break that" lines for the same player. */
    private static final long REFUSAL_COOLDOWN_MILLIS = 2000L;

    private final DarkSeaPlugin plugin;
    private final NodeRegistry registry;
    private final Map<UUID, Long> refusedAt = new HashMap<>();

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
                    new Pos(placement.x(), placement.y(), placement.z()), blocks,
                    placement.seed(), 0L);
            registry.add(node);
            paintShell(caves, carve, node, type);
            paint(caves, node, type);
            built++;
        }
        registry.save();
        plugin.getLogger().info("Placed " + built + " crystal veins in the caves");
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

    /** Writes a vein's core blocks into the world. */
    private void paint(World caves, NodeRegistry.Node node, OreType type) {
        Material material = Material.matchMaterial(type.coreBlock());
        if (material == null) {
            plugin.getLogger().warning("ores.yml '" + type.id() + "': unknown block '"
                    + type.coreBlock() + "' — vein left as rock");
            return;
        }
        for (Pos pos : node.blocks()) {
            caves.getBlockAt(pos.x(), pos.y(), pos.z()).setType(material, false);
        }
    }

    /**
     * Writes the geode rind: the shell material in every solid cell touching the
     * core, with buds scattered through it.
     *
     * <p>Only ever writes into rock. A shell cell that lands in open cave is
     * skipped rather than filled, so a geode exposed in a passage wall stays
     * exposed — you can see the crystal, which is the entire point of the vein
     * being a landmark. Filling those cells would brick the vein over.
     *
     * <p>Done once at placement and never repaired. The rind is indestructible
     * like the rest of the caves, so there is nothing to repair.
     */
    private void paintShell(World caves, CultistCarve carve, NodeRegistry.Node node,
                            OreType type) {
        if (!type.hasShell()) {
            return;
        }
        Material shell = Material.matchMaterial(type.shellBlock());
        if (shell == null) {
            plugin.getLogger().warning("ores.yml '" + type.id() + "': unknown shell '"
                    + type.shellBlock() + "' — vein placed without a rind");
            return;
        }
        Material bud = type.budBlock() == null ? null : Material.matchMaterial(type.budBlock());

        Pos origin = node.origin();
        List<OreVein.Offset> core = new ArrayList<>();
        for (Pos pos : node.blocks()) {
            core.add(new OreVein.Offset(pos.x() - origin.x(), pos.y() - origin.y(),
                    pos.z() - origin.z()));
        }
        for (OreVein.Offset offset : OreVein.shell(core)) {
            int x = origin.x() + offset.dx();
            int y = origin.y() + offset.dy();
            int z = origin.z() + offset.dz();
            if (!carve.inBounds(x, z) || y <= carve.floorY() || y >= carve.roofY()) {
                continue;
            }
            if (carve.isOpen(x, y, z)) {
                continue;   // the face of the geode, left open so it can be seen
            }
            Material material = shell;
            if (bud != null && OreVein.isBud(node.seed(), offset, type.budOneIn())) {
                material = bud;
            }
            caves.getBlockAt(x, y, z).setType(material, false);
        }
    }

    // ------------------------------------------------------------------
    // Mining
    // ------------------------------------------------------------------

    /**
     * Nothing in the caves ever breaks by itself.
     *
     * <p>Crystal is taken by {@link ExtractionChannel}, on the plugin's own
     * timer, so this handler's whole job is to say no — to plain rock with a
     * message, and to crystal silently, because the channel is already running
     * and telling the player it cannot be broken would be a lie.
     */
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

        event.setCancelled(true);

        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        if (registry.nodeAt(pos) == null) {
            refuse(player);
        }
    }

    /**
     * Says no, once, quietly.
     *
     * <p>A break event fires every time a block finishes its dig timer, and
     * the amethyst clusters in a geode's rind break instantly — so holding
     * the button on one printed this line several times a second and buried
     * the chat. It belongs on the action bar anyway: it is feedback about
     * what just happened, not something worth keeping a record of.
     */
    private void refuse(Player player) {
        long now = System.currentTimeMillis();
        Long last = refusedAt.get(player.getUniqueId());
        if (last != null && now - last < REFUSAL_COOLDOWN_MILLIS) {
            return;
        }
        refusedAt.put(player.getUniqueId(), now);
        plugin.messages().actionBar(player, "caves-unbreakable");
    }

    /**
     * Takes one crystal block out of a vein: hands the player the drop, leaves
     * the matrix rock behind, and starts the regrow clock if this was the first
     * block out of this vein.
     *
     * <p>Called by {@link ExtractionChannel} when a channel runs to completion —
     * never directly from a break event, because in the caves a break event
     * means the player tried, not that the block came out.
     *
     * @return true if a block was actually extracted
     */
    public boolean extract(Player player, Block block, NodeRegistry.Node node, OreType type) {
        Material core = Material.matchMaterial(type.coreBlock());
        if (core == null || block.getType() != core) {
            return false;   // already mined out, waiting to regrow
        }
        Material matrix = Material.matchMaterial(type.matrixBlock());
        block.setType(matrix == null ? Material.BASALT : matrix, false);

        // First touch only. Every later block out of this vein leaves the
        // deadline where it is, so working a vein never delays its return.
        //
        // And only then is there anything to write. nodes.yml holds every
        // vein's full block list — some 1,500 positions — so saving it is a
        // whole-file YAML dump on the main thread. Doing that per block meant
        // one dump every mine-seconds per miner, to persist a field that had
        // not changed since the first block. Now it happens once per vein per
        // cycle: thirteen writes across a full sweep instead of a thousand.
        if (node.touch(System.currentTimeMillis())) {
            registry.save();
        }

        ItemStack drop = DarkSeaItems.create(type.dropId(), type.dropAmount());
        if (drop == null) {
            plugin.getLogger().warning("ores.yml '" + type.id() + "': unknown drop '"
                    + type.dropId() + "'");
            return true;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
        for (ItemStack spill : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), spill);
        }
        return true;
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
            Material material = Material.matchMaterial(type.coreBlock());
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
            // Clock back to unset, so the next block mined starts a fresh cycle
            // rather than the vein being instantly due again.
            node.resetClock();
            changed = true;
        }
        if (changed) {
            registry.save();
        }
    }
}
