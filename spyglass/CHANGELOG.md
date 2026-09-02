# Spyglass — changelog

What changed in each release, written for the person deciding whether to update.

The release workflow reads this file: the section matching `spyglass/pom.xml`'s
version becomes the **What's new** part of that release's notes, and the build
**fails** if there isn't one. A version bump without an entry here is a release
whose page doesn't say what it contains, which is the thing this file exists to
prevent.

Add the new section at the top, headed `## <version>`, before bumping the pom.

---

## 1.0.0

First release.

**There is no server API for a player who has logged out.** So the plugin reads
`world/playerdata/<uuid>.dat` itself, plus the `stats/` and `advancements/`
JSON beside it — inventory, ender chest, health, position, abilities, effects,
attributes, persistent data, statistics and advancements. "Any player" means
any player, not any player who happens to be connected.

The NBT reader is deliberately paranoid. The file is written by another process
and may be half-written, from another version, or corrupt, so bad lengths and
runaway nesting fail as an `IOException` rather than as an allocation the size
of the heap.

**19 sections**, filterable and paged: `overview`, `identity`, `connection`,
`vitals`, `position`, `inventory`, `enderchest`, `armor`, `effects`,
`attributes`, `stats`, `advancements`, `permissions`, `scoreboard`, `data`,
`recipes`, `item <slot>`, `nbt [path]` and `all`. Aliases work (`inv`, `ec`,
`loc`, `perms`, `raw`, …). `nbt` takes a path in dots, slashes or brackets, and
for a player who is online the server is asked to write them out first, so the
tree you read is current rather than as old as the last autosave.

**An item filter that looks inside containers** — shulker boxes and bundles,
four levels down, printing the trail it took:

```
> spy Notch inventory tnt
   9 pack      shulker_box  holds:2 > tnt x16
```

Without that, "no diamonds" only ever meant "no diamonds outside a shulker
box", which is where people keep them.

**`/spy find <item> [player|all|saves]`.** Without `saves` it reads the people
who are connected, live. With it, the plugin reads `playerdata` itself, which
is the only way to answer "does anyone on this server have one" rather than
"does anyone here right now". That is real disk work, so it is bounded:
most-recently-played first, stopping at `find.max-saves` files or
`find.time-budget` seconds and saying which. What it read is cached against
each file's timestamp, so the second question is free.

**`/spy watch <player> [categories]`** tails what someone does into the console
as they do it — chat, commands, connections, movement, inventory, blocks,
combat, state — with a per-second budget so a fight cannot outrun the terminal.
A watch on someone offline starts the moment they join, and `watch.auto`
re-arms chosen names after a restart. Turn on `watch.log` and the same lines
are appended to `plugins/Spyglass/logs/<player>.log`, queued and drained once a
second off the main thread, so a watch left running overnight is still readable
in the morning.

**`/spy dump <player>` writes the lot to a file**, because a console scrolls —
as a **pair**: the `.txt` you read and a `.json` of the same report beside it.
The text version cannot be read back once fields are padded into columns, and
the JSON is both what `/spy diff` compares and the thing to point other tooling
at, since it does not change shape when the console formatting does. The two
are created and pruned together.

**`/spy diff <player> [file] [all]`** answers the question a full report can't:
what changed since yesterday. Fields are matched by label, so a value that
moved reads as one change rather than a removal and an addition; lines with no
label are compared as a bag. Fields that differ between any two dumps whatever
the player did — timestamps (rendered with their age, so even a date that never
moved reads as changed), the ping, tick counters, the entity id handed out
afresh on every login — are counted rather than listed unless you add `all`.
`/spy dumps [player]` lists what there is to compare against.

**Offline names come from the server's own `usercache.json`**, not from
Bukkit's `getOfflinePlayer(String)`, which will invent a profile for a name
nobody has ever used and may block on a request to Mojang while it does. The
same cache feeds tab completion and name resolution, so a name completion
offers is a name the command accepts.

**Statistics have one spelling.** Bukkit's `MINE_BLOCK` and Mojang's
`mined.stone` are the same number under two names, which meant a filter that
worked on someone online found nothing on the same player an hour later. Both
paths now fold onto the vanilla name, the one written to `stats/<uuid>.json`.

Also: `/spy list [world]` for everyone online at a glance, and permissions
`spyglass.use`, `.watch`, `.sensitive` (IP addresses), `.admin` (reload) and
`.exempt`, which hides a player from other players' inspections but never from
the console.
