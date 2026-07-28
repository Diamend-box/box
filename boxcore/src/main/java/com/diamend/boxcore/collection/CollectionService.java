package com.diamend.boxcore.collection;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.boost.BoostType;
import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Counts what players gather and pays out collection tiers.
 *
 * <p>Tier payouts are driven off the player's stored "highest tier already
 * paid" marker rather than off the amount alone, so a single huge gain (an
 * admin {@code /box collection set}, a shulker box of loot) correctly pays out
 * every tier it crossed, exactly once.
 */
public class CollectionService {

    private final BoxCorePlugin plugin;
    private final CollectionManager collections;

    public CollectionService(BoxCorePlugin plugin, CollectionManager collections) {
        this.plugin = plugin;
        this.collections = collections;
    }

    /**
     * Credits an amount of a material to every collection that tracks it.
     *
     * <p>This is where a collections boost is applied, and the only place: it
     * covers mining, kills, fishing and harvesting alike, and it deliberately
     * does not cover {@link #set}, so an admin setting a total gets the number
     * they typed rather than a boosted one.
     */
    public void add(Player player, Material material, int amount) {
        if (player == null || amount <= 0 || material == null) {
            return;
        }
        List<ItemCollection> tracked = collections.forMaterial(material);
        if (tracked.isEmpty()) {
            return;
        }
        long credited = boosted(player, amount);
        for (ItemCollection collection : tracked) {
            addTo(player, collection, credited);
        }
    }

    /** Applies the player's collections boost, when the module is running. */
    private long boosted(Player player, long amount) {
        BoostsModule boosts = plugin.boosts();
        return boosts == null ? amount : boosts.boosted(player, BoostType.COLLECTIONS, amount);
    }

    /** Credits an amount directly to one collection. */
    public void addTo(Player player, ItemCollection collection, long amount) {
        if (player == null || collection == null || amount <= 0) {
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        long total = profile.addCollected(collection.getId(), amount);
        awardTiers(player, profile, collection, total);
    }

    /** Force-sets a collection total, paying out any tiers that were crossed. */
    public void set(Player player, PlayerProfile profile, ItemCollection collection, long amount) {
        profile.setCollected(collection.getId(), Math.max(0, amount));
        if (player != null) {
            awardTiers(player, profile, collection, Math.max(0, amount));
        } else {
            // Offline players can't be paid, so mark the tiers as settled — but
            // never move the marker backwards, or lowering someone's total and
            // letting them re-gather would pay the same tiers a second time.
            profile.setAwardedTier(collection.getId(), Math.max(
                    profile.getAwardedTier(collection.getId()),
                    collection.tierFor(Math.max(0, amount))));
        }
    }

    private void awardTiers(Player player, PlayerProfile profile, ItemCollection collection, long total) {
        int paid = profile.getAwardedTier(collection.getId());
        int reached = collection.tierFor(total);
        if (reached <= paid) {
            return;
        }
        int points = 0;
        int xp = 0;
        for (int index = paid; index < reached; index++) {
            CollectionTier tier = collection.getTiers().get(index);
            points += tier.points();
            xp += tier.xp();
            for (String command : tier.commands()) {
                runCommand(player, command);
            }
            announce(player, collection, index + 1, tier);
        }
        profile.setAwardedTier(collection.getId(), reached);

        if (points > 0) {
            plugin.grantPoints(player, points, Text.plain(collection.getDisplay()) + " collection");
        }
        if (xp > 0) {
            player.giveExp(xp);
        }
        playTierSound(player);
    }

    private void announce(Player player, ItemCollection collection, int tier, CollectionTier definition) {
        if (plugin.getConfig().getBoolean("collections.announce-tiers", true)) {
            plugin.messages().send(player, "tier-up",
                    "collection", collection.getDisplay(),
                    "tier", Text.roman(tier),
                    "amount", Text.number(definition.amount()));
        }
        if (definition.message() != null && !definition.message().isBlank()) {
            plugin.messages().sendPlain(player, definition.message());
        }
        if (plugin.getConfig().getBoolean("collections.tier-title", false)) {
            Component main = Text.parse(collection.getDisplay());
            Component sub = Text.parse("<gray>Collection tier <white>" + Text.roman(tier));
            player.showTitle(Title.title(main, sub,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
    }

    private void runCommand(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    private void playTierSound(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        } catch (Throwable ignored) {
            // Feedback only — never let it interrupt a payout.
        }
    }

    public CollectionManager collections() {
        return collections;
    }
}
