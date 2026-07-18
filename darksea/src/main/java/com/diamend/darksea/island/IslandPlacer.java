package com.diamend.darksea.island;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.util.Pos;
import com.diamend.darksea.zone.Zone;
import com.diamend.darksea.zone.ZoneManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Queued schematic pasting. Clipboards are loaded and scanned for marker
 * blocks off the main thread, the paste itself runs through FAWE's
 * async-safe EditSession (this is why FAWE is a hard dependency — vanilla
 * WorldEdit must not be driven off-thread), and the small finishing pass
 * (replacing markers, registering the island) hops back to the main thread.
 * One island is in flight at a time; progress goes to the invoking admin
 * and the console.
 */
public final class IslandPlacer {

    private record PasteJob(IslandTemplate template, int x, int y, int z,
                            boolean isSpawn, IslandInstance existing, Runnable afterFinalize,
                            boolean demo) {
    }

    /** Template name used for built-in demo islands (no schematic on disk). */
    private static final String DEMO_TEMPLATE = "demo";

    /** Marker positions found in a clipboard, relative to its origin. */
    private record ScanResult(List<Pos> chests, List<Pos> spawnPoints, Pos relMin, Pos relMax) {
    }

    private final DarkSeaPlugin plugin;
    private final IslandRegistry registry;
    private final Deque<PasteJob> queue = new ArrayDeque<>();
    private final Map<String, Clipboard> clipboardCache = new HashMap<>();
    private final Random rng = new Random();
    private boolean running;

