# Changelog

Every jar used to be `DarkSea-1.0.0.jar`, which made two builds a fortnight
apart indistinguishable in your plugins folder. They now carry a version and a
build number: **`DarkSea-0.5.0-b96.jar`** is version 0.5.0 built by Actions run
96, and `/version DarkSea` in game reports the same string. A jar you built
yourself says `-blocal`.

**Versions are `0.<playtest>.<patch>`.** The minor number is the playtest the
build was cut for, because that is the only release boundary this project
actually has — a build you install and play. The patch number moves when
something is fixed between playtests. Nothing is 1.0.0 until the sea is
finished; calling day one 1.0.0 was the fib this file exists to correct.

Entries before 0.5.0 are labelled retroactively from the history. They are
accurate about what shipped, but nobody was writing version numbers down at the
time.

---

## 0.5.4 — 2026-09-01

**The Naxome Reliquary.** Relics no longer work from your pack. They work from
a bag, and the bag's slots are bought with cave crystals — which is the point:
the caves produced three materials that did nothing, so a dimension you could
skip entirely stayed skippable. Relic power now runs through them.

- **A new item, the Naxome Reliquary,** sold by the refugee trader for 30
  Chronons. Right-click it (or `/ds relic bag`) to open it. Carrying no
  reliquary means no relic does anything at all.
- **Relics are filed, not carried.** The bag's Chest tile files every woken
  relic in your pack into a collection; clicking one in the collection wears
  it, clicking it again takes it off, and shift-clicking hands the item back
  so relics stay tradeable. Duplicates never stacked a boost, so the
  collection holds one of each.
- **The bag starts at 2 slots and reaches 6.** Extra slots are bought from
  inside it for emberglass, then voidbloom, then godspore — the ladder leans
  deeper as it climbs, so the last slots need the deep caves rather than the
  first geode. Prices are `relics.bag.slot-costs` in config.yml, one line per
  slot; the list running out caps the bag whatever `max-slots` says.
- **Crystals are the only currency for slots.** Chronons already buy boats,
  waking and respecs; letting them buy slots too would have let a captain skip
  the caves and kept the dimension optional.
- The once-a-second boost pass no longer scans inventories, which also kills
  the old surprise where reshuffling your pack silently changed which relics
  were active.

## 0.5.3 — 2026-08-31

Two things you asked for: crystals you can name yourself, and boat upgrades
that cost money instead of tokens.

### Changed

- **Boat upgrades are bought with Chronons at the boat wheel.** Per-level
  upgrade tokens are gone. A token for a level you had not reached yet was a
  dead item you had to carry, and the only thing finding one could tell you was
  "not yet" — a price is legible, and it can be worked towards. Prices are in
  `config.yml` under `boat.levels` and roughly double per tier, then jump at the
  last hull: 45 / 110 / 240 / 450 / 800. Tiers are still bought one at a time
  and in sequence, so there is no saving up to skip ahead. An unpriced tier is
  free, so a config that forgets a number opens progression rather than closing
  it.
- **Chest treasure pays in coin where it used to pay in tokens.** Every token
  entry in `loot.yml` became a fat Chronon cache scaled to its ring, up to
  90–140 in a Trench vault. The sea still pays out boat progress; it now pays in
  something you can spend on anything else instead.
- **`/ds give token` is gone** — `/ds boat set <player> <level>` already did the
  job it was for.

### Added

- **Crystal names, lore and materials are config.** A new `items:` section at
  the bottom of `ores.yml` sets what emberglass, voidbloom and godspore are
  called, what they read as in the hand, and which vanilla item carries them.
  Every field is optional and a missing one keeps the shipped value, so you can
  change a name and leave the lore alone. `/ds reload` applies it.
  Identity is the hidden PDC tag rather than the name, so renaming a crystal
  cannot break a loot table, a shop rule, or a stack already sitting in a chest
  — and the rename decision, if you ever make it, is now a config edit.

Crystals remain upgrade material with no sink, which is still open decision 2 in
`PLAYTEST.md`. Nothing consumes them yet.

---

## 0.5.2 — 2026-08-05

Everything reported from playtest 6, including the chests — which were
reported for the third time and reproduced for the first.

### Fixed

- **Boats stopped dead at the end of a coast.** The throttle handed the boat
  back to the client while it was still travelling at the hull's cruise speed,
  and a rider who has released W is giving the client no forward input, so the
  client's own speed had already decayed to nothing underneath it. Handing over
  there dropped the boat from cruise to a standstill in one tick. The coast now
  runs all the way down to a crawl before letting go, and a little faster, so it
  still reads as stopping.
