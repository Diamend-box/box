package com.diamend.boxcore.travel;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.gui.TravelMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.util.Items;
import com.diamend.boxcore.util.Sounds;
import com.diamend.boxcore.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** A travel item as configured: what it grants, and how it looks. */
    public record ItemDefinition(TravelItems.Payload payload, TravelItems.Appearance appearance) {
    }

    private final BoxCorePlugin plugin;
    private final WarpManager warps;
    private final CombatTagger combat;
    private final TravelService travel;
    private final TravelItems items;
    private final Map<String, ItemDefinition> definitions = new LinkedHashMap<>();

    private boolean announceDiscovery = true;
    private boolean sounds = true;
    private Order order = Order.FOUND;
    private boolean snapCentre = true;
    private Facing facing = Facing.NEAREST;

    public TravelModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
        this.warps = new WarpManager(plugin);
        this.combat = new CombatTagger();
        this.travel = new TravelService(plugin, combat);
        this.items = new TravelItems(plugin);
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
        plugin.modules().listen(this, new TravelListener(plugin, this));
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
        snapCentre = plugin.getConfig().getBoolean("travel.snap.centre", true);
        facing = Facing.parse(plugin.getConfig().getString("travel.snap.facing", "nearest"));
        travel.setSounds(sounds);
        warps.load();
        loadItems();
    }

    // ------------------------------------------------------------------
    // Travel items
    // ------------------------------------------------------------------

    private void loadItems() {
        definitions.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("travel.items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String where = "travel.items." + id;
            String warpId = entry.getString("warp", "");
            if (warpId == null || warpId.isBlank()) {
                plugin.getLogger().warning(where + ".warp names no destination, skipping.");
                continue;
            }
            warpId = warpId.trim().toLowerCase(Locale.ROOT);
            TravelItems.Mode mode = TravelItems.Mode.parse(entry.getString("mode", "travel"));
            if (mode == TravelItems.Mode.TRAVEL && TravelItems.ANY.equals(warpId)) {
                // A ticket has to know where it is taking you before it is
                // spent. "Anywhere" only makes sense for the kind that unlocks.
                plugin.getLogger().warning(where + ": warp 'any' only works with mode 'unlock',"
                        + " skipping.");
                continue;
            }
            definitions.put(id.toLowerCase(Locale.ROOT), new ItemDefinition(
                    new TravelItems.Payload(id, warpId, mode),
                    readAppearance(entry.getConfigurationSection("item"), mode)));
        }
    }

    private TravelItems.Appearance readAppearance(ConfigurationSection item, TravelItems.Mode mode) {
        Material fallback = mode == TravelItems.Mode.UNLOCK ? Material.FILLED_MAP : Material.PAPER;
        if (item == null) {
            return new TravelItems.Appearance(fallback, null, null, 0, true);
        }
        List<String> lore = item.isList("lore") ? item.getStringList("lore") : null;
        return new TravelItems.Appearance(
                Items.material(item.getString("material"), fallback),
                item.getString("name"),
                lore == null || lore.isEmpty() ? null : lore,
                Math.max(0, item.getInt("model-data", 0)),
                item.getBoolean("glow", true));
    }

    public TravelItems items() {
        return items;
    }

    public Set<String> itemIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    /** Builds a configured travel item, or null when no such item is configured. */
    public ItemStack createItem(String id, int amount) {
        ItemDefinition definition = definitions.get(id == null
                ? ""
                : id.trim().toLowerCase(Locale.ROOT));
        if (definition == null) {
            return null;
        }
        return items.create(definition.payload(), definition.appearance(), amount,
                warps.get(definition.payload().warpId()));
    }

    /**
     * Spends a travel item, or explains why it can't be spent.
     *
     * <p>Nothing is taken from the player unless the item actually did
     * something. A ticket to a destination that has since been deleted, or one
     * used in combat, stays in the inventory to be used later or refunded — the
     * alternative is a player who paid for a trip, didn't get one, and has
     * nothing left to show a staff member.
     *
     * @return whether to take the item now. A ticket answers no even when it
     *         started a trip, because it pays for itself on arrival instead.
     */
    public boolean useItem(Player player, TravelItems.Payload payload) {
        if (player == null || payload == null) {
            return false;
        }
        return payload.mode() == TravelItems.Mode.UNLOCK
                ? unlock(player, payload)
                : ticket(player, payload);
    }

    /** Puts one destination — or all of them — on the player's list for good. */
    private boolean unlock(Player player, TravelItems.Payload payload) {
        List<Warp> targets = new ArrayList<>();
        if (payload.isAny()) {
            targets.addAll(visibleTo(player));
        } else {
            Warp warp = warps.get(payload.warpId());
            if (warp == null) {
                plugin.messages().send(player, "travel-unavailable", "warp", payload.warpId());
                return false;
            }
            targets.add(warp);
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int found = 0;
        for (Warp warp : targets) {
            if (profile.discoverWarp(warp.id())) {
                found++;
                plugin.messages().send(player, "travel-discovered",
                        "warp", Text.plain(warp.display()));
            }
        }
        if (found == 0) {
            // Already knew everywhere it would have shown them. Saying so and
            // keeping the item is better than eating it for nothing.
            plugin.messages().send(player, "travel-item-known");
            return false;
        }
        if (sounds) {
            Sounds.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        }
        return true;
    }

    /**
     * Starts a one-trip ticket, spending it only once the player arrives.
     *
     * <p>The ticket stands in for the warp's permission — that is what buying
     * one is for — but not for the combat tag, which is the module's whole
     * safety model and not something a shop should be able to sell around.
     */
    private boolean ticket(Player player, TravelItems.Payload payload) {
        Warp warp = warps.get(payload.warpId());
        if (warp == null) {
            plugin.messages().send(player, "travel-unavailable", "warp", payload.warpId());
            return false;
        }
        TravelService.Outcome outcome = travel.begin(player, warp,
                () -> consumeOne(player, payload), true);
        if (outcome == TravelService.Outcome.ALREADY_TRAVELLING) {
            plugin.messages().send(player, "travel-item-busy");
        }
        // Whether it started or not, nothing is taken here. A trip that started
        // pays for itself on arrival; one that didn't costs nothing.
        return false;
    }

    /**
     * Takes one matching travel item out of the player's inventory.
     *
     * <p>Searched for rather than held onto, because a warmup is long enough to
     * move the item to another slot, and a stack that has been moved is not the
     * same object any more.
     */
    private void consumeOne(Player player, TravelItems.Payload payload) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack held = contents[slot];
            TravelItems.Payload carried = items.read(held);
            if (carried == null
                    || !carried.warpId().equalsIgnoreCase(payload.warpId())
                    || carried.mode() != payload.mode()) {
                continue;
            }
            int left = held.getAmount() - 1;
            // Written back by slot rather than by mutating what getContents
            // handed over, so this doesn't depend on that array being live.
            if (left <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                held.setAmount(left);
                player.getInventory().setItem(slot, held);
            }
            return;
        }
    }

    /**
     * Which way a warp faces you when you arrive.
     *
     * <p>Staff place a destination by standing where it should be, and standing
     * squarely on a block looking at a cardinal direction is fiddly to do by
     * hand. Snapping means the arrival always looks deliberate without anyone
     * having to line themselves up first.
     */
    public enum Facing {
        /** However the placer happened to be looking. */
        KEEP(Float.NaN),
        /** The nearest quarter turn to however they were looking. */
        NEAREST(Float.NaN),
        SOUTH(0f),
        WEST(90f),
        NORTH(180f),
        EAST(-90f);

        private final float yaw;

        Facing(float yaw) {
            this.yaw = yaw;
        }

        public static Facing parse(String raw) {
            if (raw == null) {
                return NEAREST;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "keep", "off", "none", "exact" -> KEEP;
                case "north" -> NORTH;
                case "east" -> EAST;
                case "south" -> SOUTH;
                case "west" -> WEST;
                default -> NEAREST;
            };
        }

        /** The next one round, for a menu button that cycles. */
        public Facing next() {
            Facing[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public String display() {
            return switch (this) {
                case KEEP -> "As placed";
                case NEAREST -> "Nearest quarter turn";
                default -> name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
            };
        }
    }

    /**
     * Where a warp placed from this player's feet should actually sit.
     *
     * <p>Every path that places or moves a destination goes through here, so a
     * warp set by command lands in the same place as one set from the menu.
     */
    public Location placementFor(Player player) {
        return snap(player.getLocation().clone());
    }

    /** Applies the configured centring and facing to a location. */
    public Location snap(Location raw) {
        if (raw == null) {
            return null;
        }
        Location at = raw.clone();
        if (snapCentre) {
            // Feet on the middle of the block, not wherever in it they stopped.
            at.setX(at.getBlockX() + 0.5);
            at.setZ(at.getBlockZ() + 0.5);
            at.setY(at.getBlockY());
        }
        if (facing == Facing.NEAREST) {
            at.setYaw(Math.round(at.getYaw() / 90f) * 90f);
            at.setPitch(0f);
        } else if (facing != Facing.KEEP) {
            at.setYaw(facing.yaw);
            at.setPitch(0f);
        }
        return at;
    }

    public boolean snapCentre() {
        return snapCentre;
    }

    public Facing facing() {
        return facing;
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
