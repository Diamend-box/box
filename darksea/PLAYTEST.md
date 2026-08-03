# Playtest 3 — the fix pass

Everything in this document exists because you reported it. The last session
found nine things; eight of them turned out to be real and are fixed, one I
could not reproduce and have said so plainly rather than quietly closing.

So this is a verification run, not an exploration run. The order below is by
**how likely I am to have got it wrong**, not by how important the feature is.
Two of these fixes I could reason about but never actually run — those go
first, because a wrong fix that nobody catches becomes the foundation for the
next three weeks of work.

**Build: Actions run #92 or newer.** Older builds have unmineable crystal and a
full reset that eats your islands.

---

## What changed since you last played

| You said | What it was | Status |
| --- | --- | --- |
| Boats glide, can't steer | Thrust followed your momentum, not your hull | Fixed, **unverified** |
| Can't break anything, incl. crystal with Eff 25 | Channel died mid-dig; no pickaxe could have helped | Fixed, **unverified** |
| Caves impossible to navigate | 13 geodes in 512 blocks with no wayfinding | Readout added |
| Full reset deletes every island but the landfall | Reset queued the landfall, then refused its own generate as busy | Fixed |
| (from your logs) world folder never deleted | Guard wanted a `level.dat` an unsaved world hadn't written | Fixed |
| Respawn in the overworld | Nothing claimed the respawn | Fixed |
| Relic waking not prominent / not satisfying | Anvil in the row's last-looked-at corner; wake was one chat line | Fixed |
| Chest I couldn't reach (t5 castle, underground) | Not reproduced — see Pass 6 | **Open** |
| Chests I couldn't stand next to | 124 of 321 have only a diagonal | Needs your call |
| Mob budget | You said it was fine | Left alone |

---

## Pass 0 — before you sail

1. **Place the NPCs.** They are only ever created by hand and nothing spawns
   them for you. On the home island:

   ```
   /ds npc create refugee_trader
   /ds npc create artificer
   /ds npc create black_market
   /ds npc create boat_expert
   /ds npc create apothecary
   ```

   The artificer is the only one who wakes relics, so Pass 5 depends on it.

2. **`/ds diag`.** Expect `45 of 45 startup steps OK` and `0 warning, 0 severe`.
   The `npc placements` line now tells you off if it reads 0 instead of just
   printing a number — last time you ran diag before spawning them, which is
   fine, but a genuinely empty sea looks identical in every other line.

3. **Do not run `/ds reset full confirm` yet.** It has its own pass, and it is
   the change most likely to have side effects I have not thought of.

---

## Pass 1 — boats (do this first)

This is the fix I am least able to check from here, and the sea is most of the
game.

The bug was that the boost scaled your *measured step* — the direction you were
already travelling — so every tick of a turn added speed down the old heading,
and letting go of W just handed the boat another shove. Thrust now follows the
hull's facing, and only while you are actually holding forward.

- Take a Maelstrom out and **turn hard at speed**. It should come round, not
  plough on.
- **Let go of W.** It should slow down and stop. Previously it kept going.
- **Reverse and strafe.** The boost is deliberately forward-only, so these are
  vanilla speed. Tell me if that feels wrong — it is a choice, not a limitation.
- Compare a level 1 boat to a Maelstrom. There should still be an obvious gap.

If it is still uncontrollable, say so bluntly and do not soften it. It would
mean applying velocity to a client-driven boat cannot work at all, and I would
rather rip out the mechanism than tune something that is broken in principle.

---

## Pass 2 — the caves

Crystal was unmineable and I want to be precise about why, because it changes
what "working" looks like.

`BlockDamageEvent` fires once when a dig *starts*, not per tick. Vanilla then
runs its own timer, the break at the end gets cancelled, and the client waits
out its own delay — so a held mouse button goes **silent for about fifteen
ticks**. My abandon-grace was ten. Every channel longer than half a second died
in that gap. Separately: a froglight is not pickaxe-mineable, so it takes no
tool bonus and no Efficiency from vanilla at all — your Eff 25 was doing
literally nothing for the dig. It does count in *our* curve, which is the one
that now runs.

- Hold left-click on a geode core. **You should see a progress bar on the action
  bar** filling to 100%, then the block turns to basalt and you get the crystal.
  If there is no bar at all, the channel is not opening and that is a different
  bug from the one I fixed.
- **Look away mid-dig.** It should abort and the crack should reset.
- Try all three crystals. At reference gear they are 1.5s, 2.5s and 4.0s per
  block; your Eff 25 netherite should be noticeably faster than that.
- **The vein readout**: anywhere in the caves, the action bar should name the
  nearest geode with an arrow and a distance. The arrow is relative to the way
  you are facing, so turn on the spot and check it swings.
- Half-mine a vein, note the time, come back: it should return **whole** at
  first-touch plus its cooldown, not from the last block you took.

Navigation is still hard on purpose, but if the readout does not fix it, the
next lever is the "nothing in the caves is breakable" rule. That rule is why
you cannot dig toward anything. Tell me if it should go.

