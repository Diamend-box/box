# Playtest 6 — nothing dropped

You ran out of time partway through playtest 5. So the rule for this document
is: **anything you did not explicitly report on is still untested, and stays
here until you have looked at it.** Nothing gets quietly retired because a
build went past it. The status column below says exactly how much confidence
each line has, and most of it says "none".

Order is by **how likely I am to have got it wrong**, not by importance.

**Build: 0.5.1 or newer.** The jar is `DarkSea-<version>-b<Actions run>.jar` and
`/version DarkSea` reports the same string, so you can always check what is
actually loaded. CHANGELOG.md has what each version changed.

---

## The full ledger

Everything outstanding, and what stands behind it.

| Item | Status | Where |
| --- | --- | --- |
| Boat jitter | **You retested: still jittery.** Second, deeper cause found and fixed | Pass 1 |
| Releasing W stops you dead | You retested — no longer reported | Done |
| Surge resets momentum | You reported it; fixed, **untested** | Pass 1 |
| Abominations suffocate on spawn | You reported it; fixed, **untested** | Pass 2 |
| Keep inventory off in the caves | Fixed in 0.5.0, **never tested** | Pass 3 |
| Indicator leads to a mined-out geode | Fixed in 0.5.0, **never tested** | Pass 3 |
| "crystal does." chat spam | Fixed in 0.5.0, **never tested** | Pass 3 |
| Godspore was amethyst | Changed in 0.5.0, **never seen** | Pass 3 |
| ~12 Cores in a row at a t5 castle | Fixed in 0.5.0, **never tested** | Pass 4 |
| Relic wake text too long | Cut in 0.5.0, **never seen** | Pass 5 |
| 48 sealed chests (watchtower, spire) | Fixed in 0.5.0, **never walked** | Pass 6 |
| Unreachable castle chests | **Still not reproduced.** Needs an island id | Pass 6 |
| Vein-sense arrows | You confirmed these were fine | Done |
| Island counts / ring spread | Was already `islands-per-ring`; shapes now too | Pass 7 |

Five decisions are still open at the bottom. None have been answered across
three rounds, and none of them block anything — but each one gets more
expensive as more is built on top.

---

## Pass 0 — before you sail

`/ds diag` first. Nothing since 0.5.0 touches startup, so anything other than
all-steps-OK is news.

NPCs are still hand-placed and nothing spawns them. On a fresh world:
`/ds npc create` for `refugee_trader`, `artificer`, `black_market`,
`boat_expert`, `apothecary`.

---

## Pass 1 — boats and the surge

You said "better but still very jittery", and you were right — I fixed one
cause and left the bigger one sitting there.

The one I fixed in 0.5.0: thrust was computed from the boat's measured step,
which is noisy because the client owns a ridden boat's physics, so the speed
being written back wobbled several times a second.

The one I missed: `boostFactor` returns "leave this alone" once a boat is at its
cap. At cruise that alternated — a tick over the cap was ignored and the hull
slowed, the next tick was under and got pushed, and round again. **The boat was
being shoved and dropped a few times a second by design.** Smoothing the input
could never have fixed that, because the shaking was the on/off decision itself.

There is no per-tick decision any more. The throttle aims at the cap — a fixed
number for a given hull — ramps to it and holds it. Nothing measured feeds in.

**The surge** was the same bug from the other side: it set the boat's velocity,
and the next movement tick wrote cruise speed straight over the top, so the
burst lived about a fortieth of a second. That is your "resets momentum after".
It hands its speed to the throttle now and bleeds down from it.

- **Hold W at top speed on open water.** Any judder at all is a fail.
- **Surge at speed.** It should shove you and settle back to cruise over a
  couple of seconds, not snap back the instant it fires.
- **Let go of W.** Coast down over roughly a second — not a wall, not a glide
  that never ends. You didn't re-report this, so I think it's right; say if not.
- **Turn hard at speed.** The 0.4.0 steering fix should still hold.
- **Tap W repeatedly.** Most likely case to feel sluggish.
- Level 1 vs. Maelstrom — the gap should be obvious.

Three numbers control the feel: ramp-up, coast-down, surge bleed. If it is
*nearly* right, name which one is wrong.

If it is **still** juddering, say so plainly and don't soften it. Two attempts
in, the next step isn't another tuning pass — it's accepting that writing
velocity to a client-driven boat can't be made smooth, and building the speed
some other way.

---

## Pass 2 — mobs that spawn buried

A spawn marker only guarantees the marker block itself is clear, and a Naxian
Abomination is taller than one block, so a marker under a low roof — a garrison
hut, the underside of a stair — put its head inside stone. Anything that walked
clear on its own was fine, which is why it only happened sometimes.

Spawns now need three blocks of air (`mob-spawning.spawn-clearance`), searching
up first and then sideways, and a point with nowhere to stand is skipped rather
than moved somewhere arbitrary.

- Watch a garrison spawn in. Nothing should arrive already taking damage.
- **Watch for the opposite failure:** if islands feel emptier than they did,
  real spawn points are being skipped and the clearance is too strict.

---

## Pass 3 — the caves (untested since 0.5.0)

Four fixes here, none of which you have seen.

- **Die in the caves.** You should keep your inventory. The death handler that
  drops run loot checked *that* you died, never *where*, so it fired in every
  world.
