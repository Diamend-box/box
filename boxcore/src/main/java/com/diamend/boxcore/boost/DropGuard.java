package com.diamend.boxcore.boost;

import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Decides whether a drop may be multiplied.
 *
 * <p>A boost multiplies <em>quantities</em>. That is safe for a stack of ore,
 * where two of something is worth exactly twice one of it, and it is a
 * duplication exploit for anything whose worth is carried inside the item
 * instead of in its count. Breaking a full shulker box while a drops boost is
 * running is the clearest case: the box drops as an item holding all
 * twenty-seven of its stacks, so doubling that one item doubles everything
 * inside it. The same reasoning covers a filled bundle, and a chest item some
 * other plugin has written contents onto.
 *
 * <p>Two rules follow from that, and they are deliberately different in kind:
 *
 * <ul>
 *   <li><b>Stored contents are never multiplied.</b> Not configurable, because
 *       there is no server on which duplicating a shulker box's contents is the
 *       intended behaviour of a drops boost.</li>
 *   <li><b>Unstackable items are not multiplied by default.</b> An item that
 *       cannot stack has no quantity to multiply — copying it mints a second
 *       unique thing rather than doubling an amount. That is a judgement call
 *       rather than an exploit, so {@code boosts.drops.multiply-unstackable}
 *       can turn it back on for servers whose custom drops rely on it.</li>
 * </ul>
 *
 * <p>{@code boosts.drops.never-multiply} sits on top of both for anything
 * specific to one server — a currency item, a crate key — that is stackable and
 * carries no contents but still should not be doubled.
 */
public final class DropGuard {

    /**
     * Every shulker box, found by name rather than by {@code Tag.SHULKER_BOXES}.
     *
     * <p>The tag constants resolve against a running server's registry, which is
     * not something this class should need — it answers a question about an item
     * it has been handed, and it is asked that question in tests as well as in
     * game. The naming is fixed by the game: {@code SHULKER_BOX} and one
     * {@code <COLOUR>_SHULKER_BOX} per dye.
     */
    private static final Set<Material> SHULKER_BOXES;

    static {
        Set<Material> boxes = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            if (material.name().endsWith("SHULKER_BOX")) {
                boxes.add(material);
            }
        }
        SHULKER_BOXES = boxes;
    }

    private final Set<Material> denied;
    private final boolean unstackable;

    /**
     * @param denied      materials the operator has ruled out entirely
     * @param unstackable whether items that cannot stack may still be multiplied
     */
    public DropGuard(Collection<Material> denied, boolean unstackable) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        if (denied != null) {
            for (Material material : denied) {
                if (material != null) {
                    set.add(material);
                }
            }
        }
        this.denied = set;
        this.unstackable = unstackable;
    }

    /** A guard with nothing configured: the built-in safety rules alone. */
    public static DropGuard defaults() {
        return new DropGuard(Set.of(), false);
    }

    /** Whether this item may have its amount multiplied by a boost. */
    public boolean allows(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (carriesContents(stack)) {
            return false;
        }
        if (denied.contains(stack.getType())) {
            return false;
        }
        return unstackable || stack.getMaxStackSize() > 1;
    }

    /** The materials this guard refuses outright, for status output. */
    public Set<Material> denied() {
        return Collections.unmodifiableSet(denied);
    }

    /**
     * Whether this item's worth is in what it holds rather than in its count.
     *
     * <p>A shulker box counts whether or not it is holding anything right now.
     * It is the one container that keeps its contents through being broken, so
     * an empty one is only ever a full one that has not been filled yet, and the
     * cost of refusing to double an empty box is a player getting one shulker
     * box instead of two.
     */
    public static boolean carriesContents(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (SHULKER_BOXES.contains(stack.getType())) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            return true;
        }
        // Any other block item something has written a container into: a chest
        // or barrel handed out pre-filled, which plugins do use as a reward.
        return meta instanceof BlockStateMeta state
                && state.hasBlockState()
                && state.getBlockState() instanceof InventoryHolder;
    }
}
