# BoxTutorial

A **Paper 1.21.4** plugin that teaches boxpvp by making somebody play a tiny
version of it. `/tutorial` puts a new player in their own private arena with a
mine that fills itself back up and a villager who trades ore for gear, walks
them up the ladder — **wood → axe → more wood → pickaxe → ore → sword →
armour** — and sends them back to spawn with everything they made.

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
   in front of them, and a boss bar reading *Step 1/7 — Break 8 logs*.
3. They punch 8 logs. The step ticks over. Right-click the Trader: the ordinary
   vanilla trade screen, 8 logs in, one wooden axe out. **The ore is the money** —
   there is no currency in the arena and no economy plugin behind it.
4. Sixteen more logs — and while they're cutting, the mine refills in front of
   them. That's the moment the gamemode lands.
5. Pickaxe. Then the ore mine: stone, coal, iron. Then an iron sword, then an
   iron chestplate.
6. Done. Three seconds to read the message, then they're at spawn — carrying
   the axe, the pickaxe, the sword, the armour and the leftovers.

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
- 📋 **Steps that watch what they do** — break blocks, buy something, be
  carrying something, run a command, reach a place, play for N minutes.
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

The jar lands in `tutorial/target/BoxTutorial-1.0.0.jar`.

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
      blocks:
        OAK_LOG: 100
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
      result: "WOODEN_AXE 1"

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
when enough has gone. Sizing matters more than it looks: a mine large enough
never to refill teaches nothing, and the refill is the single most important
idea in the gamemode.

**Trades** become real `MerchantRecipe`s on a real villager. That's the whole
integration — the player sees the trade window they already know, and the plugin
watches Paper's `PlayerTradeEvent` to tick the step off.

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
| `HAVE_ITEM` | a material | they're carrying that many |
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
├── config.yml      # behaviour, the arena, and messages
├── tutorial.yml    # the steps and the glossary
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
- **No achievement system.** Seven steps and it's finished. *CustomAchievements*
  in this repo is the plugin for long-run objectives.
