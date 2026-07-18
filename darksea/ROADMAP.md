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

**Status:** seven generators live in `island/shape/`; tests enforce the
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

### 2. MythicMobs content pack — content + small code
A ready-to-copy `mythicmobs-pack/` with mob YAMLs themed per zone (e.g.
tide-husks near shore → storm callers → abyssal horrors), plus a `fallback:`
field per entry in `mobs.yml` so the config can name Mythic mobs and still
degrade gracefully to vanilla mobs when MythicMobs isn't installed.
**Needs your input: the vibe and names you want per zone — phone is fine.**

### 3. Loot 2.0 — content
Named and lored themed items per tier, junk/mid/treasure weighting, tuned
boat-token rarity, small next-tier teases. CI gets a parse test so a typo
can never brick loot loading.

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
