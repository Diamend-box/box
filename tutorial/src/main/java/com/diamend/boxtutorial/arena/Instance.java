package com.diamend.boxtutorial.arena;

import com.diamend.boxtutorial.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One player's copy of the tutorial arena.
 *
 * <p>Every instance is the same blueprint built at a different origin — index 0
 * at x=0, index 1 at x=512, and so on — so a hundred players can be in "their
 * own" tutorial while the server holds one world.
 *
 * <p>An instance is rebuilt from scratch when it's claimed rather than repaired
 * when it's released. Whatever the last player did to it stops mattering, which
 * is the only cheap way to be certain the next one starts where the steps
 * expect them to.
 */
public class Instance {

    private final Plugin plugin;
    private final ArenaBlueprint blueprint;
    private final int index;
    private final World world;
    private final Location origin;
    private final Random random = new Random();

    /** How many blocks have come out of each mine since it was last filled. */
    private final Map<String, Integer> mined = new HashMap<>();

    private UUID owner;
    private UUID villagerId;

    public Instance(Plugin plugin, ArenaBlueprint blueprint, World world, int index) {
        this.plugin = plugin;
        this.blueprint = blueprint;
        this.world = world;
        this.index = index;
        this.origin = new Location(world, index * (long) blueprint.spacing(),
                blueprint.floorY(), 0);
    }

    public int index() {
        return index;
    }

    public World world() {
        return world;
    }

    public Location origin() {
        return origin.clone();
    }

    public UUID owner() {
        return owner;
    }

