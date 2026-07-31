package com.diamend.boxcore.travel;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Sounds;
import com.diamend.boxcore.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fast travel: a list of destinations a player finds by walking into them.
 *
 * <p>Destinations are set by staff rather than by players, so nobody can plant a
 * private exit in the middle of a contested area. Finding them is the
 * progression — a warp you have never stood at shows in the menu as somewhere
 * you haven't been, which is a reason to go looking rather than a locked door
 * with no key.
 *
 * <p>The travelling itself is deliberately interruptible; see
 * {@link TravelService} for why that is the whole point on a PvP server.
 */
public class TravelModule implements BoxModule {

    /**
     * How the menu lays destinations out.
     *
     * <p>The order is a server's choice rather than a fixed rule, because what
     * "first" should mean depends on how many places there are. A handful reads
     * best in the order staff added them; two pages of them read best with the
     * ones you can actually use at the top.
     */
    public enum Order {
        /** Places you've found first, then the rest, each group by name. */
        FOUND,
        /** By name, found or not. */
        NAME,
        /** Nearest first; other worlds last. */
        DISTANCE,
        /** However warps.yml happens to list them. */
        FILE;

        public static Order parse(String raw) {
            if (raw == null) {
                return FOUND;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "name", "alphabetical" -> NAME;
                case "distance", "nearest" -> DISTANCE;
                case "file", "config", "added" -> FILE;
                default -> FOUND;
            };
        }
    }

    private final BoxCorePlugin plugin;
    private final WarpManager warps;
    private final CombatTagger combat;
    private final TravelService travel;

    private boolean announceDiscovery = true;
    private boolean sounds = true;
    private Order order = Order.FOUND;

    public TravelModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
        this.warps = new WarpManager(plugin);
        this.combat = new CombatTagger();
        this.travel = new TravelService(plugin, combat);
    }

    @Override
    public String id() {
        return "travel";
    }

    @Override
    public String displayName() {
        return "Fast travel";
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager()
                .registerEvents(new TravelListener(plugin, this), plugin);
    }

    @Override
    public void disable() {
        travel.clear();
    }

    @Override
    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        travel.configure(plugin.getConfig().getInt("travel.warmup-seconds", 3),
                plugin.getConfig().getBoolean("travel.cancel-on-move", true));
        combat.setSeconds(plugin.getConfig().getLong("travel.combat-tag-seconds", 15L));
        announceDiscovery = plugin.getConfig().getBoolean("travel.announce-discovery", true);
        sounds = plugin.getConfig().getBoolean("travel.sounds", true);
        order = Order.parse(plugin.getConfig().getString("travel.menu-order", "found"));
        travel.setSounds(sounds);
        warps.load();
    }

    public Order order() {
        return order;
    }

    public WarpManager warps() {
        return warps;
    }

    public TravelService travel() {
        return travel;
    }

    public CombatTagger combat() {
        return combat;
    }

    // ------------------------------------------------------------------
    // Finding places
    // ------------------------------------------------------------------

    /**
     * Records any warp this player is now standing near.
     *
     * <p>Permission is checked before discovery, so a warp someone can't use
     * doesn't quietly appear in their list the first time they walk past it.
     */
    public void checkDiscovery(Player player, Location where) {
        if (player == null || where == null || warps.size() == 0) {
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        for (Warp warp : warps.all()) {
            if (profile.hasDiscovered(warp.id()) || !warp.allows(player)) {
                continue;
            }
            if (warp.isNear(where) && profile.discoverWarp(warp.id())) {
                if (announceDiscovery) {
                    plugin.messages().send(player, "travel-discovered",
                            "warp", Text.plain(warp.display()));
                }
                if (sounds) {
                    // Finding somewhere is the only progression this module has;
                    // it deserves to sound like something happened.
                    Sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
                }
            }
        }
    }

    public boolean hasDiscovered(Player player, Warp warp) {
        return plugin.profiles().get(player.getUniqueId()).hasDiscovered(warp.id());
    }

    /** Warps this player is allowed to see at all, found or not. */
    public List<Warp> visibleTo(Player player) {
        List<Warp> visible = new ArrayList<>();
        for (Warp warp : warps.all()) {
            if (warp.allows(player)) {
                visible.add(warp);
            }
        }
        return visible;
    }

    /**
     * Warps this player can see, in the order the menu should show them.
     *
     * <p>Sorting happens here rather than in the menu so the ordering is one
     * decision made once, and so a warp's position on the page means the same
     * thing on page two as it does on page one.
     */
    public List<Warp> orderedFor(Player player) {
        List<Warp> visible = visibleTo(player);
        Comparator<Warp> comparator = switch (order) {
            case FILE -> null;
            case NAME -> byName();
            case DISTANCE -> byDistance(player).thenComparing(byName());
            case FOUND -> foundFirst(player).thenComparing(byName());
        };
        if (comparator != null) {
            visible.sort(comparator);
        }
        return visible;
    }

    private Comparator<Warp> byName() {
        return Comparator.comparing(warp -> Text.plain(warp.display()).toLowerCase(Locale.ROOT));
    }

    private Comparator<Warp> foundFirst(Player player) {
        return Comparator.comparing(warp -> hasDiscovered(player, warp) ? 0 : 1);
    }

    /**
     * Nearest first. A warp in another world sorts last rather than throwing —
     * distances across worlds aren't a number Minecraft will give you.
     */
    private Comparator<Warp> byDistance(Player player) {
        Location from = player.getLocation();
        return Comparator.comparingDouble(warp -> {
            Location at = warp.location();
            if (at == null || at.getWorld() == null || from.getWorld() == null
                    || !at.getWorld().equals(from.getWorld())) {
                return Double.MAX_VALUE;
            }
            return at.distanceSquared(from);
        });
    }

    public int discoveredCount(Player player) {
        int found = 0;
        for (Warp warp : visibleTo(player)) {
            if (hasDiscovered(player, warp)) {
                found++;
            }
        }
        return found;
    }

    /** Opens the travel menu. */
    public void openFor(Player player) {
        new TravelMenu(plugin, this, 0).open(player);
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(14, Material.COMPASS,
                "<green><bold>Fast travel",
                List.of("<gray>Places you've found,", "<gray>one click away."),
                player -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("");
                    int visible = visibleTo(player).size();
                    lines.add("<gray>Found: <white>" + discoveredCount(player)
                            + "</white>/" + visible);
                    if (travel.warmupSeconds() > 0) {
                        lines.add("<gray>Warmup: <white>" + travel.warmupSeconds() + "s");
                    }
                    if (combat.isTagged(player)) {
                        lines.add("<red>You're in combat.");
                    }
                    lines.add("");
                    lines.add("<yellow>Click to open");
                    return lines;
                },
                this::openFor);
    }

    @Override
    public List<String> statusLines() {
        return List.of(warps.size() + " destination(s), "
                + travel.warmupSeconds() + "s warmup, "
                + combat.seconds() + "s combat tag");
    }
}
