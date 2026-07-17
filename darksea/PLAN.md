# DarkSea — v1 Implementation Plan

A Paper **1.21.4** plugin recreating the feel of Arcane Odyssey's *Dark Sea* in
Minecraft: a lone safe island at the center of an endless ocean that grows more
hostile the farther out you sail, dotted with scattered islands holding
MythicMobs encounters and distance-scaled loot, survivable only with the right
sea armor — and eventually, a better boat.

This document is the agreed scope for **v1**. Anything under
[Out of scope](#10-out-of-scope-for-v1) is deliberately deferred.

---

## 1. Goals

1. A dedicated **Dark Sea world**: one safe home island at (0, 0), open ocean
   everywhere else.
2. **Danger zones** in concentric rings — the farther from center, the harsher
   the effects applied to unprotected players.
3. **Sea armor** in tiers; wearing a high-enough tier negates (or downgrades)
   the zone effects.
4. **Scattered islands** pasted from pre-built schematics, tiered by ring, with
   **MythicMobs spawns** and **loot chests** that scale with distance.
5. **Boat upgrades (simple)**: a per-player boat level granting speed and
   reduced sea exposure while aboard.

Design principle: every phase is independently playable and testable. The sea
is fun before islands exist; islands are fun before boats exist.

---

## 2. Target platform & dependencies

| Component | Version | Role |
| --- | --- | --- |
| Paper API | `1.21.4-R0.1-SNAPSHOT` | server platform (Java 21, same as sibling projects) |
| FastAsyncWorldEdit | **2.15.3** (`api-version: 1.21`, provides WorldEdit) | schematic load + paste for islands — **hard dependency** |
| MythicMobs | **5.12.1** (`io.lumine.mythic.bukkit.MythicBukkit`) | island mobs — **hard dependency** |

Both dependency jars have been verified against these exact versions (the jars
the server actually runs).

`plugin.yml`: `depend: [MythicMobs]`, `depend: [FastAsyncWorldEdit]` (FAWE
`provides: [WorldEdit]`, so depending on `WorldEdit` also works and tolerates
a swap to vanilla WorldEdit later — we will depend on `WorldEdit`).

### Maven coordinates (scope `provided`)

```xml
<!-- WorldEdit API (FAWE implements it) — EngineHub repo -->
<dependency>
  <groupId>com.sk89q.worldedit</groupId>
  <artifactId>worldedit-bukkit</artifactId>
  <version>7.3.10</version>
  <scope>provided</scope>
</dependency>

<!-- MythicMobs API — Lumine repo (https://mvn.lumine.io/repository/maven-public/) -->
<dependency>
  <groupId>io.lumine</groupId>
  <artifactId>Mythic-Dist</artifactId>
  <version>5.12.1</version>
  <scope>provided</scope>
</dependency>
```

Fallback if a repo is unreachable at build time: install the uploaded server
jars into the local Maven repository (`mvn install:install-file`) — they
contain the same API classes we compile against. No third-party code is shaded
into our jar either way.

Unlike CustomAchievements' reflection-based MythicMobs bridge, DarkSea compiles
**directly against the MythicMobs 5.x API** (it is a hard dependency here, so
reflection buys nothing).

### Repository layout

New sibling project, mirroring `anticheat/`:

```
darksea/
├── PLAN.md            # this document
├── README.md          # user-facing docs (written as features land)
├── pom.xml
└── src/main/...       # com.diamend.darksea.*
```

---

## 3. Architecture

```
com.diamend.darksea
├── DarkSeaPlugin            # lifecycle, wiring, config load
├── world/
│   ├── OceanChunkGenerator  # water-to-sea-level generator + noise seabed
│   └── WorldService         # creates/loads the dark_sea world, pastes spawn island
├── zone/
│   ├── Zone                 # ring model: id, minRadius, effects, requiredTier
│   ├── ZoneManager          # distance → zone lookup (radius² compare, no sqrt)
│   └── ExposureTask         # repeating task applying effects to exposed players
├── armor/
│   ├── SeaArmor             # item factory + PDC tier tag read/write
│   └── ProtectionService    # computes a player's effective protection tier
├── island/
│   ├── IslandTemplate       # schematic + tier + markers metadata
│   ├── IslandInstance       # a placed island: origin, tier, region, state
│   ├── IslandPlacer         # ring-constrained random placement + FAWE paste
│   └── IslandRegistry       # persistence (islands.yml), lookup by location
├── mob/
│   └── MobSpawner           # proximity-triggered MythicMobs spawns, caps, cleanup
├── loot/
│   ├── LootTableConfig      # tiered weighted tables (loot.yml)
│   └── ChestRefillService   # fills registered chests on cooldown
├── boat/
│   └── BoatService          # per-player boat level, speed scaling, exposure shield
├── data/
│   └── PlayerDataStore      # playerdata/<uuid>.yml (boat level, stats)
└── command/
    └── DarkSeaCommand       # /darksea (+ tab completion)
```

---

## 4. Feature specifications

### 4.1 World & home island (Phase 1)

- On first enable the plugin creates world **`dark_sea`** with
  `OceanChunkGenerator`: still water from a noise-varied seabed (~Y 40–50) up
  to sea level (Y 62), gravel/sand floor, no vanilla structures, no land.
  Vanilla ambient water mobs stay enabled; hostile natural spawns are left to
  the server's normal rules (v1 does not manage them).
- The **home island** is a schematic (`schematics/spawn/…`) pasted once at
  (0, 0) on first world creation; world spawn is set on top of it. A config
  flag records that the paste happened so restarts never re-paste.
- The plugin never touches other worlds. Everything below applies to
  `dark_sea` only.

### 4.2 Danger zones & exposure (Phase 1)

Config-driven rings measured from configurable center (default 0, 0):

```yaml
# config.yml (excerpt)
world: dark_sea
center: { x: 0, z: 0 }
exposure:
  check-interval-ticks: 20      # one pass per second
  effect-duration-ticks: 60     # re-applied each pass; expires 2s after leaving
  grace-on-login-seconds: 10
zones:
  - id: safe
    max-radius: 500
    required-tier: 0
    effects: []
  - id: zone1
    max-radius: 1500
    required-tier: 1
    effects: [{ type: POISON, amplifier: 0 }]
  - id: zone2
    max-radius: 3000
    required-tier: 2
    effects: [{ type: POISON, amplifier: 1 }, { type: WEAKNESS, amplifier: 0 }]
  - id: zone3
    max-radius: 5000
    required-tier: 3
    effects: [{ type: WITHER, amplifier: 0 }, { type: DARKNESS, amplifier: 0 }]
  - id: zone4
    max-radius: -1               # unbounded outermost ring
    required-tier: 4
    effects: [{ type: WITHER, amplifier: 1 }, { type: SLOWNESS, amplifier: 1 }]
```

**Exposure rule** — the heart of the gameplay loop:

```
exposure = zone.requiredTier − (protectionTier + boatShield)
```

- `exposure ≤ 0` → fully protected, no effects.
- `exposure ≥ 1` → the player receives the effects of the zone whose
  `required-tier` equals their exposure (partial protection *downgrades*
  danger rather than binary pass/fail — a Tier 2 set in Zone 3 feels like
  Zone 1). Creative/spectator players and a bypass permission are exempt.
- The task runs once per `check-interval-ticks`, iterates only players in
  `dark_sea`, compares squared distances, and applies effects with
  `effect-duration-ticks` so effects naturally lapse after leaving — no
  effect-removal bookkeeping.
- Feedback: an action-bar notice on zone crossings (`Entering the Wild Sea —
  your armor holds` / `…you are not protected here`) and a subtle ambient
  sound cue. No chat spam.

### 4.3 Sea armor (Phase 2)

- Custom items built on existing armor materials with a
  `PersistentDataContainer` key `darksea:tier` (int) — survives anvils,
  can't be faked, needs no resource pack:
  - **Tier 1 – Tidewalker** (chainmail base), **Tier 2 – Stormplate** (iron),
    **Tier 3 – Abyssal** (diamond), **Tier 4 – Leviathan** (netherite);
    names/lore via MiniMessage, unbreakable flag configurable.
- **Effective protection tier = the lowest `darksea:tier` among the four worn
  slots** (a missing or untagged piece counts as 0). Full commitment to a set
  is required — mixing one Leviathan boot with leather does nothing.
- Recomputed cheaply: cached per player, invalidated on inventory/equipment
  change events, re-read lazily by the exposure task.
- v1 acquisition: loot drops on islands (§4.5) + admin command
  `/darksea give armor <tier> [player]`. Crafting recipes are config-defined
  and ship disabled by default (server owner's choice).

### 4.4 Islands (Phase 3)

**Approach: pre-built schematics, plugin-scattered** (decided earlier —
reliable, good-looking, and marker-friendly).

- Templates live in `plugins/DarkSea/schematics/tier<N>/*.schem`, loaded via
  the WorldEdit API (`ClipboardFormats.findByFile`). Each template may have a
  YAML sidecar (`<name>.yml`) overriding defaults (weight, mob set, chest
  cooldown).
- **Markers inside schematics** (scanned after paste, then replaced):
  - `LODESTONE` → loot chest location (replaced by a chest facing outward,
    registered with the refill service).
  - `GOLD_BLOCK` → MythicMobs spawn point (replaced by the block beneath's
    material continuation, i.e. removed).
  - Marker materials are configurable in case a build legitimately needs them.
- **Placement** (`/darksea generate`, admin, run once per world — also
  re-runnable to fill *new* rings only):
  - For each ring: pick `island-count` (config per ring) positions with
    rejection sampling — uniform in the annulus, minimum spacing
    `min-island-gap` (default 400) from every existing island, minimum 200
    from ring borders.
  - Chunks are generated on demand by FAWE during paste; pasting is
    FAWE-async, queued island-by-island with progress logged to the console
    and to the invoking admin.
  - Each placement is recorded in `islands.yml` (template, tier, origin,
    bounding box, chest coords, spawn-point coords). Registry is the single
    source of truth; regeneration never double-pastes.
- v1 places islands **up front**, not lazily during exploration — simpler,
  and the registry makes distances/spacing verifiable. (Lazy/endless
  placement is a v2 candidate.)

### 4.5 MythicMobs & loot (Phase 3)

- **Mob spawning** — proximity-based, not vanilla spawners:
  - A slow task (every 5s) finds islands with a player within
    `activation-radius` (default 64). For each such island below its
    per-island cap, spawn its tier's configured mobs at the marker points via
    `MythicBukkit.inst().getMobManager().spawnMob(type, location, level)`.
  - Spawned entity UUIDs are tracked per island; dead or removed entities free
    cap slots. When no player has been near for `cooldown-minutes`, remaining
    tracked mobs are despawned.
  - Mob sets per tier in `mobs.yml`: list of `{ mythic-type, weight, level }`.
    Mythic mob **levels scale with tier** so one mob family can serve
    multiple rings.
- **Loot** — `loot.yml` defines one weighted table per tier:
  - Entries: vanilla items (material, amount range, weight), **sea armor
    pieces** (tier-appropriate: zone N chests are the source of tier N armor,
    with a small chance of tier N+1 — the progression engine), and **boat
    upgrade tokens** (§4.6).
  - Registered chests refill `rolls` random entries when opened if
    `refill-cooldown-minutes` (per tier, longer inward) has elapsed since the
    last refill; timestamps persist in `islands.yml`.

### 4.6 Boat upgrades — simple stats (Phase 4)

- Per-player **boat level 0–3**, stored in `playerdata/<uuid>.yml`:

| Level | Name | Speed multiplier | Boat shield |
| --- | --- | --- | --- |
| 0 | Rowboat | 1.00 | 0 |
| 1 | Sloop | 1.15 | 0 |
| 2 | Cutter | 1.30 | 1 |
| 3 | Stormrunner | 1.45 | 1 |

- **Speed**: boats are not living entities (no movement-speed attribute), so
  speed is applied by scaling the boat's horizontal velocity in a
  per-tick-cheap handler (listener on vehicle movement; only boats in
  `dark_sea` whose rider has level > 0, only the horizontal component, capped
  to keep vanilla collision sane). This is the standard, well-trodden
  approach; exact multipliers get tuned in playtesting.
- **Boat shield**: while riding a boat, the rider's `boatShield` (table above)
  is added into the exposure formula (§4.2) — an upgraded boat lets you *scout*
  one ring farther than your armor alone, but you still can't fight there
  safely. Values configurable.
- **Upgrading**: consume an **Upgrade Token** item (PDC-tagged, found in
  tiered loot) via `/darksea boat upgrade` — tokens are per-level
  (`darksea:boat_token` = level it unlocks) and only apply in sequence.
  No GUI in v1; no economy dependency (Vault was explicitly not assumed).

---

## 5. Commands & permissions

Base `/darksea` (alias `/ds`).

| Command | Description | Permission |
| --- | --- | --- |
| `/ds status` | Your zone, protection tier, boat level, exposure verdict | `darksea.use` (default: all) |
| `/ds boat upgrade` | Consume a token to raise boat level | `darksea.use` |
| `/ds tp` | Teleport to the home island | `darksea.tp` (default: op) |
| `/ds generate` | Place islands for all unfilled rings | `darksea.admin` |
| `/ds island list [tier]` / `/ds island tp <id>` | Inspect placed islands | `darksea.admin` |
| `/ds give armor <tier> [player]` / `/ds give token <level> [player]` | Grant items | `darksea.admin` |
| `/ds boat set <player> <level>` | Set a player's boat level | `darksea.admin` |
| `/ds reload` | Reload all configs | `darksea.admin` |
| — | Immunity to sea exposure | `darksea.bypass` (default: op) |

---

## 6. Data & files

```
plugins/DarkSea/
├── config.yml        # world, center, zones, exposure, boat stats, markers
├── mobs.yml          # per-tier MythicMobs sets
├── loot.yml          # per-tier weighted loot tables
├── islands.yml       # placed-island registry (plugin-managed)
├── schematics/
│   ├── spawn/        # home island
│   └── tier1..tier4/ # island template pools
└── playerdata/
    └── <uuid>.yml    # boat level (+ future per-player state)
```

All user-facing text via MiniMessage in `config.yml` `messages:` section, same
convention as CustomAchievements.

---

## 7. Performance budget

- **Exposure task**: one squared-distance compare + cached-tier lookup per
  player per second. Negligible at any realistic player count.
- **Armor cache**: recomputed only on equipment-change events, not per tick.
- **Boat velocity scaling**: fires only for boats in `dark_sea` with an
  upgraded rider; a multiply on an existing event, no scheduling.
- **Mob spawner**: 5-second cadence, registry lookup is a per-ring spatial
  bucket (no full scan), hard per-island and global mob caps.
- **Island generation**: FAWE async pastes, sequential queue — a one-time
  admin operation, expected minutes for a full world, safe on a live server.
- **No chunk loading by the plugin** outside `/ds generate`.

---

## 8. Testing

- **Unit (MockBukkit + JUnit 5**, same stack as CustomAchievements**)**:
  zone resolution by radius (boundaries, unbounded outer ring), exposure
  formula (all tier/shield combinations), armor tier computation (mixed sets,
  untagged pieces), placement sampling (annulus bounds + spacing), loot table
  weighting, config parsing (valid, missing, malformed).
- **Not unit-testable, verified on a real server**: FAWE pasting, MythicMobs
  spawning, boat velocity feel. A short manual test script goes in the README
  per phase (FAWE/Mythic API calls are isolated behind `IslandPlacer` /
  `MobSpawner` so everything around them stays mockable).

---

## 9. Milestones & acceptance criteria

**Phase 1 — The sea** (world + zones + exposure)
✔ Fresh server creates `dark_sea` with pasted home island; sailing out
without armor crosses zones with escalating effects and action-bar warnings;
`/ds status` and `/ds reload` work; effects lapse on return to safety.

**Phase 2 — Armor** (items + protection + downgrade model)
✔ `/ds give armor 2` + full set survives Zone 2 untouched, experiences
Zone 1 effects in Zone 3; removing one piece in danger re-exposes within one
check interval.

**Phase 3 — Islands, mobs, loot**
✔ `/ds generate` fills every ring per config with correct spacing;
approaching an island spawns its Mythic set (respecting caps, despawning on
abandonment); chests refill tier-appropriate loot including armor
progression drops.

**Phase 4 — Boats**
✔ Token drops in loot; `/ds boat upgrade` consumes it; upgraded boat is
measurably faster and its shield extends safe scouting range by one ring;
level persists across relogs and restarts.

Each phase = one PR-sized change set with its README section and tests.

---

## 10. Out of scope for v1

Explicitly deferred (good v2 candidates, kept out to ship a playable loop):

- True procedural island terrain (noise-generated landmasses)
- Full custom ships (display-entity vessels, HP, cargo, cannons)
- Lazy/endless island placement during exploration
- Weather/insanity layers (Dark Sea "clouds", screen effects beyond Darkness)
- Economy (Vault) pricing for upgrades; GUIs for boat/armor management
- Per-island respawnable bosses with unique drops
- Resource-pack custom models for armor/items