- **48 chests you could see and not open, and this time the right ones.** One
  mistake made in five places: every buried stair was cut two blocks of air
  high. Climbing a one-block step is a jump, and a jump needs room over your
  head as well as somewhere to land — so all of them worked going down and
  could not be walked back up. The castle's undercroft had no stair at all, the
  watchtower's crypt trench cleared three cells over each tread but not over the
  one below it, and the beast's tail cache was a two-high room with nowhere to
  jump even inside it.
- **The reachability sweep, which had been passing them.** It counted any of
  the twenty-six cells around a chest as "in reach", so a corridor on the far
  side of a wall qualified; and it let a player fall any distance without ever
  asking whether they could get back. Steps are now recorded backwards as well,
  a cell only counts if open water can be reached from it, and being in reach
  means a clear line from the eye to the chest. It also runs against the exact
  islands from `/ds islands` — an island's seed comes from where it sits, so
  the only way to test the castle somebody stood in is to ask for that castle.
- **Naxian Abominations still suffocated.** 0.5.1 checked height and not width.
  A Ravager is about as wide as it is tall, so a marker in a one-block slot — a
  doorway, the gap between two crenellations — had all the headroom it needed
  and still buried its flanks. Spawns need a box now
  (`mob-spawning.spawn-width`, default 2), and it may sit anywhere touching the
  marker rather than being centred on it.
- **No boss rose, even at the nest.** An island that had never raised its boss
  was treated as one whose boss had just died, so every nest in a fresh sea sat
  out a fifteen-minute respawn wait for something that had never existed. The
  wait now applies only where something actually fell.
- **Floating amethyst in the geodes.** A cluster is a directional block that has
  to be attached to something, and setting the material alone left it on its
  default facing with nothing underneath. Buds grow out of a face of real rock
  now, and a cell with nothing to hold on to gets plain shell instead.
- **The vault lever on the battlements.** "Furthest spawn point from the vault"
  is a crenellation gap on a castle. Levers prefer a marker standing on open
  floor — three of its four neighbours walkable — and fall back to the old rule
  only where a shape has nowhere better.

### Changed

- The shop band reads **"▲ buy from them · sell to them ▼"**, from the side of
  the counter the player is standing on.

---

## 0.5.1 — 2026-08-04

Four things from a partial playtest 5.

### Fixed

- **Boats were still jittery.** The 0.5.0 fix removed one cause and left the
  larger one in place. `boostFactor` returns 1.0 — "leave this alone" — once a
  boat is at its speed cap, so at cruise the service alternated between ticks
  that pushed the hull and ticks that ignored it and let it slow. It was
  shoving and dropping the boat several times a second, by design. The throttle
  now targets the cap itself, which is a constant for a given hull, and holds
  there: no measurement feeds into it and there is no per-tick decision left to
  flip.
- **The surge reset your momentum.** It set the boat's velocity and then the
  next movement tick wrote the carried cruise speed straight over the top of
  it, so the burst lasted a fortieth of a second. The surge now primes the
  throttle, and bleeds back down to cruise instead of being cancelled.
- **Naxian Abominations suffocated on arrival.** A spawn marker only promises
  that the marker block is clear, and an Abomination is taller than one block —
  under a low roof its head spawned inside stone. Spawns now need
  `mob-spawning.spawn-clearance` (default 3) blocks of air, searching up and
  then sideways, and skip the point rather than placing a mob somewhere
  arbitrary.

### Added

- **`generation.shape-weights`** — which island shapes a ring raises and how
  often, per ring or as a `default` block. `islands-per-ring` already
  controlled how many islands each ring gets; this is the other half, what they
  are. Unlisted shapes keep their built-in rarity, 0 keeps a shape out of the
  sea, and an unknown shape id costs you that shape rather than the config
  load. Shipped commented out, because the built-in rarities are the intended
  sea.

---

## 0.5.0 — 2026-08-03 (playtest 5 build)

The pass that stopped trusting geometry to be walkable and started proving it.

### Fixed

- **Boats jittered at speed and stopped dead when you let go of W.** Thrust was
  computed from the boat's *measured* velocity each tick, and a ridden boat's
  physics are client-authoritative, so that reading is noisy — the server was
  writing back a slightly different speed every tick, which is jitter by
  construction. Speed is now a value the server holds per rider: holding W eases
  it up, releasing it eases it down over about a second.
- **Keep inventory was off in the cultist caves.** The run-loot death handler
  checked *that* you died, never *where*, so it fired in every world. It is now
  scoped to the sea and the caves.
- **The vein indicator pointed at geodes you had already mined.** It ranked
  purely by distance. It now prefers live veins and only falls back to a
  regrowing one when nothing is live, with wording that tells you which.
