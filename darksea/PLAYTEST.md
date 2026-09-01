# Playtest 7 — the chests are actually fixed this time

Same rule as last time: **anything you did not explicitly report on is still
untested and stays in this document.** Nothing is retired because a build went
past it.

One thing did change status for real. The unreachable chests have been
**reproduced, diagnosed and fixed** — three rounds after you first reported
them. Your `/ds islands` output is what made that possible, so if something like
it happens again, that is the thing to send.

Order is by how likely I am to have got it wrong, not by importance.

**Build: 0.5.4 or newer.** The jar is `DarkSea-<version>-b<Actions run>.jar` and
`/version DarkSea` reports the same string. CHANGELOG.md has what each version
changed.

---

## The full ledger

| Item | Status | Where |
| --- | --- | --- |
| Boat jitter | **You confirmed it: good now.** Three rounds, done | Done |
| Boats stop dead at the end of a coast | You reported it; fixed, **untested** | Pass 1 |
| Surge resets momentum | Fixed in 0.5.1, **never retested** | Pass 1 |
| Unreachable chests | **Reproduced and fixed.** Never walked | Pass 2 |
| Ravagers suffocate | Fixed twice now, **untested** | Pass 3 |
| No boss at the nest | Fixed, **untested** | Pass 4 |
| 15-minute boss respawn | Shipped 0.5.0, **never seen** — nothing rose at all | Pass 4 |
| Floating amethyst | You reported it; fixed, **untested** | Pass 5 |
| Vault lever on the battlements | You reported it; fixed, **untested** | Pass 6 |
| "above: they sell" | You called it awkward; reworded, **unseen** | Pass 6 |
| Vein indicator | **You confirmed it: fine for now** | Done |
| Keep inventory in the caves | Fixed in 0.5.0, **never tested** | Pass 5 |
| "crystal does." chat spam | Fixed in 0.5.0, **never confirmed** | Pass 5 |
| Godspore as a slime ball | Changed in 0.5.0, **never seen** | Pass 5 |
| Relic wake text | Cut in 0.5.0, **never seen** | Pass 7 |
| Rebuilt watchtower and spire stairs | Built in 0.5.0, **never judged by eye** | Pass 2 |
| Island counts / ring spread / shape mix | Config, **untried** | Pass 8 |
| Chest refill cooldown | Shipped pre-camp, **never tested** | Pass 9 |
| Boat upgrades (now bought with Chronons) | **Rebuilt in 0.5.3**, untested | Pass 9 |
| Renaming a crystal in ores.yml | New in 0.5.3, untested | Pass 9 |
| The reliquary, and buying slots with crystals | **New in 0.5.4**, untested | Pass 9 |

Five decisions are still open at the bottom. None have been answered across four
rounds.

---

## Pass 0 — before you sail

`/ds diag`. Nothing since 0.5.0 touches startup, so anything other than
all-steps-OK is news.

NPCs are still hand-placed: `/ds npc create` for `refugee_trader`, `artificer`,
`black_market`, `boat_expert`, `apothecary`.

---

## Pass 1 — the end of a coast

You said the boats feel good now, which closes the jitter after three attempts.
What is left is the stop.

Letting go of W dropped you dead after about a second because the throttle
handed the boat back to the client while it was still doing cruise speed — and a
rider not holding W is feeding the client no input, so the client's own speed
had already decayed to nothing underneath it. There was a cliff at the handover.
The coast now runs all the way down to a crawl before letting go, and slightly
faster, so it still reads as slowing rather than gliding forever.

- **Let go of W at full speed.** It should slow over a bit more than a second
  and roll to a stop. Neither a wall nor a glide that never ends.
- **Hold W at top speed**, confirming the jitter fix held.
- **Surge at speed.** You never got back to this one: it should shove you and
  settle to cruise over a couple of seconds, not snap back the moment it fires.
- **Tap W repeatedly**, and **turn hard at speed**.

If the stop is *nearly* right, say whether it is still too abrupt or now too
floaty — those are two different numbers.

---

## Pass 2 — the chests

