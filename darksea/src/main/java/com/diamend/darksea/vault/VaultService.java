package com.diamend.darksea.vault;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.util.Bearing;
import com.diamend.darksea.util.Pos;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Vault cracking. An island's richest chests — the ones the loot engine
 * elects as vaults — sit sealed until someone finds the lever the plugin
 * plants elsewhere on the island and throws it. The lever is deliberately not
 * next to the loot: it goes on the island's far spawn point, so cracking a
 * castle means clearing your way across it rather than sprinting to a chest.
 *
 * <p>The important rule is that cracked stays cracked. A soft reset re-pastes
 * the island from its shape, which would otherwise restore an un-thrown lever
 * every cycle and turn vaults into a six-hour farm; instead the state lives on
 * {@link IslandInstance}, survives the re-paste, and is re-applied to the
 * freshly pasted lever. Only a full reset — which drops the island registry
 * entirely — puts the seals back, which makes full-reset day worth showing up
 * for.
 */
public final class VaultService implements Listener {

    private final DarkSeaPlugin plugin;

    public VaultService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Installation
    // ------------------------------------------------------------------

    /**
     * Plants (or re-plants) the island's vault lever after a paste. Islands
     * with no elected vaults get nothing — there is nothing to unlock — and a
     * lever on an already-cracked island comes back already thrown, so a
     * returning player sees the state they left.
     */
    public void install(World world, IslandInstance island) {
        if (island.vaultChests().isEmpty()) {
            return;
        }
        Pos spot = leverSpot(world, island);
        if (spot == null) {
            plugin.getLogger().warning("Island " + island.id()
                    + ": no standing ground for a vault lever — vaults left open");
            island.setVaultsCracked(true);
            return;
        }
        island.setVaultLever(spot);
        Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());

        // Stand it on a redstone lamp. A bare lever on bare ground reads as
        // scenery; a lamp reads as a mechanism somebody installed. It also
        // does the work for free — the lever powers the block it is attached
        // to, so the lamp is dark while the vaults are sealed and lit the
        // moment they are open, visible clear across the island at night.
        Block base = world.getBlockAt(spot.x(), spot.y() - 1, spot.z());
        base.setType(Material.REDSTONE_LAMP, false);