- **The vein indicator** should now name a geode that is actually **live**. It
  ranks unmined veins first and only falls back to a regrowing one when nothing
  is live, with different wording so you can tell which you're being sent to
  without walking there. Walk to whatever it names and confirm crystal is in it.
- **Hit an amethyst cluster** in a geode shell. The "unbreakable" line should
  appear **once**, not once per swing. That was your screenshot.
- **Godspore** drops as a slime ball now, not an amethyst shard, so it no longer
  looks identical to the shell it grows out of.

Navigation down there is still hard on purpose. If the indicator doesn't fix
it, the next lever is decision 5 below.

---

## Pass 4 — the resident boss (untested since 0.5.0)

A t5 castle gave you about twelve Cores in a row because the boss slot refilled
the instant it emptied. An island now waits **15 minutes** after its boss falls
(`boss-respawn-minutes`, `/ds reload`-able).

- Kill a t5 castle's boss and stay there. Nothing should replace it.
- Come back after 15 minutes; it should be up.
- Ordinary mobs are unaffected — they still fill in on the island budget.

15 is a guess. If clearing a castle now feels dead afterwards I'll drop it; if a
second Core in the same run still feels cheap I'll raise it.

---

## Pass 5 — waking a relic (untested since 0.5.0)

The tile said four things; it now says two — what waking does, and either "click
to wake" or how many Chronons short you are. The anvil, sounds, particles and
boon are unchanged from playtest 4, which you didn't complain about.

Hold a dormant relic, open the artificer, confirm it reads cleanly at a glance.
Ten seconds of your time.

---

## Pass 6 — the chests

**I still have not found your castle chests.** Being plain about that before
describing what I did find, because they are not the same thing.

The shape suite had always asked "is there a cell beside this chest a player
could stand in?" That is adjacency, and it says nothing about whether that cell
connects to anywhere — which is why it passed on every seed while chests were
sealed. The test I wrote asks the real question: flood the island from open sea
with a player's actual movement (walk, step up one, fall any distance, swim) and
check whether the flood ever gets within arm's reach. It's deliberately generous
— no fall damage, no drowning — so a failure means genuinely walled in.

It found **48 sealed chests**, all in the **watchtower and the spire**:

- The watchtower's spiral was a single jutting stone every sixty degrees at
  radius three. Three blocks of gap per one of rise is not a stair.
- The spire's ledges rose **two blocks at a time**, which cannot be jumped.

Both are rebuilt on a primitive that can't produce an unwalkable route: at most
one block of rise per step, three cells of air over every tread.

The spire's summit chest I gave up on — one seed in thirty-six still sealed the
wind-hollow after many attempts, so that chest moved to a new grotto passage and
the hollow is now a landmark with nothing in it. A retreat, not a fix.

**The castle passes clean at 40 seeds.** So either yours hit something the model
can't see, or it's a placement case rather than a geometry one. If you hit it
again:

1. Your **coordinates** at the opening.
2. `/ds islands` — the **island id and origin**. This is the piece I've been
   missing both times; with the origin I can rebuild that exact castle offline
   and walk it myself.
3. Whether the chest is visible from where you're standing.

Also: the watchtower and spire now have real staircases where they had
decoration. Tell me if they **look** worse — I optimised for walkable and have
never seen either rendered.

---

## Pass 7 — island generation, if you want to tune it

Half of what you asked for already existed and you may not have found it, so:

- **`generation.islands-per-ring`** — how many islands each ring gets. Currently
  6 / 8 / 8 / 10 / 2, so 34 plus the home island and the landfall. This is both
  the total count and the ring distribution; change a number, `/ds reload`,
  `/ds reset full confirm`.
- **`generation.shape-weights`** (new) — *which* shapes a ring raises and how
  often, per ring or as a `default` block. Unlisted shapes keep their built-in
  rarity, 0 keeps a shape out of the sea entirely. Shipped commented out with
  examples, because the built-in rarities are the intended sea.

If what you actually wanted was one knob to scale the whole sea up or down at
once, say so — that's a small addition and I'd rather build the thing you meant.

---

## Decisions still open

Unchanged for three rounds. None block anything; all get more expensive later.

1. **The rename.** "The Mariphage" or "The Naxian Sea" over "Vironic Sea".
   Player-visible strings only, so it's cheap either way.
2. **What crystals buy.** Still no sink whatsoever. Three cave materials that do
   nothing is the reason that dimension feels optional. Waking relics for
   crystals is still my suggestion.
3. **Should `/ds tp` land you on the highest block?**
4. **Chest placement** — redo it so every chest has a square-on face, or leave
   the 124 that are diagonal-only?
5. **Caves breakability** — keep "nothing breaks but crystal", or let players
   tunnel so the caves can be navigated by digging?

---

## What to send me, ranked

1. **Boat feel** — smooth or not, and if not, whether it's ramp-up, coast-down
   or the surge. Third round on this; a blunt "still bad" is more useful than a
   polite "better".
2. **Another unreachable chest, with the island id.** Without it I'm guessing,
   and I've guessed twice.
3. Whether anything still spawns suffocating — and whether islands now feel
   emptier, which would be my fix going too far.
4. Whether the vein indicator ever sends you somewhere already stripped.
5. Whether 15 minutes is right for the boss.
6. Whether the rebuilt watchtower and spire stairs look like part of the
   building or like a fire escape.
7. Answers to any of the five decisions.

Everything else only if it annoyed you. And if you run out of time again, just
say where you stopped — I'll carry the rest forward the same way.