- **A tier-5 castle raised about twelve Mariphage Cores in a row.** The boss
  slot refilled the instant it emptied. An island now waits
  `boss-respawn-minutes` (default 15) after its boss falls.
- **Swinging at a geode's amethyst spammed the unbreakable message once per
  hit.** Throttled to one message every two seconds.
- **48 chests could be seen and never reached** — every seed, since the shapes
  were written. See below.

### Changed

- **Godspore drops as a slime ball** rather than an amethyst shard, so it no
  longer looks identical to the geode shell it grows out of.
- The relic-waking tile was cut from four lines to two.

### Added

- **`ChestReachabilityTest`** — the test that found the 48. The existing suite
  asked whether a chest had a standable cell beside it; that is adjacency, and
  it says nothing about whether that cell connects to anywhere. This one floods
  the island from open sea using a player's real movement — walk, step up one,
  fall any distance, swim — and asks whether the flood ever gets within arm's
  reach. Deliberately generous (no fall damage, no drowning) so a failure means
  genuinely walled in.
- **`ClimbPath`** — routes are now a guarantee rather than an arrangement. It
  rises at most one block per step and clears three cells over every tread, so
  what it produces is climbable by construction. The watchtower's spiral (a
  single stone every sixty degrees at radius three) and the spire's ledges
  (rising two blocks at a time, which cannot be jumped at all) are both rebuilt
  on it.
- The spire's tier-4 cache moved into its own grotto passage. One seed in
  thirty-six still sealed the wind-hollow after many attempts, so the hollow is
  now a landmark with nothing in it. That is a retreat, not a fix.
- Jars carry a build number, which is why this file exists.

### Known open

- **The unreachable chests you found in a tier-5 castle are not reproduced.**
  The castle passes the new sweep clean at 40 seeds; the 48 sealed chests were
  all watchtower and spire. Chasing it needs the island id and origin from
  `/ds islands`.

---

## 0.4.0 — 2026-08-03 (playtest 4 build)

- Boat thrust follows the hull's facing instead of your momentum, so turning at
  speed works. (This is the fix that introduced the 0.5.0 jitter.)
- The extraction channel no longer dies mid-dig. `BlockDamageEvent` fires once
  when a dig starts, not per tick, so a held mouse button goes silent for about
  fifteen ticks — longer than the ten-tick abandon grace. Every channel over
  half a second was being killed in that gap, which is why no pickaxe could
  break crystal.
- A full reset no longer empties the sea. Queueing the landfall made the placer
  busy, so the generate immediately after refused itself as already-running.
- World folders delete properly; the guard wanted a `level.dat` that a world
  unloaded without saving had never written.
- Deaths respawn you in the sea rather than the overworld.
- The relic anvil moved to the middle of the board, glints when affordable, and
  the wake landed its own sound, particles and title.
- Castle vault stairs climb until they break into open air instead of taking a
  hardcoded step count sized against a castle that has since grown with tier.
- `/ds diag` calls out zero NPC placements as broken rather than printing a
  number.

## 0.3.0 — 2026-08-02 (playtest 3 build)

- Island mob spawns run on a budget; every mob is named.
- Relic waking added at the artificer.
- Castles gained garrison caches, built as roofed huts, so the courtyard is not
  bare.
- Chest reachability required standing height; two rooms that failed were
  opened.
- The Sunless Trench got its own loot tables.
- An in-game loot-pool editor so custom items can go in chests.

## 0.2.0 — 2026-07-26 → 07-30 (playtest 2 build)

- **The cultist caves**: a sealed 512×512 cave world, carved and validated
  offline, reached by portal from a one-off landfall island.
- Cave ore became **crystal geodes** — emberglass, voidbloom, godspore — few,
  large, contested, on first-touch regen timers with a plugin-enforced
  extraction channel and a real tool curve.
- **The outpost**: NPC shops and vault cracking, with prices in `shops.yml` and
  an in-game `/ds shop` editor.
- Worlds are asked what rules they have rather than what they are named.
- Startup survives a failed step, and `/ds diag` reports on all of it.

## 0.1.0 — 2026-07-17 → 07-22 (first live sea)

Everything before anyone played it.

- The sea itself: ocean world, danger rings, sea armor, zone crossing.
- Eleven island shapes generated from position seeds, from crescents to the
  Ruined Castle, each with beaches, loot and garrisons.
- The Naxome mob rosters, the Mariphage Core, tiered loot with Chronons,
  Vironic sets and named relics.
- Boats: five tiers, hull HP, ram power, naval combat, the boat wheel, salvage,
  stat points.
- The outer rings — Sunless Trench and the lethal Rim — and the Mariphage nest.
- Timed sea resets and run-scoped death loss.
