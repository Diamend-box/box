package com.diamend.darksea.loot;

import com.diamend.darksea.DarkSeaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The extraction loop. Dying in the Dark Sea spills only the loot gathered on
 * that run (chest loot, mob drops — anything {@link RunLoot}-stamped); the gear
 * you sailed out with is kept and handed back on respawn. Reaching the sanctuary
 * banks the run — the stamps are cleared, and that haul is yours for good. So a
 * kill is rewarding without being ruinous: you take the other player's unbanked
 * haul, not their kit.
 *
 * <p>The mechanic is dupe-safe by construction: the run loot goes out through
 * the normal death-drop list (so it lands where the killer can grab it), while
 * the kept items are pulled out of the drops entirely and restored on respawn —
 * no item is ever in two places.
 */
public final class RunLootService implements Listener {

    /** A player's kept (non-run) inventory, held between death and respawn. */
    private record Kept(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
    }

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Kept> keptByPlayer = new ConcurrentHashMap<>();

    public RunLootService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Clears the run mark from everything a player carries; announces the total. */
    public void bank(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        int banked = 0;
        for (int i = 0; i < contents.length; i++) {
            if (RunLoot.bank(contents[i])) {
                player.getInventory().setItem(i, contents[i]);
                banked++;
            }
        }
        if (banked > 0) {
            plugin.messages().send(player, "run-loot-banked", "count", String.valueOf(banked));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.settings().combat().runLootDeath()
                || !plugin.isDarkSea(player.getWorld())) {
            return;
        }
        // Take control regardless of the server keep-inventory gamerule: only the
        // run's haul drops, the rest is restored on respawn.
        event.setKeepInventory(false);
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        ItemStack[] armor = inv.getArmorContents();
        ItemStack offhand = inv.getItemInOffHand();

        List<ItemStack> spilled = new ArrayList<>();
        stripRunLoot(storage, spilled);
        stripRunLoot(armor, spilled);
        boolean offhandSpilled = RunLoot.isRunLoot(offhand);
        if (offhandSpilled) {
            spilled.add(offhand);
        }

        // Drop only the run loot (at the death spot, for the killer); keep the rest.
        event.getDrops().clear();
        event.getDrops().addAll(spilled);
        keptByPlayer.put(player.getUniqueId(),
                new Kept(storage, armor, offhandSpilled ? null : offhand));

        if (!spilled.isEmpty()) {
            plugin.messages().send(player, "run-loot-lost", "count", String.valueOf(spilled.size()));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        restore(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Safety net: a player who died then disconnected before respawning gets
        // their kept gear back on the way in.
        restore(event.getPlayer());
    }

    private void restore(Player player) {
        Kept kept = keptByPlayer.remove(player.getUniqueId());
        if (kept == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(kept.storage());
        inv.setArmorContents(kept.armor());
        inv.setItemInOffHand(kept.offhand());
    }

    /** Moves every run-loot stack out of {@code slots} (nulling it) into {@code out}. */
    private static void stripRunLoot(ItemStack[] slots, List<ItemStack> out) {
        for (int i = 0; i < slots.length; i++) {
            if (RunLoot.isRunLoot(slots[i])) {
                out.add(slots[i]);
                slots[i] = null;
            }
        }
    }
}
