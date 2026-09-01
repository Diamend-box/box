package com.diamend.darksea;

import com.diamend.darksea.armor.ProtectionService;
import com.diamend.darksea.boat.BoatMenuService;
import com.diamend.darksea.boat.BoatService;
import com.diamend.darksea.bounty.BountyService;
import com.diamend.darksea.combat.NaxCombatListener;
import com.diamend.darksea.combat.SeaGuardListener;
import com.diamend.darksea.command.DarkSeaCommand;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.config.Messages;
import com.diamend.darksea.data.PlayerDataStore;
import com.diamend.darksea.diag.LogTail;
import com.diamend.darksea.diag.StartupReport;
import com.diamend.darksea.island.IslandPlacer;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.item.ConsumableService;
import com.diamend.darksea.item.DarkSeaItems;
import com.diamend.darksea.item.SoulwakeService;
import com.diamend.darksea.loot.ChestRefillService;
import com.diamend.darksea.loot.CustomLoot;
import com.diamend.darksea.loot.CustomLootConfig;
import com.diamend.darksea.loot.LootEditorService;
import com.diamend.darksea.loot.LootConfig;
import com.diamend.darksea.loot.LootTables;
import com.diamend.darksea.loot.RunLootService;
import com.diamend.darksea.mob.MobDropService;
import com.diamend.darksea.mob.MobDrops;
import com.diamend.darksea.mob.MobSpawner;
import com.diamend.darksea.naval.NavalCombatService;
import com.diamend.darksea.naval.NavalHudService;
import com.diamend.darksea.naval.NavalWeaponListener;
import com.diamend.darksea.npc.NpcService;
import com.diamend.darksea.npc.ShopConfig;
import com.diamend.darksea.npc.ShopEditorService;
import com.diamend.darksea.npc.ShopStock;
import com.diamend.darksea.npc.ShopMenuService;
import com.diamend.darksea.relic.RelicService;
import com.diamend.darksea.relic.ReliquaryService;
import com.diamend.darksea.relic.UndrownedHeartService;
import com.diamend.darksea.vault.VaultService;
import com.diamend.darksea.world.ManagedWorld;
import com.diamend.darksea.world.cultist.ExtractionChannel;
import com.diamend.darksea.world.cultist.NodeService;
import com.diamend.darksea.world.cultist.OreConfig;
import com.diamend.darksea.world.cultist.OreTables;
import com.diamend.darksea.world.cultist.PortalService;
import com.diamend.darksea.world.cultist.VeinSense;
import com.diamend.darksea.world.RespawnService;
import com.diamend.darksea.world.SeaResetScheduler;
import com.diamend.darksea.world.WorldService;
import com.diamend.darksea.zone.ExposureTask;
import com.diamend.darksea.zone.ZoneManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * DarkSea — an Arcane Odyssey-inspired Dark Sea: one safe island in an
 * endless hostile ocean, concentric danger rings, tiered sea armor,
 * schematic islands with MythicMobs encounters and refilling loot, and
 * upgradeable boats.
 */
public final class DarkSeaPlugin extends JavaPlugin {

    private volatile DarkSeaSettings settings;
    private volatile ZoneManager zoneManager;
    /** loot.yml exactly as shipped — what the editor greys out as context. */
    private volatile LootTables shippedLoot;
    /** loot-custom.yml: everything added in game. */
    private volatile CustomLoot customLoot;
    /** The two merged. This is what chests actually roll. */
    private volatile LootTables lootTables;
    private volatile Map<String, List<MobDrops.Line>> mobDrops;
    private volatile ShopStock shopStock;
    private volatile OreTables oreTables;

    /** What each startup step did — read back by {@code /ds diag}. */
    private final StartupReport startup = new StartupReport();

    /** Every warning this plugin has logged, so diag can show them in game. */
    private final LogTail logTail = new LogTail();

    private Messages messages;
    private IslandRegistry registry;
    private PlayerDataStore dataStore;
    private ProtectionService protection;
    private BoatService boat;
    private BoatMenuService boatMenu;
    private RelicService relics;
    private ReliquaryService reliquary;
    private IslandPlacer placer;
    private NavalCombatService naval;
    private NavalHudService hud;
    private RunLootService runLoot;
    private BountyService bounty;
    private WorldService worldService;
    private MobSpawner mobSpawner;
    private ExposureTask exposureTask;
    private NpcService npcs;
    private ShopMenuService shops;
    private ShopEditorService shopEditor;
    private LootEditorService lootEditor;
    private NodeService nodes;
    private ExtractionChannel extraction;
    private PortalService portals;
    private VaultService vaults;

