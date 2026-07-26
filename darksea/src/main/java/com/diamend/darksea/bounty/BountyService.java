package com.diamend.darksea.bounty;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.item.DarkSeaItems;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player bounties: spend Chronons to put a price on a captain's head, and
 * whoever kills them in the Dark Sea collects the whole pool. Anyone can add to
 * a standing bounty, so a hated raider's price climbs as the sea turns on them.
 * Persisted to bounties.yml, so a restart never loses a purse.
 *
 * <p>Only a direct PvP kill in the sea pays out — an environmental drowning
 * (no {@link Player} killer) leaves the bounty standing for the next hunter.
 */
public final class BountyService implements Listener {

    private final DarkSeaPlugin plugin;
    private final BountyLedger ledger = new BountyLedger();
    private final File file;

    public BountyService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        load();
    }

    /**
     * A sponsor pays {@code amount} Chronons to raise the bounty on a target.
     * Returns false (spending nothing) if they can't cover it — the caller has
     * already validated that {@code amount > 0} and the target isn't the sponsor.
     */
    public boolean place(Player sponsor, Player target, int amount) {
        if (!DarkSeaItems.removeChronons(sponsor.getInventory(), amount)) {
            return false;
        }
        int total = ledger.place(target.getUniqueId(), amount);
        save();
        broadcast("bounty-placed",
                "sponsor", sponsor.getName(), "target", target.getName(),
                "amount", String.valueOf(amount), "total", String.valueOf(total));
        return true;
    }

    public int bountyOn(UUID target) {
        return ledger.get(target);
    }

    /** The richest {@code n} heads, highest first — for {@code /ds bounty}. */
    public List<Map.Entry<UUID, Integer>> top(int n) {
        return ledger.top(n);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)
                || !plugin.isDarkSea(victim.getWorld())) {
            return;
        }
        int purse = ledger.claim(victim.getUniqueId());
        if (purse <= 0) {
            return;
        }
        save();
        payChronons(killer, purse);
        broadcast("bounty-claimed",
                "killer", killer.getName(), "target", victim.getName(),
                "amount", String.valueOf(purse));
    }

    /** Announces to every online player — a bounty is server-wide news. */
    private void broadcast(String key, String... placeholders) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            plugin.messages().send(online, key, placeholders);
        }
    }

    /** Hands Chronons to a player, dropping any overflow at their feet. */
    private void payChronons(Player to, int amount) {
        ItemStack coins = DarkSeaItems.create(DarkSeaItems.CHRONON, amount);
        Location at = to.getLocation();
        for (ItemStack leftover : to.getInventory().addItem(coins).values()) {
            at.getWorld().dropItemNaturally(at, leftover);
        }
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("bounties");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                snapshot.put(key, section.getInt(key));
            }
        }
        ledger.load(snapshot);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : ledger.snapshot().entrySet()) {
            yaml.set("bounties." + e.getKey(), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save bounties.yml: " + ex.getMessage());
        }
    }
}
