package com.diamend.darksea.npc;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parses {@code shops.yml} into a {@link ShopStock}. Malformed lines are
 * logged and skipped rather than aborting the load — one typo in a price
 * should cost you that line, not the whole outpost — and the shipped-config
 * test refuses to let a skipped line reach a release.
 *
 * <p>The file's shape:
 * <pre>
 * black-market:
 *   markup: 1.6          # what it charges, against the refugee's prices
 *   salvage-rate: 1.5    # what it pays, against the refugee's prices
 *   slots: 5             # rotating lines shown per cycle
 *   pool:                # the rotating shelf, drawn from without repeats
 *     - {item: hullpiercer_arrow, amount: 2, price: 90}
 * clue-costs: [40, 120, 300, 750]
 * salvage:               # sell prices shared by everyone who takes salvage
 *   - {item: naxian_claw, price: 8}
 * shops:
 *   refugee_trader:
 *     takes-salvage: true
 *     buy:  [{item: dark_sea_boat, price: 40}]
 *     sell: [{item: relic_trade_coin, price: 25}]
 * </pre>
 * {@code amount} defaults to 1. An item id prefixed {@code vanilla:} names a
 * Material instead of a plugin item.
 */
public final class ShopConfig {

    private ShopConfig() {
    }

    public static ShopStock load(ConfigurationSection root, Logger log) {
        if (root == null) {
            log.severe("shops.yml is empty — every shop board will be blank");
            return ShopStock.empty();
        }

        double markup = 1.0;
        double salvageRate = 1.0;
        int slots = 0;
        List<ShopOffer> pool = List.of();
        ConfigurationSection market = root.getConfigurationSection("black-market");
        if (market == null) {
            log.warning("shops.yml has no 'black-market' section — nothing will rotate");
        } else {
            markup = Math.max(0.01, market.getDouble("markup", 1.0));
            salvageRate = Math.max(0.01, market.getDouble("salvage-rate", 1.0));
            slots = Math.max(0, market.getInt("slots", 0));
            pool = parseOffers(market, "pool", ShopOffer.Kind.BUY, "black-market.pool", log);
        }

        List<ShopOffer> salvage = parseOffers(root, "salvage", ShopOffer.Kind.SELL,
                "salvage", log);
        if (salvage.isEmpty()) {
            log.warning("shops.yml has no 'salvage' list — nobody will buy loot");
        }

        List<Integer> clueCosts = new ArrayList<>();
        for (int cost : root.getIntegerList("clue-costs")) {
            if (cost < 1) {
                log.warning("shops.yml clue-costs: '" + cost + "' is not a price — skipped");
                continue;
            }
            clueCosts.add(cost);
        }

        Map<String, List<ShopOffer>> fixed = new LinkedHashMap<>();
        Set<String> takesSalvage = new LinkedHashSet<>();
        ConfigurationSection shops = root.getConfigurationSection("shops");
        if (shops == null) {
            log.severe("shops.yml has no 'shops' section — every board will be blank");
            return ShopStock.empty();
        }
        for (String key : shops.getKeys(false)) {
            NpcType type = NpcType.byId(key);
            if (type == null) {
                log.warning("shops.yml: '" + key + "' is not an NPC type — skipped");
                continue;
            }
            ConfigurationSection sec = shops.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            List<ShopOffer> lines = new ArrayList<>();
            lines.addAll(parseOffers(sec, "buy", ShopOffer.Kind.BUY,
                    "shops." + key + ".buy", log));
            lines.addAll(parseOffers(sec, "sell", ShopOffer.Kind.SELL,
                    "shops." + key + ".sell", log));
            fixed.put(type.id(), lines);
            if (sec.getBoolean("takes-salvage", false)) {
                takesSalvage.add(type.id());
            }
        }

        return new ShopStock(fixed, pool, salvage, takesSalvage,
                markup, salvageRate, slots, clueCosts);
    }

    // ------------------------------------------------------------------
    // Writing back (the in-game editor)
    // ------------------------------------------------------------------

