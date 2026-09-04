package com.diamend.customachievements.gui;

import com.diamend.customachievements.CustomAchievementsPlugin;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Off-chat text entry backed by the {@link AnvilGUI} library, which opens a real
 * (NMS) anvil menu. Type in the rename field, then click the output slot to
 * confirm. Because nothing is ever typed into chat, chat-bridge plugins (e.g.
 * DiscordSRV) can't relay it.
 *
 * <p>A plain {@code Bukkit.createInventory(holder, ANVIL, ...)} cannot be used
 * for this: such an inventory is not a functional anvil menu, so
 * {@code PrepareAnvilEvent} never fires and the rename text is never captured
 * (see PaperMC/Paper#9892). AnvilGUI wraps the version-specific server internals
 * to give a working menu instead.
 */
public final class AnvilPrompt {

    private AnvilPrompt() {
    }

    /**
     * Opens the anvil prompt. {@code onDone} receives the typed text (never null,
     * possibly blank) when the player confirms; {@code onCancel} runs if they
     * close the menu without confirming. Both callbacks run on the main thread,
     * one tick later, so it is safe to open another inventory from them.
     *
     * @throws Throwable if AnvilGUI cannot build a menu on this server build; the
     *                   caller is expected to fall back to chat input.
     */
    public static void open(CustomAchievementsPlugin plugin, Player viewer, String title,
                            Consumer<String> onDone, Runnable onCancel) {
        boolean[] confirmed = {false};
        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(title)
                .text(" ")
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    confirmed[0] = true;
                    String text = state.getText() == null ? "" : state.getText().trim();
                    return List.of(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() ->
                                    Bukkit.getScheduler().runTask(plugin, () -> onDone.accept(text))));
                })
                .onClose(state -> {
                    if (!confirmed[0]) {
                        Bukkit.getScheduler().runTask(plugin, onCancel);
                    }
                })
                .open(viewer);
    }
}
