# BoxCore

The Box server's **utility and progression core** for **Paper 1.21.4** — a
modular plugin that starts with a **skill tree** and a **collections** system
and is built to keep growing into whatever the server needs next.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design, and maintained the same way. Review it and test it on your own
> server before trusting it in production — progression plugins change server
> balance, and the shipped numbers are a starting point, not gospel.

> This is a separate project from *CustomAchievements* and *AntiCheat* in the
> same repository: different module (`boxcore/`), different package
> (`com.diamend.boxcore`), different purpose.

---

## Why it's built as modules

Everything BoxCore does is a **module** — a class implementing `BoxModule` that
gets a config toggle, a slot in the `/box` hub and a line in `/box modules` for
free. Adding the next utility (an economy hook, quests, staff tools) means
writing one class and registering it in `BoxCorePlugin`, not reworking the
plugin. A module that fails to start is logged and skipped; the rest keep
running.

Three ship today:

| Module | What it does |
|--------|--------------|
| `skills` | Config-driven skill trees bought with skill points. |
| `collections` | Hypixel SkyBlock-style "everything you've ever gathered" counters whose tiers pay out skill points. |
| `playtime` | Grants skill points for hours played. |

---

## Features

- 🌳 **Skill trees** defined entirely in `trees.yml` — slots, icons, costs,
  prerequisites, multi-level nodes, per-tree permissions.
- ⚡ **Real effects, not just cosmetics** — nodes grant vanilla **attribute
  modifiers** (health, damage, armour, mining speed, reach, gravity…),
  **permanent potion effects**, **permissions**, and **console commands** on
  unlock. Any mix, on any node.
- 📈 **Effects scale with level** and their lore is **generated from the actual
  numbers**, so a description can never drift out of sync with what a node does.
- 📦 **Collections** — every item a player has ever gathered, in categories,
  with tiers that pay out skill points, XP and commands.
- ⛏️ **Counts drops, not blocks** — mining counts what the block *drops*, so
  Silk Touch and Fortune behave the way players expect. Kills, fishing and
  harvesting all feed collections too; crafting and pickups are available but
  off by default (they double-count).
- 🚫 **Anti-farm** — breaking a block you placed doesn't count, by default.
- 🏷️ **Item tags** — a collection can track `#logs` instead of listing 11
  materials.
- ♻️ **Respec** with an optional fee.
- 🔧 **Self-healing config** — delete a node from `trees.yml` and the points
  spent on it come back automatically; lower a `max-level` and levels above it
  are trimmed and refunded; change a `cost` and it's re-charged at the new
  price. Editing the tree on a live server can't leave a player in debt.
- 🕒 **Playtime points** read from the server's own statistic, so time banked
  before BoxCore was installed still counts.
- 🔌 **PlaceholderAPI** support (optional).
- 💾 **Async persistence** — profiles are snapshotted on the main thread and
  written on a background thread; saved on quit, on shutdown and on a timer.

---

## Requirements

- Java **21**
- **Paper** (or Paper-compatible) **1.21.4**
- Optional: **PlaceholderAPI**

---

## Building

```bash
cd boxcore
mvn clean package
```

The jar lands in `boxcore/target/BoxCore-1.0.0.jar`. Drop it into `plugins/`
and restart. The build fetches the Paper API from `https://repo.papermc.io`.

---

## Commands

