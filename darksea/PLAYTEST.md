# Playtest 5 — the walkability pass

Everything below exists because you reported it. Playtest 4 turned up nine
things. Eight are fixed. The ninth — the unreachable chests in the castle — I
**could not reproduce**, and what I fixed instead was a different set of
unreachable chests that I found while hunting yours. That distinction matters,
so it gets its own pass rather than a tick in a table.

Order is by **how likely I am to have got it wrong**, not by importance. The
boat goes first again, because it is still the fix I cannot check from here.

**Build: `DarkSea-0.5.0-b97.jar` or newer.** Jars are no longer all called
1.0.0 — the number after `-b` is the Actions run that built it, and
`/version DarkSea` reports the same string, so you can always tell what is
actually loaded. Runs 94 and 95 are red; don't build from them. See
CHANGELOG.md for what each version changed.

---

## What changed since you last played

| You said | What it was | Status |
| --- | --- | --- |
| Fast boats jitter, not smooth | Velocity rewritten each tick from a noisy measured step | Fixed, **unverified** |
| Releasing W stops you dead | No coast — thrust was all-or-nothing per tick | Fixed, **unverified** |
| Keep inventory fully off in the caves | Death-loot handler ran in every world, caves included | Fixed |
| Indicator leads to a geode you already mined | Ranked by distance only, ignoring whether the vein was live | Fixed |
| Godspore being amethyst is weird | Was `AMETHYST_SHARD`; now a slime ball | Fixed |
| Chat spammed "crystal does." at a cluster | Unbreakable message fired once per swing | Throttled |
| t5 castle spawned ~12 Cores in a row | Boss respawned the instant its slot was free | Fixed |
| Text to wake a relic is too long | Four lines of tile text | Cut to two |
| Several unreachable chests in the castle | **Not reproduced** — see Pass 5 | **Open** |
| The arrows were fine | — | Left alone |

---

## Pass 0 — before you sail

`/ds diag` first. Nothing in this build changes startup, so anything other than
all-steps-OK is news.

The NPCs are still hand-placed and nothing spawns them. If this is a fresh
world: `/ds npc create` for `refugee_trader`, `artificer`, `black_market`,
`boat_expert`, `apothecary`.

---

## Pass 1 — boats, again

Last round I moved thrust onto the hull's facing, which fixed the steering and
introduced what you felt: jitter, and a wall when you let go.

Both come from the same mistake. I was reading the boat's *measured* velocity
each tick and multiplying it. A ridden boat's physics are client-authoritative,
so that measurement is noisy — every tick I was writing back a slightly
different speed, which is jitter by construction. And because the multiplier
applied only while W was held, dropping W dropped the boat to vanilla speed in a
single tick.

Speed is now a number the server holds per rider rather than a number it
re-measures. Holding W eases it up toward target; releasing W eases it down.
Nothing is written back from the measured step at all.

- **Hold W at top speed on open water.** It should be smooth. Any judder at all
  is a fail and I want to know.
- **Let go of W.** It should coast down over roughly a second, not stop dead and
  not glide forever.
- **Turn hard at speed** — the fix from last round should still hold.
- **Tap W repeatedly.** This is the case most likely to feel wrong; the ramp
  might read as sluggish. Tell me if it does.
- Level 1 boat vs. Maelstrom — the gap should still be obvious.

Two numbers control the whole feel: how fast it ramps up, and how fast it coasts
down. If it is *nearly* right, say which end is wrong and I'll move one of them.

---

## Pass 2 — the caves

Three separate fixes here.

- **Die in the caves.** You should keep your inventory. The death handler that
  drops run loot was checking that you'd died — not *where* — so it ran in the
  caves and in the overworld too. It is now scoped to the sea and the caves.
- **The vein indicator.** It should now point at a geode that is actually
  **live**. It ranks unmined veins first and only falls back to a regrowing one
  when nothing is live — and when it does, the wording changes, so you can tell
  which of the two you're being sent to without walking there. Walk to whatever
  it names and confirm there's crystal in it.
- **Hit an amethyst cluster** in a geode shell. You should get the
  "unbreakable" line **once**, not once per swing. That was your screenshot.
