package com.diamend.boxcore.skill.perk;

import com.diamend.boxcore.util.Messages;
import com.diamend.boxcore.util.Registries;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Runs the {@link Perk} effects — the parts of the skill trees that aren't
 * expressible as a vanilla attribute or potion.
 *
 * <p>Every handler reads the player's cached {@link PerkSet} and returns
 * immediately when the relevant perk isn't held, so a server whose trees grant
 * no perks pays almost nothing for having this registered.
 */
public class PerkListener implements Listener {

    /** How long {@link Perk#LAST_BREATH} waits before it can fire again. */
    private static final long LAST_BREATH_COOLDOWN_SECONDS = 30L;

    /** Health fraction at or below which "badly hurt" perks trigger. */
    private static final double HURT_THRESHOLD = 0.34;

    /** Crops that replant themselves, and the drop that pays for the seed. */
    private static final Map<Material, Material> REPLANTABLE = replantable();

    /** What {@link Perk#GAPPLE_BOOST} lengthens after a golden apple. */
    private static final String[] GAPPLE_EFFECTS =
            { "absorption", "regeneration", "fire_resistance", "resistance" };

    /**
     * Block drops {@link Perk#AUTO_SMELT} will run through a furnace recipe.
     *
     * <p>Deliberately a whitelist: the recipe book would happily turn a stack of
     * oak logs into charcoal, which is not what anyone means by "auto-smelt".
     */
    private static final Set<Material> SMELTABLE = materials(
            "RAW_IRON", "RAW_GOLD", "RAW_COPPER",
            "IRON_ORE", "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
            "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "NETHER_GOLD_ORE",
            "ANCIENT_DEBRIS", "SAND", "RED_SAND", "CLAY_BALL",
            "COBBLESTONE", "COBBLED_DEEPSLATE", "NETHERRACK");

    private static final Set<Material> GOLDEN_APPLES =
            materials("GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE");

    private final Plugin plugin;
    private final PerkService perks;
    private final Messages messages;

    /** Built on first use: block drop → smelted result. */
    private Map<Material, ItemStack> smelting;
    /** Built on first use: the potion effects worth shortening or resisting. */
    private Set<PotionEffectType> harmful;

    public PerkListener(Plugin plugin, PerkService perks, Messages messages) {
        this.plugin = plugin;
        this.perks = perks;
        this.messages = messages;
    }

    // ------------------------------------------------------------------
    // Dealing damage
    // ------------------------------------------------------------------