    public boolean isFree() {
        return owner == null;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /** Where the player lands, standing on the floor and facing the trader. */
    public Location spawnPoint() {
        MineSpec.Offset offset = blueprint.spawnOffset();
        Location location = origin.clone().add(offset.x() + 0.5, offset.y(), offset.z() + 0.5);
        Location shop = shopPoint();
        location.setDirection(shop.toVector().subtract(location.toVector()));
        return location;
    }

    public Location shopPoint() {
        MineSpec.Offset offset = blueprint.shopOffset();
        return origin.clone().add(offset.x() + 0.5, offset.y(), offset.z() + 0.5);
    }

    /** True when this location is inside the arena's walls. */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().equals(world)) {
            return false;
        }
        double dx = Math.abs(location.getX() - origin.getX());
        double dz = Math.abs(location.getZ() - origin.getZ());
        double dy = location.getY() - origin.getY();
        return dx <= blueprint.radius() + 1 && dz <= blueprint.radius() + 1
                && dy >= -4 && dy <= blueprint.wallHeight() + 20;
    }

    /** Which mine, if any, that block belongs to. */
    public MineSpec mineAt(Block block) {
        if (block == null || !block.getWorld().equals(world)) {
            return null;
        }
        int x = block.getX() - origin.getBlockX();
        int y = block.getY() - origin.getBlockY();
        int z = block.getZ() - origin.getBlockZ();
        for (MineSpec mine : blueprint.mines()) {
            if (mine.contains(x, y, z)) {
                return mine;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /** Lays the whole arena out again: floor, walls, mines and the trader. */
    public void build() {
        int radius = blueprint.radius();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        // Clear the room first, so nothing a previous player left is inherited.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 1; y <= blueprint.wallHeight() + 6; y++) {
                    set(baseX + x, baseY + y, baseZ + z, Material.AIR);
                }
                set(baseX + x, baseY, baseZ + z, blueprint.floor());
            }
        }
        // A wall that can't be climbed or broken, so the void stays theoretical.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) != radius && Math.abs(z) != radius) {
                    continue;
                }
                for (int y = 1; y <= blueprint.wallHeight(); y++) {
                    set(baseX + x, baseY + y, baseZ + z, blueprint.wall());
                }
            }
        }
        for (MineSpec mine : blueprint.mines()) {
            fill(mine);
        }
        spawnTrader();
    }

    /** Fills one mine back up and forgets what was taken out of it. */
    public void fill(MineSpec mine) {
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (int x = mine.min().x(); x <= mine.max().x(); x++) {
            for (int y = mine.min().y(); y <= mine.max().y(); y++) {
                for (int z = mine.min().z(); z <= mine.max().z(); z++) {
                    set(baseX + x, baseY + y, baseZ + z, mine.roll(random));
                }
            }
        }
        mined.put(mine.id(), 0);
    }

    /**
     * Notes a block coming out of a mine, and refills it once enough have.
     *
     * <p>Counted rather than measured: reading a few hundred blocks every second
     * to ask whether a mine looks empty is work the break event already did.
     *
     * @return true when this block emptied it enough to trigger a refill
     */
    public boolean noteMined(MineSpec mine) {
        int taken = mined.merge(mine.id(), 1, Integer::sum);
        if (taken < mine.minedBeforeRefill()) {
            return false;
        }
        fill(mine);
        return true;
    }

    private void set(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            // No physics: a mine full of logs shouldn't cascade, and nothing
            // here needs an update to look right.
            block.setType(material, false);
        }
    }

    // ------------------------------------------------------------------
    // The trader
    // ------------------------------------------------------------------

    /** Spawns (or replaces) the villager and gives it the configured trades. */
    public void spawnTrader() {
        removeTrader();
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (TradeSpec trade : blueprint.trades()) {
            MerchantRecipe recipe = trade.toRecipe(blueprint.registry());
            if (recipe == null) {
                plugin.getLogger().warning("Trade '" + trade.id()
                        + "' refers to an item that isn't defined; it isn't being offered.");
                continue;
            }
            recipes.add(recipe);
        }
        try {
            Villager villager = world.spawn(shopPoint(), Villager.class, spawned -> {
                spawned.setAI(false);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setPersistent(true);
                spawned.setCollidable(false);
                spawned.setRemoveWhenFarAway(false);
                spawned.customName(Text.item(blueprint.shopName()));
                spawned.setCustomNameVisible(true);
                profession(spawned);
                if (!recipes.isEmpty()) {
                    spawned.setRecipes(recipes);
                }
            });
            villagerId = villager.getUniqueId();
        } catch (Throwable ex) {
            villagerId = null;
            plugin.getLogger().warning("Could not spawn the tutorial trader for instance "
                    + index + ": " + ex.getMessage());
        }
    }

    /**
     * Dresses the villager.
     *
     * <p>Looked up by key rather than named as a constant: professions and
     * villager types move between the API's enums and its registries from one
     * Minecraft version to the next, and this is cosmetic — worth a shrug, not
     * a plugin that won't load.
     */
    private void profession(Villager villager) {
        try {
            Villager.Profession profession = Registry.VILLAGER_PROFESSION
                    .get(NamespacedKey.minecraft(blueprint.shopProfession()));
            if (profession != null) {
                villager.setProfession(profession);
            }
            Villager.Type type = Registry.VILLAGER_TYPE
                    .get(NamespacedKey.minecraft(blueprint.shopVillagerType()));
            if (type != null) {
                villager.setVillagerType(type);
            }
            villager.setVillagerLevel(5);
        } catch (Throwable ignored) {
            // Cosmetic only.
        }
    }

    /** True when that entity is this instance's trader. */
    public boolean isTrader(Entity entity) {
        return entity != null && villagerId != null && villagerId.equals(entity.getUniqueId());
    }

    public void removeTrader() {
        try {
            for (Entity entity : world.getNearbyEntities(origin.clone().add(0, 2, 0),
                    blueprint.radius() + 2, blueprint.wallHeight() + 8, blueprint.radius() + 2)) {
                if (entity instanceof Villager) {
                    entity.remove();
                }
            }
        } catch (Throwable ex) {
            // Tidying up is never a reason to fail to build the arena.
            plugin.getLogger().fine("Could not sweep instance " + index + ": " + ex.getMessage());
        }
        villagerId = null;
    }

    /** Hands the instance back: no owner, no trader, nothing running. */
    public void release() {
        owner = null;
        mined.clear();
        removeTrader();
    }
}
