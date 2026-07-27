package com.diamend.boxcore.collection;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.gui.CollectionCategoryMenu;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.HubEntry;
import org.bukkit.Material;

import java.util.List;

/**
 * The collections module: a Hypixel SkyBlock-style record of everything a
 * player has ever gathered, whose tiers pay out skill points.
 */
public class CollectionsModule implements BoxModule {

    private final BoxCorePlugin plugin;
    private CollectionManager manager;
    private CollectionService service;
    private CollectionListener listener;

    public CollectionsModule(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "collections";
    }

    @Override
    public String displayName() {
        return "Collections";
    }

    @Override
    public void enable() {
        this.manager = new CollectionManager(plugin);
        this.manager.load();
        this.service = new CollectionService(plugin, manager);
        this.listener = new CollectionListener(plugin, service);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public void reload() {
        manager.load();
        listener.reload();
    }

    @Override
    public HubEntry hubEntry() {
        return new HubEntry(13, Material.CHEST, "<gold><bold>Collections",
                List.of("<gray>Everything you've ever gathered.", "<gray>Each tier pays out skill points.", ""),
                player -> {
                    long total = plugin.profiles().get(player.getUniqueId()).getTotalCollected();
                    return List.of(
                            "<gray>Tracked: <white>" + manager.count() + " <gray>collection(s)",
                            "<gray>Gathered: <white>" + com.diamend.boxcore.util.Text.number(total),
                            "",
                            "<yellow>Click to open");
                },
                player -> new CollectionCategoryMenu(plugin, this).open(player));
    }

    @Override
    public List<String> statusLines() {
        return List.of("<gray>" + manager.count() + " collection(s) in "
                + manager.categories().size() + " category/categories"
                + (manager.warnings().isEmpty()
                ? "" : " <red>(" + manager.warnings().size() + " config warning(s))"));
    }

    public CollectionManager collections() {
        return manager;
    }

    public CollectionService service() {
        return service;
    }
}
