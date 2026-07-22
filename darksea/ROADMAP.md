# DarkSea — Camp Roadmap (Jul 18 → ~Aug 1, 2026)

**The situation:** two weeks of phone-only contact — Minehut website works,
but no server console and no in-game testing. So this window is for work that
can be **proven by CI alone**: code with unit tests, content files, and docs.
Every push to the working branch compiles and tests in GitHub Actions, and a
fresh `DarkSea` jar hangs off every green run, ready for return day.

**The target on return:** Minehut Pro (**6 GB**), updated FAWE, and
(optional but recommended) MythicMobs — the stack this plugin was designed
for. The README now has a paste-ready **6 GB profile** for that day.

---

## Workstreams (biggest payoff first)

### 1. Demo island variety — code — IN PROGRESS
Today every demo island is the same sand pad. Add built-in shapes, picked
randomly per island, with the shape math in pure functions so JUnit can
verify sizes and marker placement without a server. *Effect: the sea stops
looking copy-pasted, even before real schematics exist.*

**Decisions (Jul 18, by Wyatt):** roster is rocky spire (z1–4), twin atoll
(z1–2), ruined watchtower (z2–4), sea-beast bones (z2–4), volcanic cone
(z3–4), abyssal monolith (z4). Full escalation: darker palettes AND bigger
islands farther out. Islands must be big enough to *explore* — every chest
is hidden inside something (grotto, tower floor, crater, vault, skull), never
visible from the water. The home island stays hand-built by Wyatt (the
generator never touches it), and any shape can be overridden later by real
schematics per tier.

**Decisions round 2 (Jul 18, by Wyatt):** every island at least 30x30, the
big ones pushing 50x50+; islands should take real time to cover. Tier sizes
must scale cleanly — a farther-ring island never rolls smaller (spire was
the offender; the viewer's auto-zoom also hid true sizes, now fixed with a
per-shape true-to-scale zoom + a size readout + a block key). Watchtower
keeps get taller: exactly 15/18/21 by tier. New seventh shape by request:
**corrupted forest** (z2–4) — decaying/infected woods with a hollow
heart-tree hiding the chest. Abyssal monolith gets cult remains — candle
ring, skull altar, toppled glyph steles, roofless dwellings, graves — "a
group of people used to live here but left or died."

**Decisions round 3 (Jul 18, by Wyatt):** viewer needed a zoom (added:
wheel/pinch/buttons + drag pan). Higher-tier islands get more loot:
**tier 3 hides two chests, tier 4 three** — every one still concealed, and
the tests enforce count, concealment, and a 6-block minimum spread.
Corrupted forest got bigger and deader (Arcane Odyssey energy: mycelium
blotches, wither roses, more snags, thinner canopies). "Block vomit" fixed:
material mixes are now position-hashed **patches** (3x4x3 cells) instead of
per-block static, and the volcano's magma veins run as sector rivulets. The
volcano got real lava: a nine-block crater well with a walkable rim,
molten seeps on the flanks (containment forced block-by-block), and a
molten seam in the chest chamber. The abyssal monolith got a true high
altar — stepped dais, gilded table, wither skull, corner pillars,
cauldron — plus a grave crypt and an under-altar reliquary for chests two
and three; and every other island now carries a trace of the cult (grotto
offering niche, mound stone circle, garrison crypt shrine, bone totem,
crater-rim fire shrine, forest shrine clearing).

