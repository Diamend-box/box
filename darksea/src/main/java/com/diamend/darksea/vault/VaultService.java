package com.diamend.darksea.vault;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.island.IslandInstance;
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
        Pos best = null;
        double bestDist = -1.0;
        for (Pos point : island.spawnPoints()) {
            double dist = vault == null ? 0.0
                    : point.distanceSquared2D(vault.x(), vault.z());
            if (dist > bestDist) {
                bestDist = dist;
                best = point;
            }
        }
        Pos anchor = best != null ? best : island.origin();
        return standingSpot(world, anchor);
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
            plugin.messages().send(event.getPlayer(), "vault-sealed");
            event.getPlayer().playSound(block.getLocation(),
                    Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.8f);
        }
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
