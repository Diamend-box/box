# DarkSea

A Minecraft **1.21.4** (Paper) plugin that recreates the feel of Arcane
Odyssey's *Dark Sea*: one safe island at the center of an endless, hostile
ocean that grows harsher the farther out you sail — dotted with scattered
islands holding **MythicMobs** encounters and distance-scaled loot,
survivable only with the right **sea armor** and, eventually, a better
**boat**.

> ℹ️ **Made with AI.** This plugin was written by an AI assistant (Anthropic's
> Claude) working from a human's requests, and is maintained the same way. It's
> shared here in the interest of transparency — review the code and test it on
> your own server before relying on it in production.

---

## Features

- 🌊 **A dedicated ocean world** (`dark_sea`) — procedurally generated open
  water with a noise-varied seabed, no vanilla land, structures or caves.
  The home island is pasted from a schematic at the center and becomes the
  world spawn.
- ☠️ **Danger rings** — config-driven concentric zones; the farther from
  center, the harsher the potion effects applied to unprotected players,
  with action-bar warnings on every crossing.
- 🛡️ **Sea armor** in four tiers (Tidewalker → Stormplate → Abyssal →
  Leviathan). Protection is the **lowest** tier among your four worn pieces —
  full commitment required. Partial protection *downgrades* danger instead of
  binary pass/fail: a Tier 2 set in Zone 3 feels like Zone 1.
- 🏝️ **Scattered islands** — pre-built schematics randomly placed per ring
  with guaranteed spacing, pasted asynchronously through FastAsyncWorldEdit.
  Marker blocks inside the builds become **refilling loot chests** and
  **MythicMobs spawn points**.
- 🐙 **Proximity encounters** — islands activate when a player sails close,
  spawning their tier's Mythic set (with per-island and global caps) and
  cleaning up after everyone leaves.
- 🎁 **Tiered loot** — weighted tables per ring; outer chests drop better
  loot, tier-appropriate armor (with a small chance of next-tier pieces) and
  boat upgrade tokens.
- ⛵ **Boat upgrades** — a per-player boat level granting real speed and a
  shield that lets you *scout* one ring farther than your armor alone.
- 🔄 **Resets built in** — `/ds reset soft` re-pastes every island in place
  (heals damage, restocks chests); `/ds reset full confirm` deletes the world
  and regenerates a **brand-new island layout** from the same schematics.

---

## Requirements

- Java **21**
- A **Paper** (or Paper-compatible) server running **1.21.4**
- **FastAsyncWorldEdit** 2.15.x (hard dependency — provides WorldEdit; pasting
  runs off the main thread, which vanilla WorldEdit does not support)
- **MythicMobs** 5.x (hard dependency)

---

## Building

```bash
cd darksea
mvn clean package
```

The finished plugin is written to `target/DarkSea-1.0.0.jar`. Drop it into
your server's `plugins/` folder (next to FastAsyncWorldEdit and MythicMobs)
and restart.

> The build downloads the Paper API from `repo.papermc.io`, the WorldEdit API
> from `maven.enginehub.org` and the MythicMobs API from `mvn.lumine.io` — the
> build machine needs access to those repositories. If one is unreachable,
> install the server's own jars into the local Maven repository with
> `mvn install:install-file` (they contain the same API classes).

---

## Getting started (first run)

1. Start the server once with the plugin installed. It creates the
   `dark_sea` world (pure ocean) and the folder layout below.
2. Run `/ds generate`. With no schematics yet, **demo mode** (on by default —
   `generation.demo-islands`) builds simple sand-platform islands in code: a
   plain home platform at the center and, in every ring, small islands each
   with one loot chest and one mob spawn point. The shipped `mobs.yml` uses
   vanilla mobs, so encounters and loot work immediately — no building
   required.
3. Sail out. Zone effects, armor, mobs, loot, boats and both resets all work.

