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
| `skills` | Config-driven skill trees bought with skill points, plus the perk engine that runs their scripted effects. |
| `collections` | Hypixel SkyBlock-style "everything you've ever gathered" counters whose tiers pay out skill points. |
| `playtime` | Records hours played into a collection, so time online pays out through the same tiers as everything else. |
| `compressor` | Folds full stacks of raw ore into single items, unlocked per ore by that ore's collection tier. |
| `boosts` | Temporary multipliers on ore drops and collection progress — server-wide, per player, scheduled, or from a consumable item. |

---

## Features

- 🌳 **Skill trees** defined entirely in `trees.yml` — slots, icons, costs,
  prerequisites, multi-level nodes, per-tree permissions. **45 nodes across four
  trees** ship as the default, tuned for a boxpvp server.
- ⚡ **Real effects, not just cosmetics** — nodes grant vanilla **attribute
  modifiers** (health, damage, armour, mining speed, reach, gravity…),
  **permanent potion effects**, **permissions**, and **console commands** on
  unlock. Any mix, on any node.
- ⚔️ **Perks for everything an attribute can't say** — lifesteal, bonus damage
  to players, poison on hit, arrows that don't get spent, stronger splash
  potions, longer gapples, surviving a killing blow, auto-smelt, bonus ore
  drops. Twenty of them, each a one-line entry in `trees.yml`.
- 📈 **Effects scale with level** and their lore is **generated from the actual
  numbers**, so a description can never drift out of sync with what a node does.
- 📦 **Collections** — every item a player has ever gathered, in categories,
  with tiers that pay out skill points, XP and commands.
- ⛏️ **Counts drops, not blocks** — mining counts what the block *drops*, so
  Silk Touch and Fortune behave the way players expect. Kills, fishing and
  harvesting all feed collections too; crafting and pickups are available but
  off by default (they double-count).
- 🚫 **Anti-farm** — breaking a block you placed doesn't count, by default. The
  flags are stored in the chunk, so they survive a restart instead of being
  forgotten with the server's uptime; pistons carry them along with the blocks
  they push; and each flag remembers *which* block was placed, so a regenerated
  mine counts normally instead of being poisoned by whatever was standing there
  before.
- 🏷️ **Item tags** — a collection can track `#logs` instead of listing 11
  materials.
- ♻️ **Respec** for a configurable **item** — a token you obtain rather than a tax
  on points you already earned.
- 🔧 **Self-healing config** — delete a node from `trees.yml` and the points
  spent on it come back automatically; lower a `max-level` and levels above it
  are trimmed and refunded; change a `cost` and it's re-charged at the new
  price. Editing the tree on a live server can't leave a player in debt.
- 🕒 **Playtime is a collection**, read from the server's own statistic (so time
  banked before BoxCore was installed still counts) and capped by its own last
  tier — the ceiling is visible in the menu, not implied.
- 📦 **Auto-compressor** — a full stack of raw ore folds into one item, unlocked
  per ore type by that ore's own collection tier. A compressed unit is worth
  exactly the stack it came from, so it buys time in the box rather than value;
  right-click to expand one back for an anvil or an enchanting table. Each ore's
  compressed form can be given its own material, name, lore, model data and
  glow — what it's worth lives on the item, so re-skinning never changes it.
  `/box give <ore> [units]` mints one to check a skin without mining for it.
- ✨ **Boosts** — temporary multipliers on ore drops and on collection progress,
  running server-wide or for one player. Start them by command, on a recurring
  schedule, or from a consumable item players right-click. They multiply
  together and are clamped by a configured ceiling; every boost expires on the
  wall clock, so one survives a relog and a global one survives a restart.
- 🔒 **Finite by construction** — every point in the plugin comes from a tier,
  and there are only so many tiers. Nothing is farmable forever.
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
| `/box compress [on\|off]` | Toggle your auto-compressor | `boxcore.use` |
| `/box boost` | Show the boosts running for you | `boxcore.use` |
| `/box points <give\|take\|set> <player> <n>` | Adjust a player's points | `boxcore.admin` |
| `/box unlock <player> <tree.node> [level]` | Force-set a node's level | `boxcore.admin` |
| `/box collection set <player> <id> <amount>` | Set a collection total | `boxcore.admin` |
| `/box collection clearplaced [chunk radius]` | Forget placed-block flags around you, after a mine regen | `boxcore.admin` |
| `/box give <ore> [units] [player]` | Give compressed ore, for testing | `boxcore.admin` |
| `/box boost global <type> <mult> <duration>` | Boost everyone | `boxcore.admin` |
| `/box boost player <name> <type> <mult> <duration>` | Boost one player | `boxcore.admin` |
| `/box boost item <id> [player] [amount]` | Give a boost item | `boxcore.admin` |
| `/box boost clear [global\|<player>]` | End boosts early | `boxcore.admin` |
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
            - effect: regeneration
              amplifier: 0
              amplifier-per-level: 1
          perks:
            - type: lifesteal
              amount: 0.04                  # per level
            - type: auto_smelt              # amount optional
          permissions: [ "myserver.perk.fly" ]
          commands: [ "give %player% diamond 1" ]