    public IslandPlacer(DarkSeaPlugin plugin, IslandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public boolean busy() {
        return running;
    }

    /**
     * Fills every ring up to its configured island count. Re-runnable: only
     * the shortfall per ring is placed, existing islands are respected in
     * spacing, nothing is ever double-pasted.
     */
    public void generate(CommandSender sender) {
        generate(sender, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #generate(CommandSender)} but places at most {@code limit}
     * islands this run (inner rings first). Lets small hosts fill the sea a
     * few islands at a time: {@code /ds generate 1} until done.
     */
    public void generate(CommandSender sender, int limit) {
        if (running) {
            plugin.messages().send(sender, "generate-busy");
            return;
        }
        DarkSeaSettings settings = plugin.settings();
        DarkSeaSettings.GenerationSettings gen = settings.generation();
        ZoneManager zones = plugin.zoneManager();
        Map<Integer, List<IslandTemplate>> templates =
                IslandTemplate.loadAll(new File(plugin.getDataFolder(), "schematics"), zones.maxTier(), plugin.getLogger());

        // Home island first if it hasn't landed yet.
        boolean spawnQueued = maybeQueueSpawn(sender, null);

        List<PlacementSampler.Point> existing = new ArrayList<>();
        for (IslandInstance island : registry.all()) {
            existing.add(new PlacementSampler.Point(island.origin().x(), island.origin().z()));
        }

        int queued = 0;
        for (int tier = 1; tier <= zones.maxTier() && queued < limit; tier++) {
            int want = gen.islandsPerRing().getOrDefault(tier, 0);
            int need = Math.min(want - registry.byTier(tier).size(), limit - queued);
            if (need <= 0) {
                continue;
            }
            List<IslandTemplate> pool = templates.get(tier);
            boolean demo = (pool == null || pool.isEmpty());
            if (demo && !gen.demoIslands()) {
                plugin.messages().send(sender, "generate-no-templates", "tier", String.valueOf(tier));
                continue;
            }
            Zone zone = zones.byTier(tier);
            if (zone == null) {
                continue;
            }
            double inner = zones.innerRadius(zone);
            double outer = zone.unbounded() ? gen.outerRadius() : zone.maxRadius();
            List<PlacementSampler.Point> points = PlacementSampler.sample(rng,
                    settings.centerX(), settings.centerZ(), inner, outer, gen.ringBorderMargin(),
                    need, gen.minIslandGap(), existing, 500);
            if (points.size() < need) {
                plugin.messages().send(sender, "generate-shortfall",
                        "tier", String.valueOf(tier),
                        "placed", String.valueOf(points.size()),
                        "wanted", String.valueOf(need));
            }
            for (PlacementSampler.Point point : points) {
                int px = (int) Math.round(point.x());
                int pz = (int) Math.round(point.z());
                if (demo) {
                    queue.add(demoJob(tier, px, pz, false, null, null));
                } else {
                    IslandTemplate template = weightedPick(pool);
                    int y = template.pasteY() != null ? template.pasteY() : gen.pasteY();
                    queue.add(new PasteJob(template, px, y, pz, false, null, null, false));
                }
                existing.add(point);
                queued++;
            }
        }

        if (queued == 0 && !spawnQueued) {
            plugin.messages().send(sender, "generate-none");
            return;
        }
        if (queued > 0) {
            plugin.messages().send(sender, "generate-started", "count", String.valueOf(queued));
        }
        pump(sender);
    }

    /**
     * Queues the home-island paste if it hasn't happened yet and a schematic
     * exists. Returns whether a job was queued. {@code afterDone} runs after
     * the spawn paste finalizes (used by the full reset to chain generation).
     */
    public boolean maybeQueueSpawn(CommandSender sender, Runnable afterDone) {
        if (registry.spawnPasted()) {
            if (afterDone != null) {
                afterDone.run();
            }
            return false;
        }
        DarkSeaSettings settings = plugin.settings();
        IslandTemplate spawn = findSpawnTemplate();
        if (spawn == null) {
            if (!settings.generation().demoIslands()) {
                plugin.messages().send(sender, "spawn-schem-missing");
                if (afterDone != null) {
                    afterDone.run();
                }
                return false;
            }
            queue.add(demoJob(0, settings.centerX(), settings.centerZ(), true, null, afterDone));
            pump(sender);
            return true;
        }
        int y = spawn.pasteY() != null ? spawn.pasteY() : settings.generation().pasteY();
        queue.add(new PasteJob(spawn, settings.centerX(), y, settings.centerZ(), true, null, afterDone, false));
        pump(sender);
        return true;
    }

    /**
     * Soft reset: re-paste the home island and every registered island over
     * itself. Positions never change; damage heals and chests restock.
     */
    public void repasteAll(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, "generate-busy");
            return;
        }
        DarkSeaSettings settings = plugin.settings();
        Map<Integer, List<IslandTemplate>> templates = IslandTemplate.loadAll(
                new File(plugin.getDataFolder(), "schematics"), plugin.zoneManager().maxTier(), plugin.getLogger());

        IslandTemplate spawn = findSpawnTemplate();
        if (registry.spawnPasted()) {
            if (spawn != null) {
                int y = spawn.pasteY() != null ? spawn.pasteY() : settings.generation().pasteY();
                queue.add(new PasteJob(spawn, settings.centerX(), y, settings.centerZ(), true, null, null, false));
            } else if (settings.generation().demoIslands()) {
                queue.add(demoJob(0, settings.centerX(), settings.centerZ(), true, null, null));
            }
        }

        int count = 0;
        for (IslandInstance island : registry.all()) {
            Pos origin = island.origin();
            if (DEMO_TEMPLATE.equals(island.template())) {
                queue.add(demoJob(island.tier(), origin.x(), origin.z(), false, island, null));
                count++;
                continue;
            }
            IslandTemplate template = findTemplate(templates, island.tier(), island.template());
            if (template == null) {
                plugin.getLogger().warning("Soft reset: no schematic named '" + island.template()
                        + "' for tier " + island.tier() + " — island " + island.id() + " skipped");
                continue;
            }
            queue.add(new PasteJob(template, origin.x(), origin.y(), origin.z(), false, island, null, false));
            count++;
        }
        plugin.messages().send(sender, "reset-soft-started", "count", String.valueOf(count));
        pump(sender);
    }

    private IslandTemplate findSpawnTemplate() {
        List<IslandTemplate> spawns = IslandTemplate.loadDirectory(
                new File(plugin.getDataFolder(), "schematics/spawn"), 0, plugin.getLogger());
        return spawns.isEmpty() ? null : spawns.get(0);
    }

    private IslandTemplate findTemplate(Map<Integer, List<IslandTemplate>> templates, int tier, String name) {
        List<IslandTemplate> pool = templates.get(tier);
        if (pool == null) {
            return null;
        }
        for (IslandTemplate template : pool) {
            if (template.name().equals(name)) {
                return template;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Built-in demo islands (no schematic required)
    // ------------------------------------------------------------------

    /** A synthetic paste job that builds a plain sand-platform island in code. */
    private PasteJob demoJob(int tier, int x, int z, boolean isSpawn,
                             IslandInstance existing, Runnable afterFinalize) {
        IslandTemplate synthetic = new IslandTemplate(DEMO_TEMPLATE, tier, null, 1, null);
        return new PasteJob(synthetic, x, plugin.settings().seaLevel(), z,
                isSpawn, existing, afterFinalize, true);
    }

    /**
     * Demo builds gutted a 1GB live server in first testing: every island
     * force-generated its chunks synchronously on the main thread, and the
     * crash that followed threw away the un-saved chunks (leaving registry
     * entries pointing at open water). So: pre-generate the island's chunks
     * ASYNC first, build only once they're ready, save-and-unload the far
     * chunks immediately (crash-proofing + memory back), and wait
     * {@code generation.demo-pace-ticks} before the next island.
     */
    private void buildDemoPaced(CommandSender sender, World world, PasteJob job) {
        int radius = demoRadius(job.isSpawn());
        int minCx = (job.x() - radius) >> 4, maxCx = (job.x() + radius) >> 4;
        int minCz = (job.z() - radius) >> 4, maxCz = (job.z() + radius) >> 4;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
                .whenComplete((done, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (err != null) {
                            throw new IllegalStateException("chunk load failed: " + err.getMessage(), err);
                        }
                        ScanResult scan = buildDemoIsland(world, job);
                        finalizeJob(sender, world, job, scan);
                        if (!job.isSpawn()) {
                            for (int cx = minCx; cx <= maxCx; cx++) {
                                for (int cz = minCz; cz <= maxCz; cz++) {
                                    world.getChunkAt(cx, cz).unload(true);
                                }
                            }
                        }
                    } catch (RuntimeException ex) {
                        plugin.getLogger().severe("Building demo island failed: " + ex);
                        feedback(sender, "paste-failed", "template", DEMO_TEMPLATE,
                                "error", String.valueOf(ex.getMessage()));
                    }
                    int pace = Math.max(1, plugin.settings().generation().demoPaceTicks());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> next(sender), pace);
                }));
    }

    private static int demoRadius(boolean isSpawn) {
        return isSpawn ? 6 : 4;
    }

    /**
     * Builds a small sand platform poking above the waterline, directly with
     * the block API (no schematic, no WorldEdit). Returns marker positions
     * relative to the job origin (y == sea level) so the normal finalize step
     * can place the chest and register the island. Home islands get a larger,
     * loot-free platform; tier islands get one chest and one spawn point.
     */
    private ScanResult buildDemoIsland(World world, PasteJob job) {
        int seaLevel = plugin.settings().seaLevel();
        int radius = demoRadius(job.isSpawn());
        int topY = seaLevel + 2;   // sand surface, two blocks proud of the water
        int baseY = seaLevel - 2;  // solid footing sunk into the sea
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int wx = job.x() + dx;
                int wz = job.z() + dz;
                for (int y = baseY; y < topY; y++) {
                    world.getBlockAt(wx, y, wz).setType(Material.STONE, false);
                }
                world.getBlockAt(wx, topY, wz).setType(Material.SAND, false);
                for (int y = topY + 1; y <= topY + 3; y++) {  // open headroom
                    world.getBlockAt(wx, y, wz).setType(Material.AIR, false);
                }
            }
        }
        Pos relMin = new Pos(-radius, baseY - seaLevel, -radius);
        Pos relMax = new Pos(radius, topY - seaLevel, radius);
        if (job.isSpawn()) {
            return new ScanResult(List.of(), List.of(), relMin, relMax);
        }
        int relSurface = topY - seaLevel + 1;  // one above the sand
        List<Pos> chests = List.of(new Pos(2, relSurface, 2));
        List<Pos> spawnPoints = List.of(new Pos(-2, relSurface, -2));
        return new ScanResult(chests, spawnPoints, relMin, relMax);
    }

    private IslandTemplate weightedPick(List<IslandTemplate> pool) {
        int total = 0;
        for (IslandTemplate template : pool) {
            total += template.weight();
        }
        int roll = rng.nextInt(total);
        for (IslandTemplate template : pool) {
            roll -= template.weight();
            if (roll < 0) {
                return template;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ------------------------------------------------------------------
    // Queue machinery
    // ------------------------------------------------------------------

    private void pump(CommandSender sender) {
        if (running || queue.isEmpty()) {
            return;
        }
        running = true;
        next(sender);
    }

    private void next(CommandSender sender) {
        PasteJob job = queue.poll();
        if (job == null) {
            running = false;
            clipboardCache.clear();
            feedback(sender, "generate-done");
            return;
        }
        DarkSeaSettings settings = plugin.settings();
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) {
            queue.clear();
            running = false;
            feedback(sender, "world-missing");
            return;
        }
        if (job.demo()) {
            buildDemoPaced(sender, world, job);
            return;
        }

        Material chestMarker = settings.generation().chestMarker();
        Material mobMarker = settings.generation().mobMarker();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Clipboard clipboard = loadClipboard(job.template().file());
                ScanResult scan = scanMarkers(clipboard, chestMarker, mobMarker);
                paste(world, clipboard, job.x(), job.y(), job.z());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        finalizeJob(sender, world, job, scan);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().severe("Finalizing paste of " + job.template().name()
                                + " failed: " + ex);
                        feedback(sender, "paste-failed",
                                "template", job.template().name(), "error", String.valueOf(ex.getMessage()));
                    }
                    next(sender);
                });
            } catch (Exception ex) {
                plugin.getLogger().severe("Pasting " + job.template().name() + " failed: " + ex);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    feedback(sender, "paste-failed",
                            "template", job.template().name(), "error", String.valueOf(ex.getMessage()));
                    next(sender);
                });
            }
        });
    }

    /** Main-thread: replace markers, register the island, report progress. */
    private void finalizeJob(CommandSender sender, World world, PasteJob job, ScanResult scan) {
        Pos origin = new Pos(job.x(), job.y(), job.z());
        List<Pos> chests = scan.chests().stream()
                .map(rel -> origin.offset(rel.x(), rel.y(), rel.z())).toList();
        List<Pos> spawnPoints = scan.spawnPoints().stream()
                .map(rel -> origin.offset(rel.x(), rel.y(), rel.z())).toList();

        for (Pos chest : chests) {
            placeChest(world, chest, origin);
        }
        for (Pos point : spawnPoints) {
            world.getBlockAt(point.x(), point.y(), point.z()).setType(Material.AIR);
        }

        if (job.isSpawn()) {
            int spawnY = world.getHighestBlockYAt(job.x(), job.z()) + 1;
            world.setSpawnLocation(job.x(), spawnY, job.z());
            registry.setSpawnPasted(true);
            plugin.getLogger().info("Home island pasted at (" + job.x() + ", " + job.z()
                    + "); world spawn set to Y " + spawnY);
        } else if (job.existing() != null) {
            job.existing().clearRefills();
            registry.save();
            feedback(sender, "generate-progress",
                    "id", job.existing().id(), "template", job.template().name(),
                    "x", String.valueOf(job.x()), "z", String.valueOf(job.z()),
                    "remaining", String.valueOf(queue.size()));
        } else {
            Pos min = origin.offset(scan.relMin().x(), scan.relMin().y(), scan.relMin().z());
            Pos max = origin.offset(scan.relMax().x(), scan.relMax().y(), scan.relMax().z());
            IslandInstance island = new IslandInstance(registry.nextId(job.template().tier()),
                    job.template().name(), job.template().tier(), origin, min, max, chests, spawnPoints);
            registry.add(island);
            feedback(sender, "generate-progress",
                    "id", island.id(), "template", job.template().name(),
                    "x", String.valueOf(job.x()), "z", String.valueOf(job.z()),
                    "remaining", String.valueOf(queue.size()));
        }

        if (job.afterFinalize() != null) {
            job.afterFinalize().run();
        }
    }

    /** Chest markers become real chests facing away from the island's center. */
    private void placeChest(World world, Pos pos, Pos origin) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        block.setType(Material.CHEST);
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            int dx = pos.x() - origin.x();
            int dz = pos.z() - origin.z();
            BlockFace facing;
            if (Math.abs(dx) >= Math.abs(dz)) {
                facing = dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
            } else {
                facing = dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            }
            directional.setFacing(facing);
            block.setBlockData(directional);
        }
    }

    // ------------------------------------------------------------------
    // WorldEdit calls (async-safe under FAWE)
    // ------------------------------------------------------------------

    private Clipboard loadClipboard(File file) throws IOException {
        Clipboard cached = clipboardCache.get(file.getAbsolutePath());
        if (cached != null) {
            return cached;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("unrecognized schematic format: " + file.getName());
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            clipboardCache.put(file.getAbsolutePath(), clipboard);
            return clipboard;
        }
    }

    private ScanResult scanMarkers(Clipboard clipboard, Material chestMarker, Material mobMarker) {
        String chestId = "minecraft:" + chestMarker.getKey().getKey();
        String mobId = "minecraft:" + mobMarker.getKey().getKey();
        BlockVector3 origin = clipboard.getOrigin();
        List<Pos> chests = new ArrayList<>();
        List<Pos> spawnPoints = new ArrayList<>();
        for (BlockVector3 point : clipboard.getRegion()) {
            String id = clipboard.getBlock(point).getBlockType().getId();
            if (id.equals(chestId)) {
                BlockVector3 rel = point.subtract(origin);
                chests.add(new Pos(rel.x(), rel.y(), rel.z()));
            } else if (id.equals(mobId)) {
                BlockVector3 rel = point.subtract(origin);
                spawnPoints.add(new Pos(rel.x(), rel.y(), rel.z()));
            }
        }
        BlockVector3 relMin = clipboard.getRegion().getMinimumPoint().subtract(origin);
        BlockVector3 relMax = clipboard.getRegion().getMaximumPoint().subtract(origin);
        return new ScanResult(chests, spawnPoints,
                new Pos(relMin.x(), relMin.y(), relMin.z()),
                new Pos(relMax.x(), relMax.y(), relMax.z()));
    }

    private void paste(World bukkitWorld, Clipboard clipboard, int x, int y, int z) throws Exception {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(operation);
        }
    }

    /** Progress goes to the invoking admin while they're online, always to console. */
    private void feedback(CommandSender sender, String key, String... placeholders) {
        if (sender instanceof Player player) {
            if (player.isOnline()) {
                plugin.messages().send(player, key, placeholders);
            }
        } else if (sender != null) {
            plugin.messages().send(sender, key, placeholders);
            return; // console sender: already logged by sendMessage
        }
        plugin.getLogger().info(key + " " + String.join(" ", placeholders));
    }
}
