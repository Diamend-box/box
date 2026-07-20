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
zoom/pan, tier/seed/key controls and all chest markers. Still not wired
into `IslandPlacer` — waiting on Wyatt's per-shape verdicts. Wiring notes
when approved: persist shape id in the registry, chunk preload from the
shape's real radius, paste bottom-up with physics off, leaves need
`persistent=true`, config flag so soft reset never re-pastes the
hand-built spawn, and the placer's finalize step now iterates
`ShapeBuild.chests()` (loot table per chest can differ later).

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
`CastleIslandTest` (shape-aware island behavior). Still pending like the
other seven: Wyatt's visual verdict from the viewer, then placer wiring.

### 4. Boat phase hardening — code
Phase 4 (tokens and upgrades) is the one phase never live-tested. Add unit
tests around token matching, consumption and persistence, and review the
speed clamp — so when you finally test it, it just works.

### 5. Schematic pipeline guide — docs
Expand the schematic docs into a start-to-finish walkthrough (build →
markers → save → sidecar → pool folders → migrate ring by ring) for when
FAWE is updated.

### 6. Balance pass — config
Per-tier effect stacks, message polish, and a sanity check of ring math at
the larger 8000-block radius.

**Default order if you go quiet: 1 → 2 → 3 → 4 → 5 → 6.** Redirect any time
by phone — nothing here needs the server.

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

## Live tests still owed (carry-over from before camp)

- **Chest refill cooldown:** open the same chest twice quickly — the second
  open must NOT restock until the tier's cooldown has passed.
- **Phase 4 boats end-to-end:** find a token in loot → `/ds boat upgrade` →
  measurable speed + shield ring → level survives relog and restart.
- **Decide (a phone message is enough):** should `/ds tp` land you on the
  highest block instead of the configured spawn spot?

Everything above stays on this branch; nothing merges or ships anywhere
until you're back and say so.
