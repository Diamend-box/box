package com.diamend.darksea;

import com.diamend.darksea.armor.ProtectionService;
import com.diamend.darksea.boat.BoatService;
import com.diamend.darksea.combat.NaxCombatListener;
import com.diamend.darksea.combat.SeaGuardListener;
import com.diamend.darksea.command.DarkSeaCommand;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.config.Messages;
import com.diamend.darksea.data.PlayerDataStore;
import com.diamend.darksea.island.IslandPlacer;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.item.ConsumableService;
import com.diamend.darksea.loot.ChestRefillService;
import com.diamend.darksea.loot.LootConfig;
import com.diamend.darksea.loot.LootTables;
import com.diamend.darksea.mob.MobDropService;
import com.diamend.darksea.mob.MobDrops;
import com.diamend.darksea.mob.MobSpawner;
import com.diamend.darksea.relic.RelicService;
import com.diamend.darksea.world.WorldService;
import com.diamend.darksea.zone.ExposureTask;
import com.diamend.darksea.zone.ZoneManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
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

    private Messages messages;
    private IslandRegistry registry;
    private PlayerDataStore dataStore;
    private ProtectionService protection;
    private BoatService boat;
    private RelicService relics;
    private IslandPlacer placer;
    private WorldService worldService;
    private MobSpawner mobSpawner;
    private ExposureTask exposureTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("mobs.yml");
        saveResourceIfAbsent("loot.yml");
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

        registry = new IslandRegistry(new File(getDataFolder(), "islands.yml"), getLogger());
        registry.load();
        dataStore = new PlayerDataStore(new File(getDataFolder(), "playerdata"), getLogger());
        protection = new ProtectionService();
        boat = new BoatService(this, dataStore);
        relics = new RelicService(this);
        placer = new IslandPlacer(this, registry);
        worldService = new WorldService(this, registry, placer);
        mobSpawner = new MobSpawner(this, registry);
        exposureTask = new ExposureTask(this);
        ChestRefillService chestRefill = new ChestRefillService(this, registry);

        getServer().getPluginManager().registerEvents(protection, this);
        getServer().getPluginManager().registerEvents(boat, this);
        getServer().getPluginManager().registerEvents(chestRefill, this);
        getServer().getPluginManager().registerEvents(exposureTask, this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(new ConsumableService(this), this);
        getServer().getPluginManager().registerEvents(new NaxCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SeaGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new MobDropService(this), this);

        PluginCommand command = getCommand("darksea");
        DarkSeaCommand executor = new DarkSeaCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        worldService.init();

        int interval = settings.exposure().checkIntervalTicks();
        exposureTask.runTaskTimer(this, interval, interval);
        mobSpawner.runTaskTimer(this, 100L, settings.mobSpawning().scanIntervalTicks());
        relics.runTaskTimer(this, 20L, 20L);

        getLogger().info("DarkSea enabled — " + settings.zones().size() + " zones, "
                + registry.all().size() + " islands registered");
    }

    @Override
    public void onDisable() {
        if (mobSpawner != null) {
            mobSpawner.despawnAll();
        }
        if (registry != null) {
            registry.save();
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
        this.mobSpawner.reloadSets();
    }

    private LootTables loadLootTables() {
        File file = new File(getDataFolder(), "loot.yml");
        return LootConfig.load(YamlConfiguration.loadConfiguration(file), getLogger());
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

    public ZoneManager zoneManager() {
        return zoneManager;
    }

    public LootTables lootTables() {
        return lootTables;
    }

    public Map<String, List<MobDrops.Line>> mobDrops() {
        return mobDrops;
    }

    public RelicService relics() {
        return relics;
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

    public IslandPlacer placer() {
        return placer;
    }

    public WorldService worldService() {
        return worldService;
    }

    public MobSpawner mobSpawner() {
        return mobSpawner;
    }
}
