package com.diamend.darksea.relic;

import com.diamend.darksea.DarkSeaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-game relic editor: {@code /ds relic editor}. Make a relic that has
 * nothing to do with the Dark Sea, give it a material, a name, lore and a
 * boost, and it drops, sells, wakes and wears exactly like a shipped one.
 *
 * <p>Three rules keep it safe to hand an admin. The shipped six are drawn for
 * context and are not editable — loot.yml and shops.yml name them by id, and
 * an edit here could break a file the editor cannot see; the way to start from
 * one is to clone it. Every change is written to {@code relics-custom.yml} the
 * moment it is made, so a crash costs nothing. And an id is fixed at creation:
 * a relic's id is its identity in every chest, shop line and reliquary that
 * already holds one, so the editor will let you rename the <em>display</em>
 * name all day and never the id.
 *
 * <p>Text arrives through chat rather than an anvil. Clicking a text tile
 * closes the board and asks for a line; the next thing typed is the answer,
 * and the board reopens on it. Chat is the only input in Minecraft wide enough
 * for a MiniMessage colour string, and formatting is validated on the way in —
 * an unclosed tag is refused at the prompt rather than throwing every time the
 * item is drawn afterwards.
 */
public final class RelicEditorService implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private static final int BOARD_SIZE = 54;

    /** Where the list stops showing relics and starts showing controls. */
    private static final int LIST_CONTROL_ROW = 45;

    /** How long a chat prompt waits before it stops swallowing what you type. */
    private static final long PROMPT_TIMEOUT_MILLIS = 120_000L;

    /** The word that abandons a prompt without changing anything. */
    private static final String CANCEL_WORD = "cancel";

    /** What a chat prompt is collecting. */
    private enum Field { ID, MATERIAL, NAME, LORE, BOOST_LINE, EFFECT }

    /** An outstanding chat prompt: which relic, which field, and when it gives up. */
    private record Prompt(Field field, String relicId, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final DarkSeaPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public RelicEditorService(DarkSeaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    public void openList(Player admin) {
        show(admin, new RelicEditor(RelicEditor.Page.LIST, null),
                "relic-editor-title-list", "");
    }

    public void openEdit(Player admin, String relicId) {
        Relic relic = Relic.byId(relicId);
        if (relic == null || !relic.custom()) {
            openList(admin);
            return;
        }
        show(admin, new RelicEditor(RelicEditor.Page.EDIT, relicId),
                "relic-editor-title-edit", relic.id());
    }

    private void show(Player admin, RelicEditor editor, String titleKey, String label) {
        Inventory inv = plugin.getServer().createInventory(editor, BOARD_SIZE,
                plugin.messages().component(titleKey, "relic", label));
        editor.setInventory(inv);
        populate(editor);
        admin.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private void populate(RelicEditor editor) {
        Inventory inv = editor.getInventory();
        if (inv == null) {
            return;
        }
        inv.clear();
        editor.clear();
        if (editor.page() == RelicEditor.Page.LIST) {
            drawList(editor, inv);
        } else {
            drawEdit(editor, inv);
        }
    }

    /**
     * Every relic there is: the shipped six first, greyed, then the admin's
     * own. Showing the shipped ones is not decoration — their tiers and revive
     * costs are the only scale a new relic's numbers mean anything against.
     */
    private void drawList(RelicEditor editor, Inventory inv) {
        int slot = 0;
        for (Relic relic : Relic.builtIns()) {
            if (slot >= LIST_CONTROL_ROW) {
                break;
            }
            inv.setItem(slot, listTile(relic, false));
            editor.putRelic(slot, relic.id());
            slot++;
        }
        // A gap, so the two halves never read as one list.
        slot = Math.min(LIST_CONTROL_ROW, ((slot / 9) + 1) * 9);
        for (Relic relic : Relic.customs()) {
            if (slot >= LIST_CONTROL_ROW) {
                break;
            }
            inv.setItem(slot, listTile(relic, true));
            editor.putRelic(slot, relic.id());
            slot++;
        }

        inv.setItem(LIST_CONTROL_ROW, tile(Material.NETHER_STAR,
                "relic-editor-new", List.of("relic-editor-new-hint")));
        editor.putButton(LIST_CONTROL_ROW, RelicEditor.Button.NEW);
        inv.setItem(53, tile(Material.WRITABLE_BOOK, "relic-editor-help",
                List.of("relic-editor-help-file", "relic-editor-help-shipped",
                        "relic-editor-help-clone", "relic-editor-help-id")));
        editor.putButton(53, RelicEditor.Button.HELP);
    }

    private ItemStack listTile(Relic relic, boolean editable) {
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().component("relic-editor-line-boost",
                "boost", boostLabel(relic)));
        lore.add(plugin.messages().component("relic-editor-line-numbers",
                "tier", String.valueOf(relic.tier()),
                "cost", String.valueOf(relic.reviveCost())));
        lore.add(plugin.messages().component(
                editable ? "relic-editor-line-yours" : "relic-editor-line-shipped",
                "id", relic.id()));
        lore.add(plugin.messages().component(
                editable ? "relic-editor-line-edit" : "relic-editor-line-clone"));
        ItemStack stack = new ItemStack(relic.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(relic.displayName())));
        meta.lore(clean(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    /** One tile per editable field, plus the item itself so the result is never a guess. */
    private void drawEdit(RelicEditor editor, Inventory inv) {
        Relic relic = Relic.byId(editor.relicId());
        if (relic == null) {
            drawList(editor, inv);
            return;
        }
        inv.setItem(4, relic.createDormant());

        put(editor, inv, 10, RelicEditor.Button.MATERIAL, relic.material(),
                "relic-editor-material", List.of(
                        line("relic-editor-material-now", "material", relic.material().name()),
                        msg("relic-editor-material-hint")));
        put(editor, inv, 12, RelicEditor.Button.NAME, Material.NAME_TAG,
                "relic-editor-name", List.of(
                        noItalic(MM.deserialize(relic.displayName())),
                        msg("relic-editor-name-hint")));
        put(editor, inv, 14, RelicEditor.Button.LORE, Material.BOOK,
                "relic-editor-lore", loreTileLines(relic));
        put(editor, inv, 16, RelicEditor.Button.BOOST_LINE, Material.PAPER,
                "relic-editor-boost-line", List.of(
                        line("relic-editor-boost-line-now", "text", relic.boostLine()),
                        msg("relic-editor-boost-line-hint")));

        put(editor, inv, 19, RelicEditor.Button.BOOST, Material.BLAZE_POWDER,
                "relic-editor-boost", List.of(
                        line("relic-editor-boost-now", "boost", boostLabel(relic)),
                        msg("relic-editor-boost-hint")));
        put(editor, inv, 21, RelicEditor.Button.EFFECT,
                relic.boost() == Relic.Boost.EFFECT ? Material.POTION : Material.GLASS_BOTTLE,
                "relic-editor-effect", effectTileLines(relic));
        put(editor, inv, 23, RelicEditor.Button.TIER, Material.EXPERIENCE_BOTTLE,
                "relic-editor-tier", List.of(
                        line("relic-editor-tier-now", "tier", String.valueOf(relic.tier())),
                        msg("relic-editor-tier-hint")));
        put(editor, inv, 25, RelicEditor.Button.COST, Material.GOLD_INGOT,
                "relic-editor-cost", List.of(
                        line("relic-editor-cost-now", "cost", String.valueOf(relic.reviveCost())),
                        msg("relic-editor-cost-hint")));

        put(editor, inv, 38, RelicEditor.Button.GIVE, Material.CHEST,
                "relic-editor-give", List.of(msg("relic-editor-give-hint")));
        put(editor, inv, 42, RelicEditor.Button.DELETE, Material.LAVA_BUCKET,
                "relic-editor-delete", List.of(msg("relic-editor-delete-hint")));
        put(editor, inv, 45, RelicEditor.Button.BACK, Material.ARROW,
                "relic-editor-back", List.of());
        put(editor, inv, 53, RelicEditor.Button.HELP, Material.WRITABLE_BOOK,
                "relic-editor-help", List.of(msg("relic-editor-help-file"),
                        msg("relic-editor-help-id"), msg("relic-editor-help-loot")));
    }

    private List<Component> loreTileLines(Relic relic) {
        List<Component> lines = new ArrayList<>();
        if (relic.lore().isEmpty()) {
            lines.add(msg("relic-editor-lore-empty"));
        } else {
            for (String raw : relic.lore()) {
                lines.add(noItalic(MM.deserialize(raw)));
            }
        }
        lines.add(msg("relic-editor-lore-hint"));
        return lines;
    }

    private List<Component> effectTileLines(Relic relic) {
        List<Component> lines = new ArrayList<>();
        lines.add(line("relic-editor-effect-now",
                "effect", CustomRelicConfig.effectName(relic.effect()),
                "level", String.valueOf(relic.effectAmplifier() + 1)));
        if (relic.boost() != Relic.Boost.EFFECT) {
            lines.add(msg("relic-editor-effect-inert"));
        }
        lines.add(msg("relic-editor-effect-hint"));
        return lines;
    }

    private void put(RelicEditor editor, Inventory inv, int slot, RelicEditor.Button button,
                     Material material, String nameKey, List<Component> lore) {
        inv.setItem(slot, tileOf(material, nameKey, lore));
        editor.putButton(slot, button);
    }

    private ItemStack tile(Material material, String nameKey, List<String> loreKeys) {
        List<Component> lore = new ArrayList<>(loreKeys.size());
        for (String key : loreKeys) {
            lore.add(msg(key));
        }
        return tileOf(material, nameKey, lore);
    }

    private ItemStack tileOf(Material material, String nameKey, List<Component> lore) {
        ItemStack stack = new ItemStack(material.isItem() ? material : Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(plugin.messages().component(nameKey)));
        meta.lore(clean(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component msg(String key) {
        return plugin.messages().component(key);
    }

    private Component line(String key, String... placeholders) {
        return plugin.messages().component(key, placeholders);
    }

    private String boostLabel(Relic relic) {
        if (relic.boost() != Relic.Boost.EFFECT) {
            return relic.boost().name();
        }
        return "EFFECT: " + CustomRelicConfig.effectName(relic.effect())
                + " " + (relic.effectAmplifier() + 1);
    }

    private static List<Component> clean(List<Component> lore) {
        List<Component> out = new ArrayList<>(lore.size());
        for (Component component : lore) {
            out.add(noItalic(component));
        }
        return out;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RelicEditor editor)) {
            return;
        }
        event.setCancelled(true);  // the editor never moves a real item
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        if (!admin.hasPermission("darksea.admin")) {
            admin.closeInventory();
            plugin.messages().send(admin, "no-permission");
            return;
        }
        if (event.getClickedInventory() != editor.getInventory()) {
            return;  // a click down in your own pack does nothing here
        }
        if (editor.page() == RelicEditor.Page.LIST) {
            handleListClick(admin, editor, event);
        } else {
            handleEditClick(admin, editor, event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RelicEditor) {
            event.setCancelled(true);
        }
    }

    private void handleListClick(Player admin, RelicEditor editor, InventoryClickEvent event) {
        if (editor.buttonAt(event.getSlot()) == RelicEditor.Button.NEW) {
            if (Relic.customs().size() >= LIST_CONTROL_ROW - 9) {
                plugin.messages().send(admin, "relic-editor-full");
                return;
            }
            ask(admin, Field.ID, null);
            return;
        }
        String id = editor.relicAt(event.getSlot());
        if (id == null) {
            return;
        }
        Relic relic = Relic.byId(id);
        if (relic == null) {
            populate(editor);
            return;
        }
        if (relic.custom()) {
            openEdit(admin, id);
            return;
        }
        // A shipped relic is read-only, so the only useful thing a click can
        // do is offer the copy the admin actually wanted.
        cloneRelic(admin, relic);
    }

    /** A shipped relic copied into an editable one, under a free id. */
    private void cloneRelic(Player admin, Relic source) {
        String id = freeId(source.id() + "_copy");
        Relic copy = Relic.custom(id, source.tier(), source.reviveCost(), source.boost(),
                source.material(), source.displayName(), source.lore(), source.boostLine(),
                source.effect(), source.effectAmplifier());
        List<Relic> all = new ArrayList<>(Relic.customs());
        all.add(copy);
        plugin.saveCustomRelics(all);
        admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
        plugin.messages().send(admin, "relic-editor-cloned", "id", id);
        openEdit(admin, id);
    }

    /** {@code base}, or base_2, base_3... — the first id nothing else has claimed. */
    private String freeId(String base) {
        String id = Relic.sanitizeId(base);
        if (Relic.isIdFree(id)) {
            return id;
        }
        for (int n = 2; n < 1000; n++) {
            String candidate = id + "_" + n;
            if (Relic.isIdFree(candidate)) {
                return candidate;
            }
        }
        return id + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private void handleEditClick(Player admin, RelicEditor editor, InventoryClickEvent event) {
        Relic relic = Relic.byId(editor.relicId());
        RelicEditor.Button button = editor.buttonAt(event.getSlot());
        if (relic == null || button == null) {
            return;
        }
        switch (button) {
            case BACK -> openList(admin);
            case HELP -> { }
            case MATERIAL -> ask(admin, Field.MATERIAL, relic.id());
            case NAME -> ask(admin, Field.NAME, relic.id());
            case BOOST_LINE -> ask(admin, Field.BOOST_LINE, relic.id());
            case LORE -> {
                if (event.getClick() == ClickType.DROP
                        || event.getClick() == ClickType.CONTROL_DROP) {
                    save(admin, editor, relic.withLore(List.of()));
                } else if (event.isRightClick()) {
                    List<String> lore = new ArrayList<>(relic.lore());
                    if (!lore.isEmpty()) {
                        lore.remove(lore.size() - 1);
                    }
                    save(admin, editor, relic.withLore(lore));
                } else {
                    ask(admin, Field.LORE, relic.id());
                }
            }
            case BOOST -> save(admin, editor,
                    relic.withBoost(cycle(relic.boost(), event.isRightClick())));
            case EFFECT -> {
                if (event.isRightClick()) {
                    int next = relic.effectAmplifier() >= 4 ? 0 : relic.effectAmplifier() + 1;
                    save(admin, editor, relic.withEffect(relic.effect(), next));
                } else {
                    ask(admin, Field.EFFECT, relic.id());
                }
            }
            case TIER -> {
                int next = event.isRightClick() ? relic.tier() - 1 : relic.tier() + 1;
                save(admin, editor, relic.withTier(next < 1 ? 5 : next > 5 ? 1 : next));
            }
            case COST -> {
                int step = event.isShiftClick() ? 100 : 25;
                int next = relic.reviveCost() + (event.isRightClick() ? -step : step);
                save(admin, editor, relic.withReviveCost(Math.max(0, next)));
            }
            case GIVE -> give(admin, relic, event.isShiftClick());
            case DELETE -> {
                if (!event.isShiftClick()) {
                    plugin.messages().send(admin, "relic-editor-delete-hint");
                    return;
                }
                delete(admin, relic);
            }
            default -> { }
        }
    }

    private static Relic.Boost cycle(Relic.Boost from, boolean backwards) {
        Relic.Boost[] all = Relic.Boost.values();
        int at = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == from) {
                at = i;
                break;
            }
        }
        return all[(at + (backwards ? all.length - 1 : 1)) % all.length];
    }

    private void give(Player admin, Relic relic, boolean awake) {
        ItemStack item = relic.createDormant();
        if (awake) {
            relic.wake(item);
        }
        for (ItemStack leftover : admin.getInventory().addItem(item).values()) {
            admin.getWorld().dropItemNaturally(admin.getLocation(), leftover);
        }
        admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
        plugin.messages().send(admin, awake ? "relic-editor-given-awake" : "relic-editor-given",
                "id", relic.id());
    }

    private void delete(Player admin, Relic relic) {
        List<Relic> kept = new ArrayList<>();
        for (Relic other : Relic.customs()) {
            if (!other.id().equals(relic.id())) {
                kept.add(other);
            }
        }
        plugin.saveCustomRelics(kept);
        admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 0.8f);
        plugin.messages().send(admin, "relic-editor-deleted", "id", relic.id());
        openList(admin);
    }

    /** Writes one relic back into the custom list, in place, and redraws. */
    private void save(Player admin, RelicEditor editor, Relic updated) {
        List<Relic> all = new ArrayList<>(Relic.customs());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(updated.id())) {
                all.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(updated);
        }
        plugin.saveCustomRelics(all);
        admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.4f);
        if (editor != null) {
            populate(editor);
        }
    }

    // ------------------------------------------------------------------
    // Chat prompts
    // ------------------------------------------------------------------

    /** Closes the board and waits for one line of chat. */
    private void ask(Player admin, Field field, String relicId) {
        prompts.put(admin.getUniqueId(),
                new Prompt(field, relicId, System.currentTimeMillis() + PROMPT_TIMEOUT_MILLIS));
        admin.closeInventory();
        plugin.messages().send(admin, "relic-editor-prompt-"
                + field.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        plugin.messages().send(admin, "relic-editor-prompt-cancel");
    }

    /**
     * Chat is where the editor's text comes from, so a prompted line must never
     * also reach the server. Handled at LOWEST so the cancel lands before any
     * chat plugin has formatted it, and everything past the read hops back to
     * the main thread — this event is async and inventories are not.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player admin = event.getPlayer();
        Prompt prompt = prompts.get(admin.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (prompt.expired()) {
            prompts.remove(admin.getUniqueId());
            return;  // a forgotten prompt stops eating chat rather than eating it forever
        }
        prompts.remove(admin.getUniqueId());
        event.setCancelled(true);
        String typed = PLAIN.serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(admin, prompt, typed));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player admin, Prompt prompt, String typed) {
        if (!admin.isOnline()) {
            return;
        }
        if (!admin.hasPermission("darksea.admin")) {
            plugin.messages().send(admin, "no-permission");
            return;
        }
        if (typed.isEmpty() || typed.equalsIgnoreCase(CANCEL_WORD)) {
            plugin.messages().send(admin, "relic-editor-cancelled");
            reopen(admin, prompt.relicId());
            return;
        }
        if (prompt.field() == Field.ID) {
            create(admin, typed);
            return;
        }
        Relic relic = Relic.byId(prompt.relicId());
        if (relic == null || !relic.custom()) {
            openList(admin);
            return;  // deleted while the prompt was open
        }
        Relic updated = switch (prompt.field()) {
            case MATERIAL -> withMaterial(admin, relic, typed);
            case NAME -> validFormatting(admin, typed) ? relic.withDisplayName(typed) : null;
            case BOOST_LINE -> validFormatting(admin, typed) ? relic.withBoostLine(typed) : null;
            case LORE -> withLoreLine(admin, relic, typed);
            case EFFECT -> withEffect(admin, relic, typed);
            case ID -> null;
        };
        if (updated != null) {
            save(admin, null, updated);
        }
        reopen(admin, relic.id());
    }

    private void reopen(Player admin, String relicId) {
        if (relicId == null) {
            openList(admin);
        } else {
            openEdit(admin, relicId);
        }
    }

    private void create(Player admin, String typed) {
        String id = Relic.sanitizeId(typed);
        if (!Relic.isIdFree(id)) {
            plugin.messages().send(admin, "relic-editor-id-taken", "id", id);
            openList(admin);
            return;
        }
        Relic made = Relic.custom(id, 3, 100, Relic.Boost.SPEED, Material.AMETHYST_SHARD,
                "<white>" + id + "</white>", List.of(), "an unnamed boon", null, 0);
        List<Relic> all = new ArrayList<>(Relic.customs());
        all.add(made);
        plugin.saveCustomRelics(all);
        admin.playSound(admin.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        plugin.messages().send(admin, "relic-editor-created", "id", id);
        openEdit(admin, id);
    }

    private Relic withMaterial(Player admin, Relic relic, String typed) {
        Material material;
        if (typed.equalsIgnoreCase("hand")) {
            material = admin.getInventory().getItemInMainHand().getType();
        } else {
            material = Material.matchMaterial(typed);
        }
        if (material == null || !material.isItem() || material == Material.AIR) {
            plugin.messages().send(admin, "relic-editor-bad-material", "value", typed);
            return null;
        }
        return relic.withMaterial(material);
    }

    private Relic withLoreLine(Player admin, Relic relic, String typed) {
        if (!validFormatting(admin, typed)) {
            return null;
        }
        if (relic.lore().size() >= 8) {
            plugin.messages().send(admin, "relic-editor-lore-full");
            return null;
        }
        List<String> lore = new ArrayList<>(relic.lore());
        lore.add(typed);
        return relic.withLore(lore);
    }

    private Relic withEffect(Player admin, Relic relic, String typed) {
        PotionEffectType effect = CustomRelicConfig.effectByName(typed);
        if (effect == null) {
            plugin.messages().send(admin, "relic-editor-bad-effect", "value", typed);
            return null;
        }
        // Naming an effect is the whole point of the EFFECT boost, so setting
        // one on a relic that is still SPEED almost certainly means the admin
        // wanted both. Switching for them beats a relic that silently ignores
        // the effect they just chose.
        Relic updated = relic.withEffect(effect, relic.effectAmplifier());
        return updated.boost() == Relic.Boost.EFFECT
                ? updated : updated.withBoost(Relic.Boost.EFFECT);
    }

    /**
     * Whether a typed line survives MiniMessage. Checked here, once, rather
     * than left to blow up in {@code createDormant} — an unclosed tag saved
     * into a relic would throw every time anything drew it, which looks like
     * the plugin breaking rather than a typo.
     */
    private boolean validFormatting(Player admin, String typed) {
        try {
            MM.deserialize(typed);
            return true;
        } catch (RuntimeException ex) {
            plugin.messages().send(admin, "relic-editor-bad-format", "why", ex.getMessage());
            return false;
        }
    }
}