**Status:** eight generators live in `island/shape/` (the ruined castle
joined Jul 20 — see workstream 3b). Every shape now ends with a shared
shoreline pass (`ShapeSketch.shore`, Wyatt's beach note Jul 20): a 3–5
column sand apron BFS-grown from the land edge, stepping waterline →
shallows, writing only empty cells so it can't bury rooms or bust radius
budgets (the castle's budget went 40→44 to make room). Tests enforce the
30x30 minimum footprint, tier-monotonic sizing, per-tier chest counts
(1/1/2/3), concealment for every chest, and chest spacing; 200-seed sweep
clean across all 18 tier combos. Preview artifact regenerated with
zoom/pan, tier/seed/key controls and all chest markers.

**WIRED (Jul 20, Wyatt green-lit the looks).** `IslandPlacer` now raises
these shapes in-world wherever a ring has no schematic pool (they replace
the old sand pads): `generate()` rolls a shape via `DemoShapes.pick`,
persists its id as the island's template, and pastes it through the same
async chunk-preload + paced-build path the demo pads used. The translate
step writes every declared cell with physics off — including the shape's
explicit AIR cells, which set to air and so drain the ocean back out of
the carved interiors (vaults, undercrofts, cave mouths); cells the shape
never mentions are left alone, so the surrounding sea stays sea. Chests
and mob points come back as origin-relative markers for the existing
finalize step, so vault election, mob-tier boost, wealth floor and refill
all light up from the shape traits automatically. Soft reset (`/ds reset
soft`) rebuilds each island byte-for-byte: only the shape id + tier are
stored, and the build seed is derived from the island's position
(`DemoShapes.seedFor`), a contract a JUnit test now pins. The hand-built
home island is never shaped (spawn stays a plain platform / your
schematic). *Known live-test item for return day: interiors that open
to the sea through a doorway can slowly re-flood once physics resumes —
same limitation schematic islands have; a soft reset heals it.*

**Boxpvp guard rails (Jul 20, Wyatt's server is boxpvp).** New `combat`
config + `SeaGuardListener`: generated islands are **protected
loot-content** — no breaking, building, bucket-griefing or blowing up an
island's blocks (explosions are trimmed, not cancelled, so a creeper still
downs a careless player), only the chests open; admins (`darksea.admin`)
bypass so the home island stays editable. Protection covers the whole
vertical column over an island plus a `combat.island-protect-buffer`
(default 5) ring past its edge, so nobody pillars up alongside to cheese
the mobs from range (Wyatt's reason for protecting islands in the first
place) or digs in from beneath. **PvP is on across the whole sea
except the home sanctuary** — within `combat.pvp-safe-radius` of center
(defaults to the Calm Waters ring, 500) no player can strike another or
grief a block. PvP itself still rides `server.properties` `pvp: true`;
the listener only carves out spawn. Shrink `pvp-safe-radius` to hug just
the spawn island if the 500 bubble feels wide.

### 2. MythicMobs content pack — content + small code — IN PROGRESS
A ready-to-copy `mythicmobs-pack/` with mob YAMLs themed per zone, plus a
`fallback:` field per entry in `mobs.yml` so the config can name Mythic mobs
and still degrade gracefully to vanilla mobs when MythicMobs isn't installed.

**Decisions (Jul 18, by Wyatt):** the story is the **Naxome** — a kinder
civilization that lived on these islands — versus the **Order of the Soul**,
the monolith cult reseeding the **Mariphage** (an ancient curse that plagues
the sea like a virus) to revive a primordial power; infection deepens with
distance because the Order's influence is strongest out there. Two families,
two mobs per ring, all named by Wyatt: Crazed Sailor / Vironic Initiate
(ring 1), Mutated Naxian / Vironic Acolyte (2), Transmuted Naxian / Vironic
Templar (3), Naxian Abomination / Vironic Lord (4). Only the Abyssal Reaches
gets a boss. New spawn rule by request: a mob's tier is its **minimum** ring
— lower-tier mobs also appear farther out, made rarer per ring
(`lower-tier-decay`).

**Status:** pack shipped (`mythicmobs-pack/Mobs/`, one file per family) and
`mobs.yml` re-rostered with `fallback:` per entry; MythicMobs demoted to a
true softdepend (vanilla-only servers now work); minimum-tier pools live in
pure `MobPool` with JUnit coverage, plus a CI test that locks mobs.yml and
the pack together (typo-proof). Canon written down in `LORE.md`.

**Decisions round 2 (Jul 18, by Wyatt):** "Vironic" stays. The boss is the
**Mariphage Core** — a core of the virus, the spreader rather than the
cause (so it stays refightable: anything that could infect a whole ocean
alone would be too big to fight). Big, imposing, otherworldly → warden,
summoning **Mariphage Vessels** (endermites; name provisional). Shipped in
`Mobs/darksea-boss.yml`, wired into tier 4 at weight 1 (~6% of native
picks). Boss loot stays modest until Loot 2.0. Open: Vessel rename if
Wyatt wants one; one-boss-per-island logic if the rare double-Core ever
annoys in play.

**Decisions round 3 (Jul 18, by Wyatt):** stats must be round numbers —
LevelModifiers dropped, every stat is now flat and readable (400, not 390).
Templar and Lord needed to feel like more than fat vanilla mobs: the
Templar now spills Vessels when struck and hurls attackers away; the Lord
pulses Wither on everyone within 6 blocks. The Core got a real fight
rhythm: an inhale that drags players in every ~12s, a Poison II plague
burst punishing melee, Darkness blows, Vessel sheds, and a lurch (Speed II)
when wounded — sprint out, trade hits, back off, repeat.

### 3. Loot 2.0 — content + code — IN PROGRESS
Named and lored themed items per tier, junk/mid/treasure weighting, tuned
boat-token rarity, small next-tier teases. CI gets a parse test so a typo
can never brick loot loading.

**Status (round 1):** `LootEntry` item lines now take optional MiniMessage
`name` + `lore`; `loot.yml` rewritten as junk/goods/treasure bands with
one-plus named **Naxome relic** per ring. Token weights tuned so each ring
favors its own boat level. `LootShippedConfigTest` locks the file in CI.

**Decisions round 2 (Jul 19, by Wyatt) — the deep pass:**
- **Chronons** — the Naxome's currency, decently common in every ring,
  richer on some islands than others; spent with the refugees at the main
  island (they fled in time; the calm center is theirs now).
- **Vault chests** — yes: one chest per multi-chest island is elevated.
- **Cultist sets** — each Vironic rank wears its own armor set, protecting
  against the Mariphage at the rank's first ring (Initiate 1 → Lord 4),
  slots named hood / bodice / robes / boots, with a set bonus: +2 damage
  in the Dark Sea per set tier.
- **Mob weapons** — crazed sailors drop a cutlass; mutated Naxians a fast
  mid-damage claw; transmuted Naxians an enhanced claw (more damage);
  abominations a slow heavy bone with DPS ≈ the enhanced claw. Exact
  numbers delegated (post-End: useful, never overpowered).
- **Chest gear** — normal Naxian weapons in chests, on average worse than
  enemy drops but with some genuinely good finds; **no enchantments** on
  any custom gear. Utility loot too: temporary boat-speed drink, other
  single-use items, long potions; keep some of it deliberately mediocre.
- **Relics** — useless (dormant) as found; the refugees revive them for
  permanent inventory-carried boosts, max 2–3 active (damage, resistance,
  speed, boat speed, regen, crit were the candidate pool).
- **Mariphage Core** — drops a relic that spreads Mariphage effects onto
  your targets, and the **Mariphage Stinger**: 2 hearts of true damage
  through defense, a bit slower than a sword. Explicitly NOT guaranteed.

**Status (round 2, built):** all of the above is in.
- `DarkSeaItems` registry (PDC ids, attribute-modifier stats, zero
  enchants, unbreakable): Sailor's Cutlass 6.0@2.0, Naxian Claw 4.5@2.8,
  Enhanced Claw 6.0@2.4, Abomination Bone 13.0@1.1 (DPS-matched to the
  claw), Mariphage Stinger 4.0 TRUE @1.4, plus chest finds Boarding Axe
  8.0@1.15, Harborguard Pike 8.0@1.2, Ceremonial Blade 6.5@1.8 — balance
  band (post-End: above bare netherite at the top, everything below
  Sharpness-V netherite) locked by `WeaponBalanceTest`.
- **Chronons** (prismarine crystals, PDC-tagged) in every ring, amounts
  scaling 3–6 → 10–16 by tier; per-island wealth multiplier 0.6x–1.8x
  derived from the island's position (some islands were rich once).
- **Vault tables** per tier in loot.yml; multi-chest islands elect one
  deterministic vault chest (survives resets, no storage) that rolls more
  and better; single-chest islands never do.
- **Vironic sets** (leather, dyed deeper purple by rank, Initiate/Acolyte/
  Templar/Lord = tiers 1–4 locked to mobs.yml by test): protect exactly
  like sea armor of their tier through the same PDC path, full set adds
  2x tier damage in the Dark Sea. Cloth armor points are the trade.
  Slot names shipped as Hood / **Vestments** / Robes / Boots (bodice →
  vestments; one-string revert if Wyatt prefers the original).
- **Mob drops** plugin-side (`drops:` in mobs.yml): spawner PDC-stamps
  every spawn, works with and without MythicMobs. Cultists drop their
  rank's set at 18%, cursed drop their weapons at 8–10%, everyone carries
  a few Chronons. Core: Stinger 30%, Vector 25% (chase drops, not
  guaranteed — as decided), 24–48 Chronons always, spare Sealed Sample 35%.
- **Relic revival**: relics drop dormant; `/ds relic revive` at the calm
  center (the refugees' ring) pays Chronons — 50/100/150/200 by tier,
  Vector 250 — and wakes the held relic. Awake relics work from the
  inventory, capped by `relics.max-active` (**2** shipped; says 2–3 in
  the decision — bump the config if 3 feels better). Mapping: Trade Coin
  +10% speed, Harbor Bell +15% boat speed, Sealed Sample +1 damage,
  Monolith Splinter +3 armor, Heart of the Naxome slow regen, Vector
  infects your melee targets (Poison II + Slowness). Crit was left out of
  v1 (no clean vanilla hook) — candidate for a seventh relic later.
- **Consumables**: Tidal Draught (+25% boat speed, 90s), Naxian Sea-Salve
  (regen + absorption), Deepsight Tonic (8 min night vision), Gillwater
  Philter (8 min water breathing).
- CI: `DarkSeaItemsTest`, `WeaponBalanceTest`, `VironicArmorTest`,
  `MobDropsTest`, `RelicTest`, `LootMathTest`, expanded
  `LootShippedConfigTest` (base+vault parse clean, Chronons everywhere
  and growing, relics real, mob-only weapons never in chests, nothing
  enchanted, tokens/cooldown/armor-tease invariants kept).

**Verdicts (Jul 20, by Wyatt):** **Vestments stays** (better than
bodice); **max-active stays 2** unless live testing makes relics feel
underpowered (then bump the config to 3); drop rates approved as shipped.
The refugees' trader (Chronon goods shop) remains the natural next loot
workstream once the main island build exists to host it.

### 3b. Ruined Castle island — BUILT (Wyatt's idea Jul 19, green-lit Jul 20)
A rarer island class, bigger than everything else (~75x75), with much
better loot but more and higher-tier enemies. All three needs from the
sketch are now in, CI-locked, pure code:

- **The eighth shape** (`island/shape/RuinedCastle`, rings 2–4): a
  drowned Naxian fortress spanning **67/72/79** blocks by tier — square
  curtain wall (two thick, rotting column by column, two breaches), four
  corner towers each ruined its own amount, a south gatehouse, a
  roofless keep with a throne dais over a **sunken vault**, a great hall
  over an **undercroft**, an Order **chapel** with a buried reliquary,
  plus a **crypt** (t3+) and a **well cistern** (t4). Its own masonry
  palette per tier (stone brick → deepslate brick → polished blackstone
  brick, patch-weathered). **4/5/6 chests** by tier, every one in a
  buried or walled room; a 600-seed sweep holds footing, concealment and
  spacing.
- **Rarity weighting**: `DemoShapes.pick` is weighted now; standard
  shapes sit at 10, the castle at 3 → **~5%** of rolls, so roughly one
  or two castles per sea (never in ring 1). Locked by a distribution
  test.
- **Shape-driven island traits** (`DemoShape` defaults + shape-aware
  `IslandInstance`): the castle elects **two vault chests**
  (`LootMath.vaultChestIndices`, deterministic, always leaves a plain
  chest), floors its Chronon wealth at **1.4x** (a castle never drowned
  poor), garrisons **one ring deeper** (`mobTier` — ring-2 castle
  fights ring 3's roster; ring 4 falls back to its own pool), and holds
  **+4 mobs** over the per-island cap with seven spawn points. Spawner
  and chest refill both consume the traits already, so all of it goes
  live the moment shape wiring lands in the placer.

Preview artifact regenerated ("fourth sounding") with the castle tab —
grid widened to ±44 and the block encoding to 5 base64 chars for the
bigger footprint. Wyatt's Jul 20 phone notes, both fixed same day:
beaches (see workstream 1's shoreline pass) and a barren bailey — the
courtyard now carries worn causeways from the gate to keep, hall and
chapel, the Order's garrison camp (soul-fire, bedrolls, stores), a dead
orchard on coarse earth, a ruined market row, the harbor lord's toppled
statue, a cold smithy corner, and hash-scattered ground litter. Tests: `DemoShapeTest` (per-shape budgets + castle
size/rarity/traits), `LootMathTest` (multi-vault election),
`CastleIslandTest` (shape-aware island behavior). Wyatt green-lit the
looks Jul 20 and all eight shapes are now wired into the placer (see
workstream 1) — the castle goes live like the rest on the next reset.

### 3c. Mariphage nest island — BUILT (Wyatt's ask Jul 21)
Wyatt: "make a new island where the Mariphage Cores can be found." His
calls (Jul 21): **nest-only** (the Core is pulled from native roaming and
found only at nests), spanning **tier 4 rarely and tier 5 commonly**, a
**rareish landmark with a guaranteed Core**. Built, pure code, CI-locked:

- **The ninth shape** (`island/shape/MariphageNest`, tiers 4–5): a low
  prismarine reef gone to sculk — a drowned shelf and set-back deck that
  wades into the sea, an **open sculk-and-sea-light socket** at the heart
  the Core rises from (ringed by shriekers, left clear to the sky for the
  warden's headroom), coral-and-sculk rim spires, turtle-egg clutches, and
  **three buried egg-chambers** (deepslate casing + stepped shaft, mirroring
  the monolith's crypts) for its three chests. Reef widens by tier so a t5
  nest always out-sizes a t4 one.
- **Resident-boss plumbing** (`DemoShape.bossMob`/`bossFallback`,
  `IslandInstance`, `MobSpawner`): a shape can name a mob the spawner keeps
  **exactly one of** standing whenever a player is near — re-raised past the
  ordinary mob cap the moment the reef is empty of one (the Order grows
  another). Fallback **WARDEN** so nests work without MythicMobs. The Core
  is removed from the tier-4 native pool; its signature drops still fire
  wherever it dies.
- **Per-tier rarity** (`DemoShape.rarityWeight(tier)`, weighted pick): the
  nest weighs **2** against six shapes at 10 in the Reaches (~3%, a rare
  stray) but is the **plurality** of the Sunless Trench at weight **4** against
  the two intruders that also reach tier 5 — the ruined castle and volcanic
  cone at weight **2** each — so ~50% of the Trench's sparse islands
  (`islands-per-ring[5] = 2`, ~14.5k–24.5k out) are Core nests, the rest a
  lucky drowned fortress or dead volcano (each a rare non-nest Core path). The
  Devouring Rim (tier 6) stays island-free.

Boxpvp note: a Core nest on about half the Trench's islands is a strong
endgame draw and a strong deterrent — 400 HP, pulls, plague bursts, Darkness —
kept sparse (2 islands, ~1 nest) and gated behind a ~15-min haul into tier-5
waters. Nest weight, island count and Core drops are the tuning knobs. Tests: `DemoShapeTest` (nest
rarity/boss/tier + the shared structural sweep at t4 and t5), `MobConfigTest`
(Core no longer native), `ConfigParsingTest` (tier-5 generation).

### 3c-ii. The Trench fills out — BUILT (Wyatt's ask Jul 22)
Wyatt: "add significantly more mobs to the normal t5 islands with a small
chance to add a core to them too … the outer lines of the Naxome that fell to
the Order first." The **ruined castle** and **volcanic cone** now reach tier 5
(rarity **2** each against the nest's 10 — see 3c-i), and out in the Trench:
- **Swollen garrisons** (`DemoShape.mobCapBonus(int tier)`, tier-aware): a t5
  castle holds **20** concurrent (`per-island-cap 6` + **14**, up from 10) with
  **13** garrison spawn points across its bailey (up from 7); a t5 volcano
  holds **18** (6 + **12**, up from 6) with **7** points ringing the black
  beach (up from 3). Below the Trench both keep their ordinary watch.
- **A rare resident Core** (`DemoShape.residentBossChance(int tier)`, rolled
  per-island in `IslandInstance.bossMob()` from the position seed so a soft
  reset re-decides identically): **~12%** of t5 castles/volcanoes keep a
  Mariphage Core standing (WARDEN fallback), **never** below tier 5. The nest
  stays the only island *guaranteed* one.
- The nest itself grew to **4 chests / 2 vaults** (a 4th buried egg-chamber);
  the castle to **7 chests** at t5 (a second "deep ossuary" crypt).

Boxpvp lever: a Core-bearing castle is a jackpot **and** a wall — 20 plague
mobs plus a warden guarding 7 chests (2 vaults). The knobs are the `0.12`
Core chance, the `14`/`12` cap bonuses, and the `2`/`2` Trench rarity weights.
Tests: `DemoShapeTest` (boss-guarantee invariant), `CastleIslandTest`
(tier-aware cap + the per-island Core roll's rare-but-real band + determinism).

### 3c-iii. The Soulwake Compass — the nest's hunting charm — BUILT (Wyatt's ask Jul 22)
Wyatt: "nest chests should also contain a special item … a consumable that
points you towards the closest player, allowing for easier hunting." Built as a
one-shot direction-finder, not a live radar:
- **The item** (`DarkSeaItems.SOULWAKE_COMPASS`, a `RECOVERY_COMPASS`):
  right-click and it reads the **nearest living soul's** heading — distance,
  8-point bearing, and an above/below/level hint — then is **spent**. A private
  soul-flame streak points the way and a sculk shriek fires, both to the hunter
  alone. `SoulwakeService` is the shell; the pick/bearing/range math is pure in
  `HunterSense` (unit-tested off-server).
- **What counts as quarry**: another player in the Dark Sea, alive, not a
  spectator/creative, **outside the home sanctuary** (a safe camper's heading
  never leaks), within `SOULWAKE_RANGE` (**30,000** blocks — effectively
  sea-wide, the outer rings sit ~25k out; Wyatt's Jul 22 bump). No lag cost:
  the use only loops the sea's online players, and range is a distance
  threshold in that loop, not a chunk-loading search radius. An empty sea keeps
  the charge — nothing to point at, nothing spent.
- **Where it drops** (shape-seam, nest-exclusive): a new Bukkit-free
  `DemoShape.chestBonusItem()` / `chestBonusChance(boolean vault)` that only the
  nest overrides; `ChestRefillService` seeds it on top of the rolled table.
  Odds per nest chest on each refill: a flat **30%** (Wyatt's Jul 22 call, up
  from the first 0.5-vault/0.15-plain split) → ~1.2 compasses per nest per
  refill cycle across its four caches. Tier tables stay clean; no other shape
  (castle/volcano vaults included) can drop it.

Boxpvp lever: an action-forcing "UAV" that collapses the search on an 8000-radius
sea, but fair — snapshot only (the quarry can juke after the ping), consumable,
nest-sourced, and blind to sanctuary campers. **No team system yet**, so it
finds the literally nearest other player (an ally included). Knobs: `SOULWAKE_RANGE`
(30,000), the flat `0.30` seed odds on `MariphageNest`, the sanctuary skip.
Tests: `HunterSenseTest` (bearing/nearest/range math), `DemoShapeTest`
(only the nest seeds a bonus), `CastleIslandTest` (nest-vs-plain delegation),
`DarkSeaItemsTest` (the seeded id is a real registry item).

### 3d. The Sunless Trench as a deadlier t4 — BUILT (Wyatt's ask Jul 21)
Wyatt: "make t5 a more sparse and deadly version of t4." The Trench was
already sparse (3 nests) but had **no roaming foes at all** (the roster
named enemies only through tier 4) and its water was barely a step past the
Reaches. Two contained changes close that:

- **Roaming roster** (`MobPool.MAX_TIER` 4→5): the pool builder now carries
  the Reaches' Abomination and Lord one ring further, thinned by
  `lower-tier-decay` (0.35), so the Trench roams the **same foes as tier 4
  but sparser** — a deadlier echo, not a new bestiary. `mobs.yml` documents
  where to add Trench-only foes if we ever want them.
- **Sunless water** (`config.yml` zone5): the Trench earns its name with
  **perpetual Darkness** on top of its deeper wither — still gear-reducible,
  so armor matters but tier-4 kit that walked the Reaches will struggle.
  Bypass-protection stays the rim's alone.

Honest limit: the roster's `level` field is future headroom (the shipped
pack is flat-statted and vanilla fallbacks ignore it), so a t5 Abomination
isn't numerically tougher than a t4 one *yet* — today's deadliness delta is
the Darkness + the guaranteed Core, and the roster fills what was an empty
ring. Tests: `MobPoolTest` (Trench inherits the Reaches' foes, thinned),
`ConfigParsingTest` (zone5 carries Darkness, still gear-reducible).

### 4. Boats — hardening + PvP — BUILT (Wyatt, Jul 20 PM)
Two halves, both done:
- **Hardening.** MockBukkit tests around token matching, consumption and
  persistence; speed clamp reviewed and pinned by pure tests.
- **Boat PvP.** Level-scaled hull toughness (Wyatt: "toughness scales with
  level"); a sunk boat dumps its rider into the hostile sea.

### 4b. Naval combat — BUILT (Wyatt, Jul 20 PM, designed together in depth)
Boat-vs-boat PvP, the full package. Wyatt's calls, all locked in chat:
- **Ramming** (closing-speed based, per-pair cooldown): defender's hull
  takes **75%** of the force, attacker's **25%** (Wyatt's split), each
  softened only by its own toughness; small **rider bleed** on both; ram
  **knockback** away from the charger. No rams in the sanctuary.
- **The chase problem** (Wyatt's worry: "max boat speed = never caught").
  Fixed two ways, both his pick:
  - **Wounded hull**: ANY hull damage — plain arrows included — slows the
    boat to 70% speed for 4s. Chip the runner, close, ram.
  - **Battered hull (Wyatt)**: on top of that momentary dip, every point of
    HP the hull is *missing* shaves 5% off the top speed until it's repaired
    or regenerates (`naval.hull.speed-penalty-per-hp`). A 10-HP hull chipped
    to 4 cruises at 70%; at 1 HP it crawls at 55%. The two stack as the harsher
    of the momentary slow and the persistent per-HP tax, floored at 15% so a
    beaten hull can always limp home to the dry-dock. Return-day knob if the
    chase feels too swingy.
  - **Ram Power (Wyatt)**: the offensive stat that was missing — the
    attacker's charge lands harder per boat level (linear off 1.0,
    `naval.ram.power-per-level` = 0.15, softened from 0.25 on Wyatt's call):
    Rowboat 1.00×, Sloop 1.15×, Cutter 1.30×, Stormrunner 1.45×, Tempest
    1.60×, Maelstrom 1.75×. Boosts only the
    hull damage *dealt* (the 25% bouncing back rides at 1.0), and only one hit,
    so a low-tier swarm still gangs a maxed boat.
  - **Six boat tiers (Wyatt: "add 2 more levels, up the HP not just
    toughness")**: levels 4–5 (**Tempest**, **Maelstrom**) join the ladder,
    and hull HP is now a per-tier stat (`boat.levels.N.hp`; 0 = fall back to
    the global `naval.hull.max-hp` = 10). Stormrunner 14, Tempest 18,
    Maelstrom 24 — the apex tiers out-soak the default both by toughness AND
    raw HP. **Toughness curve flattened (Wyatt: weaker ships shouldn't be
    hopeless)** to 1.0 / 1.25 / 1.5 / 1.8 / 2.1 / 2.4, because toughness × hp
    is effective HP — the old 1→5 curve on top of bigger hulls made a
    Maelstrom ~12× a Rowboat; now it's ~5.8×. Their tokens drop in the deep
    zones (3–4). NOTE for return day:
    the per-*absolute*-HP speed tax now bites big hulls hard — a 24-HP
    Maelstrom at half hull crawls at 40% (vs a Rowboat's 75% at half), and
    hits the 15% floor with 7 HP still left. Intended "big ship = ponderous
    when holed", but if it feels too punishing we can switch the tax to
    per-*fraction* missing so all tiers slow at the same rate.
  - **Custom stat points (Wyatt)**: a captain earns **1 point per boat level**
    (0 at Rowboat → 5 at Maelstrom) and spends them from a new **Outfit** page
    in the boat wheel, into any of four stats — **Speed** (+3%/pt), **Toughness**
    (+0.15/pt), **Hull HP** (+2/pt), **Ram Power** (+0.10x/pt). Shield is
    deliberately NOT allocatable (Wyatt wants it a rarer, tier-only feature).
    **Respec** costs Chronons scaled by points committed (`reset-cost-per-point`
    × spent). Allocation + respec are blocked while combat-tagged so nobody
    buffs a hull mid-ram. Points persist per-player (playerdata `stats.*`),
    survive restarts, and fold into the live combat math (speed cap, toughness
    divisor, max HP, ram power). All knobs in `boat.stat-points`. NOTE: Wyatt
    green-lit Speed as spendable knowing the chase-risk — kept the per-point
    speed small (3%) as a hedge; revisit if a speed-stacked glass build is a
    problem. Open thread: he's considering making **shield level-5-exclusive**
    (lower it on mid tiers so only the Maelstrom gets max Dark Sea protection).
  - **Ram surge**: sprint OR jump key at the tiller (both, because toggle
    sprint makes the sprint flag latch — jump behaves the same for
    everyone), burst to 1.8× the cap, 9s cooldown.
- **Naval arsenal** (Wyatt: "arrows as anti-boat weapons + harpoon gun";
  all three approved, reel-in pull chosen):
  - **Chainshot arrow** — tiny damage, 50% slow for 6s. Tier 1+ loot.
  - **Hullpiercer arrow** — heavy hull damage through half the toughness
    bonus. Tier 2+ loot.
  - **Harpoon gun** — crossbow, 24-block line, reels the hooked boat
    toward the shooter ~2s. **Surging while hooked snaps the line but
    spends the surge** — the counterplay loop. Tier 3 vault / tier 4 loot.
  - All naval ammo is island loot → tagged as run-loot → part of the haul.
- Naval weapons run on their own hull-HP model (10 HP) because Bukkit
  can't partially damage a boat entity; vanilla melee keeps vanilla
  wobble + the toughness divisor.
- **Hull combat tag + gradual regen** (Wyatt, Jul 21): any naval hit
  combat-tags the hull for **60s** of zero healing, then it claws back at
  **0.5 HP/s** (one pip every 2s) up to full — it never snaps to 10 the
  way the old 15s-quiet model did. Worst case (1 HP) = 78s to full.
  Config: `naval.hull.combat-tag-seconds`, `naval.hull.regen-per-second`
  (replaced `regen-seconds`). Curve is pure `NavalMath.regenHp`, CI-pinned.
  The HUD reads the same function, so a tagged hull visibly sits frozen
  then ticks up. Return-day tuning knobs; possible HUD combat-tag glyph.
- **All hits feed one hull-HP pool** (Wyatt, Jul 21): melee and plain
  arrows on a ridden boat now flow through the same 10-HP hull model as
  rams and naval ammo — every hit combat-tags the hull (freezing regen)
  and chips the pool. `NavalCombatService.onVehicleDamage` (HIGHEST) now
  cancels the vanilla boat-break so the HP pool is the *only* thing that
  sinks a ridden Dark Sea boat; it runs after `BoatService`'s HIGH
  toughness-divide, so it takes the already-softened damage whole. Naval
  ammo is skipped (PDC-tagged) so `NavalWeaponListener` isn't double-hit;
  the sanctuary and unridden/empty boats are left to vanilla (keeps
  owner-pickup working).
- **The boat wheel (GUI)** (Wyatt, Jul 21): sneak-right-click your own boat
  — or `/ds boat` — opens the plugin's first chest menu (`BoatMenu` /
  `BoatMenuService`), Wyatt's design. Four controls: **Upgrade** (now
  consumes the next-level token from anywhere in the pack, not just the
  main hand — `BoatService.upgrade` was generalized, so the command shares
  it), a **Stats** readout (class/level, live hull HP, toughness, shield,
  speed, combat state), **Repair**, and **Pick Up**.
  - *Ownership*: a boat is stamped (`darksea:boat_owner` PDC) with the first
    player to board it and never reassigned, so an enemy can ride it but
    can't claim it; the wheel + stow are owner-only. An unowned hull is
    claimed by the first captain to open its wheel.
  - *Pick Up* stows the boat as a new **Dark Sea Boat** item
    (`DarkSeaItems.DARK_SEA_BOAT`, dark-oak boat) — **blocked while the hull
    is combat-tagged** so nobody pockets their boat out from under a ram to
    deny the kill, and blocked if a non-owner is aboard. Level rides the
    player, so re-placing the item gives you your level back.
  - *Repair* patches the hull straight to full for
    `naval.repair.cost-per-hp` Chronons per missing HP (default 2 → 20 max),
    **but only inside the home sanctuary** (Wyatt's call) — the home island
    is now a proper dry-dock, and you must sail all the way back to use it.
    Price is pure `NavalMath.repairCost`, CI-pinned. Self-gates against
    abuse: home is already no-PvP, so you can't repair-tank at sea.
  - Return-day tuning: repair cost, whether stow should also require being
    out of the sanctuary/stationary.
- **Sink → salvage → rebuild** (Wyatt, Jul 21): a boat sunk in combat no
  longer just vanishes. `NavalCombatService.sink` still strands the sailor
  in the water (the whole point), but now floats a **Wrecked Dark Sea Boat**
  item (`DarkSeaItems.BROKEN_DARK_SEA_BOAT`) into their pack. It can't be
  placed — `BoatMenuService.onUseWreck` cancels the vanilla boat-placement —
  and right-clicking it anywhere but home just tells you to dock. At the
  home island a right-click rebuilds it into a working Dark Sea Boat for the
  full repair price (`NavalMath.repairCost(0, maxHp, cost-per-hp)` = 20
  Chronons at defaults). Net effect: sinking is now a **Chronon tax + strand**
  instead of a total boat loss — since level always lived on the player, the
  practical change is you keep your hull (broken) and pay to re-float it,
  which also creates real Chronon demand. Flagged for return-day: the rebuild
  cost, and the edge where a thief sunk in a stolen boat gets the wreck (the
  rider, not the owner, salvages it). Wyatt's broader **destruction** idea is
  still to come — this covered the recover-on-sink half.
- Boxpvp note (flagged to Wyatt): escape-always-wins would break the
  run-loot kill economy — the richest targets have the best boats. The
  wounded-hull rule is what keeps them killable.
- Live-test items for return day: surge feel (PlayerInputEvent key edges,
  toggle-sprint double-tap quirk), harpoon reel strength, ram frequency in
  real scrums.

### 4c. Naval HUD — BUILT (Wyatt, Jul 21, designed together)
Always-on action bar while riding a boat in the Dark Sea (Wyatt's call:
hull HP visible at all times, "even if it's quite small"):
- `⛵ Name ❘ ▮▮▮▮▮▮▯▯▯▯ 6 ❘ ⚠ hull wounded ❘ ⚡ 7s` — hull pips (raw
  10-pip pool, green/yellow/red by fraction), boat name, wounded flag,
  surge countdown / ready.
- **Harpooned override**: `⚓ HARPOONED — surge to cut!` replaces the name
  slot, but the hull pips and surge timer stay — HP is visible in EVERY
  state, per Wyatt.
- Raw pool (always /10), not toughness-scaled "effective HP": your own
  bar reads identically on every boat, so it becomes muscle memory.
- BMP-only glyphs (⛵ ⚓ ⚠ ⚡ ▮ ▯ ❘) — vanilla Unifont fallback renders
  them; true emoji would be boxes on unmodified clients.
- Transient flashes (surge fired / cooldown denied) hold the ticker off
  the bar for 1.5s so they're readable, then the HUD resumes.
- Pure renderer (`NavalHud`) fully pinned by tests; ticker
  (`NavalHudService`, every `naval.hud.period-ticks`, default 10) is
  Bukkit-facing and CI-compiled. `naval.hud.enabled` kill switch.
- Open design question for return day: enemy hull visibility — currently
  hidden (limping away at 1 HP stays a bluff). Candidate: hit-marker
  flash showing the target's pips only at the moment you land a naval
  hit.

### 5. Timed sea reset — BUILT (Wyatt, Jul 20 PM)
`SeaResetScheduler` resets the whole sea on a cycle so loot can't be
hoarded or camp-farmed forever. Config `reset.auto` (ships **enabled, 6h,
soft**): every interval a countdown broadcasts at each `warn-minutes` mark
(30/10/5/1), then everyone in the sea is washed home to the sanctuary
(so an island healing over them can't bury anyone) and the existing soft
reset heals + restocks in place. `mode: full` swaps in a whole new layout
each cycle — a heavier "season" wipe — reusing `WorldService.resetFull`
(which evacuates to the fallback world itself). Skips the churn when
nobody's online. Reads live settings each tick, so `/ds reload` re-times
or disables it. The countdown logic is a pure function pinned by
`SeaResetSchedulerTest`.
*Deferred (not needed to ship the cycle): dropping per-chest refill so
each chest is one loot per cycle — the reset already caps hoarding; make
it a toggle if farming still feels too easy in play.*
Camping the sanctuary border: Wyatt's call is **no action** — the safe
zone is big enough to slip in and out.

### 6. Run-scoped death loss — BUILT (Wyatt, Jul 20 PM)
The extraction loop. `RunLoot` stamps every item the sea hands out (chest
loot in `ChestRefillService`, mob drops in `MobDropService`) with a PDC
mark. `RunLootService` on `PlayerDeathEvent` in the sea forces keep-inv
off, then drops **only** the stamped items (at the death spot, for the
killer) and restores the untagged gear on respawn — dupe-safe by
construction (run loot goes through the normal drop list, kept gear is
pulled out of drops entirely, no item is ever in two places; a join
handler is the safety net if someone dies-then-quits). Reaching the
tier-0 sanctuary banks the haul: `ExposureTask`'s existing zone-crossing
hook clears the marks. Config `combat.run-loot-death` (default on); XP/
level kept. `RunLootTest` pins the stamp round-trip. Balance read (Wyatt
asked): sound and non-punishing; the one thing it doesn't pressure is a
fully-geared player's own gear, so top-gear players roam at low personal
risk — fine for boxpvp, revisit only if ganking gets oppressive.

### 6b. The outer rings — BUILT (Wyatt, this session)
Two new danger rings past the Abyssal Reaches, and a new zone concept.
- **Zone 5 — The Sunless Trench** (tier 5, bounded 14500–24500): survivable
  ONLY with the best kit. Tier-4 armor + a shield-2 boat (protection 6)
  fully negates it; tier-4 armor alone leaves you stung. No islands — it's
  the approach gauntlet. Pushed far out (Wyatt): the Trench begins ~14,500
  from center — ~15 min at a maxed Maelstrom's 16.2 b/s, and the whole run
  out is tier-4 danger.
- **Zone 6 — The Devouring Rim** (tier 6, unbounded, begins ~24,500 ≈ 25 min out): the lethal
  rim. New `bypass-protection: true` zone flag — armor and shield are
  ignored, so its full effects (Wither II + Darkness + Slowness II +
  Weakness) land on everyone no matter the gear. Wither ignores armor and
  can kill, so the ONLY way across is out-healing it with stacks of golden/
  enchanted apples, bleeding the whole way (Wyatt's exact spec: "nearly
  impossible to reach… possible with stacks of gaps/egaps but taking
  significant damage the whole time"). Whatever's out there is **bait** —
  reachable in theory, almost never in practice.
- Mechanic: `ExposureTask` forces `exposure = requiredTier` for a bypass
  ring (skipping the armor/shield subtraction), so the downgrade rule can
  never soften the rim. `ZoneManagerTest` + `ConfigParsingTest` pin it.
- **The bait — the Undrowned Heart (Wyatt's payoff).** A consumable relic
  (`undrowned_heart`, a Heart of the Sea) attuned once by right-click and
  consumed for good. Once attuned, a killing blow is refused like a Totem of
  Undying — the damage is cancelled, effects cleared, and the captain is left
  at `revive-health` with a totem's Regeneration/Absorption/Fire-Resistance —
  but only once per `relics.undrowned.cooldown-seconds` (150s = 2:30). The cooldown
  is stamped into player data (`undrowned.last-save`), so relogging can't
  reset it and chain saves. `UndrownedHeartService` holds the death-save and
  attune listeners; its lethality + cooldown math is pure and unit-tested
  (`UndrownedHeartServiceTest`). Not visible in-world — Wyatt seeds hints of
  its existence on the home island.
  - **Boxpvp note:** an auto-totem every 2 min is a strong open-water edge —
    the cooldown and revive-health are both config knobs so it can be dialed,
    and it's currently world-agnostic (fires anywhere, not just the sea).
- **Placement — a fixed landmark (Wyatt's call).** The Heart lives at one
  hand-built landmark past the Rim, placed by Wyatt with the hints on the
  home island pointing to it. No generation code needed: admins spawn a Heart
  with `/ds give item undrowned_heart` (tab-completed) and stock the landmark
  chest by hand. No islands generate in tiers 5–6, so the landmark is the
  only thing out there.

### 7. Schematic pipeline guide — docs — BUILT (Jul 22)
`SCHEMATICS.md` — a start-to-finish walkthrough (build → markers → set
origin → `//copy`/`//schem save` → drop into `schematics/tierN/` → optional
sidecar → `/ds reload` + `/ds reset full confirm` → verify → next ring) for
when FAWE is updated on return day. Written against what the plugin already
reads — no code changes. Calls out the return-day gotchas: builds must be
self-contained and foot-reachable (island blocks are protected, no mining
in), a tier folder with any `.schem` overrides the built-in shapes for that
whole ring (so migration is incremental — unbuilt rings keep the generated
shapes), the timed reset should be paused while testing builds, tier 5 is
nest-dominant (~half nests, rest rare castle/volcano) by design, and raw
schematics are plain islands (castle/nest
traits are shape-code seams, flag if wanted on a hand-build). Linked from the
README's "Building island schematics" section.

### 8. Balance pass — config (naval curve done Jul 22; foot PvP is Wyatt's)
Per-tier effect stacks, message polish, a sanity check of ring math at the
larger 8000-block radius, and — now that it's a boxpvp server — a
specifically-PvP look at the armor/boat tiers as the player-vs-player power
curve, not just the PvE survival gates.

**Naval curve — done (Jul 22).** After laying out the effective-HP ladder
(hp x toughness) for Wyatt to judge:
- **Smoothed the mid-tiers** so every upgrade feels earned instead of a cliff
  at tier 3: Sloop hp 0->11, Cutter hp 0->13 (they used to inherit the global
  10). Effective HP is now 10 / 13.75 / 19.5 / 25.2 / 37.8 / 57.6 — apex
  untouched, a Maelstrom still ~5.8x a Rowboat.
- **Buffed the hullpiercer** (the rare anti-tank arrow, and the counter that
  keeps the effective-HP ladder from being a wall): damage 6.0->9.0 and it now
  ignores three-quarters of the toughness bonus instead of half. New config
  knob `naval.hullpiercer.toughness-factor` (0.25) makes the pierce tunable.
  Sinks a Maelstrom in ~4 hits, was ~7.
- **Two live-feel knobs flagged, not changed:** harpoon velocity-carry (0.6)
  vs a fully speed-stacked runner, and hullpiercer drop accessibility.

**Foot PvP — Wyatt's.** He's tuning weapons/armor around the server's own
custom gear, so the DarkSea weapon damages and the Vironic set bonus stay as
shipped until he calls specific numbers. (Reference DPS vs vanilla netherite
sword 12.8: Enhanced Claw 14.4, Abomination Bone 14.3 burst, Stinger 5.6 but
TRUE/armor-piercing; Vironic is a full-set-only leather glass cannon, +2x tier
in-sea, one borrowed piece breaks it.)

### 8b. Chronon bounties — BUILT (Wyatt picked it Jul 22)
Spend Chronons to put a price on a captain's head; whoever kills them in the
Dark Sea collects the whole pool, broadcast server-wide. Anyone can add to a
standing bounty, so a hated raider's price climbs.
- **`BountyLedger`** (Bukkit-free, unit-tested): pooling, one-shot `claim`,
  richest-first `top`, snapshot/load that drops malformed rows.
- **`BountyService`**: spends Chronons via the existing
  `DarkSeaItems.removeChronons`, pays the killer on a `PlayerDeathEvent` (only
  a real player kill in the sea — an environmental drowning leaves the bounty
  standing), persists to `bounties.yml` across restarts.
- **`/ds bounty`** lists standing bounties; **`/ds bounty <player> <amount>`**
  places one (online targets, no self-bounty). Tab-completes online names.
- Ten new `bounty-*` messages; the placed/claimed lines broadcast to everyone.

### 9. Vault-cracking mechanic — code (MAYBE, later update; Wyatt Jul 21)
Backlog idea, parked deliberately. Today every chest is reachable **on foot**
(walk / crawl / drop — see 3e); nothing is sealed, because island blocks
can't be mined. A later update could make the castle's two **true vaults**
(and maybe the Core's chambers) *earned* instead of merely walked into: a
lever, a puzzle, or a breakable vault door that the protection listener
whitelists — so cracking a vault is a deliberate act, not a pickaxe grind.
Wants its own design pass (what opens it, whether it's timed/contested,
whether it resets on the soft-reset cycle) before any code.

### 3e. Every chest lootable without breaking blocks — BUILT (Wyatt caught it Jul 21)
Wyatt's question — "how do you break in if you can't break blocks?" — exposed
a real bug: island protection cancels all block-breaks, so a **sealed** chest
was dead loot. Found and fixed:
- **The castle's buried vaults** (throne vault, reliquary, crypt, cistern) had
  decorative stairs whose treads jumped two blocks — un-climbable. Routed each
  through a guaranteed 1-per-step shaft (`climbOut`).
- **The corrupted forest's cellar** climbed *inward* and got sealed under the
  mushroom dome (and re-sealed by trees planted after it). Now it climbs
  *outward*, carved **last** so nothing can bury it; the fallen-log crawl got a
  walkable mouth.
- **A reachability flood-fill** (`DemoShapeTest.everyChestCanBeLootedWithoutBreakingBlocks`)
  now proves every chest on every shape is walk/crawl/fall-reachable — swept
  clean over 28,000 chests offline, locked to a broad seed band in CI so no
  future edit can re-seal one.

**Default order if you go quiet: 4 → 5 → 6 → 7 → 8** (1–3 are done).
Redirect any time by phone — nothing here needs the server.

---

## Return-day checklist

1. Upgrade the Minehut plan (6 GB).
2. Update **FAWE** — kills the "Unsupported class file major version 69" error.
3. Optionally install **MythicMobs** 5.x.
4. Download the newest **DarkSea jar** from the latest green Actions run and
   replace the old one in `plugins/`.
5. Apply the **6 GB profile** from the README (config values +
   `view-distance`), restart.
6. If using the Mythic pack: copy `mythicmobs-pack/` contents into
   `plugins/MythicMobs/`, restart.
7. `/ds reset full confirm` → brand-new layout with the new shapes and loot.
8. Note: the **timed sea reset ships enabled** (6h soft cycle,
   `reset.auto` in config). Turn it off or re-time it there if it gets in
   the way while you're setting up.

## Live tests still owed (carry-over from before camp)

- **Chest refill cooldown:** open the same chest twice quickly — the second
  open must NOT restock until the tier's cooldown has passed.
- **Phase 4 boats end-to-end:** find a token in loot → `/ds boat upgrade` →
  measurable speed + shield ring → level survives relog and restart.
- **Decide (a phone message is enough):** should `/ds tp` land you on the
  highest block instead of the configured spawn spot?

Everything above stays on this branch; nothing merges or ships anywhere
until you're back and say so.
