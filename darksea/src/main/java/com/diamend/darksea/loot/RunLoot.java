package com.diamend.darksea.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * The "gathered this run" mark. Every item the sea hands out — chest loot,
 * mob drops — is stamped in its PersistentDataContainer. A player who goes
 * down in the Dark Sea spills only their stamped items (their run's haul);
 * reaching the calm-water sanctuary banks them (the stamp is cleared) so they
 * become permanently theirs. The tag rides the item through anvils and trades,
 * so it can't be laundered by renaming.
 */
public final class RunLoot {

    /** Byte PDC tag: present means the item was gathered on the current run. */
    public static final NamespacedKey KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:run_loot"));

    private RunLoot() {
    }

    /** Stamps an item as gathered this run. Returns the same stack for chaining. */
    public static ItemStack tag(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether an item still carries the run mark (unbanked haul). */
    public static boolean isRunLoot(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /**
     * Banks an item — strips the run mark so it's safe to keep. Returns whether
     * the item actually changed (so callers can count what was banked).
     */
    public static boolean bank(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) {
            return false;
        }
        meta.getPersistentDataContainer().remove(KEY);
        item.setItemMeta(meta);
        return true;
    }
}