**Found it.** One mistake made in five places: every buried stair was cut two
blocks of air high. Climbing a one-block step is a jump, and a jump needs room
over your head as well as somewhere to land. All five worked perfectly walking
*down* and could not be walked back up — which is exactly what you hit: drop
into a cellar, stand next to the chest, no way home.

- the castle's undercroft had no stair at all
- the watchtower's crypt trench cleared three cells over each tread but not over
  the one below it, putting a ceiling where a jumping head goes
- the beast's tail cache was a two-high room, so there was nowhere to jump even
  inside it
- the stair-cutter shared by the castle and the forest was two high everywhere

It survived two rounds because **my test was wrong in two ways at once**: it
counted any of the twenty-six cells around a chest as "in reach", so a corridor
on the far side of a wall passed; and it let a player fall any distance without
ever asking whether they could get back out. Both fixed, and the sweep now runs
against the exact islands from `/ds islands` as well as its own seeds — an
island's seed comes from where it sits, so the only way to test the castle you
stood in is to ask for that castle.

Clean at 1284 chests across every shape, tier and seed, and on both islands you
sent.

- **Walk a t5 castle and open everything.** The undercroft, the chapel
  reliquary, the cistern and the crypts are what changed.
- **The watchtower's crypt** and **the beast's tail cache**.
- If you find another, `/ds islands` again — it worked.
- Also: the watchtower and spire stairs have never been judged by eye. Do they
  look like part of the building or like a fire escape?

---

## Pass 3 — mobs that spawn buried

Second attempt. The first fix checked how much air was *above* a spawn point and
never how much was beside it, and a Ravager is about as wide as it is tall — so
a marker in a doorway or a crenellation gap had all the headroom it needed and
still had its flanks in stone.

Spawns now need a two-by-two footprint three blocks tall, and the box may sit
anywhere touching the marker rather than being centred on it, so it should cost
far fewer spawn points than it sounds like.

- Watch a garrison spawn. Nothing should arrive already taking damage.
- **Watch for the opposite failure too:** if islands feel emptier, real spawn
  points are being skipped and `mob-spawning.spawn-width` is too strict.

---

## Pass 4 — the boss that never came

Nothing rose at the nest because an island that had **never** raised its boss was
treated as one whose boss had just died — so every nest in a fresh sea sat out a
fifteen-minute respawn wait for a boss that had never existed. That was my 0.5.0
fix for the twelve-Cores-in-a-row problem, aimed at the wrong condition.

- **Sail to a nest.** A Core should be standing, or rise shortly after you
  arrive.
- **Kill it and stay.** Nothing should replace it.
- **Come back after 15 minutes.** It should be up. This is the half that has
  never once been observed, because until now nothing rose at all.

If clearing a castle feels dead afterwards I'll lower it; if a second Core in the
same run still feels cheap I'll raise it.

---

## Pass 5 — the caves

The floating amethyst was a cluster placed with no support: it is a directional
block that must attach to something, and setting the material alone left it
facing up with nothing underneath. Buds now grow out of a face of real rock, and
a cell with nothing to hold on to gets plain shell.

The vein indicator you have already confirmed is fine. Everything else down here
has been shipped for two builds and never tested:

- **Any floating crystal left?** Walk a couple of geodes.
- **Die in the caves.** You should keep your inventory.
- **Hit an amethyst cluster.** The "unbreakable" line should appear once, not
  once per swing.
- **Godspore** should drop as a slime ball, not an amethyst shard.

---

## Pass 6 — the outpost and the vault lever

Your lever was on a redstone lamp wedged between two crenellations on the castle
roof, because "furthest spawn point from the vault" is a battlement. It now
prefers a marker standing on open floor — three of its four neighbours walkable —
and only falls back to the old rule where a shape has nowhere better.

- **Find the lever on a castle and a couple of other shapes.** It should be
  somewhere you would walk past, not somewhere you would climb to.
- The shop band now reads **"▲ buy from them · sell to them ▼"**, from your side
  of the counter rather than the trader's. Open a shop and say if it still reads
  awkwardly — it is one config line either way.

---

## Pass 7 — waking a relic

