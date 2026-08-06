# BoxTutorial

A **Paper 1.21.4** plugin that teaches boxpvp by making somebody play a tiny
version of it. `/tutorial` puts a new player in their own private arena with a
mine that fills itself back up and a villager who trades ore for gear, walks
them up the ladder — **wood → axe → more wood → pickaxe → ore → sword → armour
→ a better axe → compressed log → charm** — and sends them back to spawn with
everything they made.

Every reward is a named slot you can point at **your own item**: hold it, click
the slot in `/tutorial items`, done.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design, and maintained the same way. Review it and test it on your own
> server before trusting it in production.

> This is a separate project from *CustomAchievements*, *BoxCore* and
> *AntiCheat* in the same repository: different module (`tutorial/`), different
> package (`com.diamend.boxtutorial`), different purpose. It has **no dependency
> on any of them**, and none on a mine-reset, shop, economy or world-management
> plugin either — it builds and runs the arena itself.

---

## Why a sandbox instead of a checklist

Telling a newcomer "mine ore, sell it, buy gear" is a sentence they'll forget
before they've found the mine. Having them *do it once*, in a room with exactly
two things in it, is a thing they'll remember — and the version they learn in
can't be griefed, can't be lost, and can't be got wrong.

So the tutorial is a place, not a list of instructions.

---

## What a player sees

