package com.diamend.boxcore.skill;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.gui.TreePickerMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import com.diamend.boxcore.skill.perk.PerkListener;
import com.diamend.boxcore.skill.perk.PerkService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * The skill-tree module: config-driven trees whose nodes are bought with skill
 * points and grant lasting attribute, potion and permission effects.
 */
public class SkillsModule implements BoxModule {

    private final BoxCorePlugin plugin;
    private SkillTreeManager treeManager;
    private PerkService perkService;
    private EffectApplier effectApplier;
    private SkillService service;
    private BukkitTask refreshTask;

    public SkillsModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "skills";
    }

    @Override
    public String displayName() {
        return "Skill trees";
    }

    @Override
    public void enable() {
        this.treeManager = new SkillTreeManager(plugin);
        this.treeManager.load();
        this.perkService = new PerkService(plugin.profiles(), treeManager);
        this.effectApplier = new EffectApplier(plugin, treeManager, plugin.profiles(), perkService);
        this.service = new SkillService(plugin, treeManager, plugin.profiles(), effectApplier,
                plugin.messages());

        plugin.getServer().getPluginManager().registerEvents(
                new SkillListener(plugin, plugin.profiles(), service, effectApplier), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PerkListener(plugin, perkService, plugin.messages()), plugin);

        startRefreshTask();
        perkService.startMending(plugin);

        // Catch anyone already online (a /reload, or a module re-enable).
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            service.reconcile(plugin.profiles().get(player.getUniqueId()));
            effectApplier.apply(player);
        }
    }

    @Override
    public void disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (perkService != null) {
            perkService.stop();
            perkService.invalidateAll();
        }
        if (effectApplier != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                effectApplier.clear(player);
            }
        }
    }

    @Override
    public void reload() {
        treeManager.load();
        // The trees the totals were computed from just changed underneath them.
        perkService.invalidateAll();
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        startRefreshTask();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            service.reconcile(plugin.profiles().get(player.getUniqueId()));
            effectApplier.apply(player);
        }
    }

    /**
     * Periodically re-applies potion effects. Other plugins (and milk buckets)
     * clear effects wholesale; without this a player could lose a perk they
     * paid for until their next relog.
     */
    private void startRefreshTask() {
        int seconds = plugin.getConfig().getInt("skills.effect-refresh-seconds", 30);
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                effectApplier.refreshPotions(player);
            }
        }, ticks, ticks);
    }

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(11, Material.ENCHANTED_BOOK, "<light_purple><bold>Skill Trees",
                List.of("<gray>Spend skill points on lasting", "<gray>upgrades to your character.", ""),
                player -> List.of(
                        "<gray>Available: <white>"
                                + plugin.profiles().get(player.getUniqueId()).getAvailablePoints()
                                + " <gray>point(s)",
                        "<gray>Spent: <white>"
                                + plugin.profiles().get(player.getUniqueId()).getPointsSpent(),
                        "",
                        "<yellow>Click to open"),
                player -> new TreePickerMenu(plugin, this).open(player));
    }

    @Override
    public List<String> statusLines() {
        return List.of("<gray>" + treeManager.treeCount() + " tree(s), "
                + treeManager.nodeCount() + " node(s)"
                + (treeManager.warnings().isEmpty()
                ? "" : " <red>(" + treeManager.warnings().size() + " config warning(s))"));
    }

    public SkillTreeManager trees() {
        return treeManager;
    }

    public SkillService service() {
        return service;
    }

    public EffectApplier effects() {
        return effectApplier;
    }

    public PerkService perks() {
        return perkService;
    }
}
