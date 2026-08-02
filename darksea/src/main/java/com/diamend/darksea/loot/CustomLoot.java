package com.diamend.darksea.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin-owned half of the loot pool: everything added in game through
 * {@code /ds loot}, kept in {@code loot-custom.yml} rather than mixed into
 * {@code loot.yml}.
 *
 * <p>Two files, not one, for a plain reason. loot.yml is hand-written and
 * heavily commented — the comments are the only record of why the shipped
 * weights are what they are, and no YAML serialiser preserves them. An editor
 * that rewrote that file would silently delete its own documentation the first
 * time anybody clicked a button. So the editor writes a file it entirely owns,
 * and the two are merged at load: shipped entries first, added entries after.
 *
 * <p>Immutable, and free of Bukkit except through {@link LootEntry}, so the
 * merge arithmetic is unit-testable.
 */
public record CustomLoot(Map<Key, List<LootEntry>> added) {

    /** Which table an addition belongs to: a tier, and base or vault. */
    public record Key(int tier, boolean vault) {
        @Override
        public String toString() {
            return tier + (vault ? "-vault" : "-base");
        }
    }

    public static CustomLoot empty() {
        return new CustomLoot(Map.of());
    }

    public List<LootEntry> entriesFor(Key key) {
        return added.getOrDefault(key, List.of());
    }

    /** This overlay with one table's list replaced wholesale. */
    public CustomLoot with(Key key, List<LootEntry> entries) {
        Map<Key, List<LootEntry>> copy = new LinkedHashMap<>(added);
        if (entries.isEmpty()) {
            copy.remove(key);
        } else {
            copy.put(key, List.copyOf(entries));
        }
        return new CustomLoot(Map.copyOf(copy));
    }

    /** This overlay with one more entry on the end of a table. */
    public CustomLoot plus(Key key, LootEntry entry) {
        List<LootEntry> entries = new ArrayList<>(entriesFor(key));
        entries.add(entry);
        return with(key, entries);
    }

    /** How many entries this overlay contributes in total — for the editor's counters. */
    public int size() {
        int total = 0;
        for (List<LootEntry> entries : added.values()) {
            total += entries.size();
        }
        return total;
    }

    /**
     * The shipped tables with this overlay's entries appended.
     *
     * <p>Appending rather than replacing is what makes the overlay safe: a
     * shipped table keeps its rolls, its cooldown and every line it had, and an
     * addition only ever dilutes the existing weights. An overlay entry for a
     * tier with no shipped table is dropped — there is no sensible rolls or
     * cooldown to invent, and silently inventing one would make a typo'd tier
     * number look like it worked.
     */
    public LootTables mergeInto(LootTables shipped) {
        if (added.isEmpty()) {
            return shipped;
        }
        Map<Integer, LootTable> base = new LinkedHashMap<>(shipped.base());
        Map<Integer, LootTable> vault = new LinkedHashMap<>(shipped.vault());
        for (Map.Entry<Key, List<LootEntry>> line : added.entrySet()) {
            Key key = line.getKey();
            Map<Integer, LootTable> target = key.vault() ? vault : base;
            LootTable table = target.get(key.tier());
            if (table == null) {
                continue;
            }
            List<LootEntry> merged = new ArrayList<>(table.entries());
            merged.addAll(line.getValue());
            target.put(key.tier(), new LootTable(table.tier(), table.rolls(),
                    table.refillCooldownMinutes(), List.copyOf(merged)));
        }
        return new LootTables(Map.copyOf(base), Map.copyOf(vault));
    }
}
