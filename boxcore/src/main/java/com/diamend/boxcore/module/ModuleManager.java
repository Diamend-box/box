package com.diamend.boxcore.module;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers, enables and tears down {@link BoxModule}s.
 *
 * <p>Enabling is fail-soft on purpose: a module that blows up (bad config, a
 * missing optional dependency) is logged and skipped rather than taking the
 * whole plugin — and the server owner's other features — down with it.
 */
public class ModuleManager {

    private final Plugin plugin;
    private final Map<String, BoxModule> registered = new LinkedHashMap<>();
    private final Map<String, BoxModule> active = new LinkedHashMap<>();
    /** Listeners each module registered, so they can be taken back down. */
    private final Map<String, List<Listener>> listeners = new LinkedHashMap<>();

    public ModuleManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(BoxModule module) {
        registered.put(module.id(), module);
    }

    /** Whether the config switches this module on. */
    public boolean isConfigured(BoxModule module) {
        return plugin.getConfig().getBoolean("modules." + module.id() + ".enabled",
                module.enabledByDefault());
    }

    /**
     * Registers a listener on a module's behalf.
     *
     * <p>Modules must register through here rather than straight at the plugin
     * manager. A listener registered directly cannot be found again, so
     * switching a module off would leave its handlers running and the feature
     * half-alive — which is the difference between a module being disabled and
     * a module merely looking disabled.
     */
    public void listen(BoxModule module, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.computeIfAbsent(module.id(), id -> new ArrayList<>()).add(listener);
    }

    public void enableAll() {
        for (BoxModule module : registered.values()) {
            if (!isConfigured(module)) {
                plugin.getLogger().info("Module '" + module.id() + "' is disabled in config.");
                continue;
            }
            enable(module);
        }
    }

    /** Brings one module up. Returns whether it is now running. */
    public boolean enable(BoxModule module) {
        if (module == null || active.containsKey(module.id())) {
            return module != null && active.containsKey(module.id());
        }
        try {
            module.enable();
            active.put(module.id(), module);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Module '" + module.id() + "' failed to enable and was skipped", ex);
            // Half an enable is worse than none: drop anything it managed to
            // hook up before it threw.
            unlisten(module.id());
            return false;
        }
    }

    /** Takes one module back down, listeners included. */
    public void disable(BoxModule module) {
        if (module == null || !active.containsKey(module.id())) {
            return;
        }
        try {
            module.disable();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Module '" + module.id() + "' threw while disabling", ex);
        }
        unlisten(module.id());
        active.remove(module.id());
    }

    private void unlisten(String id) {
        List<Listener> registeredListeners = listeners.remove(id);
        if (registeredListeners == null) {
            return;
        }
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
    }

    /**
     * Switches a module on or off and writes the choice to config.
     *
     * <p>The config write is the point: a module toggled in game and not
     * written down would come back on the next restart, which is the kind of
     * surprise that costs an evening.
     *
     * @return whether the module is running afterwards
     */
    public boolean setEnabled(String id, boolean enabled) {
        BoxModule module = registered.get(id);
        if (module == null) {
            return false;
        }
        plugin.getConfig().set("modules." + id + ".enabled", enabled);
        plugin.saveConfig();
        if (enabled) {
            return enable(module);
        }
        disable(module);
        return false;
    }

    public void disableAll() {
        List<BoxModule> modules = new ArrayList<>(active.values());
        java.util.Collections.reverse(modules);
        for (BoxModule module : modules) {
            disable(module);
        }
        active.clear();
        listeners.clear();
    }

    /**
     * Re-reads config for every module, and applies any change to whether they
     * should be running at all.
     *
     * <p>Editing {@code modules.<id>.enabled} used to need a restart, because
     * reload only spoke to modules that were already up. Switching one off in
     * the file and reloading appeared to do nothing, which reads as the toggle
     * being broken rather than as it needing a restart.
     */
    public void reloadAll() {
        for (BoxModule module : registered.values()) {
            boolean wanted = isConfigured(module);
            boolean running = active.containsKey(module.id());
            if (wanted && !running) {
                enable(module);
                continue;
            }
            if (!wanted && running) {
                disable(module);
                continue;
            }
            if (!running) {
                continue;
            }
            try {
                module.reload();
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Module '" + module.id() + "' threw while reloading", ex);
            }
        }
    }

    public boolean isActive(String id) {
        return active.containsKey(id);
    }

    public BoxModule get(String id) {
        return active.get(id);
    }

    /** Returns the active module of the given type, or null. */
    public <T extends BoxModule> T get(Class<T> type) {
        for (BoxModule module : active.values()) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        return null;
    }

    public List<BoxModule> registered() {
        return new ArrayList<>(registered.values());
    }

    public List<BoxModule> activeModules() {
        return new ArrayList<>(active.values());
    }

    /** Hub icons contributed by the active modules, in registration order. */
    public List<HubEntry> hubEntries() {
        List<HubEntry> entries = new ArrayList<>();
        for (BoxModule module : active.values()) {
            HubEntry entry = module.hubEntry();
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
