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
import com.diamend.darksea.island.IslandPlacer;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.item.ConsumableService;
import com.diamend.darksea.item.SoulwakeService;
import com.diamend.darksea.loot.ChestRefillService;
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
import com.diamend.darksea.relic.UndrownedHeartService;
import com.diamend.darksea.vault.VaultService;
import com.diamend.darksea.world.ManagedWorld;
import com.diamend.darksea.world.cultist.NodeService;
import com.diamend.darksea.world.cultist.OreConfig;
import com.diamend.darksea.world.cultist.OreTables;
import com.diamend.darksea.world.cultist.PortalService;
import com.diamend.darksea.world.SeaResetScheduler;
import com.diamend.darksea.world.WorldService;
import com.diamend.darksea.zone.ExposureTask;
import com.diamend.darksea.zone.ZoneManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * DarkSea — an Arcane Odyssey-inspired Dark Sea: one safe island in an
 * endless hostile ocean, concentric danger rings, tiered sea armor,
 * schematic islands with MythicMobs encounters and refilling loot, and
 * upgradeable boats.
 */
public final class DarkSeaPlugin extends JavaPlugin {

    private volatile DarkSeaSettings settings;
    private volatile ZoneManager zoneManager;
    private volatile LootTables lootTables;
    private volatile Map<String, List<MobDrops.Line>> mobDrops;
    private volatile ShopStock shopStock;
    private volatile OreTables oreTables;

    private Messages messages;
    private IslandRegistry registry;
    private PlayerDataStore dataStore;
    private ProtectionService protection;
    private BoatService boat;
    private BoatMenuService boatMenu;
    private RelicService relics;
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
    private NodeService nodes;
    private PortalService portals;
    private VaultService vaults;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("mobs.yml");
        saveResourceIfAbsent("loot.yml");
        saveResourceIfAbsent("shops.yml");
        saveResourceIfAbsent("ores.yml");
        createDirectories();

        try {
            settings = DarkSeaSettings.load(getConfig(), getLogger());
            zoneManager = new ZoneManager(settings.zones());
        } catch (IllegalStateException ex) {
            getLogger().severe("Unusable configuration: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(settings.messages());
        lootTables = loadLootTables();
        mobDrops = loadMobDrops();
        shopStock = loadShopStock();
        oreTables = loadOreTables();

        registry = new IslandRegistry(new File(getDataFolder(), "islands.yml"), getLogger());
        registry.load();
        dataStore = new PlayerDataStore(new File(getDataFolder(), "playerdata"), getLogger());
        protection = new ProtectionService();
        boat = new BoatService(this, dataStore);
        boatMenu = new BoatMenuService(this);
        relics = new RelicService(this);
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
        nodes = new NodeService(this);
        portals = new PortalService(this);
        vaults = new VaultService(this);
        ChestRefillService chestRefill = new ChestRefillService(this, registry);

        getServer().getPluginManager().registerEvents(protection, this);
        getServer().getPluginManager().registerEvents(boat, this);
        getServer().getPluginManager().registerEvents(boatMenu, this);
        getServer().getPluginManager().registerEvents(chestRefill, this);
        getServer().getPluginManager().registerEvents(exposureTask, this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(new ConsumableService(this), this);
        getServer().getPluginManager().registerEvents(new SoulwakeService(this), this);
        getServer().getPluginManager().registerEvents(new UndrownedHeartService(this), this);
        getServer().getPluginManager().registerEvents(new NaxCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SeaGuardListener(this), this);
        getServer().getPluginManager().registerEvents(naval, this);
        getServer().getPluginManager().registerEvents(new NavalWeaponListener(this, naval), this);
        getServer().getPluginManager().registerEvents(runLoot, this);
        getServer().getPluginManager().registerEvents(bounty, this);
        getServer().getPluginManager().registerEvents(new MobDropService(this), this);
        getServer().getPluginManager().registerEvents(npcs, this);
        getServer().getPluginManager().registerEvents(shops, this);
        getServer().getPluginManager().registerEvents(shopEditor, this);
        getServer().getPluginManager().registerEvents(nodes, this);
        getServer().getPluginManager().registerEvents(portals, this);
        getServer().getPluginManager().registerEvents(vaults, this);

        PluginCommand command = getCommand("darksea");
        DarkSeaCommand executor = new DarkSeaCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        worldService.init();
        npcs.spawnAll();
        placer.maybeQueueLandfall(getServer().getConsoleSender(), null);
        portals.installReturnPad(worldService.caves());
        nodes.placeAllIfEmpty(worldService.caves());
        nodes.runTaskTimer(this, NodeService.REGROW_PERIOD_TICKS,
                NodeService.REGROW_PERIOD_TICKS);

        int interval = settings.exposure().checkIntervalTicks();
        exposureTask.runTaskTimer(this, interval, interval);
        mobSpawner.runTaskTimer(this, 100L, settings.mobSpawning().scanIntervalTicks());
        relics.runTaskTimer(this, 20L, 20L);
        hud.start();
        new SeaResetScheduler(this).runTaskTimer(this,
                SeaResetScheduler.TICK_PERIOD, SeaResetScheduler.TICK_PERIOD);

        getLogger().info("DarkSea enabled — " + settings.zones().size() + " zones, "
                + registry.all().size() + " islands registered");
    }

    @Override
    public void onDisable() {
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
    }

    /**
     * /ds reload: swaps the settings snapshot, messages, loot tables and mob
     * sets. World shape and task intervals are startup-only.
     */
    public void reloadAll() {
        reloadConfig();
        DarkSeaSettings loaded = DarkSeaSettings.load(getConfig(), getLogger());
        this.settings = loaded;
        this.zoneManager = new ZoneManager(loaded.zones());
        this.messages.reload(loaded.messages());
        this.lootTables = loadLootTables();
        this.mobDrops = loadMobDrops();
        this.shopStock = loadShopStock();
        this.oreTables = loadOreTables();
        this.mobSpawner.reloadSets();
    }

    private LootTables loadLootTables() {
        File file = new File(getDataFolder(), "loot.yml");
        return LootConfig.load(YamlConfiguration.loadConfiguration(file), getLogger());
    }

    private OreTables loadOreTables() {
        File file = new File(getDataFolder(), "ores.yml");
        OreTables loaded = OreConfig.load(
                YamlConfiguration.loadConfiguration(file), getLogger());
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

    public VaultService vaults() {
        return vaults;
    }
}
