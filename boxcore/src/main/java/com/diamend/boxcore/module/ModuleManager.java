package com.diamend.boxcore.module;

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

    public void enableAll() {
        for (BoxModule module : registered.values()) {
            if (!isConfigured(module)) {
                plugin.getLogger().info("Module '" + module.id() + "' is disabled in config.");
                continue;
            }
            try {
                module.enable();
                active.put(module.id(), module);
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Module '" + module.id() + "' failed to enable and was skipped", ex);
            }
        }
    }

    public void disableAll() {
        List<BoxModule> modules = new ArrayList<>(active.values());
        java.util.Collections.reverse(modules);
        for (BoxModule module : modules) {
            try {
                module.disable();
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Module '" + module.id() + "' threw while disabling", ex);
            }
        }
        active.clear();
    }

    public void reloadAll() {
        for (BoxModule module : active.values()) {
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
