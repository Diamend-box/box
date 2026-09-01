# Spyglass

**Read any player's data from the server console** — on a **Paper 1.21.4**
server, whether they are logged in or not.

Type `/spy Notch` in the console and you get who they are, how they are and
where they are. Type `/spy Notch inventory` and you get all 41 slots, enchants
and durability included — **even if Notch logged off three weeks ago**, because
the offline half of the plugin reads their save file directly.

> ℹ️ **Made with AI.** Written by an AI assistant (Anthropic's Claude) from a
> human's design. Review it and test it on your own server before trusting it in
> production.

> A separate project from the other plugins in this repository: its own module
> (`spyglass/`), its own package (`com.diamend.spyglass`), its own jar.

---

## Why it exists

Every other way to look at a player's data assumes you are *in the game*: open
their inventory, stand where they stand, read a GUI. From the console you get
nothing. Spyglass is the other way round — the console is the first-class
audience, and everything is aligned plain text meant to be read in a terminal
(and grepped out of a log).

Two halves, one command:

| | Online player | Offline player |
|---|---|---|
| Where the data comes from | the live server objects | `world/playerdata/<uuid>.dat`, `stats/`, `advancements/` |
| Inventory, ender chest, armour | ✅ | ✅ |
| Health, hunger, XP, abilities, position | ✅ | ✅ |
| Effects, attributes, persistent data, tags | ✅ | ✅ |
| Statistics, advancements | ✅ | ✅ |
| Raw NBT tree | ✅ (saved first, so it is current) | ✅ |
| Ping, client brand, IP address | ✅ | — not stored in a save |
| Effective permissions, live scoreboard | ✅ | — only exist in memory |

---

## Usage

```
/spy <player> [section] [filter] [page]
```

`<player>` is a name (exact, or a unique prefix of someone online) or a UUID.
The default section is `overview`.

```
> spy Notch
=== Notch (online) — overview ===
-- Overview --
  uuid                069a79f4-44e9-4726-a5be-fca90e38aaf5
  game mode           SURVIVAL
  health              [########--] 17.5/20
  hunger              [#######---] 14/20  saturation 2.5
  experience          level 31 (50.0% to next, 1,024 total)
  position            world 120 64 -33
  ping                42 ms
  held item           diamond_sword  dur 1441/1561  "Excalibur"  {sharpness 5}
  inventory           23/41 slots used, 918 item(s)
  effects             speed 2
  flags               sprinting, op
```

### Sections

| Section | What you get |
|---|---|
| `overview` | who they are, how they are, where they are |
| `identity` | name, UUID, op, ban and whitelist state, first seen |
| `connection` | address, ping, client brand, session times |
| `vitals` | health, hunger, air, fire, XP, game mode, every flag |
| `position` | world, coordinates, facing, biome, light, respawn point, last death |
| `inventory` | all 41 slots, labelled hotbar / pack / armour / off hand |
| `enderchest` | the ender chest |
| `armor` | what they are wearing and holding |
| `effects` | active potion effects with levels and time left |
| `attributes` | attribute values and their modifiers |
| `stats` | every statistic they have a number for, under vanilla's names |
| `advancements` | done, part-done, and what criteria are left |
| `permissions` \* | effective permission nodes and which plugin granted them |
| `scoreboard` \* | team, objectives and scores |
| `data` | persistent data container, scoreboard tags |
| `recipes` | recipes they have unlocked |
| `item <slot>` | everything about one item, shulker contents included |
| `nbt [path]` | the raw save tree |
| `all` | every section at once — best paired with `/spy dump` |

\* needs the player online. Everything else reads from their save file.

Aliases work (`inv`, `ec`, `loc`, `perms`, `raw`, …), and any long section takes
a **filter** and a **page**:

```
> spy Notch stats mined          # only statistics matching "mined"
> spy Notch permissions essentials 2
> spy Notch inventory shulker
```

An item filter looks **inside** shulker boxes and bundles, up to four levels
down, and prints the trail it took to get there — so the answer does not depend
on how neatly somebody packed their bag:

```
> spy Notch inventory tnt
   9 pack      shulker_box  holds:2 > tnt x16
```

### The raw save tree

```
> spy Notch nbt abilities
=== Notch (online) — nbt abilities ===
  file        world/playerdata/069a79f4-44e9-4726-a5be-fca90e38aaf5.dat
  written     2026-08-13 04:35:12 (0s ago) (saved just now for this read)

  abilities: compound(7)
    flying: 0b
    mayfly: 1b
    instabuild: 0b
    invulnerable: 0b
    mayBuild: 1b
    flySpeed: 0.05f
    walkSpeed: 0.1f
```

Paths take dots, slashes and brackets, and don't care about capitalisation:
`Inventory.0.components`, `inventory[0]/id`. With no path you get the whole
tree. For an online player the server is asked to save them first, so what you
read is current rather than as old as the last autosave (`save-before-nbt`).

### The other commands

| Command | Purpose |
|---|---|
| `/spy list [world]` | everyone online: world, position, health, mode, ping |
| `/spy find <item> [player]` | find an item in online inventories and ender chests, containers included |
| `/spy watch <player> [categories]` | follow what someone does, live, in the console |
| `/spy unwatch <player\|all>` | stop following |
| `/spy watching` | who is being followed, by whom |
| `/spy dump <player>` | write the whole report — raw NBT included — to a file |
| `/spy dumps [player]` | the dumps on disk, newest first |
| `/spy diff <player> [file] [all]` | what changed between a dump and now |
| `/spy sections` | the table above, in the console |
| `/spy reload` | re-read `config.yml` |

### Watching, live

```
> spy watch Notch
[Spyglass] Watching Notch (chat, command, connection, inventory, blocks, combat, state).
[spy] 04:35:31 Notch chat  where is everyone
[spy] 04:35:36 Notch command  /home base
[spy] 04:35:37 Notch teleport  world 120 64 -33 -> world -812 71 344  (COMMAND)
[spy] 04:35:44 Notch block break  diamond_ore at world -814 12 341
[spy] 04:35:49 Notch hurt by  player Jeb for 6.5, 11.5 health left
```

Categories: `chat`, `command`, `connection`, `movement`, `inventory`, `blocks`,
`combat`, `state`, or `all`. Name them to narrow it:
`/spy watch Notch combat chat`. `movement` is off by default because it is by
far the loudest. A watch on someone who is **offline** starts the moment they
join, and `watch.auto` in the config re-arms chosen names after a restart.

Busy players can outrun a console, so each watch has a lines-per-second budget;
anything over it is dropped and counted (`… 14 line(s) dropped to keep up`).

### Dumping

```
> spy dump Notch
[Spyglass] Building a full report on Notch...
[Spyglass] Wrote 2,918 lines to plugins/Spyglass/dumps/Notch-20260813-043512-880.txt
           (and the same again as .json)
```

Every section plus the entire NBT tree. Each dump lands as a **pair**: the
`.txt` is the page you read, and the `.json` beside it is the same report as
data — `{"entries":[{"section","kind","label","value"}]}` — for `/spy diff` and
for anything else you want to point at it. The two are written and pruned
together (`dumps.keep`).

### What changed since then

```
> spy diff Notch
[Spyglass] Comparing Notch against Notch-20260813-043512-880.json...
=== Notch — diff ===
  from        Notch-20260813-043512-880.json  (2026-08-13 04:35:12 (6h 12m ago))
  to          now  (2026-08-13 10:47:41 (0s ago))

-- Vitals --
  ~ health                  [##########] 20/20  ->  [#####-----] 11/20
  ~ food                    20/20  ->  14/20

-- Inventory --
  - 13 pack     diamond x12
  + 13 pack     dirt x1

-- Statistics --
  ~ mined.stone             482  ->  1,043
  + custom.damage_taken     26

  6 change(s), plus 9 that always move (add "all" to see them).
```

Fields are matched by label, so a value that moved reads as one change rather
than as a removal and an addition; lines with no label (inventory rows, raw NBT)
are compared as a bag. `/spy diff <player> <file>` picks an older dump —
`/spy dumps` lists them — and `all` includes the fields that differ between any
two dumps whatever the player did: timestamps (rendered with their age, so even
a date that never moved reads as changed), the ping, tick counters, and the
entity id a player is handed afresh on every login.

---

## Permissions

| Node | Default | Grants |
|---|---|---|
| `spyglass.use` | op | `/spy` — inspecting, listing, finding, dumping |
| `spyglass.watch` | op | `/spy watch` and `/spy unwatch` |
| `spyglass.sensitive` | op | seeing IP addresses in reports |
| `spyglass.admin` | op | `/spy reload` |
| `spyglass.exempt` | false | cannot be inspected or watched **by other players** |

The console is the server owner by definition: it always sees everything,
including players holding `spyglass.exempt`. `spyglass.exempt` is there so staff
with `/spy` can't quietly read each other; grant it deliberately.

Two privacy switches in `config.yml`: `mask-ip` hides addresses from everyone,
and `log-usage` writes a line to the server log every time somebody inspects
somebody — worth turning on if more than one person has `/spy`.

---

## Configuration

```yaml
page-size: 30              # lines per page of a long section
mask-ip: false             # hide IPs from everyone, not just the unprivileged
save-before-nbt: true      # save an online player before reading their raw NBT
log-usage: false           # log who inspected whom

dumps:
  folder: dumps
  keep: 50                 # 0 keeps every dump

watch:
  default-categories: [chat, command, connection, inventory, blocks, combat, state]
  movement-sample-seconds: 3
  max-lines-per-second: 20
  auto: []                 # names the console follows automatically, across restarts
```

---

## What it does not do

- **It never writes to a player.** No editing inventories, no setting health,
  no moving anyone. The only write it asks for is Bukkit's own
  `Player#saveData()` before reading raw NBT, so the tree you read is current.
- **It does not search offline players' inventories** (`/spy find` covers
  people who are online). Reading every save on disk for one query is not
  something a command should do on a whim — read one player at a time with
  `/spy <player> inventory`.
