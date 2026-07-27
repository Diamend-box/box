package com.diamend.boxcore.module;

import java.util.List;

/**
 * One self-contained feature of BoxCore.
 *
 * <p>BoxCore is meant to grow: skill trees and collections are the first two
 * modules, and anything added later (economy hooks, quests, staff tools…)
 * plugs in the same way — implement this, register it in
 * {@code BoxCorePlugin}, and it gains a config toggle, a slot in the
 * {@code /box} hub and a line in {@code /box modules} for free.
 *
 * <p>A module that throws while enabling is skipped and logged; the rest of the
 * plugin carries on without it.
 */
public interface BoxModule {

    /** Stable id, used as the config key under {@code modules.<id>}. */
    String id();

    /** Human-readable name for {@code /box modules}. */
    String displayName();

    /** Whether the module runs when the config says nothing about it. */
    default boolean enabledByDefault() {
        return true;
    }

    /** Called once, on plugin enable, if the module is switched on. */
    void enable() throws Exception;

    /** Called on plugin disable, for modules that were successfully enabled. */
    default void disable() {
    }

    /** Called by {@code /box reload}; re-read config-backed state here. */
    default void reload() {
    }

    /** Optional icon contributed to the {@code /box} hub menu. */
    default HubEntry hubEntry() {
        return null;
    }

    /** Optional extra lines shown under the module in {@code /box modules}. */
    default List<String> statusLines() {
        return List.of();
    }
}