---

## Pass 3 — the full reset (on a world you don't mind losing)

Two bugs stacked here, and the fix touches world deletion, so treat it with
suspicion.

Queueing the landfall started the paste queue, which made the placer busy, so
the `generate()` immediately after refused itself — that "Island generation or
a reset is already running" line in your screenshot was the sea being emptied.
And the folder delete refused to run because it wanted a `level.dat` that a
world unloaded without saving had never written.

- `/ds reset full confirm`, then watch the console. You should see the world
  removed, then a **34-island** generation, landfall included.
- You should **not** see `Refusing to delete` — and if you do, it now says
  which check failed, so send me that line verbatim.
- `/ds islands` afterwards should list the full set, not just `t5-1`.
- Fly to a couple of old island coordinates and confirm nothing is left standing
  from the previous layout.

---

## Pass 4 — dying

- Die in the sea. You should respawn **in the sea**, not in the overworld.
- Die in the caves. Also the sea — the caves have no spawn of their own and the
  way in is a boat ride.
- Set a bed on an island and die. Your bed should win.
- Confirm run loot still drops at the death spot and the rest of your gear comes
  back, which is unchanged but shares the same event.

---

## Pass 5 — waking a relic

You said this wasn't prominent enough and didn't feel satisfying. Both were
presentation problems, and I did not touch what relics actually do.

- Open the artificer with a **dormant relic in your main hand**. The anvil is
  now in the **middle of the bottom row**, and it glints when you can afford it.
- The tile should tell you four things: what waking does, what you're holding,
  what boon you'll get, and either "click to wake" or how many Chronons short
  you are.
- Click it. Expect the anvil strike, then **half a second later** the relic
  answers with its own sound, end rod particles, and a title naming the boon.
- Open it again holding the woken relic — it should say so rather than offering
  to wake it twice.

If it still lands flat, tell me which part: the tile, the moment, or the boon
itself. Those are three different fixes and I would rather do the right one.

---

## Pass 6 — the castle chest I couldn't find

This is the one I failed on, and I want to be straight about it. I built a
walkability model to hunt it and it flagged 121 chests — then I checked the
model against itself and found it could only reach **176 of a t5 castle's 921
standable floor cells**. It cannot walk around a castle, so its verdicts were
mostly its own blind spots. I threw the number away.

What I did fix on the way: `climbOut`, the function whose whole job is
guaranteeing a climbable stair out of every buried vault, took a **hardcoded
step count** — six or seven, chosen against the castle as it was before it grew
with tier. A fixed count cannot make that guarantee. It now climbs until it
breaks into open air. Your "I could see an opening but not the chest" is exactly
what a stair that stops short looks like, so this may well be it. I can't claim
it.

If you find another one:

1. Stand at the opening and note your **coordinates**.
2. `/ds islands` — get the **island id and its origin**.
3. Tell me whether the chest is visible from the opening or not.

With the island's origin I can rebuild that exact castle offline from its
position seed and look straight at the geometry. Right now I am guessing.

---

## Pass 7 — chests generally

I swept all 321 chests across every shape, tier and seed. None are unreachable
by the standard the test can check, and that standard is now locked in so it
cannot regress. But **124 of them have no square-on face** — only a diagonal
standing spot. That is legal and openable, and it is also precisely what "I
could loot it but couldn't stand next to it" describes.

Making every chest face-accessible is a placement change across all eleven
shapes, not a bug fix. Worth doing? Your call — see below.

---

## Decisions I need from you

Three carried over, two new. None block anything, but all five are cheaper to
answer now than after more is built on top.

1. **The rename.** I still think "The Mariphage" or "The Naxian Sea" beats
   "Vironic Sea". Player-visible strings only, so it is cheap either way.
2. **What crystals buy.** They still have no sink at all. The caves produce
   three materials that do nothing, which is the real reason that dimension
   feels optional. My suggestion is still to make waking relics cost crystals —
   it gives the caves a purpose and gives relics a cost worth going to get.
3. **Should `/ds tp` land you on the highest block?**
4. **Chest placement** (new): redo it so every chest has a square-on standing
   face, or leave the 124 diagonals?
5. **Caves breakability** (new): keep "nothing breaks but crystal", or let
   players dig through the rock so the caves can be navigated by tunnelling?

---

## What to send me, ranked

1. **Boat feel.** One sentence is enough. It is the fix I am least sure of.
2. **Whether crystal comes out of a geode**, and whether you saw the progress
   bar. If there was no bar, say so — that distinguishes two different bugs.
3. **The full reset console output**, especially any `Refusing to delete` line.
4. **Another unreachable chest**, with coordinates and island id, if you hit one.
5. Anything the vein readout got wrong — a backwards arrow would be worse than
   no arrow.
6. Answers to any of the five decisions above.

Everything else, only if it annoyed you. The last two rounds were long lists and
they were the right lists — most of what you flagged was real.
