# Building Real Island Schematics — start to finish

This is the return-day walkthrough for replacing the built-in demo/generated
shapes with your own WorldEdit builds, one ring at a time. Everything here is
what the plugin already reads — no code changes needed. The compact version
lives in the README; this is the full pipeline with the gotchas called out.

**Prerequisite:** FAWE updated on the server (the old build throws
`Unsupported class file major version 69` on 1.21.4 — updating FAWE is item 2
on the return-day checklist for a reason). WorldEdit alone works too; FAWE is
just faster on big pastes.

---

## The pipeline at a glance

```
build → place markers → set origin → //copy → //schem save
      → drop into schematics/tierN/ → (optional) sidecar .yml
      → /ds reload → /ds reset full confirm → verify → next ring
```

You never touch config to add a schematic — dropping a `.schem` into the right
folder is the whole registration step. The tier folder a build lives in IS its
ring.

---

## 1. Build it (anywhere)

Build on a creative flatworld — the plugin doesn't care where a schematic was
authored, only what's in the clipboard. Two hard rules from the way islands
are placed and protected in-world:

- **Everything is protected loot-content.** Once live, players can't break,
  place, bucket, or blow up an island's blocks — only open its chests. So the
  build has to be *complete and self-contained*; there's no "players will
  finish it" and no mining in. Every chest must be reachable **on foot**
  (walk / crawl / drop), because nobody can dig to it. Build the path in.
- **Interiors that open to the sea can slowly re-flood** once physics resumes
  (same limitation the generated shapes have). Prefer sealed rooms, or accept
  that a soft reset heals a re-flooded doorway. Don't put a chest behind a
  hole the ocean pours through.

Size to the ring — the generated shapes set the bar: **≥30×30** for the inner
rings, **50×50+** for the outer ones, and the ruined castle runs ~75×75.
Farther-out islands should never feel smaller than near ones.

## 2. Place the two markers

Two blocks are scanned inside the paste and replaced. Defaults (configurable
under `markers:` in config.yml):

| Marker block | Becomes | Notes |
| --- | --- | --- |
| `LODESTONE` | A registered, refilling loot chest | Faces away from island center automatically. One per chest you want. |
| `GOLD_BLOCK` | A mob-spawn point | Removed on scan (becomes empty air). |

Chest count is up to you, but match the generated cadence so loot density
stays even: roughly **1 chest for tier 1–2, 2 for tier 3, 3 for tier 4**, each
hidden inside something (grotto, tower floor, crater, vault) — never visible
from the water. Drop a `GOLD_BLOCK` wherever you want a mob to appear; the
per-island cap and tiering come from config, not the schematic.

> Vault election, wealth floor, mob-tier boost and resident-boss behavior are
> **shape traits** that only the built-in generated shapes carry. A raw
> schematic is a plain island: its `LODESTONE`s are ordinary refilling chests
> (single-vault election still applies on multi-chest islands via the normal
> path). If you want castle-style two-vault / nest-style guaranteed-boss
> behavior on a hand-built schematic, that's a code seam we'd add — flag it.

## 3. Set the origin and copy

The **origin point** is the clipboard position that will land at the ring's
paste height. Stand on the spot you want anchored — pick it at the build's
**waterline** so the island sits right in the sea — then:

```
//copy       (WorldEdit copies relative to where you stand)
//schem save <name>
```

`<name>` becomes the template id you'll see in `/ds island list` and in each
island's stored `template` field. Use short, lowercase, no-spaces names
(`wreck_small`, `bone_atoll`) — they show up in messages and logs.

The clipboard origin lands at `generation.paste-y` (default **Y 58**). If a
build's natural waterline isn't at 58, either re-pick the origin block at the
right height, or override per-template with a sidecar (next step).

## 4. Drop it into the ring's pool folder

```
plugins/DarkSea/schematics/
├── spawn/          # the home island — first .schem found is used
├── tier1/          # Calm-adjacent ring
├── tier2/
├── tier3/
├── tier4/
└── tier5/          # only the Mariphage nest fits here by design — see below
```

Copy `<name>.schem` into the tier folder for the ring you want it in. That's
the registration. **Real schematics take precedence over the built-in shapes
per tier** — the moment a tier folder has any `.schem`, that ring stops using
generated/demo islands and draws only from your pool. So a tier is all-or-
nothing: add builds for a ring and you own that whole ring's look.

