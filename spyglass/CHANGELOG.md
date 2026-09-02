# Changelog

All notable changes to **Spyglass** are documented here.

> This plugin was written with AI assistance (Anthropic's Claude).

## [1.0.0]

First release. **Read any player's data from the server console** — on a
**Paper 1.21.4** server, whether they are logged in or not.

Every other way to look at a player's data assumes you are *in the game*: open
their inventory, stand where they stand, read a GUI. From the console you get
nothing. Spyglass is the other way round — the console is the first-class
audience, and everything is aligned plain text meant to be read in a terminal
and grepped out of a log.

### The half that matters: offline players

There is no server API for someone who has logged out, so the plugin reads
`world/playerdata/<uuid>.dat` itself, plus the `stats/` and `advancements/`
JSON beside it. **"Any player" means any player**, not any player who happens
to be connected right now — inventory, ender chest, health, position,
abilities, effects, attributes, persistent data, statistics and advancements
all come out of the file.

The NBT reader is deliberately paranoid. The file is written by another process
and may be half-written, from another version, or corrupt, so bad lengths and
runaway nesting fail as an `IOException` rather than as an allocation the size
of the heap.

### Added

- **`/spy <player> [section] [filter] [page]`** — 19 sections: `overview`,
  `identity`, `connection`, `vitals`, `position`, `inventory`, `enderchest`,
  `armor`, `effects`, `attributes`, `stats`, `advancements`, `permissions`,
  `scoreboard`, `data`, `recipes`, `item <slot>`, `nbt [path]` and `all`.
  Aliases work (`inv`, `ec`, `loc`, `perms`, `raw`, …), and any long section
  takes a filter and a page number.
- **`/spy <player> nbt [path]`** — the raw save tree, whole or one branch.
  Paths take dots, slashes and brackets and ignore capitalisation. For a player
  who is online the server is asked to write them out first, so what you read
  is current rather than as old as the last autosave.
- **An item filter that looks inside containers.** Shulker boxes and bundles,
  four levels down, printing the trail it took —
  `9 pack shulker_box holds:2 > tnt x16`. Without it, "no diamonds" only ever
  meant "no diamonds outside a shulker box", which is where people keep them.
- **`/spy find <item> [player|all|saves]`** — searches inventories and ender
  chests, containers included. `saves` reads every save file on the disk, which
  is the only way to answer "does anyone on this server have one" rather than
  "does anyone here right now". Bounded three ways: most-recently-played first,
  a file count (`find.max-saves`) and a time budget (`find.time-budget`),
  saying plainly which one it hit. What it read is cached against each file's
  timestamp, so asking again costs no disk.
- **`/spy watch <player> [categories]`** — tails what someone does into the
  console as they do it, by category (`chat`, `command`, `connection`,
  `movement`, `inventory`, `blocks`, `combat`, `state`). A watch on someone
  offline starts the moment they join, and `watch.auto` re-arms chosen names
  after a restart. Each watch has a lines-per-second budget, so a fight cannot
  outrun the terminal.
- **`watch.log`** — the same lines appended to
  `plugins/Spyglass/logs/<player>.log`, which is what makes a watch left
  running overnight worth anything in the morning. Queued and drained once a
  second off the main thread; rotates at 16 MB.
- **`/spy dump <player>`** — the whole report, raw NBT included, written to a
  file, because a console scrolls. Each dump lands as a **pair**: the `.txt`
  you read and a `.json` of the same report beside it. The text version cannot
  be read back once fields are padded into columns, and the JSON is both what
  `/spy diff` compares and the thing to point other tooling at — it does not
  change shape when the console formatting does. The two are created and pruned
  together.
- **`/spy diff <player> [file] [all]`** — what changed between a dump and now.
  Fields are matched by label, so a value that moved reads as one change rather
  than a removal and an addition; lines with no label are compared as a bag.
  Fields that differ between any two dumps whatever the player did — timestamps
  (rendered with their age, so even a date that never moved reads as changed),
  the ping, tick counters, the entity id handed out afresh on every login — are
  counted rather than listed unless you add `all`.
- **`/spy dumps [player]`** — the dumps on disk, newest first.
- **`/spy list [world]`** — everyone online at a glance: world, position,
  health, game mode, ping.
- **Offline names.** UUIDs on disk are named from the server's own
  `usercache.json` rather than Bukkit's `getOfflinePlayer(String)`, which will
  invent a profile for a name nobody has ever used and may block on a request
  to Mojang while it does. That same cache feeds tab completion and name
  resolution, so a name completion offers is a name the command accepts.
- **Statistics under one name.** Bukkit's `MINE_BLOCK` and Mojang's
  `mined.stone` are the same number under two spellings, which meant a filter
  that worked on someone online found nothing on the same player an hour later.
  Both paths now fold onto the vanilla name, the one written to
  `stats/<uuid>.json`.
- **Permissions** — `spyglass.use`, `spyglass.watch`, `spyglass.sensitive`
  (IP addresses), `spyglass.admin` (reload), and `spyglass.exempt`, which hides
  a player from other players' inspections but never from the console.

### Notes

- **It never writes to a player.** No editing inventories, no setting health,
  no moving anyone. The only write it asks for is Bukkit's own
  `Player#saveData()` before reading raw NBT, so the tree you read is current.
- **A broken field never takes the report down.** Every value goes through a
  guard, so a call this server's fork does not implement prints `n/a` and the
  other ninety fields still arrive.
- **Disk work is off the main thread.** Reading a save file, a stats file, a
  folder listing or the name cache happens asynchronously and comes back to the
  main thread only to be sent.
- A player's IP is not in their save file, so `connection` is thinner offline,
  and effective permissions and the live scoreboard only exist in memory.
- It does not read other plugins' storage. Essentials' `userdata`, LuckPerms'
  database and the rest are their own formats; what shows up here is whatever
  they wrote into the player's own save.

### Install

Drop the jar in `plugins/` on a **Paper 1.21.4** server and restart. No
dependencies. Then type `/spy` in the console.

Full documentation:
**[spyglass/README.md](https://github.com/Diamend-box/box/blob/spyglass-v1.0.0/spyglass/README.md)**.

### Verified

Every release is built by CI, which runs the unit suites and then boots a real
headless Paper 1.21.4 server and drives `/spy` from an actual console. The
offline half is checked against a save file written by a separate Python script
from Mojang's format — not by the plugin's own writer — so the assertions
cannot pass merely because the reader and the writer agree with each other.

> **Worth knowing anyway:** reading `playerdata` means parsing a format Mojang
> can change. It is tested against 1.21.4 and copes with the older
> pre-1.20.5 item layout, but a future version could still move something.
> Nothing here writes to a player, so the worst case is a field reading `n/a`.