    /**
     * All the damage-scaling perks, in one pass. They multiply together, so
     * hitting a wounded player with a bow can stack Finisher, Headhunter and
     * Marksman — which is the point of specialising.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        Player player = attacker(event);
        if (player == null || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        PerkSet held = perks.of(player);
        if (held.isEmpty()) {
            return;
        }
        double multiplier = 1.0;

        double wounded = held.value(Perk.FINISHER);
        if (wounded > 0 && healthFraction(victim) <= HURT_THRESHOLD) {
            multiplier *= 1.0 + wounded;
        }
        double versusPlayers = held.value(Perk.PLAYER_DAMAGE);
        if (versusPlayers > 0 && victim instanceof Player) {
            multiplier *= 1.0 + versusPlayers;
        }
        double ranged = held.value(Perk.PROJECTILE_DAMAGE);
        if (ranged > 0 && event.getDamager() instanceof Projectile) {
            multiplier *= 1.0 + ranged;
        }
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }

        double venom = held.value(Perk.VENOM_STRIKE);
        if (venom > 0 && event.getDamager() instanceof Player) {
            givePotion(victim, "poison", (int) (venom * 20), 0);
        }
    }

    /** Heals a share of the damage a melee hit actually landed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifesteal(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        double share = perks.of(player).value(Perk.LIFESTEAL);
        if (share <= 0) {
            return;
        }
        double healed = event.getFinalDamage() * share;
        if (healed <= 0) {
            return;
        }
        player.setHealth(Math.min(maxHealthOf(player), player.getHealth() + healed));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double chance = perks.of(player).chance(Perk.ARROW_SAVER);
        if (chance > 0 && roll(chance)) {
            event.setConsumeItem(false);
        }
    }

    /**
     * Runs at MONITOR and spawns the bonus loot itself rather than appending to
     * {@code getDrops()}. Both matter: the drop list is what collections count,
     * and a perk's bonus loot should be a bonus, not collection progress — the
     * same rule the ore and log bounties follow.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        PerkSet held = perks.of(killer);
        if (held.isEmpty()) {
            return;
        }

        double seconds = held.value(Perk.ADRENALINE);
        if (seconds > 0) {
            givePotion(killer, "speed", (int) (seconds * 20), 1);
        }

        double chance = held.chance(Perk.MOB_LOOT);
        if (chance <= 0 || !roll(chance)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        for (ItemStack drop : new ArrayList<>(event.getDrops())) {
            if (drop != null && !drop.getType().isAir()) {
                dead.getWorld().dropItemNaturally(dead.getLocation(), drop.clone());
            }
        }
    }

    // ------------------------------------------------------------------
    // Taking damage
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PerkSet held = perks.of(player);
        if (held.isEmpty()) {
            return;
        }
        double remaining = player.getHealth() - event.getFinalDamage();

        if (held.has(Perk.SECOND_CHANCE) && remaining <= 0
                && perks.claim(player.getUniqueId(), Perk.SECOND_CHANCE,
                        (long) held.value(Perk.SECOND_CHANCE) * 60_000L)) {
            event.setCancelled(true);
            surviveKillingBlow(player);
            return;
        }

        double burst = held.value(Perk.LAST_BREATH);
        if (burst <= 0 || remaining <= 0) {
            return;
        }
        // Only when this hit is what put them in trouble — otherwise every
        // subsequent hit in the same fight would re-arm it.
        double max = maxHealthOf(player);
        boolean wasSafe = max <= 0 || player.getHealth() / max > HURT_THRESHOLD;
        if (!wasSafe || remaining / max > HURT_THRESHOLD) {
            return;
        }
        if (perks.claim(player.getUniqueId(), Perk.LAST_BREATH,
                LAST_BREATH_COOLDOWN_SECONDS * 1000L)) {
            givePotion(player, "speed", (int) (burst * 20), 1);
            givePotion(player, "resistance", (int) (burst * 20), 0);
        }
    }

    private void surviveKillingBlow(Player player) {
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        if (player.getHealth() < 4.0) {
            player.setHealth(Math.min(4.0, maxHealthOf(player)));
        }
        givePotion(player, "regeneration", 200, 1);
        givePotion(player, "absorption", 600, 0);
        givePotion(player, "resistance", 100, 1);
        messages.send(player, "second-chance");
        try {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Feedback only.
        }
    }

    /**
     * Shortens incoming debuffs.
     *
     * <p>The event can only be cancelled, not edited, so a shortened copy is
     * added in its place. Effects we add ourselves arrive with cause
     * {@code PLUGIN} and are skipped, which is also what stops this recursing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDebuff(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
            return;
        }
        PotionEffect incoming = event.getNewEffect();
        if (incoming == null || !harmful().contains(incoming.getType())) {
            return;
        }
        if (incoming.getDuration() <= 0 || incoming.getDuration() == PotionEffect.INFINITE_DURATION) {
            return;
        }
        double shorter = perks.of(player).value(Perk.DEBUFF_RESIST);
        if (shorter <= 0) {
            return;
        }
        int duration = (int) Math.max(1, incoming.getDuration() * Math.max(0.05, 1.0 - shorter));
        event.setCancelled(true);
        player.addPotionEffect(new PotionEffect(incoming.getType(), duration,
                incoming.getAmplifier(), incoming.isAmbient(), incoming.hasParticles(),
                incoming.hasIcon()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player thrower)) {
            return;
        }
        double stronger = perks.of(thrower).value(Perk.POTION_POWER);
        if (stronger <= 0) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            double intensity = event.getIntensity(affected);
            event.setIntensity(affected, Math.min(1.0, intensity * (1.0 + stronger)));
        }
    }

    /** Stretches the effects a golden apple just granted. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        if (!GOLDEN_APPLES.contains(event.getItem().getType())) {
            return;
        }
        Player player = event.getPlayer();
        double longer = perks.of(player).value(Perk.GAPPLE_BOOST);
        if (longer <= 0) {
            return;
        }
        // The effects aren't applied until after the event, so extend next tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (String name : GAPPLE_EFFECTS) {
                PotionEffectType type = Registries.potionEffect(name);
                PotionEffect active = type == null ? null : player.getPotionEffect(type);
                if (active == null || active.getDuration() == PotionEffect.INFINITE_DURATION) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(type,
                        (int) (active.getDuration() * (1.0 + longer)), active.getAmplifier(),
                        active.isAmbient(), active.hasParticles(), active.hasIcon()));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double saved = perks.of(player).value(Perk.HUNGER_SAVER);
        if (saved <= 0) {
            return;
        }
        event.setExhaustion((float) (event.getExhaustion() * Math.max(0.0, 1.0 - saved)));
    }

    // ------------------------------------------------------------------
    // Grinding
    // ------------------------------------------------------------------

    /**
     * Auto-smelt, bonus drops and crop replanting all hang off the same event.
     * {@code BlockDropItemEvent} is the right hook because it hands over the
     * drops that were actually produced — Fortune, Silk Touch and all.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        PerkSet held = perks.of(event.getPlayer());
        if (held.isEmpty()) {
            return;
        }
        Material broken = event.getBlockState().getType();

        if (held.has(Perk.AUTO_SMELT)) {
            for (Item item : event.getItems()) {
                ItemStack smelted = smelt(item.getItemStack());
                if (smelted != null) {
                    item.setItemStack(smelted);
                }
            }
        }

        double bounty = 0.0;
        if (isOre(broken)) {
            bounty = held.chance(Perk.ORE_BOUNTY);
        } else if (isLog(broken)) {
            bounty = held.chance(Perk.LOG_BOUNTY);
        }
        if (bounty > 0 && roll(bounty)) {
            Block block = event.getBlock();
            for (Item item : new ArrayList<>(event.getItems())) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                        item.getItemStack().clone());
            }
        }

        if (held.has(Perk.REPLANT)) {
            replant(event);
        }
    }

    /**
     * Puts a fully grown crop back in the ground, paying for it out of the
     * harvest so the perk is a convenience rather than a free seed.
     */
    private void replant(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        Material crop = state.getType();
        Material seed = REPLANTABLE.get(crop);
        if (seed == null) {
            return;
        }
        BlockData data = state.getBlockData();
        if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        if (!takeOne(event.getItems(), seed)) {
            return; // nothing to replant with — leave the ground bare
        }
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) {
                return; // somebody built there in the meantime
            }
            Ageable fresh = (Ageable) crop.createBlockData();
            fresh.setAge(0);
            // With physics on, a crop over ruined soil pops off by itself.
            block.setBlockData(fresh, true);
        });
    }

    /** Removes a single item of the given type from the drops. */
    private static boolean takeOne(List<Item> drops, Material type) {
        for (Iterator<Item> iterator = drops.iterator(); iterator.hasNext(); ) {
            Item item = iterator.next();
            ItemStack stack = item.getItemStack();
            if (stack.getType() != type) {
                continue;
            }
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            } else {
                iterator.remove();
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCastLine(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        double faster = perks.of(event.getPlayer()).value(Perk.FISHING_SPEED);
        if (faster <= 0) {
            return;
        }
        // Floor the reduction: instant bites would trivialise fishing loot.
        double keep = Math.max(0.2, 1.0 - faster);
        FishHook hook = event.getHook();
        try {
            int min = Math.max(1, (int) Math.round(hook.getMinWaitTime() * keep));
            int max = Math.max(min, (int) Math.round(hook.getMaxWaitTime() * keep));
            hook.setMinWaitTime(min);
            hook.setMaxWaitTime(max);
        } catch (Throwable ex) {
            // Wait-time setters have moved around between versions; a slower
            // bite is a far better outcome than a broken fishing rod.
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }
        double bonus = perks.of(event.getPlayer()).value(Perk.XP_BOOST);
        if (bonus <= 0) {
            return;
        }
        event.setAmount((int) Math.round(event.getAmount() * (1.0 + bonus)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /** The player behind a hit, whether they swung or shot. */
    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private static double maxHealthOf(LivingEntity entity) {
        Attribute attribute = Registries.attribute("max_health");
        if (attribute == null) {
            return entity.getHealth();
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? entity.getHealth() : instance.getValue();
    }

    private static double healthFraction(LivingEntity entity) {
        double max = maxHealthOf(entity);
        return max <= 0 ? 1.0 : entity.getHealth() / max;
    }

    private void givePotion(LivingEntity target, String effect, int ticks, int amplifier) {
        PotionEffectType type = Registries.potionEffect(effect);
        if (type != null && ticks > 0) {
            target.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, true, true));
        }
    }

    /**
     * Ores and logs are matched by name rather than by tag so that a modded or
     * datapack-added ore behaves like a vanilla one, and so a new wood type in
     * the next update needs no code change.
     */
    private static boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private static boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM")
                || name.endsWith("_WOOD") || name.endsWith("_HYPHAE");
    }

    private ItemStack smelt(ItemStack input) {
        if (input == null || !SMELTABLE.contains(input.getType())) {
            return null;
        }
        ItemStack result = smeltingRecipes().get(input.getType());
        if (result == null || result.getType().isAir()) {
            return null;
        }
        ItemStack smelted = result.clone();
        smelted.setAmount(Math.max(1, result.getAmount() * input.getAmount()));
        return smelted;
    }

    /**
     * Indexes the server's furnace recipes once. Reading them from the registry
     * rather than hard-coding pairs means a datapack that re-balances smelting
     * is respected for free.
     */
    private Map<Material, ItemStack> smeltingRecipes() {
        if (smelting != null) {
            return smelting;
        }
        Map<Material, ItemStack> map = new EnumMap<>(Material.class);
        try {
            Iterator<Recipe> recipes = Bukkit.recipeIterator();
            while (recipes.hasNext()) {
                Recipe recipe = recipes.next();
                if (!(recipe instanceof FurnaceRecipe furnace)) {
                    continue;
                }
                ItemStack result = furnace.getResult();
                if (result.getType().isAir()) {
                    continue;
                }
                for (Material input : inputsOf(furnace)) {
                    if (SMELTABLE.contains(input)) {
                        map.putIfAbsent(input, result);
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read the server's furnace recipes; auto-smelt is inactive.", ex);
        }
        smelting = map;
        return map;
    }

    private static List<Material> inputsOf(FurnaceRecipe recipe) {
        RecipeChoice choice = recipe.getInputChoice();
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            return materials.getChoices();
        }
        return List.of(recipe.getInput().getType());
    }

    private Set<PotionEffectType> harmful() {
        if (harmful != null) {
            return harmful;
        }
        Set<PotionEffectType> set = new HashSet<>();
        for (String name : new String[] { "poison", "wither", "slowness", "weakness",
                "mining_fatigue", "blindness", "nausea", "hunger", "levitation", "darkness",
                "unluck", "glowing" }) {
            PotionEffectType type = Registries.potionEffect(name);
            if (type != null) {
                set.add(type);
            }
        }
        harmful = set;
        return set;
    }

    private static Map<Material, Material> replantable() {
        Map<Material, Material> map = new EnumMap<>(Material.class);
        put(map, "WHEAT", "WHEAT_SEEDS");
        put(map, "CARROTS", "CARROT");
        put(map, "POTATOES", "POTATO");
        put(map, "BEETROOTS", "BEETROOT_SEEDS");
        put(map, "NETHER_WART", "NETHER_WART");
        return map;
    }

    private static void put(Map<Material, Material> map, String crop, String seed) {
        Material block = Material.getMaterial(crop);
        Material item = Material.getMaterial(seed);
        if (block != null && item != null) {
            map.put(block, item);
        }
    }

    /** Resolves material names, skipping any this server version doesn't have. */
    private static Set<Material> materials(String... names) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }
}
