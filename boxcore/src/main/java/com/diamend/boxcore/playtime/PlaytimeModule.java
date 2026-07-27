package com.diamend.boxcore.playtime;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.collection.ItemCollection;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.module.BoxModule;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Feeds hours played into a collection, so time spent online pays out through
 * exactly the same tier machinery as everything else.
 *
 * <p>Making it a collection rather than a per-hour trickle is what puts a
 * visible ceiling on it: the collection's last tier is the last point playtime
 * will ever pay, and a player can see that in the menu instead of assuming the
 * tap runs forever.
 *
 * <p>Hours come from the server's own {@code PLAY_ONE_MINUTE} statistic rather
 * than a counter of our own, so time banked before BoxCore was installed counts
 * and the total survives anything happening to our data.
 */
public class PlaytimeModule implements BoxModule {

    /** 20 ticks per second × 3600 seconds. */
    private static final int TICKS_PER_HOUR = 72_000;

    private final BoxCorePlugin plugin;
    private BukkitTask task;

    public PlaytimeModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "playtime";
    }

    @Override
    public String displayName() {
        return "Playtime points";
    }

    @Override
    public void enable() {
        // Every minute is plenty: the payout only ever changes on the hour.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                sync(player);
            }
        }, 200L, 1200L);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** The collection hours are recorded into, or null when there isn't one. */
    public ItemCollection collection() {
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        if (collections == null) {
            return null;
        }
        String id = plugin.getConfig().getString("playtime.collection", "");
        ItemCollection named = id.isBlank() ? null : collections.collections().get(id);
        return named != null ? named
                : collections.collections().bySource(ItemCollection.SOURCE_PLAYTIME);
    }

    /** Brings a player's recorded hours up to date, paying any tiers crossed. */
    public void sync(Player player) {
        ItemCollection collection = collection();
        if (collection == null) {
            return; // collections module off, or no playtime collection configured
        }
        long hours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile.getCollected(collection.getId()) == hours) {
            return; // nothing has changed; don't dirty the profile every minute
        }
        CollectionsModule collections = plugin.modules().get(CollectionsModule.class);
        collections.service().set(player, profile, collection, hours);
    }

    @Override
    public List<String> statusLines() {
        ItemCollection collection = collection();
        if (collection == null) {
            return List.of("<red>No playtime collection configured");
        }
        int points = 0;
        for (com.diamend.boxcore.collection.CollectionTier tier : collection.getTiers()) {
            points += tier.points();
        }
        return List.of("<gray>" + collection.tierCount() + " tier(s), <white>" + points
                + "<gray> point(s) in total");
    }
}