- **Godspore** now drops as a slime ball. Green, organic, and no longer
  identical to the amethyst it grows out of.

---

## Pass 3 — the resident boss

You went to a t5 castle and it gave you about twelve Cores in a row. The boss
slot refilled the moment the previous one died, so a castle you were standing in
would raise its boss again immediately, forever.

An island now waits **15 minutes** after its boss falls before raising the next
one. That's `boss-respawn-minutes` in config.yml and `/ds reload` picks it up.

- Kill a t5 castle's boss. Stay there. Nothing should replace it.
- Come back after 15 minutes and it should be up again.
- Normal mobs are unaffected — they still fill in on the island budget.

15 is a guess. If clearing a castle now feels dead afterwards, say so and I'll
drop it; if a second Core inside the same run still feels cheap, I'll raise it.

---

## Pass 4 — waking a relic

The tile said four things. It now says two: what waking does, and either "click
to wake" or how short you are. The anvil, the sounds, the particles and the boon
are all unchanged from last round, which you didn't complain about.

Quick look only — hold a dormant relic, open the artificer, confirm it reads
cleanly at a glance.

---

## Pass 5 — the chests

**I did not find your castle chests.** I want to be plain about that before
describing what I did find, because they are not the same thing.

Here is what happened. The shape suite has always asked, of every chest, "is
there a cell beside this one a player could stand in?" That is adjacency. It
says nothing about whether that cell is joined to the outside world, which is
why it passed on every seed while chests were sealed. So I wrote the test that
asks the real question: flood the island from the open sea using a player's
actual movement — walk, step up one, fall any distance, swim — and check whether
the flood ever gets within arm's reach of the chest. It's deliberately generous
(no fall damage, no drowning) so a failure means genuinely walled in.

It found **48 sealed chests**. All of them in the **watchtower and the spire**:

- The watchtower's spiral was a single jutting stone every sixty degrees at
  radius three — three blocks of gap per one of rise. That is not a stair.
- The spire's parkour ledges rose **two blocks at a time**, which cannot be
  jumped at all.

Both looked like staircases in a cutaway and neither was climbable, so every
chest above them was unreachable on every seed the sea has ever generated. Both
are rebuilt on a primitive that can't produce an unwalkable route: it rises at
most one block per step and clears three cells over every tread.

The spire's summit chest I gave up on. After many attempts one seed in thirty-six
still sealed the wind-hollow, so I moved that chest into a new grotto passage and
left the hollow as a landmark with nothing in it. That's a retreat, not a fix.

**The castle comes out clean at 40 seeds.** So either your castle hit something
this model can't see, or it's a placement case rather than a geometry one. If you
hit it again:

1. Note your **coordinates** at the opening.
2. `/ds islands` — the **island id and origin**. This is the part I've been
   missing both times; with the origin I can rebuild that exact castle offline
   and walk it myself.
3. Whether the chest is visible from where you're standing.

While you're at it: the watchtower and the spire now have real staircases where
they used to have decoration. Tell me if they **look** worse. I optimised those
for being walkable and I have not seen either one rendered.

---

## Decisions still open

Same five as last time; none answered yet, none blocking, all cheaper now than
later.

1. **The rename.** "The Mariphage" or "The Naxian Sea" over "Vironic Sea".
   Player-visible strings only.
2. **What crystals buy.** Still no sink whatsoever. Three cave materials that do
   nothing is the reason that dimension feels optional. Waking relics for
   crystals remains my suggestion.
3. **Should `/ds tp` land you on the highest block?**
4. **Chest placement** — redo it so every chest has a square-on face, or leave
   the 124 that are diagonal-only?
5. **Caves breakability** — keep "nothing breaks but crystal", or let players
   tunnel?

---

## What to send me, ranked

1. **Boat feel** — smooth or not, and whether the coast is too long or too
   short. Still the fix I'm least sure of, now for the second round running.
2. **Another unreachable chest, with the island id.** Without it I'm guessing,
   and I've now guessed twice.
3. Whether the vein indicator ever sends you somewhere empty.
4. Whether 15 minutes is right for the boss.
5. Whether the rebuilt watchtower and spire stairs look like part of the
   building or like a fire escape.
6. Answers to any of the five decisions.

Everything else only if it annoyed you.