    /**
     * Renders a snapshot back into {@code shops.yml}. The in-game editor is
     * the only caller, and it writes after every change so a crash can never
     * lose an edit.
     *
     * <p>A rewrite necessarily loses hand-written comments, so the header and
     * the per-section notes are re-emitted here rather than living only in the
     * shipped resource — the file stays readable and hand-editable no matter
     * how many times the editor has been through it.
     */
    public static void save(ShopStock stock, File file, Logger log) {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("black-market.markup", stock.markup());
        yaml.set("black-market.salvage-rate", stock.salvageRate());
        yaml.set("black-market.slots", stock.slots());
        yaml.set("black-market.pool", toMaps(stock.rotatingPool()));
        yaml.set("clue-costs", stock.clueCosts());
        yaml.set("salvage", toMaps(stock.salvage()));

        for (NpcType type : NpcType.values()) {
            String path = "shops." + type.id();
            List<ShopOffer> lines = stock.fixedFor(type.id());
            yaml.set(path + ".takes-salvage", stock.takesSalvage().contains(type.id()));
            List<Map<String, Object>> buys = toMaps(byKind(lines, ShopOffer.Kind.BUY));
            List<Map<String, Object>> sells = toMaps(byKind(lines, ShopOffer.Kind.SELL));
            yaml.set(path + ".buy", buys.isEmpty() ? null : buys);
            yaml.set(path + ".sell", sells.isEmpty() ? null : sells);
        }

        yaml.options().setHeader(HEADER);
        yaml.setComments("black-market", List.of("",
                "The black market's shelf rotates once per sea reset, and is priced off",
                "the refugee's numbers: it charges 'markup' times what he charges and",
                "pays 'salvage-rate' times what he pays. Fewer 'slots' than pool entries",
                "is the design — scarcity is what makes a good cycle feel good."));
        yaml.setComments("clue-costs", List.of("",
                "What the old boat expert charges per rumour about the Heart, in order.",
                "The list's length is how many he has to sell; each rung needs a matching",
                "shop-clue-<n> message in config.yml."));
        yaml.setComments("salvage", List.of("",
                "Sea salvage: what the redundant weapons are worth. These are the",
                "refugee's honest prices; everyone with 'takes-salvage: true' buys from",
                "this same list, and the black market applies its salvage-rate on top."));
        yaml.setComments("shops", List.of("",
                "Per-NPC lines. 'buy' is what the NPC sells to players; 'sell' is what it",
                "buys from them. An 'item' may be a DarkSea id, 'vanilla:MATERIAL', or",
                "'custom:<base64>' — a literal item snapshot written by /ds shop."));

        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    private static final List<String> HEADER = List.of(
            "DarkSea — shop boards",
            "",
            "Every price the outpost's NPCs trade at. Editable three ways: by hand here,",
            "with /ds reload to pick it up; or in game with /ds shop, which writes this",
            "file back after every change.",
            "",
            "A line is {item, amount, price}. 'amount' defaults to 1 and 'price' is always",
            "the price of the whole lot, not per item.",
            "",
            "Two rules worth keeping when you edit this file:",
            "  1. Nothing that ends a run should be on a 'buy' list — the Undrowned Heart,",
            "     the Soulwake Compass, boat upgrade tokens, or relics. Shops should make a",
            "     run cheaper to attempt, never skippable. A unit test enforces this.",
            "  2. The artificer should pay less for a dormant relic than waking one costs,",
            "     or selling relics becomes a way to fund waking them.");

    private static List<ShopOffer> byKind(List<ShopOffer> lines, ShopOffer.Kind kind) {
        List<ShopOffer> filtered = new ArrayList<>();
        for (ShopOffer offer : lines) {
            if (offer.kind() == kind) {
                filtered.add(offer);
            }
        }
        return filtered;
    }

    /** Offers as the plain maps the parser reads back, omitting default amounts. */
    private static List<Map<String, Object>> toMaps(List<ShopOffer> offers) {
        List<Map<String, Object>> maps = new ArrayList<>(offers.size());
        for (ShopOffer offer : offers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("item", offer.itemId());
            if (offer.amount() != 1) {
                map.put("amount", offer.amount());
            }
            map.put("price", offer.price());
            maps.add(map);
        }
        return maps;
    }

    /**
     * Reads a list of {@code {item, amount, price}} maps. Anything that isn't
     * a usable line is named in the log and dropped.
     */
    private static List<ShopOffer> parseOffers(ConfigurationSection sec, String path,
                                               ShopOffer.Kind kind, String where, Logger log) {
        List<ShopOffer> offers = new ArrayList<>();
        List<Map<?, ?>> raw = sec.getMapList(path);
        for (Map<?, ?> entry : raw) {
            Object item = entry.get("item");
            if (item == null || item.toString().isBlank()) {
                log.warning("shops.yml " + where + ": a line has no 'item' — skipped");
                continue;
            }
            int amount = intOf(entry.get("amount"), 1);
            int price = intOf(entry.get("price"), 0);
            if (amount < 1 || price < 1) {
                log.warning("shops.yml " + where + ": '" + item
                        + "' has amount " + amount + " and price " + price + " — skipped");
                continue;
            }
            offers.add(new ShopOffer(item.toString(), amount, price, kind));
        }
        return List.copyOf(offers);
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return -1;  // caller reports it against the line's item id
        }
    }
}
