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

### 1. Demo island variety — code
Today every demo island is the same sand pad. Add a handful of built-in
shapes — rocky spire, broken ship deck, twin atoll, ruined watchtower —
picked randomly per island and scaling a little with tier. The shape math
lives in pure functions so JUnit can verify sizes and marker placement
without a server. *Effect: the sea stops looking copy-pasted, even before
real schematics exist.*

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