1. They join. A clickable line offers the tutorial (it doesn't grab them).
2. `/tutorial` — the world fades and they're standing on a small platform.
   A **wood mine** on the left, an **ore mine** on the right, a **Trader**
   in front of them, and a boss bar reading *Step 1/11 — Break 8 logs*.
3. They punch 8 logs. The step ticks over. Right-click the Trader: the ordinary
   vanilla trade screen, 8 logs in, one wooden axe out. **The ore is the money** —
   there is no currency in the arena and no economy plugin behind it.
4. Sixteen more logs — and while they're cutting, the mine refills in front of
   them. That's the moment the gamemode lands.
5. Pickaxe. Then the ore mine: stone, coal, iron. Then a sword, then armour.
6. An **axe upgrade** — 24 logs and 2 raw iron for an Efficiency II iron axe,
   bought right before the grind it shortens. Spending on a tool feels like
   going backwards; it isn't, and that's the lesson.
7. The long one: **64 logs for a compressed log** — a glowing log worth a stack,
   which teaches compacting the only way that sticks. That buys the **charm**.
8. Last step: put the charm in the off hand. Their hearts go up, because the
   charm's stats are real off-hand attribute modifiers.
9. Done. Three seconds to read the message, then they're at spawn — carrying
   the axes, the pickaxe, the sword, the armour, the charm and the leftovers.

Everything above is `tutorial.yml` and `config.yml`. Change the ladder, the
mines, the trades, the layout — none of it is compiled in.

---

## Features

- 🏝️ **A private arena per player** — one void world, instances 512 blocks
  apart, built fresh when claimed and handed back when they leave. Dozens of
  players can be in "their own tutorial" while the server holds one world.
- ⛏️ **Mines that regenerate** — a cuboid and a weighted block table, like any
  mine-reset plugin. The shipped ones are deliberately small so the refill
  happens where the player can see it.
- 🧑‍🌾 **A real villager with real trades** — vanilla trade window, vanilla
  behaviour, nothing to learn. Trades are config; ore is the price.
- 🗡️ **Your items, not mine** — sword, axe, pickaxe, armour, charm and
  compressed log are named slots. `/tutorial items` binds any of them to the
  item in your hand, stored whole so a custom item keeps its model data, tags
  and attributes. Nothing is ever taken from your hand to do it.
- 🔮 **A charm that does something** — configured stats become off-hand
  attribute modifiers, so vanilla applies them while it's in the off hand,
  writes them in the tooltip, and removes them when it isn't. No ticking task,
  nothing to leak if the server stops mid-tutorial.
- 📋 **Steps that watch what they do** — break blocks, buy something, carry
  something, hold something in the off hand, run a command, reach a place, play
  for N minutes.
- 🧭 **A boss bar** with the current step and its count, on screen only while
  they're actually in the arena.
- 🔒 **Nothing can go wrong in there** — only the mines can be broken, nothing
  can be placed, nothing can hurt them, and the walls are barriers. A player
  who logs out inside is put back into a new arena when they return.
- 🎒 **They keep what they made.** The arena isn't a sandbox that gets
  confiscated at the door.
- 📖 **A glossary** of the words every boxpvp server assumes you know — box,
  mine, warzone, combat tag, KOTH, kits, dying — on `/tutorial topics`.
- 🛠️ **Staff tools** — `/tutorial start <player>`, `complete`, `reset`,
  `steps`, `reload`, all without a restart.
- 🪧 **PlaceholderAPI** support (optional).

---

## Requirements

- Java **21**
- **Paper** (or Paper-compatible) **1.21.4**
- Optional: **PlaceholderAPI**

The tutorial world is created on first use. Multiverse and friends are welcome
but not needed; if a world with the configured name already exists, it's adopted
rather than created.

---

## Building

```bash
cd tutorial
mvn clean package
```

The jar lands in `tutorial/target/BoxTutorial-1.1.0.jar`. Release notes are in
[`CHANGELOG.md`](CHANGELOG.md).

---

## Commands

Base command: `/tutorial` (aliases `/guide`, `/howto`, `/tut`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/tutorial` | Go in — or, if you're already in there, open the board | `boxtutorial.use` |
| `/tutorial menu` | The step board, wherever you are | `boxtutorial.use` |
| `/tutorial topics` | Browse the glossary | `boxtutorial.use` |
| `/tutorial what <topic>` | Explain one topic in chat | `boxtutorial.use` |
| `/tutorial status` | How far you got | `boxtutorial.use` |
| `/tutorial start` | Run it again from the top | `boxtutorial.use` |
| `/tutorial next` | Skip the step you're stuck on | `boxtutorial.use` |
| `/tutorial stop` | Leave the arena and turn it off | `boxtutorial.use` |
| `/tutorial steps` | List every step, with its trigger | `boxtutorial.admin` |
| `/tutorial items` | Bind the reward items to your own | `boxtutorial.admin` |
| `/tutorial item set\|clear\|give\|list <id>` | The same, by command | `boxtutorial.admin` |
| `/tutorial start <player>` | Put someone else in it | `boxtutorial.admin` |
| `/tutorial complete <player> <step>` | Mark a step done | `boxtutorial.admin` |
| `/tutorial reset <player>` | Wipe their progress | `boxtutorial.admin` |
| `/tutorial reload` | Reload config, steps and arena | `boxtutorial.admin` |

| Node | Default | Grants |
|------|---------|--------|
| `boxtutorial.use` | everyone | The tutorial and the glossary |
| `boxtutorial.admin` | ops | Running it for other people, and editing |

---

## The arena (`config.yml` → `arena:`)

```yaml
arena:
  world: tutorial_arena
  spacing: 512          # blocks between one instance and the next
  max-instances: 32     # how many people can be in the tutorial at once
  y: 64
  radius: 12            # half-width of the platform (12 = 25x25)
  wall-height: 3
  floor: STONE_BRICKS
  wall: BARRIER
  spawn-offset: [0, 1, 8]

  shopkeeper:
    offset: [0, 1, -8]
    name: "<gold>Trader <gray>(right-click)"
    profession: toolsmith
    type: plains

  mines:
    wood:
      min: [-9, 1, -2]     # offsets from the middle of the platform;
      max: [-6, 3, 1]      # y: 1 is the first block above the floor
      refill-at: 50        # refill once it's down to 50% left
      blocks:              # weights, not percentages
        OAK_LOG: 90
        DARK_OAK_LOG: 10
      drops:               # what a block gives instead of its own drop
        DARK_OAK_LOG: "OAK_LOG 4"
    ore:
      min: [6, 1, -2]
      max: [9, 3, 1]
      refill-at: 50
      blocks:              # weights, not percentages
        STONE: 40
        COAL_ORE: 20
        IRON_ORE: 40

  trades:
    axe:
      cost: [ "OAK_LOG 8" ]     # up to two different items (a vanilla limit)
      result: "item:axe"        # a named slot — see `items:` below
    axe_t2:
      cost: [ "OAK_LOG 24", "RAW_IRON 2" ]
      result: "item:axe_t2"
    charm:
      cost: [ "item:compressed_log 1" ]
      result: "item:charm"

  return:
    mode: WORLD_SPAWN     # or LOCATION (world/x/y/z), or COMMAND (e.g. spawn)
```

**Instances** are numbered from the origin outwards: instance 0 sits at x=0,
instance 1 at x=512, and so on. Each is rebuilt from the blueprint when it's
claimed, so whatever the last player did to it stops mattering. Leaving the
world — walking out, `/spawn`, being summoned by staff, logging off — hands the
instance straight back to the pool; the player's progress is untouched and
`/tutorial` drops them into a new one at the same step.

**Mines** count what comes out of them rather than scanning for gaps, and refill
when enough has gone — and refill *immediately* if the whole thing is cleared,
whatever `refill-at` says, because the counter is only a guess and nobody should
be left standing in an empty room waiting on it. Sizing matters more than it
looks: a mine large enough never to refill teaches nothing, and the refill is the
single most important idea in the gamemode.

A mine's `drops:` table overrides what a block gives when it's broken there, so
a rarer block in the table can simply be worth more of the common one — which is
how the shipped wood mine makes dark oak worth four oak logs, instead of a
second item a first-day player has to work out what to do with.

**Trades** become real `MerchantRecipe`s on a real villager. That's the whole
integration — the player sees the trade window they already know, and the plugin
watches Paper's `PlayerTradeEvent` to tick the step off. A trade whose *price*
is one of the named items is checked against the registry before it goes
through: vanilla decides for itself how closely an ingredient has to match, and
a charm that costs one compressed log must not be buyable with an ordinary log.
What that check reads is the **trade window's cost slots** — the item actually
being handed over — and not the player's inventory, which by then no longer
holds it.

---

## Your items (`config.yml` → `items:`)

```yaml
items:
  sword:
    label: "Sword"
    material: IRON_SWORD
    name: "<white>Practice Sword"
    lore: [ "<dark_gray>From the practice yard." ]
  axe_t2:
    label: "Axe II"
    material: IRON_AXE
    name: "<aqua>Reinforced Axe <gray>(II)"
    enchants:              # default only — a bound item is never edited
      efficiency: 2
  charm:
    label: "Charm"
    material: NETHER_STAR
    name: "<light_purple>Novice Charm"
    glow: true
    stats:                 # applied as off-hand attribute modifiers
      max_health: 4        # +2 hearts while it's in the off hand
      attack_damage: 1
      armor: 2
```

Seven slots ship: `axe`, `axe_t2`, `pickaxe`, `sword`, `armor`,
`compressed_log`, `charm`.
What's above is only the **default** — to use your own:

- **`/tutorial items`** — a menu of every slot. **Click holding an item** and
  that item becomes the slot (your item stays in your hand; a copy is stored).
  **Click empty-handed** for a copy of the current one. **Right-click** to go
  back to the default.
- **`/tutorial item set <id>`** — the same thing without the menu.
- `/tutorial item clear|give|list <id>` — reset one, get one, see them all.

Bindings live in `items.yml`, stored as the item's own bytes, so an item from
ItemsAdder, Oraxen, MMOItems or a datapack keeps everything the plugin doesn't
understand. Villagers already standing in front of a player are re-issued their
trades the moment you bind something, so you can retune the ladder live.

`stats:` uses vanilla attribute names (`max_health`, `attack_damage`, `armor`,
`armor_toughness`, `movement_speed`, `attack_speed`, `knockback_resistance`,
`luck`). They're looked up in the registry rather than by constant, so both the
1.21.2 renames and the older `generic.` spellings work. If your own charm brings
its own attributes, leave `stats:` out and nothing is added.

`enchants:` takes vanilla enchantment names (`efficiency`, `unbreaking`,
`sharpness`, `fortune`, …) and applies only to the built-in default. An item you
bind arrives with whatever enchantments it already had, and nothing here adds
to them.

---

## The steps (`tutorial.yml`)

Steps run in the order they appear in the file. Move a block up, and it happens
sooner.

```yaml
steps:
  chop-wood:
    icon: OAK_LOG
    name: "<yellow>Break 8 logs"
    description:
      - "<gray>The wood mine is on your left. Punch it — no tool needed yet."
    trigger: BREAK_BLOCK
    target: OAK_LOG
    amount: 8
    hint: "<dark_gray>Hold left-click on the logs."
```

| Trigger | Target | Completes when… |
|---------|--------|-----------------|
| `BREAK_BLOCK` | a material | they break that many |
| `BUY_ITEM` | what the trade gives | they take it from the trader |
| `HAVE_ITEM` | a material or `item:<id>` | they're carrying that many |
| `OFFHAND_ITEM` | a material or `item:<id>` | it's in their off hand |
| `READ` | – | they click the step in the menu |
| `MANUAL` | – | staff run `/tutorial complete` |
| `RUN_COMMAND` | the command, no slash | they type it (`sell` also matches `/sell all`) |
| `PLACE_BLOCK` / `PICK_UP_ITEM` / `CRAFT_ITEM` | a material | the obvious thing |
| `KILL_MOB` / `KILL_PLAYER` | an entity type / – | the obvious thing |
| `ENTER_WORLD` | a world name | they arrive in it |
| `REACH_LOCATION` | `world;x;y;z;radius` | they stand inside it |
| `PLAYTIME_MINUTES` | – | `amount` minutes of tutorial time pass |

The gear steps use `HAVE_ITEM` rather than `BUY_ITEM` on purpose: it completes
however they got the thing, so a player who buys ahead of the step isn't told to
go and buy a second one.

A test enforces that **every shipped step asks for something the arena can
provide** — a step wanting a block no mine contains, or gear no trade sells,
fails the build rather than stranding a player.

---

## Configuration highlights (`config.yml`)

| Setting | Default | Does |
|---------|---------|------|
| `auto-start` | `true` | Offer the tutorial on a player's first join |
| `auto-start-mode` | `invite` | `invite` = a clickable line; `enter` = teleport them straight in |
| `auto-start-existing` | `false` | Also offer it to players who joined before install |
| `sequential` | `true` | Arm one step at a time (`false` = free-order checklist) |
| `allow-step-skip` | `true` | Players may walk past a step they can't finish |
| `bossbar` | `true` | The on-screen step tracker |
| `nudge-interval-ticks` | `20` | How often the bar refreshes and "carrying it" is checked |
| `return-delay-ticks` | `60` | Beat between the last step and the teleport out |
| `completion.commands` | `[]` | Console commands when someone finishes (`%player%`) |

---

## PlaceholderAPI

| Placeholder | Shows |
|---|---|
| `%boxtutorial_step%` | the current step's name |
| `%boxtutorial_step_goal%` | what it's asking for |
| `%boxtutorial_step_number%` / `_total%` | position and length |
| `%boxtutorial_completed%` / `_remaining%` / `_percent%` / `_bar%` | progress |
| `%boxtutorial_active%` / `_finished%` | `true` / `false` |
| `%boxtutorial_done_<id>%` | `true` when that step is done |

---

## Data & files

```
plugins/BoxTutorial/
├── config.yml      # behaviour, the arena, the items and messages
├── tutorial.yml    # the steps and the glossary
├── items.yml       # the items you've bound (written by the plugin)
└── progress.yml    # one short entry per player
```

The arena world (`tutorial_arena` by default) is generated empty and rebuilt per
claim, so it needs no backing up and nothing in it is worth keeping.

---

## What it deliberately doesn't do

- **No economy.** Ore is the currency, inside the arena and nowhere else. The
  tutorial can't be farmed for money because there is no money in it.
- **No inventory confiscation.** They keep what they made.
- **No forced tutorial.** It offers, it can be stopped, and it can be run again.
- **No achievement system.** Ten steps and it's finished. *CustomAchievements*
  in this repo is the plugin for long-run objectives.
