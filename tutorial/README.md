# BoxTutorial

A small **Paper 1.21.4** plugin that walks a first-time player through a
**boxpvp** server: what the gamemode *is*, what they're supposed to do first,
and what the words everyone else is using mean.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design, and maintained the same way. Review it and test it on your own
> server before trusting it in production.

> This is a separate project from *CustomAchievements*, *BoxCore* and
> *AntiCheat* in the same repository: different module (`tutorial/`), different
> package (`com.diamend.boxtutorial`), different purpose. It has **no dependency
> on any of them** — drop it into a server that runs none of the others and it
> works.

---

## The problem it solves

Someone who has never played boxpvp joins, spawns in a lobby, and has no idea
that the mine regenerates, that gear is a consumable, that money is the score,
or that hitting somebody stops them from warping away for ten seconds. They read
`/help`, learn nothing, and leave.

BoxTutorial is the thing that tells them — as a short, ordered walkthrough with a
boss bar keeping their place, plus a glossary they can come back to.

---

## Features

- 📋 **An ordered walkthrough**, defined entirely in `tutorial.yml`. Only the
  step a player is on is armed, so mining while reading step one can't silently
  tick off step three.
- 🎯 **Steps complete themselves** from what the player actually does: break
  blocks, run a command, kill a player, enter a world, reach a place, play for
  N minutes — or simply read the step and click it, which is the right trigger
  for the things a newcomer is missing (knowledge, not actions).
- 🧭 **A boss bar** naming the current step and counting its progress, so the
  tutorial survives the player closing chat. It disappears the moment they
  finish or ask it to.
- 💬 **Click-to-run hints** — a step can offer a command, and it arrives in chat
  as a line they can click instead of retype.
- 📖 **A glossary** of the things every boxpvp server assumes you know — box,
  mine, warzone, combat tag, KOTH, kits, dying — on `/tutorial topics` and
  `/tutorial what <topic>`, readable at any time, tutorial or no tutorial.
- 🚪 **Nothing can trap you.** Optional steps can always be skipped, and by
  default any step can. A skipped step is recorded as *skipped*, not as done —
  the checklist keeps telling the truth.
- 🔁 **It survives a logout.** Progress is stored, and a player who left
  half-way through step three comes back to step three, the bar, and a reminder
  of what it wanted.
- 🎁 **Rewards** — console commands per step and on completion (`%player%`,
  `%uuid%`), for a starter kit that lands when someone actually finishes.
- 🛠️ **Staff tools** — `/tutorial start <player>`, `complete`, `reset`, `steps`
  and `reload`, all without a restart.
- 🪧 **PlaceholderAPI** support (optional) for scoreboards and welcome
  holograms.
- 🪶 **Lightweight** — one boss-bar pass per second for the players actually
  mid-tutorial, seven small event handlers, and a single `progress.yml`. No
  `PlayerMoveEvent` handler, no database, no scheduler per player.

---

## Requirements

- Java **21**
- **Paper** (or Paper-compatible) **1.21.4**
- Optional: **PlaceholderAPI**

---

## Building

```bash
cd tutorial
mvn clean package
```

The jar lands in `tutorial/target/BoxTutorial-1.0.0.jar`. Drop it into
`plugins/` and restart. The build fetches the Paper API from
`https://repo.papermc.io`.

---

## Commands