Ten seconds of your time, and untested for three builds. Hold a dormant relic,
open the artificer, confirm the tile reads cleanly at a glance: what waking does,
and either "click to wake" or how many Chronons short you are. Nothing else.

---

## Pass 8 — island generation, if you want to tune it

Untried so far:

- **`generation.islands-per-ring`** — how many islands each ring gets, currently
  6 / 8 / 8 / 10 / 2. This is both the total count and the distribution.
- **`generation.shape-weights`** — which shapes a ring raises and how often, per
  ring or as a `default` block. 0 keeps a shape out entirely. Shipped commented
  out, because the built-in rarities are the intended sea.

Change, `/ds reload`, `/ds reset full confirm`. If what you actually wanted was
one knob to scale the whole sea at once, say so.

---

## Pass 9 — the two I nearly lost, and the two new ones

One was owed a live test before camp; the other two are new in 0.5.3. Same rule
as everything else: they stay here until you say otherwise.

- **Chest refill cooldown.** Open the same chest twice quickly. The second
  open must *not* restock until the tier's cooldown has passed.
- **Boat upgrades, end to end.** Boat tokens are gone: tiers are bought with
  Chronons at the boat wheel, 45 / 110 / 240 / 450 / 800. Earn the coin →
  `/ds boat upgrade` (or the wheel's Upgrade tile) → the Chronons leave your
  pack, the hull changes class, and measurable speed and shield follow → the
  level survives a relog and a restart. Try it one coin short as well: it should
  tell you the price and how much you have, and take nothing.
  Say whether the prices feel right. They are the one number here I have no way
  to test, and they are a config line each.

- **The reliquary, start to finish.** Buy one from the refugee trader (30 Chronons) and right-click it. File a woken relic with the Chest tile, click it in the collection to wear it, confirm the boost actually lands, then take it off and confirm it stops. Shift-click one to get the item back. Then drop the reliquary out of your pack: every relic boost should switch off while it is gone. Finally buy a slot with emberglass and check the count sticks across a relog and a restart.
  The thing I most want to know: does the bag read clearly the first time you open it, without me explaining it? If you have to guess what a tile does, it needs different words.
  Crystal prices are 12 / 20 emberglass, then 16 voidbloom, then 14 godspore. Say whether the caves pay out fast enough to make those feel earned rather than grindy — same as the boat prices, it is a config line each.

- **Rename a crystal.** Open `ores.yml`, find the `items:` block at the bottom,
  change `emberglass`'s `name` to anything, `/ds reload`, mine one. It should
  come out with the new name — and an emberglass already in a chest should still
  work as emberglass, because identity is a hidden tag rather than the name.
  This is the cheap way to settle the rename decision if you want to try names
  in game rather than on paper.

---

## Decisions still open

Unchanged for four rounds. None block anything; all get more expensive later.

1. **The rename.** "The Mariphage" or "The Naxian Sea" over "Vironic Sea".
2. **What crystals buy.** Still no sink at all — three cave materials that do
   nothing is why that dimension feels optional. Waking relics for crystals is
   still my suggestion. (Boat tiers now cost Chronons, so that sink is taken;
   crystals need one of their own.)
3. **Should `/ds tp` land you on the highest block?**
4. **Chest placement** — redo it so every chest has a square-on face, or leave
   the 124 that are diagonal-only?
5. **Caves breakability** — keep "nothing breaks but crystal", or let players
   tunnel so the caves can be navigated by digging?

---

## What to send me, ranked

1. **Whether the coast stops right** — still too abrupt, or now too floaty.
2. **Whether any chest is still sealed** — and if so, `/ds islands`.
3. Whether anything spawns suffocating, and whether islands feel emptier.
4. Whether a Core is standing at the nest, and whether 15 minutes is right.
5. Whether the surge still resets your momentum.
6. Whether the lever is somewhere sensible now.
7. Whether the watchtower and spire stairs look like part of the building.
8. Whether the reliquary explains itself, and whether the crystal prices feel earned.
9. Answers to any of the open decisions.

Everything else only if it annoyed you. If you run out of time, just say where
you stopped — I'll carry the rest forward the same way.