The **home island** goes in `schematics/spawn/` — the first schematic found is
pasted at center and the generator never touches it (it stays hand-built /
yours). This is also where the refugee trader and any Undrowned-Heart hints
live once you build them.

**Tier 5 (the Sunless Trench)** builds only a handful of islands from a
nest-*dominant* pool: the Mariphage nest at weight 10, plus rare Trench
variants of the ruined castle and the volcano at weight 2 each (~71% nest,
~14% castle, ~14% volcano over `islands-per-ring[5]` = 3). The nest is a
guaranteed Core; the two outposts are the rare non-nest Core path — far
denser garrisons (castle 14 mobs, volcano 12) with a 0.12 chance to raise a
Core of their own. Dropping ordinary schematics into `tier5/` replaces that
whole pool with plain islands and removes every Trench Core path — only do it
if that's what you want. Tier 6 (the Devouring Rim) generates no islands at
all; the bait landmark out there is hand-placed, not a schematic pool.

## 5. (Optional) sidecar YAML

Drop a `<name>.yml` next to the schematic (same basename) to tune it:

```yaml
# schematics/tier2/wreck_small.yml
weight: 3      # selection weight within the tier pool (default 1)
paste-y: 60    # override generation.paste-y just for this template
```

- **weight** — relative odds within the ring. Two builds at weight 1 each
  appear equally; bump one to 3 to make it thrice as common. Use it to make a
  rare landmark build show up ~once per sea.
- **paste-y** — per-template height override, for a build whose waterline
  isn't the global paste-y.

No sidecar = weight 1, global paste-y. Perfectly fine for most builds.

## 6. Load and regenerate

```
/ds reload                 # re-reads config + rescans schematic folders
/ds reset full confirm     # new sea from the current pools (wipes the old layout)
```

`/ds reset full confirm` teleports anyone in the sea to the main-world spawn,
deletes the world folder + island registry, recreates the world with a fresh
seed, and re-runs generation — now drawing your new schematics for any tier
that has them. A **soft** reset (`/ds reset soft`) keeps positions and just
re-pastes over the existing islands; use full when you've changed which
schematics a ring can pull.

> Heads-up: the timed sea reset ships **enabled** (6h soft cycle). While you're
> setting up and testing builds, turn it off (`reset.auto.enabled: false`) or
> re-time it so it doesn't wash the sea out from under you mid-verification.

## 7. Verify before moving to the next ring

- `/ds island list` — confirms your template names are the ones placed in that
  tier (the `template` column should read your schematic ids, not `demo` or a
  built-in shape id).
- `/ds island tp <id>` — teleport to one and walk it: every chest reachable on
  foot? Mobs appearing at your `GOLD_BLOCK` points and stopping at the cap?
  Nothing visible from the water that should be hidden? No ocean pouring into a
  room?
- If a ring reports `generate-no-templates` or gets skipped, the folder is
  empty or misnamed (`schematics/tier2/` exactly, `.schem` extension).
- Watch for `generate-shortfall` — "found room for X of Y" means spacing is too
  tight for that ring's island count; either lower `islands-per-ring[N]` or
  raise `outer-radius` / lower `min-island-gap`.

Once a ring looks right, move to the next one. Migrating **ring by ring** (fill
tier1, verify, then tier2, …) is the safe path: a tier with no schematics keeps
using the built-in shapes, so the sea is always fully populated even mid-
migration. You're never forced to build all five rings before the sea is
playable.

---

## Quick reference

| Thing | Where | Default |
| --- | --- | --- |
| Home island | `schematics/spawn/` (first found) | hand-built, never generated |
| Ring pools | `schematics/tier1/` … `tier5/` | empty → built-in shapes |
| Chest marker | block placed in build | `LODESTONE` (`markers.chest`) |
| Mob-spawn marker | block placed in build | `GOLD_BLOCK` (`markers.mob-spawn`) |
| Paste height | `generation.paste-y` or sidecar | Y 58 |
| Selection weight | sidecar `weight:` | 1 |
| Islands per ring | `generation.islands-per-ring` | 6/8/8/10/3 |
| Reload after adding | `/ds reload` then `/ds reset full confirm` | — |

Any tier folder with at least one `.schem` overrides the built-in shapes for
that whole ring — that's the single rule that makes the migration incremental.