        block.setType(Material.LEVER, false);
        BlockData data = block.getBlockData();
        if (data instanceof Switch lever) {
            lever.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
            lever.setPowered(island.vaultsCracked());
            block.setBlockData(lever, false);
        }
    }

    /**
     * Where the lever goes: the mob spawn point furthest from the first vault
     * chest, which on every shape puts it deep in the structure rather than
     * beside the prize. Falls back to the island origin's column. Returns the
     * air block sitting on solid ground, or null if there isn't one.
     */
    private Pos leverSpot(World world, IslandInstance island) {
        Pos vault = island.vaultChest();
        // Two passes. The first only considers markers standing on open floor,
        // because "furthest from the vault" on a castle is a battlement, and a
        // lever on a lamp wedged between two crenellations is what the sixth
        // playtest photographed: it reads as scenery, it is a nuisance to
        // click, and it is nowhere a player would look. The second pass drops
        // that requirement so a cramped shape still gets a lever at all.
        Pos best = pickAnchor(world, island, vault, true);
        if (best == null) {
            best = pickAnchor(world, island, vault, false);
        }
        Pos anchor = best != null ? best : island.origin();
        return standingSpot(world, anchor);
    }

    private Pos pickAnchor(World world, IslandInstance island, Pos vault, boolean openFloorOnly) {
        Pos best = null;
        double bestDist = -1.0;
        for (Pos point : island.spawnPoints()) {
            Pos spot = standingSpot(world, point);
            if (spot == null || (openFloorOnly && !onOpenFloor(world, spot))) {
                continue;
            }
            double dist = vault == null ? 0.0
                    : point.distanceSquared2D(vault.x(), vault.z());
            if (dist > bestDist) {
                bestDist = dist;
                best = point;
            }
        }
        return best;
    }

    /**
     * Whether this is floor a player would walk over rather than a perch: at
     * least three of the four neighbouring columns must be standable too.
     * A crenellation gap fails on the walls either side of it, and a roof edge
     * or a jutting stone fails on the nothing underneath it.
     */
    private boolean onOpenFloor(World world, Pos spot) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int walkable = 0;
        for (int[] step : around) {
            Block here = world.getBlockAt(spot.x() + step[0], spot.y(), spot.z() + step[1]);
            Block below = world.getBlockAt(spot.x() + step[0], spot.y() - 1, spot.z() + step[1]);
            if (here.isPassable() && below.getType().isSolid()) {
                walkable++;
            }
        }
        return walkable >= 3;
    }

    /**
     * The first air block with solid ground under it, searching down from the
     * anchor. Levers need something to stand on and the anchor is a mob spawn
     * marker, which the placer cleared to air — so this normally resolves one
     * or two blocks down.
     */
    private Pos standingSpot(World world, Pos anchor) {
        for (int y = anchor.y() + 2; y >= anchor.y() - 4; y--) {
            if (world.getMinHeight() > y - 1) {
                break;
            }
            Block here = world.getBlockAt(anchor.x(), y, anchor.z());
            Block below = world.getBlockAt(anchor.x(), y - 1, anchor.z());
            if (here.getType().isAir() && below.getType().isSolid()) {
                return new Pos(anchor.x(), y, anchor.z());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Cracking and sealing
    // ------------------------------------------------------------------

    /**
     * Two jobs on one event: throwing a vault lever cracks its island, and
     * opening a sealed vault chest is refused. Runs at LOW so the island
     * protection listener still has the last word on everything else.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!plugin.isDarkSea(block.getWorld())) {
            return;
        }
        IslandInstance island = plugin.registry()
                .islandAtColumn(block.getX(), block.getZ(), 8);
        if (island == null) {
            return;
        }
        Pos clicked = new Pos(block.getX(), block.getY(), block.getZ());
        if (block.getType() == Material.LEVER && clicked.equals(island.vaultLever())) {
            crack(event.getPlayer(), island);
            return;
        }
        if (block.getType() == Material.CHEST
                && !island.vaultsCracked()
                && isVault(island, clicked)) {
            event.setCancelled(true);
            sendSealedHint(event.getPlayer(), island);
            event.getPlayer().playSound(block.getLocation(),
                    Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.8f);
        }
    }


    /**
     * Refuses a sealed vault AND says where the lever is.
     *
     * A floor lever planted at the far side of a 70-block island is not
     * something a player finds by looking; the second live test called
     * hunting for it a pain, and the fiction never suffered for the hint —
     * the harbour lords' garrison would have known where their own bar was.
     * The direction is given at the moment it is wanted, which is the moment
     * the chest refuses to open.
     */
    private void sendSealedHint(Player player, IslandInstance island) {
        Pos lever = island.vaultLever();
        if (lever == null) {
            plugin.messages().send(player, "vault-sealed");
            return;
        }
        double dx = lever.x() - player.getLocation().getX();
        double dz = lever.z() - player.getLocation().getZ();
        plugin.messages().send(player, "vault-sealed-hint",
                "direction", Bearing.compass(dx, dz),
                "distance", String.valueOf(Bearing.blocks(dx, dz)));
    }

    /**
     * A double chest registers under one of its halves; a player may well
     * click the other. Treat either half as the vault.
     */
    private boolean isVault(IslandInstance island, Pos clicked) {
        List<Pos> vaults = island.vaultChests();
        if (vaults.contains(clicked)) {
            return true;
        }
        for (Pos vault : vaults) {
            if (vault.y() == clicked.y()
                    && Math.abs(vault.x() - clicked.x()) + Math.abs(vault.z() - clicked.z()) == 1) {
                return true;
            }
        }
        return false;
    }

    /** Throws the lever for real: permanent for this island, until a full reset. */
    private void crack(Player player, IslandInstance island) {
        if (island.vaultsCracked()) {
            plugin.messages().send(player, "vault-already-cracked");
            return;
        }
        island.setVaultsCracked(true);
        plugin.registry().save();
        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.2f, 0.9f);
        plugin.messages().send(player, "vault-cracked",
                "count", String.valueOf(island.vaultChests().size()));
    }
}