    @Override
    public void onEnable() {
        // Attached before anything else runs, so a warning from the very first
        // config parse is still readable in game an hour later.
        getLogger().addHandler(logTail);

        step("config-files", () -> {
            saveDefaultConfig();
            saveResourceIfAbsent("mobs.yml");
            saveResourceIfAbsent("loot.yml");
            saveResourceIfAbsent("shops.yml");
            saveResourceIfAbsent("ores.yml");
            createDirectories();
        });

        // The one genuinely fatal step. Without zones there is no sea to be in,
        // and every listener below would be deciding rules against nothing.
        try {
            settings = DarkSeaSettings.load(getConfig(), getLogger());
            zoneManager = new ZoneManager(settings.zones());
            startup.ok("settings", 0L);
        } catch (RuntimeException ex) {
            startup.failed("settings", describe(ex), 0L);
            getLogger().severe("Unusable configuration: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(settings.messages());
        lootTables = stepGet("loot.yml", this::loadLootTables, LootTables.empty());
        // A failed loot.yml step leaves shippedLoot unset, and the merge has to
        // survive that — a broken loot.yml should cost the shipped tables, not
        // the whole plugin.
        if (shippedLoot == null) {
            shippedLoot = lootTables;
        }
        customLoot = stepGet("loot-custom.yml", this::loadCustomLoot, CustomLoot.empty());
        lootTables = customLoot.mergeInto(shippedLoot);
        mobDrops = stepGet("mobs.yml", this::loadMobDrops, Map.<String, List<MobDrops.Line>>of());
        shopStock = stepGet("shops.yml", this::loadShopStock, ShopStock.empty());
        oreTables = stepGet("ores.yml", this::loadOreTables, OreTables.empty());

        registry = new IslandRegistry(new File(getDataFolder(), "islands.yml"), getLogger());
        step("islands.yml", registry::load);
        dataStore = new PlayerDataStore(new File(getDataFolder(), "playerdata"), getLogger());
        protection = new ProtectionService();
        boat = new BoatService(this, dataStore);
        boatMenu = new BoatMenuService(this);
        relics = new RelicService(this);
        reliquary = new ReliquaryService(this);
        placer = new IslandPlacer(this, registry);
        naval = new NavalCombatService(this);
        hud = new NavalHudService(this);
        runLoot = new RunLootService(this);
        bounty = new BountyService(this);
        worldService = new WorldService(this, registry, placer);
        mobSpawner = new MobSpawner(this, registry);
        exposureTask = new ExposureTask(this);
        npcs = new NpcService(this);
        shops = new ShopMenuService(this);
        shopEditor = new ShopEditorService(this);
        lootEditor = new LootEditorService(this);
        nodes = new NodeService(this);
        extraction = new ExtractionChannel(this, nodes);
        portals = new PortalService(this);
        vaults = new VaultService(this);
        ChestRefillService chestRefill = new ChestRefillService(this, registry);

        // Each listener registered as its own step. A feature whose constructor
        // throws is then a feature that is missing, not a plugin that is gone.
        listener("protection", () -> protection);
        listener("boat", () -> boat);
        listener("boat-menu", () -> boatMenu);
        listener("chest-refill", () -> chestRefill);
        listener("exposure", () -> exposureTask);
        listener("relics", () -> relics);
        listener("reliquary", () -> reliquary);
        listener("loot-editor", () -> lootEditor);
        listener("consumables", () -> new ConsumableService(this));
        listener("soulwake", () -> new SoulwakeService(this));
        listener("undrowned-heart", () -> new UndrownedHeartService(this));
        listener("nax-combat", () -> new NaxCombatListener(this));
        listener("sea-guard", () -> new SeaGuardListener(this));
        listener("naval", () -> naval);
        listener("naval-weapons", () -> new NavalWeaponListener(this, naval));
        listener("run-loot", () -> runLoot);
        listener("bounty", () -> bounty);
        listener("mob-drops", () -> new MobDropService(this));
        listener("npcs", () -> npcs);
        listener("shops", () -> shops);
        listener("shop-editor", () -> shopEditor);
        listener("nodes", () -> nodes);
        listener("extraction", () -> extraction);
        listener("respawn", () -> new RespawnService(this));
        listener("portals", () -> portals);
        listener("vaults", () -> vaults);

        // Guarded like the rest, but called out if it fails: with no command
        // there is no /ds diag either, and whoever is testing is flying blind.
        step("command", () -> {
            PluginCommand command = getCommand("darksea");
            if (command == null) {
                throw new IllegalStateException("plugin.yml declares no 'darksea' command");
            }
            DarkSeaCommand executor = new DarkSeaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        });

        // World creation first. Everything below it needs a world to act on, so
        // if this is the step that failed, expect the next few to fail with it —
        // that cascade is in the diag output rather than hidden behind a stack
        // trace from whichever one happened to throw first.
        step("world-init", worldService::init);
        step("npc-spawn", npcs::spawnAll);
        step("landfall", () -> placer.maybeQueueLandfall(getServer().getConsoleSender(), null));
        step("portal-pad", () -> portals.installReturnPad(worldService.caves()));
        step("ore-nodes", () -> nodes.placeAllIfEmpty(worldService.caves()));

        step("node-timer", () -> nodes.runTaskTimer(this, NodeService.REGROW_PERIOD_TICKS,
                NodeService.REGROW_PERIOD_TICKS));
        step("extraction", extraction::start);
        step("vein-sense", () -> new VeinSense(this, nodes, extraction)
                .runTaskTimer(this, VeinSense.PERIOD_TICKS, VeinSense.PERIOD_TICKS));
        step("exposure-timer", () -> {
            int interval = settings.exposure().checkIntervalTicks();
            exposureTask.runTaskTimer(this, interval, interval);
        });
        step("mob-spawner", () ->
                mobSpawner.runTaskTimer(this, 100L, settings.mobSpawning().scanIntervalTicks()));
        step("relic-timer", () -> relics.runTaskTimer(this, 20L, 20L));
        step("naval-hud", hud::start);
        step("reset-scheduler", () -> new SeaResetScheduler(this).runTaskTimer(this,
                SeaResetScheduler.TICK_PERIOD, SeaResetScheduler.TICK_PERIOD));

        getLogger().info("DarkSea enabled — " + settings.zones().size() + " zones, "
                + registry.all().size() + " islands registered");
        getLogger().info(startup.summary());
        if (!startup.healthy()) {
            getLogger().severe("DarkSea came up degraded. Run /ds diag in game for the detail.");
        }
    }

    // ------------------------------------------------------------------
    // Guarded startup
    //
    // Paper disables a whole plugin when onEnable throws, which used to mean
    // one unreachable schematic or one world that would not create took the
    // entire Dark Sea down with it. Each step now runs behind a catch: a
    // failure costs that one feature for the session and is recorded for
    // /ds diag, rather than costing the plugin.
    // ------------------------------------------------------------------

    /** Runs a startup step, recording whether it worked. Never throws. */
    private void step(String name, Runnable body) {
        long start = System.nanoTime();
        try {
            body.run();
            startup.ok(name, elapsedMillis(start));
        } catch (Throwable ex) {
            startup.failed(name, describe(ex), elapsedMillis(start));
            getLogger().log(Level.SEVERE,
                    "DarkSea startup step '" + name + "' failed — continuing without it", ex);
        }
    }

    /**
     * The same, for a step that produces something. On failure the fallback is
     * used, so a broken shops.yml gives empty boards rather than a null
     * dereference in the first player's GUI.
     */
    private <T> T stepGet(String name, Supplier<T> body, T fallback) {
        long start = System.nanoTime();
        try {
            T value = body.get();
            startup.ok(name, elapsedMillis(start));
            return value;
        } catch (Throwable ex) {
            startup.failed(name, describe(ex), elapsedMillis(start));
            getLogger().log(Level.SEVERE,
                    "DarkSea startup step '" + name + "' failed — using empty defaults", ex);
            return fallback;
        }
    }

    /**
     * Registers one listener as its own step. The factory is called inside the
     * guard on purpose — a listener that throws does so in its constructor far
     * more often than in {@code registerEvents}.
     */
    private void listener(String name, Supplier<Listener> factory) {
        step("listener:" + name, () ->
                getServer().getPluginManager().registerEvents(factory.get(), this));
    }

    private static long elapsedMillis(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000.0);
    }

    /** Exception type and message, short enough for a chat line. */
    private static String describe(Throwable ex) {
        String message = ex.getMessage();
        String text = ex.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    @Override
    public void onDisable() {
        if (extraction != null) {
            // Clears every in-flight crack overlay. Skipping this would leave
            // half-broken-looking crystal for whoever logs in after a reload.
            extraction.stop();
        }
        if (npcs != null) {
            npcs.despawnAll();  // they are re-spawned from npcs.yml on enable
        }
        if (mobSpawner != null) {
            mobSpawner.despawnAll();
        }
        if (registry != null) {
            registry.save();
        }
        if (bounty != null) {
            bounty.save();
        }
        getLogger().removeHandler(logTail);
    }

    /**
     * /ds reload: swaps the settings snapshot, messages, loot tables and mob
     * sets. World shape and task intervals are startup-only.
     */
    public void reloadAll() {
        // Cleared first so the warning list after a reload describes this
        // reload, not a mix of it and whatever startup complained about.
        logTail.reset();
        reloadConfig();
        DarkSeaSettings loaded = DarkSeaSettings.load(getConfig(), getLogger());
        this.settings = loaded;
        this.zoneManager = new ZoneManager(loaded.zones());
        this.messages.reload(loaded.messages());
        this.customLoot = loadCustomLoot();
        this.lootTables = this.customLoot.mergeInto(loadLootTables());
        this.mobDrops = loadMobDrops();
        this.shopStock = loadShopStock();
        this.oreTables = loadOreTables();
        this.mobSpawner.reloadSets();
    }

    /**
     * Reads loot.yml and remembers it verbatim. The editor needs the shipped
     * tables on their own — merging is one-way, and a merged table cannot say
     * which of its lines came from which file.
     */
    private LootTables loadLootTables() {
        File file = new File(getDataFolder(), "loot.yml");
        LootTables loaded =
                LootConfig.load(YamlConfiguration.loadConfiguration(file), getLogger());
        this.shippedLoot = loaded;
        return loaded;
    }

    private CustomLoot loadCustomLoot() {
        return CustomLootConfig.load(
                new File(getDataFolder(), "loot-custom.yml"), getLogger());
    }

    private OreTables loadOreTables() {
        File file = new File(getDataFolder(), "ores.yml");
        OreTables loaded = OreConfig.load(
                YamlConfiguration.loadConfiguration(file), getLogger());
        // Crystal names and lore are config, not code. Installing them here
        // rather than at the call sites means /ds reload renames them too,
        // since this is the one path both startup and reload run through.
        DarkSeaItems.setDisplays(loaded.displays());
        getLogger().info("Loaded " + loaded.totalVeins() + " ore veins across "
                + loaded.types().size() + " types");
        return loaded;
    }

    private ShopStock loadShopStock() {
        File file = new File(getDataFolder(), "shops.yml");
        ShopStock loaded = ShopConfig.load(
                YamlConfiguration.loadConfiguration(file), getLogger());
        getLogger().info("Loaded " + loaded.lineCount() + " shop lines");
        return loaded;
    }

    private Map<String, List<MobDrops.Line>> loadMobDrops() {
        File file = new File(getDataFolder(), "mobs.yml");
        return MobDrops.parse(YamlConfiguration.loadConfiguration(file), getLogger());
    }

    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void createDirectories() {
        for (String dir : new String[]{"schematics/spawn", "schematics/tier1", "schematics/tier2",
                "schematics/tier3", "schematics/tier4", "playerdata"}) {
            File file = new File(getDataFolder(), dir);
            if (!file.exists() && !file.mkdirs()) {
                getLogger().warning("Could not create " + file);
            }
        }
    }

    // ------------------------------------------------------------------
    // Service access (read every use, so /ds reload takes effect live)
    // ------------------------------------------------------------------

    public DarkSeaSettings settings() {
        return settings;
    }

    /** What each startup step did. Backs {@code /ds diag}. */
    public StartupReport startup() {
        return startup;
    }

    /** The plugin's retained warnings. Backs {@code /ds diag warnings}. */
    public LogTail logTail() {
        return logTail;
    }

    public Messages messages() {
        return messages;
    }

    /**
     * Which of the plugin's worlds this is, or null for a world the plugin
     * does not manage at all (the vanilla overworld, a hub, someone else's
     * plot world). Callers ask the returned descriptor what rules apply rather
     * than comparing names themselves — see {@link ManagedWorld}.
     */
    public ManagedWorld managedWorld(World world) {
        if (world == null) {
            return null;
        }
        return managedWorld(world.getName());
    }

    public ManagedWorld managedWorld(String worldName) {
        DarkSeaSettings snapshot = settings;
        if (snapshot == null || worldName == null) {
            return null;
        }
        if (worldName.equals(snapshot.worldName())) {
            return ManagedWorld.DARK_SEA;
        }
        if (worldName.equals(snapshot.cultist().worldName())) {
            return ManagedWorld.CULTIST;
        }
        return null;
    }

    /** Shorthand for the commonest guard: is this happening in the sea? */
    public boolean isDarkSea(World world) {
        return managedWorld(world) == ManagedWorld.DARK_SEA;
    }

    public ZoneManager zoneManager() {
        return zoneManager;
    }

    public LootTables lootTables() {
        return lootTables;
    }

    public Map<String, List<MobDrops.Line>> mobDrops() {
        return mobDrops;
    }

    /** The parsed shops.yml snapshot — re-read by {@code /ds reload}. */
    public ShopStock shopStock() {
        return shopStock;
    }

    public ReliquaryService reliquary() {
        return reliquary;
    }

    public RelicService relics() {
        return relics;
    }

    public PlayerDataStore data() {
        return dataStore;
    }

    public IslandRegistry registry() {
        return registry;
    }

    public ProtectionService protection() {
        return protection;
    }

    public BoatService boat() {
        return boat;
    }

    public BoatMenuService boatMenu() {
        return boatMenu;
    }

    public IslandPlacer placer() {
        return placer;
    }

    public NavalCombatService naval() {
        return naval;
    }

    public NavalHudService hud() {
        return hud;
    }

    public RunLootService runLoot() {
        return runLoot;
    }

    public BountyService bounty() {
        return bounty;
    }

    public WorldService worldService() {
        return worldService;
    }

    public MobSpawner mobSpawner() {
        return mobSpawner;
    }

    public NpcService npcs() {
        return npcs;
    }

    public ShopMenuService shops() {
        return shops;
    }

    /** The parsed ores.yml snapshot — re-read by {@code /ds reload}. */
    public OreTables oreTables() {
        return oreTables;
    }

    public NodeService nodes() {
        return nodes;
    }

    public PortalService portals() {
        return portals;
    }

    public ShopEditorService shopEditor() {
        return shopEditor;
    }

    /**
     * Swaps the live shop snapshot and writes shops.yml — the in-game editor's
     * only way to change anything. Written on every edit rather than on close,
     * so a crash mid-session can't lose work.
     */
    public void saveShopStock(ShopStock updated) {
        this.shopStock = updated;
        ShopConfig.save(updated, new File(getDataFolder(), "shops.yml"), getLogger());
    }

    /** loot.yml as shipped, without the in-game additions merged in. */
    public LootTables shippedLoot() {
        return shippedLoot;
    }

    /** The in-game loot additions, as {@code /ds loot} last left them. */
    public CustomLoot customLoot() {
        return customLoot;
    }

    /**
     * Swaps the live overlay, re-merges the tables and writes
     * loot-custom.yml. Re-merging here rather than on the next reload is the
     * point: an item added in game is in the pool before the editor is even
     * closed, so it can be verified against a real chest immediately.
     */
    public void saveCustomLoot(CustomLoot updated) {
        this.customLoot = updated;
        this.lootTables = updated.mergeInto(shippedLoot);
        CustomLootConfig.save(updated,
                new File(getDataFolder(), "loot-custom.yml"), getLogger());
    }

    public LootEditorService lootEditor() {
        return lootEditor;
    }

    public VaultService vaults() {
        return vaults;
    }
}
