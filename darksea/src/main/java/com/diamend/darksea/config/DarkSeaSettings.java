package com.diamend.darksea.config;

import com.diamend.darksea.island.shape.DemoShapes;
import com.diamend.darksea.zone.Zone;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Immutable snapshot of every configurable value. Reload swaps the whole
 * snapshot atomically; services always read through the plugin's current
 * snapshot, so a reload takes effect on the next tick without restarts
 * (except world shape and task intervals, which are read once at startup).
 */
public record DarkSeaSettings(
        String worldName,
        CultistSettings cultist,
        int landfallOffsetX,
        int landfallOffsetZ,
        int seaLevel,
        int seabedBaseY,
        int seabedVariation,
        int centerX,
        int centerZ,
        ExposureSettings exposure,
        List<Zone> zones,
        ArmorSettings armor,
        GenerationSettings generation,
        MobSpawnSettings mobSpawning,
        CombatSettings combat,
        ResetSettings reset,
        BoatSettings boat,
        NavalSettings naval,
        RelicSettings relics,
        Map<String, String> messages) {

    public record ExposureSettings(int checkIntervalTicks, int effectDurationTicks, int graceOnLoginSeconds) {
    }

    public record ArmorStyle(String displayName, String materialPrefix) {
    }

    public record ArmorSettings(boolean unbreakable, Map<Integer, ArmorStyle> tiers) {
    }

    /**
     * {@code shapeWeights} overrides which built-in island shapes a ring
     * raises and how often, keyed by ring tier with {@code 0} holding the
     * defaults that apply to every ring. A shape absent from the map keeps its
     * own built-in rarity; a shape mapped to 0 is kept out of the sea.
     */
    public record GenerationSettings(int pasteY, double minIslandGap, double ringBorderMargin,
                                     double outerRadius, Map<Integer, Integer> islandsPerRing,
                                     Material chestMarker, Material mobMarker, boolean demoIslands,
                                     int demoPaceTicks,
                                     Map<Integer, Map<String, Integer>> shapeWeights) {

        /** The weight overrides in force for a ring: its own, else the defaults. */
        public Map<String, Integer> shapeWeightsFor(int tier) {
            Map<String, Integer> ring = shapeWeights().get(tier);
            if (ring != null && !ring.isEmpty()) {
                return ring;
            }
            return shapeWeights().getOrDefault(0, Map.of());
        }
    }

    /**
     * {@code perIslandCap} is how many of an island's garrison stand at once;
     * {@code islandBudget} is how many it will ever spawn before the island
     * is spent. Without a budget a killed mob frees its slot immediately and
     * an island is an endless farm — clearing one has to mean something, so
     * the budget is consumed per spawn and only refills {@code
     * budgetRefillMinutes} after the island's FIRST spawn, the same
     * first-touch clock the crystal geodes use.
     */
    public record MobSpawnSettings(int scanIntervalTicks, double activationRadius, int perIslandCap,
                                   int globalCap, int abandonCooldownMinutes,
                                   int islandBudget, int budgetRefillMinutes,
                                   int bossRespawnMinutes, int spawnClearance,
                                   int spawnWidth) {
    }

    /**
     * Boxpvp guard rails. Generated islands are protected loot-content: with
     * {@code protectIslands} on, nobody breaks, builds on or blows up an
     * island's blocks (only its chests open), so the sea's islands are a
     * contested prize rather than a mine. PvP is otherwise on everywhere in
     * the Dark Sea except within {@code pvpSafeRadius} of center — the home
     * island's sanctuary, where players can neither be struck nor grief.
     * Admins ({@code darksea.admin}) bypass block protection so the home
     * island stays hand-editable. {@code islandProtectBuffer} extends the
     * protected footprint this many blocks past each island's edge, so players
     * can't pillar up alongside an island to cheese its mobs from range.
     * {@code runLootDeath} turns on the extraction loop: dying in the sea drops
     * only loot gathered on the run (kept gear is safe), banked by reaching the
     * sanctuary.
     */
    public record CombatSettings(boolean protectIslands, double pvpSafeRadius,
                                 int islandProtectBuffer, boolean runLootDeath) {
    }

    /**
     * The timed sea reset. When {@code autoEnabled}, the whole Dark Sea resets
     * every {@code intervalHours} so loot can't be hoarded or camp-farmed
     * forever — a broadcast counts down at each {@code warnMinutes} mark, then
     * the sea heals and restocks. {@code fullMode} false is a soft reset
     * (islands healed and restocked in place, positions kept); true is a full
     * re-layout (new world seed each cycle — a heavier "season" wipe).
     */
    public record ResetSettings(boolean autoEnabled, int intervalHours, boolean fullMode,
                                List<Integer> warnMinutes) {
    }

    /**
     * A boat upgrade tier. {@code speed} scales sailing velocity, {@code shield}
     * folds into the exposure formula (scout one ring farther), and
     * {@code toughness} divides incoming hull damage in PvP — a higher boat
     * survives more hits before it's sunk, so the upgrade path buys naval
     * survivability, not just pace.
     */
    /**
     * A boat tier's stats. {@code hp} is the tier's own hull max HP; 0 means
     * "use the global {@code naval.hull.max-hp}", so only the tiers that want a
     * bigger hull than the default need to set it.
     *
     * <p>{@code cost} is what buying this tier costs in Chronons, paid at the
     * boat wheel. It is a property of the level rather than of a curve formula
     * so the ramp can be shaped by hand — the jump into the last hull should be
     * felt, and a formula would only ever make it smooth.
     */
    public record BoatLevel(String name, double speed, int shield, double toughness, double hp,
                            int cost) {

        /** A level with no price — level 0, and any tier a config forgets to price. */
        public BoatLevel(String name, double speed, int shield, double toughness, double hp) {
            this(name, speed, shield, toughness, hp, 0);
        }
    }

    public record BoatSettings(double speedCapBase, Map<Integer, BoatLevel> levels,
                               StatPointSettings statPoints) {
    }

    /**
     * The custom stat-point economy: a captain earns one point per boat level
     * and spends it from the boat wheel's Outfit page. Each field is how much a
     * single point adds to that stat; {@code resetCostPerPoint} is the Chronon
     * price to respec, scaled by how many points are currently committed.
     */
    public record StatPointSettings(double speedPerPoint, double toughnessPerPoint,
                                    double hpPerPoint, double ramPowerPerPoint,
                                    int resetCostPerPoint) {
    }

    /**
     * Ram tuning. A collision registers only above {@code minClosingSpeed}
     * (blocks/tick of convergence); its force is closing speed ×
     * {@code damagePerSpeed}, capped at {@code maxDamage}. The defender's hull
     * eats {@code defenderShare} of the force, the attacker's the rest — each
     * softened by its own toughness. {@code riderBleed} of each hull share
     * also lands on that boat's rider, so a hard ram stings the sailor, not
     * just the ship. {@code pairCooldownMillis} keeps one scrape from firing
     * every tick two hulls stay in contact.
     */
    public record RamSettings(double minClosingSpeed, double damagePerSpeed, double maxDamage,
                              double defenderShare, double riderBleed, double knockback,
                              long pairCooldownMillis, double powerPerLevel) {
    }

    /**
     * The hull health model behind naval weapons (rams and anti-ship ammo —
     * vanilla whacking still uses vanilla boat wobble). A hull has
     * {@code maxHp}. Any naval hit combat-tags it for {@code combatTagSeconds},
     * during which it heals nothing; after the tag it climbs back at
     * {@code regenPerSecond} HP/s — a slow claw-back, never a snap to full.
     * Any hull damage also slows the boat to {@code woundedSpeedFactor} of its
     * speed for {@code woundedSlowSeconds} — the wounded-hull rule that keeps
     * a max-speed boat catchable: land a shot, close the gap. On top of that
     * momentary dip, every point of HP the hull is missing shaves
     * {@code speedPenaltyPerHp} off the top speed until it's repaired — a
     * persistent limp that grows as a hull is chipped down.
     */
    public record HullSettings(double maxHp, int combatTagSeconds, double regenPerSecond,
                               int woundedSlowSeconds, double woundedSpeedFactor,
                               double speedPenaltyPerHp) {
    }

    /**
     * The ram surge: tap sprint or jump at the tiller for a burst to
     * {@code boostFactor} × the normal speed cap, then wait
     * {@code cooldownSeconds}. Chases are won by timing surges, not raw
     * stats; surging while harpooned snaps the line instead.
     */
    public record SurgeSettings(double boostFactor, int cooldownSeconds) {
    }

    /**
     * The harpoon gun: hooks a ridden boat within {@code range} blocks and
     * reels it toward the shooter for {@code pullSeconds} at
     * {@code pullStrength} blocks/tick of added pull.
     */
    public record HarpoonSettings(double range, double pullSeconds, double pullStrength) {
    }

    /**
     * The always-on boat action bar (hull pips, wounded flag, surge state),
     * repainted every {@code periodTicks} while riding in the Dark Sea.
     */
    public record HudSettings(boolean enabled, int periodTicks) {
    }

    /**
     * Naval weapons, together. {@code chainshotSlowSeconds} /
     * {@code chainshotSpeedFactor} are the sail-shredder arrow's harder,
     * longer wounded-hull slow; {@code hullpiercerDamage} is the boat-killer
     * arrow's hull damage, applied with only {@code hullpiercerToughnessFactor}
     * of the target's toughness BONUS (above 1.0) counted.
     */
    /**
     * The home dry-dock: a wounded hull is patched back to full for
     * {@code costPerHp} Chronons per missing HP — but only within the home
     * sanctuary, so a captain must sail all the way back to safety to use it.
     */
    public record RepairSettings(double costPerHp) {
    }

    public record NavalSettings(RamSettings ram, HullSettings hull, SurgeSettings surge,
                                int chainshotSlowSeconds, double chainshotSpeedFactor,
                                double hullpiercerDamage, double hullpiercerToughnessFactor,
                                HarpoonSettings harpoon,
                                HudSettings hud, RepairSettings repair) {
    }

    /**
     * Relic knobs. {@code maxActive} bounds how many awake relics may work from
     * a player's inventory at once. The Undrowned Heart is a separate,
     * consumed-once relic: once attuned, a lethal blow is refused like a totem
     * and the captain is left at {@code undrownedReviveHealth} health, but only
     * once per {@code undrownedCooldownSeconds} — the cooldown is stored per
     * player so relogging can't reset it.
     */
    public record RelicSettings(int maxActive, int undrownedCooldownSeconds,
                                double undrownedReviveHealth,
                                int bagStartSlots, int bagMaxSlots, List<SlotCost> bagCosts) {

        /** Without a configured bag: the shipped 2-to-6 ladder is used. */
        public RelicSettings(int maxActive, int undrownedCooldownSeconds,
                             double undrownedReviveHealth) {
            this(maxActive, undrownedCooldownSeconds, undrownedReviveHealth,
                    2, 6, List.of());
        }

        public RelicSettings {
            bagCosts = bagCosts == null ? List.of() : List.copyOf(bagCosts);
            bagStartSlots = Math.max(1, bagStartSlots);
            bagMaxSlots = Math.max(bagStartSlots, bagMaxSlots);
        }

        /**
         * What the {@code bought}-th extra slot costs, or null past the end of
         * the ladder. A short ladder simply caps the bag earlier than
         * {@code bagMaxSlots} would — the price list is the real ceiling.
         */
        public SlotCost costForSlot(int bought) {
            return bought >= 0 && bought < bagCosts.size() ? bagCosts.get(bought) : null;
        }
    }

    /**
     * One rung of the reliquary ladder: an amount of one DarkSea item (a cave
     * crystal, in the shipped config) that buys the next relic slot.
     */
    public record SlotCost(String itemId, int amount) {
        public SlotCost {
            amount = Math.max(1, amount);
        }
    }

    /**
     * The cultist caves: a second plugin-generated world past the origin
     * island, playing by ordinary rules.
     *
     * <p>{@code halfExtent} is half the world's width, so 256 is a 512x512 box.
     * Bounding it is deliberate — every vein stays findable, the map has a
     * shape that can be designed around, and a small server's disk use is
     * capped instead of growing with however far someone decides to walk.
     *
     * <p>{@code chamberRadius} is the guaranteed-open room at the origin the
     * portal arrives in, so nobody is ever dropped inside stone.
     */
    public record CultistSettings(String worldName, int floorY, int roofY,
                                  int halfExtent, int chamberRadius) {
    }

    /**
     * Parses a configuration. Throws {@link IllegalStateException} with a
     * human-readable reason for unusable configs (no zones, no armor tiers);
     * recoverable oddities (an unknown effect type, a bad material) are
     * logged and skipped.
     */
    public static DarkSeaSettings load(FileConfiguration cfg, Logger log) {
        String worldName = cfg.getString("world.name", "dark_sea");
        // The caves past the origin island. A separate world rather than a
        // region of the sea, because it plays by entirely different rules.
        int cavesFloor = cfg.getInt("world.caves.floor-y", 20);
        int cavesRoof = Math.max(cavesFloor + 8, cfg.getInt("world.caves.roof-y", 90));
        CultistSettings cultist = new CultistSettings(
                cfg.getString("world.caves.name", "dark_sea_caves"),
                cavesFloor,
                cavesRoof,
                Math.max(32, cfg.getInt("world.caves.half-extent", 256)),
                Math.max(1, cfg.getInt("world.caves.chamber-radius", 6)));
        // Where the one cultist landfall stands, relative to the calm center.
        // Far enough out to need a real boat, inside the rings that gate on
        // sea armor so getting there is itself the prerequisite.
        int landfallOffsetX = cfg.getInt("world.landfall.offset-x", 2600);
        int landfallOffsetZ = cfg.getInt("world.landfall.offset-z", -1800);
        int seaLevel = cfg.getInt("world.sea-level", 62);
        int seabedBaseY = cfg.getInt("world.seabed.base-y", 45);
        int seabedVariation = cfg.getInt("world.seabed.variation", 5);
        int centerX = cfg.getInt("center.x", 0);
        int centerZ = cfg.getInt("center.z", 0);

        ExposureSettings exposure = new ExposureSettings(
                Math.max(1, cfg.getInt("exposure.check-interval-ticks", 20)),
                Math.max(20, cfg.getInt("exposure.effect-duration-ticks", 60)),
                Math.max(0, cfg.getInt("exposure.grace-on-login-seconds", 10)));

        List<Zone> zones = loadZones(cfg, log);
        if (zones.isEmpty()) {
            throw new IllegalStateException("config.yml defines no zones — the Dark Sea needs at least one");
        }

        ArmorSettings armor = loadArmor(cfg, log);
        if (armor.tiers().isEmpty()) {
            throw new IllegalStateException("config.yml defines no armor tiers under armor.tiers");
        }

        GenerationSettings generation = loadGeneration(cfg, log);

        MobSpawnSettings mobSpawning = new MobSpawnSettings(
                Math.max(20, cfg.getInt("mob-spawning.scan-interval-ticks", 100)),
                Math.max(8, cfg.getDouble("mob-spawning.activation-radius", 64)),
                Math.max(1, cfg.getInt("mob-spawning.per-island-cap", 6)),
                Math.max(1, cfg.getInt("mob-spawning.global-cap", 120)),
                Math.max(1, cfg.getInt("mob-spawning.abandon-cooldown-minutes", 5)),
                Math.max(1, cfg.getInt("mob-spawning.island-budget", 14)),
                Math.max(1, cfg.getInt("mob-spawning.budget-refill-minutes", 20)),
                Math.max(0, cfg.getInt("mob-spawning.boss-respawn-minutes", 15)),
                Math.max(1, cfg.getInt("mob-spawning.spawn-clearance", 3)),
                Math.max(1, cfg.getInt("mob-spawning.spawn-width", 2)));

        CombatSettings combat = new CombatSettings(
                cfg.getBoolean("combat.protect-islands", true),
                Math.max(0, cfg.getDouble("combat.pvp-safe-radius", 500)),
                Math.max(0, cfg.getInt("combat.island-protect-buffer", 5)),
                cfg.getBoolean("combat.run-loot-death", true));

        ResetSettings reset = loadReset(cfg);

        BoatSettings boat = loadBoat(cfg, log);

        NavalSettings naval = loadNaval(cfg);

        RelicSettings relics = new RelicSettings(
                Math.min(9, Math.max(1, cfg.getInt("relics.max-active", 2))),
                Math.max(1, cfg.getInt("relics.undrowned.cooldown-seconds", 120)),
                Math.max(1.0, cfg.getDouble("relics.undrowned.revive-health", 1.0)),
                Math.max(1, cfg.getInt("relics.bag.starting-slots", 2)),
                Math.max(1, cfg.getInt("relics.bag.max-slots", 6)),
                slotCosts(cfg.getStringList("relics.bag.slot-costs")));

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection msgSec = cfg.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                String value = msgSec.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        }

        return new DarkSeaSettings(worldName, cultist, landfallOffsetX, landfallOffsetZ, seaLevel, seabedBaseY, seabedVariation, centerX, centerZ,
                exposure, zones, armor, generation, mobSpawning, combat, reset, boat, naval,
                relics, Map.copyOf(messages));
    }

    /**
     * Parses the reliquary ladder from {@code "<item-id> <amount>"} lines, in
     * order: the first line prices the first extra slot. A malformed line is
     * skipped rather than failing startup, so a typo costs one rung, not the
     * server.
     */
    public static List<SlotCost> slotCosts(List<String> lines) {
        List<SlotCost> costs = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 2) {
                continue;
            }
            try {
                costs.add(new SlotCost(parts[0], Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                // not a price — skip the rung
            }
        }
        return List.copyOf(costs);
    }

    private static List<Zone> loadZones(FileConfiguration cfg, Logger log) {
        List<Zone> zones = new ArrayList<>();
        for (Map<?, ?> zm : cfg.getMapList("zones")) {
            Object idRaw = zm.get("id");
            if (idRaw == null) {
                log.warning("Skipping a zone with no id in config.yml");
                continue;
            }
            String id = String.valueOf(idRaw);
            String name = zm.get("name") != null ? String.valueOf(zm.get("name")) : id;
            double maxRadius = toDouble(zm.get("max-radius"), -1);
            int requiredTier = toInt(zm.get("required-tier"), 0);

            List<Zone.ZoneEffect> effects = new ArrayList<>();
            if (zm.get("effects") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> em)) {
                        continue;
                    }
                    String typeName = String.valueOf(em.get("type"));
                    PotionEffectType type = resolveEffect(typeName);
                    if (type == null) {
                        log.warning("Zone '" + id + "': unknown potion effect '" + typeName + "' — skipped");
                        continue;
                    }
                    effects.add(new Zone.ZoneEffect(type, Math.max(0, toInt(em.get("amplifier"), 0))));
                }
            }
            boolean bypassProtection = zm.get("bypass-protection") instanceof Boolean b && b;
            zones.add(new Zone(id, name, maxRadius, requiredTier, List.copyOf(effects), bypassProtection));
        }
        return zones;
    }

    private static PotionEffectType resolveEffect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT));
            return Registry.EFFECT.get(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ArmorSettings loadArmor(FileConfiguration cfg, Logger log) {
        boolean unbreakable = cfg.getBoolean("armor.unbreakable", true);
        Map<Integer, ArmorStyle> tiers = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("armor.tiers");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int tier;
                try {
                    tier = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    log.warning("armor.tiers." + key + " is not a number — skipped");
                    continue;
                }
                String name = sec.getString(key + ".name", "Tier " + tier);
                String prefix = sec.getString(key + ".material", "IRON");
                // Fail fast on a prefix that doesn't form real armor materials.
                if (Material.matchMaterial(prefix + "_HELMET") == null) {
                    log.warning("armor.tiers." + key + ".material '" + prefix
                            + "' is not an armor material prefix — skipped");
                    continue;
                }
                tiers.put(tier, new ArmorStyle(name, prefix.toUpperCase(Locale.ROOT)));
            }
        }
        return new ArmorSettings(unbreakable, Map.copyOf(tiers));
    }

    private static GenerationSettings loadGeneration(FileConfiguration cfg, Logger log) {
        Map<Integer, Integer> perRing = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("generation.islands-per-ring");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    perRing.put(Integer.parseInt(key), Math.max(0, sec.getInt(key)));
                } catch (NumberFormatException ex) {
                    log.warning("generation.islands-per-ring." + key + " is not a number — skipped");
                }
            }
        }
        Material chestMarker = matchMaterial(cfg.getString("markers.chest"), Material.LODESTONE, "markers.chest", log);
        Material mobMarker = matchMaterial(cfg.getString("markers.mob-spawn"), Material.GOLD_BLOCK, "markers.mob-spawn", log);
        return new GenerationSettings(
                cfg.getInt("generation.paste-y", 58),
                Math.max(0, cfg.getDouble("generation.min-island-gap", 400)),
                Math.max(0, cfg.getDouble("generation.ring-border-margin", 200)),
                Math.max(500, cfg.getDouble("generation.outer-radius", 8000)),
                Map.copyOf(perRing), chestMarker, mobMarker,
                cfg.getBoolean("generation.demo-islands", false),
                Math.max(1, cfg.getInt("generation.demo-pace-ticks", 10)),
                loadShapeWeights(cfg, log));
    }

    /**
     * {@code generation.shape-weights}, as {@code ring -> shape id -> weight}.
     * The {@code default} block lands under key 0 and stands in for any ring
     * without one of its own. An unknown shape id is warned about and skipped
     * rather than failing the load — a typo should cost you one shape, not the
     * whole sea.
     */
    private static Map<Integer, Map<String, Integer>> loadShapeWeights(FileConfiguration cfg,
                                                                      Logger log) {
        ConfigurationSection sec = cfg.getConfigurationSection("generation.shape-weights");
        if (sec == null) {
            return Map.of();
        }
        Map<Integer, Map<String, Integer>> weights = new HashMap<>();
        for (String ringKey : sec.getKeys(false)) {
            int tier;
            if ("default".equalsIgnoreCase(ringKey)) {
                tier = 0;
            } else {
                try {
                    tier = Integer.parseInt(ringKey);
                } catch (NumberFormatException ex) {
                    log.warning("generation.shape-weights." + ringKey
                            + " is neither a ring number nor 'default' — skipped");
                    continue;
                }
            }
            ConfigurationSection ring = sec.getConfigurationSection(ringKey);
            if (ring == null) {
                continue;
            }
            Map<String, Integer> perShape = new HashMap<>();
            for (String shapeId : ring.getKeys(false)) {
                if (DemoShapes.byId(shapeId) == null) {
                    log.warning("generation.shape-weights." + ringKey + "." + shapeId
                            + " is not a known island shape — skipped");
                    continue;
                }
                perShape.put(shapeId, Math.max(0, ring.getInt(shapeId)));
            }
            if (!perShape.isEmpty()) {
                weights.put(tier, Map.copyOf(perShape));
            }
        }
        return Map.copyOf(weights);
    }

    private static ResetSettings loadReset(FileConfiguration cfg) {
        List<Integer> warns = new ArrayList<>();
        for (int minutes : cfg.getIntegerList("reset.auto.warn-minutes")) {
            if (minutes > 0) {
                warns.add(minutes);
            }
        }
        if (warns.isEmpty()) {
            warns = List.of(30, 10, 5, 1);
        }
        return new ResetSettings(
                cfg.getBoolean("reset.auto.enabled", true),
                Math.max(1, cfg.getInt("reset.auto.interval-hours", 6)),
                "full".equalsIgnoreCase(cfg.getString("reset.auto.mode", "soft")),
                List.copyOf(warns));
    }

    private static NavalSettings loadNaval(FileConfiguration cfg) {
        RamSettings ram = new RamSettings(
                Math.max(0.05, cfg.getDouble("naval.ram.min-closing-speed", 0.5)),
                Math.max(0, cfg.getDouble("naval.ram.damage-per-speed", 8.0)),
                Math.max(0, cfg.getDouble("naval.ram.max-damage", 10.0)),
                Math.min(1.0, Math.max(0, cfg.getDouble("naval.ram.defender-share", 0.75))),
                Math.min(1.0, Math.max(0, cfg.getDouble("naval.ram.rider-bleed", 0.3))),
                Math.max(0, cfg.getDouble("naval.ram.knockback", 0.8)),
                Math.max(250, cfg.getLong("naval.ram.pair-cooldown-millis", 1500)),
                Math.max(0, cfg.getDouble("naval.ram.power-per-level", 0.25)));
        HullSettings hull = new HullSettings(
                Math.max(1.0, cfg.getDouble("naval.hull.max-hp", 10.0)),
                Math.max(0, cfg.getInt("naval.hull.combat-tag-seconds", 60)),
                Math.max(0.01, cfg.getDouble("naval.hull.regen-per-second", 0.5)),
                Math.max(1, cfg.getInt("naval.hull.wounded-slow-seconds", 4)),
                Math.min(1.0, Math.max(0.05, cfg.getDouble("naval.hull.wounded-speed-factor", 0.7))),
                Math.max(0, cfg.getDouble("naval.hull.speed-penalty-per-hp", 0.03)));
        SurgeSettings surge = new SurgeSettings(
                Math.max(1.0, cfg.getDouble("naval.surge.boost-factor", 1.8)),
                Math.max(1, cfg.getInt("naval.surge.cooldown-seconds", 9)));
        HarpoonSettings harpoon = new HarpoonSettings(
                Math.max(4, cfg.getDouble("naval.harpoon.range", 24)),
                Math.max(0.5, cfg.getDouble("naval.harpoon.pull-seconds", 2.0)),
                Math.max(0.05, cfg.getDouble("naval.harpoon.pull-strength", 0.35)));
        HudSettings hud = new HudSettings(
                cfg.getBoolean("naval.hud.enabled", true),
                Math.max(2, cfg.getInt("naval.hud.period-ticks", 10)));
        RepairSettings repair = new RepairSettings(
                Math.max(0, cfg.getDouble("naval.repair.cost-per-hp", 2.0)));
        return new NavalSettings(ram, hull, surge,
                Math.max(1, cfg.getInt("naval.chainshot.slow-seconds", 6)),
                Math.min(1.0, Math.max(0.05, cfg.getDouble("naval.chainshot.speed-factor", 0.5))),
                Math.max(0, cfg.getDouble("naval.hullpiercer.damage", 9.0)),
                Math.min(1.0, Math.max(0.0, cfg.getDouble("naval.hullpiercer.toughness-factor", 0.25))),
                harpoon, hud, repair);
    }

    private static BoatSettings loadBoat(FileConfiguration cfg, Logger log) {
        Map<Integer, BoatLevel> levels = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("boat.levels");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    log.warning("boat.levels." + key + " is not a number — skipped");
                    continue;
                }
                levels.put(level, new BoatLevel(
                        sec.getString(key + ".name", "Level " + level),
                        Math.max(1.0, sec.getDouble(key + ".speed", 1.0)),
                        Math.max(0, sec.getInt(key + ".shield", 0)),
                        Math.max(1.0, sec.getDouble(key + ".toughness", 1.0)),
                        Math.max(0, sec.getDouble(key + ".hp", 0.0)),
                        Math.max(0, sec.getInt(key + ".cost", 0))));
            }
        }
        if (!levels.containsKey(0)) {
            levels.put(0, new BoatLevel("Rowboat", 1.0, 0, 1.0, 0.0));
        }
        StatPointSettings statPoints = new StatPointSettings(
                Math.max(0, cfg.getDouble("boat.stat-points.speed-per-point", 0.03)),
                Math.max(0, cfg.getDouble("boat.stat-points.toughness-per-point", 0.15)),
                Math.max(0, cfg.getDouble("boat.stat-points.hp-per-point", 2.0)),
                Math.max(0, cfg.getDouble("boat.stat-points.ram-power-per-point", 0.10)),
                Math.max(0, cfg.getInt("boat.stat-points.reset-cost-per-point", 8)));
        return new BoatSettings(Math.max(0.1, cfg.getDouble("boat.speed-cap-base", 0.45)),
                Map.copyOf(levels), statPoints);
    }

    private static Material matchMaterial(String name, Material fallback, String path, Logger log) {
        if (name == null) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            log.warning(path + " '" + name + "' is not a material — using " + fallback);
            return fallback;
        }
        return mat;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