Base command: `/box` (aliases `/boxcore`, `/bx`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/box` | Open the hub | `boxcore.use` |
| `/box skills [tree]` | Open the skill trees | `boxcore.use` |
| `/box collections [category]` | Open the collections | `boxcore.use` |
| `/box points` | Show your points | `boxcore.use` |
| `/box respec` | Refund every node you own | `boxcore.respec` |
| `/box points <give\|take\|set> <player> <n>` | Adjust a player's points | `boxcore.admin` |
| `/box unlock <player> <tree.node> [level]` | Force-set a node's level | `boxcore.admin` |
| `/box collection set <player> <id> <amount>` | Set a collection total | `boxcore.admin` |
| `/box reset <player>` | Wipe a player's BoxCore data | `boxcore.admin` |
| `/box modules` | List modules and their state | `boxcore.admin` |
| `/box reload` | Re-read every config | `boxcore.admin` |

Admin commands work on offline players (anyone the server has seen before).

### Permissions

| Node | Default | Grants |
|------|---------|--------|
| `boxcore.use` | everyone | The hub, trees and collections |
| `boxcore.respec` | everyone | `/box respec` |
| `boxcore.admin` | op | Everything above |

---

## Configuration

Three files, all reloadable with `/box reload`:

- **`config.yml`** — module toggles, point sources, messages, storage.
- **`trees.yml`** — the skill trees.
- **`collections.yml`** — the collections.

### A skill node

```yaml
trees:
  combat:
    display: "<red><bold>Combat</bold>"
    icon: IRON_SWORD
    rows: 6
    nodes:
      toughness:
        slot: 11
        icon: RED_DYE
        display: "<red>Toughness"
        description: [ "<gray>Extra hearts, plain and simple." ]
        cost: 1              # or: costs: [1, 2, 4]  (per level)
        max-level: 5
        requires: [ "some_other_node:2" ]   # "node" or "node:level"
        requires-points: 12                 # points spent in this tree
        effects:
          attributes:
            max_health: 2.0                 # per level, flat
            movement_speed:
              amount: 0.05
              operation: add_scalar         # percent of the base value
          potions:
            - effect: haste
              amplifier: 0
              amplifier-per-level: 1
          permissions: [ "myserver.perk.fly" ]
          commands: [ "give %player% diamond 1" ]
```

Attribute names accept every spelling Mojang and Bukkit have used —
`max_health`, `GENERIC_MAX_HEALTH`, `generic.max_health` and
`minecraft:max_health` all resolve to the same attribute, so a config written
today won't break on the next rename.

Operations: `add_number` (flat, the default), `add_scalar` (percent of the
attribute's base) and `multiply_scalar_1` (percent of the running total).

### A collection

```yaml
collections:
  cobblestone:
    display: "<gray>Cobblestone"
    category: mining
    icon: COBBLESTONE
    items: [ COBBLESTONE, STONE ]      # or a tag: [ "#logs" ]
    points-per-tier: 1
    tiers: [ 50, 250, 1000, 5000 ]
```

Tiers can also carry their own rewards:

```yaml
    tiers:
      - amount: 1000
        points: 2
        xp: 500
        message: "<gold>The mines are yours."
        commands: [ "give %player% diamond_pickaxe 1" ]
```

Where collections are counted from is set in `config.yml`:

```yaml
collections:
  sources:
    block-break: true
    entity-kill: true
    fishing: true
    harvest: true
    craft: false     # usually double-counts
    pickup: false    # counts other players' drops as yours
  count-player-placed-blocks: false
```

---

## Placeholders

With PlaceholderAPI installed:

| Placeholder | Value |
|-------------|-------|
| `%boxcore_points%` | Points available |
| `%boxcore_points_spent%` / `%boxcore_points_earned%` | Ledger totals |
| `%boxcore_nodes%` | Nodes unlocked |
| `%boxcore_node_<tree.node>%` | Owned level of one node |
| `%boxcore_tree_<tree>%` | Points spent in one tree |
| `%boxcore_collected%` | Items gathered overall |
| `%boxcore_collection_<id>%` | Amount gathered |
| `%boxcore_tier_<id>%` | Collection tier reached |
| `%boxcore_progress_<id>%` | Percent toward the next tier |

---

## Storage

`plugins/BoxCore/playerdata/<uuid>.yml`:

```yaml
name: Steve
points:
  earned: 24
  spent: 11
  playtime-granted: 4
nodes:
  combat.toughness: 3
collections:
  cobblestone:
    amount: 5312
    tier: 4
```

Points are stored as a **ledger** (earned vs. spent) rather than a balance.
That's what lets the plugin recompute "spent" from the nodes a player actually
owns whenever `trees.yml` changes — nobody loses points, and nobody ends up
owing them.

---

## Balance notes

The shipped trees and collections are a **starting point sized for a survival
server**, not a tuned economy. The actual numbers as shipped:

| | |
|---|---|
| Points to max every node in every tree | **171** (combat 63, gathering 63, wayfarer 45) |
| Points available from collections, fully maxed | **278** across 217 tiers |
| Points from playtime | 1 per 5 hours, uncapped |

So a player who maxes every collection can eventually buy every node — but the
top collection tiers are deliberately brutal (150,000 cobblestone, 512 ancient
debris), and the first few tiers of everything come quickly. Expect early
points to arrive fast and the last third of a tree to be a long-term goal.

The strongest nodes (`Executioner`, `Featherweight`, `Deep Breath`, `Cave Eyes`,
`Last Stand`) additionally sit behind 12 points already spent in their own tree,
so they can't be rushed first.

If that's too generous, the levers in order of bluntness are: `points-per-tier`
on individual collections, `playtime.hours-per-point`, and node `cost`/`costs`.
All three can change on a live server — the refund logic means nobody is left
in debt.

Turn `playtime` off, retune `points-per-tier`, or delete trees wholesale — the
refund logic means you can rebalance on a live server without wiping anyone.

---

## Testing

```bash
cd boxcore
mvn test
```

The ledger and collection maths are tested pure; the plugin lifecycle,
config parsing and unlock paths run against a real Paper API via MockBukkit
(which downloads a server implementation, so those need network access). CI
additionally boots a headless Paper 1.21.4, loads the jar and asserts the
shipped configs parse without a single warning.