**When you're ready for real content:** drop your **home island** schematic
into `plugins/DarkSea/schematics/spawn/` and island builds into
`schematics/tier1/` … `tier4/` (see
[Building island schematics](#building-island-schematics)), replace the mobs in
`mobs.yml` with your **MythicMobs** internal names, set
`generation.demo-islands: false`, then `/ds reset full confirm` to regenerate
with your builds. Real schematics take precedence over demo islands per tier,
so you can migrate one ring at a time.

---

## Commands

Base command: `/darksea` (alias: `/ds`)

| Command | Description | Permission |
| --- | --- | --- |
| `/ds status` | Your zone, protection tier, boat level, exposure verdict | `darksea.use` (default: all) |
| `/ds boat upgrade` | Consume a held token to raise your boat level | `darksea.use` |
| `/ds tp` | Teleport to the home island | `darksea.tp` (default: op) |
| `/ds generate [count]` | Place islands for all unfilled rings, or only `count` at a time (re-runnable) | `darksea.admin` |
| `/ds reset soft` | Re-paste all islands in place: heal damage, restock loot | `darksea.admin` |
| `/ds reset full confirm` | Delete & regenerate the world with a new layout | `darksea.admin` |
| `/ds island list [tier]` | List placed islands | `darksea.admin` |
| `/ds island tp <id>` | Teleport to a placed island | `darksea.admin` |
| `/ds give armor <tier> [player]` | Give a full sea-armor set | `darksea.admin` |
| `/ds give token <level> [player]` | Give a boat upgrade token | `darksea.admin` |
| `/ds boat set <player> <level>` | Set a player's boat level | `darksea.admin` |
| `/ds reload` | Reload configs (zones, loot, mobs, messages, boat stats) | `darksea.admin` |

`darksea.bypass` (default: op) grants immunity to sea exposure. Creative and
spectator players are always exempt.

---

## How exposure works

Every second, each player in the Dark Sea is resolved to a zone and:

```
exposure = zone required tier − (armor tier + boat shield)
```

- `exposure ≤ 0` — fully protected, nothing happens.
- `exposure ≥ 1` — you receive the effects of the ring whose required tier
  equals your exposure. Partial protection downgrades danger: Tier 2 armor in
  Zone 4 gives you Zone 2's effects, not Zone 4's.

Armor tier is the **lowest** `darksea:tier` tag among your four worn pieces
(missing/vanilla pieces count as 0). The tag lives in the item's persistent
data — it survives anvils and can't be faked by renaming. Boat shield only
applies while you're actually riding a boat in the Dark Sea.

Effects are applied with a short duration and re-applied each pass, so they
lapse naturally a couple of seconds after you sail back to safety.

---

## Building island schematics

Build islands anywhere (a creative flatworld is fine), then copy/save them
with WorldEdit: `//copy` standing at your chosen **origin point** (that exact
spot lands at `generation.paste-y`, default Y 58 — pick it at the build's
waterline), then `//schem save <name>`.

Two marker blocks are scanned after pasting and replaced:

| Marker (configurable) | Becomes |
| --- | --- |
| `LODESTONE` | A loot chest (facing away from the island center), registered for refills |
| `GOLD_BLOCK` | Removed — becomes a MythicMobs spawn point |

Optional sidecar YAML next to a schematic (same basename, `.yml`):

```yaml
# schematics/tier2/wreck_small.yml
weight: 3      # selection weight within the tier pool (default 1)
paste-y: 60    # override generation.paste-y for this template
```

Put templates in `schematics/tier1/` … `tier4/`; the home island goes in
`schematics/spawn/` (first schematic found is used).

---

## MythicMobs & loot

- `mobs.yml` maps each tier to a weighted list of Mythic **internal names**
  (the ids from your `Mobs/*.yml`) with per-tier levels. The shipped file
  contains **placeholder names** — replace them with mobs that exist on your
  server, or nothing will spawn (an unknown type logs one warning).
- Islands activate when a player is within `activation-radius` (default 64).
  One mob spawns per scan pass until the per-island cap is reached; mobs
  despawn after `abandon-cooldown-minutes` with nobody near.
- `loot.yml` defines a weighted table per tier: vanilla items, sea-armor
  pieces and boat tokens. Chests refill on open once their tier's cooldown
  has elapsed; timestamps persist across restarts.

## Boats

Boat levels live in `config.yml` (`boat.levels`): name, speed multiplier and
shield per level. Upgrading consumes a **Boat Upgrade Token** (found in loot)
matching the *next* level, held in the main hand: `/ds boat upgrade`. Levels
persist in `playerdata/<uuid>.yml`. Speed is applied by scaling the boat's
horizontal velocity, capped at `speed-cap-base × multiplier` blocks/tick —
tune both to taste.

---

## Resetting the sea

The Dark Sea is designed to be re-rolled — the ocean is procedural and the
islands are schematics plus a registry file, so nothing of value lives in the
world folder itself.

- **`/ds reset soft`** — keeps the map. Re-pastes the home island and every
  registered island over itself: heals griefing/mining damage, restores
  stolen chests, clears every refill timestamp (instant restock), despawns
  tracked mobs so encounters respawn. Island positions do not change.
- **`/ds reset full confirm`** — new sea entirely. Players in the world are
  teleported to the main world spawn; the world folder and island registry
  are deleted; the world is recreated with a fresh seed and generation
  re-runs automatically. Same schematics, brand-new random layout — anything
  players built out there is wiped (by design: the Dark Sea is not a place to
  settle).

Scheduled automatic resets are deliberately not in v1 — drive `/ds reset`
with any command scheduler if you want a cadence.

---

## Configuration files

```
plugins/DarkSea/
├── config.yml        # world shape, center, zones, exposure, armor, generation,
│                     # markers, mob-spawning caps, boat stats, messages
├── mobs.yml          # per-tier MythicMobs sets (internal names + weights + levels)
├── loot.yml          # per-tier weighted loot tables
├── islands.yml       # placed-island registry — plugin-managed, don't hand-edit
├── schematics/
│   ├── spawn/        # home island (first .schem found is used)
│   └── tier1..tier4/ # island template pools (+ optional .yml sidecars)
└── playerdata/
    └── <uuid>.yml    # boat level
```

Zones, armor names, generation counts, mob sets, loot tables, boat stats and
messages all reload with `/ds reload`. World shape (`world.*`) and task
intervals need a restart. All user-facing text is
[MiniMessage](https://docs.advntr.dev/minimessage/format.html).

---

## Low-RAM hosts (Minehut and friends)

The Dark Sea is an extra world: exploring it generates new chunks, and chunk
generation is the most memory-hungry thing a server does. On a small shared
heap with many plugins, flying fast through fresh ocean can run the server out
of memory (`OutOfMemoryError` → watchdog stall → crash). If that happens:
travel by boat instead of creative-flying at speed, lower `view-distance` in
`server.properties`, and explore gradually so chunks generate at a survivable
pace. The plugin itself is light; it's the chunk generation that costs.

---

## Manual test script (per phase)

Things that can't be unit-tested (FAWE pasting, Mythic spawning, boat feel):

**Phase 1 — the sea.** Fresh server → `dark_sea` exists, pure ocean.
Sail/fly out past 500, 1500, 3000, 5000 blocks: action-bar notice at each
crossing, escalating effects without armor. Return to center: effects lapse
within ~2s. `/ds status` matches your position.

**Phase 2 — armor.** `/ds give armor 2`, wear the full set → Zone 2 harmless,
Zone 3 gives Zone 1's effects. Drop one boot in Zone 2 → exposed within one
check interval. Rename a vanilla netherite set at an anvil → still tier 0.

**Phase 3 — islands, mobs, loot, reset.** `/ds generate` → progress lines,
`/ds island list` shows counts per ring, spacing ≥ `min-island-gap`.
`/ds island tp t1-1` → approach: mobs appear at marker points, stop at the
cap; leave for `abandon-cooldown-minutes` → they despawn. Open a chest →
loot; open again → unchanged until the cooldown. Grief an island, then
`/ds reset soft` → healed, chests restocked. `/ds reset full` (warns) →
`confirm` → evacuated, world regenerates, `/ds island list` shows a new
layout at different coordinates.

**Phase 4 — boats.** Find/give a level-1 token, hold it, `/ds boat upgrade`.
Boat is measurably faster; at level 2 the shield lets you sit in the next
ring unharmed **while aboard** but not on foot. Level survives relog and
restart.