- A player's IP is not in their save file, so `connection` is thinner offline.

---

## Building

Standard Maven project targeting Java 21:

```bash
cd spyglass
mvn -B clean package
# -> target/Spyglass-1.0.0.jar
```

Drop the jar in `plugins/` on a **Paper 1.21.4** server. No dependencies.

> CI builds this on every push (`.github/workflows/spyglass.yml`), runs the
> tests, and boots a real Paper server to drive `/spy` from an actual console
> before publishing the **`Spyglass-jar`** artifact. That artifact is the
> download.

---

## How it is put together

| Package | What lives there |
|---|---|
| `nbt` | a defensive binary NBT reader, path lookup and a tree printer — no server API involved, so it is unit tested against a save file the tests build themselves |
| `offline` | `playerdata/<uuid>.dat`, `stats/*.json` and `advancements/*.json`, and the inspector that renders them |
| `inspect` | the live-player inspector, item formatting, target resolution |
| `report` | the line model everything renders into: coloured for a console, plain for a file |
| `watch` | the live tail: categories, rate limiting, and the listener that feeds it |

Two design rules worth knowing if you change it:

1. **Never take the report down.** Every field goes through `Safe`, so a call
   this server's fork does not implement prints `n/a` and the other ninety
   fields still arrive.
2. **Disk work is off the main thread.** Reading a save file, a stats file or a
   folder listing happens asynchronously and comes back to the main thread only
   to be sent.
