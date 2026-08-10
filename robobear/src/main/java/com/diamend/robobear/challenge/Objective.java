package com.diamend.robobear.challenge;

import com.diamend.robobear.mine.MineIndex;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * One round's goal, already rolled: a concrete amount of a concrete thing, and
 * what finishing it pays in Cogs.
 *
 * <p>Objectives are generated per round rather than configured per challenge —
 * that's what makes a run a ladder you climb rather than a script you memorise.
 * Two are offered each round and the player picks one, so the harder of the pair
 * paying more Cogs is a real decision rather than flavour.
 */
public class Objective {

    private final ObjectiveType type;
    private final String mineId;
    private final Material material;
    private final int amount;
    private final int cogs;

    public Objective(ObjectiveType type, String mineId, Material material, int amount, int cogs) {
        this.type = type;
        this.mineId = mineId;
        this.material = material;
        this.amount = Math.max(1, amount);
        this.cogs = Math.max(0, cogs);
    }

    public ObjectiveType type() {
        return type;
    }

    public String mineId() {
        return mineId;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public int cogs() {
        return cogs;
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /** Whether a broken block counts. The mine check is the caller's to make. */
    public boolean acceptsBlock(MineIndex mines, Block block) {
        if (type == ObjectiveType.KILL_MOBS) {
            return false;
        }
        if (type == ObjectiveType.MINE_MATERIAL && block.getType() != material) {
            return false;
        }
        return mines.isInside(mineId, block);
    }

    public boolean isKill() {
        return type == ObjectiveType.KILL_MOBS;
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    public String describe(MineIndex mines) {
        return switch (type) {
            case MINE_BLOCKS -> "Break " + Text.number(amount) + " blocks in " + mineLabel(mines);
            case MINE_MATERIAL -> "Break " + Text.number(amount) + "× " + Items.prettyName(material)
                    + " in " + mineLabel(mines);
            case KILL_MOBS -> "Kill " + Text.number(amount) + " hostile mobs";
        };
    }

    /** The short form, for an actionbar where space is scarce. */
    public String describeShort(MineIndex mines) {
        return switch (type) {
            case MINE_BLOCKS -> "blocks in " + mineLabel(mines);
            case MINE_MATERIAL -> Items.prettyName(material);
            case KILL_MOBS -> "mobs";
        };
    }

    private String mineLabel(MineIndex mines) {
        MineRegion mine = mines.byId(mineId);
        return mine == null ? mineId : mine.label();
    }

    public Material icon() {
        return switch (type) {
            case MINE_BLOCKS -> Material.IRON_PICKAXE;
            case MINE_MATERIAL -> material != null && material.isItem() ? material : Material.IRON_PICKAXE;
            case KILL_MOBS -> Material.IRON_SWORD;
        };
    }

    /** Whether this objective can still be attempted on the current server state. */
    public boolean isValid(MineIndex mines) {
        return type == ObjectiveType.KILL_MOBS || mines.exists(mineId);
    }
}
