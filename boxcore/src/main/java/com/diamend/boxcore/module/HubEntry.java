package com.diamend.boxcore.module;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An icon a module contributes to the {@code /box} hub.
 *
 * @param slot   preferred slot in the hub inventory; modules are laid out in
 *               registration order when two ask for the same slot
 * @param icon   the material to show
 * @param name   display name (MiniMessage / legacy colour codes)
 * @param lore   static lore lines shown above {@link #dynamicLore}
 * @param dynamicLore per-player lines (e.g. "3 points available"), may be null
 * @param open   what clicking the icon does
 */
public record HubEntry(int slot,
                       Material icon,
                       String name,
                       List<String> lore,
                       Function<Player, List<String>> dynamicLore,
                       Consumer<Player> open) {

    public HubEntry(int slot, Material icon, String name, List<String> lore, Consumer<Player> open) {
        this(slot, icon, name, lore, null, open);
    }

    public List<String> loreFor(Player player) {
        if (dynamicLore == null) {
            return lore;
        }
        List<String> extra = dynamicLore.apply(player);
        if (extra == null || extra.isEmpty()) {
            return lore;
        }
        java.util.List<String> combined = new java.util.ArrayList<>(lore);
        combined.addAll(extra);
        return combined;
    }
}