```

Attribute names accept every spelling Mojang and Bukkit have used —
`max_health`, `GENERIC_MAX_HEALTH`, `generic.max_health` and
`minecraft:max_health` all resolve to the same attribute, so a config written
today won't break on the next rename.

Operations: `add_number` (flat, the default), `add_scalar` (percent of the
attribute's base) and `multiply_scalar_1` (percent of the running total).

### Perks

Perks are the effects vanilla has no attribute for. Give one an `amount` or
leave it off to take the default; add `per-level: false` to stop it scaling
(what a cooldown wants).

**Fighting**

| Perk | What it does |
|------|--------------|
| `lifesteal` | Heals a share of the melee damage you deal |
| `adrenaline` | Speed II for N seconds after a kill |
| `finisher` | Bonus damage to targets below a third health |
| `player_damage` | Bonus damage to other players |
| `projectile_damage` | Bonus bow, crossbow and trident damage |
| `venom_strike` | Your melee hits poison the target for N seconds |
| `arrow_saver` | Chance not to spend the arrow |
| `mob_loot` | Chance to double a mob's drops |

**Staying alive**

| Perk | What it does |
|------|--------------|
| `potion_power` | Your splash potions land stronger |
| `gapple_boost` | Golden apple effects last longer |
| `last_breath` | Speed II + Resistance I when a hit drops you low (30s cooldown) |
| `debuff_resist` | Incoming harmful potion effects are shorter |
| `second_chance` | Survive a killing blow; `amount` is the cooldown in minutes |
| `hunger_saver` | Less exhaustion from everything you do |

**Grinding**

| Perk | What it does |
|------|--------------|
| `ore_bounty` | Chance to double an ore's drops |
| `log_bounty` | Chance for a bonus log |
| `auto_smelt` | Ores, sand and cobble drop already smelted |
| `replant` | Fully grown crops replant themselves, one seed from the harvest |
| `fishing_speed` | Fish bite sooner |
| `xp_boost` | More XP from every source |

One deliberate rule runs through all of them: **perk loot is not collection
progress**. `auto_smelt` changes what lands on the floor but collections still
count the raw block drop, and the bonus items from `ore_bounty`, `log_bounty`
and `mob_loot` don't count at all. Collections stay a measure of what you
broke and killed, not of what your perks paid you for it.

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

A collection can be fed by something other than items — that's how hours
played sit in the same menu, with the same visible tiers and the same visible
ceiling:

```yaml
  playtime:
    display: "<light_purple>Time Served"
    category: time
    source: playtime          # fed by the playtime module, not by items
    unit: hours               # shown after the amount
    tiers: [ 1, 2, 5, 8, 12, 17, 25, 35, 45, 60, 80, 100 ]
```

Where item collections are counted from is set in `config.yml`:

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

### Respeccing

A respec costs an item rather than skill points, so it's something a player
obtains — a crate reward, a shop purchase, a boss drop — instead of a tax on
progress they already made:

```yaml
skills:
  allow-respec: true
  respec-item:
    item: NETHER_STAR
    amount: 1
    # Optional: only items with exactly this display name count, so a plain
    # nether star can't be used as a token. Blank accepts any of the type.
    name: ""
```

Set `item: ""` for free respecs. The item is taken only once the respec is
certain to go through, so a refused one never eats the token.

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
| `%boxcore_perk_<perk>%` | Total of one perk (`true`/`false` for on-off perks) |
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
| Nodes | **45** across four trees |
| Points to max everything | **281** (combat 89, gathering 84, wayfarer 73, duelist 35) |
| Points from item collections | **175** across 175 tiers |
| Points from playtime | **12** across 12 tiers (1 hour → 100 hours) |
| **Total earnable, ever** | **187** — 67% of the trees |

**The economy is deliberately finite.** Every point comes from crossing a
collection tier, each tier pays once, and there are 187 of them — so a player
who does absolutely everything still ends up 94 points short of owning the
whole tree. Choosing a build is the point; a respec item is how you change
your mind.

That includes playtime. It used to be an uncapped trickle, which meant an AFK
client earned forever; it is now a collection with a last tier at 100 hours.
Early tiers come quickly and then it slows, so the first few points arrive in
an evening and the last ones take a few weeks — after which time online pays
nothing and only gathering does.

The strongest nodes (`Executioner`, `Trophy Hunter`, `Last Stand`, `Cave Eyes`,
`Deep Breath`, `Featherweight`, `Second Chance`, `Headhunter`, `Venom Strike`)
sit behind 10–12 points already spent in their own tree, so no one rushes
straight to a capstone.

The four trees are shaped for a **boxpvp** server. **Combat** and **Gathering**
are wide (four parallel lines each) so there's always something affordable;
**Wayfarer** covers mobility and the grit to survive a bad fight; **Duelist**
is small and late-game, all bows, potions and player-versus-player edges.

Nothing here is a survival-server convenience — no anvil discounts, no
durability savers, no crafting or enchanting bonuses. Every node either wins
fights or speeds up the grind that funds them.

If that's too generous, the levers in order of bluntness are: `points-per-tier`
on individual collections, the playtime collection's tiers, and node
`cost`/`costs`. All three can change on a live server — the refund logic means
nobody is left in debt.

Delete the playtime collection to stop paying for time online, retune
`points-per-tier`, or delete trees wholesale — the refund logic means you can
rebalance on a live server without wiping anyone.

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