Base command: `/tutorial` (aliases `/guide`, `/howto`, `/tut`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/tutorial` | Open the checklist | `boxtutorial.use` |
| `/tutorial topics` | Browse the glossary | `boxtutorial.use` |
| `/tutorial what <topic>` | Explain one topic in chat | `boxtutorial.use` |
| `/tutorial status` | How far you got | `boxtutorial.use` |
| `/tutorial start` | Run it again from the top | `boxtutorial.use` |
| `/tutorial continue` | Pick up where you left off | `boxtutorial.use` |
| `/tutorial next` | Skip the step you're stuck on | `boxtutorial.use` |
| `/tutorial stop` | Turn it off (keeps your progress) | `boxtutorial.use` |
| `/tutorial steps` | List every step, with its trigger | `boxtutorial.admin` |
| `/tutorial start <player>` | Start it for someone else | `boxtutorial.admin` |
| `/tutorial status <player>` | Their progress | `boxtutorial.admin` |
| `/tutorial complete <player> <step>` | Mark a step done | `boxtutorial.admin` |
| `/tutorial reset <player>` | Wipe their progress | `boxtutorial.admin` |
| `/tutorial reload` | Reload config and steps | `boxtutorial.admin` |

### Permissions

| Node | Default | Grants |
|------|---------|--------|
| `boxtutorial.use` | everyone | The tutorial and the glossary |
| `boxtutorial.admin` | ops | Running it for other people, and editing |

---

## Writing the tutorial (`tutorial.yml`)

Steps run in **the order they appear in the file**. Move a block up, and it
happens sooner.

```yaml
steps:
  mine:
    icon: IRON_PICKAXE
    name: "<yellow>Break some rock"
    description:
      - "<gray>The mine is shared, and it regenerates."
      - "<gray>This is where your money starts."
    trigger: BREAK_BLOCK
    target: ANY          # or "IRON_ORE, GOLD_ORE, DIAMOND_ORE"
    amount: 16
    command: "/warp mine"   # offered in chat, click to run
    hint: "<gray>Take a pickaxe."
    optional: false
    rewards:
      message: "<green>That's the loop."
      commands: [ "eco give %player% 100" ]
```

### Triggers

| Trigger | Target | Completes when… |
|---------|--------|-----------------|
| `READ` | – | they click the step in the menu |
| `MANUAL` | – | staff run `/tutorial complete` |
| `RUN_COMMAND` | the command, no slash | they type it (`sell` also matches `/sell all`) |
| `BREAK_BLOCK` | a material | they break that many |
| `PLACE_BLOCK` | a material | they place that many |
| `PICK_UP_ITEM` | a material | they pick up that many |
| `CRAFT_ITEM` | a material | they craft it |
| `KILL_MOB` | an entity type | they kill that many |
| `KILL_PLAYER` | – | they win a fight |
| `ENTER_WORLD` | a world name | they arrive in it |
| `REACH_LOCATION` | `world;x;y;z;radius` | they stand inside it |
| `PLAYTIME_MINUTES` | – | `amount` minutes of tutorial time pass |

A blank target (or `ANY`) counts everything. A target may be a comma-separated
list — `target: "sell, shop"` accepts either command.

> **The shipped defaults are a starting point.** They describe boxpvp the
> gamemode, which is the part a first-timer is missing, but the *commands* are
> guesses at yours. Point `command:` and `target:` at whatever your server
> actually uses, then `/tutorial reload`. Anything server-specific in the
> defaults is marked `optional: true`, so nobody gets stuck on a `/sell` that
> doesn't exist here.

### The glossary

```yaml
topics:
  combat-tag:
    icon: CLOCK
    title: "<red>Combat tag"
    lines:
      - "<gray>The moment you hit a player, you're tagged for a few seconds."
      - "<gray>While tagged you can't teleport, warp or fly."
```

Topics are found by id, by prefix, or by a word in the title, so
`/tutorial what combat` works.

### Text formatting

Names, descriptions, hints and messages accept **both**
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) (`<green>`,
`<bold>`, `<gradient:…>`) and classic colour codes (`&a`, `&l`, `&#ff8800`) —
mixed freely.

---

## Configuration (`config.yml`)

The settings worth knowing about; the file itself documents the rest.

| Setting | Default | Does |
|---------|---------|------|
| `auto-start` | `true` | Start the tutorial by itself on a player's first join |
| `auto-start-existing` | `false` | Also start it for players who joined before install |
| `join-delay-seconds` | `4` | Wait, so it lands after the join spam |
| `remind-on-join` | `true` | Re-state the current step on a later join |
| `sequential` | `true` | Arm one step at a time (`false` = free-order checklist) |
| `allow-step-skip` | `true` | Players may walk past a step they can't finish |
| `allow-restart` | `true` | Players may run the whole thing again |
| `bossbar` | `true` | The on-screen step tracker |
| `actionbar` | `false` | An actionbar line as well |
| `nudge-interval-ticks` | `20` | How often the bar refreshes and places are checked |
| `completion.commands` | `[]` | Console commands when someone finishes |
| `completion.broadcast` | `false` | Tell the server when someone finishes |
| `autosave-minutes` | `5` | How often progress is written out |

---

## PlaceholderAPI

| Placeholder | Shows |
|---|---|
| `%boxtutorial_step%` | the current step's name |
| `%boxtutorial_step_goal%` | what it's asking for |
| `%boxtutorial_step_number%` | which step they're on |
| `%boxtutorial_total%` | how many there are |
| `%boxtutorial_completed%` / `_remaining%` | done / left |
| `%boxtutorial_percent%` | 0–100 |
| `%boxtutorial_bar%` | a ten-segment progress bar |
| `%boxtutorial_active%` | `true` while it's running |
| `%boxtutorial_finished%` | `true` once they've been through it |
| `%boxtutorial_done_<id>%` | `true` when that step is done |

---

## Data & files

```
plugins/BoxTutorial/
├── config.yml      # behaviour and messages
├── tutorial.yml    # the steps and the glossary
└── progress.yml    # one short entry per player
```

`progress.yml` is written by the plugin — it holds a name, three flags, a list
of step ids and any part-finished counts, for everyone who has ever joined.
`config.yml` and `tutorial.yml` are yours; the plugin only reads them.

---

## What it deliberately doesn't do

- **No achievement system.** Steps aren't goals to grind; there are a handful
  and then it's finished. *CustomAchievements* in this repo is the plugin for
  long-run objectives.
- **No economy, warps or kits.** It teaches yours. Every action it suggests is a
  command you configured, run by the plugin that owns it.
- **No forced tutorial.** It can always be stopped, skipped and reopened. A
  player who wants to work it out for themselves is allowed to.
